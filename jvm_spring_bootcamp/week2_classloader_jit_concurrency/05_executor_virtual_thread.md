# Day 5 — Executor · Virtual Thread (Project Loom)

## 한 줄 요약

JDK 21에서 **Virtual Thread**가 정식 출시. **수백만 개**를 만들어도 OK. blocking I/O가 많은 워크로드(웹 서버, 외부 API 호출)에서 게임 체인저. CPU 바운드 작업은 여전히 Platform Thread가 유리.

## 학습 목표

- [ ] Platform Thread vs Virtual Thread 차이를 안다
- [ ] Virtual Thread가 어떻게 동작하는지 (carrier thread, pinning)
- [ ] Virtual Thread를 언제 쓰고 언제 안 쓰는지
- [ ] Pinning 함정 (synchronized + blocking)
- [ ] Spring Boot 3.2+에서 Virtual Thread 활성화

---

## Platform Thread (전통적 스레드)

```
Java Thread (1)  ─────▶  OS Thread (1)  ─────▶  Kernel scheduling
                          (커널 자원)
                          - 메모리 1-2MB stack
                          - 컨텍스트 스위치 비용
```

| 항목 | 값 |
|---|---|
| 메모리 | 1~2MB (Stack) |
| 생성 비용 | OS syscall |
| 최대 개수 | 수천~수만 (OS limit) |
| 스위치 비용 | µs 수준 |

수만 개 만들면 OOM 또는 OS 한도. **blocking I/O가 많은 워크로드의 한계점**.

---

## Virtual Thread (JDK 21+)

```
Virtual Thread (수만~수백만)
        │
        │  M:N 스케줄링
        ▼
Carrier Thread (Platform Thread, CPU 수만큼)
        │
        ▼
OS Thread
```

JVM이 ForkJoinPool에 있는 **소수의 Carrier**에 Virtual Thread를 **mount/unmount**해서 실행. blocking I/O 만나면 자동 unmount → carrier는 다른 Virtual Thread 실행.

| 항목 | 값 |
|---|---|
| 메모리 | 수백 byte~수 KB |
| 생성 비용 | new Object 수준 |
| 최대 개수 | **수백만** |
| Blocking I/O | Carrier 양보 → 다른 VT가 사용 |

### 만드는 법

```java
// 1) 직접 시작
Thread vt = Thread.ofVirtual().name("vt-").start(() -> {
    System.out.println(Thread.currentThread());
});

// 2) Executor
ExecutorService es = Executors.newVirtualThreadPerTaskExecutor();
// 작업마다 Virtual Thread 1개 ── 무제한

es.submit(() -> {
    // blocking I/O
    HttpResponse<?> resp = httpClient.send(req, ...);
});

// 3) per-task executor 안에서 structured concurrency
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var f1 = scope.fork(() -> fetchUser(id));
    var f2 = scope.fork(() -> fetchOrders(id));
    scope.join();
    scope.throwIfFailed();
    return new Result(f1.get(), f2.get());
}
```

---

## Pinning — Virtual Thread가 막히는 함정

Virtual Thread가 다음을 만나면 **carrier에 박혀서(pinned)** 양보 못 함:

1. `synchronized` 블록 안에서 blocking
2. Native method (JNI) 호출 중
3. `Object.wait()` (안에서)

```java
// ❌ Pinning 위험
synchronized (this) {
    long start = System.currentTimeMillis();
    HttpResponse r = httpClient.send(...);    // synchronized 안에서 I/O
    // 이 동안 carrier는 다른 VT를 처리 못 함
}

// ✅ ReentrantLock으로 변경
ReentrantLock lock = new ReentrantLock();
lock.lock();
try {
    httpClient.send(...);                      // 정상적으로 unmount
} finally {
    lock.unlock();
}
```

### Pinning 모니터링

```bash
java -Djdk.tracePinnedThreads=full Main
# 또는
java -Djdk.tracePinnedThreads=short Main
```

운영서엔 `-Djdk.tracePinnedThreads=short`로 켜두면 좋음.

---

## 언제 쓰고 언제 안 쓰나

### ✅ Virtual Thread 좋음

- **HTTP 서버** (각 요청 = 한 VT)
- **외부 API 호출 많음** (대기 시간 길음)
- **DB 호출** (JDBC는 blocking)
- **레거시 blocking 코드** (논블로킹으로 안 바꾸고 throughput 개선)

### ❌ Virtual Thread 안 좋음

- **CPU 바운드** (실제로 계산만 하면 Platform Thread만큼 빠를 뿐 더 빠르진 않음)
- **ThreadLocal 가득** (VT 수만 개면 ThreadLocal 메모리 폭주)
- **synchronized + blocking 많음** (Pinning)

---

## Spring Boot 3.2+의 Virtual Thread

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

이 한 줄로:
- Tomcat 요청 스레드 → Virtual Thread
- `@Async` → Virtual Thread
- `TaskExecutor` 기본 → Virtual Thread

### 확인

```java
@RestController
public class TestController {
    @GetMapping("/thread")
    public String t() {
        return Thread.currentThread().toString();
        // VirtualThread[#42,...]@ForkJoinPool-1-worker-3
    }
}
```

---

## 실전 벤치 — 1만 동시 요청

### Platform Thread (Tomcat 기본 max 200)

```
1만 요청 동시 도착
→ 200개는 처리, 9800개는 큐 대기
→ 큐 가득 차면 거부
→ 평균 latency 매우 큼
```

### Virtual Thread

```
1만 요청 동시 도착
→ 1만 VT 즉시 생성, 모두 동시 실행
→ blocking 시 carrier가 다른 VT 처리
→ 모든 요청 거의 동시에 완료
```

**전제**: 처리 로직이 대부분 I/O 대기. CPU 바운드라면 큰 차이 없음.

---

## 다른 Executor 종류 정리

| Executor | 용도 |
|---|---|
| `Executors.newFixedThreadPool(N)` | 고정 N개. 일반 작업 |
| `Executors.newCachedThreadPool()` | 무한정 늘릴 수 있는 풀 — **위험** |
| `Executors.newSingleThreadExecutor()` | 단일 스레드 (순서 보장) |
| `Executors.newScheduledThreadPool(N)` | 지연·반복 작업 |
| `Executors.newWorkStealingPool(N)` | ForkJoinPool — divide & conquer |
| `Executors.newVirtualThreadPerTaskExecutor()` | **JDK 21+** Virtual Thread per task |

### newCachedThreadPool 위험

```java
// ❌ 부하 늘면 스레드 무한 생성 → OOM
ExecutorService cached = Executors.newCachedThreadPool();
```

명시적 ThreadPoolExecutor 또는 Virtual Thread 권장.

---

## 운영 사례

### 사례 1 — Reactive에서 Virtual Thread로

**Before**: WebFlux + R2DBC. 코드 어려움. 디버깅 힘듦.

**After**: Spring Boot 3.2 + VT. 평범한 Servlet + JDBC + JPA. throughput은 비슷, 코드는 훨씬 단순.

→ **간단한 backend에서 Reactive 정당화 어려워짐**. Virtual Thread가 대안.

### 사례 2 — Pinning 폭주

**증상**: VT 활성화 후 오히려 throughput 떨어짐.

**진단**: `-Djdk.tracePinnedThreads=full` 로그 확인.

**원인**: 옛 코드에 `synchronized(this)` 안에서 RestTemplate 호출.

**조치**: `synchronized` → `ReentrantLock` 또는 동기화 범위 축소.

---

## 실습 (Hands-on)

### 1단계 — Virtual Thread 만들어보기

```java
public class VTDemo {
    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        
        try (var es = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10_000; i++) {
                es.submit(() -> {
                    try { Thread.sleep(1000); } catch (Exception e) {}
                });
            }
        }
        
        System.out.println("elapsed: " + (System.currentTimeMillis() - start) + "ms");
    }
}
```

```bash
javac VTDemo.java
java VTDemo
# 약 1초 — 1만 VT가 동시에 잠
```

### 2단계 — Platform Thread로 같은 코드

```java
try (var es = Executors.newFixedThreadPool(200)) {
    // 위와 동일
}
```

`elapsed` 약 50초 — 200개씩 처리.

### 3단계 — Pinning 재현·진단

```java
public class PinDemo {
    public static void main(String[] args) throws Exception {
        Object lock = new Object();
        try (var es = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10; i++) {
                es.submit(() -> {
                    synchronized (lock) {
                        try { Thread.sleep(1000); } catch (Exception e) {}
                    }
                });
            }
        }
    }
}
```

```bash
java -Djdk.tracePinnedThreads=full PinDemo
# → Pinning 경고 출력
```

### 4단계 — Spring Boot 3.2 + VT

```yaml
spring.threads.virtual.enabled: true
```

```java
@RestController
class C {
    @GetMapping("/slow")
    public String slow() throws Exception {
        Thread.sleep(2000);                     // blocking
        return Thread.currentThread() + "";
    }
}
```

k6 또는 wrk로 1만 동시 요청 — 1만이 거의 동시에 끝남.

---

## 더 읽어볼 자료

- 📘 『Modern Java in Action』 — VT 챕터 (개정판)
- 🔗 [JEP 444: Virtual Threads](https://openjdk.org/jeps/444) — 정식 출시
- 🔗 [JEP 453: Structured Concurrency (Preview)](https://openjdk.org/jeps/453)
- 🔗 [Spring Boot 3.2 — Embracing Virtual Threads](https://spring.io/blog/2023/09/20/a-bird-s-eye-view-of-jdk-21-virtual-threads)
- 🎓 InfoQ — Ron Pressler, "Project Loom"
- 🔗 [Pinning 트러블슈팅](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html#GUID-DC4306FC-D6C1-4BCC-AECE-48C32C1A8DAA)
