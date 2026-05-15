# Lab 1 — JDK 21 설치 + 도구 검증 + 첫 GC 로그

## 목표

- JDK 21 LTS 설치 (Temurin 또는 Oracle)
- JDK 진단 도구 동작 확인 (jps, jstack, jmap, jcmd, jstat)
- Mission Control 설치
- 첫 GC 로그 만들어보기

---

## 1단계 — JDK 21 설치

### Windows (Chocolatey)

```powershell
choco install temurin21 -y

# 또는 직접 다운로드
# https://adoptium.net/temurin/releases/?version=21
```

### 환경변수 확인

```powershell
java -version
# openjdk version "21.0.x" 2024-...

# JAVA_HOME 설정 (PowerShell)
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot", "User")
```

### Linux/WSL (apt)

```bash
sudo apt update
sudo apt install -y temurin-21-jdk

# 또는 SDKMAN
curl -s "https://get.sdkman.io" | bash
sdk install java 21.0.x-tem
```

---

## 2단계 — JDK 도구 검증

```powershell
# 모두 동작해야 함
jps -version
jstack -h
jmap -help
jcmd -help
jstat -options
jfr help
```

PowerShell에서 안 뜨면 `JAVA_HOME\bin`이 PATH에 있는지 확인.

---

## 3단계 — Mission Control 설치

```powershell
# Oracle: https://www.oracle.com/java/technologies/jdk-mission-control.html
# 또는 Adoptium JMC: https://adoptium.net/jmc/

# 압축 풀고 jmc.exe 실행 확인
```

---

## 4단계 — 첫 Java 앱 + GC 로그

### 작업 디렉토리

```powershell
mkdir C:\jvm-lab
cd C:\jvm-lab
```

### Allocation.java

```java
import java.util.*;

public class Allocation {
    public static void main(String[] args) throws Exception {
        List<byte[]> alive = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (int i = 0; i < 200_000; i++) {
            byte[] data = new byte[100 * 1024];   // 100KB
            // 10%만 살림
            if (i % 10 == 0) alive.add(data);
            // 1000개 넘으면 옛 것 버림 (Old로 승격 유도)
            if (alive.size() > 1000) alive.remove(0);

            if (i % 10_000 == 0) {
                long elapsed = System.currentTimeMillis() - start;
                System.out.printf("[%6dms] iter=%d alive=%d%n", elapsed, i, alive.size());
            }
        }
        System.out.println("done");
    }
}
```

### 컴파일·실행

```powershell
javac Allocation.java

# GC 로그 + Heap 작게
java -Xms128m -Xmx128m `
     -Xlog:gc*:file=gc.log:time,uptime,level,tags `
     Allocation
```

### 결과 확인

```powershell
# gc.log 생성됐는지
ls gc.log

# 첫 20줄 보기
Get-Content gc.log | Select-Object -First 20
```

---

## 5단계 — GC 로그 읽기

`gc.log`에서 다음을 찾아보세요:

### A. 처음 로그

```
[0.012s][info][gc,init] CardTable entry size: 512
[0.012s][info][gc] Using G1
[0.020s][info][gc,heap,coops] Heap address: ...
```

→ "Using G1" 확인 (JDK 21 기본).

### B. Young GC

```
[0.234s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 50M->8M(128M) 12.345ms
```

→ STW 시간 확인.

### C. Concurrent Mark (있을 수 있음)

```
[1.234s][info][gc] GC(5) Concurrent Mark Cycle
[1.456s][info][gc] GC(5) Pause Remark 100M->90M(128M) 5.678ms
[1.500s][info][gc] GC(5) Concurrent Mark 200.0ms
```

### D. Pause Time 통계

마지막 GC 라인 보기 — 평균 Pause 시간 추정.

---

## 6단계 — gceasy.io에 업로드

1. https://gceasy.io 에 접속
2. `gc.log` 업로드
3. 결과 확인:
   - **Throughput** (% — GC가 아닌 시간 비율)
   - **Pause Time Average / Max**
   - **Heap Utilization After GC**

### 정상 기준

| 지표 | 좋음 | 주의 | 위험 |
|---|---|---|---|
| Throughput | > 99% | 95-99% | < 95% |
| Pause Avg | < 50ms | 50-200ms | > 200ms |
| Pause Max | < 200ms | 200-1000ms | > 1s |
| Full GC | 0 | 가끔 | 자주 |

이 lab의 작은 Heap에서는 Throughput가 낮을 수 있음 (Heap 키우면 개선).

---

## 7단계 — 옵션 비교 실험

### A. Parallel GC

```powershell
java -Xms128m -Xmx128m -XX:+UseParallelGC `
     -Xlog:gc*:file=gc_parallel.log Allocation
```

### B. ZGC

```powershell
java -Xms128m -Xmx128m -XX:+UseZGC `
     -Xlog:gc*:file=gc_zgc.log Allocation
```

### C. G1 with Pause Goal

```powershell
java -Xms128m -Xmx128m -XX:+UseG1GC -XX:MaxGCPauseMillis=20 `
     -Xlog:gc*:file=gc_g1_low.log Allocation
```

### 비교 — 어느 것이 가장 빠르게 끝났나? STW 평균은?

gceasy.io에 각각 업로드해서 비교표 작성.

---

## 8단계 — 동작 중인 JVM 진단해보기

```powershell
# Allocation을 백그라운드로 더 오래 돌리기
java -Xms128m -Xmx128m -Xlog:gc*:file=gc.log Allocation &

# PID 확인
jps
# 12345 Allocation

# Heap 상태
jcmd 12345 GC.heap_info

# 스레드덤프
jcmd 12345 Thread.print | Select-String -Pattern "RUNNABLE|main"

# 옵션 확인
jcmd 12345 VM.flags

# 강제 GC
jcmd 12345 GC.run
```

---

## 산출물

이 lab을 완료하면 다음을 갖고 있어야 합니다:

- [ ] JDK 21 + 도구 동작 확인
- [ ] Mission Control 설치
- [ ] `gc.log` 3종 (G1 기본 / Parallel / ZGC)
- [ ] gceasy.io 결과 3종 캡쳐 또는 메모
- [ ] `jcmd`로 동작 중 JVM 진단 1회

---

## 트러블슈팅

### "jps가 자기 자신만 보임"

WSL과 Windows JDK가 섞이거나, 다른 사용자 권한으로 띄운 경우. 같은 셸 / 같은 권한에서 띄워야 함.

### "Mission Control이 jcmd 못 찾음"

JMC 설정에서 `Window → Preferences → Java → Installed JREs`로 JDK 21 등록.

### "gc.log가 안 만들어짐"

상대 경로 문제. 절대 경로(`C:\jvm-lab\gc.log`) 또는 `:gc.log` 같은 형식 확인. 또는 `-Xlog` 문법 오타.

---

## 다음 단계

[Lab 2 — GC 폭주 재현·튜닝](lab2_gc_logs.md)
