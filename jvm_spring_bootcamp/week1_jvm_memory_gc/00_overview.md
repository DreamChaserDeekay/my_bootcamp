# Week 1 — JVM 메모리·GC·튜닝

## 주차 목표

JVM이 코드를 어떻게 실행하고, 메모리를 어떻게 관리하며, GC가 무엇을 하는지 **그림을 그릴 수 있게 된다**. 운영서 OOM·GC 폭주가 났을 때 어디서부터 봐야 할지 안다.

---

## 일정

| Day | 주제 | 핵심 |
|---|---|---|
| Day 1 | [JVM 아키텍처](01_jvm_architecture.md) | ClassLoader → Runtime Data Area → Execution Engine |
| Day 2 | [메모리 영역](02_memory_areas.md) | Heap(Young/Old), Metaspace, Stack, PC, Native |
| Day 3 | [GC 알고리즘](03_gc_algorithms.md) | Serial / Parallel / CMS(폐기) / G1 / ZGC / Shenandoah |
| Day 4 | [GC 로그·튜닝](04_gc_tuning.md) | `-Xlog:gc*`, 옵션 매핑, 튜닝 사이클 |
| Day 5 | [힙·스레드 덤프 분석](05_heap_thread_dump.md) | jmap, jstack, MAT, OQL, async-profiler |

### Lab

| Lab | 내용 |
|---|---|
| [lab1_setup_jdk.md](labs/lab1_setup_jdk.md) | JDK 21 설치 + 도구 검증 + 첫 GC 로그 |
| [lab2_gc_logs.md](labs/lab2_gc_logs.md) | GC 폭주 재현 → 로그 해석 → 옵션 튜닝 |

---

## 학습 결과

이 주차를 마치면:

- [ ] JVM 메모리 영역 5개를 그릴 수 있다 (Heap/Stack/Metaspace/PC/Native)
- [ ] Young/Old 비율과 Minor/Major GC 차이를 안다
- [ ] G1 GC의 region 개념과 ZGC의 Colored Pointer를 설명한다
- [ ] GC 로그(`-Xlog:gc*`)에서 STW 시간·heap 변화를 읽는다
- [ ] 힙덤프(`jmap -dump`)·스레드덤프(`jstack`)를 떠서 분석한다
- [ ] OOM 종류 3가지(Heap / Metaspace / Direct Buffer)를 구별한다
- [ ] 운영용 JVM 옵션 세트를 가지고 있다

---

## Week 1을 마치면 다음 질문에 답할 수 있어야 한다

1. `String s = "hello"`로 만든 객체는 어디 사는가?
2. Young Generation이 가득 차면 어떤 일이 일어나는가?
3. CMS는 왜 폐기됐고 G1은 어떻게 그 문제를 풀었는가?
4. ZGC가 STW 10ms 미만을 어떻게 달성하는가?
5. Heap을 -Xmx 4G로 줬는데 컨테이너 메모리는 6G를 쓴다. 차이의 정체는?
6. Full GC가 1분에 한 번씩 도는 운영서 — 어떻게 진단을 시작하는가?
7. `OutOfMemoryError`의 8종을 구별할 수 있는가?
