# Day 5 — 캡스톤: 부하 시나리오 · 진단 · 보고서

## 한 줄 요약

4주간 익힌 모든 것을 **하나의 시나리오**에 적용한다. Spring Boot 앱을 띄우고, 부하를 가해 한계에 부딪힌 다음, **계층별로 진단하고 보고서를 쓴다**. 진단의 깊이가 곧 운영자의 가치다.

---

## 캡스톤 개요

### 목표

다음의 종합 시나리오를 수행하고 보고서 작성:

1. Spring Boot 에코 + DB 호출 미니 앱을 띄운다
2. 인위적 부하를 가한다 (wrk 또는 k6)
3. 한계에 도달한 지점을 찾는다 (CPU? 메모리? 풀 고갈? backlog?)
4. **5계층(앱·JVM·소켓·TCP·OS) 각각에서 진단**한다
5. 적어도 2가지 튜닝을 적용해 처리량을 개선한다
6. 보고서: 가설 → 측정 → 분석 → 조치 → 재측정 형식

### 산출물

```
capstone/
├── REPORT.md                       ← 메인 보고서
├── app/                            ← 테스트 대상 (practice_app 또는 본인 작성)
├── load-test/
│   ├── scenario.lua / scenario.js
│   └── run.sh
├── captures/                       ← tcpdump pcap (선택)
├── profiles/                       ← perf, async-profiler 출력
├── screenshots/                    ← htop, Wireshark 캡처
└── data/                           ← 측정 csv
```

---

## 단계 1: 테스트 앱 준비

`practice_app`을 사용하거나, 직접 간단히:

```java
// src/main/java/com/example/cap/CapApp.java
@SpringBootApplication
@RestController
public class CapApp {
    public static void main(String[] args) { SpringApplication.run(CapApp.class, args); }

    @GetMapping("/echo")
    public String echo(@RequestParam(defaultValue="hi") String s) { return "ECHO:" + s; }

    @GetMapping("/work")
    public String work(@RequestParam(defaultValue="50") int ms) throws InterruptedException {
        Thread.sleep(ms);    // 인위 지연 (DB 호출 시뮬레이션)
        return "OK";
    }

    @GetMapping("/cpu")
    public long cpu(@RequestParam(defaultValue="100000") int n) {
        long sum = 0;
        for (long i = 0; i < n; i++) sum += i * i;
        return sum;
    }
}
```

```yaml
# application.yml
server:
  port: 8080
  tomcat:
    threads:
      max: 200
    accept-count: 100
    max-connections: 8192
```

---

## 단계 2: 부하 시나리오

```bash
# wrk
sudo apt install -y wrk

# 시나리오 A: 인메모리 echo (CPU 한계 측정)
wrk -t 10 -c 100 -d 60s http://localhost:8080/echo

# 시나리오 B: 50ms 지연 work (스레드 풀 한계 측정)
wrk -t 10 -c 500 -d 60s 'http://localhost:8080/work?ms=50'

# 시나리오 C: CPU 부하 (CPU 한계)
wrk -t 10 -c 100 -d 60s 'http://localhost:8080/cpu?n=10000000'
```

### 측정해야 할 메트릭

| 메트릭 | 어디서 |
|---|---|
| RPS, p50/p95/p99 | wrk 출력 |
| 에러율 | wrk 출력 (Socket errors, Non-2xx) |
| CPU 사용률 | `top`/`mpstat` (자기 + 다른 터미널) |
| 메모리 | `free`, `jcmd ... GC.heap_info` |
| 스레드 수 | `jstack`, `ps -L` |
| 소켓 상태 | `ss -tan` (ESTAB, TIME_WAIT, LISTEN의 Recv-Q) |
| TCP retransmits | `nstat` |
| GC | `jstat -gcutil` |

### 자동화 스크립트

```bash
#!/usr/bin/env bash
# load-test/run.sh
set -euo pipefail

SCENARIO=${1:-A}
DURATION=${2:-60s}
CONNS=${3:-100}
THREADS=${4:-10}

LOG_DIR="data/$(date +%Y%m%d_%H%M%S)_$SCENARIO"
mkdir -p "$LOG_DIR"

case "$SCENARIO" in
    A) URL='http://localhost:8080/echo' ;;
    B) URL='http://localhost:8080/work?ms=50' ;;
    C) URL='http://localhost:8080/cpu?n=10000000' ;;
esac

echo "Scenario $SCENARIO: $URL"

# 백그라운드 측정
mpstat -P ALL 1 > "$LOG_DIR/mpstat.log" &
MP=$!
vmstat 1 > "$LOG_DIR/vmstat.log" &
VM=$!
(while true; do ss -tan | awk 'NR>1 {print $1}' | sort | uniq -c | xargs -I{} echo "$(date +%s) {}"; sleep 1; done) > "$LOG_DIR/sockets.log" &
SK=$!
(while true; do jstat -gcutil $(jps | grep CapApp | awk '{print $1}') 1s 1; sleep 1; done) > "$LOG_DIR/gc.log" &
JS=$!

# 부하
wrk -t "$THREADS" -c "$CONNS" -d "$DURATION" "$URL" | tee "$LOG_DIR/wrk.log"

# 측정 종료
kill $MP $VM $SK $JS 2>/dev/null || true

echo "Logs: $LOG_DIR"
```

---

## 단계 3: 계층별 진단 템플릿

### Layer 1: 앱 (Spring/Tomcat/Netty)

- HTTP 액세스 로그: 어느 엔드포인트가 느린가?
- Actuator `/actuator/metrics`: jvm.threads.live, tomcat.threads.busy, http.server.requests
- 액티브 스레드 수가 max에 닿았나?

### Layer 2: JVM

- GC 빈도, GC 시간 (`jstat -gcutil`)
- 힙 사용량 (`GC.heap_info`)
- 스레드 덤프에서 BLOCKED·WAITING 스레드 비율

### Layer 3: 소켓 / Java NIO

- `ss -tan` 상태 분포
- TIME_WAIT 누적?
- CLOSE_WAIT 누적? (앱 leak)
- accept queue overflows (`nstat TcpExtListenOverflows`)

### Layer 4: TCP

- 재전송 (`nstat TcpExtTCPLossProbes`)
- RTT 변동 (Wireshark)
- 윈도우 크기

### Layer 5: OS

- CPU 분포 (mpstat 각 코어)
- IO wait (vmstat wa)
- 인터럽트, 컨텍스트 스위치 (vmstat in/cs)
- 파일 디스크립터 사용량

---

## 단계 4: 보고서 템플릿

`capstone/REPORT.md` 예시 골격:

```markdown
# OS·네트워크 부트캠프 캡스톤 보고서

## 1. 시나리오 개요

- 대상 앱: Spring Boot 3.x, JDK 17, Tomcat 기본
- 환경: WSL2 Ubuntu 22.04, 4 vCPU, 8GB RAM
- 부하: wrk 10 threads × 500 connections × 60s
- 시나리오: `GET /work?ms=50` (50ms 지연 응답)

## 2. 기준선 (Baseline)

| 메트릭 | 값 |
|---|---|
| RPS | 950 |
| p50 latency | 52ms |
| p95 latency | 350ms |
| p99 latency | 1800ms |
| 에러율 | 0% |
| CPU | 65% |
| 메모리 | 1.2GB / 8GB |
| Tomcat busy threads | 200/200 ← 한계 도달! |
| Socket TIME_WAIT | 23,500 |
| TCP retransmits/s | 0 |

[wrk 출력 스크린샷]
[htop 스크린샷]
[ss -tan 분포 그래프]

## 3. 가설

스레드 풀이 200으로 제한되어 있고 평균 응답이 50ms이므로,
Little's Law에 의해 최대 RPS ≈ 200/0.05 = 4000. 그러나 실측은 950.

→ 가설 1: 스레드 컨텍스트 스위치 비용
→ 가설 2: TCP TIME_WAIT 누적으로 인한 포트 고갈
→ 가설 3: GC pause로 인한 잠금

## 4. 진단

### 4.1 GC 분석

`jstat -gcutil` 결과:
- YGC: 매 3초
- FGC: 거의 없음
- OU (Old Used): 안정적 30~40%

→ GC는 문제 아님

### 4.2 스레드 분석

`jstack`에서 200개 worker 모두 `Thread.sleep`에 머묾.
→ 스레드 풀 한계가 직접적 병목

### 4.3 OS 레벨

`mpstat -P ALL`에서 4코어 모두 ~70% — 여유 있음
`vmstat`에서 cs (context switch) 매우 높음 (50000/s)
→ 200 스레드 + 컨텍스트 스위치 비용

### 4.4 소켓

`ss -tan`에서 TIME_WAIT 23k. 운영서버 LB 뒤에 있으면 큰 영향.

## 5. 조치 1: 스레드 풀 200 → 500

`server.tomcat.threads.max: 500`

재측정:

| 메트릭 | Before | After |
|---|---|---|
| RPS | 950 | 2,800 |
| p95 | 350ms | 95ms |
| CPU | 65% | 88% |

스레드 풀 한계가 직접 원인이었음 확인.

## 6. 조치 2: 가상 스레드 (JDK 21)

JDK 21 + `spring.threads.virtual.enabled=true`

| 메트릭 | After | Virtual Threads |
|---|---|---|
| RPS | 2,800 | 4,200 |
| p95 | 95ms | 65ms |
| 워커 스레드 | 500 | 8 (캐리어) |
| 컨텍스트 스위치 | 50k/s | 12k/s |

가상 스레드가 동일 부하에서 OS 자원을 훨씬 적게 씀.

## 7. 결론

- 1차 병목: **스레드 풀 크기**
- 진단 도구 핵심: `jstack`, `wrk`, `mpstat`, `ss`
- 교훈: 블로킹 IO 앱에서 스레드 풀 크기 결정은 Little's Law + 실측
- 다음 단계: 가상 스레드 도입 검토, Reactive 전환 비용 분석

## 8. 부록

- [wrk 전체 출력](data/...)
- [GC 로그](data/...)
- [캡처 pcap](captures/...)
```

---

## 단계 5: 채점 가이드 (자가)

| 항목 | 점수 |
|---|---|
| **시나리오 명확** (목표·가설·메트릭 정의) | /10 |
| **기준선 측정 완성도** (위 표 + 스크린샷) | /15 |
| **계층별 진단 깊이** (앱/JVM/소켓/TCP/OS) | /25 |
| **튜닝의 근거와 재측정** | /20 |
| **보고서 가독성** (표·그래프·결론) | /15 |
| **새로운 발견 또는 인사이트** | /15 |
| **합계** | /100 |

70점 이상: 운영 가능. 85점 이상: 시니어 운영.

---

## 다음 단계 (졸업 후)

### 자격증 추천

- **LPIC-1, LPIC-2**: Linux 시스템 관리
- **RHCSA, RHCE**: Red Hat
- **CCNA**: 네트워크 기초
- **CKAD, CKA**: Kubernetes
- **AWS SysOps Administrator**

### 심화 학습 경로

| 영역 | 권장 학습 |
|---|---|
| 시스템 프로그래밍 | 『Linux Programming Interface』 정독, 작은 unix tool 직접 작성 |
| 네트워크 심화 | 『TCP/IP Illustrated Vol. 1』, Stanford CS144 |
| 성능 분석 | 『Systems Performance』 + Brendan Gregg 블로그 |
| 컨테이너·k8s | 『Kubernetes in Action』 |
| SRE | Google SRE Book (무료) |
| Java 고성능 | 『Java Performance: The Definitive Guide』 |

### 본인 환경에 적용

- 회사 서비스의 운영 메트릭 대시보드 만들기
- 한 운영 장애를 골라 위 보고서 형식으로 회고 작성
- 신규 시스템 도입 시 RFC(설계 문서)에 OS·네트워크 챕터 추가

---

## 마무리

4주, 20 Day, 약 60시간의 학습. 이 과정 후에는:

- 운영서버에서 SSH 끊김 없이 작업하는 법
- `tcpdump` 한 줄로 사건의 진실을 찾는 법
- "Connection refused"가 어느 계층의 문제인지 즉답하는 능력
- Bash·PowerShell로 반복작업을 자동화하는 습관
- 시스템이 느릴 때 추측 대신 측정하는 자세

… 이 모든 것이 단단해졌으리라 믿는다.

이 자료는 본인의 운영 노트로 계속 업데이트하길 권한다. 새로 부딪힌 문제, 새로 쓴 명령, 새로 알게 된 옵션을 `resources/troubleshooting_playbook.md`나 본인의 사내 위키에 더해가자.

수고했습니다.

이번 주 마무리: [`labs/lab7_chat_server.md`](labs/lab7_chat_server.md), [`checklist.md`](checklist.md)

← 메인으로: [`../README.md`](../README.md)
