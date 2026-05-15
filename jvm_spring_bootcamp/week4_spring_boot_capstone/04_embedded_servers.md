# Day 4 — 내장 서버 (Tomcat / Jetty / Undertow / Reactor Netty)

## 한 줄 요약

Spring Boot의 강점 중 하나는 **내장 서버**. WAR를 만들 필요 없이 `java -jar app.jar`로 실행. 기본 Tomcat이지만 Jetty·Undertow로 교체 가능, WebFlux이면 Reactor Netty.

## 학습 목표

- [ ] Tomcat 내장 메커니즘 (TomcatServletWebServerFactory)
- [ ] 4가지 서버 비교 (Tomcat / Jetty / Undertow / Reactor Netty)
- [ ] 서블릿 vs 리액티브 모델
- [ ] Tomcat 튜닝 핵심 옵션
- [ ] Connection / Thread / Queue 관계
- [ ] graceful shutdown 메커니즘

---

## 내장 서버 — 어떻게 동작하나

WAR 시대:
```
사용자가 WAR 작성 → 서버(Tomcat 등)에 deploy → 서버가 main
```

Boot 시대:
```
사용자가 main 작성 → main이 Tomcat을 라이브러리로 시작 → 서버가 클래스
```

### 내부 흐름

```
SpringApplication.run()
   ↓
ApplicationContext.refresh()
   ↓
ServletWebServerApplicationContext.onRefresh()       ← 서블릿 ApplicationContext
   ↓
createWebServer()
   ↓
TomcatServletWebServerFactory.getWebServer()
   ↓
   ├─ new Tomcat()
   ├─ Connector 설정 (port 8080, NIO 등)
   ├─ Engine, Host, Context 등록
   ├─ DispatcherServlet 매핑
   └─ tomcat.start()
   ↓
ContextRefreshedEvent
```

### 코드로 확인

```java
@RestController
public class ServerInfoController {
    @Autowired ServletWebServerApplicationContext ctx;
    
    @GetMapping("/server")
    public String info() {
        WebServer server = ctx.getWebServer();
        return server.getClass().getName() + " on port " + server.getPort();
    }
}
// → org.springframework.boot.web.embedded.tomcat.TomcatWebServer on port 8080
```

---

## 4가지 내장 서버 비교

### Tomcat (기본)

```gradle
implementation 'org.springframework.boot:spring-boot-starter-web'
// 자동 포함: spring-boot-starter-tomcat
```

| 항목 | 값 |
|---|---|
| 모델 | 서블릿 (스레드 per 요청) |
| NIO | NIO Connector 기본 |
| 친숙도 | 매우 높음 |
| 성능 | 좋음 |

### Jetty

```gradle
implementation('org.springframework.boot:spring-boot-starter-web') {
    exclude module: 'spring-boot-starter-tomcat'
}
implementation 'org.springframework.boot:spring-boot-starter-jetty'
```

| 항목 | 값 |
|---|---|
| 모델 | 서블릿 |
| 시작 시간 | Tomcat보다 약간 빠름 |
| 메모리 | 약간 적음 |
| WebSocket | 가벼움 |

### Undertow

```gradle
implementation('org.springframework.boot:spring-boot-starter-web') {
    exclude module: 'spring-boot-starter-tomcat'
}
implementation 'org.springframework.boot:spring-boot-starter-undertow'
```

| 항목 | 값 |
|---|---|
| 모델 | 서블릿 + Native NIO |
| 성능 | 매우 빠름 |
| WebSocket·HTTP/2 | 우수 |
| 의존성 | 가벼움 |

JBoss/WildFly 출신. 성능 벤치에서 가장 빠른 편.

### Reactor Netty (WebFlux)

```gradle
implementation 'org.springframework.boot:spring-boot-starter-webflux'
```

| 항목 | 값 |
|---|---|
| 모델 | 리액티브 (논블로킹, 적은 스레드) |
| API | `Mono<T>` / `Flux<T>` |
| 학습곡선 | 가파름 |
| 디버깅 | 어려움 |

JDK 21 Virtual Thread가 등장한 후 입지 약화. blocking I/O가 많은 일반 백엔드는 Tomcat + VT가 단순하고 빠를 수 있음.

---

## 서블릿 모델 — Thread per Request

```
요청 1 ──▶ Tomcat Thread 1 ──▶ DispatcherServlet ──▶ Controller (blocking)
                                                    ↓
                                                    DB 호출 (1초 대기)
                                                    ↓
                                                    응답

요청 2 ──▶ Tomcat Thread 2 ──▶ ...
요청 200 ──▶ Tomcat Thread 200
요청 201 ──▶ 큐 대기
```

요청마다 스레드 잡힘. blocking I/O면 대기 동안 스레드 쉼.

### Tomcat 튜닝 옵션

```yaml
server:
  port: 8080
  tomcat:
    threads:
      min-spare: 10                # 평소 idle 유지
      max: 200                     # 최대 (기본 200)
    max-connections: 8192          # OS accept 큐
    accept-count: 100              # max-connections 차면 큐
    connection-timeout: 20000      # client read timeout
    keep-alive-timeout: 60000
  shutdown: graceful
  
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

### 핵심 관계

```
TCP listen 큐 (accept-count)
       │
       ▼
서버 accept (max-connections)
       │
       ▼
Tomcat 스레드 풀 (max)
       │
       ├── 처리 중인 요청
       └── 응답 대기
```

`max=200`인데 부하 1000 req/s · 평균 1초 ─▶ 200개 처리, 800개 대기 → latency 폭증.

### Virtual Thread (Boot 3.2+)

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

`max` 한계가 사라짐 — VT를 무제한 만들 수 있음. blocking I/O 워크로드에서 throughput 폭증.

---

## 리액티브 모델 — 적은 스레드, 비동기

```
Event Loop (CPU 수 = 8개 정도)
       │
       └── 요청 1 → Mono.fromCallable() → DB 호출 (non-blocking I/O)
                                              ↓
                                              [event loop 다른 요청 처리]
                                              ↓
                                              응답 도착 → continuation 실행
```

| 항목 | 서블릿 | 리액티브 |
|---|---|---|
| 스레드 수 | 수백 | 수 (event loop) |
| 메모리/요청 | 1~2MB (스택) | 적음 |
| blocking 코드 | OK | ❌ (다른 요청 막힘) |
| 학습 | 쉬움 | 어려움 |
| 디버깅 | 직관적 | 추적 어려움 |

### 언제 WebFlux 정당화되는가

- **외부 API 호출이 매우 많음** + 응답 시간 길음 + 수만 동시 연결
- **WebSocket·SSE** 대규모
- **수만 동시 long-polling**

대부분의 일반 backend는 **Tomcat + Virtual Thread**가 정답.

---

## graceful shutdown

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

SIGTERM 수신 시 흐름:

```
1. WebServer가 새 connection 거부
2. 처리 중인 요청 완료 대기 (최대 30s)
3. ApplicationContext.close()
4. @PreDestroy 호출
5. JVM 종료
```

### k8s preStop

readiness probe만으로 충분치 않을 수 있음 — k8s가 SIGTERM 보내는 시점과 LB가 endpoint 제거하는 시점이 다름.

```yaml
lifecycle:
  preStop:
    exec:
      command: ["sleep", "10"]    # LB에서 빠질 시간 확보
terminationGracePeriodSeconds: 60
```

---

## DispatcherServlet — 서블릿 모델의 entry point

```
HTTP 요청
   ↓
Tomcat Connector
   ↓
DispatcherServlet (FrontController 패턴)
   ↓
HandlerMapping       ← URL → Controller 매핑
   ↓
HandlerInterceptor   ← 인터셉터 (인증 등)
   ↓
HandlerAdapter       ← 메서드 호출
   ↓
Controller.method()
   ↓
ViewResolver         ← 또는 ResponseBodyAdvice
   ↓
HTTP 응답
```

`@RestController` 메서드 한 호출의 흐름. 각 단계에 끼어들 수 있는 컴포넌트.

---

## 운영 사례

### 사례 1 — Tomcat 풀 고갈

**증상**: `/actuator/metrics/tomcat.threads.busy`가 200 (max). 새 요청 timeout.

**원인**: 외부 API 호출이 30초씩 걸림. 평균 100 req/s → 30초 동안 3000 inflight → 200으로 못 감당.

**조치**:
1. 외부 API timeout 단축 (RestClient `.timeout(Duration.ofSeconds(2))`)
2. `max` 늘리기 (메모리 한계까지)
3. Virtual Thread 활성화
4. Circuit Breaker (Resilience4j)

### 사례 2 — graceful shutdown 안 됨

**증상**: 배포 시 사용자 요청 5xx 응답.

**원인**: `server.shutdown: graceful` 안 설정.

**조치**: 위 yml + readiness 응답·preStop 조합.

### 사례 3 — Reactor Netty에서 blocking 코드

```java
@GetMapping("/")
public Mono<String> get() {
    return Mono.fromCallable(() -> {
        return jdbcTemplate.query(...);    // ❌ blocking
    });    // .subscribeOn(Schedulers.boundedElastic())이 없으면 event loop 막힘
}
```

조치: `subscribeOn(boundedElastic())` 또는 R2DBC로 마이그레이션. 또는 VT로 전환.

---

## 실습 (Hands-on)

### 1단계 — 서버 종류 바꿔보기

```gradle
// Jetty
implementation('org.springframework.boot:spring-boot-starter-web') {
    exclude module: 'spring-boot-starter-tomcat'
}
implementation 'org.springframework.boot:spring-boot-starter-jetty'
```

```java
@GetMapping("/srv")
public String srv() {
    return ctx.getWebServer().getClass().getName();
}
```

`Jetty...WebServer`, `Tomcat...WebServer`, `Undertow...WebServer` 확인.

### 2단계 — Tomcat 풀 상태 보기

```yaml
management:
  endpoints:
    web:
      exposure:
        include: metrics
```

```bash
curl /actuator/metrics/tomcat.threads.busy
curl /actuator/metrics/tomcat.threads.config.max
curl /actuator/metrics/tomcat.sessions.active.current
```

### 3단계 — 부하 + Tomcat 메트릭 추적

```java
@GetMapping("/slow")
public String slow() throws Exception {
    Thread.sleep(2000);
    return "ok";
}
```

부하 도구로 동시 50:

```bash
# wrk 또는 hey
hey -n 1000 -c 50 http://localhost:8080/slow
```

그동안 메트릭 모니터:

```bash
watch -n 1 'curl -s .../actuator/metrics/tomcat.threads.busy | jq'
```

### 4단계 — Virtual Thread 활성화 비교

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

같은 부하 → throughput·latency 비교.

### 5단계 — Graceful Shutdown 테스트

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

```bash
# 1. /slow 호출 후 곧바로 종료
curl http://localhost:8080/slow &
sleep 0.5
# Ctrl+C 또는 PID에 SIGTERM
kill <pid>

# 응답: 정상 완료
# 로그: "Graceful shutdown" 메시지
```

---

## 더 읽어볼 자료

- 📘 『Spring Boot in Action』 — 서블릿/리액티브 챕터
- 📘 『Reactive Spring』 (Josh Long) — WebFlux 깊이
- 🔗 [Tomcat — Connector Configuration](https://tomcat.apache.org/tomcat-10.1-doc/config/http.html)
- 🔗 [Reactor Netty 문서](https://projectreactor.io/docs/netty/release/reference/index.html)
- 🔗 [Spring Boot — Graceful Shutdown](https://docs.spring.io/spring-boot/docs/current/reference/html/web.html#web.graceful-shutdown)
- 🎓 SpringOne — "Choosing the Right Web Stack"
