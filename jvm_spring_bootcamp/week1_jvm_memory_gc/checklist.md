# Week 1 — 자가 점검 체크리스트

## 개념

- [ ] JVM 3대 구성요소를 종이에 그릴 수 있다 (ClassLoader / Runtime Data Area / Execution Engine)
- [ ] 5개 메모리 영역을 설명한다 (Heap / Stack / Metaspace / PC / Native)
- [ ] Heap의 Young/Old 분리 이유와 세대 가설을 안다
- [ ] PermGen → Metaspace 변경의 이유를 설명한다
- [ ] Stack에 무엇이 저장되는지 (primitive, 참조, frame) 안다
- [ ] String Pool과 intern()의 동작과 위험을 안다
- [ ] OutOfMemoryError 8종을 구별한다
- [ ] STW(Stop-The-World) 개념과 측정 단위를 안다
- [ ] Interpreter + JIT(C1/C2) 하이브리드 동작을 설명한다

## GC 알고리즘

- [ ] Serial/Parallel/G1/ZGC/Shenandoah의 차이를 표로 그린다
- [ ] G1의 region 모델·Mixed GC를 설명한다
- [ ] ZGC의 Colored Pointer·Load Barrier 개념을 안다
- [ ] CMS가 폐기된 이유를 설명한다
- [ ] 워크로드별 GC 추천을 한다 (배치/API/대용량/저용량)

## 도구

- [ ] `jps`로 JVM 프로세스 찾기
- [ ] `jstat -gcutil <pid> 1000`으로 실시간 GC 상태 확인
- [ ] `jcmd <pid> GC.heap_info`로 Heap 상태
- [ ] `jcmd <pid> Thread.print`로 스레드덤프
- [ ] `jcmd <pid> GC.heap_dump <file>`로 힙덤프
- [ ] `jstack <pid>`로 데드락 탐지
- [ ] `jcmd <pid> JFR.start ... JFR.dump ...`로 JFR 녹화
- [ ] Mission Control로 .jfr 분석
- [ ] MAT 또는 VisualVM으로 .hprof 분석
- [ ] async-profiler로 CPU flame graph 생성

## 로그·옵션

- [ ] `-Xlog:gc*:file=gc.log:time,uptime,level,tags` 옵션 의미를 안다
- [ ] GC 로그 한 줄을 해부해서 STW 시간·Heap 변화를 읽는다
- [ ] gceasy.io 사용
- [ ] 운영용 JVM 옵션 세트를 가지고 있다 (Heap, GC, Dump, Log)
- [ ] 컨테이너 환경의 `-XX:MaxRAMPercentage` 등을 안다

## 실습 결과물

- [ ] Lab 1 — JDK 설치, 도구 검증, 첫 GC 로그
- [ ] Lab 1 — 3가지 GC(G1/Parallel/ZGC) 로그 비교
- [ ] Lab 2 — 누수 코드로 OOM 재현
- [ ] Lab 2 — MAT로 누수 원인 진단 (static Map)
- [ ] Lab 2 — 누수 수정 후 안정 확인

## 자기 점검 질문

다음에 즉답하지 못하면 해당 Day를 다시 봅니다:

1. JDK 21의 기본 GC는?
   <details><summary>답</summary>G1 GC (JDK 9부터). JDK 23에서 ZGC가 기본 후보 논의 중.</details>

2. Young Generation의 Eden 영역이 가득 차면?
   <details><summary>답</summary>Minor GC 발생. 살아있는 객체는 Survivor로 복사. age 카운터 증가. 임계치 넘으면 Old로 승격.</details>

3. `OutOfMemoryError: Metaspace`가 자주 나면 어디를 의심?
   <details><summary>답</summary>동적 클래스 생성(CGLIB, Groovy, Javassist), ClassLoader 누수(WAR redeploy), `-XX:MaxMetaspaceSize` 부족.</details>

4. Full GC가 분당 10회 발생. 어디부터 진단?
   <details><summary>답</summary>① jstat -gcutil로 Old 사용률 확인 ② 단조증가면 누수 → 힙덤프 ③ 변동이 크면 Old 회수 안 되는 큰 객체 또는 humongous</details>

5. 컨테이너에서 `-Xmx4g`만 설정했더니 OOMKilled. 왜?
   <details><summary>답</summary>Heap 외에도 Metaspace, Direct Buffer, Stack, Native가 메모리 사용. Heap + 50% 추가 정도가 일반적. RAMPercentage 사용 권장.</details>

6. G1에서 Humongous Allocation 경고가 보임. 무엇이고 영향은?
   <details><summary>답</summary>Region 50% 이상인 큰 객체. G1이 Old에 직접 배치, GC 효율 떨어짐. Region size 키우거나 객체를 chunk로 분할.</details>

7. 스레드덤프에서 다수가 `BLOCKED`. 가장 먼저 볼 것은?
   <details><summary>답</summary>"waiting to lock"의 객체와 "locked"한 스레드. 그 스레드가 무엇을 하고 있나 확인. 외부 I/O 잡고 있으면 동기화 범위 문제.</details>

8. ZGC의 STW가 1ms 미만인 비결은?
   <details><summary>답</summary>Colored Pointer로 GC 상태를 포인터에 인코딩. Load Barrier가 stale 참조를 실시간 업데이트. 객체 이동을 앱과 동시에 진행.</details>

---

## 만약 다 통과했다면

Week 2 [ClassLoader·JIT·동시성](../week2_classloader_jit_concurrency/00_overview.md)로!
