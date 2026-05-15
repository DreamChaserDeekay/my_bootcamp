# JVM·Spring 트러블슈팅 플레이북

증상별 진단 절차. 운영서에서 바로 따라할 수 있게 구체적 명령어 적었음.

---

## 원칙

1. **재시작 전 증거 확보** — 사고는 재현 어려움
2. **측정만, 추측 X**
3. **변경 한 번에 하나**
4. **재측정으로 확인**
5. **사고 보고서 작성**

---

## 시나리오 1: "OOM 발생"

### 진단

```bash
# 1. 어느 영역?
tail /var/log/app.log | grep "OutOfMemoryError"
# "Java heap space" → Heap
# "Metaspace"       → 클래스 누수
# "Direct buffer"   → NIO
# "unable to create native thread" → OS 스레드

# 2. (Heap이면) 자동 덤프 있나?
ls /var/log/heapdumps/*.hprof

# 3. 없으면 즉시 생성
jcmd $PID GC.heap_dump /tmp/heap.hprof

# 4. NMT (Heap 외 영역)
jcmd $PID VM.native_memory summary
```

### 분석

```bash
# Eclipse MAT
MemoryAnalyzer.exe /tmp/heap.hprof
# → Leak Suspects Report

# OQL 쿼리
SELECT * FROM byte[] s WHERE s.@length > 1048576    -- 1MB+ byte[]
SELECT * FROM java.util.HashMap m WHERE m.size > 10000
```

### 조치

| 원인 | 단기 | 장기 |
|---|---|---|
| static 누수 | Heap 증가, 재시작 주기화 | static 컬렉션 제거 |
| 큰 캐시 | LRU 도입 | Caffeine·Redis로 외부화 |
| Metaspace | -XX:MaxMetaspaceSize 증가 | 동적 클래스 생성 점검 |
| Direct | -XX:MaxDirectMemorySize 증가 | release() 추가 |
| Native thread | ulimit 증가 | 스레드 풀 제한 |

---

## 시나리오 2: "Full GC 폭주"

### 진단

```bash
# GC 상태 실시간
jstat -gcutil $PID 1000 30
#  S0   S1     E      O      M     CCS    YGC YGCT  FGC FGCT  GCT
#   0   0    45.2   91.7    95     90      24  0.46    3  6.8  7.26
#                    ^^^^                              ^^^
#                    Old 91%                           Full GC 자주

# GC 로그 분석 (gceasy.io 또는)
grep "Full GC" /var/log/gc.log | tail -20

# Humongous?
grep "G1 Humongous" /var/log/gc.log | head -5
```

### 원인 추론

| 시그널 | 원인 |
|---|---|
| Old 사용률 단조증가 | 메모리 누수 |
| Old 사용률 변동 + Mixed GC 자주 | Old 회수 정상이지만 부족 |
| Full GC만 자주, Young 정상 | Humongous 의심 |
| 짧은 시간에 폭주 | 큰 배치 작업 시작 |

### 조치

```bash
# 1. 힙덤프 분석 (누수 확인)
jcmd $PID GC.heap_dump /tmp/heap.hprof

# 2. Heap 늘리기 (응급)
# 단, 누수면 시간 미루기일 뿐

# 3. region 크기 조정 (humongous 우회)
-XX:G1HeapRegionSize=32m

# 4. Mixed GC 조기 시작
-XX:InitiatingHeapOccupancyPercent=35

# 5. JDK 업그레이드 (가장 좋은 GC 튜닝일 수 있음)
```

---

## 시나리오 3: "Tomcat 스레드 고갈"

### 진단

```bash
# 메트릭
curl /actuator/metrics/tomcat.threads.busy
curl /actuator/metrics/tomcat.threads.config.max
# busy == max → 풀 가득

# 스레드덤프
jcmd $PID Thread.print | grep -c "http-nio"
jcmd $PID Thread.print | grep -A 5 "http-nio-8080-exec-1"
# → 대부분 어디서 멈춰있나?
```

### 패턴별 원인

| 스택 | 원인 |
|---|---|
| `SocketInputStream.socketRead0` | 외부 API 응답 대기 |
| `HikariPool.getConnection` | DB 풀 고갈 |
| `Thread.sleep` | 명시적 대기 |
| `Object.wait` (외부 lock) | 잠금 경합 |
| `JNI/Native` | 네이티브 코드 hang |

### 조치

```yaml
# 1. timeout 추가
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 1000
            read-timeout: 3000

# 2. 풀 크기 증가
server:
  tomcat:
    threads:
      max: 400

# 3. Virtual Thread (Boot 3.2+)
spring:
  threads:
    virtual:
      enabled: true
```

```java
// 4. Semaphore로 외부 호출 throttle
@Service
public class ApiClient {
    private final Semaphore sem = new Semaphore(50);
    
    public Response call() throws InterruptedException {
        sem.acquire();
        try { return external.call(); }
        finally { sem.release(); }
    }
}
```

---

## 시나리오 4: "HikariCP 풀 고갈"

### 진단

```bash
# 메트릭
curl /actuator/metrics/hikaricp.connections.active
curl /actuator/metrics/hikaricp.connections.pending
curl /actuator/metrics/hikaricp.connections.timeout

# 로그 (leak detection 켜면)
grep "Connection leak detection" /var/log/app.log
```

### 흔한 원인

```java
// 1. 트랜잭션 안 닫음 (Spring이 알아서지만 수동 처리 시 위험)
// 2. ResultSet/Statement 안 닫음
// 3. 트랜잭션 안에 외부 I/O (호출 시간만큼 connection 점유)
@Transactional
public void slowMethod() {
    repo.save(...);
    httpClient.call();    // ❌ 5초 → connection 5초 점유
}

// 4. 비동기 작업이 트랜잭션을 commit 없이 이어감
```

### 조치

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30
      leak-detection-threshold: 60000   # 60초 안 반환되면 stacktrace
      validation-timeout: 5000
```

```java
// 트랜잭션 안에서 외부 호출 제거
@Service
public class OrderService {
    @Transactional
    public Order save(Order o) {
        return repo.save(o);     // 트랜잭션 짧게
    }
    
    public void place(Order o) {
        Order saved = save(o);
        httpClient.notify(saved);    // 트랜잭션 밖
    }
}
```

---

## 시나리오 5: "데드락"

### 진단

```bash
jstack $PID > thread.txt
grep -A 30 "Found one Java-level deadlock" thread.txt
```

자동으로 데드락 분석:

```
Found one Java-level deadlock:
=============================
"Thread-A":
  waiting to lock monitor 0x000... (object 0x..., a com.example.X),
  which is held by "Thread-B"
"Thread-B":
  waiting to lock monitor 0x000... (object 0x..., a com.example.Y),
  which is held by "Thread-A"
```

### 원인 패턴

```java
// 1. 잠금 순서 비일관
synchronized (A) { synchronized (B) { ... } }    // Thread 1
synchronized (B) { synchronized (A) { ... } }    // Thread 2

// 2. 잠금 + DB 잠금 섞임
synchronized (this) {
    db.update(...);    // DB row lock도 잡음
}
// 다른 곳에서 DB row lock 잡고 this의 synchronized 시도 → DB 측 데드락 가능
```

### 조치

```java
// 1. 잠금 순서 일관성 (ID 작은 것 먼저 등)
public void transfer(Account a, Account b) {
    Account first = a.id < b.id ? a : b;
    Account second = a.id < b.id ? b : a;
    synchronized (first) {
        synchronized (second) {
            // ...
        }
    }
}

// 2. tryLock with timeout
ReentrantLock lock = new ReentrantLock();
if (lock.tryLock(1, TimeUnit.SECONDS)) {
    try { ... } finally { lock.unlock(); }
} else {
    throw new BusinessException("busy");
}

// 3. 잠금 범위 축소 (외부 호출 빼냄)
synchronized (this) {
    // ... 짧은 작업만
}
externalCall();  // 잠금 밖
```

---

## 시나리오 6: "@Transactional 안 먹음"

### 증상

- `repo.save()`가 동작은 하는데 롤백이 안 됨
- 또는 트랜잭션 active가 false로 출력
- 또는 @TransactionalEventListener afterCommit가 안 호출됨

### 진단

```java
@Transactional
public void method() {
    boolean active = TransactionSynchronizationManager.isActualTransactionActive();
    System.out.println("active: " + active);
}
```

```yaml
# 트랜잭션 로그
logging.level.org.springframework.transaction.interceptor: TRACE
```

콘솔에 "Don't need to create transaction" 메시지면 self-invocation.

### 원인·조치

| 원인 | 조치 |
|---|---|
| Self-invocation | 클래스 분리 |
| Private 메서드 | public으로 |
| Final 메서드 | final 제거 |
| Final 클래스 | 인터페이스 추가 또는 final 제거 |
| Checked exception | `rollbackFor = Exception.class` |
| 같은 클래스 안에서 @Transactional 메서드 호출 | 별도 Service |

---

## 시나리오 7: "응답 느려졌어요"

### 진단

```bash
# 1. 어디서 느림?
curl /actuator/metrics/http.server.requests | jq

# 2. CPU 사용량
top -p $PID
# 또는 jstat -gc $PID 1000 30 — GC 영향?

# 3. CPU 폭주 스레드
top -H -p $PID
# 가장 CPU 먹는 TID 확인
printf '%x\n' <TID>     # 16진수로
jstack $PID | grep -A 30 "nid=0x<TID>"

# 4. flame graph
./profiler.sh -d 30 -f cpu.html $PID

# 5. DB? 외부 API?
# /actuator/metrics/hikaricp.connections.usage
# 외부 호출 로그 분석
```

### 흔한 원인

| 원인 | 시그널 |
|---|---|
| GC 폭주 | jstat의 FGCT 증가 |
| DB 슬로우 쿼리 | hikaricp.usage 길어짐 |
| 외부 API 느림 | http.client.requests latency |
| 잠금 경합 | jstack에 BLOCKED 대량 |
| 메모리 부족 → swap | OS sar / vmstat |
| 풀 고갈 | tomcat.threads.busy == max |

---

## 시나리오 8: "Spring Boot 부팅 5분"

### 진단

```yaml
spring:
  main:
    log-startup-info: true
```

```bash
./gradlew bootRun --args='--debug' > startup.log 2>&1

# 어느 단계?
grep "Started\|seconds" startup.log | tail -20
```

### Actuator startup

```java
// 코드 추가
public static void main(String[] args) {
    SpringApplication app = new SpringApplication(App.class);
    app.setApplicationStartup(new BufferingApplicationStartup(2048));
    app.run(args);
}
```

```bash
curl /actuator/startup | jq '.timeline.events | sort_by(.duration) | reverse | .[0:20]'
```

가장 오래 걸린 단계 top 20.

### 흔한 원인

- Flyway/Liquibase 마이그레이션
- `@PostConstruct`에서 외부 호출
- ComponentScan 범위 너무 큼
- JPA 엔티티 매우 많음

---

## 시나리오 9: "k8s에서 OOMKilled"

### 진단

```bash
# k8s 이벤트
kubectl describe pod my-pod
# Reason: OOMKilled

# JVM 안에선 OOM 없었나?
kubectl logs my-pod | grep OutOfMemoryError
# 없으면 → Heap 외 영역 또는 컨테이너 메모리 추정 실패
```

### 원인

- Heap + Metaspace + Direct + Stack 합이 container limit 초과
- `-Xmx`만 설정하고 나머지 무시
- Direct Memory 누수 (Netty)

### 조치

```yaml
# 1. Heap 외 메모리도 고려한 설정
env:
  - name: JAVA_OPTS
    value: -XX:MaxRAMPercentage=70
# = container의 70%만 Heap. 30%는 Metaspace/Direct/Stack/Native

# 2. NMT로 추적
-XX:NativeMemoryTracking=summary
# jcmd $PID VM.native_memory summary

# 3. Netty leak detection (dev/staging)
io.netty.leakDetection.level: PARANOID
```

---

## 시나리오 10: "graceful shutdown 안 됨"

### 증상

배포 시 사용자 요청 5xx 응답.

### 진단

```bash
# SIGTERM 후 로그
grep "graceful\|shutdown" /var/log/app.log
```

### 조치

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

```yaml
# k8s
lifecycle:
  preStop:
    exec:
      command: ["sh", "-c", "sleep 10"]    # LB에서 빠질 시간
terminationGracePeriodSeconds: 60
```

readiness probe가 SIGTERM 후 즉시 DOWN으로 전환되도록 (Boot 자동).

---

## 자기 노트 (추가)

본인 환경에서 마주친 사건을 같은 형식으로:

```markdown
### 2026-MM-DD — 사고 제목

- **증상**: 
- **진단 단계**: 
- **원인**: 
- **조치**: 
- **재발 방지**: 
- **학습**: 
```
