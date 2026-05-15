# Day 5 — 힙·스레드 덤프 분석

## 한 줄 요약

힙덤프(`jmap`)는 "메모리에 뭐가 사는가", 스레드덤프(`jstack`)는 "지금 누가 뭘 하고 있는가". 운영 사고의 80%는 이 둘로 진단된다.

## 학습 목표

- [ ] `jmap`, `jstack`, `jcmd`, `jstat`, `jfr`을 자유롭게 쓴다
- [ ] 힙덤프(.hprof)를 MAT/JVisualVM/Mission Control로 분석한다
- [ ] OQL(Object Query Language)로 덤프를 쿼리한다
- [ ] 스레드덤프에서 데드락·BLOCKED·WAITING 패턴을 안다
- [ ] async-profiler로 CPU·alloc·lock flame graph를 만든다
- [ ] JFR(Java Flight Recorder)로 운영급 프로파일링한다

---

## JDK 표준 진단 도구

| 도구 | 용도 |
|---|---|
| `jps` | JVM 프로세스 목록 (Java용 `ps`) |
| `jstat` | GC·class load·compiler 통계 (실시간) |
| `jmap` | 힙덤프, 힙 통계 |
| `jstack` | 스레드덤프 |
| `jcmd` | 통합 명령 (가장 강력) |
| `jhsdb` | Hotspot Serviceability Agent (코어 덤프 분석) |
| `jconsole` | GUI 모니터링 |
| `jfr` (CLI) / Mission Control | Java Flight Recorder |

> **JDK 11+에서는 `jcmd`가 거의 모든 일을 한다**. 다른 도구는 잠깐 쓸 때 편리.

---

## jcmd — 만능 칼

```bash
# PID 확인
jcmd                                 # 모든 JVM 나열
# 12345 com.example.MyApp

# 사용 가능한 명령
jcmd 12345 help

# 자주 쓰는 명령들
jcmd 12345 VM.version                    # JVM 정보
jcmd 12345 VM.system_properties          # 시스템 프로퍼티
jcmd 12345 VM.flags                      # 적용된 옵션
jcmd 12345 VM.uptime
jcmd 12345 GC.heap_info                  # Heap 상태
jcmd 12345 GC.heap_dump /tmp/heap.hprof  # 힙덤프
jcmd 12345 Thread.print                  # 스레드덤프
jcmd 12345 VM.native_memory summary      # NMT (사전 -XX:NativeMemoryTracking=summary 필요)
jcmd 12345 GC.run                        # System.gc()
jcmd 12345 JFR.start duration=60s filename=app.jfr  # JFR 녹화
```

---

## 힙덤프 — "메모리에 뭐가 사는가"

### 1) 만들기

```bash
# 방법 A — jcmd (권장)
jcmd 12345 GC.heap_dump /tmp/heap.hprof

# 방법 B — jmap
jmap -dump:live,format=b,file=heap.hprof 12345
#       └─live: 살아있는 객체만 (먼저 GC 돌고 덤프)

# 방법 C — OOM 시 자동 (운영 필수)
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/heapdumps/

# 덤프 크기 = Heap 크기와 거의 같음 (4G Heap → 4G .hprof)
# 디스크 공간 확보 필요
```

### 2) 분석 도구

| 도구 | 특징 |
|---|---|
| **Eclipse MAT (Memory Analyzer Tool)** | 표준. Leak Suspects 자동 |
| **VisualVM** | 가볍지만 큰 덤프(4G+)는 버거움 |
| **JDK Mission Control** | 작은 덤프엔 OK |
| **YourKit / JProfiler** | 상용. 매우 강력 |

### 3) MAT 사용법

```bash
# 다운로드: https://eclipse.dev/mat/
MemoryAnalyzer.exe heap.hprof
# 또는 IntelliJ Profiler에서 hprof 열기
```

MAT 메뉴:
- **Leak Suspects Report** — 자동 분석. 가장 먼저 봐야 함
- **Histogram** — 클래스별 인스턴스 수·총 크기
- **Dominator Tree** — "이 객체가 죽으면 풀려나는 메모리"
- **GC Roots** — 왜 이 객체가 살아있는지 경로 추적

### 4) OQL — 덤프에 SQL 던지기

```sql
-- 100KB 이상 byte[]
SELECT * FROM byte[] s WHERE s.@length > 102400

-- 특정 클래스의 인스턴스 수
SELECT count(*) FROM com.example.Order

-- 사용자별 세션 개수
SELECT u.userName, count(s) FROM com.example.Session s
WHERE s.expired = false
GROUP BY u.userName

-- 큰 HashMap
SELECT * FROM java.util.HashMap m WHERE m.size > 10000
```

### 5) 흔한 누수 패턴

| 패턴 | 의심 |
|---|---|
| `byte[]` 톱 — 큰 게 많음 | HTTP 응답 버퍼 누수 |
| `char[]` / `String` 톱 | String 캐싱 누수 |
| `HashMap$Node` 톱 + Map.size 큼 | static Map 누수 |
| `Thread` 인스턴스 누적 | ThreadPool 만 만들고 안 닫음 |
| ClassLoader가 여러 개 | 재배포 누수 (web container) |

---

## 스레드덤프 — "지금 누가 뭘 하나"

### 1) 만들기

```bash
# 방법 A — jcmd
jcmd 12345 Thread.print > thread.txt

# 방법 B — jstack
jstack 12345 > thread.txt
jstack -l 12345 > thread.txt        # -l: 추가 lock 정보

# 방법 C — 시그널 (Linux)
kill -3 12345                        # stdout으로
# (운영 콘솔 로그에 출력됨)

# 운영 진단 — 5초 간격 5회
for i in 1..5; do jstack 12345 > thread-$i.txt; sleep 5; done
```

### 2) 한 스레드 한 줄

```
"http-nio-8080-exec-1" #34 daemon prio=5 os_prio=0 cpu=15.32ms elapsed=1234.56s tid=0x... nid=0x1f3a runnable [0x00007f...]
   java.lang.Thread.State: RUNNABLE
       at java.net.SocketInputStream.socketRead0(java.base@21/Native Method)
       at java.net.SocketInputStream.read(java.base@21/SocketInputStream.java:...)
       at org.apache.coyote.http11.Http11InputBuffer.fill(...)
       ...
       at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(...)
       at java.lang.Thread.run(java.base@21/Thread.java:...)
```

핵심 정보:
- 이름 (`http-nio-8080-exec-1`) — 어떤 풀의 스레드?
- 상태 (`RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`)
- 스택 — 지금 어디서 멈춰있는가
- `nid` — OS 스레드 ID (Linux `ps -L`과 매칭)

### 3) Thread 상태

| 상태 | 의미 | 의심 |
|---|---|---|
| **NEW** | 시작 전 | 보통 무시 |
| **RUNNABLE** | 실행 중 또는 OS 대기 (I/O 포함) | CPU 폭주 시 누가 많은지 |
| **BLOCKED** | synchronized 잠금 대기 | **데드락·잠금 경합 의심** |
| **WAITING** | `Object.wait()`, `LockSupport.park()` | 정상 (idle pool) 많음 |
| **TIMED_WAITING** | `sleep`, `wait(ms)`, `await(ms)` | 정상 |

### 4) 데드락 탐지

`jstack`은 자동으로 데드락을 표시:

```
Found one Java-level deadlock:
=============================
"Thread-A":
  waiting to lock monitor 0x000... (object 0x..., a java.lang.Object),
  which is held by "Thread-B"
"Thread-B":
  waiting to lock monitor 0x000... (object 0x..., a java.lang.Object),
  which is held by "Thread-A"
```

### 5) 흔한 운영 패턴

#### A. DB 풀 고갈

```
"http-nio-8080-exec-1" ... TIMED_WAITING
   at java.lang.Thread.sleep(...)
   at com.zaxxer.hikari.pool.HikariPool.getConnection(...)
   ...
```

수십 개 같은 스택 → **HikariCP 풀에서 connection 대기** → DB 측 문제.

#### B. 외부 API 느림

```
"http-nio-8080-exec-3" ... RUNNABLE
   at java.net.SocketInputStream.socketRead0(...Native Method)
   at ...HttpURLConnection.getInputStream(...)
```

`socketRead0` 많음 → **외부 호출 응답 안 옴** → timeout 설정 점검.

#### C. GC 폭주 (CPU 100%)

```
"GC Thread#0" ... RUNNABLE
"GC Thread#1" ... RUNNABLE
"GC Thread#7" ... RUNNABLE
```

→ Heap·GC 로그 확인.

---

## CPU 폭주 진단 — 어느 스레드가 CPU를 먹나

### Linux

```bash
# 1. CPU 톱 스레드 찾기
top -H -p 12345
# 또는 ps -T -p 12345 -o pid,tid,%cpu,comm | sort -k3 -nr

#   PID   TID  %CPU COMMAND
# 12345 12399  98.5 java          ← 이 nid가 의심
# 12345 12400  85.0 java

# 2. nid를 16진수로
printf '%x\n' 12399                  # → 0x306f

# 3. 스레드덤프에서 nid=0x306f 찾기
jstack 12345 | grep -A 30 'nid=0x306f'
# → 어느 메서드가 도는지 보임
```

### Windows

```powershell
# 스레드 CPU 확인
Get-Process -Id 12345 | Select-Object -ExpandProperty Threads | Sort-Object TotalProcessorTime -Descending | Select-Object -First 5
# Id가 OS 스레드 ID (10진수). 16진수 변환:
'{0:x}' -f 12399                     # → 306f

jstack.exe 12345 | findstr nid=0x306f
```

---

## async-profiler — Flame Graph

JDK 표준은 아니지만 **사실상의 표준**. CPU·alloc·lock 프로파일 모두 가능.

### 1) 다운로드 & 실행

```bash
# https://github.com/async-profiler/async-profiler
./profiler.sh -d 30 -f cpu.html 12345         # 30초 CPU 프로파일

# 결과: cpu.html에 flame graph (브라우저로 열기)
```

### 2) 모드

```bash
./profiler.sh -e cpu -d 30 -f cpu.html PID    # CPU
./profiler.sh -e alloc -d 30 -f alloc.html PID # 할당 (메모리 누수 추적)
./profiler.sh -e lock -d 30 -f lock.html PID  # Lock contention
./profiler.sh -e wall -d 30 -f wall.html PID  # Wall-clock (대기 포함)
```

### 3) 읽는 법

```
   ┌────────────────────────────────────┐  ← 가장 위: 실제 실행 중이던 메서드
   │   System.arraycopy()                │
   ├─────────────────┬──────────────────┤
   │  String.split   │  ArrayList.grow  │  ← 호출자
   ├─────────────────┴──────────────────┤
   │           parseRequest()            │
   ├────────────────────────────────────┤
   │           handleHttp()              │
   └────────────────────────────────────┘  ← 가장 아래: 진입점

   가로 너비 = CPU 시간 비율
   넓은 봉우리 = hot spot
```

---

## JFR (Java Flight Recorder) — 운영 표준

운영서에서 **항상 켜둘 수 있는** 저오버헤드(<1%) 프로파일러. JDK 11+ 무료.

### 1) 시작·정지·덤프

```bash
# 즉시 시작
jcmd 12345 JFR.start name=myrec duration=120s filename=/tmp/profile.jfr

# 현재 녹화 확인
jcmd 12345 JFR.check

# 멈춤
jcmd 12345 JFR.stop name=myrec

# 또는 시작 시점부터 켜기
java -XX:StartFlightRecording=duration=60s,filename=app.jfr MyApp

# 항상 켜고 마지막 N분만 보관 (긴급용)
java -XX:StartFlightRecording=disk=true,maxage=1h,maxsize=500m MyApp
```

### 2) 분석 — JDK Mission Control

```bash
jmc                                  # GUI 실행
# File → Open → app.jfr
```

JMC가 자동으로 분석:
- **Memory** — 할당 핫스폿, GC 시간
- **CPU** — 메서드별 CPU 사용
- **I/O** — 파일·소켓 작업
- **Lock contention** — 잠금 경합

### 3) 운영 활용

JDK 21에선 `-XX:StartFlightRecording=disk=true,maxage=1h,maxsize=500m`로 **링버퍼**처럼 항상 녹화. 사고나면 `jcmd PID JFR.dump filename=incident.jfr`로 즉시 캡처.

---

## ❌ 진단 안티패턴 vs ✅ 패턴

### 1) 사고 발생 → 재시작 먼저

```bash
# ❌ 일단 재시작 (증거 사라짐)
systemctl restart myapp

# ✅ 증거 확보 후 재시작
jcmd <pid> Thread.print > thread-incident.txt
jcmd <pid> GC.heap_dump /tmp/incident-heap.hprof
jcmd <pid> JFR.dump filename=/tmp/incident.jfr
# 그 후 재시작
```

### 2) 한 번 떠본 스레드덤프로 결론

```bash
# ❌ 한 번 떠봄
jstack <pid>
# 보고: "BLOCKED 스레드 많음"

# ✅ 여러 번 떠보고 변화 관찰
for i in 1..5; do
    jstack <pid> > thread-$i.txt
    sleep 5
done
diff thread-1.txt thread-5.txt
# → 같은 스택이 5번 다 나오면 진짜 멈춰있음
# → 매번 바뀌면 정상 (단순 스냅샷)
```

### 3) Heap 덤프 → 다 본 척

```
# ❌ MAT의 Leak Suspects만 보고 결론
# Leak Suspects는 가이드일 뿐

# ✅ Histogram + Dominator Tree로 교차 검증
# OQL로 직접 쿼리
# 시간 차이 둔 두 개 덤프 비교 (`diff` 메뉴)
```

---

## 운영 사고 사례

### 사례 1 — N+1 DB 폭주 (서비스 멈춤)

**증상**: 응답 timeout. CPU 30%만 씀. DB 측은 정상.

**진단**:
```bash
jstack <pid> | grep -B 1 "TIMED_WAITING\|BLOCKED" | head -50
# → 100개 스레드가 hikari.pool.getConnection에 대기
```

**원인**: JPA N+1 폭주 → DB 커넥션 점유 → 풀 고갈.

**조치**: `@EntityGraph` 또는 fetch join 적용.

### 사례 2 — Tomcat 워커 모두 BLOCKED

**증상**: 새 요청 안 받음.

**진단**:
```
"http-nio-8080-exec-200" BLOCKED
  waiting to lock <0x...> (a com.example.Cache)
  ...
"http-nio-8080-exec-1" RUNNABLE
  - locked <0x...> (a com.example.Cache)
  at SlowLoader.load(...)
```

**원인**: 캐시 동기화에 외부 API 호출이 들어있음. 한 스레드가 잡고 외부 응답 기다리는 사이 나머지 199개가 대기.

**조치**: 캐시 동기화 범위에서 외부 호출 제거. 또는 lock-free 캐시(Caffeine 등).

---

## 실습 (Hands-on)

### 1단계 — 자기 JVM 진단해보기

```bash
# 아무 Spring Boot 앱 실행
# 1. PID 찾기
jps

# 2. 정보 확인
jcmd <pid> VM.system_properties
jcmd <pid> VM.flags
jcmd <pid> GC.heap_info

# 3. 스레드덤프
jcmd <pid> Thread.print
```

### 2단계 — 일부러 OOM 만들고 덤프 분석

```bash
java -Xmx128m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=. HeapOOM
# heap-dump.hprof 생성

# Mission Control 또는 MAT로 열기
```

### 3단계 — 데드락 재현

```java
// Deadlock.java
public class Deadlock {
    static final Object A = new Object();
    static final Object B = new Object();

    public static void main(String[] args) {
        new Thread(() -> {
            synchronized (A) {
                sleep(100);
                synchronized (B) { System.out.println("never"); }
            }
        }, "Thread-1").start();
        new Thread(() -> {
            synchronized (B) {
                sleep(100);
                synchronized (A) { System.out.println("never"); }
            }
        }, "Thread-2").start();
    }
    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (Exception e) {}
    }
}
```

```bash
javac Deadlock.java
java Deadlock &
jstack $! | grep -A 20 "deadlock"
```

### 4단계 — JFR 녹화·분석

```bash
java -XX:StartFlightRecording=duration=30s,filename=app.jfr AllocationLoad

# 30초 후 app.jfr 생성. Mission Control로 열기
jmc app.jfr
```

---

## 더 읽어볼 자료

- 📘 『Java Performance: The Definitive Guide』 — 3장 진단 도구
- 📘 『Optimizing Java』 — 5장 (jstack/jmap 활용)
- 🔗 [async-profiler](https://github.com/async-profiler/async-profiler)
- 🔗 [Eclipse MAT 매뉴얼](https://help.eclipse.org/latest/topic/org.eclipse.mat.ui.help/welcome.html)
- 🔗 [JFR Event Streaming](https://openjdk.org/jeps/349)
- 🎓 Marcus Hirt — "Get Started with Mission Control"
- 🔗 Brendan Gregg — [Flame Graphs](https://www.brendangregg.com/flamegraphs.html)
