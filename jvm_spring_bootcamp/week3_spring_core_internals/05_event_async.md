# Day 5 — Event · Async · Scheduler

## 한 줄 요약

Spring은 `ApplicationEvent`로 컨테이너 내부 이벤트를 전파하고, `@Async`로 비동기 실행하며, `@Scheduled`로 주기 실행한다. 모두 같은 `TaskExecutor` 추상 위에 구축. **commonPool 함정**과 **`@Async` self-invocation 함정**을 알면 안전하게 쓸 수 있다.

## 학습 목표

- [ ] `ApplicationEvent`·`@EventListener`로 이벤트 발행·수신을 한다
- [ ] `@TransactionalEventListener`의 phase를 안다 (BEFORE_COMMIT, AFTER_COMMIT 등)
- [ ] `@Async`의 동작과 self-invocation 함정
- [ ] TaskExecutor 종류와 설정
- [ ] `@Scheduled`의 두 모드 (fixedDelay vs fixedRate vs cron)
- [ ] Spring Boot 3.2+ Virtual Thread 통합

---

## ApplicationEvent — 컨테이너 안의 이벤트 버스

```java
// 1. 이벤트 정의 (POJO도 가능, JDK 16+)
public class OrderPlacedEvent {
    private final Order order;
    public OrderPlacedEvent(Order o) { this.order = o; }
    public Order getOrder() { return order; }
}

// 2. 발행
@Service
public class OrderService {
    @Autowired ApplicationEventPublisher publisher;
    
    @Transactional
    public void place(Order o) {
        repo.save(o);
        publisher.publishEvent(new OrderPlacedEvent(o));
    }
}

// 3. 수신
@Component
public class NotifyOnOrder {
    @EventListener
    public void onOrder(OrderPlacedEvent e) {
        emailService.send(e.getOrder());
    }
}
```

### 기본 동작 — 동기·같은 트랜잭션

```java
@Transactional
public void place(Order o) {
    repo.save(o);
    publisher.publishEvent(new OrderPlacedEvent(o));    // ← 같은 스레드, 같은 트랜잭션
    // ↑ Listener의 emailService.send()가 여기서 동기 실행
    // 예외 던지면 전체 롤백
}
```

→ **기본은 동기**. ApplicationEvent ≠ 비동기.

### @TransactionalEventListener

```java
@Component
public class NotifyOnOrder {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrder(OrderPlacedEvent e) {
        // 트랜잭션 커밋 후에만 실행
        emailService.send(e.getOrder());
    }
}
```

phase 선택:

| phase | 언제 |
|---|---|
| `BEFORE_COMMIT` | flush 후 commit 직전 |
| `AFTER_COMMIT` (기본) | commit 성공 후 |
| `AFTER_ROLLBACK` | 롤백 후 |
| `AFTER_COMPLETION` | commit 또는 rollback 후 |

**AFTER_COMMIT**이 가장 유용 — DB 변경이 확정된 후 외부 시스템 호출.

```java
@Transactional
public void place(Order o) {
    repo.save(o);
    publisher.publishEvent(new OrderPlacedEvent(o));
}
// → 1. repo.save() ... 
// → 2. publishEvent (아직 Listener 안 호출)
// → 3. 메서드 종료, AOP가 commit
// → 4. AFTER_COMMIT Listener 실행 (commit 성공 시)
```

> Day 4의 `TransactionSynchronizationManager.registerSynchronization`과 같은 메커니즘. 어노테이션 버전이 더 깔끔.

---

## @Async — 비동기 실행

```java
@Service
public class EmailService {
    @Async
    public void send(String to, String body) {
        // 이 호출은 즉시 반환, 별도 스레드에서 실행
    }
}

@SpringBootApplication
@EnableAsync                            // ← 필수
public class App { ... }
```

### 작동 원리

```
client.send(to, body)
   ↓
EmailService$$Proxy.send()              ← AOP 프록시
   ↓
AsyncExecutionInterceptor               ← @Async 어드바이스
   ↓
TaskExecutor에 작업 제출
   ↓ (즉시 반환)
client (계속 진행)

    별도 스레드에서:
       └─▶ realEmailService.send(to, body)
```

### 반환 타입

```java
@Async
public void fireAndForget() { ... }                 // void

@Async
public CompletableFuture<Result> fetch() {          // CompletableFuture
    return CompletableFuture.completedFuture(...);
}

@Async
public Future<Result> oldStyle() { ... }            // Future (옛 방식)
```

void는 결과·예외 받을 수 없음. **CompletableFuture 권장**.

### TaskExecutor 설정

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(10);
        exec.setMaxPoolSize(50);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("async-");
        exec.setRejectedExecutionHandler(new CallerRunsPolicy());
        exec.initialize();
        return exec;
    }
    
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> 
            log.error("Async error in {}", method, ex);
    }
}
```

Spring Boot 3.2+에선 `spring.threads.virtual.enabled: true`로 Virtual Thread 사용.

### @Async 함정 — Self-Invocation

```java
@Service
public class A {
    public void outer() {
        inner();        // ❌ self-call → 동기 실행!
    }
    
    @Async
    public void inner() { ... }
}
```

@Transactional과 같은 이유. AOP 프록시 우회. 클래스 분리.

### 다른 함정 — 기본 Executor

`@EnableAsync` 후 `AsyncConfigurer` 없으면:
1. Spring Boot 2.x: `SimpleAsyncTaskExecutor` — **풀 없음**, 매 호출마다 새 스레드 → 부하 폭주 시 OOM
2. Spring Boot 3.0+: `ApplicationTaskExecutor` (사실은 ThreadPoolTaskExecutor 기본값) — 적당

> 반드시 명시적 Executor 설정 권장.

---

## @Scheduled — 주기적 실행

```java
@Component
public class CleanupJob {
    
    // 매 1시간
    @Scheduled(fixedRate = 3600_000)
    public void cleanup() { ... }
    
    // 마지막 실행 종료 1시간 후
    @Scheduled(fixedDelay = 3600_000)
    public void compact() { ... }
    
    // cron 표현식
    @Scheduled(cron = "0 0 2 * * ?")     // 매일 새벽 2시
    public void nightlyBatch() { ... }
}

@SpringBootApplication
@EnableScheduling                         // ← 필수
public class App { ... }
```

### fixedRate vs fixedDelay

```
fixedRate = 5000:
   0s     5s     10s    15s
   ●──────●──────●──────●
   (이전 종료 무관, 5초마다 시작)
   (이전이 6초 걸리면? 즉시 시작 또는 누적)

fixedDelay = 5000:
   0s     6s     11s    16s
   ●──────●──────●──────●
   (이전 종료 후 5초 대기 후 시작)
```

**일반적으로 fixedDelay가 안전** — 작업이 길어져도 겹치지 않음.

### Cron — Spring 표현식

```
초 분 시 일 월 요일

"0 0 2 * * ?"        매일 02:00:00
"0 */15 * * * *"     매 15분
"0 0 9-18 * * MON-FRI"  평일 9-18시 매시 정각
```

> Quartz cron(5필드)과 다름. Spring은 6필드(초 포함).

### Scheduler ThreadPool

```yaml
# application.yml
spring:
  task:
    scheduling:
      pool:
        size: 5         # 기본 1 (단일 스레드)
```

`@Scheduled` 메서드가 여러 개고 시간 겹치면 충돌. 풀 크기 늘려야 동시 실행.

### 클러스터 환경

같은 `@Scheduled`가 모든 인스턴스에서 도는 문제. 해결:
- **ShedLock** (라이브러리) — DB에 lock 잡고 한 인스턴스만 실행
- 또는 **Quartz** (clusterable)
- 또는 외부 스케줄러 (k8s CronJob)

---

## TaskExecutor 계층

```
   Executor (JDK)
       │
       ▼
   AsyncTaskExecutor (Spring, async + Future)
       │
       ▼
   TaskExecutor (Spring abstraction)
       │
       ├── ThreadPoolTaskExecutor    ← 가장 흔함
       ├── SimpleAsyncTaskExecutor   ← 풀 없음, 위험
       ├── SyncTaskExecutor          ← 호출자 스레드, 테스트용
       └── ConcurrentTaskExecutor    ← JDK ExecutorService wrapping
```

### ThreadPoolTaskExecutor 설정

```java
ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
exec.setCorePoolSize(10);             // 평소 유지
exec.setMaxPoolSize(50);              // 최대
exec.setQueueCapacity(100);           // 큐 크기
exec.setKeepAliveSeconds(60);
exec.setAllowCoreThreadTimeOut(false);
exec.setThreadNamePrefix("app-async-");
exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
exec.setWaitForTasksToCompleteOnShutdown(true);
exec.setAwaitTerminationSeconds(30);
exec.initialize();
```

Spring Boot에서는 `application.yml`로:

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 10
        max-size: 50
        queue-capacity: 100
      thread-name-prefix: app-async-
```

이 풀이 `@Async`·`ApplicationEventMulticaster`·`SimpleAsyncTaskScheduler` 등의 기본.

---

## 운영 사례

### 사례 1 — @TransactionalEventListener AFTER_COMMIT으로 Kafka 보냄

```java
@Service
public class OrderService {
    @Transactional
    public void place(Order o) {
        repo.save(o);
        publisher.publishEvent(new OrderPlacedEvent(o));
    }
}

@Component
public class KafkaPublisher {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrder(OrderPlacedEvent e) {
        kafkaTemplate.send("orders", e.getOrder());
    }
}
```

**왜?**: 트랜잭션 롤백되면 메시지 안 보냄. 이중기장 방지의 기본 패턴.

> 그러나 commit 후 Kafka 전송 실패하면 메시지 손실. 완벽 해결은 **Transactional Outbox 패턴**.

### 사례 2 — @Async가 안 먹어요

```java
@Service
public class A {
    public void outer() {
        inner();          // ❌ self-call → 동기
    }
    
    @Async
    public void inner() { ... }
}
```

해결: 클래스 분리.

### 사례 3 — Scheduler 중복 실행 (k8s 다중 인스턴스)

```java
// 3개 인스턴스 모두 @Scheduled 실행 → 3번 cleanup
```

ShedLock으로 한 번만:

```java
@Scheduled(cron = "0 0 2 * * ?")
@SchedulerLock(name = "cleanup", lockAtMostFor = "PT5M")
public void cleanup() { ... }
```

DB·Redis·ZooKeeper 등에 lock. 한 인스턴스만 실행.

---

## 실습 (Hands-on)

### 1단계 — Event 흐름 추적

```java
public record OrderEvent(String id, long amount) {}

@Service
public class OrderService {
    @Autowired ApplicationEventPublisher publisher;
    
    @Transactional
    public void place(String id, long amount) {
        System.out.println("1. place() — saving");
        // repo.save
        System.out.println("2. publishing event");
        publisher.publishEvent(new OrderEvent(id, amount));
        System.out.println("3. place() — done");
    }
}

@Component
public class Listeners {
    @EventListener
    public void sync(OrderEvent e) {
        System.out.println("  SYNC: " + e);
    }
    
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void beforeCommit(OrderEvent e) {
        System.out.println("  BEFORE_COMMIT: " + e);
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(OrderEvent e) {
        System.out.println("  AFTER_COMMIT: " + e);
    }
}
```

예상 출력:
```
1. place() — saving
2. publishing event
  SYNC: OrderEvent[...]              ← 발행 즉시 실행
3. place() — done
  BEFORE_COMMIT: OrderEvent[...]      ← commit 직전
  AFTER_COMMIT: OrderEvent[...]       ← commit 후
```

### 2단계 — @Async 동작 확인

```java
@Service
public class AsyncService {
    @Async
    public CompletableFuture<String> fetch(String url) throws Exception {
        Thread.sleep(2000);
        return CompletableFuture.completedFuture(
            "result from " + url + " on " + Thread.currentThread().getName());
    }
}

@RestController
class TestC {
    @Autowired AsyncService svc;
    
    @GetMapping("/async")
    public List<String> test() throws Exception {
        long t = System.currentTimeMillis();
        var f1 = svc.fetch("a");
        var f2 = svc.fetch("b");
        var f3 = svc.fetch("c");
        
        List<String> results = List.of(f1.get(), f2.get(), f3.get());
        long elapsed = System.currentTimeMillis() - t;
        return List.of("elapsed=" + elapsed + "ms", results.toString());
    }
}
```

예상: `elapsed ≈ 2000ms` (3개 동시 → 2초). 동기였으면 6초.

### 3단계 — @Async self-invocation 함정 재현

```java
@Service
public class SelfDemo {
    public void outer() {
        long t = System.currentTimeMillis();
        inner();
        inner();
        inner();
        System.out.println("elapsed: " + (System.currentTimeMillis() - t) + "ms");
    }
    
    @Async
    public void inner() {
        try { Thread.sleep(1000); } catch (Exception e) {}
    }
}
```

예상: `elapsed: 3000ms` (직렬). @Async 안 먹음.

→ 클래스 분리 후 다시 → `elapsed: ~1000ms`.

### 4단계 — @Scheduled 활용

```java
@Component
public class StatJob {
    private final AtomicInteger counter = new AtomicInteger();
    
    @Scheduled(fixedDelay = 5000)
    public void heartbeat() {
        System.out.println("beat " + counter.incrementAndGet() + 
            " on " + Thread.currentThread().getName());
    }
    
    @Scheduled(cron = "0 * * * * *")    // 매 분
    public void minutely() {
        System.out.println("minute mark at " + LocalTime.now());
    }
}
```

`@EnableScheduling` 필요.

---

## 더 읽어볼 자료

- 📘 『Spring in Action』 — 이벤트, 비동기, 스케줄러 챕터
- 🔗 [Spring Reference — Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- 🔗 [Spring Reference — Async](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- 🔗 [ShedLock](https://github.com/lukas-krecan/ShedLock) — 분산 스케줄러
- 🔗 [Transactional Outbox 패턴](https://microservices.io/patterns/data/transactional-outbox.html)
- 🎓 김영한 — Spring 이벤트 처리
