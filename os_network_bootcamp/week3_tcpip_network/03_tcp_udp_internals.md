# Day 3 — TCP · UDP 내부 동작

## 한 줄 요약

TCP는 **신뢰성 있는 스트림** (재전송, 순서 보장, 흐름 제어), UDP는 **무신뢰 datagram** (빠르고 단순). 운영 디버깅에서는 **TCP의 상태머신**(특히 TIME_WAIT, CLOSE_WAIT)을 모르면 "포트가 안 풀려요" 같은 문제를 해결할 수 없다.

## 학습 목표

- [ ] TCP의 **3-way handshake**와 **4-way termination**을 그릴 수 있다
- [ ] TCP **상태머신**의 주요 상태(LISTEN, SYN_SENT, ESTABLISHED, FIN_WAIT, TIME_WAIT, CLOSE_WAIT)를 안다
- [ ] **TIME_WAIT가 왜 60초간 남는지** 설명한다
- [ ] **CLOSE_WAIT가 많으면** 무엇이 잘못된 건지 안다
- [ ] TCP의 **윈도우**·**MSS**·**혼잡제어** 기초를 안다
- [ ] UDP의 특성과 사용 사례를 안다
- [ ] `ss`, `netstat`, `Get-NetTCPConnection`으로 연결 상태를 조사한다

---

## 1. TCP vs UDP

| 특성 | TCP | UDP |
|---|---|---|
| 단위 | 스트림 (바이트) | 데이터그램 (메시지) |
| 신뢰성 | 보장 (재전송, 순서) | 안 함 |
| 연결 | 있음 (handshake) | 없음 |
| 흐름 제어 | 윈도우 | 없음 |
| 혼잡 제어 | 있음 | 없음 |
| 헤더 | 20+ 바이트 | 8 바이트 |
| 멀티캐스트 | 안 됨 | 됨 |
| 사용 | HTTP, SSH, DB, 메일 | DNS, NTP, VoIP, 게임, DHCP, QUIC* |

\* QUIC는 UDP 위에 신뢰성을 다시 구현 (HTTP/3)

---

## 2. TCP 3-way Handshake (연결)

```
Client                      Server
  │                            │
  │ ─── SYN (seq=x) ─────────► │
  │                            │
  │ ◄── SYN-ACK (seq=y, ack=x+1) │
  │                            │
  │ ─── ACK (ack=y+1) ───────► │
  │                            │
  │      ESTABLISHED           │
```

- **SYN**: 시퀀스 번호의 시작값(ISN)을 알림
- **SYN-ACK**: 서버도 자기 ISN을 알리고 클라이언트 SYN을 확인
- **ACK**: 클라이언트도 서버 SYN을 확인 → 연결 성립

> 면접 단골: "왜 3번이고 2번이면 안 되나?" → 양방향 시퀀스 번호 합의가 필요하기 때문. 한 번이라도 빠지면 한쪽이 다른 쪽 ISN을 확신할 수 없음.

### SYN 시 무엇이 정해지나

- ISN (Initial Sequence Number) — 보안상 무작위
- MSS (Maximum Segment Size) — 보통 1460 (Ethernet 기준)
- Window Scale, Timestamp, Selective ACK 옵션 협상

---

## 3. TCP 4-way Termination (종료)

```
Client                      Server
  │                            │
  │ ─── FIN ───────────────► │   "나 보낼 거 끝났어"
  │                          ACK│
  │ ◄────────────────────────  │
  │   FIN_WAIT_1 → FIN_WAIT_2  │   CLOSE_WAIT
  │                            │
  │   (서버는 아직 보낼 수 있음)  │
  │                            │
  │ ◄── FIN ────────────────── │   "나도 끝났어"
  │ ACK                        │
  │ ──────────────────────────►│
  │   TIME_WAIT (보통 60초)     │   LAST_ACK → CLOSED
  │                            │
  │   CLOSED                   │
```

### 4번 메시지가 필요한 이유

TCP는 **반이중(half-duplex) 종료**를 허용한다. 한 쪽이 끝나도 다른 쪽은 계속 보낼 수 있어, FIN/ACK가 양쪽 각각 필요 → 합해서 4개.

---

## 4. TCP 상태머신

```
                          ┌──── CLOSED ────┐
                          │                │
                       (active             (passive
                        open)               open: listen())
                          ↓                  ↓
                       SYN_SENT           LISTEN
                          │                  │
                       (SYN-ACK)          (SYN 수신)
                          ↓                  ↓
                       (ACK 전송)         SYN_RCVD
                          ↓                  ↓
                  ┌─→ ESTABLISHED ←────────┘
                  │       │
                  │    (close())
                  │       ↓
                  │   FIN_WAIT_1
                  │       │ (ACK 수신)
                  │       ↓
                  │   FIN_WAIT_2
                  │       │ (FIN 수신)
                  │       ↓
                  │   TIME_WAIT
                  │       │
                  │    (2MSL 대기)
                  │       ↓
                  └─── CLOSED
```

수동 close 측은:

```
ESTABLISHED → CLOSE_WAIT → LAST_ACK → CLOSED
```

### 주요 상태

| 상태 | 의미 | 운영 관점 |
|---|---|---|
| LISTEN | 서버가 연결 대기 중 | `ss -tln`에서 보임 |
| SYN_SENT | 클라이언트가 SYN 보내고 응답 대기 | 짧게 머무름 |
| SYN_RCVD | 서버가 SYN-ACK 보내고 ACK 대기 | SYN flood 공격이 이걸 채움 |
| ESTABLISHED | 양방향 데이터 교환 가능 | 정상 |
| FIN_WAIT_1 | close() 호출, FIN 보냄 | |
| FIN_WAIT_2 | FIN의 ACK 받음, 상대 FIN 대기 | 상대가 close 안 하면 오래 머묾 |
| **TIME_WAIT** | 마지막 ACK 보내고 2MSL 대기 | **흔한 운영 이슈** |
| **CLOSE_WAIT** | 상대가 FIN 보냈으나 내가 close 안 함 | **앱 버그 신호** |
| LAST_ACK | 자기 FIN의 ACK 대기 | |
| CLOSED | 종료 | 정상 |

---

## 5. TIME_WAIT — 가장 흔한 오해

### 왜 있는가

```
A         B
 ── ACK ──►   (마지막 ACK)
              CLOSED
 (이 ACK이 유실되면?)
              B는 다시 FIN 보낼 수 있음
 (그런데 A가 이미 CLOSED라면 RST 응답)

→ A가 일정 시간 TIME_WAIT으로 남아 ACK 재전송 보장
```

또한 **포트가 재사용될 때 이전 연결의 지연 패킷이 끼어드는 것을 막기 위해**서도 필요.

### 2MSL?

- MSL = Maximum Segment Lifetime, 보통 30초
- TIME_WAIT = 2 × MSL = 60초 (Linux 기본)
- 옛 Windows는 240초(!)였음 → 운영 사고 단골

### "운영서버에 TIME_WAIT가 수만 개"

부하 테스트나 짧은 keep-alive 없는 HTTP 클라이언트가 연결을 끝없이 만들면 **active close 측에 TIME_WAIT 누적**.

```bash
ss -ant | awk 'NR>1 {print $1}' | sort | uniq -c
#  10  ESTAB
# 156  TIME-WAIT     ← 많음
```

### 해결 방법 (위험도 순서)

```bash
# 가장 좋은 해결: 연결 재사용 (keep-alive, 커넥션 풀)
# Spring WebClient/RestTemplate에 ConnectionPool 설정

# 적당한 해결: TIME_WAIT 동안 같은 5-tuple 재사용 허용
sudo sysctl -w net.ipv4.tcp_tw_reuse=1     # 안전 (timestamp 기반)

# 나쁜 해결: tcp_tw_recycle (RFC 위반, NAT 환경에서 사고). Linux 4.12에서 제거됨
# 절대 ❌

# 응급조치: TIME_WAIT 시간 단축
sudo sysctl -w net.ipv4.tcp_fin_timeout=15

# 로컬 포트 범위 확대
sudo sysctl -w net.ipv4.ip_local_port_range="10000 65000"
```

> **운영 진실**: TIME_WAIT 자체는 정상. 1만 개 정도는 64GB RAM 서버에서 큰 부담 아님 (TCB 한 개당 수백 바이트). 진짜 문제는 **로컬 포트 고갈** (1 IP에서 ~28k 포트가 한계) → 그래서 클라이언트 측 connection pool이 답.

---

## 6. CLOSE_WAIT — 항상 앱 버그

```
상대(서버)가 close() → FIN 보냄
내(클라이언트)가 ACK는 자동으로 보냄 → CLOSE_WAIT
내가 close()를 호출해야 LAST_ACK → CLOSED로
... 그런데 안 함 ← 앱 버그!
```

### 흔한 원인

- Java에서 `try-with-resources` 안 쓰고 `InputStream.close()` 잊음
- `RestTemplate` 응답을 다 안 읽고 버림
- 예외 처리에서 connection 누수
- 풀이 leak detector 없이 무한히 만듬

### 진단

```bash
# 어느 프로세스가 CLOSE_WAIT 누적하나
ss -tap | grep CLOSE-WAIT
# 또는
ss -tanp 'state close-wait'

# Spring Boot 운영
jcmd <pid> Thread.print               # 스레드 덤프
jcmd <pid> VM.native_memory summary    # 네이티브 자원
```

### Java 권장 패턴

```java
// ❌ 누수 위험
HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
InputStream is = conn.getInputStream();
return readAll(is);
// 예외 시 close 안 됨

// ✅ try-with-resources
try (InputStream is = conn.getInputStream()) {
    return readAll(is);
}

// ✅ Spring RestTemplate은 응답 본문을 항상 소비
ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
// Spring이 내부에서 자동 close

// ✅ WebClient는 reactive이므로 subscribe까지 가야 close
webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
```

---

## 7. ss / netstat / PowerShell

### Linux ss (recommended) — netstat의 후계

```bash
# 모든 TCP 연결
ss -t -a

# 리스닝 (LISTEN)
ss -tln                                # TCP, listening, numeric (DNS 안 함)
ss -tln 'sport = :8080'                # 8080만

# 프로세스 포함
sudo ss -tlnp

# 통계
ss -s

# 상태별
ss -t state established
ss -t state time-wait | wc -l

# 특정 IP/포트
ss -tn 'dst 192.168.1.100'
ss -tn 'dport = :443'

# 더 자세히 (timer, send-q, recv-q)
ss -tan
```

### netstat (legacy, 여전히 흔히 보임)

```bash
netstat -tlnp
netstat -an
netstat -i                             # 인터페이스 통계
netstat -rn                            # 라우팅 (ip route로 대체)
```

### PowerShell

```powershell
Get-NetTCPConnection                                # 전체
Get-NetTCPConnection -State Listen                  # 리스닝
Get-NetTCPConnection -LocalPort 8080                # 특정 포트

# 프로세스 결합
Get-NetTCPConnection -State Listen |
    Select-Object LocalAddress, LocalPort, OwningProcess,
                  @{N='Name';E={(Get-Process -Id $_.OwningProcess).Name}}

# TIME_WAIT 수
(Get-NetTCPConnection -State TimeWait).Count

# UDP 리스닝
Get-NetUDPEndpoint
```

### Read/Write Queue (Send-Q, Recv-Q)

`ss -tan` 출력의 두 컬럼:

| | LISTEN 상태 | ESTABLISHED 상태 |
|---|---|---|
| Recv-Q | accept queue에 쌓인 연결 수 | 앱이 읽지 않은 바이트 |
| Send-Q | accept queue 최대치 (backlog) | 상대가 ACK 안 한 바이트 |

운영 진단:

- LISTEN의 `Recv-Q`가 backlog에 가까움 → 앱이 accept를 못 따라감 (또는 backlog가 너무 작음)
- ESTABLISHED의 `Recv-Q`가 큼 → 앱이 read 안 함 → 백프레셔 신호
- `Send-Q`가 큼 → 상대(또는 네트워크)가 느림

---

## 8. TCP 윈도우 · MSS · 혼잡제어 (개념)

### MSS (Maximum Segment Size)

TCP가 한 세그먼트에 담을 수 있는 최대 데이터. 보통 **1460 바이트** (Ethernet MTU 1500 - IP 헤더 20 - TCP 헤더 20).

### 윈도우

- **수신 윈도우(rwnd)**: "이만큼은 받아줄 수 있어" (수신자 버퍼)
- **혼잡 윈도우(cwnd)**: "이만큼은 네트워크가 견딜 수 있어" (송신자 추정)
- 실제 송신량 = min(rwnd, cwnd)

### 혼잡제어

- **Slow Start**: cwnd를 1 MSS에서 시작, 매 RTT마다 2배 (지수 증가)
- **Congestion Avoidance**: 임계치 도달 시 1씩 (선형)
- **Fast Retransmit + Fast Recovery**: 중복 ACK 3개 받으면 즉시 재전송
- 알고리즘: Reno, NewReno, CUBIC (Linux 기본), BBR (Google, 처리량 우수)

```bash
# 현재 알고리즘
sysctl net.ipv4.tcp_congestion_control

# 변경 (테스트용)
sudo sysctl -w net.ipv4.tcp_congestion_control=bbr
```

> 일반 앱 개발에서 직접 만질 일은 거의 없음. 알고만 두자.

---

## 9. UDP — 단순함의 미학

```
헤더: src port | dst port | length | checksum  (8 bytes)
```

- 연결 없음. 보내고 끝.
- 순서 보장 X, 도착 보장 X.
- 한 datagram = 한 메시지. 경계 보존.

### 사용처

| 프로토콜 | 왜 UDP |
|---|---|
| DNS | 쿼리 1번 응답 1번, 빠름 (큰 응답은 TCP fallback) |
| NTP | 시간 동기 — TCP 오버헤드 불필요 |
| DHCP | 같은 LAN에서 브로드캐스트 |
| VoIP, 비디오 | 지연 < 신뢰성 (재전송보다 손실 허용) |
| 게임 | 위와 동일 |
| **QUIC** (HTTP/3) | UDP 위에서 TCP+TLS를 재구현 (heads-of-line blocking 회피) |

### Java UDP

```java
DatagramSocket sock = new DatagramSocket();
byte[] payload = "hello".getBytes();
DatagramPacket packet = new DatagramPacket(
    payload, payload.length, InetAddress.getByName("8.8.8.8"), 53);
sock.send(packet);

byte[] buf = new byte[1500];
DatagramPacket reply = new DatagramPacket(buf, buf.length);
sock.setSoTimeout(2000);
sock.receive(reply);   // timeout 시 SocketTimeoutException
```

---

## 10. 실제 사례

### "TIME_WAIT 누적으로 새 연결이 안 됨"

- 클라이언트가 동일 (IP, port) 5-tuple로 연결을 빠르게 만들고 끊음 → 로컬 포트 고갈
- 해결: connection pool (Apache HttpClient, OkHttp 등). Spring `RestTemplate`은 기본이 새 연결마다 별도 → `ClientHttpRequestFactory`에 pool 주입.

```java
// Apache HttpClient 5 + Spring
PoolingHttpClientConnectionManager pool = PoolingHttpClientConnectionManagerBuilder.create()
    .setMaxConnTotal(200)
    .setMaxConnPerRoute(50)
    .build();
CloseableHttpClient hc = HttpClients.custom().setConnectionManager(pool).build();
RestTemplate rt = new RestTemplate(new HttpComponentsClientHttpRequestFactory(hc));
```

### "CLOSE_WAIT 1000개" — Github 실화

Tomcat이 응답 보낸 후 클라이언트가 잘못 동작해 FIN을 보내고 close하지 않음 → 서버측 CLOSE_WAIT 누적. 결국 파일디스크립터 한계 도달 → 새 요청 못 받음.

해결: 클라이언트 fix 외에, 서버에서도 `Connection: close` 강제 또는 idle timeout 설정.

```yaml
# Spring Boot Tomcat
server:
  tomcat:
    connection-timeout: 20s
    keep-alive-timeout: 60s
    max-keep-alive-requests: 100
```

### "DNS만 UDP라서 빠르다고요? 그럼 왜 가끔 TCP?"

DNS 응답이 512 바이트 초과 시 (EDNS0 미지원 시) TCP로 fallback. DNSSEC 응답도 큼.

---

## 11. 실습

### Step 1: 3-way handshake 직접 보기

```bash
# 한 터미널에 tcpdump
sudo tcpdump -nn -i any -S 'host example.com and port 80' &

# 다른 터미널에서 요청
curl -sS http://example.com/ > /dev/null
```

캡처에서 SYN, SYN-ACK, ACK를 확인. Wireshark에서 더 쉬움.

### Step 2: TIME_WAIT 만들기

```bash
# WSL Ubuntu
for i in $(seq 1 200); do
    curl -s -o /dev/null http://example.com/
done

ss -tan state time-wait | wc -l
# 많이 쌓임 — keep-alive 없이 매번 새 연결이므로
```

이번엔 keep-alive로:

```bash
# HTTP/1.1 keep-alive: curl이 기본으로 함
# 단, 별도 curl 인스턴스라 매번 새 연결이 됨
# 한 curl 인스턴스에서 여러 요청
curl -s -o /dev/null \
    http://example.com/ http://example.com/ http://example.com/ http://example.com/

ss -tan state time-wait | wc -l   # 거의 안 늘어남
```

### Step 3: 로컬 서버로 상태 관찰

WSL에서:

```bash
# 간단한 서버
python3 -m http.server 8080 &

# 다른 터미널
ss -tlnp 'sport = :8080'
curl http://localhost:8080/
ss -tan 'sport = :8080 or dport = :8080'

# CLOSE_WAIT 만들기 — 클라이언트가 일부만 읽고 끊기
(echo "GET / HTTP/1.1"; echo "Host: localhost"; echo ""; sleep 30) | nc localhost 8080 &
# 다른 터미널에서 ss로 보면 어떤 상태?
ss -tan 'sport = :8080 or dport = :8080'
```

### Step 4: PowerShell로

```powershell
# 본인 PC의 LISTEN 포트와 프로세스
Get-NetTCPConnection -State Listen |
    Select LocalPort,
           @{N='Process';E={(Get-Process -Id $_.OwningProcess).Name}} |
    Sort LocalPort

# TIME_WAIT 카운트
(Get-NetTCPConnection -State TimeWait).Count

# 특정 원격지로 가는 연결
Get-NetTCPConnection -RemoteAddress 8.8.8.8 -ErrorAction SilentlyContinue
```

---

## 더 읽어볼 자료

- 📘 『TCP/IP Illustrated, Vol. 1』 Ch. 13~17 — TCP 전반
- 🔗 RFC 9293 — TCP (2022 업데이트, 옛 RFC 793 대체)
- 🔗 RFC 768 — UDP (정말 짧음, 한 페이지)
- 🔗 Vincent Bernat, "Coping with the TCP TIME-WAIT": <https://vincent.bernat.ch/en/blog/2014-tcp-time-wait-state-linux>
- 🔗 ss 매뉴얼: `man ss`
- 🎓 Brendan Gregg, "TCP analysis": <https://www.brendangregg.com/tcpsnoop.html>

---

## 자가 점검

- [ ] 3-way handshake와 4-way termination을 그림으로 그린다
- [ ] TIME_WAIT가 왜 2MSL 동안 남는지 두 가지 이유를 댄다
- [ ] CLOSE_WAIT 누적이 보이면 어디서 고쳐야 하는지(앱) 안다
- [ ] `ss -tlnp` 출력 해석 가능
- [ ] Java try-with-resources가 왜 중요한지 (CLOSE_WAIT 방지) 설명한다

다음: [`04_dns_http_tls.md`](04_dns_http_tls.md)
