# Day 3 — AOP 프록시 (JDK Dynamic vs CGLIB)

## 한 줄 요약

Spring AOP는 **런타임 프록시**로 동작 — Bean의 자식 클래스(CGLIB) 또는 인터페이스 구현체(JDK Dynamic Proxy)를 동적으로 만들어 메서드 호출을 가로챈다. 이 메커니즘 때문에 **self-invocation은 AOP가 안 먹는다**.

## 학습 목표

- [ ] JDK Dynamic Proxy와 CGLIB 차이를 안다
- [ ] Spring AOP가 어느 프록시를 언제 선택하는지 안다
- [ ] CGLIB의 제약 (final, private)을 안다
- [ ] Self-invocation 함정의 근본 원인을 코드 수준에서 설명
- [ ] AspectJ vs Spring AOP의 차이
- [ ] `@EnableAspectJAutoProxy(proxyTargetClass=true)`의 의미

---

## AOP — 횡단 관심사

```java
@Service
public class OrderService {
    public void place(Order o) {
        // 1. 트랜잭션 시작
        // 2. 권한 체크
        // 3. 로깅
        try {
            // 진짜 비즈니스 로직
            ordersRepo.save(o);
        } finally {
            // 4. 메트릭
            // 5. 로깅
            // 6. 트랜잭션 commit/rollback
        }
    }
}
```

AOP로:

```java
@Service
public class OrderService {
    @Transactional
    @PreAuthorize("hasRole('USER')")
    @Timed("order.place")
    public void place(Order o) {
        ordersRepo.save(o);     // 핵심만
    }
}
// 트랜잭션, 권한, 메트릭은 AOP가 추가
```

---

## Spring AOP — 런타임 프록시

```
사용자 코드
   ↓ orderService.place(o)
   ↓
Proxy 인스턴스   ← Spring이 등록한 Bean
   ↓ before advice 실행 (트랜잭션 시작 등)
   ↓
Real OrderService 인스턴스    ← 실제 객체
   ↓ place() 실행
   ↑
   ↑ after advice (commit/rollback)
   ↑
Proxy
   ↑
사용자 코드 (반환)
```

### 두 가지 프록시 방식

#### 1) JDK Dynamic Proxy — 인터페이스 기반

```java
// 인터페이스가 있으면 기본 사용
public interface OrderService {
    void place(Order o);
}

@Service
public class OrderServiceImpl implements OrderService {
    @Transactional
    public void place(Order o) { ... }
}

// Spring 등록:
// Bean Type: OrderService (인터페이스)
// 실체: $Proxy42 (Proxy.newProxyInstance가 만든 동적 클래스)
```

내부:
```java
OrderService proxy = (OrderService) Proxy.newProxyInstance(
    classLoader,
    new Class<?>[]{OrderService.class},     // 구현할 인터페이스
    (proxy, method, args) -> {
        // before
        Object result = method.invoke(realImpl, args);
        // after
        return result;
    }
);
```

#### 2) CGLIB — 상속 기반

```java
@Service
public class OrderService {           // 인터페이스 없음
    @Transactional
    public void place(Order o) { ... }
}

// Spring 등록:
// Bean Type: OrderService
// 실체: OrderService$$EnhancerBySpringCGLIB$$abc... (OrderService를 상속한 동적 서브클래스)
```

내부:
```java
class OrderService$$EnhancerBySpringCGLIB$$abc extends OrderService {
    @Override
    public void place(Order o) {
        // before
        super.place(o);
        // after
    }
}
```

---

## Spring AOP 프록시 선택 규칙

| | 옛 Spring | Spring Boot 2.x+ |
|---|---|---|
| 클래스에 인터페이스 있음 | JDK Dynamic Proxy | **CGLIB** (`proxyTargetClass=true` 기본) |
| 클래스에 인터페이스 없음 | CGLIB | CGLIB |
| `@Configuration` | CGLIB | CGLIB |

> Spring Boot는 **항상 CGLIB**가 기본 (예측 가능성 우선).

### `proxyTargetClass`

```java
// 명시적으로 JDK Dynamic Proxy 사용
@EnableAspectJAutoProxy(proxyTargetClass = false)
// 또는
spring.aop.proxy-target-class = false
```

> 거의 손댈 일 없음.

---

## CGLIB의 제약 — final / private

CGLIB는 **서브클래스 생성**으로 동작하므로:

```java
// ❌ final 클래스 → 프록시 불가
@Service
public final class MyService {
    @Transactional
    public void doIt() { ... }
}
// 시작 시 예외: Cannot subclass final class

// ❌ final 메서드 → AOP override 불가
@Service
public class MyService {
    @Transactional
    public final void doIt() { ... }    // ← @Transactional 안 먹음!
                                        // 컴파일·시작은 정상, 런타임에 무시
}

// ❌ private 메서드 → 자식이 override 불가
@Service
public class MyService {
    @Transactional
    private void doIt() { ... }         // ← @Transactional 안 먹음
}
```

---

## Self-Invocation 함정 — 가장 흔한 사고

```java
@Service
public class OrderService {
    
    public void place(Order o) {
        // ... 
        process(o);                     // ❌ self-call!
    }
    
    @Transactional
    public void process(Order o) {
        // ... DB 작업
    }
}
```

문제: `place()`가 호출되면 **프록시**의 `place()` 실행. 그 안에서 `process(o)`는 **this**의 직접 호출 (Java 다형성, this의 정적 타입). 프록시를 거치지 않음 → `@Transactional` 무시.

### 왜?

```
컨테이너에서 받은 것:                 진짜 객체:
   OrderService$$Proxy                  OrderService (this)
       │                                   │
       │ place()                            │
       ▼                                   ▼
       AOP: before                       place() 본문
       super.place() ────────────────▶   process() 호출  ← this.process()
                                                ↓
                                         실제 OrderService.process()
                                         (AOP 우회!)
       AOP: after
```

### 해결책

```java
// 1) 자기 자신을 프록시로 주입 (자기 참조)
@Service
public class OrderService {
    @Autowired private OrderService self;        // 순환 같지만 OK (Spring이 프록시 주입)
    
    public void place(Order o) {
        self.process(o);                          // 프록시 경유
    }
    
    @Transactional
    public void process(Order o) { ... }
}

// 2) ApplicationContext에서 가져오기
@Service
public class OrderService {
    @Autowired private ApplicationContext ctx;
    
    public void place(Order o) {
        ctx.getBean(OrderService.class).process(o);
    }
    
    @Transactional
    public void process(Order o) { ... }
}

// 3) 클래스 분리 (가장 권장)
@Service
class OrderService {
    @Autowired ProcessService process;
    
    public void place(Order o) {
        process.run(o);    // 다른 Bean이라 프록시 경유
    }
}

@Service
class ProcessService {
    @Transactional
    public void run(Order o) { ... }
}

// 4) AspectJ (컴파일타임 weaving) — Spring AOP 한계 벗어남
```

> **3번이 정답**. 클래스 분리가 SOLID에도 맞음.

---

## AspectJ vs Spring AOP

| | Spring AOP | AspectJ |
|---|---|---|
| 메커니즘 | 런타임 프록시 | 컴파일타임 또는 로드타임 weaving |
| 대상 | Spring Bean만 | 모든 객체 |
| Self-invocation | 안 먹음 | 먹음 |
| field access | 못 함 | 가능 |
| private | 못 함 | 가능 |
| 성능 | 메서드 호출당 약간의 오버헤드 | 거의 없음 |
| 복잡도 | 낮음 | 높음 (컴파일·도구 설정) |

> 대부분의 경우 Spring AOP로 충분. 극단적 횡단 관심사 (모니터링·logging) 만 AspectJ.

---

## Aspect 만들기 — 실제 코드

```java
@Aspect
@Component
public class LoggingAspect {
    
    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);
    
    @Around("execution(* com.example.service..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("{} took {}ms", pjp.getSignature(), elapsed);
            return result;
        } catch (Throwable t) {
            log.error("{} failed", pjp.getSignature(), t);
            throw t;
        }
    }
}
```

### Advice 종류 5가지

| Advice | 언제 |
|---|---|
| `@Before` | 메서드 실행 전 |
| `@After` | 메서드 종료 후 (성공·실패 모두) |
| `@AfterReturning` | 정상 반환 후 |
| `@AfterThrowing` | 예외 발생 후 |
| `@Around` | 가장 강력 (전후 모두) |

### Pointcut 표현식

```java
// 클래스의 모든 메서드
"execution(* com.example.OrderService.*(..))"

// 패키지 하위 모든 클래스
"execution(* com.example.service..*(..))"

// 특정 어노테이션 붙은 메서드
"@annotation(com.example.Audited)"

// 특정 어노테이션 붙은 클래스의 모든 메서드
"@within(org.springframework.stereotype.Service)"

// 인자 매칭
"execution(* place(..)) && args(order)"
```

---

## AOP가 만든 클래스 보기

```java
@Service
public class MyService {
    @Transactional
    public void doIt() { ... }
}

// 다른 곳에서
@RestController
class Debug {
    @Autowired MyService service;
    
    @GetMapping("/debug")
    public String debug() {
        return service.getClass().getName();
    }
}

// → com.example.MyService$$SpringCGLIB$$0
// (또는 옛 Spring: ...$$EnhancerBySpringCGLIB$$abc123)
```

---

## 운영 사례

### 사례 1 — `@Transactional`이 same-class 호출에서 안 먹음

위의 self-invocation 함정. 가장 흔한 사고.

### 사례 2 — `@Async`가 안 먹음

```java
@Service
public class NotifyService {
    public void sendAll(List<User> users) {
        for (User u : users) {
            sendOne(u);          // ❌ self-invocation → 동기 실행
        }
    }
    
    @Async
    public void sendOne(User u) { ... }
}
```

같은 문제. 클래스 분리.

### 사례 3 — `@Transactional` 메서드가 private

```java
@Service
public class Foo {
    @Transactional
    private void doIt() { ... }      // ❌ CGLIB이 override 불가
}
```

`@Transactional`이 private에 안 먹음. Static analysis 도구로 잡거나 `public`으로.

### 사례 4 — `@Transactional`을 final 클래스에

```java
@Service
public final class Foo {              // ❌ 프록시 불가
    @Transactional
    public void doIt() { ... }
}
```

Boot 시작 시 예외. Kotlin의 default `final` 때문에 자주 만남:

```kotlin
// kotlin-spring 플러그인이 자동으로 open 붙임
@Service
class FooService {                    // 자동 open
    @Transactional
    fun doIt() { ... }
}
```

`org.jetbrains.kotlin.plugin.spring` 의존성 필요.

---

## 실습 (Hands-on)

### 1단계 — AOP가 만든 클래스 보기

```java
@RestController
class DebugController {
    @Autowired ApplicationContext ctx;
    
    @GetMapping("/proxies")
    public Map<String, String> proxies() {
        return Arrays.stream(ctx.getBeanDefinitionNames())
            .filter(n -> !n.startsWith("org.springframework"))
            .collect(Collectors.toMap(
                Function.identity(),
                n -> ctx.getBean(n).getClass().getName()));
    }
}
```

`/proxies` 호출하면 어느 Bean이 CGLIB로 감싸졌는지 보임.

### 2단계 — Self-Invocation 함정 재현

```java
@Service
public class SelfDemo {
    public void outer() {
        System.out.println("outer: " + getClass());
        inner();
    }
    
    @Transactional
    public void inner() {
        System.out.println("inner: " + getClass());
        System.out.println("transaction active: " + 
            TransactionSynchronizationManager.isActualTransactionActive());
    }
}

@RestController
class Test {
    @Autowired SelfDemo demo;
    
    @GetMapping("/self")
    public String test() {
        demo.outer();
        return "see logs";
    }
    
    @GetMapping("/proxy")
    public String testProxy() {
        demo.inner();    // 직접 호출 → 프록시 거침
        return "see logs";
    }
}
```

예상:
```
GET /self:
  outer: SelfDemo$$SpringCGLIB$$0     ← 프록시
  inner: SelfDemo$$SpringCGLIB$$0
  transaction active: false           ← AOP 안 먹음!

GET /proxy:
  inner: SelfDemo$$SpringCGLIB$$0
  transaction active: true            ← AOP 먹음
```

### 3단계 — Custom Aspect

```java
@Aspect
@Component
public class TimingAspect {
    @Around("@annotation(org.springframework.stereotype.Service)")
    public Object time(ProceedingJoinPoint pjp) throws Throwable {
        long t = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long elapsed = (System.nanoTime() - t) / 1_000_000;
            System.out.printf("%s: %dms%n", pjp.getSignature(), elapsed);
        }
    }
}
```

모든 Service 메서드 호출 시간 자동 로깅.

### 4단계 — JDK Dynamic Proxy 명시

```java
@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class App { ... }
```

인터페이스가 있는 Service만 `$Proxy<n>` (JDK Dynamic). 없으면 여전히 CGLIB.

---

## 더 읽어볼 자료

- 📘 『Spring in Action』 — AOP 챕터
- 📘 『AspectJ in Action』 (Laddad)
- 🔗 [Spring Reference — AOP](https://docs.spring.io/spring-framework/reference/core/aop.html)
- 🔗 [Proxy Mechanisms 비교](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- 🎓 김영한 — "스프링 핵심 원리 - 고급편" (AOP 깊이)
- 🔗 [Vlad Mihalcea — AOP transactional pitfall](https://vladmihalcea.com/spring-transactional-annotation/)
