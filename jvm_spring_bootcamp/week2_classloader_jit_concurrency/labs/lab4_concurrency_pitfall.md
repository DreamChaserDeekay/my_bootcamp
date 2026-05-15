# Lab 4 — 동시성 함정 + Virtual Thread 비교

## 목표

- 보이지 않는 race condition 재현
- volatile / synchronized / Atomic 각각의 효과 비교
- Pinning 진단
- Virtual Thread vs Platform Thread throughput 측정

---

## 1단계 — Visibility 누락 재현

```java
// Visibility.java
public class Visibility {
    static boolean ready = false;     // volatile 없음
    static int value = 0;
    
    public static void main(String[] args) throws Exception {
        Thread reader = new Thread(() -> {
            int loops = 0;
            while (!ready) { loops++; }
            System.out.println("read value=" + value + " loops=" + loops);
        });
        reader.start();
        
        Thread.sleep(100);
        value = 42;
        ready = true;
        
        reader.join(5000);
        if (reader.isAlive()) {
            System.out.println("reader stuck — visibility 누락!");
            System.exit(1);
        }
    }
}
```

```bash
javac Visibility.java

# Server JIT — 가시성 누락 발생 가능
java -server Visibility
```

환경에 따라 "reader stuck" 출력될 수 있음. JIT가 `while(!ready)`를 hoist 최적화.

### 해결

```java
static volatile boolean ready = false;
```

다시 실행 — 즉시 응답.

---

## 2단계 — Counter 비교

```java
// CounterBench.java
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;

public class CounterBench {
    static final int THREADS = 4;
    static final int ITERS = 10_000_000;
    
    public static void main(String[] args) throws Exception {
        warmup();
        bench("synchronized", new SyncCounter());
        bench("AtomicLong", new AtomicLongCounter());
        bench("LongAdder", new LongAdderCounter());
    }
    
    static void bench(String name, Counter c) throws Exception {
        long t = System.nanoTime();
        ExecutorService es = Executors.newFixedThreadPool(THREADS);
        for (int i = 0; i < THREADS; i++) {
            es.submit(() -> {
                for (int j = 0; j < ITERS; j++) c.increment();
            });
        }
        es.shutdown();
        es.awaitTermination(1, TimeUnit.MINUTES);
        long elapsed = (System.nanoTime() - t) / 1_000_000;
        System.out.printf("%-15s sum=%d, elapsed=%dms%n", name, c.sum(), elapsed);
    }
    
    static void warmup() throws Exception {
        bench("warmup", new AtomicLongCounter());
    }
    
    interface Counter { void increment(); long sum(); }
    
    static class SyncCounter implements Counter {
        long c = 0;
        public synchronized void increment() { c++; }
        public synchronized long sum() { return c; }
    }
    static class AtomicLongCounter implements Counter {
        AtomicLong c = new AtomicLong();
        public void increment() { c.incrementAndGet(); }
        public long sum() { return c.get(); }
    }
    static class LongAdderCounter implements Counter {
        LongAdder c = new LongAdder();
        public void increment() { c.increment(); }
        public long sum() { return c.sum(); }
    }
}
```

```bash
javac CounterBench.java
java CounterBench
```

예상 결과 (M1 Mac 기준):
```
warmup          sum=40000000, elapsed=...
synchronized    sum=40000000, elapsed=2000~3000ms
AtomicLong      sum=40000000, elapsed=400~600ms
LongAdder       sum=40000000, elapsed=100~200ms
```

→ **LongAdder가 압도적** (충돌 분산).

---

## 3단계 — ConcurrentHashMap get-then-put race

```java
// MapRace.java
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class MapRace {
    public static void main(String[] args) throws Exception {
        // ❌ 잘못된 방식
        ConcurrentHashMap<String, Long> m1 = new ConcurrentHashMap<>();
        run("get-then-put", () -> {
            Long v = m1.get("k");
            if (v == null) m1.put("k", 1L);
            else m1.put("k", v + 1);
        });
        System.out.println("get-then-put result: " + m1.get("k"));
        
        // ✅ atomic compute
        ConcurrentHashMap<String, Long> m2 = new ConcurrentHashMap<>();
        run("compute", () -> {
            m2.compute("k", (k, v) -> v == null ? 1L : v + 1);
        });
        System.out.println("compute result: " + m2.get("k"));
        
        // ✅ merge
        ConcurrentHashMap<String, Long> m3 = new ConcurrentHashMap<>();
        run("merge", () -> {
            m3.merge("k", 1L, Long::sum);
        });
        System.out.println("merge result: " + m3.get("k"));
    }
    
    static void run(String name, Runnable r) throws Exception {
        ExecutorService es = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 100_000; i++) es.submit(r);
        es.shutdown();
        es.awaitTermination(1, TimeUnit.MINUTES);
    }
}
```

```bash
javac MapRace.java
java MapRace
```

예상:
```
get-then-put result: 73402     ← 손실 발생!
compute result: 100000          ← 정확
merge result: 100000             ← 정확
```

---

## 4단계 — Deadlock 진단

```java
// DeadlockDemo.java
public class DeadlockDemo {
    static final Object A = new Object();
    static final Object B = new Object();
    
    public static void main(String[] args) {
        new Thread(() -> {
            synchronized (A) {
                sleep(100);
                synchronized (B) {
                    System.out.println("T1 done");
                }
            }
        }, "T1").start();
        
        new Thread(() -> {
            synchronized (B) {
                sleep(100);
                synchronized (A) {
                    System.out.println("T2 done");
                }
            }
        }, "T2").start();
    }
    
    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (Exception e) {}
    }
}
```

```bash
javac DeadlockDemo.java
java DeadlockDemo &
# 다른 터미널
jstack <pid> | findstr -A 30 "Found one Java-level deadlock"
```

`jstack`이 자동으로 데드락 출력:
```
Found one Java-level deadlock:
=============================
"T2":
  waiting to lock monitor 0x... (object 0x..., a java.lang.Object),
  which is held by "T1"
"T1":
  waiting to lock monitor 0x... (object 0x..., a java.lang.Object),
  which is held by "T2"
```

---

## 5단계 — Virtual Thread Throughput

```java
// ConcurrencyBench.java
import java.util.concurrent.*;

public class ConcurrencyBench {
    static final int TASKS = 10_000;
    static final int SLEEP_MS = 1000;
    
    public static void main(String[] args) throws Exception {
        bench("Fixed-200", Executors.newFixedThreadPool(200));
        bench("Virtual", Executors.newVirtualThreadPerTaskExecutor());
    }
    
    static void bench(String name, ExecutorService es) throws Exception {
        long t = System.currentTimeMillis();
        try (es) {
            for (int i = 0; i < TASKS; i++) {
                es.submit(() -> {
                    try { Thread.sleep(SLEEP_MS); } catch (Exception e) {}
                });
            }
        }
        long elapsed = System.currentTimeMillis() - t;
        System.out.printf("%-12s: %dms%n", name, elapsed);
    }
}
```

```bash
javac ConcurrencyBench.java
java ConcurrencyBench
```

예상:
```
Fixed-200   : 50000ms     ← 200개씩 처리 → 50초
Virtual     : 1100ms       ← 모두 동시 → 1초
```

→ **Virtual Thread의 압도적 advantage** (blocking I/O 워크로드에서).

---

## 6단계 — Pinning 재현·진단

```java
// PinningDemo.java
import java.util.concurrent.*;

public class PinningDemo {
    static final Object lock = new Object();
    
    public static void main(String[] args) throws Exception {
        try (var es = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 5; i++) {
                int n = i;
                es.submit(() -> {
                    synchronized (lock) {
                        try { Thread.sleep(1000); } catch (Exception e) {}
                    }
                    System.out.println(n + " done");
                });
            }
        }
    }
}
```

```bash
javac PinningDemo.java
java -Djdk.tracePinnedThreads=full PinningDemo
```

출력:
```
Thread[#42,ForkJoinPool-1-worker-1,5,CarrierThreads]
    java.base/jdk.internal.misc.Unsafe.park ... <== monitors:1
        java.base/jdk.internal.vm.Continuation.yield ...
        ...
        PinningDemo$$Lambda/0x000... ... <== monitors:1
```

`<== monitors:1`가 Pinning 표시. `synchronized` 안에서 sleep → carrier 묶임.

### 해결 (ReentrantLock)

```java
import java.util.concurrent.locks.*;
static final ReentrantLock lock = new ReentrantLock();

// synchronized (lock) { ... }
//   ↓
lock.lock();
try { ... } finally { lock.unlock(); }
```

→ Pinning 사라짐.

---

## 7단계 — ThreadPool Rejection 정책

```java
// RejectionDemo.java
import java.util.concurrent.*;

public class RejectionDemo {
    public static void main(String[] args) throws Exception {
        // CallerRunsPolicy 시연
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2),
            new ThreadPoolExecutor.CallerRunsPolicy());
        
        for (int i = 0; i < 10; i++) {
            final int n = i;
            pool.execute(() -> {
                System.out.printf("task %d on %s%n", n, Thread.currentThread().getName());
                try { Thread.sleep(500); } catch (Exception e) {}
            });
        }
        pool.shutdown();
    }
}
```

예상:
```
task 0 on pool-1-thread-1
task 1 on pool-1-thread-2
task 2 on main          ← CallerRuns
task 3 on main          ← CallerRuns
...
```

호출자(main)가 직접 실행하면서 자연스럽게 throttle.

---

## 산출물

이 lab으로 다음을 검증:

- [ ] volatile 없으면 가시성 누락 가능 (JIT 최적화)
- [ ] LongAdder가 충돌 큰 환경에서 압도적
- [ ] `compute` / `merge`가 atomic, get-then-put는 race
- [ ] jstack이 데드락 자동 탐지
- [ ] Virtual Thread가 blocking I/O 워크로드에서 50배 throughput
- [ ] synchronized + blocking → Pinning, ReentrantLock으로 해결
- [ ] CallerRunsPolicy로 백프레셔 구현

---

## 다음 단계

[Week 2 Checklist](../checklist.md)
