# Day 3 — Starter · Actuator · Micrometer

## 한 줄 요약

**Starter**는 `의존성 한 줄에 = autoconfigure + 라이브러리 + 합리적 기본값`. **Actuator**는 운영용 엔드포인트 모음. **Micrometer**는 메트릭 추상 — Prometheus·Datadog·New Relic 어디든 같은 코드.

## 학습 목표

- [ ] Starter의 두 모듈 구조 (autoconfigure + starter)
- [ ] Custom Starter 만들기
- [ ] Actuator 자주 쓰는 endpoint 10개
- [ ] Custom Health Indicator
- [ ] Micrometer Counter / Timer / Gauge
- [ ] Prometheus 통합

---

## Starter 구조

Spring Boot의 `spring-boot-starter-web`는 사실 거의 비어있다:

```xml
<!-- spring-boot-starter-web -->
<dependencies>
    <dependency>spring-boot-starter</dependency>
    <dependency>spring-boot-starter-json</dependency>
    <dependency>spring-boot-starter-tomcat</dependency>
    <dependency>spring-web</dependency>
    <dependency>spring-webmvc</dependency>
</dependencies>
```

→ 단지 의존성 묶음. 실제 자동설정은 `spring-boot-autoconfigure`에 있음.

### Custom Starter 표준 패턴

```
my-feature-starter/                ← Maven/Gradle 모듈 1 — 사용자가 의존
└── pom.xml (또는 build.gradle)
    └── implementation('my-feature-autoconfigure')

my-feature-autoconfigure/          ← Maven/Gradle 모듈 2 — 실제 코드
├── pom.xml
└── src/main/
    ├── java/com/example/feature/
    │   ├── MyFeatureAutoConfiguration.java
    │   ├── MyFeatureProperties.java
    │   └── MyService.java
    └── resources/META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## Custom Starter 만들기 — 풀 예제

### 1) `my-feature-autoconfigure` 모듈

`build.gradle`:

```gradle
plugins {
    id 'java-library'
    id 'org.springframework.boot' version '3.3.4' apply false
    id 'io.spring.dependency-management' version '1.1.6'
}

dependencyManagement {
    imports {
        mavenBom org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-autoconfigure'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'
}
```

`MyFeatureProperties.java`:

```java
@ConfigurationProperties(prefix = "my.feature")
public record MyFeatureProperties(
    boolean enabled,
    String greeting,
    int retries
) {
    public MyFeatureProperties {
        if (greeting == null) greeting = "Hello";
        if (retries == 0) retries = 3;
    }
}
```

`MyService.java`:

```java
public class MyService {
    private final MyFeatureProperties props;
    
    public MyService(MyFeatureProperties props) {
        this.props = props;
    }
    
    public String hello(String name) {
        return props.greeting() + ", " + name;
    }
}
```

`MyFeatureAutoConfiguration.java`:

```java
@AutoConfiguration
@ConditionalOnProperty(name = "my.feature.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MyFeatureProperties.class)
public class MyFeatureAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public MyService myService(MyFeatureProperties props) {
        return new MyService(props);
    }
}
```

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.example.feature.MyFeatureAutoConfiguration
```

### 2) `my-feature-starter` 모듈

`build.gradle`:

```gradle
plugins { id 'java-library' }

dependencies {
    api project(':my-feature-autoconfigure')
    // 라이브러리 의존성 추가
    api 'org.apache.commons:commons-lang3:3.14.0'
}
```

이게 전부. 의존성 묶음만.

### 3) 사용자 앱에서

```gradle
dependencies {
    implementation 'com.example:my-feature-starter:1.0.0'
}
```

```yaml
my:
  feature:
    greeting: "안녕"
```

```java
@Service
public class App {
    @Autowired MyService my;
    
    public void run() {
        System.out.println(my.hello("world"));    // 안녕, world
    }
}
```

> 보통 사내 라이브러리(로깅 표준, 보안 표준 등)을 Starter로 만들어 팀끼리 공유.

---

## Actuator

Spring Boot의 운영 친화 endpoint. 의존성만 추가하면 사용 가능.

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

기본 노출되는 것은 `/actuator/health`, `/actuator/info`뿐 (보안 이유). 다음으로 활성:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"        # 모두 노출 (개발용)
        exclude: shutdown
  endpoint:
    health:
      show-details: always
```

### 주요 endpoint 10개

| Endpoint | 용도 |
|---|---|
| `/actuator/health` | liveness/readiness — k8s probe |
| `/actuator/info` | 빌드 정보, git 정보 |
| `/actuator/metrics` | 메트릭 목록 |
| `/actuator/metrics/{name}` | 특정 메트릭 값 |
| `/actuator/prometheus` | Prometheus 스크랩 형식 |
| `/actuator/env` | Environment 프로퍼티 |
| `/actuator/configprops` | @ConfigurationProperties 값 |
| `/actuator/beans` | 등록된 Bean 목록 |
| `/actuator/mappings` | URL → 컨트롤러 매핑 |
| `/actuator/threaddump` | 스레드덤프 |
| `/actuator/heapdump` | 힙덤프 (.hprof) |
| `/actuator/loggers` | 로거별 레벨, 실시간 변경 |
| `/actuator/conditions` | AutoConfig 매치 결과 |
| `/actuator/startup` | 부팅 단계 timing |
| `/actuator/shutdown` | 우아한 종료 (위험!) |

### `/actuator/loggers` — 운영 hot 디버그

```bash
# 현재 로거 레벨
curl http://localhost:8080/actuator/loggers/com.example.OrderService

# 동적으로 DEBUG로
curl -X POST http://localhost:8080/actuator/loggers/com.example.OrderService \
     -H "Content-Type: application/json" \
     -d '{"configuredLevel": "DEBUG"}'

# 원복
curl -X POST http://localhost:8080/actuator/loggers/com.example.OrderService \
     -H "Content-Type: application/json" \
     -d '{"configuredLevel": null}'
```

**운영서 디버그 시 핵심**. 재시작 없이 로그 레벨 변경.

---

## Health Indicator

`/actuator/health` 응답:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { ... } },
    "diskSpace": { "status": "UP", "details": { ... } }
  }
}
```

Boot가 기본으로 등록: `DataSource`, `Redis`, `Kafka`, `DiskSpace`, `Mail`, ...

### Custom Health Indicator

```java
@Component
public class ExternalApiHealth implements HealthIndicator {
    
    @Autowired RestClient client;
    
    @Override
    public Health health() {
        try {
            String resp = client.get().uri("/ping").retrieve().body(String.class);
            return Health.up()
                .withDetail("response", resp)
                .build();
        } catch (Exception e) {
            return Health.down(e)
                .withDetail("hint", "Check external API")
                .build();
        }
    }
}
```

→ `/actuator/health`에 자동 포함.

### k8s probe 설정

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
```

```yaml
# k8s
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8080 }
```

- **liveness**: 앱이 살아있는가 (`DOWN`이면 k8s가 재시작)
- **readiness**: 트래픽 받을 준비됐는가 (`DOWN`이면 LB에서 제외)

graceful shutdown 시 readiness를 먼저 DOWN으로.

---

## Micrometer — 메트릭 추상

Java의 SLF4J 같은 개념. 한 번 작성하면 여러 시스템에 전달 가능.

```gradle
implementation 'io.micrometer:micrometer-registry-prometheus'
```

### 기본 — 자동 메트릭

Spring Boot가 자동으로 노출:
- `http.server.requests` — 컨트롤러별 응답 시간·횟수
- `jvm.memory.used` / `jvm.gc.pause`
- `hikaricp.connections.*` — DataSource 풀
- `tomcat.threads.busy`
- `system.cpu.usage`

### Counter / Timer / Gauge

```java
@Service
public class OrderService {
    private final MeterRegistry registry;
    private final Counter orderCounter;
    private final Timer orderTimer;
    
    public OrderService(MeterRegistry registry) {
        this.registry = registry;
        this.orderCounter = Counter.builder("orders.created")
            .description("Number of orders created")
            .tag("source", "api")
            .register(registry);
        this.orderTimer = registry.timer("orders.place.duration");
    }
    
    @Timed("orders.place")    // AOP — @EnableAspectJAutoProxy 필요
    public Order place(OrderReq req) {
        return orderTimer.record(() -> {
            Order o = doPlace(req);
            orderCounter.increment();
            return o;
        });
    }
    
    @Scheduled(fixedRate = 60_000)
    public void recordPending() {
        registry.gauge("orders.pending", repo.countPending());
    }
}
```

### Prometheus 노출

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
```

```bash
curl http://localhost:8080/actuator/prometheus
# orders_created_total{source="api"} 42
# http_server_requests_seconds_count{method="GET",uri="/api/orders"} 100
# jvm_memory_used_bytes{area="heap",...} 123456789
```

Prometheus가 이걸 스크랩 → Grafana로 시각화.

---

## info endpoint

```yaml
info:
  app:
    name: '@project.name@'         # Maven/Gradle 치환
    version: '@project.version@'
  env: ${spring.profiles.active}
management:
  info:
    git:
      mode: full
      enabled: true
    build:
      enabled: true
```

`gradle.properties`:
```
springBootInfoBuildEnabled=true
```

`git.properties` (gradle-git-properties 플러그인이 생성):
```
git.commit.id=abc123
git.commit.time=2026-05-15T10:00:00Z
git.branch=main
```

→ `/actuator/info`로 빌드·git 정보 노출. 운영 사고 시 어떤 버전인지 빠르게 확인.

---

## Tracing (선택)

Boot 3.x는 Micrometer Tracing이 표준:

```gradle
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-zipkin'
```

```yaml
management:
  tracing:
    sampling.probability: 1.0
```

→ 컨트롤러·`RestClient`·DB 호출에 자동 trace context 전파.

---

## 운영 사례

### 사례 1 — 부팅 후 5분에 OOM, 원인 모름

**진단**: `/actuator/heapdump`로 즉시 덤프 → Mission Control 분석.

```bash
curl http://localhost:8080/actuator/heapdump -o heap.hprof
```

운영서에서 외부 도구 없이 덤프 가능. (보안: ingress 차단 필요)

### 사례 2 — Hikari Connection Leak

**증상**: `hikaricp.connections.active`가 시간이 갈수록 증가.

**진단**:
```bash
curl /actuator/metrics/hikaricp.connections.active
curl /actuator/metrics/hikaricp.connections.usage    # 사용 시간
```

leak-detection-threshold:
```yaml
spring:
  datasource:
    hikari:
      leak-detection-threshold: 60000    # 60초 안 반환되면 로그
```

→ 누가 connection을 안 닫는지 stacktrace로.

### 사례 3 — 운영서 로그 레벨 임시 변경

```bash
# 1. 현재 INFO인 OrderService를 DEBUG로
curl -X POST .../actuator/loggers/com.example.OrderService \
    -d '{"configuredLevel":"DEBUG"}'

# 2. 30분 분석 후 원복
curl -X POST .../actuator/loggers/com.example.OrderService \
    -d '{"configuredLevel":null}'
```

> 재시작 없이 — 운영 사고 골든타임 안에 정보 수집.

---

## 실습 (Hands-on)

### 1단계 — Actuator 활성·탐색

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health.show-details: always
```

```bash
curl http://localhost:8080/actuator | jq
curl http://localhost:8080/actuator/health | jq
curl http://localhost:8080/actuator/metrics | jq
curl http://localhost:8080/actuator/metrics/jvm.memory.used | jq
```

### 2단계 — Custom Health Indicator

```java
@Component
public class RandomHealth implements HealthIndicator {
    @Override
    public Health health() {
        return Math.random() > 0.5 
            ? Health.up().withDetail("rand", "ok").build()
            : Health.down().withDetail("rand", "bad luck").build();
    }
}
```

`/actuator/health` 새로고침해서 변화 확인.

### 3단계 — Custom Metric

```java
@RestController
public class CounterController {
    private final Counter counter;
    
    public CounterController(MeterRegistry reg) {
        this.counter = Counter.builder("my.requests")
            .description("My API requests")
            .register(reg);
    }
    
    @GetMapping("/hit")
    public String hit() {
        counter.increment();
        return "ok";
    }
}
```

```bash
# 호출
curl .../hit; curl .../hit; curl .../hit

# 메트릭 확인
curl .../actuator/metrics/my.requests
curl .../actuator/prometheus | grep my_requests
```

### 4단계 — Custom Starter 만들기

별도 프로젝트로 위 예제 따라 만들고, 다른 프로젝트에서 의존성 추가 → 동작 확인.

---

## 더 읽어볼 자료

- 📘 『Spring Boot in Action』 — Actuator 챕터
- 🔗 [Spring Boot Reference — Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- 🔗 [Micrometer 문서](https://micrometer.io/docs)
- 🔗 [Boot 3.x — Custom Starter 가이드](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration.custom-starter)
- 🎓 SpringOne — "Observability with Spring Boot 3"
