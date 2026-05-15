# GC 로그 읽기 — 한 페이지 가이드

JDK 21 / G1 기준. 다른 GC도 비슷한 형식.

---

## 로그 켜기 (운영 권장)

```bash
-Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags:filecount=10,filesize=20M
```

---

## 한 줄 해부

```
[2026-05-15T10:23:45.123+0900][12.345s][info][gc      ] GC(42) Pause Young (Normal) (G1 Evacuation Pause) 256M->180M(512M) 23.456ms
    │                          │       │     │           │     │                  │                          │            │
    │                          │       │     │           │     │                  │                          │            └── STW 시간
    │                          │       │     │           │     │                  │                          └── before → after (total)
    │                          │       │     │           │     │                  └── 원인
    │                          │       │     │           │     └── Young (Normal/Mixed/Concurrent Start)
    │                          │       │     │           └── GC 번호
    │                          │       │     └── 태그
    │                          │       └── 레벨
    │                          └── 시작 후 12.345초
    └── 시각
```

---

## GC 종류별 시그널

### Young GC — 정상 (자주 일어남)

```
GC(1) Pause Young (Normal) (G1 Evacuation Pause) 200M->50M(512M) 15.0ms
```

- Eden + Survivor 정리
- STW 보통 10~50ms
- before − after = 회수된 양

### Mixed GC — Old 일부 정리

```
GC(50) Pause Young (Mixed) (G1 Evacuation Pause) 400M->200M(512M) 80.0ms
```

- Young + Old 일부
- STW Young보다 김 (50~200ms)
- Old 사용률 ↓

### Concurrent — STW 아님

```
GC(51) Concurrent Mark Cycle
GC(51) Concurrent Mark From Roots
GC(51) Concurrent Mark From Roots 50.0ms
GC(51) Pause Remark 350M->340M(512M) 5.0ms     ← 짧은 STW
GC(51) Concurrent Mark 200.0ms
GC(51) Pause Cleanup 340M->330M(512M) 3.0ms    ← 짧은 STW
```

앱과 동시 진행. 짧은 STW pause 2번 (Remark, Cleanup).

### Full GC — 위기

```
GC(100) Pause Full (G1 Compaction Pause) 500M->480M(512M) 5000.0ms
                                                          ^^^^^^^^
                                                          5초 STW!
```

- 모두 STW
- Old가 가득 차거나, allocation failure
- 운영서에선 **거의 없어야 정상**
- 자주 나면 → 누수 or Heap 부족

### Humongous Allocation

```
GC(20) Pause Young (Concurrent Start) (G1 Humongous Allocation)
GC(20)   To-space exhausted
```

Region 50%+ 객체. Old에 직접 할당, 단편화 위험.

---

## 읽는 순서 (위기 진단)

1. **Full GC가 있나?** → 가장 큰 위기 신호
2. **Heap After GC가 단조증가?** → 누수 신호 (gceasy.io 그래프)
3. **STW 평균·최대?** → latency 영향
4. **GC 빈도?** → 시작·종료 시간 간격

---

## 흔한 패턴별 진단

### "Young GC 빈번, Old 안 차"

```
GC(N) Pause Young ... 200M->50M(512M) 10ms
GC(N+1) Pause Young ... 220M->55M(512M) 12ms
GC(N+2) Pause Young ... 210M->52M(512M) 11ms
```

→ **정상**. Young Generation이 빠르게 채워지고 비워짐. throughput 손실 적음.

### "Old가 점점 차오름"

```
After GC: Old usage 30%
       ...
After GC: Old usage 45%
       ...
After GC: Old usage 70%
       ...
After GC: Old usage 95%   ← Mixed GC 트리거되어야 함
```

→ Mixed GC가 따라가지 못하거나 누수.

### "STW 길어짐"

```
GC(N)   Pause Young ... 20ms
GC(N+10) Pause Young ... 50ms
GC(N+20) Pause Young ... 200ms
GC(N+30) Pause Mixed ... 500ms
```

→ Young/Old 비율 검토, region size 확인, Pause goal 점검.

### "Concurrent Mark 자주 아니면 안 일어남"

```
GC(N) Concurrent Mark Cycle   ← 없으면 Mixed GC 트리거 안 됨
```

→ `-XX:InitiatingHeapOccupancyPercent` 너무 높음.

---

## gceasy.io 활용

`gc.log` 업로드 → 자동 분석.

### 봐야 할 지표 5가지

| 지표 | 좋음 | 주의 | 위험 |
|---|---|---|---|
| **Throughput** | > 99% | 95-99% | < 95% |
| **Pause Average** | < 50ms | 50-200ms | > 200ms |
| **Pause Max** | < 200ms | 200-1000ms | > 1s |
| **Full GC count** | 0 | 가끔 | 자주 |
| **Heap After GC trend** | 안정 | 천천히 증가 | 단조증가 |

### "Throughput"이란

```
Throughput = (전체 시간 - GC STW 합) / 전체 시간
```

99% = GC가 1% CPU만 사용. 운영 목표.

---

## 옵션·튜닝 가이드 (반복)

1. **측정** (현재 GC 로그 분석)
2. **가설** (어디가 문제?)
3. **한 가지** 옵션 변경
4. **재측정**
5. **비교** → 효과 있나?

---

## 함정

### 1) Heap을 무작정 늘림

```
"OOM 자주 → -Xmx 두 배"
```

→ Heap 크면 Full GC도 김. 그리고 누수면 시간만 미룸.

### 2) `MaxGCPauseMillis`를 너무 낮춤

```
"-XX:MaxGCPauseMillis=10"
```

→ JVM이 Young을 잘게 쪼개 자주 GC. Throughput 떨어짐.

### 3) `+DisableExplicitGC` 없이 외부 라이브러리

옛 RMI 등이 `System.gc()`를 주기적으로 호출 → Full GC 폭주. 운영서엔 항상 켜둘 것.

---

## 도구 비교

| 도구 | 강점 | 약점 |
|---|---|---|
| **gceasy.io** | 빠름·시각화 | 분량 제한 (무료) |
| **GCViewer** (CLI) | 오프라인 | 옛 UI |
| **JMC (JFR)** | 가장 풍부 | JFR 필요 |
| **VisualVM** | 무료, 친숙 | 큰 덤프 버거움 |

---

## 자주 보는 GC 로그 키워드

| 키워드 | 의미 |
|---|---|
| **Pause Young** | Young 영역 GC (STW) |
| **Pause Mixed** | Young + Old 일부 (STW) |
| **Pause Full** | 전체 (STW, 위기) |
| **Concurrent Mark** | 동시 mark (STW 아님) |
| **Evacuation** | 살아있는 객체 복사 |
| **Compaction** | 단편화 정리 |
| **Humongous** | 큰 객체 |
| **Allocation Failure** | 할당 실패 |
| **Metadata GC** | Metaspace |
| **To-space exhausted** | 위기. Young/Survivor 부족 |
| **G1 Evacuation Pause** | G1의 살아있는 객체 복사 |
| **Concurrent Start** | concurrent 시작 |

---

## 한 줄 진단 명령

```bash
# Throughput 추정
grep "Pause" gc.log | awk '{sum += $NF} END {print sum/1000 " sec total STW"}'

# Full GC 빈도
grep -c "Pause Full" gc.log

# 평균 STW
grep "Pause Young" gc.log | awk '{print $NF}' | sed 's/ms//' | awk '{s+=$1; n++} END {print s/n " ms avg"}'

# Heap 사용 추이
grep "Pause Young" gc.log | awk '{print $(NF-1)}'
# 결과 예: 200M->50M(512M)
```
