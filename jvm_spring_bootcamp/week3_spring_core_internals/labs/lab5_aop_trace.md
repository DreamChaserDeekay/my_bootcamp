# Lab 5 — AOP 프록시 추적

## 목표

- Spring AOP가 만든 동적 클래스를 직접 본다
- JDK Dynamic Proxy vs CGLIB 차이를 확인
- Self-invocation 함정을 재현
- AOP가 적용되는 / 안 되는 경우를 명확히 한다

---

## 1단계 — 프로젝트 구조

```
spring-aop-lab/
├── build.gradle
└── src/main/java/com/example/aoplab/
    ├── App.java
    ├── service/
    │   ├── OrderService.java         (인터페이스)
    │   ├── OrderServiceImpl.java
    │   ├── DirectService.java        (인터페이스 없음)
    │   └── SelfInvokeService.java
    ├── aop/
    │   └── LoggingAspect.java
    └── web/
        └── DebugController.java
```

### build.gradle

```gradle
plugins {
    id 'org.springframework.boot' version '3.3.4'
    id 'io.spring.dependency-management' version '1.1.6'
    id 'java'
}

group = 'com.example'
java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories { mavenCentral() }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

---

## 2단계 — Service 정의

```java
// OrderService.java (인터페이스)
package com.example.aoplab.service;

public interface OrderService {
    String place(String item);
}

// OrderServiceImpl.java
@Service
public class OrderServiceImpl implements OrderService {
    public String place(String item) {
        return "ordered: " + item + " (impl=" + getClass().getSimpleName() + ")";
    }
}

// DirectService.java (인터페이스 없음)
@Service
public class DirectService {
    public String run() {
        return "direct (impl=" + getClass().getSimpleName() + ")";
    }
}

// SelfInvokeService.java
@Service
public class SelfInvokeService {
    public String outer() {
        return "outer:" + inner();    // self-call
    }
    
    @Audited
    public String inner() {
        return "inner:" + Thread.currentThread().getName();
    }
}

// Audited.java — 표시용 어노테이션
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {}
```

### LoggingAspect.java

```java
@Aspect
@Component
public class LoggingAspect {
    
    @Around("@annotation(com.example.aoplab.service.Audited)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("  AOP before: " + pjp.getSignature());
        Object result = pjp.proceed();
        System.out.println("  AOP after: " + pjp.getSignature());
        return result;
    }
}
```

### DebugController.java

```java
@RestController
public class DebugController {
    @Autowired OrderService orderService;     // 인터페이스 타입
    @Autowired DirectService directService;
    @Autowired SelfInvokeService selfService;
    
    @GetMapping("/order/{item}")
    public String order(@PathVariable String item) {
        return orderService.place(item) + "\nproxy=" + orderService.getClass().getName();
    }
    
    @GetMapping("/direct")
    public String direct() {
        return directService.run() + "\nproxy=" + directService.getClass().getName();
    }
    
    @GetMapping("/self/outer")
    public String selfOuter() {
        return selfService.outer();
    }
    
    @GetMapping("/self/inner")
    public String selfInner() {
        return selfService.inner();
    }
}
```

---

## 3단계 — Spring Boot 기본 (CGLIB)

```java
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

```bash
./gradlew bootRun
```

### 결과 1 — 인터페이스 기반

```bash
curl http://localhost:8080/order/coffee
```

```
ordered: coffee (impl=OrderServiceImpl$$SpringCGLIB$$0)
proxy=com.example.aoplab.service.OrderServiceImpl$$SpringCGLIB$$0
```

→ 인터페이스 있어도 **CGLIB**가 기본. 자식 클래스로 감쌈.

### 결과 2 — 인터페이스 없음

```bash
curl http://localhost:8080/direct
```

```
direct (impl=DirectService$$SpringCGLIB$$0)
proxy=com.example.aoplab.service.DirectService$$SpringCGLIB$$0
```

당연히 CGLIB.

---

## 4단계 — JDK Dynamic Proxy 강제

```java
@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class App { ... }
```

또는

```yaml
spring:
  aop:
    proxy-target-class: false
```

재실행:

```bash
curl http://localhost:8080/order/coffee
```

```
ordered: coffee (impl=$Proxy<n>)
proxy=jdk.proxy3.$Proxy<n>
```

→ JDK Dynamic Proxy. `$Proxy<n>`은 JDK가 동적 생성한 클래스.

```bash
curl http://localhost:8080/direct
```

```
direct (impl=DirectService$$SpringCGLIB$$0)
```

→ 인터페이스 없으니 여전히 CGLIB.

---

## 5단계 — Self-Invocation 함정

```bash
# proxyTargetClass=true (CGLIB) 환경에서
curl http://localhost:8080/self/outer
```

예상:
```
outer:inner:http-nio-8080-exec-1
```

**AOP 어드바이스 출력 없음!** — self-call이라 프록시 우회.

```bash
curl http://localhost:8080/self/inner
```

예상:
```
  AOP before: ... inner(..)
  AOP after: ...
inner:http-nio-8080-exec-1
```

직접 호출 → 프록시 거침 → AOP 작동.

---

## 6단계 — Self-Invocation 해결책

### 방법 1 — ApplicationContext에서 자기 참조

```java
@Service
public class SelfFixed1 {
    @Autowired ApplicationContext ctx;
    
    public String outer() {
        SelfFixed1 self = ctx.getBean(SelfFixed1.class);
        return "outer:" + self.inner();
    }
    
    @Audited
    public String inner() {
        return "inner";
    }
}
```

### 방법 2 — 자기 자신을 Bean으로 주입

```java
@Service
public class SelfFixed2 {
    @Autowired @Lazy SelfFixed2 self;     // @Lazy 권장 (옛 Spring에선 순환)
    
    public String outer() {
        return "outer:" + self.inner();
    }
    
    @Audited
    public String inner() {
        return "inner";
    }
}
```

### 방법 3 — 클래스 분리 (가장 권장)

```java
@Service
class Outer {
    @Autowired Inner inner;
    public String run() { return "outer:" + inner.process(); }
}

@Service
class Inner {
    @Audited
    public String process() { return "inner"; }
}
```

---

## 7단계 — 모든 Bean의 프록시 여부 출력

```java
@RestController
class ProxyListController {
    @Autowired ApplicationContext ctx;
    
    @GetMapping("/proxies")
    public List<Map<String, String>> list() {
        return Arrays.stream(ctx.getBeanDefinitionNames())
            .filter(n -> !n.startsWith("org.springframework"))
            .filter(n -> !n.contains("HandlerMapping") && !n.contains("HandlerAdapter"))
            .map(n -> {
                Object bean = ctx.getBean(n);
                String cls = bean.getClass().getName();
                Map<String, String> m = new LinkedHashMap<>();
                m.put("name", n);
                m.put("class", cls);
                m.put("proxied", String.valueOf(
                    cls.contains("$$SpringCGLIB$$") || cls.contains("$Proxy")));
                return m;
            })
            .collect(Collectors.toList());
    }
}
```

```bash
curl http://localhost:8080/proxies | jq
```

`@Transactional`·`@Async`·`@Audited` 붙은 Bean만 `proxied: true`.

---

## 8단계 — CGLIB의 final 제약

```java
@Service
public final class FinalService {      // ← final
    @Audited
    public String run() { return "ok"; }
}
```

부팅 시 예외:
```
Caused by: org.springframework.aop.framework.AopConfigException:
    Could not generate CGLIB subclass of class com.example...FinalService
```

`final` 제거 또는 인터페이스 추가 후 `proxyTargetClass=false`.

### final 메서드 시도

```java
@Service
public class HalfFinal {
    @Audited
    public final String doIt() { return "ok"; }   // ← final 메서드
}
```

부팅은 성공. 그러나 `@Audited` 무시 (CGLIB이 override 불가, 경고 로그).

---

## 9단계 — Kotlin spring 플러그인 시뮬레이션

Kotlin 환경이면 `org.jetbrains.kotlin.plugin.spring`이 자동으로 Service 클래스를 `open`으로 변환. 안 그러면 모든 Spring Service가 final → CGLIB 못 만듦.

---

## 산출물

이 lab으로 확인:

- [ ] Spring Boot 기본은 CGLIB (인터페이스 있어도)
- [ ] proxyTargetClass=false로 JDK Dynamic Proxy 강제 가능
- [ ] Self-invocation은 어드바이스 무시
- [ ] 3가지 해결법 (ApplicationContext, @Lazy 자기 주입, 클래스 분리)
- [ ] final 클래스·메서드는 CGLIB 제약
- [ ] /proxies로 어느 Bean이 프록시인지 한눈에

---

## 다음 단계

[Lab 6 — Transactional Pitfalls](lab6_transactional_pitfall.md)
