# Week 2 — ClassLoader · JIT · JMM · 동시성

## 주차 목표

JVM이 클래스를 **어떻게 찾고 적재하고 실행하는지**, 그리고 멀티스레드 환경에서 **어떻게 메모리 일관성을 보장하는지** 안다. Spring·Hibernate가 사용하는 동적 프록시·바이트코드 조작의 기반.

---

## 일정

| Day | 주제 | 핵심 |
|---|---|---|
| Day 1 | [ClassLoader](01_classloader.md) | 위임 모델, 사용자 정의 로더, Spring의 ClassLoader |
| Day 2 | [바이트코드·JIT](02_bytecode_jit.md) | javap, ASM, C1/C2, 인라이닝, escape analysis |
| Day 3 | [JMM·happens-before](03_jmm_happens_before.md) | volatile, final, synchronized 메모리 모델 |
| Day 4 | [동시성 자료구조 내부](04_concurrent_internals.md) | AQS, ReentrantLock, ConcurrentHashMap, AtomicXxx |
| Day 5 | [Executor·Virtual Thread](05_executor_virtual_thread.md) | ThreadPoolExecutor, ForkJoinPool, Project Loom |

### Lab

| Lab | 내용 |
|---|---|
| [lab3_classloader_demo.md](labs/lab3_classloader_demo.md) | 사용자 정의 ClassLoader, 동일 클래스 두 로더에 적재 |
| [lab4_concurrency_pitfall.md](labs/lab4_concurrency_pitfall.md) | 보이지 않는 race, 데드락, Virtual Thread 비교 |

---

## 학습 결과

이 주차를 마치면:

- [ ] ClassLoader 위임 모델을 그림으로 설명할 수 있다
- [ ] WAR redeploy 시 ClassLoader 누수가 왜 일어나는지 안다
- [ ] CGLIB/JDK Dynamic Proxy의 차이를 안다 (Spring AOP의 기반)
- [ ] JIT의 C1/C2, escape analysis, inline cache를 설명한다
- [ ] JMM의 happens-before 규칙 6가지를 안다
- [ ] volatile vs synchronized vs AtomicXxx의 차이
- [ ] AQS 기반 잠금이 어떻게 동작하는지 안다
- [ ] Virtual Thread를 언제 쓰고 언제 안 쓰는지 안다

---

## Week 2를 마치면 답할 수 있어야 할 것

1. `Class.forName()`과 `ClassLoader.loadClass()`의 차이는?
2. Spring Boot fat jar 안의 클래스는 어떤 ClassLoader가 적재?
3. `@Transactional`이 same-class 호출에서 안 먹는 이유를 JIT·프록시 관점에서 설명
4. `long`이나 `double` 변수는 왜 atomic하지 않은가? volatile 붙이면?
5. ConcurrentHashMap의 `compute`와 `put`의 차이는 동시성 관점에서?
6. ThreadPoolExecutor가 작업을 거부하는 4가지 정책은?
7. Virtual Thread 1만 개 만들기 vs Platform Thread 1만 개 만들기 — 무엇이 다른가?
