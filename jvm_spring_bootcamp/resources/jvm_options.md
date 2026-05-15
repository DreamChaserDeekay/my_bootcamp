# JVM 옵션 치트시트

JDK 21 기준. 자주 쓰는 것만 추렸음 — 외워둘 가치 있는 셋.

---

## 1. Heap

```bash
-Xms4g                              # 초기 Heap
-Xmx4g                              # 최대 Heap (= -Xms 권장)
-Xss512k                            # 스레드 스택
-XX:MaxMetaspaceSize=512m
-XX:MaxDirectMemorySize=1g          # NIO Direct Buffer
-XX:ReservedCodeCacheSize=256m      # JIT 코드 캐시 (기본 240m)
-XX:CompressedClassSpaceSize=1g
```

### 컨테이너 친화

```bash
-XX:+UseContainerSupport            # 기본 켜짐 (cgroup 인식)
-XX:MaxRAMPercentage=75             # 컨테이너 메모리의 75%를 Heap
-XX:InitialRAMPercentage=50
-XX:MinRAMPercentage=50
```

---

## 2. GC

```bash
# Garbage Collector 선택
-XX:+UseG1GC                        # JDK 9+ 기본
-XX:+UseZGC                         # 큰 Heap + 낮은 latency
-XX:+ZGenerational                  # ZGC generational (JDK 21+)
-XX:+UseParallelGC                  # 처리량 우선
-XX:+UseSerialGC                    # 작은 앱
-XX:+UseShenandoahGC                # RedHat

# G1 튜닝
-XX:MaxGCPauseMillis=200            # 목표 STW (기본 200)
-XX:G1HeapRegionSize=16m            # 1m~32m
-XX:G1NewSizePercent=20
-XX:G1MaxNewSizePercent=40
-XX:InitiatingHeapOccupancyPercent=45  # Mixed GC 시작
-XX:G1MixedGCCountTarget=8
-XX:G1ReservePercent=10
-XX:+UseStringDeduplication         # 중복 String 제거

# Parallel GC
-XX:ParallelGCThreads=8

# 명시적 GC 비활성
-XX:+DisableExplicitGC              # System.gc() 무시
```

---

## 3. 로깅 (JDK 11+ unified)

```bash
# 기본 GC 로그
-Xlog:gc:file=gc.log

# 자세한 GC 로그 (운영 권장)
-Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags:filecount=10,filesize=20M

# 추가 카테고리
-Xlog:gc+ergo*=debug                # heuristic
-Xlog:gc+humongous=trace            # G1 humongous
-Xlog:gc+phases=debug
-Xlog:safepoint                     # safepoint 분석

# 시작 로그 끄기 (운영 시 잡음)
-Xlog:gc:none

# 클래스 로딩 로그
-Xlog:class+load=info               # 로딩되는 클래스
```

---

## 4. OOM 대응 (필수)

```bash
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/heapdumps/
-XX:OnOutOfMemoryError="kill -9 %p"      # OOM 즉시 종료 (k8s 재시작)
-XX:ErrorFile=/var/log/hs_err_pid%p.log
```

---

## 5. JIT 컴파일

```bash
# 정상 (Tiered C1+C2, 기본)
# 옵션 없음

# 디버그 — 컴파일 활동
-XX:+PrintCompilation

# Inline 출력
-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining

# C1만 (느림, 빠른 시작)
-XX:-TieredCompilation

# Interpreter 강제 (매우 느림, 디버깅용)
-Xint

# 처음부터 모두 컴파일 (시작 오래, 이후 빠름)
-Xcomp
```

---

## 6. 진단

```bash
# Native Memory Tracking
-XX:NativeMemoryTracking=summary    # 또는 detail

# Flight Recorder (낮은 오버헤드)
-XX:StartFlightRecording=duration=60s,filename=app.jfr
-XX:StartFlightRecording=disk=true,maxage=1h,maxsize=500m       # 항상 녹화

# Pinning 추적 (Virtual Thread)
-Djdk.tracePinnedThreads=full         # 또는 short

# Class loading 자세히
-verbose:class
```

---

## 7. 보안·격리

```bash
# SecurityManager (JDK 17 deprecate, 21 removed)
# -Djava.security.manager — 사용 X

# 인증서 truststore
-Djavax.net.ssl.trustStore=/path/to/truststore.jks
-Djavax.net.ssl.trustStorePassword=...
```

---

## 8. 운영 권장 세트 (4G heap, JDK 21, G1)

```bash
java \
  -Xms4g -Xmx4g \
  -XX:MaxMetaspaceSize=512m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/heapdumps/ \
  -Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags:filecount=10,filesize=20M \
  -XX:NativeMemoryTracking=summary \
  -XX:StartFlightRecording=disk=true,maxage=1h,maxsize=500m \
  -Djdk.tracePinnedThreads=short \
  -jar app.jar
```

---

## 9. k8s + 컨테이너 (선호)

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
      -XX:NativeMemoryTracking=summary
      -XX:StartFlightRecording=disk=true,maxage=1h,maxsize=500m
resources:
  limits:
    memory: 4Gi      # MaxRAMPercentage가 75% × 4Gi ≈ 3Gi Heap
```

---

## 10. ZGC 운영 (큰 Heap)

```bash
java \
  -Xms32g -Xmx32g \
  -XX:+UseZGC -XX:+ZGenerational \
  -XX:+HeapDumpOnOutOfMemoryError \
  -Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=10,filesize=20M \
  -jar app.jar
```

---

## 11. 배치 (throughput)

```bash
java \
  -Xms8g -Xmx8g \
  -XX:+UseParallelGC \
  -XX:ParallelGCThreads=$(nproc) \
  -Xlog:gc:file=/var/log/gc.log:time:filecount=3,filesize=10M \
  -jar batch.jar
```

---

## 12. 옵션 확인·진단

```bash
# 모든 옵션 (긴 목록)
java -XX:+PrintFlagsFinal -version

# 특정 옵션
java -XX:+PrintFlagsFinal -version | findstr UseG1GC

# 동작 중 JVM의 현재 옵션
jcmd <pid> VM.flags
jcmd <pid> VM.system_properties
jcmd <pid> VM.command_line
```

---

## 13. 옛 deprecate 주의

| 옛 옵션 | 새 옵션 |
|---|---|
| `-XX:+PrintGCDetails` | `-Xlog:gc*` |
| `-XX:+PrintGCDateStamps` | `-Xlog:gc*::time` |
| `-Xloggc:file.log` | `-Xlog:gc*:file=file.log` |
| `-XX:+UseConcMarkSweepGC` | **제거** (JDK 14). G1로 |
| `-XX:PermSize` | **제거**. Metaspace |
| `-XX:+UseG1GC` | 기본이라 명시 불필요 |
| `-XX:+CMSParallelRemarkEnabled` | **제거** |

---

## 14. 디버깅 트릭

```bash
# 클래스가 어디서 로드되는지
-Xlog:class+load=info | grep MyClass

# safepoint 시간 (응답 지연 추적)
-Xlog:safepoint

# 다른 JVM에서 같은 옵션 비교
diff <(jcmd <pid1> VM.flags) <(jcmd <pid2> VM.flags)
```

---

## 자주 묻는 옵션 5가지

| 질문 | 옵션 |
|---|---|
| "OOM 자동 덤프" | `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/path` |
| "GC 로그" | `-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=20M` |
| "컨테이너에서 Heap" | `-XX:MaxRAMPercentage=75` (직접 -Xmx보다) |
| "Virtual Thread Pinning" | `-Djdk.tracePinnedThreads=short` |
| "JFR 항상 녹화" | `-XX:StartFlightRecording=disk=true,maxage=1h,maxsize=500m` |
