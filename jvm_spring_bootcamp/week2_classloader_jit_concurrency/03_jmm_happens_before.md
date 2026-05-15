# Day 3 — Java Memory Model · happens-before

## 한 줄 요약

JMM은 **여러 스레드가 같은 메모리를 읽고 쓸 때 무엇이 보이고 무엇이 안 보이는가**의 규칙. JIT·CPU의 재정렬을 고려해야 하므로 단순히 "쓴 다음 읽으면 보이겠지"가 아니다. `volatile`, `synchronized`, `final`, `java.util.concurrent`가 이 가시성을 만든다.

## 학습 목표

- [ ] Reordering(재정렬)의 3가지 출처를 안다 (컴파일러, CPU, 캐시)
- [ ] happens-before 6가지 규칙을 외운다
- [ ] `volatile`이 무엇을 보장하고 무엇을 보장하지 않는지 안다
- [ ] `synchronized`의 메모리 효과를 설명한다
- [ ] `final`의 안전한 발행 보장
- [ ] Double-Checked Locking이 왜 깨졌고 어떻게 고쳤는지 안다

---

## 멀티스레드의 함정

```java
class Visibility {
    boolean ready = false;
    int value = 0;

    // Thread A
    void writer() {
        value = 42;
        ready = true;
    }

    // Thread B
    void reader() {
        if (ready) {
            System.out.println(value);   // 42를 보장? ❌
        }
    }
}
```

이 코드의 출력 가능성:
- 42 (정상)
- 0 (`value`가 아직 안 보임)
- 출력 안 됨 (`ready`가 영원히 false로 보임)

이유:
1. **컴파일러/JIT 재정렬** — 의존성 없으면 순서 바꿈
2. **CPU 재정렬** — store buffer, load reorder
3. **CPU 캐시** — 코어마다 캐시, MESI 프로토콜이 즉시 전파 안 함

**해결**: `volatile`, `synchronized`, `final`, AtomicXxx, java.util.concurrent.

---

## happens-before — 가시성의 정확한 규칙

JMM은 "이런 순서로 일어나면 결과가 보이는 것이 보장된다"의 **부분 순서(partial order)**.

> **A happens-before B**: A의 결과는 B에서 보인다.

### 규칙 6가지

#### 1. 프로그램 순서 규칙
같은 스레드 안에서 순차적으로 쓰면 그 순서대로 happens-before.
```java
int x = 1;        // (1) hb (2)
int y = 2;
```

#### 2. 모니터 잠금 규칙
`unlock` happens-before 그 잠금을 잡은 다음 스레드의 `lock`.
```java
synchronized (lock) { x = 1; }    // unlock
// ─── happens-before ──
synchronized (lock) { read = x; } // lock
```

#### 3. volatile 규칙
volatile 변수의 write happens-before 그 변수의 모든 read.
```java
volatile boolean ready;

ready = true;             // write
// ─── happens-before ──
if (ready) { ... }        // read (다른 스레드)
```

#### 4. 스레드 시작 규칙
`Thread.start()` happens-before 그 스레드의 첫 액션.
```java
x = 1;
Thread t = new Thread(() -> {
    System.out.println(x);   // 1 보장
});
t.start();
```

#### 5. 스레드 종료 규칙
스레드의 마지막 액션 happens-before 다른 스레드의 `t.join()` 반환 또는 `t.isAlive() == false`.
```java
Thread t = new Thread(() -> {
    x = 1;
});
t.start();
t.join();
System.out.println(x);       // 1 보장
```

#### 6. 전이성 (Transitivity)
A hb B 이고 B hb C 이면 A hb C.

#### 보너스 — final 규칙
생성자에서 final 필드 할당 happens-before 생성자 종료. **안전한 발행** 보장.
```java
class Immutable {
    final int x;
    Immutable() { x = 42; }  // hb 생성자 끝
}
// 다른 스레드가 이 객체의 참조를 얻으면 x = 42를 반드시 봄
```

---

## volatile — 무엇을 하나

### 보장

1. **가시성(visibility)** — write는 즉시 다른 스레드에서 read 가능
2. **메모리 배리어 ↔ 재정렬 금지** — volatile 주위의 다른 변수 read/write가 재정렬되지 않음

### 보장 안 함

- **원자성(atomicity)** — `count++`은 volatile 붙여도 race 가능 (`read-modify-write`이라서)

```java
// ❌ 잘못된 사용
volatile int count;
void increment() {
    count++;            // read → +1 → write의 3 단계 → race
}

// ✅
AtomicInteger count = new AtomicInteger();
void increment() {
    count.incrementAndGet();
}
```

### volatile의 정확한 사용처

```java
class WorkerStopFlag {
    private volatile boolean stop = false;
    
    public void run() {
        while (!stop) {       // 다른 스레드에서 stop = true 하면 즉시 보임
            doWork();
        }
    }
    
    public void shutdown() {
        stop = true;
    }
}
```

**전형적 패턴**: 한 스레드만 쓰고 여러 스레드가 읽는 flag.

---

## long·double의 비원자성 트랩

JVM 스펙상 32-bit JVM은 64-bit `long`/`double`의 write가 두 번에 나뉠 수 있음. 64-bit JVM은 대부분 atomic이지만 **스펙으로 보장 안 됨**.

```java
// ❌ 32-bit에서 race 가능
long balance;             // 다른 스레드가 절반 쓴 상태를 읽을 수 있음

// ✅
volatile long balance;    // volatile은 read/write atomicity 보장 (long/double 포함)
// 또는
AtomicLong balance = new AtomicLong();
```

---

## synchronized — 두 효과

1. **상호 배제** (Mutual Exclusion) — 같은 잠금에 한 번에 한 스레드만
2. **메모리 가시성** — 잠금 진입 시 read, 해제 시 write 동기화

```java
class Counter {
    private int count = 0;
    
    public synchronized void increment() {
        count++;          // atomic + 다른 스레드에서 보임
    }
    
    public synchronized int get() {
        return count;     // 최신 값 보장
    }
}
```

### synchronized vs ReentrantLock

```java
ReentrantLock lock = new ReentrantLock();

lock.lock();
try {
    // critical section
} finally {
    lock.unlock();
}
```

| 기능 | synchronized | ReentrantLock |
|---|---|---|
| 가독성 | 좋음 (블록 끝나면 자동 해제) | 직접 try/finally |
| timeout | X | `tryLock(timeout)` |
| 공정성 | 비공정 | 선택 가능 (`new ReentrantLock(true)`) |
| 인터럽트 | X | `lockInterruptibly()` |
| Condition | wait/notify | 여러 Condition |

> 단순 한 줄 잠금은 `synchronized`, 복잡한 시나리오는 `ReentrantLock`.

---

## final 필드 — 안전한 발행

```java
public class Point {
    final int x, y;
    
    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

// 다른 스레드:
Point p = sharedPoint;   // 어떤 식으로든 받음
System.out.println(p.x);  // 정확한 값 보장
```

JMM의 final 규칙으로 **생성자 종료 후의 final 필드는 모든 스레드에 보임**. 별도 동기화 불필요.

### 주의 — 생성자에서 `this` 누출

```java
public class Listener {
    final int x;
    
    public Listener(EventBus bus, int x) {
        this.x = x;
        bus.register(this);    // ❌ this 누출!
                              // 다른 스레드가 미초기화 Listener 받을 수 있음
    }
}

// ✅
public static Listener create(EventBus bus, int x) {
    Listener l = new Listener(x);
    bus.register(l);
    return l;
}
```

---

## Double-Checked Locking — 왜 안 되고 어떻게 고쳤나

### 잘못된 옛 코드

```java
class SingletonBroken {
    private static SingletonBroken instance;
    
    public static SingletonBroken getInstance() {
        if (instance == null) {                    // 1차
            synchronized (SingletonBroken.class) {
                if (instance == null) {            // 2차
                    instance = new SingletonBroken();  // ❌ 재정렬 가능
                }
            }
        }
        return instance;
    }
}
```

문제: `instance = new SingletonBroken()`이 다음으로 분해됨:
1. 메모리 할당
2. 객체 참조를 instance에 할당
3. 생성자 실행

JIT/CPU가 1 → 2 → 3을 1 → 2 → 3 또는 1 → 3 → 2 또는 등등으로 재정렬 가능. 1차 검사에서 **미초기화 객체 참조**를 받을 수 있음.

### 해결 — volatile

```java
class Singleton {
    private static volatile Singleton instance;       // ← volatile!
    
    public static Singleton getInstance() {
        Singleton local = instance;
        if (local == null) {
            synchronized (Singleton.class) {
                local = instance;
                if (local == null) {
                    local = new Singleton();
                    instance = local;
                }
            }
        }
        return local;
    }
}
```

### 더 간단 — Initialization-On-Demand Holder

```java
class Singleton {
    private Singleton() {}
    
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();   // 첫 사용 시점에 로드
    }
    
    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

ClassLoader가 클래스 초기화 시 lock을 자동으로 잡아주므로 thread-safe. volatile도 필요 없음. **가장 권장**.

### 아니면 그냥 enum

```java
public enum Singleton {
    INSTANCE;
    
    public void doIt() {}
}
```

Joshua Bloch 추천. 가장 간단·안전.

---

## 가시성 vs 원자성 vs 순서

| 보장 | volatile | synchronized | AtomicXxx |
|---|---|---|---|
| 가시성 | O | O | O |
| 원자성 | O (개별 read/write) | O | O |
| 복합 연산 원자성 | X | O | O (CAS) |
| 재정렬 금지 | O | O | O |

### 어느 것을 쓸까

```java
// Flag (한 곳 쓰기, 여러 곳 읽기)
private volatile boolean stop;

// Counter (여러 곳 증감)
private final AtomicLong count = new AtomicLong();

// 복합 로직
private final Object lock = new Object();
synchronized (lock) {
    // 여러 변수 일관 변경
}

// Map
private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>();
```

---

## 실제 운영 사례

### 사례 1 — Map.put이 사라짐 (HashMap 동시 사용)

**증상**: 캐시에 put 한 값이 종종 사라짐. 가끔 `ConcurrentModificationException`.

**원인**: 여러 스레드가 `HashMap`을 동시 사용. JDK 8 이전엔 무한 루프 + 데이터 손상.

**조치**: `ConcurrentHashMap` 또는 `Collections.synchronizedMap`.

### 사례 2 — Spring Bean의 일관성 깨짐

```java
@Service
public class StatService {
    private long count = 0;
    
    public void incr() { count++; }     // ❌ Service Bean은 singleton, 멀티스레드
    
    public long get() { return count; }
}
```

조치: `AtomicLong` 또는 `@Synchronized`.

---

## 실습 (Hands-on)

### 1단계 — 가시성 누락 재현

```java
// VisibilityDemo.java
public class VisibilityDemo {
    static boolean ready = false;        // volatile 없음!
    static int value;

    public static void main(String[] args) throws Exception {
        Thread reader = new Thread(() -> {
            while (!ready) { /* spin */ }
            System.out.println(value);
        });
        reader.start();
        
        Thread.sleep(100);
        value = 42;
        ready = true;
        reader.join(2000);
        System.out.println("main done");
    }
}
```

```bash
javac VisibilityDemo.java
# 이 코드는 환경 따라 reader가 영원히 spin 할 수 있음
# (JIT가 while(!ready)를 while(true)로 최적화)
java -server VisibilityDemo
```

이제 `static volatile boolean ready;`로 바꾸고 다시 — 즉시 출력.

### 2단계 — AtomicXxx vs synchronized 속도

```java
// 1억 번 증감, 4 스레드
// (a) synchronized counter
// (b) AtomicLong
// (c) LongAdder (contention이 큰 경우)
```

JMH로 측정 — 보통 LongAdder >> AtomicLong > synchronized.

### 3단계 — happens-before 위반 진단

자기 코드를 다시 보고 다음 검사:

- 멀티스레드가 공유하는 필드 — `volatile` / `final` / 동기화 중 무엇?
- ThreadPool에 제출된 Runnable의 capture 변수
- 생성자에서 외부에 노출되는 객체

---

## 더 읽어볼 자료

- 📘 『Java Concurrency in Practice』 (Brian Goetz) — **JMM의 정전**. 3장, 16장
- 📘 『The Art of Multiprocessor Programming』 (Herlihy, Shavit)
- 🔗 [JSR-133 FAQ](https://www.cs.umd.edu/~pugh/java/memoryModel/jsr-133-faq.html)
- 🔗 [JLS §17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html)
- 🔗 [Shipilev — Close Encounters of The Java Memory Model](https://shipilev.net/blog/2014/jmm-pragmatics/)
- 🎓 Aleksey Shipilev — JMM 강연 다수
