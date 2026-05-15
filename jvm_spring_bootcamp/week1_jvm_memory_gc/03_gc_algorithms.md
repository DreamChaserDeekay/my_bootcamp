# Day 3 — GC 알고리즘

## 한 줄 요약

GC는 **Heap에서 안 쓰는 객체를 자동으로 정리**하는 일. 알고리즘마다 **처리량(throughput)**과 **응답성(latency, STW 시간)**의 트레이드오프가 다르다 — Serial/Parallel은 throughput, G1/ZGC/Shenandoah는 latency.

## 학습 목표

- [ ] STW(Stop-The-World), Throughput, Latency 개념을 안다
- [ ] Mark-Sweep, Mark-Compact, Copy 알고리즘의 차이를 안다
- [ ] G1의 region 개념과 Mixed GC를 설명한다
- [ ] ZGC의 Colored Pointer / Load Barrier로 어떻게 sub-ms STW를 달성하는지 안다
- [ ] 5가지 GC를 워크로드에 맞게 고를 수 있다
- [ ] CMS가 왜 폐기됐는지(JDK 9 deprecate, 14 제거) 안다

---

## GC가 풀어야 하는 문제

### 1. Reachability — 누가 살아있는가?

**GC Root**에서 **참조 그래프**를 따라가서 도달 가능한 객체만 "살아있음".

```
GC Roots:
  - 스레드의 스택 (Local 변수, 메서드 파라미터)
  - static 변수
  - JNI 참조
  - 동기화 잠금 객체
  - 시스템 ClassLoader

       ┌──────────────┐
       │   GC Root    │
       │  (Stack)     │
       └──────┬───────┘
              │ 참조
              ▼
       ┌──────────────┐         ┌──────────────┐
       │   Customer   │────────▶│    Order     │   ← 살아있음
       └──────────────┘         └──────────────┘

                                ┌──────────────┐
                                │   Old Object │   ← 죽음 (참조 끊김)
                                └──────────────┘
                                       │
                                       ▼
                                ┌──────────────┐
                                │   Another    │   ← 죽음 (참조 사슬이 끊김)
                                └──────────────┘
```

### 2. Memory Reclaim — 어떻게 청소하는가?

| 알고리즘 | 동작 | 장점 | 단점 |
|---|---|---|---|
| **Mark-Sweep** | 살아있는 거 mark → 죽은 거 sweep | 단순 | 메모리 단편화 |
| **Mark-Compact** | mark → 살아있는 것을 한쪽에 모음 | 단편화 X | 이동 비용 |
| **Copy** | 한 영역의 살아있는 것을 다른 영역으로 복사 | 빠름, 단편화 X | 메모리 2배 필요 |
| **Generational** | Young은 Copy, Old는 Mark-Compact | 세대 가설 활용 | 구현 복잡 |

---

## 주요 GC 알고리즘 5종

### 1) Serial GC

가장 단순. **한 스레드**가 모든 일을 하면서 다른 모든 스레드 정지.

```bash
java -XX:+UseSerialGC MyApp
```

| 항목 | 값 |
|---|---|
| **스레드** | 1개 |
| **STW** | 매우 김 (수백 ms~) |
| **적합** | 클라이언트, 작은 앱, 단일 코어 |
| **부적합** | 서버 |

### 2) Parallel GC (옛 Throughput Collector)

**여러 스레드**가 GC를 병렬로 수행. JDK 8까지 서버 기본.

```bash
java -XX:+UseParallelGC -XX:ParallelGCThreads=8 MyApp
```

| 항목 | 값 |
|---|---|
| **목표** | 최대 throughput (GC가 차지하는 CPU 비율 최소화) |
| **STW** | 김 (수십~수백 ms) |
| **적합** | 배치, 분석 작업, latency 둔감 워크로드 |
| **부적합** | 응답 시간 민감한 API |

### 3) CMS (Concurrent Mark Sweep) — **폐기**

Old Generation의 mark·sweep을 **앱과 동시 실행** (STW 최소화). 단편화·복잡성 때문에 JDK 9에서 deprecate, **JDK 14에서 제거**.

```bash
# 이제 못 씀
java -XX:+UseConcMarkSweepGC MyApp   # JDK 14+에서 오류
```

> 옛 운영서를 인수받으면 GC 옵션부터 확인. CMS이면 **무조건 G1으로 교체**.

### 4) G1 GC (Garbage First) — **JDK 9~21 기본**

**Region 기반**. Heap을 수백 개 region으로 나누고, **garbage가 많은 region 우선** 청소.

```bash
java -XX:+UseG1GC MyApp              # JDK 9+ 기본 (생략 가능)
```

#### Region 모델

```
   ┌──────────────────────────────────────────────────┐
   │           Heap (예: 4GB)                          │
   │                                                  │
   │   각 region = 1~32MB (heap 크기에 따라 자동)      │
   │                                                  │
   │  ┌──┐┌──┐┌──┐┌──┐┌──┐┌──┐┌──┐┌──┐                 │
   │  │E │ │S │ │O │ │E │ │  │ │O │ │H │ │E │  ...    │
   │  └──┘└──┘└──┘└──┘└──┘└──┘└──┘└──┘                 │
   │                                                  │
   │  E = Eden                                        │
   │  S = Survivor                                    │
   │  O = Old                                         │
   │  H = Humongous (region 절반 이상의 큰 객체)        │
   └──────────────────────────────────────────────────┘
```

#### GC 종류

| GC | 무엇 |
|---|---|
| **Young GC** | Eden + Survivor만 정리 (Minor) |
| **Mixed GC** | Young + Old 일부 region 정리 |
| **Full GC** | 비상시 (튜닝 실패 시 발생) — 모두 STW |

> **목표**: Full GC를 피해야 한다. Full GC가 자주 발생하면 알고리즘이 따라가지 못한다는 신호.

#### Pause Time Goal

```bash
-XX:MaxGCPauseMillis=200             # 목표 STW (200ms, 기본)
                                     # JVM이 이 목표 맞추려고 region 수 조절
```

이 옵션이 G1의 강점 — **목표를 정하면 알아서 맞춤**.

### 5) ZGC — JDK 15부터 production-ready

**sub-ms STW**가 목표. Heap 크기와 무관하게 STW < 1ms (보통 수십 µs).

```bash
java -XX:+UseZGC MyApp
java -XX:+UseZGC -XX:+ZGenerational MyApp  # JDK 21+ generational mode
```

#### 비결 — Colored Pointer + Load Barrier

```
   포인터(64-bit)의 상위 비트에 GC 상태(컬러)를 박아 넣음
   ┌──────────────┬────────────────────────────────────┐
   │  컬러(4-5b)  │      실제 주소                       │
   └──────────────┴────────────────────────────────────┘

   객체 참조를 읽을 때 Load Barrier가:
   1. 컬러 확인
   2. 컬러가 stale이면 → 새 주소로 자동 업데이트
   3. 정상 컬러면 그대로 사용

   → GC가 백그라운드로 이동시켜도 앱은 모름
   → STW가 거의 없음
```

| 항목 | 값 |
|---|---|
| **STW** | 보통 < 1ms (수십~수백 µs) |
| **Heap** | 8MB ~ 16TB |
| **CPU 오버헤드** | ~15% (Load Barrier) |
| **적합** | 큰 Heap + latency 민감 API |
| **부적합** | 작은 Heap (오버헤드만 큼) |

### 6) Shenandoah — RedHat

ZGC와 비슷한 컨셉 (concurrent compaction). JDK 12+ (Temurin 등에 포함).

```bash
java -XX:+UseShenandoahGC MyApp
```

ZGC와 거의 동급. 어느 쪽이든 비슷한 결과.

---

## 비교표 — 어느 GC를 고를까

| GC | STW | Throughput | Heap 한도 | 추천 워크로드 |
|---|---|---|---|---|
| Serial | 매우 김 | 낮음 | < 100MB | 작은 CLI |
| Parallel | 김 (100~수백ms) | 가장 높음 | ~8G | 배치, 분석 |
| **G1** | 100~200ms | 높음 | 1G~16G | **API/Web 서버 기본** |
| ZGC | < 1ms | 보통 (~85%) | ~16TB | 큰 Heap, 응답 민감 |
| Shenandoah | < 10ms | 보통 | 큼 | ZGC 대안 |

### 결정 가이드

```
Heap < 4G ?
├─ 응답시간 중요 ─▶ G1 (JDK 17+) 또는 ZGC
└─ 처리량 중요   ─▶ Parallel

Heap 4~32G ?
├─ STW < 100ms 필요 ─▶ G1 + 튜닝 또는 ZGC
└─ STW 200ms OK     ─▶ G1 기본 옵션

Heap > 32G ?
└─ 거의 무조건 ZGC 또는 Shenandoah
```

### 금융권 실용 가이드

| 워크로드 | 추천 |
|---|---|
| 결제 API (낮은 latency) | G1 + `MaxGCPauseMillis=100` |
| 야간 배치 | Parallel |
| 시세 스트리밍 (대용량 Heap) | ZGC |
| 레거시 모놀리스 (큰 Heap) | ZGC로 마이그레이션 검토 |

---

## STW (Stop-The-World)

GC가 메모리를 정리하는 동안 **모든 애플리케이션 스레드 정지**. GC 종류·구간마다 STW 길이가 다르다.

```
                  STW                    STW (mixed)
   ┌──────────────────────────────────────────────────▶ 시간
   │  앱 실행     │ G1 Young GC │   앱 실행      │ G1 Mixed │ 앱 실행 ...
   │            │   ~50ms     │                │  ~100ms  │
```

### STW 측정 — GC 로그

```
[3.456s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause)
                   45M->8M(256M) 12.345ms
                         │                 │
                         │                 └── STW 시간
                         └── Heap 사용량 변화
```

---

## ❌ 잘못된 튜닝 vs ✅ 올바른 튜닝

### 1) GC 옵션 무지성 복붙

```bash
# ❌ 인터넷에서 본 옵션 100개 한꺼번에
-XX:+UseG1GC
-XX:+UseStringDeduplication
-XX:+ParallelRefProcEnabled
-XX:G1HeapRegionSize=4M
-XX:MaxGCPauseMillis=50
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1ReservePercent=20
# ... 어느 옵션이 어떤 효과인지 모름

# ✅ 기본부터 시작 + 측정
-Xms4g -Xmx4g                        # heap만 고정
-XX:+UseG1GC                         # JDK 17+ 기본이므로 생략 가능
-Xlog:gc*:file=gc.log:time,uptime    # GC 로그
# → 며칠 운영 → 로그 분석 → 그 다음에 한 가지씩 바꿈
```

### 2) GC 횟수에 집착

```
# ❌ "Young GC가 분당 100번이라 많다"
# 사실 Young GC는 빠르면 짧음. 빈도가 문제 X. STW 합계가 문제.

# ✅ "처리량 손실율 = GC STW 합 / 전체 시간"이 5% 미만이면 정상
# 1% 미만이면 매우 좋음
```

---

## 운영 사고 사례

### 사례 1 — Full GC 폭주 (Old 100%)

**증상**: 분당 Full GC 10번, 매번 5초 STW. API timeout.

**원인**: Old Generation이 가득 차 회수 못 함. **메모리 누수** — `static List`가 점점 자람.

**진단**:
```bash
jstat -gcutil <pid> 1000             # 1초마다 GC 상태
# S0 S1 E O M CCS YGC YGCT FGC FGCT GCT
# 0  0 12 99 99 95  100 5.0  20  100 105   ← Old 99%, Full GC 빈번
```

**조치**: 힙덤프 → MAT로 leak suspect → `static List` 찾아 제거.

### 사례 2 — Humongous Object (G1)

**증상**: G1에서 갑자기 Full GC가 발생. 평소엔 안 일어났음.

**원인**: 어떤 batch 작업이 **region의 50% 넘는 큰 byte[] 할당**. G1이 이를 Humongous로 분류, Old에 직접 할당 → 누적 → Full GC.

**진단**:
```
-Xlog:gc+humongous=trace
```

**조치**: 큰 객체를 chunk로 분할. 또는 region 크기 키움 (`-XX:G1HeapRegionSize=16m`).

### 사례 3 — JDK 17로 업그레이드 후 latency 개선

**증상**: 같은 코드, JDK 11(G1) → JDK 17(G1) 옮겼더니 P99가 200ms → 50ms.

**원인**: G1이 JDK 11 → 17 사이 크게 개선. 옵션 그대로도 latency 향상.

**시사**: **JDK 업그레이드는 좋은 GC 튜닝**.

---

## 실습 (Hands-on)

### 1단계 — 현재 JVM의 GC 확인

```bash
java -XX:+PrintFlagsFinal -version | findstr /i "UseG1GC UseZGC UseParallelGC"
# JDK 21에선 UseG1GC = true가 기본
```

### 2단계 — GC 로그 켜기

```bash
java -Xlog:gc*:file=gc.log:time,uptime:filecount=5,filesize=10M -Xmx256m HeapOOM
```

옵션 해석:
- `-Xlog:gc*` — GC 관련 모든 로그
- `:file=gc.log` — 파일로 출력
- `:time,uptime` — 시간 정보 포함
- `:filecount=5,filesize=10M` — 10MB 5개로 회전

### 3단계 — GC 종류별 비교

```bash
# Parallel
java -XX:+UseParallelGC -Xlog:gc -Xmx512m Workload

# G1
java -XX:+UseG1GC -Xlog:gc -Xmx512m Workload

# ZGC
java -XX:+UseZGC -Xlog:gc -Xmx512m Workload

# 각각 GC 로그 비교 — STW 시간, 빈도
```

### 4단계 — Pause Time 목표

```bash
# G1에 50ms 목표 강제
java -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -Xlog:gc -Xmx512m Workload
# → JVM이 Young region 작게, GC 자주 → STW 짧음
```

---

## 더 읽어볼 자료

- 📘 『The Garbage Collection Handbook』 2nd ed. (Jones, Hosking, Moss) — 거의 모든 GC 알고리즘 다룸
- 📘 『Optimizing Java』 (Evans, Verburg, O'Reilly)
- 🔗 [JEP 333: ZGC: A Scalable Low-Latency GC](https://openjdk.org/jeps/333)
- 🔗 [JEP 439: Generational ZGC](https://openjdk.org/jeps/439) — JDK 21
- 🔗 Aleksey Shipilev — [GC sequential phases](https://shipilev.net/jvm/anatomy-quarks/)
- 🔗 [Oracle GC Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/)
- 🎓 InfoQ — Per Liden, "Designing ZGC"
