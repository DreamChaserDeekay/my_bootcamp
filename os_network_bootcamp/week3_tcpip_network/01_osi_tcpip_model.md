# Day 1 — OSI 7계층 · TCP/IP 4계층 모델

## 한 줄 요약

네트워크는 한 번에 풀기엔 너무 복잡해서 **계층(layer)** 으로 나누었다. 각 계층은 위·아래 계층만 신경 쓰면 되고, 같은 계층끼리는 자기네 규약(프로토콜)으로 대화한다. **OSI 7계층**은 학문적 모델, **TCP/IP 4(또는 5)계층**이 실제 인터넷이 따르는 모델이다.

## 학습 목표

- [ ] OSI 7계층 각각의 책임과 대표 프로토콜을 안다
- [ ] OSI와 TCP/IP 모델의 매핑을 안다
- [ ] **캡슐화(encapsulation)** 와 **역캡슐화**가 무엇인지 그림으로 그릴 수 있다
- [ ] MAC 주소·IP 주소·포트가 각각 어느 계층의 식별자인지 안다
- [ ] Java 소켓 코드의 한 줄이 어느 계층에서 일어나는 일인지 매핑한다

---

## 1. 왜 계층화?

**비유**: 편지 보내기

1. 너가 편지를 씀 → "내용" (응용 계층)
2. 봉투에 넣고 주소를 씀 → "주소 형식" (표현·세션 등)
3. 우체국이 받아 분류 → 광역으로 보냄 (전송·네트워크)
4. 트럭에 실어 도로로 보냄 → 물리적 운송 (데이터링크·물리)

각 단계는 다른 단계의 구현을 몰라도 된다. 너는 트럭의 종류를 모르고도 편지를 보낼 수 있다. **레이어 분리의 핵심 가치**가 이것.

---

## 2. OSI 7계층

```
┌────────────────────────────────────────────────┐
│ 7. Application   응용 (HTTP, SMTP, DNS, FTP)    │
├────────────────────────────────────────────────┤
│ 6. Presentation  표현 (TLS, JPEG, ASCII, UTF-8) │
├────────────────────────────────────────────────┤
│ 5. Session       세션 (NetBIOS, RPC)            │
├────────────────────────────────────────────────┤
│ 4. Transport     전송 (TCP, UDP)                │
├────────────────────────────────────────────────┤
│ 3. Network       네트워크 (IP, ICMP, ARP*)      │
├────────────────────────────────────────────────┤
│ 2. Data Link     데이터링크 (Ethernet, Wi-Fi)   │
├────────────────────────────────────────────────┤
│ 1. Physical      물리 (전기·광·전파)            │
└────────────────────────────────────────────────┘
```

\* ARP는 보통 2.5계층에 두기도 함

### 각 계층 요약

| 계층 | 단위 (PDU) | 식별자 | 책임 | 예 |
|---|---|---|---|---|
| 7 Application | message | URL, hostname | 응용 프로토콜 | HTTP, SMTP, DNS, FTP, SSH |
| 6 Presentation | message | - | 인코딩·암호화 | TLS, JPEG, ASCII, UTF-8 |
| 5 Session | message | - | 세션 관리 | NetBIOS, RPC (현대는 7에 통합되는 추세) |
| 4 Transport | segment(TCP)/datagram(UDP) | port | 종단 간 신뢰성, 다중화 | TCP, UDP, SCTP |
| 3 Network | packet | IP address | 라우팅, 논리 주소 | IP, ICMP, IGMP |
| 2 Data Link | frame | MAC address | 인접 노드 간 전송, 에러 검출 | Ethernet, Wi-Fi, PPP |
| 1 Physical | bit | - | 신호 전송 | UTP 케이블, 광섬유, 전파 |

---

## 3. TCP/IP 4(또는 5)계층 — 실제 인터넷

```
OSI                    TCP/IP (4-layer)          TCP/IP (5-layer)
─────                  ─────────────────         ─────────────────
7 Application
6 Presentation     →   Application          →   Application
5 Session
─────────────────      ─────────────────         ─────────────────
4 Transport        →   Transport            →   Transport
─────────────────      ─────────────────         ─────────────────
3 Network          →   Internet             →   Internet (Network)
─────────────────      ─────────────────         ─────────────────
2 Data Link        →   Network Access       →   Data Link
─────────────────                                ─────────────────
1 Physical         →                            Physical
```

> **요점**: 실제 구현은 7개로 분리 안 되어 있다. 5계층 모델이 가장 현실적. 면접에서는 OSI 7로 답하는 게 안전.

---

## 4. 캡슐화 (Encapsulation)

데이터가 송신되는 과정:

```
응용 데이터 ("Hello")
       │
       ▼   [+ HTTP 헤더]
HTTP 메시지
       │
       ▼   [+ TCP 헤더 (포트, seq, ack, flag)]
TCP 세그먼트
       │
       ▼   [+ IP 헤더 (src IP, dst IP, TTL)]
IP 패킷
       │
       ▼   [+ Ethernet 헤더 (src MAC, dst MAC) + FCS]
Ethernet 프레임
       │
       ▼
물리 신호 (전기·전파)
```

수신 쪽에서는 역순으로 헤더를 벗겨내며 위로 올린다.

### 한 프레임의 실제 모습

```
┌──────────────────────────────────────────────────────────┐
│ Ethernet 헤더 (14 B): dst MAC | src MAC | EtherType       │
├──────────────────────────────────────────────────────────┤
│ IP 헤더 (20 B): ver, len, TTL, proto, src IP, dst IP, ... │
├──────────────────────────────────────────────────────────┤
│ TCP 헤더 (20 B): src port, dst port, seq, ack, flags, ... │
├──────────────────────────────────────────────────────────┤
│ HTTP 메시지: GET / HTTP/1.1\r\nHost: ...                 │
├──────────────────────────────────────────────────────────┤
│ FCS (4 B): Ethernet 프레임 체크섬                         │
└──────────────────────────────────────────────────────────┘
```

각 헤더는 **그 계층이 동작하기 위해 필요한 정보**만 담는다. 위 계층은 아래 헤더의 존재를 거의 모른다.

---

## 5. 식별자의 위계 — MAC, IP, Port

| 계층 | 식별자 | 범위 | 예 |
|---|---|---|---|
| 2 | MAC 주소 | 같은 링크(LAN) 내에서 유일 | `00:1A:2B:3C:4D:5E` (48 bit) |
| 3 | IP 주소 | 전 세계에서 유일해야 (공인 IP) | `192.168.1.10` / `2001:db8::1` |
| 4 | 포트 | 한 호스트 안에서 프로세스 식별 | `80`, `8080`, `443` |
| 7 | URL/호스트명 | 사람이 읽기 위함 | `https://api.example.com:8080/users` |

### 한 줄로

> **MAC 주소는 "이 LAN의 어느 카드"**, **IP는 "인터넷에서 어느 호스트"**, **포트는 "그 호스트의 어느 프로세스"**.

---

## 6. 같은 계층끼리만 "이해"

각 계층의 프로토콜은 **자기 계층의 헤더만** 본다.

- 라우터는 IP 헤더(3계층)만 본다. TCP 페이로드는 모르고 알 필요도 없음.
- 스위치는 Ethernet MAC(2계층)만 본다. IP를 모름 (L3 스위치는 예외).
- 방화벽은 IP+포트+페이로드까지 본다 (deep packet inspection은 5~7계층까지).

이 사실이 운영 디버깅에서 중요:

> "ping은 가는데 HTTP는 안 돼요" → ICMP(L3)는 통하나 TCP 80(L4)이 막힌 것. **계층별로 따로 봐야** 한다.

---

## 7. Java 코드와 계층 매핑

```java
// 7계층 — HTTP (응용)
HttpClient client = HttpClient.newHttpClient();
HttpRequest req = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))   // 7계층의 식별자(URL)
    .GET().build();
HttpResponse<String> res = client.send(req, BodyHandlers.ofString());

// 안에서 어떤 일이 일어나는가:
// 1. DNS lookup: api.example.com → 93.184.216.34 (7→3 계층 매핑)
// 2. TCP socket 생성, connect(host, 443) — L4
// 3. TCP 3-way handshake (SYN/SYN-ACK/ACK) — L4 위에서 L4 행위
// 4. TLS handshake (ClientHello, ServerHello, ...) — L6
// 5. HTTP/1.1 또는 HTTP/2 메시지 송수신 — L7
```

```java
// 4계층 — TCP 직접
Socket sock = new Socket();
sock.connect(new InetSocketAddress("example.com", 80), 5000);  // L4 + L3
sock.getOutputStream().write("GET / HTTP/1.0\r\n\r\n".getBytes());  // L7 메시지
String response = new String(sock.getInputStream().readAllBytes());

// 4계층 — UDP 직접 (DNS, NTP 같은 곳)
DatagramSocket udp = new DatagramSocket();
byte[] payload = "...".getBytes();
DatagramPacket packet = new DatagramPacket(payload, payload.length,
    InetAddress.getByName("8.8.8.8"), 53);
udp.send(packet);
```

> Spring `RestTemplate`/`WebClient`는 다 위 흐름의 추상화. **막혔을 때는 한 계층씩 풀어 봐야 한다.**

---

## 8. 잘 쓰는 진단 도구의 계층별 매핑

| 도구 | 무엇을 보나 |
|---|---|
| `ping` (ICMP) | L3 도달성 — 호스트가 살아있고 IP가 라우팅됨 |
| `traceroute` / `tracert` | L3 경로 — 각 hop의 IP |
| `mtr` | ping + traceroute 통합 |
| `arp -a` | L2 — 같은 LAN의 IP↔MAC 매핑 |
| `ip route` / `route print` | L3 라우팅 테이블 |
| `nslookup`/`dig` | L7 (DNS) |
| `nc` (netcat) | L4 — TCP 포트 열림 확인 |
| `curl -v` | L7 (HTTP, TLS 정보 포함) |
| `openssl s_client` | L6 (TLS) |
| `tcpdump`/`wireshark` | 모든 계층, 헤더 보임 |
| `nmap` | L3, L4 스캔 |
| `iptables`/`Windows Firewall` | L3, L4 필터링 |

---

## 9. 흔한 운영 케이스 — 계층별로 풀기

### 케이스 1: "회사 외부에서 API가 안 돼요"

```
1. DNS (L7) — api.example.com 이 어디로 resolve 되나?
   $ dig api.example.com
2. L3 (IP) — 그 IP에 도달 가능?
   $ ping <ip>
3. L4 (TCP 포트) — 443이 열려 있나?
   $ nc -zv <ip> 443
   $ Test-NetConnection <ip> -Port 443
4. L6 (TLS) — 핸드셰이크 되나? 인증서 유효?
   $ openssl s_client -connect <ip>:443 -servername api.example.com
5. L7 (HTTP) — 응답 코드는?
   $ curl -v https://api.example.com/
```

이 사다리를 위에서 아래로 또는 아래에서 위로 진단하면, 어디서 막혔는지 정확히 짚을 수 있다.

### 케이스 2: "ping은 가는데 SSH가 안 돼요"

- ping(ICMP, L3) OK → IP 라우팅 정상
- ssh(TCP 22, L4) 실패 → 방화벽이 22번만 막거나, sshd가 안 떠 있음
- `nc -zv <host> 22`로 L4 확인 → 응답 없으면 방화벽, "Connection refused"면 데몬이 없음

---

## 10. 실습

### Step 1: 계층별 도구 일주

본인 PC에서 다음을 차례로 실행:

```bash
# Linux/WSL
ping -c 4 google.com                          # L3
traceroute google.com                          # L3 경로
arp -a                                          # L2
ip route                                        # L3 라우팅
ip addr                                         # L3 주소
nc -zv google.com 443                          # L4
dig google.com                                  # L7 (DNS)
curl -v https://google.com 2>&1 | head -30     # L7 (HTTP, TLS)
```

```powershell
# Windows PowerShell
Test-NetConnection google.com -Port 443       # L3+L4 한 번에
Resolve-DnsName google.com                     # L7
Get-NetRoute -DestinationPrefix '0.0.0.0/0'   # L3 기본 게이트웨이
Get-NetNeighbor                                # L2 ARP
tracert google.com                             # L3
curl.exe -v https://google.com 2>&1 | Select -First 30  # L7
```

### Step 2: 한 요청의 캡슐화 보기

```bash
# Wireshark 또는 tcpdump로 google.com 요청 캡처
sudo tcpdump -i any -nn -v 'host google.com and port 443' -c 10
# 또는 GUI Wireshark 필터: tcp.port == 443 and ip.host == 1.2.3.4
```

캡처된 패킷 한 개를 골라 헤더를 모두 확인:

- Ethernet: src MAC, dst MAC
- IP: src IP, dst IP, TTL
- TCP: src port, dst port, flags (SYN, ACK, ...)

### Step 3: Java로 단계 확인

```java
// SimpleClient.java
import java.net.*;
import java.io.*;

public class SimpleClient {
    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();

        // L7 → L3: DNS 풀이
        InetAddress addr = InetAddress.getByName("example.com");
        long tDns = System.nanoTime();

        // L4: TCP connect
        Socket sock = new Socket();
        sock.connect(new InetSocketAddress(addr, 80), 5000);
        long tConn = System.nanoTime();

        // L7: HTTP 요청
        sock.getOutputStream().write("GET / HTTP/1.0\r\nHost: example.com\r\n\r\n".getBytes());
        BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        String firstLine = br.readLine();
        long tHttp = System.nanoTime();

        System.out.printf("DNS:  %.2f ms%n", (tDns - t0) / 1e6);
        System.out.printf("TCP:  %.2f ms%n", (tConn - tDns) / 1e6);
        System.out.printf("HTTP: %.2f ms%n", (tHttp - tConn) / 1e6);
        System.out.println("Status: " + firstLine);
        sock.close();
    }
}
```

```bash
javac SimpleClient.java
java SimpleClient
# DNS:  12.34 ms
# TCP:  87.65 ms
# HTTP: 43.21 ms
# Status: HTTP/1.0 200 OK
```

각 계층의 시간을 따로 보면, 어느 계층이 병목인지 즉시 보임.

---

## 더 읽어볼 자료

- 📘 『TCP/IP Illustrated, Vol. 1』 (W. Richard Stevens) — 이 분야의 성서
- 📘 『Computer Networks』 (Tanenbaum) — 교과서 표준
- 🔗 RFC 1122 (Internet Hosts) — 4-계층 모델 정의
- 🔗 RFC 1180 (TCP/IP Tutorial) — 짧고 좋음
- 🔗 Cloudflare Learning Center: <https://www.cloudflare.com/learning/network-layer/what-is-the-osi-model/>
- 🎓 Stanford CS144: <https://cs144.github.io/>

---

## 자가 점검

- [ ] OSI 7계층을 처음부터 끝까지 외운다
- [ ] MAC, IP, 포트가 각각 어느 계층의 식별자인지 즉시 답한다
- [ ] 캡슐화를 그림으로 그릴 수 있다
- [ ] "ping은 가는데 HTTP 안 됨"을 어느 계층 문제인지 즉답한다
- [ ] Java 코드 한 줄을 보고 어느 계층의 행위인지 매핑한다

다음: [`02_ip_subnet_routing.md`](02_ip_subnet_routing.md)
