# Quick Reference — 한 페이지 카드

## 진단 시작 3종 (운영 사고 첫 5분)

```bash
PID=$(jps -l | grep myapp | awk '{print $1}')

# 1. 스레드덤프
jcmd $PID Thread.print > thread.txt

# 2. Heap 상태
jcmd $PID GC.heap_info

# 3. NMT
jcmd $PID VM.native_memory summary
```

---

## 운영 endpoint 자주 쓰는 것

```bash
curl /actuator/health
curl /actuator/health/liveness
curl /actuator/health/readiness

curl /actuator/metrics/jvm.memory.used | jq
curl /actuator/metrics/jvm.gc.pause | jq
curl /actuator/metrics/tomcat.threads.busy | jq
curl /actuator/metrics/hikaricp.connections.active | jq
curl /actuator/metrics/http.server.requests | jq

curl /actuator/heapdump -o heap.hprof
curl /actuator/threaddump | jq '.threads[] | select(.threadState=="BLOCKED")'

# 로거 동적 변경 (재시작 불필요)
curl -X POST /actuator/loggers/com.example.X \
     -H "Content-Type: application/json" \
     -d '{"configuredLevel":"DEBUG"}'
```

---

## 우선순위 진단 순서

```
1. 증상: 응답 안 옴
   ├── /actuator/health → DOWN?
   ├── Thread.print → BLOCKED 대량?
   ├── /actuator/metrics → CPU·Memory?
   └── Last 15분 로그 → 예외?

2. 증상: OOM
   ├── Heap (jmap)? → static 누수, 큰 객체
   ├── Metaspace? → 클래스 누수 (CGLIB, 동적 클래스)
   ├── Direct? → NIO buffer 누수 (Netty)
   └── Native thread? → 스레드 풀 무한

3. 증상: 느림
   ├── /actuator/metrics/http.server.requests → 어느 endpoint?
   ├── /actuator/metrics/jvm.gc.pause → GC 폭주?
   ├── Thread.print → 어디 멈춰있나?
   └── async-profiler → flame graph
```

---

## OOM 종류 8가지

```
java.lang.OutOfMemoryError: Java heap space            ← Heap, static 누수 의심
java.lang.OutOfMemoryError: GC overhead limit exceeded ← GC가 98% 도는데 결과 없음
java.lang.OutOfMemoryError: Metaspace                  ← 클래스 메타 (CGLIB)
java.lang.OutOfMemoryError: Direct buffer memory       ← NIO (Netty leak)
java.lang.OutOfMemoryError: unable to create new native thread  ← OS 스레드 한도
java.lang.OutOfMemoryError: Requested array size exceeds VM limit
java.lang.OutOfMemoryError: Compressed class space
java.lang.OutOfMemoryError: Out of swap space?
```

각각 영역이 다름. **메시지로 진단 시작점 결정**.

---

## JMM × happens-before 빠른 표

| 상황 | 보장 | 방법 |
|---|---|---|
| 한 스레드가 read·write | OK (program order) | - |
| 한 스레드 write → 다른 스레드 read | **보장 안 됨** | volatile / synchronized / AtomicXxx |
| count++ atomic? | **NO** | AtomicXxx 또는 synchronized |
| volatile long/double | atomic O | volatile 키워드 |
| 생성자에서 final | publication 안전 | final 키워드 |
| ThreadPool에 잡힌 객체 | 시작 시 happens-before | submit 전에 객체 완성 |

---

## Spring AOP 함정

| 함정 | 영향 |
|---|---|
| `@Transactional` self-invocation | 트랜잭션 안 먹음 |
| `@Async` self-invocation | 동기 실행됨 |
| `@Transactional` on private | 무시 |
| `@Transactional` on final method | 무시 |
| `final class` | 부팅 실패 (CGLIB) |
| checked exception | 자동 롤백 X (rollbackFor 필요) |
| readOnly + 명시적 flush | flush 됨 (Hibernate) |

---

## @Transactional 권장

```java
// 클래스 단위 기본
@Transactional(rollbackFor = Exception.class)
public abstract class BaseService { ... }

// 조회는 readOnly
@Transactional(readOnly = true)
public List<X> findAll() { ... }

// 별도 트랜잭션
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void audit(...) { ... }

// 커밋 후 외부 호출
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onSaved(SavedEvent e) { kafka.send(...); }
```

---

## ThreadPool 선택 가이드

| 상황 | 추천 |
|---|---|
| 일반 비동기 | ThreadPoolTaskExecutor (Boot 자동) |
| 작업이 많은 blocking I/O | Virtual Thread (JDK 21+) |
| CPU 바운드 | ForkJoinPool (work-stealing) |
| 순서 보장 | newSingleThreadExecutor |
| 외부 API throttle | Semaphore + bounded pool |

**금지**: `Executors.newCachedThreadPool()` (무한 스레드).

---

## GC 결정 트리

```
Heap < 4G ?
├── 응답 민감 → G1 (JDK 17+)
└── 처리량 → Parallel

Heap 4~32G ?
├── STW < 100ms → G1 + Pause goal 100 또는 ZGC
└── 일반 → G1 기본

Heap > 32G → ZGC + ZGenerational
배치 → Parallel
```

---

## JVM 옵션 운영 세트

```bash
java \
  -Xms4g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/heapdumps/ \
  -Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags:filecount=10,filesize=20M \
  -XX:NativeMemoryTracking=summary \
  -XX:StartFlightRecording=disk=true,maxage=1h,maxsize=500m \
  -Djdk.tracePinnedThreads=short \
  -jar app.jar
```

## 컨테이너 (k8s)

```yaml
env:
  - name: JAVA_OPTS
    value: >-
      -XX:+UseG1GC
      -XX:MaxRAMPercentage=75
      -XX:MaxGCPauseMillis=100
      -XX:+HeapDumpOnOutOfMemoryError
      -XX:HeapDumpPath=/var/log/heapdumps/
      -Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags:filecount=10,filesize=20M
      -XX:NativeMemoryTracking=summary
resources:
  limits:
    memory: 4Gi
```

---

## 응급 처치 (사고 골든타임)

```bash
# 1. 증거 확보 (재시작 전)
jcmd $PID Thread.print > thread-$(date +%s).txt
jcmd $PID GC.heap_dump /tmp/heap-$(date +%s).hprof
jcmd $PID JFR.dump filename=/tmp/incident-$(date +%s).jfr
curl /actuator/heapdump -o heap-$(date +%s).hprof
curl /actuator/threaddump > thread-$(date +%s).json

# 2. 로그 보존
cp /var/log/app.log /backup/incident.log

# 3. 그 다음 재시작
```

---

## 면접·운영 단골 질문 10개

1. JVM 메모리 영역 5개 그려보세요
2. G1과 ZGC의 차이는?
3. `@Transactional`이 안 먹는 경우?
4. `synchronized`와 `volatile`의 차이?
5. AtomicLong vs LongAdder?
6. Virtual Thread vs Platform Thread?
7. `ApplicationContext.refresh()` 단계?
8. `@SpringBootApplication`이 하는 일?
9. JIT의 Tiered Compilation?
10. Heap OOM 났을 때 진단 순서?
