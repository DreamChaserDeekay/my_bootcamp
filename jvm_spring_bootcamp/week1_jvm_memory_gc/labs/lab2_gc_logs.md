# Lab 2 — GC 폭주 재현 → 로그 해석 → 옵션 튜닝

## 목표

- 메모리 누수로 Full GC 폭주를 인위적으로 만든다
- GC 로그·heap dump·thread dump를 떠서 진단한다
- 옵션을 한 번에 하나씩 바꿔 효과를 측정한다
- "측정 → 가설 → 한 변경 → 재측정"의 튜닝 사이클을 익힌다

---

## 1단계 — 누수 코드 작성

### LeakySimulator.java

```java
import java.util.*;
import java.util.concurrent.*;

public class LeakySimulator {
    // ❌ 의도된 누수: static Map이 계속 자람
    static final Map<Long, byte[]> LEAK = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        long id = 0;
        Random r = new Random();
        while (true) {
            // 작은 요청 시뮬레이션
            byte[] body = new byte[r.nextInt(10 * 1024) + 1024]; // 1~10KB
            LEAK.put(id++, body);

            // CPU 작업
            for (int i = 0; i < 1000; i++) {
                Math.sqrt(i);
            }

            if (id % 1000 == 0) {
                System.out.printf("[%s] id=%d, leak.size=%d%n",
                    new Date(), id, LEAK.size());
            }

            // 1ms 쉼 — TPS 비슷하게
            if (id % 100 == 0) Thread.sleep(1);
        }
    }
}
```

```powershell
javac LeakySimulator.java
```

---

## 2단계 — Baseline 측정

```powershell
java -Xms256m -Xmx256m `
     -Xlog:gc*:file=baseline.log:time,uptime,level,tags `
     -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=. `
     LeakySimulator
```

진행 중 다른 터미널에서:

```powershell
jps                                  # PID 확인
jstat -gcutil <PID> 1000 30          # 1초 간격 30회
# 출력:
#  S0  S1   E    O    M   CCS   YGC  YGCT  FGC  FGCT  GCT
#   0   0  45.2 87.3  95   90   23   0.45    2  4.5   4.95
#   0   0  60.1 91.7  95   90   24   0.46    3  6.8   7.26
#   ↑Old이 91% → 점점 차오름
#   ↑FGC가 증가 → Full GC가 자주 발생
```

수 분 안에 `OutOfMemoryError: Java heap space` 발생.

```
java.lang.OutOfMemoryError: Java heap space
Dumping heap to .\java_pid12345.hprof ...
Heap dump file created [256123456 bytes]
```

---

## 3단계 — GC 로그 해석

`baseline.log`를 [gceasy.io](https://gceasy.io)에 업로드.

확인:
- **Throughput** — 점점 떨어짐
- **Pause Time** — Full GC가 시작되면서 늘어남
- **Heap After GC** — **회수되는 양이 줄어듬** (누수의 명확한 증거)

### 누수 시그널 — Heap After GC가 단조증가

```
GC 후 Heap:
    GC #1: 40M → 30M  (free 10M)
    GC #2: 80M → 50M  (free 30M)
    GC #3: 100M → 80M (free 20M)
    GC #4: 150M → 130M (free 20M)
    ...
    GC #20: 250M → 245M (free 5M)   ← 회수가 거의 안 됨
```

이 패턴이 **메모리 누수의 결정적 증거**.

---

## 4단계 — 힙덤프 분석

### Eclipse MAT으로

1. MAT 실행 → 만든 `.hprof` 열기
2. **Leak Suspects Report** — 자동 분석
3. 결과 화면에서 "Problem Suspect 1" 클릭

예상 출력:
```
The thread "main" keeps local variables with total size 240,123,456 bytes
The thread is alive.
The class "LeakySimulator", loaded by "...", occupies 240,123,456 (95.30%) bytes.
The memory is accumulated in one instance of "java.util.concurrent.ConcurrentHashMap"
loaded by "<system class loader>".
```

→ **static `LEAK` Map이 240MB** 차지.

### OQL로 직접 쿼리

```sql
SELECT * FROM java.util.concurrent.ConcurrentHashMap m
WHERE m.size > 1000
```

### Histogram

가장 많은 메모리를 점유하는 클래스:
1. `byte[]` — 95% (LEAK의 값들)
2. `java.util.concurrent.ConcurrentHashMap$Node` — 4%
3. ...

---

## 5단계 — 스레드덤프

문제 상황(Full GC 폭주 중)에서:

```powershell
jstack <PID> > thread.txt
type thread.txt | findstr -A 5 "GC Thread"
```

```
"GC Thread#0" os_prio=0 ... runnable
"GC Thread#1" os_prio=0 ... runnable
...
```

→ **GC 스레드들이 RUNNABLE 상태로 CPU를 먹고 있음**. 앱 코드는 거의 멈춤.

---

## 6단계 — 가설 검증 — 그냥 Heap만 늘리면?

```powershell
java -Xms512m -Xmx512m `
     -Xlog:gc*:file=larger.log:time,uptime,level,tags `
     LeakySimulator
```

결과: **OOM이 좀 늦게 나지만 결국 또 터짐**. 근본 원인은 누수이지 Heap 부족이 아님.

→ **튜닝 안티패턴**의 살아있는 증거.

---

## 7단계 — 진짜 수정

`LeakySimulator`의 누수를 고침:

```java
// Before:
static final Map<Long, byte[]> LEAK = new ConcurrentHashMap<>();

// After: TTL이나 size 제한 있는 캐시
static final Map<Long, byte[]> CACHE = Collections.synchronizedMap(
    new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, byte[]> e) {
            return size() > 1000;       // 1000개 넘으면 가장 오래된 것 제거
        }
    }
);
```

(또는 Caffeine 같은 라이브러리 사용)

```powershell
java -Xms256m -Xmx256m -Xlog:gc*:file=fixed.log LeakySimulator2
```

지금은 Old가 안정화됨 — Heap이 더 이상 자라지 않음.

---

## 8단계 — JFR로 운영 환경처럼

운영 환경 시뮬레이션:

```powershell
java -Xms256m -Xmx256m `
     -XX:StartFlightRecording=duration=120s,filename=app.jfr `
     LeakySimulator
```

2분 후 `app.jfr` 생성.

```powershell
jmc                                  # Mission Control 실행
# File → Open → app.jfr
```

JMC에서:
- **Memory** 탭 — 할당 hotspot 확인
- **Garbage Collections** 탭 — GC 시간 그래프
- **Allocations** — 어느 메서드가 할당을 많이?

---

## 9단계 — 정리·체크리스트

이 lab으로 다음을 경험:

- [ ] `-Xlog:gc*` 옵션과 로그 위치
- [ ] `-XX:+HeapDumpOnOutOfMemoryError`로 자동 덤프
- [ ] `jstat -gcutil`로 실시간 GC 모니터링
- [ ] Heap After GC가 단조증가 = 누수
- [ ] MAT의 Leak Suspects Report 사용
- [ ] Heap만 늘려도 누수는 해결 안 됨
- [ ] JFR + Mission Control로 운영급 분석

---

## 보너스 — Old Generation 강제 채우기

학습 목적으로 Old만 빠르게 채우기:

```java
// OldGenFiller.java
import java.util.*;

public class OldGenFiller {
    public static void main(String[] args) throws Exception {
        List<byte[]> survivors = new ArrayList<>();
        for (int i = 0; ; i++) {
            // 크고 오래 사는 객체 → 바로 Old로 승격되는 경향
            survivors.add(new byte[1024 * 1024]);   // 1MB
            if (i % 10 == 0) {
                System.out.printf("alive=%d MB%n", survivors.size());
                Thread.sleep(100);
            }
        }
    }
}
```

```powershell
java -Xms128m -Xmx128m -Xlog:gc* OldGenFiller
# → 빠르게 Full GC, 그다음 OOM
```

---

## 다음 단계

[Week 1 Checklist](../checklist.md)
