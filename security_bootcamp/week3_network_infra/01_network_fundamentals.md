# Day 1 — 네트워크 기초·OSI·TCP/UDP·DNS·Wireshark

## 1. OSI 7계층 vs TCP/IP — 보안 관점

| OSI | TCP/IP | 프로토콜 | 공격·통제 |
|-----|--------|---------|---------|
| 7 Application | Application | HTTP, SMTP, DNS | XSS, SQLi, Phishing — WAF, 입력 검증 |
| 6 Presentation | (포함) | TLS, MIME | TLS 다운그레이드, 인증서 변조 |
| 5 Session | (포함) | 세션 ID | 세션 하이재킹 |
| 4 Transport | Transport | TCP, UDP, QUIC | SYN flood, port scan |
| 3 Network | Internet | IP, ICMP | IP spoofing, smurf |
| 2 Data Link | Network Access | Ethernet, ARP, WiFi | ARP spoofing, MAC flooding |
| 1 Physical | | Cable, RF | 도청, 케이블 탭핑 |

**핵심 관찰**: 공격은 어느 계층에서든 가능. **방어도 다층(Defense in Depth)** 으로.

---

## 2. TCP 3-way Handshake (그리고 SYN Flood)

```
Client       Server
  |          |
  | --SYN--->|     클라이언트가 연결 요청 (seq=x)
  |<-SYN/ACK-|     서버가 응답 (seq=y, ack=x+1), 큐에 반쯤 열린 연결 보관
  | ---ACK-->|     클라이언트 확인 (ack=y+1), 연결 완성
```

**SYN Flood**: 공격자가 SYN만 잔뜩 보내고 ACK 안 보냄 → 서버 큐 가득 → 신규 연결 거부 (DoS).

**방어**: SYN cookies (`net.ipv4.tcp_syncookies=1`), `tcp_max_syn_backlog` 증가, 방화벽·DDoS 방어 서비스.

---

## 3. UDP

비연결형, 빠르지만 검증 없음. **UDP amplification 공격** (DNS, NTP, Memcached) 이 큰 DDoS의 원천.

방화벽에서 불필요한 UDP 포트 차단. 외부에서 닿을 수 있는 서비스의 amplification factor 점검.

---

## 4. DNS — 위협과 보안

### 4.1 DNS는 평문
기본 DNS(UDP 53)는 평문. 도청·변조 가능. → **DoH (DNS over HTTPS), DoT (DNS over TLS), DNSSEC**.

### 4.2 도메인 보안 레코드
| 레코드 | 용도 |
|--------|------|
| **SPF** | TXT — 메일 발신 IP 화이트리스트. 스푸핑 방지 |
| **DKIM** | 메일 본문 서명 |
| **DMARC** | SPF/DKIM 정책. `p=reject` 가 최강 |
| **CAA** | 어느 CA가 이 도메인 인증서 발급 가능 |
| **MTA-STS** | SMTP TLS 강제 |
| **DNSSEC** | DNS 응답 무결성 |

### 4.3 점검
```bash
dig +short TXT example.com           # SPF, DMARC
dig +short CAA example.com
nslookup -type=TXT _dmarc.example.com
```
또는 https://mxtoolbox.com 에서 종합 점검.

### 4.4 DNS 공격
- **DNS Spoofing/Cache Poisoning** — 응답 위조
- **DNS Tunneling** — DNS 쿼리·응답에 데이터 인코딩 → 방화벽 우회 데이터 유출
- **Domain Hijacking** — 등록기관 계정 탈취 (도메인 자체가 도난)
- **Subdomain Takeover** — 사용 안 하는 CNAME이 외부 서비스(GitHub Pages, Heroku) 가리킬 때 그 외부 서비스에 가입해 takeover

**Subdomain takeover 점검**:
- 본인 도메인 모든 CNAME 확인
- CNAME이 가리키는 외부 서비스가 살아있는지

---

## 5. Wireshark — 패킷 분석 실전

### 5.1 인터페이스 선택
- 가장 트래픽 많은 인터페이스 (보통 이더넷/Wi-Fi)

### 5.2 캡처 필터 vs 디스플레이 필터
- **캡처 필터** (BPF): 캡처 자체를 제한. `port 80`
- **디스플레이 필터**: 이미 캡처한 것에서 표시. `http.request.method == "POST"`

### 5.3 자주 쓰는 필터

```
# 디스플레이 필터
http                              # HTTP만
http.request.method == "POST"
http.host == "example.com"
http.request.uri contains "/login"
tcp.port == 8080
ip.addr == 192.168.1.10
tls.handshake.type == 1           # ClientHello
dns
ftp
not (arp or stp)
```

### 5.4 Follow TCP Stream
요청·응답을 한 줄로 재구성. Right-click → Follow → TCP Stream.

### 5.5 HTTPS 복호화 (개발자 본인 트래픽)
브라우저에 `SSLKEYLOGFILE` 환경 변수 설정 → 키 파일 생성 → Wireshark에 키 파일 지정.

```
SSLKEYLOGFILE=C:\sslkey.log
```
이렇게 하면 본인 브라우저의 HTTPS 트래픽이 보임. (다른 사람 트래픽 X)

### 5.6 의심스러운 패턴
- 평소 안 가던 IP·도메인으로 outbound
- 큰 전송 (data exfiltration)
- DNS 쿼리 패턴 비정상 (긴 서브도메인 → tunneling 가능성)
- 평소 안 쓰는 포트로 트래픽

---

## 6. ARP·LAN 공격

### 6.1 ARP Spoofing (LAN MITM)
LAN에서 공격자가 "내가 게이트웨이"라고 ARP 응답 → 트래픽이 공격자 거쳐감.

**방어**:
- Static ARP (관리 호스트)
- ARP Inspection (스위치 기능)
- 802.1X (포트 인증)
- VPN으로 LAN 신뢰 안 함 (Zero Trust)

### 6.2 회사 Wi-Fi 환경 체크
- WPA3 사용 (없으면 WPA2-Enterprise)
- 게스트망과 사내망 분리
- Captive Portal 인증서

---

## 7. 패시브 / 액티브 정찰 도구 — 네트워크 측면

### 7.1 Nmap 고급 옵션
```bash
# 가장 일반적인 종합 스캔
sudo nmap -sV -sC -O -p- -T4 -oA scan target

# 다른 인터페이스 스푸핑
nmap -D RND:10 target            # decoy
nmap --spoof-mac 0 target

# UDP 스캔 (느림)
sudo nmap -sU --top-ports 100 target

# 취약점 스크립트
nmap --script "vuln and safe" target
```

### 7.2 Masscan
인터넷 전체 스캔 가능 수준의 속도. 본인 자산 인벤토리에:
```bash
masscan 10.0.0.0/16 -p1-65535 --rate=10000
```

### 7.3 Naabu, RustScan
빠른 포트 발견 → Nmap에 넘기는 워크플로우.

---

## 8. 본인 환경 외부 노출 점검

### 자가 진단 표
| 자산 유형 | 외부 노출? | 인증? | 모니터링? | 위험도 |
|---------|---------|------|---------|------|
| 메인 웹 (80/443) | ✅ | 일부 | ✅ | Low |
| 관리자 페이지 | ? | ? | ? | ? |
| API | ? | ? | ? | ? |
| DB 포트 | ❌ (반드시) | - | - | - |
| SSH (22) | 제한 IP | 키 | ✅ | Mid |
| RDP | 절대 X | - | - | - |
| Redis/Memcached | 절대 X | - | - | - |
| Elasticsearch | 절대 X | - | - | - |
| Kubernetes API | 제한 | mTLS | ✅ | High |
| Docker daemon | 절대 X | - | - | - |
| CI/CD (Jenkins) | 제한 | SSO+MFA | ✅ | High |

**가장 흔한 사고 원인**: DB, Redis, Elasticsearch, MongoDB가 인증 없이 외부 노출.

---

## 9. 실습

### 실습 1.1 — Wireshark 기본
1. Wireshark 시작, 인터페이스 선택
2. 캡처 시작, 브라우저로 `http://example.com` 접속 (HTTP, 평문)
3. Stop → 디스플레이 필터 `http`
4. GET 요청 → Follow → TCP Stream으로 평문 확인
5. 같은 작업을 `https://example.com`으로 → TLS로 암호화돼서 못 봄 확인

### 실습 1.2 — DNS 보안 레코드 점검
본인 회사 도메인의 SPF, DMARC, CAA, DNSSEC 점검:
```
nslookup -type=TXT yourcompany.com
nslookup -type=TXT _dmarc.yourcompany.com
nslookup -type=CAA yourcompany.com
```
+ https://mxtoolbox.com/SuperTool.aspx

### 실습 1.3 — Nmap 자가 점검
```bash
# 본인 VPS 또는 회사 외부 IP (사전 허가)
sudo nmap -sV -p- -T4 -oA self_scan your.public.ip
```
결과 분석:
- 의도한 포트만 열려 있는가
- 서비스 버전 노출 정도

### 실습 1.4 — Subdomain Takeover 점검
```bash
# subjack 또는 nuclei
nuclei -u subdomain.example.com -t takeovers/
```

---

## 더 읽어볼 자료
- 📘 *TCP/IP Illustrated* — Stevens (고전이지만 깊다)
- 🔗 RFC 비공식 한국어 — DNS, TCP 관련
- 🎓 SANS SEC503 (네트워크 모니터링)
