# Day 1 — IoC 컨테이너 (ApplicationContext refresh)

## 한 줄 요약

Spring의 모든 마법은 `ApplicationContext.refresh()` 안에서 일어난다 — 12개의 정해진 단계로 Bean을 찾고, 정의하고, 만들고, 의존성 주입하고, 후처리한다. 이 흐름을 모르면 `@Autowired`가 왜 가끔 `null`인지 알 수 없다.

## 학습 목표

- [ ] BeanFactory와 ApplicationContext의 관계를 안다
- [ ] BeanDefinition이 무엇이고 어떻게 만들어지는지 안다
- [ ] `refresh()`의 12단계를 외운다
- [ ] `@ComponentScan`이 어떻게 Bean을 찾는지 안다
- [ ] `BeanFactoryPostProcessor` vs `BeanPostProcessor` 차이

---

## 큰 그림

```
   사용자                            Spring 내부

   @Service                          ┌──────────────────┐
   class UserService { ... }   ───▶  │ ComponentScan    │ 클래스 발견
                                     └────────┬─────────┘
                                              ▼
                                     ┌──────────────────┐
                                     │ BeanDefinition   │ 설계도 (name, type, scope, init...)
                                     └────────┬─────────┘
                                              ▼
                                     ┌──────────────────┐
                                     │ BeanFactory      │ Bean 저장소·생성기
                                     │ DefaultListable  │
                                     │ BeanFactory      │
                                     └────────┬─────────┘
                                              ▼
                                     ┌──────────────────┐
   userService.findById(...)   ◀───  │ 의존성 주입       │
                                     │ + BeanPostProcessor│
                                     │ + AOP 프록시      │
                                     └──────────────────┘
```

| | 무엇 |
|---|---|
| **BeanDefinition** | Bean의 메타데이터(class, scope, dependencies, init/destroy, 등) |
| **BeanFactory** | BeanDefinition으로 Bean 생성·반환·관리. 기본 인터페이스 |
| **ApplicationContext** | BeanFactory + 추가 (Event, Resource, MessageSource, AOP 등). 우리가 흔히 쓰는 컨테이너 |

`ApplicationContext`는 내부에 `DefaultListableBeanFactory`를 갖고 있음 (조합).

---

## BeanDefinition — Bean의 설계도

```java
public interface BeanDefinition {
    String getBeanClassName();
    String getScope();
    String[] getDependsOn();
    String getInitMethodName();
    String getDestroyMethodName();
    ConstructorArgumentValues getConstructorArgumentValues();
    MutablePropertyValues getPropertyValues();
    // ...
}
```

다음 소스가 BeanDefinition으로 변환됨:

```java
// @Service / @Component / @Repository / @Controller
@Service
class UserService { ... }
// → BeanDefinition(beanClassName=UserService, scope=singleton)

// @Bean 메서드
@Configuration
class Config {
    @Bean
    public UserService userService() { return new UserService(); }
}
// → BeanDefinition(factoryMethodName=userService, factoryBeanName=Config)

// XML (옛 방식)
<bean class="com.example.UserService" />

// 프로그램으로 직접
ctx.registerBean(UserService.class, () -> new UserService());
```

---

## ApplicationContext.refresh() — 12단계

`AbstractApplicationContext.refresh()` 소스 직접 읽기 (요약):

```java
public void refresh() throws BeansException, IllegalStateException {
    synchronized (this.startupShutdownMonitor) {
        // 1. 준비
        prepareRefresh();
        
        // 2. BeanFactory 가져오기/만들기
        ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
        
        // 3. BeanFactory 후처리 (Aware 등록, ClassLoader, BeanPostProcessor 등록)
        prepareBeanFactory(beanFactory);
        
        try {
            // 4. 서브클래스 후처리 훅
            postProcessBeanFactory(beanFactory);
            
            // 5. BeanFactoryPostProcessor 호출
            //    여기서 @Configuration 처리, @ComponentScan 실행 → BeanDefinition 등록
            invokeBeanFactoryPostProcessors(beanFactory);
            
            // 6. BeanPostProcessor 등록 (실제 Bean 생성 전에)
            registerBeanPostProcessors(beanFactory);
            
            // 7. MessageSource 초기화
            initMessageSource();
            
            // 8. Event multicaster 초기화
            initApplicationEventMulticaster();
            
            // 9. 서브클래스 hook (Web에서는 Tomcat 등 시작)
            onRefresh();
            
            // 10. ApplicationListener 등록
            registerListeners();
            
            // 11. Singleton Bean 인스턴스화 (가장 중요!)
            //     - 의존성 주입
            //     - BeanPostProcessor 호출 (before init, after init)
            //     - @PostConstruct, InitializingBean.afterPropertiesSet, init-method
            //     - AOP 프록시 생성 (post init)
            finishBeanFactoryInitialization(beanFactory);
            
            // 12. 완료 — ContextRefreshedEvent 발행
            finishRefresh();
        }
        catch (BeansException ex) {
            destroyBeans();
            cancelRefresh(ex);
            throw ex;
        }
    }
}
```

### 각 단계의 의미

| 단계 | 무엇 | 사용자가 끼어들 수 있는 방법 |
|---|---|---|
| 1 | 메타데이터 set, listener 초기화 | - |
| 2 | XML/Java 설정으로 BeanFactory 생성 | - |
| 3 | ApplicationContextAware 등 인식 | - |
| 4 | 서브클래스 hook | onRefresh override |
| 5 | **BeanFactoryPostProcessor 실행** | 직접 등록 가능 |
| 6 | **BeanPostProcessor 등록** | 직접 등록 가능 |
| 7-8 | 인프라 초기화 | - |
| 9-10 | Listener 등록 | ApplicationListener |
| 11 | **모든 singleton Bean 생성·주입·init·AOP** | @PostConstruct 등 |
| 12 | ContextRefreshedEvent | listener |

---

## BeanFactoryPostProcessor vs BeanPostProcessor

```
BeanDefinition 레지스트리        Bean 인스턴스
       ▲                              ▲
       │                              │
   조작            구분                조작
       │                              │
BeanFactoryPostProcessor        BeanPostProcessor
(BeanDefinition 단계에서 개입)   (Bean 생성 단계에서 개입)
```

### BeanFactoryPostProcessor — BeanDefinition 조작

Bean이 만들어지기 **전에** BeanDefinition을 수정.

```java
@Component
public class MyBFPP implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        BeanDefinition bd = beanFactory.getBeanDefinition("userService");
        bd.setScope("prototype");        // 강제로 prototype 변경
    }
}
```

대표 구현: `ConfigurationClassPostProcessor` — `@Configuration`·`@ComponentScan`·`@Bean` 처리.

### BeanPostProcessor — Bean 인스턴스 조작

Bean이 만들어진 **직후** 인스턴스 수정·교체.

```java
@Component
public class MyBPP implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String name) {
        // init 메서드 호출 전
        return bean;
    }
    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        // init 메서드 호출 후
        // 여기서 AOP 프록시 교체!
        return bean;
    }
}
```

대표 구현:
- `AutowiredAnnotationBeanPostProcessor` — `@Autowired`·`@Value` 주입
- `CommonAnnotationBeanPostProcessor` — `@PostConstruct`·`@PreDestroy` 호출
- `AnnotationAwareAspectJAutoProxyCreator` — **AOP 프록시 생성!**

> AOP 프록시는 `postProcessAfterInitialization`에서 만들어진다. **이게 핵심**. 같은 클래스 호출 → 프록시 우회 → AOP 안 먹음. (Week 3 Day 3에서 깊이)

---

## @Configuration의 마법 — CGLIB 강화

```java
@Configuration
class Config {
    @Bean
    public Foo foo() { return new Foo(); }
    
    @Bean
    public Bar bar() {
        return new Bar(foo());          // foo()를 두 번 호출하면 Foo 객체 둘일까?
    }
}
```

답: 아니. Spring이 `Config`를 **CGLIB 서브클래스**로 만들어 `foo()` 호출 시 BeanFactory에서 가져오게 함. → 항상 같은 singleton Foo.

```java
@Configuration(proxyBeanMethods = false)    // CGLIB 안 씀 (lite mode)
class Config { ... }
// @Bean 메서드 직접 호출하면 매번 new
// Spring Boot는 이걸 권장 (성능)
```

### @Configuration vs @Component

| | @Configuration | @Component + @Bean |
|---|---|---|
| Bean 메서드 호출 | BeanFactory에서 (CGLIB 가로채기) | 매번 new (proxy 없음) |
| `final` 가능 | X (CGLIB 상속) | O |
| 성능 | 약간 느림 | 빠름 |

> **앱 코드에 두 종류의 @Bean 모드가 섞이면 디버깅 지옥**. 한쪽으로 통일 권장.

---

## ComponentScan — 어떻게 Bean을 찾나

```java
@ComponentScan(basePackages = "com.example")
```

내부:
1. `basePackages`를 ClassPath에서 스캔
2. `.class` 파일 발견 → ASM으로 헤더만 읽기 (전체 로드 X, 빠름)
3. `@Component`(또는 그 메타 어노테이션 — `@Service`/`@Repository`/`@Controller`) 있나?
4. 있으면 BeanDefinition 생성·등록

```bash
# 어떤 Bean이 등록됐는지 확인
@RestController
class DebugController {
    private final ApplicationContext ctx;
    public DebugController(ApplicationContext ctx) { this.ctx = ctx; }
    
    @GetMapping("/beans")
    public String[] beans() { return ctx.getBeanDefinitionNames(); }
}
```

또는 Actuator `/actuator/beans`.

---

## @Conditional · @Profile

```java
@Configuration
@ConditionalOnProperty(name = "feature.cache.enabled", havingValue = "true")
class CacheConfig {
    @Bean public Cache cache() { return new Cache(); }
}
```

`@Conditional`은 BeanDefinition 단계에서 평가 — 조건 false면 BeanDefinition도 등록 안 함. (Spring Boot AutoConfiguration의 기반, Week 4 Day 2)

---

## Bean Scope

| Scope | 의미 |
|---|---|
| `singleton` (기본) | 컨테이너당 1개 |
| `prototype` | 요청할 때마다 새 인스턴스 |
| `request` (Web) | HTTP 요청당 |
| `session` (Web) | HTTP 세션당 |
| `application` (Web) | ServletContext당 |

### prototype의 함정

```java
@Service
public class SingletonService {
    @Autowired
    private PrototypeBean proto;            // 주입은 한 번뿐
    
    public void use() {
        proto.doSomething();                // 항상 같은 인스턴스
    }
}
```

singleton에 prototype을 주입하면 prototype의 의미 상실. 해결책:

```java
// 1) ObjectProvider
@Autowired private ObjectProvider<PrototypeBean> protoProvider;
PrototypeBean p = protoProvider.getObject();  // 매번 new

// 2) Method Injection (@Lookup)
@Lookup
public abstract PrototypeBean createProto();
```

---

## Bean 정의 순서·의존성

```java
@Component
@DependsOn("dbInitializer")
class UserService { ... }
```

Spring이 의존성 그래프를 분석해서 순서 결정. 순환 의존성은 setter/필드 주입이면 가능하지만 생성자 주입에선 불가 (옛 Boot에서는 가능했으나 2.6+ 금지).

```java
// ❌ 순환 (Spring Boot 2.6+에서 기본 금지)
@Service
class A { A(B b) {} }
@Service
class B { B(A a) {} }
```

해결책:
- 설계 검토 (보통 분리 가능)
- `@Lazy` 한쪽에 (Bean을 프록시로 감싸서 늦게 생성)

---

## 운영 사례

### 사례 1 — `@Autowired`가 `null`

```java
@Service
public class Foo {
    @Autowired private Bar bar;
    
    private final String value = bar.fetch();   // ❌ NPE
    // 필드 초기화는 생성자 시점 = @Autowired 전
}
```

생성자 주입을 사용하면 자연스럽게 해결:

```java
@Service
public class Foo {
    private final Bar bar;
    private final String value;
    
    public Foo(Bar bar) {
        this.bar = bar;
        this.value = bar.fetch();    // 정상
    }
}
```

### 사례 2 — `@PostConstruct`에서 Bean이 안 보임

```java
@PostConstruct
public void init() {
    ctx.getBean(MyBean.class).doWork();    // ❌ 어떤 Bean은 아직 안 만들어짐
}
```

해결: `ApplicationListener<ContextRefreshedEvent>` 사용 — **모든 Bean이 만들어진 후 호출**.

```java
@EventListener
public void onReady(ContextRefreshedEvent e) {
    ctx.getBean(MyBean.class).doWork();
}
```

---

## 실습 (Hands-on)

### 1단계 — refresh 흐름 추적

```java
@SpringBootApplication
public class App implements ApplicationListener<ApplicationContextEvent> {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
    
    @Override
    public void onApplicationEvent(ApplicationContextEvent event) {
        System.out.println("EVENT: " + event.getClass().getSimpleName());
    }
}
```

실행하면:
```
EVENT: ApplicationStartingEvent
EVENT: ApplicationEnvironmentPreparedEvent
EVENT: ApplicationContextInitializedEvent
EVENT: ApplicationPreparedEvent
EVENT: ContextRefreshedEvent
EVENT: ApplicationStartedEvent
EVENT: ApplicationReadyEvent
```

### 2단계 — BeanFactoryPostProcessor 만들기

```java
@Component
public class TraceBFPP implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory bf) {
        System.out.println("BeanDefinitions: " + bf.getBeanDefinitionCount());
        for (String name : bf.getBeanDefinitionNames()) {
            BeanDefinition bd = bf.getBeanDefinition(name);
            System.out.println("  " + name + " → " + bd.getBeanClassName());
        }
    }
}
```

### 3단계 — BeanPostProcessor 만들기

```java
@Component
public class TraceBPP implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String name) {
        System.out.println("  BEFORE init: " + name);
        return bean;
    }
    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        System.out.println("  AFTER init: " + name + " class=" + bean.getClass().getName());
        return bean;
    }
}
```

`@Transactional` 붙은 Bean의 출력에서 `$$EnhancerBySpringCGLIB$$` 확인.

### 4단계 — Actuator로 Bean 트리

```yaml
management:
  endpoints:
    web:
      exposure:
        include: beans
```

```bash
curl http://localhost:8080/actuator/beans | jq | head -100
```

---

## 더 읽어볼 자료

- 📘 『Spring in Action』 6th ed. (Walls)
- 📘 『Pro Spring 6』 (Cosmina) — IoC 챕터
- 🔗 [Spring Framework 소스](https://github.com/spring-projects/spring-framework) — `AbstractApplicationContext.refresh()`
- 🔗 [Spring Reference — IoC](https://docs.spring.io/spring-framework/reference/core/beans.html)
- 🎓 김영한 — "스프링 핵심 원리 - 기본편" (인프런, 한국어)
- 🎓 김영한 — "스프링 핵심 원리 - 고급편" (AOP 포함)
