# Day 4 — `java.util.concurrent` 내부

## 한 줄 요약

`java.util.concurrent` 패키지는 **AQS(AbstractQueuedSynchronizer)**와 **CAS(Compare-And-Swap)**를 기반으로 잠금·자료구조를 구현. ReentrantLock·Semaphore·CountDownLatch는 AQS의 변형이고, ConcurrentHashMap·AtomicXxx는 CAS의 활용.

## 학습 목표

- [ ] CAS(Compare-And-Swap)의 의미와 ABA 문제를 안다
- [ ] AQS의 기본 구조 (state + FIFO 대기 큐)를 설명한다
- [ ] ReentrantLock·Semaphore·CountDownLatch가 AQS 기반임을 안다
- [ ] ConcurrentHashMap의 bin/seg 구조와 lock striping을 안다
- [ ] AtomicLong vs LongAdder의 차이를 안다
- [ ] CompletableFuture의 callback 체인 동작을 안다

---

## CAS — 동시성의 원자

```java
class AtomicLong {
    private volatile long value;
    
    public long incrementAndGet() {
        long expected;
        long updated;
        do {
            expected = value;
            updated = expected + 1;
        } while (!compareAndSet(expected, updated));    // CAS
        return updated;
    }
}
```

**Compare-And-Swap**:
> "현재 값이 X일 때만 Y로 바꿔라. 그 사이 다른 스레드가 바꿨으면 실패."

CPU 명령(`lock cmpxchg` on x86)으로 한 번에 처리. 락 없이 원자적 업데이트.

### ABA 문제

```
시간 1: 값 = A
시간 2: T1이 A 읽음, 작업 중
시간 3: T2가 A → B → A로 바꿈
시간 4: T1의 CAS(expected=A, new=C) → 성공 (그러나 잘못된 시점)
```

T1은 "아직 A네, 안 바뀌었네" 생각하지만 실은 한 바퀴 돌아온 A. 단순 CAS로는 못 잡음.

**해결**: 버전 카운터 추가 (`AtomicStampedReference`).

```java
AtomicStampedReference<Node> ref = new AtomicStampedReference<>(node, 0);

int stamp = ref.getStamp();
Node val = ref.getReference();
// 작업 ...
ref.compareAndSet(val, newNode, stamp, stamp + 1);
// → 값+버전 모두 같아야 성공
```

---

## AQS — AbstractQueuedSynchronizer

`java.util.concurrent.locks`의 잠금들이 모두 이걸 상속.

### 핵심 구조

```
┌─────────────────────────────────┐
│ AQS                              │
│                                  │
│ int state          ← 잠금 상태   │
│ Node head          ← 대기 큐     │
│ Node tail                        │
│ Thread owner       ← 누가 잡았나 │
└─────────────────────────────────┘

대기 큐 (FIFO):
    head ─▶ Node ─▶ Node ─▶ Node ─▶ tail
            Thread A  Thread B  Thread C
            (대기)    (대기)    (대기)
```

### state 의미는 서브클래스가 정의

| 잠금 | state 의미 |
|---|---|
| **ReentrantLock** | 0 = unlocked, N = 잠긴 횟수 (재진입) |
| **Semaphore** | 남은 permit 수 |
| **CountDownLatch** | 남은 count |
| **ReentrantReadWriteLock** | 상위 16bit = read count, 하위 16bit = write count |

### acquire 흐름 (간략화)

```java
public void acquire(int arg) {
    if (!tryAcquire(arg)) {            // 1. CAS로 즉시 시도
        addToWaitQueue();              // 2. 실패 시 큐에 추가
        while (notHead() || !tryAcquire()) {
            LockSupport.park();        // 3. CPU 양보
        }
    }
}

public void release(int arg) {
    if (tryRelease(arg)) {
        unparkNext();                  // 4. 대기 큐 다음 스레드 깨움
    }
}
```

---

## ReentrantLock — AQS 활용 예

```java
public class ReentrantLock {
    private final Sync sync = new NonfairSync();   // AQS 서브클래스
    
    abstract static class Sync extends AbstractQueuedSynchronizer {
        @Override
        protected boolean tryAcquire(int acquires) {
            Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (compareAndSetState(0, acquires)) {   // CAS!
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (current == getExclusiveOwnerThread()) {
                setState(c + acquires);                  // 재진입
                return true;
            }
            return false;
        }
        
        @Override
        protected boolean tryRelease(int releases) {
            int c = getState() - releases;
            boolean free = (c == 0);
            if (free) setExclusiveOwnerThread(null);
            setState(c);
            return free;
        }
    }
    
    public void lock() { sync.acquire(1); }
    public void unlock() { sync.release(1); }
}
```

핵심: **CAS로 state 변경 + 큐에서 다음 스레드 깨우기**.

### 공정 vs 비공정

```java
new ReentrantLock(true);   // 공정 — 큐 순서대로
new ReentrantLock(false);  // 비공정 — 새로 온 스레드도 한 번 시도
```

**비공정이 throughput 좋음** — 깨우기 비용 적음. 하지만 starvation 가능.

---

## ConcurrentHashMap — 진화

### JDK 7 — Segment 기반

Map을 16개 segment로 분할. 각 segment에 별도 ReentrantLock. 16 thread 동시 쓰기 가능.

### JDK 8+ — Node + bin 기반

```
ConcurrentHashMap
├── Node[] table          ← 배열 (bin 단위)
└── 각 bin마다:
    - bin이 비어있으면: CAS로 추가 (락 없이!)
    - bin이 비어있지 않으면: bin의 head를 synchronized
    - 너무 길면 → 트리로 변환 (red-black tree)
```

**lock striping이 더 세밀**. bin 단위(기본 16개)에서 노드 수에 따라.

### compute vs put

```java
ConcurrentHashMap<String, Long> map = new ConcurrentHashMap<>();

// ❌ race
Long current = map.get("k");
if (current == null) map.put("k", 1L);
else map.put("k", current + 1);

// ✅ atomic
map.compute("k", (key, val) -> val == null ? 1L : val + 1);

// 또는
map.merge("k", 1L, Long::sum);
```

`compute`/`merge`/`putIfAbsent`/`replace` 같은 메서드가 atomic. **get-then-put을 절대 쓰지 마라**.

---

## AtomicLong vs LongAdder

```java
// AtomicLong — 단일 변수, 모든 스레드 경합
AtomicLong total = new AtomicLong();
total.incrementAndGet();         // CAS → 충돌 시 재시도

// LongAdder — 스레드별 cell 분산
LongAdder total = new LongAdder();
total.increment();               // 자기 cell에 +1 → 충돌 없음
total.sum();                     // 모든 cell 합 (느림, 가끔만)
```

| | AtomicLong | LongAdder |
|---|---|---|
| 충돌 적음 | 빠름 | 비슷 |
| 충돌 많음 | 느림 (재시도 폭주) | **훨씬 빠름** |
| sum() 정확도 | 항상 정확 | concurrent 호출 시 근사 |
| 메모리 | 적음 | 스레드 수만큼 cell |

**카운터·통계는 거의 항상 LongAdder**. 결과값을 정확히 다른 곳에서 비교해야 한다면 AtomicLong.

---

## CountDownLatch · CyclicBarrier · Semaphore · Phaser

| | 용도 |
|---|---|
| **CountDownLatch** | N개 작업이 끝날 때까지 대기 (1회용) |
| **CyclicBarrier** | N개 스레드가 모일 때까지 (재사용 가능) |
| **Semaphore** | 최대 N개 동시 접근 |
| **Phaser** | 동적 멤버, 다단계 |

### CountDownLatch 예

```java
CountDownLatch latch = new CountDownLatch(3);

for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        doWork();
        latch.countDown();
    }).start();
}

latch.await();                   // 3개가 다 countDown 할 때까지 대기
System.out.println("all done");
```

### Semaphore 예 — 동시 호출 제한

```java
Semaphore sem = new Semaphore(10);    // 동시 10개

void callExternal() throws InterruptedException {
    sem.acquire();
    try {
        externalApi.call();
    } finally {
        sem.release();
    }
}
```

> 외부 API 호출 동시성 제한은 Semaphore가 표준.

---

## ReadWriteLock vs StampedLock

### ReentrantReadWriteLock

- read는 여러 스레드 동시 가능
- write는 단독
- read가 많은 워크로드에 좋음

```java
ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
rwl.readLock().lock();
try { ... } finally { rwl.readLock().unlock(); }
```

### StampedLock (JDK 8+)

ReadWriteLock의 **낙관적 read** 버전. 더 빠르지만 손이 더 감.

```java
StampedLock sl = new StampedLock();

// 낙관적 read
long stamp = sl.tryOptimisticRead();
int v = state;
if (!sl.validate(stamp)) {        // 그 사이 write 있었으면 false
    stamp = sl.readLock();        // fallback: 비관적 read
    try { v = state; } finally { sl.unlockRead(stamp); }
}
```

JDK·Spring 내부에서 자주 쓰임. 코드는 복잡하므로 일반 앱에선 ReadWriteLock 충분.

---

## CompletableFuture — Future의 후예

```java
CompletableFuture.supplyAsync(() -> fetchUser(id))                // 비동기 시작
    .thenApply(user -> user.getName())                            // map
    .thenCompose(name -> fetchProfileAsync(name))                 // flatMap
    .thenCombine(scoreFuture, (profile, score) -> profile.withScore(score))
    .exceptionally(ex -> Profile.empty())
    .thenAccept(p -> log.info("done: {}", p));
```

| 메서드 | 의미 |
|---|---|
| `thenApply(fn)` | sync map |
| `thenApplyAsync(fn)` | async map (다른 스레드) |
| `thenCompose(fn)` | flatMap |
| `thenCombine(other, fn)` | 둘 합치기 |
| `thenAccept(c)` | consume |
| `allOf(...)` / `anyOf(...)` | 합산 |
| `exceptionally(fn)` | 예외 처리 |
| `handle(bi)` | 결과·예외 모두 처리 |

### 함정 — 기본 Executor는 ForkJoinPool.commonPool()

```java
CompletableFuture.supplyAsync(() -> blockingIO())   // commonPool에서 실행
// commonPool은 CPU 수만큼 스레드만 만듦 → blocking I/O면 풀 고갈
```

**Blocking 작업은 명시적 Executor 지정**:

```java
ExecutorService io = Executors.newFixedThreadPool(50);
CompletableFuture.supplyAsync(() -> blockingIO(), io);
```

---

## ThreadPoolExecutor 내부

```java
new ThreadPoolExecutor(
    corePoolSize,           // 평소 유지 스레드 수
    maximumPoolSize,        // 최대 스레드 수
    keepAliveTime, unit,    // idle 스레드 종료 시간
    workQueue,              // 작업 대기열
    threadFactory,
    rejectedExecutionHandler
);
```

### 작업 도착 시 흐름

```
새 작업 도착
    │
    ▼
스레드 수 < corePoolSize ?
    Yes ─▶ 새 스레드 생성 (현재 풀 비어있어도)
    No  │
        ▼
    Queue에 넣을 수 있나 ?
        Yes ─▶ Queue에 enqueue, 기존 스레드가 처리
        No  │
            ▼
        스레드 수 < maximumPoolSize ?
            Yes ─▶ 새 스레드 생성
            No  ─▶ RejectedExecutionHandler!
```

### Queue 선택이 결정적

| Queue | 동작 |
|---|---|
| **SynchronousQueue** | 즉시 핸드오프 → maximumPoolSize까지 즉시 확장 |
| **LinkedBlockingQueue (unbounded)** | 무한 큐 → maximumPoolSize 무의미 |
| **ArrayBlockingQueue(N)** | 차면 maximumPoolSize까지 확장 |

> Tomcat 기본은 LinkedBlockingQueue → max는 의미 없음. **메모리 폭주 위험**, 큐 크기 명시 권장.

### Rejection Policy

```java
new ThreadPoolExecutor.AbortPolicy();           // RejectedExecutionException 던짐 (기본)
new ThreadPoolExecutor.DiscardPolicy();         // 조용히 버림
new ThreadPoolExecutor.DiscardOldestPolicy();   // 큐의 가장 오래된 거 버림
new ThreadPoolExecutor.CallerRunsPolicy();      // 호출자 스레드에서 실행 (back-pressure)
```

> **CallerRunsPolicy**는 백프레셔의 좋은 패턴 — 호출자가 느려지면 자연스럽게 입력도 느려짐.

---

## 운영 사례

### 사례 1 — Tomcat thread starvation

**증상**: API 응답 안 옴. 새 요청 처리 안 됨.

**진단**: 스레드덤프 — `http-nio-8080-exec-*` 200개가 모두 외부 API 응답 대기 중.

**원인**: 외부 API timeout 5초, Tomcat 풀 200, 부하 100 req/s. → 1초마다 500개 inflight → 풀 고갈.

**조치**:
1. 외부 API timeout 1초로 단축
2. Semaphore로 동시 호출 50개 제한
3. WebClient(논블로킹)로 마이그레이션 검토

### 사례 2 — ConcurrentHashMap get-then-put race

```java
// ❌
Long current = stats.get(key);
if (current == null) {
    stats.put(key, 1L);
} else {
    stats.put(key, current + 1);
}
// → 동시 호출 시 일부 increment 손실
```

**증상**: 통계가 실제보다 적게 집계됨.

**조치**: `stats.merge(key, 1L, Long::sum)`.

---

## 실습 (Hands-on)

### 1단계 — Counter 벤치

`AtomicLong` vs `LongAdder` vs `synchronized` — 4 스레드·100M 증감 측정.

```java
public class CounterBench {
    public static void main(String[] args) throws Exception {
        run("AtomicLong", () -> {
            AtomicLong c = new AtomicLong();
            return c::incrementAndGet;
        });
        run("LongAdder", () -> {
            LongAdder c = new LongAdder();
            return c::increment;
        });
        run("synchronized", () -> {
            Object lock = new Object();
            int[] c = {0};
            return () -> { synchronized (lock) { c[0]++; } };
        });
    }
    // 측정 헬퍼 ...
}
```

JIT warm-up 위해 두 번 측정 권장.

### 2단계 — ThreadPool 동작 관찰

```java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    2, 5, 10, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(3));

for (int i = 0; i < 10; i++) {
    final int n = i;
    try {
        pool.execute(() -> {
            System.out.println(n + " on " + Thread.currentThread().getName());
            try { Thread.sleep(1000); } catch (Exception e) {}
        });
    } catch (RejectedExecutionException e) {
        System.out.println(n + " rejected");
    }
}

pool.shutdown();
```

작업이 어느 순서로 어디서 실행되는지, 언제 거부되는지 관찰.

### 3단계 — Tomcat 스레드 풀 설정 보기

```yaml
# application.yml
server:
  tomcat:
    threads:
      min-spare: 10
      max: 200
    max-connections: 8192
    accept-count: 100
```

각 값을 `application.yml`로 노출하고 부하 테스트(JMeter, k6)에서 동작 관찰.

---

## 더 읽어볼 자료

- 📘 『Java Concurrency in Practice』 — 가장 중요. 7장(취소·종료), 14장(동기화 기본)
- 📘 『Java Performance Engineering』 (Monica Beckwith) — 동시성 챕터
- 🔗 [Doug Lea — Concurrent Programming in Java](http://gee.cs.oswego.edu/dl/cpj/) — `j.u.c`의 원작자
- 🔗 [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- 🔗 [Spring async 가이드](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- 🎓 InfoQ — Brian Goetz, "Concurrency Past and Present"
