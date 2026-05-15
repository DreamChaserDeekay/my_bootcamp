# Day 2 — Bean 생명주기

## 한 줄 요약

Spring Bean은 **생성 → 의존성 주입 → 초기화 → 사용 → 소멸**의 흐름을 따른다. 14개의 정해진 단계가 있고 각 단계마다 사용자가 끼어들 훅이 있다.

## 학습 목표

- [ ] Bean 생명주기 14단계를 외운다
- [ ] `@PostConstruct` / `InitializingBean.afterPropertiesSet` / `@Bean(initMethod)` 차이
- [ ] AOP 프록시가 정확히 어디서 만들어지는지 안다
- [ ] BeanPostProcessor의 두 콜백 (before / after init)에 무엇이 끼는지
- [ ] 소멸 콜백 3가지와 Spring Boot graceful shutdown

---

## Bean 생명주기 14단계

```
1.  생성자 호출 (또는 factory method)
       │
       ▼
2.  의존성 주입 (필드, setter)
       │
       ▼
3.  *Aware 인터페이스 호출
       - BeanNameAware
       - BeanFactoryAware
       - ApplicationContextAware
       │
       ▼
4.  BeanPostProcessor.postProcessBeforeInitialization()
       - @PostConstruct 호출 (CommonAnnotationBPP)
       - @Autowired 후처리 (이미 끝났지만 ConfigurationProperties 등)
       │
       ▼
5.  InitializingBean.afterPropertiesSet()
       │
       ▼
6.  @Bean(initMethod = "init") / <bean init-method="init">
       │
       ▼
7.  BeanPostProcessor.postProcessAfterInitialization()
       - AnnotationAwareAspectJAutoProxyCreator → AOP 프록시 생성!
       - 반환값이 진짜 Bean (프록시일 수 있음)
       │
       ▼
8.  ────── Bean 사용 ──────
       │
       ▼
9.  컨텍스트 종료 시작
       │
       ▼
10. DisposableBean.destroy()
       │
       ▼
11. @PreDestroy
       │
       ▼
12. @Bean(destroyMethod) / <bean destroy-method>
       │
       ▼
13. 인스턴스 해제 (GC 대상)
```

---

## 단계 1-2 — 생성·주입

```java
@Service
public class UserService {
    private final UserRepository repo;
    
    public UserService(UserRepository repo) {     // 생성자 주입
        this.repo = repo;
    }
}
```

### 주입 방식 비교

```java
// 1) 생성자 (권장)
@Service
public class A {
    private final B b;
    public A(B b) { this.b = b; }
}

// 2) Setter
@Service
public class A {
    private B b;
    @Autowired public void setB(B b) { this.b = b; }
}

// 3) 필드 (테스트·읽기 어려움. 비권장)
@Service
public class A {
    @Autowired private B b;
}
```

### 왜 생성자가 권장되나

- `final` 가능 → 불변성
- null 주입 불가 → 시작 시 발견
- 순환 의존성 즉시 탐지
- 테스트에서 mock 주입이 간단 (생성자 호출만)
- Spring 4.3+ 단일 생성자면 `@Autowired` 생략 가능

### 옛 setter 패턴이 남은 이유

- 순환 의존성 허용 (Spring Boot 2.6+에서 금지지만)
- Optional 의존성

---

## 단계 3 — *Aware 인터페이스

Spring이 "이 Bean에게 X를 알려준다"의 메커니즘.

```java
@Service
public class MyService implements ApplicationContextAware, BeanNameAware {
    private ApplicationContext ctx;
    private String myName;
    
    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        this.ctx = ctx;
    }
    
    @Override
    public void setBeanName(String name) {
        this.myName = name;
    }
}
```

요즘은 거의 안 씀 — `@Autowired ApplicationContext ctx`로 충분. 다만 framework·라이브러리 구현에선 여전히 사용.

---

## 단계 4-7 — 초기화

### 호출 순서

```
postProcessBeforeInitialization()    ← BPP가 여기서 끼어듬
    ↓
@PostConstruct                       ← CommonAnnotationBPP가 호출
    ↓
afterPropertiesSet()                 ← InitializingBean 구현이면
    ↓
@Bean(initMethod = "init")           ← @Bean 메타에 있으면
    ↓
postProcessAfterInitialization()     ← BPP가 여기서 끼어듬 (AOP!)
```

### 3가지 초기화 콜백 중 어느 것?

```java
@Component
public class Init1 {
    @PostConstruct                  // ✅ 표준, 권장
    public void init() { ... }
}

@Component
public class Init2 implements InitializingBean {
    @Override
    public void afterPropertiesSet() { ... }    // Spring 의존성 강함
}

@Configuration
public class Config {
    @Bean(initMethod = "init")      // POJO 사용 시
    public Init3 init3() { return new Init3(); }
}
```

> 일반적으로 **@PostConstruct**. POJO나 외부 라이브러리는 `@Bean(initMethod)`.

---

## 단계 7 — AOP 프록시 생성 (핵심!)

```
postProcessAfterInitialization()
   ↓
AnnotationAwareAspectJAutoProxyCreator
   ↓
이 Bean이 advice 매칭되나? (@Transactional, @Async, ...)
   ↓
Yes → CGLIB 또는 JDK Dynamic Proxy 생성 → 프록시 반환
No  → 원본 그대로 반환
```

### 그래서:

```java
@Service
public class OrderService {
    @Transactional
    public void place(Order o) { ... }
}

// ApplicationContext에 등록된 것은:
//   OrderService → OrderService$$EnhancerBySpringCGLIB$$abc...
//                  (CGLIB 서브클래스 = 프록시)

// 다른 곳에서 @Autowired OrderService → 프록시 인스턴스 받음
// → 프록시의 place()가 호출되면서 @Transactional 적용
```

> **AOP는 BeanPostProcessor.afterInit에서 일어난다.** Day 3에서 깊이.

---

## 단계 9-12 — 소멸

```
ApplicationContext.close() 또는 SIGTERM
   ↓
@PreDestroy                              ← 권장
   ↓
DisposableBean.destroy()
   ↓
@Bean(destroyMethod = "cleanup")
```

### 예

```java
@Component
public class ResourceService {
    private FileChannel channel;
    
    @PostConstruct
    public void init() throws IOException {
        channel = FileChannel.open(...);
    }
    
    @PreDestroy
    public void cleanup() throws IOException {
        channel.close();
    }
}
```

### Spring Boot Graceful Shutdown

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

SIGTERM 받으면:
1. 새 요청 거부
2. 기존 요청 처리 완료까지 대기 (최대 30s)
3. ApplicationContext.close() → @PreDestroy → 종료

> Kubernetes 환경에서 핵심. `preStop`도 추가하면 더 안전.

---

## BeanPostProcessor가 끼어드는 정확한 지점

```java
public abstract class AbstractAutowireCapableBeanFactory {
    
    protected Object initializeBean(String beanName, Object bean, RootBeanDefinition mbd) {
        
        invokeAwareMethods(beanName, bean);                       // 단계 3
        
        Object wrapped = bean;
        wrapped = applyBeanPostProcessorsBeforeInitialization(   // 단계 4
            wrapped, beanName);
        
        invokeInitMethods(beanName, wrapped, mbd);               // 단계 5-6
        // - @PostConstruct (BPP가 이미 실행)
        // - InitializingBean.afterPropertiesSet
        // - @Bean(initMethod)
        
        wrapped = applyBeanPostProcessorsAfterInitialization(    // 단계 7
            wrapped, beanName);
        
        return wrapped;
    }
}
```

→ 단계 7의 반환값이 **컨테이너에 등록되는 진짜 Bean**.

---

## 중요한 BeanPostProcessor 5종

| BPP | 역할 |
|---|---|
| `AutowiredAnnotationBeanPostProcessor` | `@Autowired`·`@Value` 처리 |
| `CommonAnnotationBeanPostProcessor` | `@PostConstruct`·`@PreDestroy`·`@Resource` |
| `ApplicationContextAwareProcessor` | *Aware 인터페이스 콜백 |
| `AnnotationAwareAspectJAutoProxyCreator` | **AOP 프록시 생성** |
| `ConfigurationClassPostProcessor` | `@Configuration` 처리 (BFPP) |

---

## 운영 사례

### 사례 1 — @PostConstruct에서 예외

```java
@Component
public class DbPing {
    @Autowired DataSource ds;
    
    @PostConstruct
    public void check() throws SQLException {
        ds.getConnection().close();   // DB 안 뜨면 SQLException
    }
}
// → BeansException → ApplicationContext startup 실패
// → SpringApplication.run() 예외
// → 컨테이너 종료
```

이건 **의도된 fast-fail**일 수 있음 (DB 없으면 시작 안 됨). 그런데:

```java
// ❌ 시작 시간 길어지면 k8s liveness probe 죽임
@PostConstruct
public void heavyInit() {
    // 30초 걸리는 작업
}
```

해결: ApplicationReadyEvent로 옮기거나 `@Async`.

```java
@EventListener(ApplicationReadyEvent.class)
public void heavyInit() {
    // 컨테이너는 이미 ready
}
```

### 사례 2 — @PreDestroy 안 호출됨

```bash
docker stop myapp   # SIGTERM
```

문제: Spring Boot 2.x 기본은 graceful shutdown 비활성화. SIGTERM 받자마자 강종.

```yaml
# ✅ 명시적 활성화
server.shutdown: graceful
```

---

## 실습 (Hands-on)

### 1단계 — 생명주기 추적 Bean

```java
@Component
public class LifecycleDemo implements 
        BeanNameAware, ApplicationContextAware, InitializingBean, DisposableBean {
    
    public LifecycleDemo() {
        System.out.println("1. Constructor");
    }
    
    @Autowired
    public void setDeps(Environment env) {
        System.out.println("2. Setter injection");
    }
    
    @Override
    public void setBeanName(String name) {
        System.out.println("3a. BeanNameAware: " + name);
    }
    
    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        System.out.println("3b. ApplicationContextAware");
    }
    
    @PostConstruct
    public void postConstruct() {
        System.out.println("4. @PostConstruct");
    }
    
    @Override
    public void afterPropertiesSet() {
        System.out.println("5. InitializingBean.afterPropertiesSet");
    }
    
    @PreDestroy
    public void preDestroy() {
        System.out.println("10. @PreDestroy");
    }
    
    @Override
    public void destroy() {
        System.out.println("11. DisposableBean.destroy");
    }
}
```

### 2단계 — BeanPostProcessor 끼워넣기

```java
@Component
public class TimingBPP implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String name) {
        if (name.equals("lifecycleDemo")) {
            System.out.println("  BPP.before");
        }
        return bean;
    }
    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        if (name.equals("lifecycleDemo")) {
            System.out.println("  BPP.after");
        }
        return bean;
    }
}
```

### 3단계 — 종료 시 출력 확인

```java
@SpringBootApplication
public class App {
    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext ctx = SpringApplication.run(App.class, args);
        Thread.sleep(2000);
        ctx.close();
    }
}
```

예상 출력:
```
1. Constructor
2. Setter injection
3a. BeanNameAware: lifecycleDemo
3b. ApplicationContextAware
  BPP.before
4. @PostConstruct
5. InitializingBean.afterPropertiesSet
  BPP.after
   ... Bean 사용 ...
10. @PreDestroy
11. DisposableBean.destroy
```

### 4단계 — AOP 프록시 적용 확인

`LifecycleDemo`에 `@Transactional` 또는 `@Async` 추가 → BPP.after에서 출력되는 `bean.getClass()`가 `...$$EnhancerBySpringCGLIB$$...`인지 확인.

---

## 더 읽어볼 자료

- 📘 『Spring in Action』 — Bean 생명주기 챕터
- 🔗 [AbstractAutowireCapableBeanFactory.initializeBean()](https://github.com/spring-projects/spring-framework/blob/main/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractAutowireCapableBeanFactory.java)
- 🔗 [Spring Reference — Bean Lifecycle](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html)
- 🎓 김영한 — "스프링 핵심 원리 - 기본편" 7-8장
