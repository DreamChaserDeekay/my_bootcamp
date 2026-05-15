# Day 4 — GC 로그·튜닝

## 한 줄 요약

GC 튜닝은 "GC 로그 켜고 측정 → 한 가지 변경 → 재측정"의 사이클. **추측 금지**.

## 학습 목표

- [ ] GC 로그를 켜고 의미를 읽을 수 있다
- [ ] 핵심 JVM 옵션 20개를 외운다 (Heap, GC, Dump, Log)
- [ ] 튜닝 사이클 5단계를 따라 한다
- [ ] GCViewer · GCEasy · gceasy.io로 로그를 시각화한다
- [ ] 컨테이너 환경의 JVM 옵션을 안다

---

## GC 로그 — JDK 11+ 통합 로깅

JDK 9부터 `-Xlog`로 통합. 옛 `-XX:+PrintGCDetails` 같은 옵션은 폐기.

### 기본 GC 로그

```bash
java -Xlog:gc:file=gc.log MyApp
```

### 자세한 GC 로그 (운영 권장)

```bash
java -Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=10,filesize=20M MyApp
```

- `gc*` — GC 관련 모든 태그
- `time,uptime,level,tags` — 출력 메타데이터 (절대시간, 시작 후 경과, 로그 레벨, 태그)
- `filecount=10,filesize=20M` — 20MB짜리 10개로 회전 (운영 디스크 보호)

### 로그 한 줄 해부

```
[2026-05-15T10:23:45.123+0900][12.345s][info][gc      ] GC(42) Pause Young (Normal) (G1 Evacuation Pause) 256M->180M(512M) 23.456ms
    │                          │       │     │           │     │                  │                          │            │
    │                          │       │     │           │     │                  │                          │            └── STW 시간
    │                          │       │     │           │     │                  │                          └── 변화: Heap 256M → 180M (총 512M 중)
    │                          │       │     │           │     │                  └── G1 Evacuation 단계
    │                          │       │     │           │     └── Young GC (Normal)
    │                          │       │     │           └── GC 번호 (#42)
    │                          │       │     └── 태그
    │                          │       └── 레벨
    │                          └── 시작 후 12.345초
    └── 절대 시각
```

### 자주 보는 GC 로그 라인

```
# Young GC — Eden+Survivor만
GC(1) Pause Young (Normal) (G1 Evacuation Pause) 200M->50M(512M) 15.0ms

# Mixed GC — Young + Old 일부
GC(50) Pause Young (Mixed) (G1 Evacuation Pause) 400M->200M(512M) 80.0ms

# Concurrent Mark — STW 아님
GC(51) Concurrent Mark Cycle
GC(51) Concurrent Mark From Roots
GC(51) Concurrent Mark From Roots 50.0ms

# Full GC — 위급
GC(100) Pause Full (G1 Compaction Pause) 500M->480M(512M) 5000.0ms
                                                          ^^^^^^^
                                                          5초 STW = 비상

# Humongous 할당
GC(20) Pause Young (Concurrent Start) (G1 Humongous Allocation)
```

### 작은 키워드 차이

| 키워드 | 의미 |
|---|---|
| **Young** | Eden + Survivor만 |
| **Mixed** | Young + Old 일부 |
| **Full** | 모두 STW (위기) |
| **Concurrent** | 앱과 동시 (STW X) |
| **Evacuation** | 살아있는 객체 다른 region으로 복사 |
| **Compaction** | 단편화 정리 (보통 Full에서) |
| **Humongous** | 큰 객체 (region 50%+) |
| **Allocation Failure** | 할당 실패 → GC 유발 |
| **Metadata GC** | Metaspace 정리 |

---

## 핵심 JVM 옵션 30개

### Heap

```bash
-Xms4g                              # 초기 Heap
-Xmx4g                              # 최대 Heap
# 권장: -Xms == -Xmx (재할당 비용 회피)

-Xss512k                            # 스레드 스택 크기
-XX:MaxMetaspaceSize=512m           # Metaspace 한도
-XX:MaxDirectMemorySize=1g          # Direct Buffer 한도
-XX:ReservedCodeCacheSize=256m      # JIT 코드 캐시
```

### GC 선택

```bash
-XX:+UseG1GC                        # G1 (JDK 9+ 기본)
-XX:+UseZGC                         # ZGC
-XX:+ZGenerational                  # ZGC generational mode (JDK 21+)
-XX:+UseParallelGC                  # 처리량 우선
-XX:+UseShenandoahGC                # Shenandoah
```

### G1 튜닝

```bash
-XX:MaxGCPauseMillis=200            # 목표 STW (기본 200)
-XX:G1HeapRegionSize=16m            # region 크기 (1m~32m)
-XX:G1NewSizePercent=20             # Young 최소 비율
-XX:G1MaxNewSizePercent=40          # Young 최대 비율
-XX:InitiatingHeapOccupancyPercent=45  # Mixed GC 시작 임계치
-XX:G1MixedGCCountTarget=8          # Mixed GC 분할 회수
```

### 로깅

```bash
-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=10,filesize=20M
-XX:+PrintFlagsFinal                # 모든 옵션 최종 값 출력 (디버그용)
```

### OOM 시 자동 덤프 (필수)

```bash
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/heapdumps/
-XX:OnOutOfMemoryError="kill -9 %p"  # OOM 시 프로세스 죽이기 (k8s가 재시작)
```

### Native Memory Tracking

```bash
-XX:NativeMemoryTracking=summary    # 또는 detail
# 이후 jcmd <pid> VM.native_memory summary
```

### 컨테이너 친화 (JDK 10+)

```bash
-XX:+UseContainerSupport            # 기본 켜짐 (cgroup 메모리·CPU 인식)
-XX:MaxRAMPercentage=75             # 컨테이너 메모리의 75%를 Heap으로
-XX:InitialRAMPercentage=50
```

> **컨테이너에서는 `-Xmx` 직접 지정보다 RAMPercentage 권장**. 컨테이너 limit이 바뀌어도 따라감.

---

## 튜닝 사이클 5단계

```
1. 측정 (Baseline)
       │
       ▼
2. 가설 (Hypothesis)
       │
       ▼
3. 변경 한 가지 (Change ONE)
       │
       ▼
4. 재측정
       │
       ▼
5. 비교 → 효과 있나? → Yes: 유지 / No: 롤백
       │
       └── 다음 가설로
```

### 단계 1 — 측정

운영에 가까운 환경에서 며칠 돌리며 GC 로그 수집.

확인 항목:
- 총 GC 시간 비율 (`gc_time / total_time`) — 5% 미만이면 OK
- Young GC 평균 STW
- Mixed/Full GC 발생 여부
- Heap 사용 추이 (계속 자라면 누수)

### 단계 2 — 가설

| 증상 | 가설 |
|---|---|
| Full GC가 자주 | Heap 부족 또는 메모리 누수 |
| Young GC 평균 100ms+ | Young 영역이 너무 큼 또는 객체가 너무 많이 살아남음 |
| Mixed GC가 100ms+ | Old의 garbage가 적음, region 작음 |
| Heap이 계속 증가 | 누수 (덤프 분석 필요) |

### 단계 3 — 한 가지만 변경

```bash
# ❌ 한꺼번에 여러 옵션
-XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=8m -XX:InitiatingHeapOccupancyPercent=35

# ✅ 한 번에 하나
# 우선 -XX:MaxGCPauseMillis=100만 변경
```

### 단계 4-5 — 재측정·비교

같은 워크로드로 같은 시간 측정. STW 합·throughput 비교.

---

## GC 로그 분석 도구

### 1) GCViewer (오프라인, 무료)

```bash
# JAR 다운로드
java -jar gcviewer-1.36.jar gc.log

# 통계
# - Throughput: 96.5%       ← 95% 미만이면 튜닝 필요
# - Pause avg: 12ms
# - Pause max: 234ms
# - Full GC count: 0        ← 0이어야 정상
```

### 2) GCeasy.io (온라인)

- gc.log 업로드 → 시각화
- 무료 (분량 제한 있음)
- 추천 옵션도 제안

### 3) JDK Mission Control (JFR)

```bash
# JFR 녹화 (1분)
jcmd <pid> JFR.start duration=60s filename=app.jfr

# JMC로 열기
jmc app.jfr
```

JFR이 GC 로그보다 훨씬 풍부 — CPU 프로파일, 메서드별 hot spot, lock contention 등.

---

## ❌ 나쁜 튜닝 vs ✅ 좋은 튜닝

### 1) "Latency 좋게 하려고 STW 짧게"

```bash
# ❌ MaxGCPauseMillis를 무작정 낮춤
-XX:MaxGCPauseMillis=10

# 결과: GC가 너무 자주 → throughput 떨어짐 → 결과적으로 느려짐
```

```bash
# ✅ 워크로드에 맞춤
# 결제 API: 100ms 정도가 합리적
-XX:MaxGCPauseMillis=100
# ZGC로 옮기는 게 빠른 길일 수도 있음
```

### 2) "Heap을 두 배로 늘리면 GC 줄겠지"

```bash
# ❌ -Xmx 8g → 16g
# Heap이 클수록 한 번 GC의 작업량도 큼 → STW 길어질 수 있음
```

```bash
# ✅ GC 횟수가 많다면
# 1. 누수 확인
# 2. -XX:NewRatio 조정 (Young 키워서 Old 승격 줄임)
# 3. 그래도 부족하면 Heap 증가
```

### 3) "운영서 GC 로그 끔 (디스크 아낌)"

```bash
# ❌ 로그 X
# 사고나면 원인 못 찾음

# ✅ 회전 정책으로 항상 켬
-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=10,filesize=20M
# 합 200MB만 차지 (대부분 디스크에 무시할 수준)
```

---

## 운영용 옵션 세트 (템플릿)

### 일반 API 서버 (Heap 4G, latency 100ms 목표)

```bash
java \
  -Xms4g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/heapdumps/ \
  -Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags:filecount=10,filesize=20M \
  -XX:NativeMemoryTracking=summary \
  -jar app.jar
```

### 컨테이너 (k8s)

```yaml
env:
  - name: JAVA_OPTS
    value: >-
      -XX:+UseG1GC
      -XX:MaxRAMPercentage=75
      -XX:MaxGCPauseMillis=100
      -XX:+HeapDumpOnOutOfMemoryError
      -XX:HeapDumpPath=/var/log/heapdumps/
      -Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags:filecount=10,filesize=20M
resources:
  limits:
    memory: 4Gi                    # MaxRAMPercentage가 이걸 본다
```

### 대용량 Heap (32G+, latency 민감)

```bash
java \
  -Xms32g -Xmx32g \
  -XX:+UseZGC -XX:+ZGenerational \
  -XX:+HeapDumpOnOutOfMemoryError \
  -Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=10,filesize=20M \
  -jar app.jar
```

### 배치 (throughput 우선)

```bash
java \
  -Xms8g -Xmx8g \
  -XX:+UseParallelGC \
  -XX:ParallelGCThreads=8 \
  -Xlog:gc:file=/var/log/gc.log:time:filecount=5,filesize=20M \
  -jar batch.jar
```

---

## 실습 (Hands-on)

### 1단계 — GC 로그 수집

```java
// AllocationLoad.java — 부하 만들기
import java.util.*;

public class AllocationLoad {
    public static void main(String[] args) throws Exception {
        List<byte[]> survivors = new ArrayList<>();
        for (int i = 0; i < 100000; i++) {
            byte[] data = new byte[100 * 1024];   // 100KB
            if (i % 10 == 0) survivors.add(data); // 10%만 살아남음
            if (survivors.size() > 1000) survivors.remove(0);
            if (i % 1000 == 0) Thread.sleep(10);
        }
    }
}
```

```bash
javac AllocationLoad.java
java -Xms256m -Xmx256m \
     -Xlog:gc*:file=gc.log:time,uptime,level,tags \
     AllocationLoad

# 끝나면 gc.log 확인
```

### 2단계 — gceasy.io 분석

`gc.log`를 [gceasy.io](https://gceasy.io)에 업로드.

확인:
- Throughput %
- Avg/Max pause
- Heap utilization trend

### 3단계 — 옵션 변경 비교

```bash
# A. 기본
java -Xms256m -Xmx256m -Xlog:gc*:file=gc_a.log AllocationLoad

# B. Pause 목표 50ms
java -Xms256m -Xmx256m -XX:MaxGCPauseMillis=50 \
     -Xlog:gc*:file=gc_b.log AllocationLoad

# C. Parallel GC
java -Xms256m -Xmx256m -XX:+UseParallelGC \
     -Xlog:gc*:file=gc_c.log AllocationLoad

# 세 로그 비교 — STW 평균/최대, throughput, GC 횟수
```

### 4단계 — Pinpoint/Scouter 연결 (선택)

운영 환경이라면 APM 붙이기. Pinpoint 에이전트로:

```bash
java -javaagent:/path/to/pinpoint-bootstrap.jar \
     -Dpinpoint.agentId=app-1 \
     -Dpinpoint.applicationName=MyApp \
     -jar app.jar
```

Pinpoint 대시보드에서 **Heap·GC·Thread**가 실시간으로 보임.

---

## 더 읽어볼 자료

- 📘 『Optimizing Java』 (Evans, Verburg) — 8~10장
- 📘 『JVM Performance Engineering』 (Monica Beckwith) — JDK 21 기반 최신
- 🔗 [JEP 158: Unified JVM Logging](https://openjdk.org/jeps/158) — `-Xlog`의 출처
- 🔗 [GCeasy.io 무료 분석](https://gceasy.io)
- 🔗 [Oracle G1 Tuning](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html)
- 🎓 Tagir Valeev — "Performance pitfalls"
- 🎓 [Naver D2 — JVM GC와 튜닝](https://d2.naver.com/helloworld/1329) (한국어, 약간 오래됨)
