# Week 2 — 체크리스트

## ClassLoader

- [ ] 3단계 위임 모델 그릴 수 있다 (Bootstrap / Platform / Application)
- [ ] 클래스 로딩 5단계 (Loading → Verify → Prepare → Resolve → Initialize)
- [ ] `Class.forName()` vs `loadClass()` 차이
- [ ] 사용자 정의 ClassLoader 작성 가능
- [ ] 같은 클래스, 두 로더 → ClassCastException 이유
- [ ] Spring Boot fat jar의 LaunchedClassLoader
- [ ] Tomcat WebappClassLoader 위임 반전과 redeploy 누수

## 바이트코드·JIT

- [ ] `javap -c -v` 출력을 읽을 수 있다
- [ ] invoke 5종 차이 (static/special/virtual/interface/dynamic)
- [ ] Tiered Compilation 5 레벨
- [ ] Inlining 결정 기준
- [ ] Escape Analysis와 스칼라 치환
- [ ] `-XX:+PrintCompilation`, `-XX:+PrintInlining` 사용
- [ ] JMH로 마이크로벤치마크
- [ ] Code Cache 가득 차면 어떻게 되는가

## JMM·happens-before

- [ ] happens-before 6규칙
- [ ] volatile이 보장하는 것 / 안 보장하는 것
- [ ] synchronized의 두 효과 (배제 + 가시성)
- [ ] final 안전 발행 규칙
- [ ] Double-Checked Locking 함정과 해결 3가지
- [ ] long/double 비원자성 트랩

## 동시성 자료구조

- [ ] CAS와 ABA 문제
- [ ] AQS 기본 구조 (state + FIFO queue)
- [ ] ReentrantLock이 AQS의 활용임을 안다
- [ ] ConcurrentHashMap의 bin 기반 lock striping
- [ ] AtomicLong vs LongAdder 차이
- [ ] CountDownLatch / Semaphore / CyclicBarrier 용도
- [ ] CompletableFuture 체인과 commonPool 함정

## Executor·Virtual Thread

- [ ] ThreadPoolExecutor의 작업 도착 흐름
- [ ] Queue 선택의 영향 (Synchronous / Linked / Array)
- [ ] Rejection 4 정책
- [ ] Platform vs Virtual Thread 차이
- [ ] Pinning 함정 (synchronized + blocking)
- [ ] Spring Boot 3.2+ VT 활성화

## 실습 결과

- [ ] Lab 3 — 사용자 정의 ClassLoader 동작
- [ ] Lab 3 — 두 로더에서 동일 클래스 → ClassCastException
- [ ] Lab 4 — Counter 3종 벤치 (synchronized vs Atomic vs LongAdder)
- [ ] Lab 4 — ConcurrentHashMap get-then-put race 손실 측정
- [ ] Lab 4 — jstack으로 데드락 자동 탐지
- [ ] Lab 4 — Virtual Thread 50배 throughput 측정
- [ ] Lab 4 — Pinning 진단 (-Djdk.tracePinnedThreads)

## 자기 점검

1. `@Transactional`이 same-class 호출에서 안 먹는 이유 (JIT·프록시 관점)
   <details><summary>답</summary>Spring AOP는 CGLIB로 동적 서브클래스 생성. 외부에서 호출하면 서브클래스의 override 메서드 실행. 같은 클래스 안에서 호출하면 this의 직접 호출이라 AOP 없음. JIT의 인라이닝과는 별개.</details>

2. `volatile`만으로 `count++`이 atomic하지 않은 이유
   <details><summary>답</summary>++ 는 read-modify-write 3단계. volatile은 개별 read/write의 가시성·원자성만 보장. 3단계 사이 다른 스레드가 끼어들 수 있음.</details>

3. `ConcurrentHashMap.compute`가 atomic한 이유
   <details><summary>답</summary>해당 bin에 synchronized로 잠금을 잡고 함수를 실행. CAS·synchronized 조합으로 다른 스레드가 같은 키를 동시에 수정 불가.</details>

4. ThreadPool에 unbounded LinkedBlockingQueue를 쓰면 위험한 이유
   <details><summary>답</summary>큐가 무한이라 maximumPoolSize 도달 전에 무한정 큐에 쌓임. 메모리 OOM 위험. 또한 작업이 처리 안 되는 동안 새 스레드 생성 안 됨.</details>

5. Virtual Thread 1만 개 만들었을 때 OS Thread도 1만 개?
   <details><summary>답</summary>아니. Carrier(=Platform Thread, 보통 CPU 수)에 M:N으로 mount. blocking I/O 만나면 unmount → carrier가 다른 VT 실행.</details>

6. JIT가 가시성을 깨는 코드 예
   <details><summary>답</summary>`while (!flag) { /* ... */ }`에서 flag가 volatile 아니면 JIT가 flag를 hoist (한 번만 읽어서 레지스터에). 다른 스레드가 flag = true 해도 영원히 spin.</details>

7. 같은 클래스(같은 이름)가 두 ClassLoader에서 적재되면?
   <details><summary>답</summary>JVM은 둘을 다른 Class로 취급. cast 불가, instanceof 실패. Class equality는 (ClassLoader, name) 쌍으로 결정.</details>

---

## 통과했다면

Week 3 [Spring Core 내부](../week3_spring_core_internals/00_overview.md)로!
