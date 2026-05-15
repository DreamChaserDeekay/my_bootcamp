# Day 1 — Spring Boot 시작 흐름

## 한 줄 요약

`SpringApplication.run()`은 **Environment 준비 → ApplicationContext 생성 → Bean 등록 → refresh → CommandLineRunner**까지 정해진 흐름을 따른다. 7개의 ApplicationEvent를 발행하므로 어디든 끼어들 수 있다.

## 학습 목표

- [ ] `SpringApplication.run()`의 12단계
- [ ] 7개 라이프사이클 이벤트
- [ ] `WebApplicationType` 자동 감지 (NONE / SERVLET / REACTIVE)
- [ ] `CommandLineRunner` vs `ApplicationRunner` 차이
- [ ] application.yml 로딩 순서 및 우선순위
- [ ] 부팅 시 어디서 시간이 오래 걸리는지 진단

---

## SpringApplication.run() 흐름

```java
public ConfigurableApplicationContext run(String... args) {
    // 1. StopWatch 시작 (부팅 시간 측정용)
    // 2. SpringApplicationRunListeners 시작
    //    → ApplicationStartingEvent 발행
    
    // 3. Environment 준비
    //    - SystemProperties / EnvironmentVariables 읽기
    //    - application.yml / application.properties 로딩
    //    - @PropertySource 처리
    //    → ApplicationEnvironmentPreparedEvent
    
    // 4. Banner 출력 (보통 Spring 로고)
    
    // 5. ApplicationContext 생성 (WebApplicationType에 따라)
    //    - SERVLET → AnnotationConfigServletWebServerApplicationContext
    //    - REACTIVE → AnnotationConfigReactiveWebServerApplicationContext
    //    - NONE → AnnotationConfigApplicationContext
    
    // 6. ApplicationContextInitializer 적용
    //    → ApplicationContextInitializedEvent
    
    // 7. Bean 등록 (sources에 명시된 클래스)
    //    @SpringBootApplication 클래스가 BeanDefinition으로
    //    → ApplicationPreparedEvent
    
    // 8. ApplicationContext.refresh()
    //    → 모든 Bean 생성·주입·AOP·init
    //    → 내장 서버 시작 (Web이면)
    //    → ContextRefreshedEvent (Spring Framework 이벤트)
    
    // 9. afterRefresh hook
    
    // 10. StopWatch 종료, 시작 로그
    //     "Started ... in 4.532 seconds (process running for 5.123)"
    //     → ApplicationStartedEvent
    
    // 11. CommandLineRunner / ApplicationRunner 실행
    //     사용자가 정의한 startup 작업
    
    // 12. → ApplicationReadyEvent
    //     모든 것이 준비됨
    
    return context;
}
```

### 이벤트 7종 — 끼어들 수 있는 지점

```
1. ApplicationStartingEvent              ← run() 시작 직후 (Env도 안 만들어짐)
2. ApplicationEnvironmentPreparedEvent   ← Env 준비됨, Context는 아직
3. ApplicationContextInitializedEvent    ← Context 생성, 사용자 코드 아직
4. ApplicationPreparedEvent              ← 사용자 BeanDefinition 등록 완료, refresh 전
5. ContextRefreshedEvent                 ← refresh 완료 (Spring Framework 이벤트)
6. ApplicationStartedEvent               ← Boot run() 완료, Runner 실행 전
7. ApplicationReadyEvent                 ← 모든 Runner 완료 — 진짜 ready

이후
   ContextClosedEvent                    ← 컨텍스트 종료 시
```

각 이벤트의 `EventListener`로 끼어들기:

```java
@Component
public class StartupTrace {
    @EventListener
    public void onReady(ApplicationReadyEvent e) {
        // 이때부터 새 트래픽 받기 시작
    }
    
    @EventListener
    public void onStarted(ApplicationStartedEvent e) {
        // 모든 Bean 준비됨, Runner는 아직
    }
}
```

---

## CommandLineRunner vs ApplicationRunner

둘 다 부팅 마지막에 실행.

```java
@Component
public class Init1 implements CommandLineRunner {
    @Override
    public void run(String... args) {
        // args = main 메서드의 args 그대로
    }
}

@Component
public class Init2 implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        // 파싱된 args
        boolean debug = args.containsOption("debug");
        List<String> files = args.getNonOptionArgs();
    }
}
```

> ApplicationRunner가 약간 더 편함. 둘 다 startup 작업용.

### 흔한 startup 작업

```java
@Component
public class DataLoader implements ApplicationRunner {
    @Autowired UserRepo repo;
    
    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("seed")) {
            repo.save(new User("admin", "..."));
        }
    }
}
```

`./gradlew bootRun --args='--seed'`로 시드 데이터 생성.

---

## WebApplicationType 자동 감지

```java
public enum WebApplicationType {
    NONE,        // spring-web, spring-webflux 둘 다 없음
    SERVLET,     // spring-web (Tomcat 등)
    REACTIVE     // spring-webflux (Netty 등)
}
```

classpath 클래스로 자동 결정:
- `org.springframework.web.servlet.DispatcherServlet` 있으면 SERVLET
- `org.springframework.web.reactive.DispatcherHandler` 있으면 REACTIVE
- 둘 다 있으면 SERVLET (Web → MVC 우선)

명시 강제:

```java
SpringApplication app = new SpringApplication(App.class);
app.setWebApplicationType(WebApplicationType.NONE);
app.run(args);
```

```yaml
spring.main.web-application-type: none
```

---

## application.yml — 로딩 순서·우선순위

Spring Boot가 자동으로 다음 위치를 봄:

```
1. classpath:application.properties / .yml
2. classpath:application-{profile}.yml
3. file:./config/application.yml         ← jar 옆 config/
4. file:./application.yml
5. file:./config/*/application.yml       ← Docker 패턴
```

위에서 아래로 갈수록 **우선순위 높음**.

### 우선순위 전체 (높은 것이 이김)

```
1. @TestPropertySource (테스트)
2. Command-line args                     → --server.port=9000
3. SPRING_APPLICATION_JSON
4. ServletConfig / ServletContext init params
5. JNDI
6. Java System Properties                → -Dserver.port=9000
7. OS Environment Variables              → SERVER_PORT=9000
8. application-{profile}.yml (외부)
9. application.yml (외부)
10. application-{profile}.yml (classpath)
11. application.yml (classpath)
12. @PropertySource
13. SpringApplication.setDefaultProperties
```

### k8s에서 흔한 패턴

```yaml
# application.yml (classpath, 기본)
server:
  port: 8080
spring:
  profiles:
    active: ${ENV:local}
```

```yaml
# k8s ConfigMap → /config/application.yml
spring:
  datasource:
    url: jdbc:db2://prod-db:50000/MYDB
```

```yaml
# k8s 환경변수 → 최우선
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
```

---

## Profile

```yaml
# application.yml
spring:
  profiles:
    active: dev
---
spring:
  config:
    activate:
      on-profile: dev
db:
  url: jdbc:h2:mem:test
---
spring:
  config:
    activate:
      on-profile: prod
db:
  url: jdbc:db2://prod:50000/MYDB
```

```bash
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
# 또는
./gradlew bootRun --args='--spring.profiles.active=prod'
```

`@Profile("dev")`을 Bean에 붙여 활성 프로필에만 등록.

---

## 부팅 시간 진단

```yaml
spring:
  main:
    log-startup-info: true        # 기본 켜짐
debug: true                       # Conditional report 출력
```

```bash
./gradlew bootRun --debug
# 또는 -Ddebug
```

### Boot 3.x — Application Startup Tracking

```java
SpringApplication app = new SpringApplication(App.class);
app.setApplicationStartup(new BufferingApplicationStartup(2048));
app.run(args);
```

Actuator `/actuator/startup`에서 JSON으로 단계별 시간 출력. 어디가 느린지 즉시 파악.

```bash
curl http://localhost:8080/actuator/startup | jq
# 각 BeanDefinition 등록·생성 시간
```

---

## ApplicationContextInitializer — refresh 이전 개입

```java
public class MyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        // refresh 전에 환경·BeanDefinition 조작 가능
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
            "custom", Map.of("custom.key", "value")));
    }
}
```

등록:

```java
SpringApplication app = new SpringApplication(App.class);
app.addInitializers(new MyInitializer());
app.run(args);
```

또는 `META-INF/spring.factories` (Boot 2.x) / `spring.factories` 비슷한 패턴 (3.x는 imports 형식).

---

## SpringFactoriesLoader — Boot의 마법

Spring Boot가 **classpath의 jar에서 자동으로 등록할 클래스를 찾는 메커니즘**:

```
spring-boot-autoconfigure.jar
└── META-INF/
    ├── spring.factories                                    (옛 방식, deprecate)
    └── spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
            ← 200+ AutoConfiguration 클래스 나열
```

```
# org.springframework.boot.autoconfigure.AutoConfiguration.imports
org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
...
```

> Day 2에서 깊이.

---

## 운영 사례

### 사례 1 — 부팅 시간 5분

**증상**: Spring Boot 앱 시작에 5분.

**진단**:
```bash
./gradlew bootRun --debug | grep "Started"
# 또는 application startup tracking
```

```bash
curl /actuator/startup | jq '.timeline.events | sort_by(.duration) | reverse | .[0:10]'
```

→ `flywayMigrate` 단계가 4분.

**원인**: 데이터 마이그레이션 SQL이 큰 인덱스 재생성.

**조치**: 마이그레이션 분할, 부분 적용.

### 사례 2 — `application.yml`이 안 먹음

**증상**: `server.port=9000`을 yml에 적었는데 8080.

**진단**:
```bash
curl /actuator/env | jq
```

→ Command-line args나 OS env에서 `SERVER_PORT=8080`이 우선.

**조치**: 우선순위 표 확인.

### 사례 3 — Profile 안 먹음

**증상**: `prod` profile인데 dev db 가리킴.

**진단**:
```bash
curl /actuator/info
# 또는
curl /actuator/env | grep "profiles.active"
```

→ `spring.profiles.active=dev`가 classpath의 application.yml에 박혀있고 환경변수가 그것을 못 이김.

**조치**: 우선순위 확인. 보통 `SPRING_PROFILES_ACTIVE` 환경변수 권장.

---

## 실습 (Hands-on)

### 1단계 — 이벤트 추적

```java
@Component
public class TraceListener {
    @EventListener(ApplicationStartingEvent.class)
    public void onStarting(ApplicationStartingEvent e) {
        System.out.println("1. STARTING");
    }
    @EventListener(ApplicationEnvironmentPreparedEvent.class)
    public void onEnv(ApplicationEnvironmentPreparedEvent e) {
        System.out.println("2. ENV_PREPARED");
    }
    @EventListener(ApplicationContextInitializedEvent.class)
    public void onInit(ApplicationContextInitializedEvent e) {
        System.out.println("3. CTX_INIT");
    }
    @EventListener(ApplicationPreparedEvent.class)
    public void onPrep(ApplicationPreparedEvent e) {
        System.out.println("4. APP_PREPARED");
    }
    @EventListener(ContextRefreshedEvent.class)
    public void onRef(ContextRefreshedEvent e) {
        System.out.println("5. CTX_REFRESHED");
    }
    @EventListener(ApplicationStartedEvent.class)
    public void onStarted(ApplicationStartedEvent e) {
        System.out.println("6. APP_STARTED");
    }
    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent e) {
        System.out.println("7. APP_READY");
    }
}
```

@Component이지만 1-2는 컨테이너 등록 전이라 안 출력. 등록하려면:

```java
public static void main(String[] args) {
    SpringApplication app = new SpringApplication(App.class);
    app.addListeners(new TraceListenerEarly());    // Bean으로 등록 X
    app.run(args);
}
```

### 2단계 — application startup tracking

```java
SpringApplication app = new SpringApplication(App.class);
app.setApplicationStartup(new BufferingApplicationStartup(2048));
app.run(args);
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: startup
```

```bash
curl http://localhost:8080/actuator/startup | jq
```

각 BeanDefinition·refresh 단계 timing.

### 3단계 — Profile 실험

`application-dev.yml`, `application-prod.yml` 만들고 다음 시도:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
./gradlew bootRun --args='--spring.profiles.active=prod'
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

활성 프로필별 출력 비교.

### 4단계 — CommandLineRunner

```java
@Component
public class Seeder implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("seed")) {
            System.out.println("Seeding data...");
        }
    }
}
```

```bash
./gradlew bootRun --args='--seed'
```

---

## 더 읽어볼 자료

- 📘 『Spring Boot in Action』 (Walls)
- 🔗 [Spring Boot Reference — Application Startup](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.spring-application)
- 🔗 [Boot 3.x — Externalized Config](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- 🎓 김영한 — "스프링 핵심 원리 - 활용편"
