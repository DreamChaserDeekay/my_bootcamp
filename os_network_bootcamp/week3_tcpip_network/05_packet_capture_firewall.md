# Day 5 — 패킷 캡처 · 방화벽

## 한 줄 요약

운영의 한계 상황 — 로그도 모자라고 추측도 한계일 때 — 의 마지막 무기는 **패킷 캡처**다. `tcpdump`로 한 줄에 캡처하고 Wireshark로 분석하는 능력은, 막연한 가설을 사실로 만든다. 그 옆에 **방화벽 룰**을 정확히 다루는 능력이 함께 가야 한다.

## 학습 목표

- [ ] `tcpdump`의 필수 옵션과 BPF 필터를 자유롭게 쓴다
- [ ] Wireshark로 캡처를 열어 흐름·통계·디코드를 본다
- [ ] **`iptables`** / `nftables` 의 chain 흐름과 룰 작성법을 안다
- [ ] **Windows Defender Firewall**을 PowerShell로 설정한다
- [ ] 운영 환경의 흔한 방화벽 시나리오(웹 서버, DB 접근 제한)를 작성한다
- [ ] 패킷 캡처의 윤리·법적 경계를 안다

---

## 1. tcpdump — 한 줄로 캡처

### 기본

```bash
# 모든 인터페이스에서 모든 패킷 (조심: 시끄러움)
sudo tcpdump -i any

# 특정 인터페이스
sudo tcpdump -i eth0

# 인터페이스 목록
sudo tcpdump -D

# 출력 조절
sudo tcpdump -nn          # IP/포트 숫자로 (DNS 안 함, 빠름)
sudo tcpdump -v            # verbose
sudo tcpdump -vv -vvv     # 더, 더
sudo tcpdump -X            # 페이로드 hex+ascii
sudo tcpdump -A            # ascii 페이로드만

# 개수 제한
sudo tcpdump -c 10 ...

# 패킷 크기 (snaplen)
sudo tcpdump -s 0 ...      # 전체 (기본은 truncate)

# 파일로 저장 (Wireshark에서 열기)
sudo tcpdump -i any -w /tmp/capture.pcap port 80

# 파일 읽기 (분석)
tcpdump -r /tmp/capture.pcap -nn
```

### BPF 필터 — 핵심

```bash
# 호스트
sudo tcpdump host 192.168.1.10
sudo tcpdump src host 192.168.1.10
sudo tcpdump dst host 192.168.1.10

# 포트
sudo tcpdump port 443
sudo tcpdump src port 22
sudo tcpdump dst port 80
sudo tcpdump portrange 8000-9000

# 프로토콜
sudo tcpdump tcp
sudo tcpdump udp
sudo tcpdump icmp
sudo tcpdump arp

# 네트워크
sudo tcpdump net 10.0.0.0/8

# 조합 (and, or, not)
sudo tcpdump 'host 192.168.1.10 and port 80'
sudo tcpdump 'tcp and (port 80 or port 443)'
sudo tcpdump 'src 10.0.0.1 and not port 22'

# TCP 플래그
sudo tcpdump 'tcp[tcpflags] & tcp-syn != 0'         # SYN
sudo tcpdump 'tcp[tcpflags] & (tcp-rst|tcp-fin) != 0'  # 종료
sudo tcpdump 'tcp[13] & 0x02 != 0'                  # SYN (옛 표기)

# HTTP 요청만 (GET/POST 시작)
sudo tcpdump -A -s 0 'tcp port 80 and (((ip[2:2] - ((ip[0]&0xf)<<2)) - ((tcp[12]&0xf0)>>2)) != 0)'
# (페이로드 있는 TCP만)
```

### 실전 한 줄

```bash
# 특정 IP와 주고받는 모든 패킷
sudo tcpdump -nn -i any host 192.168.1.100

# 자신과 외부 간 80/443
sudo tcpdump -nn -i any 'not src and dst net 192.168.0.0/16 and (port 80 or port 443)'

# DNS 쿼리만
sudo tcpdump -nn -i any 'udp port 53'

# TCP handshake만 (SYN, SYN-ACK)
sudo tcpdump -nn -i any 'tcp[tcpflags] & tcp-syn != 0'

# 5xx 응답 보일 가능성 있는 (HTTP) — 실제 페이로드 일부 보기
sudo tcpdump -nn -i any -A -s 1500 'tcp port 80'

# Wireshark용 저장
sudo tcpdump -i any -w /tmp/issue.pcap -G 60 -W 10 port 443
# -G 60: 60초마다 새 파일, -W 10: 최대 10개 (rotation)
```

### 운영서버 안전 캡처 패턴

```bash
# 디스크 폭주 방지 — 최대 100MB, 5개 rotation
sudo tcpdump -i any -w /tmp/cap.pcap -C 100 -W 5 'port 443'

# 백그라운드로 + 종료시 정리
sudo tcpdump -i any -w /tmp/cap.pcap port 443 &
TCPID=$!
trap 'sudo kill $TCPID' EXIT
sleep 60
```

---

## 2. Wireshark — GUI 분석

### 일반 사용 흐름

1. **캡처 시작**: 인터페이스 선택 → 시작
2. **필터로 좁히기**:
   - 캡처 필터: BPF (tcpdump와 동일)
   - 표시 필터: Wireshark 전용 (강력)
3. **흐름 따라가기**: 우클릭 → Follow → TCP Stream
4. **통계**: Statistics 메뉴 → I/O Graphs, Conversations, Endpoints, HTTP, TCP Stream Graph

### 표시 필터 (display filter)

```
# 기본
ip.addr == 192.168.1.10
ip.src == 10.0.0.1
tcp.port == 443
http.request.method == GET

# TCP 플래그
tcp.flags.syn == 1 and tcp.flags.ack == 0    # SYN만 (handshake 시작)
tcp.flags.reset == 1                          # RST

# 재전송·중복 ACK
tcp.analysis.retransmission
tcp.analysis.duplicate_ack
tcp.analysis.zero_window

# DNS
dns.qry.name contains "google"
dns.flags.response == 0                       # 쿼리만

# HTTP
http.response.code >= 400
http.host == "api.example.com"

# TLS
tls.handshake.type == 1                       # ClientHello
tls.handshake.extensions_server_name == "example.com"

# 페이로드 검색
frame contains "password"
tcp.payload contains "ERROR"
```

### 운영 디버깅에 자주 쓰는 통계

| 메뉴 | 용도 |
|---|---|
| Conversations | 어느 두 호스트가 가장 많이 통신? |
| I/O Graphs | 시간대별 트래픽 |
| Expert Information | 재전송·중복 ACK 같은 이상 신호 자동 정리 |
| HTTP > Requests | 호스트/URI별 요청 수, 응답시간 |
| TCP Stream Graph > Round Trip Time | RTT 변동 (네트워크 품질) |

### Wireshark 명령형 (tshark)

```bash
# Wireshark의 CLI 버전 — 운영서버에서 GUI 없이도 강력
tshark -i any -Y 'http.response.code >= 400'
tshark -r capture.pcap -Y 'http' -T fields -e ip.src -e http.request.uri
```

---

## 3. iptables — Linux 방화벽 (전통)

### 구조

```
incoming → [PREROUTING] → [INPUT] → 로컬 프로세스
                  ↓
              [FORWARD] → outgoing
                  ↑
로컬 프로세스 → [OUTPUT] → [POSTROUTING] → outgoing
```

테이블:

- **filter** (기본): 차단·허용
- **nat**: SNAT/DNAT
- **mangle**: 패킷 헤더 수정
- **raw**: 연결 추적 회피

체인: 표 안의 룰 체인 (위 PREROUTING/INPUT 등).

### 룰 작성

```bash
# 전체 보기
sudo iptables -L -n -v
sudo iptables -L INPUT -n -v --line-numbers

# 기본 정책 (말미 fallthrough)
sudo iptables -P INPUT DROP        # ⚠ 이 라인 직후 ssh가 끊긴다!
sudo iptables -P FORWARD DROP
sudo iptables -P OUTPUT ACCEPT

# 룰 추가 (앞에 -I, 끝에 -A)
sudo iptables -A INPUT -i lo -j ACCEPT                                  # 루프백
sudo iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT   # 기존 연결
sudo iptables -A INPUT -p tcp --dport 22 -s 192.168.1.0/24 -j ACCEPT    # SSH (사내만)
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT
sudo iptables -A INPUT -p icmp -j ACCEPT                                 # ping
sudo iptables -A INPUT -j DROP                                           # 나머지 차단

# 삭제
sudo iptables -D INPUT 3              # 줄번호로
sudo iptables -F INPUT                # 체인 비우기

# 저장 (영구화)
# Debian/Ubuntu: iptables-persistent
sudo apt install iptables-persistent
sudo netfilter-persistent save

# RHEL/CentOS:
sudo iptables-save > /etc/sysconfig/iptables
```

### ❌ 흔한 사고

```bash
# ❌ 정책을 DROP으로 바꾸기 전에 ESTABLISHED 룰 안 넣음
sudo iptables -P INPUT DROP
# 결과: 즉시 SSH 끊김. 콘솔로만 복구 가능

# ✅ 올바른 순서
sudo iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
sudo iptables -A INPUT -i lo -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT
sudo iptables -P INPUT DROP    # 마지막에
```

> ⚠ **사내 운영서버에서 iptables 변경 시 반드시 `at` 같은 deadman을 걸어두자**:
>
> ```bash
> # 5분 후 모든 룰 비우기 — 잘못되면 자동 복구
> echo "iptables -F && iptables -P INPUT ACCEPT" | sudo at now + 5 minutes
> # 룰 변경 시도
> sudo iptables ...
> # 정상 작동 확인되면
> sudo atrm <jobid>
> ```

### nftables — iptables의 후계

최신 Linux는 nftables. 문법이 더 깔끔.

```bash
sudo nft list ruleset

sudo nft add table inet filter
sudo nft add chain inet filter input '{ type filter hook input priority 0; policy drop; }'
sudo nft add rule inet filter input ct state established,related accept
sudo nft add rule inet filter input iifname lo accept
sudo nft add rule inet filter input tcp dport {22, 80, 443} accept
```

### UFW — 우분투 친화

```bash
sudo ufw status
sudo ufw allow 22/tcp
sudo ufw allow from 192.168.1.0/24 to any port 5432
sudo ufw deny from 1.2.3.4
sudo ufw enable
sudo ufw reload
```

UFW는 내부적으로 iptables/nftables를 만든다. 간단한 서버라면 UFW로 충분.

---

## 4. Windows Defender Firewall — PowerShell

### 조회

```powershell
# 프로파일별 상태 (Domain/Private/Public)
Get-NetFirewallProfile

# 모든 룰
Get-NetFirewallRule

# 활성 룰만
Get-NetFirewallRule -Enabled True | Select DisplayName, Direction, Action

# 특정 룰의 포트
Get-NetFirewallRule -DisplayName "Remote Desktop*" |
    Get-NetFirewallPortFilter
```

### 룰 추가

```powershell
# 인바운드 — 8080 허용
New-NetFirewallRule -DisplayName "Allow MyApp 8080" `
    -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow

# 특정 원격에서만
New-NetFirewallRule -DisplayName "Allow MyApp from LAN" `
    -Direction Inbound -Protocol TCP -LocalPort 8080 `
    -RemoteAddress 192.168.1.0/24 -Action Allow

# 특정 프로그램
New-NetFirewallRule -DisplayName "Allow Java" `
    -Direction Inbound -Program "C:\Program Files\Java\jdk-17\bin\java.exe" -Action Allow

# 차단
New-NetFirewallRule -DisplayName "Block 1.2.3.4" `
    -Direction Inbound -RemoteAddress 1.2.3.4 -Action Block

# 삭제
Remove-NetFirewallRule -DisplayName "Allow MyApp 8080"
```

### 프로파일 제어

```powershell
# Public 프로파일은 항상 강력하게 (모든 인바운드 차단 기본)
Set-NetFirewallProfile -Profile Public `
    -DefaultInboundAction Block -DefaultOutboundAction Allow -Enabled True

# 일시적으로 끄기 (디버깅 — 끝나면 꼭 다시 켜기)
Set-NetFirewallProfile -Profile Domain,Private,Public -Enabled False
```

### Linux ↔ Windows 매핑

| 작업 | Linux | PowerShell |
|---|---|---|
| 모든 룰 보기 | `iptables -L -n` | `Get-NetFirewallRule` |
| 포트 허용 | `iptables -A INPUT -p tcp --dport 80 -j ACCEPT` | `New-NetFirewallRule ... -LocalPort 80 -Action Allow` |
| IP 차단 | `iptables -A INPUT -s 1.2.3.4 -j DROP` | `New-NetFirewallRule ... -RemoteAddress 1.2.3.4 -Action Block` |
| 룰 비우기 | `iptables -F` | `Remove-NetFirewallRule -DisplayName *` (위험) |

---

## 5. 흔한 운영 시나리오

### 시나리오 1: 웹 서버 (Linux)

```bash
sudo iptables -F
sudo iptables -A INPUT -i lo -j ACCEPT
sudo iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 22 -s 10.0.0.0/8 -j ACCEPT    # SSH 사내망만
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT
sudo iptables -A INPUT -p icmp --icmp-type echo-request -m limit --limit 1/sec -j ACCEPT  # ping 레이트리밋
sudo iptables -A INPUT -j LOG --log-prefix "DROP: " --log-level 4    # 차단 로그
sudo iptables -A INPUT -j DROP
```

### 시나리오 2: DB 서버 — 앱 서버에서만 접근

```bash
sudo iptables -A INPUT -p tcp --dport 5432 -s 10.0.1.0/24 -j ACCEPT  # 앱 서버 서브넷
sudo iptables -A INPUT -p tcp --dport 5432 -j DROP
```

### 시나리오 3: 부트 시 sshd 차단으로 락아웃

```bash
# at으로 deadman switch
echo "iptables -F && iptables -P INPUT ACCEPT" | sudo at now + 10 minutes

# 룰 적용
sudo iptables -P INPUT DROP
sudo iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
sudo iptables -A INPUT -i lo -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT     # 잊지 말 것!

# 별도 세션에서 다시 SSH 접속 가능한지 확인
# 가능하면 at job 취소
sudo atrm <jobid>
```

---

## 6. 패킷 캡처와 윤리·법

- 본인이 소유한 시스템·네트워크에서만 캡처
- 사내망: 사전 보안팀 합의 + 작업 변경 승인
- 공용 Wi-Fi 도청: 다수 국가에서 형법 위반 (한국 통신비밀보호법 제16조)
- 캡처된 파일에는 자격증명, 개인정보가 들어있을 수 있음 — **즉시 안전한 디렉터리, 짧은 보관, 안전 삭제**
- TLS 해독을 위한 `SSLKEYLOGFILE` 사용 시 그 키 자체가 비밀 → 외부 공유 금지

### 안전한 캡처 디렉터리

```bash
sudo install -d -m 700 -o root -g root /var/captures
sudo tcpdump -i any -w /var/captures/issue.pcap port 443
# 분석 후
sudo shred -u /var/captures/issue.pcap     # 안전 삭제
```

---

## 7. 실습

### Step 1: 자기 PC의 트래픽 캡처

```bash
# WSL에서
sudo tcpdump -i any -nn -c 20 'port 443'

# 다른 터미널에서
curl -s https://example.com/ > /dev/null
```

캡처에서 어떤 흐름이 보이는가? (SYN → SYN-ACK → ACK → ClientHello → ...)

### Step 2: HTTP 헤더 페이로드 보기

```bash
# 평문 HTTP만 (TLS 없는 옛 서버 또는 본인 로컬 앱)
sudo tcpdump -i any -nn -A -s 0 'tcp port 80'

# 다른 터미널
curl -sS http://example.com/
```

`GET / HTTP/1.1`이 평문으로 보이는지 확인.

### Step 3: 자기 서버에 SYN flood 흉내 (로컬만!)

```bash
# WSL에서 python으로 가짜 서버
python3 -m http.server 8000 &

# hping3 (apt install hping3)으로 SYN만 보내고 ACK 안 함 → SYN_RCVD 누적
sudo hping3 -S -p 8000 --flood -c 100 127.0.0.1
# Ctrl+C로 중단

# 결과
ss -tan state syn-recv
```

⚠ 로컬 127.0.0.1로만. 외부에 절대 하지 말 것.

### Step 4: Windows Firewall로 일시 차단

```powershell
# 자기 PC에서 8080 차단
New-NetFirewallRule -DisplayName "TempBlock8080" `
    -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Block

# 다른 PC나 WSL에서 접속 시도
# 차단 확인 후
Remove-NetFirewallRule -DisplayName "TempBlock8080"
```

### Step 5: iptables 시뮬레이션 (Docker)

```bash
# 새 Docker 컨테이너에서 iptables 실험 (호스트에 영향 X)
docker run --rm -it --privileged ubuntu:22.04 bash
# 안에서
apt update && apt install -y iptables
iptables -L
iptables -A INPUT -p tcp --dport 80 -j DROP
iptables -L -n -v
```

---

## 더 읽어볼 자료

- 📘 『Practical Packet Analysis』 (Chris Sanders) — Wireshark 입문 명서
- 📘 『The TCP/IP Guide』 (Charles Kozierok, 무료 부분) — <http://www.tcpipguide.com>
- 🔗 tcpdump tutorial: <https://danielmiessler.com/study/tcpdump/>
- 🔗 Wireshark docs: <https://www.wireshark.org/docs/>
- 🔗 iptables vs nftables: <https://wiki.archlinux.org/title/Nftables>
- 🔗 Microsoft "Windows Firewall with Advanced Security": <https://learn.microsoft.com/windows/security/threat-protection/windows-firewall/windows-firewall-with-advanced-security>

---

## 자가 점검

- [ ] `tcpdump -i any -nn -c 20 'tcp port 443'`이 무엇을 하는지 즉답
- [ ] BPF 필터로 SYN만 골라낼 수 있는가?
- [ ] iptables INPUT 체인의 흐름을 안다
- [ ] iptables 변경 전에 deadman을 걸 줄 안다
- [ ] PowerShell `New-NetFirewallRule`로 인바운드 룰 추가했다
- [ ] 캡처의 윤리적·법적 경계를 안다

이번 주 마무리:

- [`labs/lab5_tcpdump_wireshark.md`](labs/lab5_tcpdump_wireshark.md)
- [`labs/lab6_firewall_iptables.md`](labs/lab6_firewall_iptables.md)
- [`checklist.md`](checklist.md)
