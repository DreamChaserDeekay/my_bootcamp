# Day 2 — IP · 서브넷 · CIDR · 라우팅 · NAT

## 한 줄 요약

IP 주소는 단순한 숫자가 아니라 **네트워크 ID + 호스트 ID** 두 부분으로 나뉜다. **서브넷 마스크/CIDR**가 그 경계를 정한다. 라우팅은 "이 IP는 어느 다음 hop으로 보낼까"를 결정하고, NAT는 사설 IP와 공인 IP를 변환한다.

## 학습 목표

- [ ] IPv4 vs IPv6 차이를 안다
- [ ] **CIDR 표기**(예: `192.168.1.0/24`)를 보고 네트워크/브로드캐스트/호스트 수를 계산한다
- [ ] 사설 IP 대역 (RFC 1918)을 안다
- [ ] 라우팅 테이블을 읽고 패킷이 어디로 갈지 추적한다
- [ ] **NAT**(SNAT, DNAT, PAT/Masquerade)를 구별하고 동작을 설명한다
- [ ] 기본 게이트웨이, DNS 서버를 PowerShell·Linux 양쪽에서 조회·설정한다

---

## 1. IPv4 기본

### 표기

- 32 bit = 4 옥텟. 각 옥텟은 0~255.
- 예: `192.168.1.10` (점-십진수 표기, dotted-decimal)

### 클래스(legacy) — 알아두기만

| 클래스 | 시작 비트 | 범위 | 기본 마스크 |
|---|---|---|---|
| A | 0 | 0.0.0.0 ~ 127.255.255.255 | /8 |
| B | 10 | 128.0.0.0 ~ 191.255.255.255 | /16 |
| C | 110 | 192.0.0.0 ~ 223.255.255.255 | /24 |
| D | 1110 | 224~239 (멀티캐스트) | - |
| E | 1111 | 240~ (예약) | - |

> 현대는 **CIDR**가 클래스를 대체. 클래스풀(classful) 라우팅은 거의 안 씀. 면접 단골 주제이긴 함.

---

## 2. 사설 IP (RFC 1918)

인터넷에서 라우팅되지 않는, 사내에서 자유롭게 쓸 수 있는 대역:

| 대역 | CIDR | 크기 |
|---|---|---|
| 10.0.0.0/8 | 10.x.x.x | ~1670만 |
| 172.16.0.0/12 | 172.16.x.x ~ 172.31.x.x | ~104만 |
| 192.168.0.0/16 | 192.168.x.x | ~65k |

특수 대역:

| 대역 | 용도 |
|---|---|
| 127.0.0.0/8 | 루프백 (보통 127.0.0.1) |
| 169.254.0.0/16 | 링크로컬 (DHCP 실패 시 자동) |
| 224.0.0.0/4 | 멀티캐스트 |
| 0.0.0.0/0 | 디폴트 라우트 |
| 255.255.255.255 | 제한 브로드캐스트 |

---

## 3. CIDR과 서브넷 마스크

```
192.168.1.0/24
       ^      ^
       │      └ 네트워크 비트 수 (24)
       └ 네트워크 주소
```

- `/24`: 네트워크 비트 24개, 호스트 비트 8개. 호스트 256개 중 사용 가능 254개 (네트워크/브로드캐스트 제외)
- 마스크: `11111111.11111111.11111111.00000000` = `255.255.255.0`

### CIDR 계산표

| CIDR | Mask | 호스트 수 (사용가능) |
|---|---|---|
| /30 | 255.255.255.252 | 2 |
| /29 | 255.255.255.248 | 6 |
| /28 | 255.255.255.240 | 14 |
| /27 | 255.255.255.224 | 30 |
| /26 | 255.255.255.192 | 62 |
| /25 | 255.255.255.128 | 126 |
| /24 | 255.255.255.0 | 254 |
| /23 | 255.255.254.0 | 510 |
| /22 | 255.255.252.0 | 1022 |
| /21 | 255.255.248.0 | 2046 |
| /20 | 255.255.240.0 | 4094 |
| /16 | 255.255.0.0 | 65,534 |
| /8 | 255.0.0.0 | 16,777,214 |

### 예제: `10.1.2.0/22`

- 네트워크 비트 22, 호스트 비트 10 → 1024 - 2 = **1022 호스트**
- 마스크: `255.255.252.0`
- 범위: `10.1.0.0` ~ `10.1.3.255`
- 네트워크 주소: `10.1.0.0`
- 브로드캐스트: `10.1.3.255`
- 사용가능: `10.1.0.1` ~ `10.1.3.254`

### CIDR 계산 도구

```bash
# Linux: ipcalc
ipcalc 10.1.2.0/22
# 또는
sipcalc 10.1.2.0/22
```

```powershell
# PowerShell: 직접 계산 또는 .NET
[ipaddress]'10.1.2.0' | Format-List

# 또는 모듈
Install-Module PSIPCalc
Get-IPCalc -CIDR 10.1.2.0/22
```

---

## 4. IPv6 — 짧게

- 128 bit = 16 옥텟. 16진수로 표기, 콜론 구분: `2001:0db8:85a3:0000:0000:8a2e:0370:7334`
- 연속 0 그룹은 `::` 한 번만 가능: `2001:db8:85a3::8a2e:370:7334`
- 루프백: `::1`, 미정: `::`

### 주요 대역

| 대역 | 용도 |
|---|---|
| `2000::/3` | 글로벌 유니캐스트 |
| `fc00::/7` | 유니크 로컬 (사설 IP 대응) |
| `fe80::/10` | 링크로컬 (라우팅 안 됨, 같은 링크) |
| `ff00::/8` | 멀티캐스트 |

### IPv6의 특징

- NAT 없음 (이론상). 모든 호스트가 글로벌 IP 가능
- IPSec 통합 (사실상 옵션)
- 자동 구성(SLAAC): 라우터 광고로 주소 자동 생성

---

## 5. 라우팅 테이블

### Linux

```bash
ip route
# default via 192.168.1.1 dev eth0 proto dhcp metric 100
# 169.254.0.0/16 dev eth0 scope link metric 1000
# 192.168.1.0/24 dev eth0 proto kernel scope link src 192.168.1.10

# 더 옛 도구
route -n
```

### Windows

```powershell
Get-NetRoute | Sort DestinationPrefix
# 또는
route print
```

### 라우팅 결정 흐름

패킷이 나갈 때 라우팅 테이블을 위에서 아래로 보며 **가장 구체적(most specific) 매치**를 찾는다.

```
패킷 dst = 192.168.1.50

테이블:
1. 192.168.1.0/24 dev eth0    ← 매치 (/24가 더 구체적)
2. 0.0.0.0/0 via 192.168.1.1  ← 모든 것에 매치하나, 위가 우선

→ 결과: eth0으로 직접 전송 (같은 LAN)
```

```
패킷 dst = 8.8.8.8

테이블:
1. 192.168.1.0/24 dev eth0
2. 0.0.0.0/0 via 192.168.1.1  ← 매치, 게이트웨이로 전달

→ 결과: 게이트웨이 192.168.1.1로 (인터넷)
```

### 라우팅 변경 (운영서버)

```bash
# 임시 라우트 추가
sudo ip route add 10.20.0.0/16 via 192.168.1.254

# 삭제
sudo ip route del 10.20.0.0/16

# 영구화: /etc/netplan/*.yaml (Ubuntu 18+), /etc/sysconfig/network-scripts/ (RHEL)
```

```powershell
# Windows 영구 라우트
New-NetRoute -DestinationPrefix 10.20.0.0/16 -NextHop 192.168.1.254 -InterfaceIndex 12 -PolicyStore PersistentStore
Remove-NetRoute -DestinationPrefix 10.20.0.0/16
```

---

## 6. ARP — 같은 LAN에서 IP를 MAC으로

송신 호스트가 같은 LAN의 IP에 보내려면 그 IP의 MAC을 알아야 함.

```
A "누가 192.168.1.10인가요? 답해주세요. 저는 192.168.1.5 (MAC aa:bb:..)"  (브로드캐스트)
B "내가 192.168.1.10이고 MAC은 cc:dd:..."  (유니캐스트)
```

```bash
# ARP 캐시
ip neigh
# 또는
arp -a

# 캐시 비우기 (트러블슈팅 시)
sudo ip neigh flush all
```

```powershell
Get-NetNeighbor
Get-NetNeighbor -State Reachable
Remove-NetNeighbor -InterfaceIndex 12 -Confirm:$false
```

---

## 7. NAT (Network Address Translation)

사설 IP는 인터넷에서 라우팅 안 되니, **공인 IP로 변환**해줘야 한다. 그게 NAT.

### 종류

| 종류 | 무엇을 바꾸나 |
|---|---|
| **SNAT (Source NAT)** | 나갈 때 src IP를 공인 IP로 |
| **DNAT (Destination NAT)** | 들어올 때 dst IP를 내부 IP로 (=포트포워딩) |
| **PAT (Port Address Translation, Masquerade)** | 여러 사설 IP를 하나의 공인 IP로 공유 (포트 번호로 구분) |

### 그림

```
[사설망]                        [공인망]
192.168.1.10:54321 ──SNAT──> 203.0.113.5:60001 ──> google.com:443
192.168.1.20:33445 ──SNAT──> 203.0.113.5:60002 ──> google.com:443

라우터는 변환 테이블을 유지:
54321→60001 (192.168.1.10)
33445→60002 (192.168.1.20)
```

### 포트포워딩 (DNAT)

```
외부에서 203.0.113.5:8080 접속 → 내부 192.168.1.100:8080 (홈 서버)
```

### iptables로 NAT 설정 (Linux 라우터)

```bash
# SNAT (masquerade — 동적 공인 IP)
sudo iptables -t nat -A POSTROUTING -s 192.168.1.0/24 -o eth0 -j MASQUERADE

# DNAT (포트포워딩)
sudo iptables -t nat -A PREROUTING -i eth0 -p tcp --dport 8080 \
    -j DNAT --to-destination 192.168.1.100:8080
```

### Java/Spring과 NAT

- `0.0.0.0`로 bind 했는데 외부에서 접속 안 됨 → 라우터가 DNAT 안 했거나 방화벽
- Docker `-p 8080:80`: 호스트 8080 → 컨테이너 80, 내부적으로 DNAT
- Kubernetes `Service`: ClusterIP → Pod IP 변환 (kube-proxy가 iptables 룰 자동 생성)

---

## 8. 인터페이스 / IP 조회·설정

### Linux

```bash
# IP 주소와 인터페이스
ip addr show
ip a                            # 단축
ip a show eth0

# 인터페이스 UP/DOWN
sudo ip link set eth0 up
sudo ip link set eth0 down

# IP 추가 (임시)
sudo ip addr add 192.168.1.50/24 dev eth0

# IP 삭제
sudo ip addr del 192.168.1.50/24 dev eth0

# 영구화 (Ubuntu, Netplan)
sudo vim /etc/netplan/01-netcfg.yaml
sudo netplan apply
```

### Windows PowerShell

```powershell
# 정보
Get-NetIPConfiguration                 # ipconfig 대체
Get-NetIPAddress
Get-NetIPAddress -InterfaceAlias Ethernet -AddressFamily IPv4

# 설정 (관리자)
New-NetIPAddress -InterfaceAlias 'Ethernet' -IPAddress 192.168.1.50 `
    -PrefixLength 24 -DefaultGateway 192.168.1.1
Remove-NetIPAddress -IPAddress 192.168.1.50

# DNS
Set-DnsClientServerAddress -InterfaceAlias 'Ethernet' -ServerAddresses '8.8.8.8','1.1.1.1'
Get-DnsClientServerAddress

# 다시 자동으로
Set-NetIPInterface -InterfaceAlias 'Ethernet' -Dhcp Enabled
```

---

## 9. 흔한 진단 시나리오

### "서버에 ping이 안 가요"

```bash
# 1. 자기 자신부터
ping 127.0.0.1                          # 네트워크 스택 정상?
ping <자신의 IP>                        # NIC 정상?

# 2. 같은 LAN
ping <게이트웨이>                       # L2/L3 정상?

# 3. 인터넷
ping 8.8.8.8                            # 라우팅 정상?
ping google.com                         # DNS 정상?

# 4. 경로
traceroute 8.8.8.8                      # 어느 hop에서 멈추나?
mtr 8.8.8.8                             # ping + traceroute 합본
```

### "사내 망의 다른 서브넷이 안 보여요"

- 라우팅 테이블에 그 서브넷이 있나? `ip route get <ip>`
- 방화벽이 막나? (사내 라우터/스위치 ACL)
- 양 끝 호스트의 서브넷 마스크가 일치하나?

### "Docker 컨테이너에서 호스트 DB가 안 보여요"

- 컨테이너에서 호스트는 보통 `host.docker.internal` (Docker Desktop) 또는 게이트웨이 IP
- `--network host` 모드로 컨테이너가 호스트 네트워크 공유
- `iptables -t nat -L`로 Docker가 만든 NAT 규칙 확인

---

## 10. 실습

### Step 1: CIDR 계산 챌린지

다음을 손으로 풀고, ipcalc로 검증:

1. `10.0.5.0/22`의 네트워크 주소, 브로드캐스트, 사용가능 호스트 수
2. `172.16.32.0`이 속한 가장 큰 사설 IP 대역의 CIDR
3. `192.168.1.0/24`를 4개 서브넷으로 분할 (각각 /26)

```bash
ipcalc 10.0.5.0/22
# Network: 10.0.4.0/22
# HostMin: 10.0.4.1
# HostMax: 10.0.7.254
# Broadcast: 10.0.7.255
# Hosts/Net: 1022
```

### Step 2: 라우팅 표 해석

```bash
$ ip route
default via 192.168.1.1 dev eth0
10.10.0.0/16 via 192.168.1.254 dev eth0
192.168.1.0/24 dev eth0 proto kernel scope link src 192.168.1.10

# 다음 패킷은 어디로?
# - dst=8.8.8.8?               → default → 192.168.1.1
# - dst=10.10.5.50?             → 10.10.0.0/16 → 192.168.1.254
# - dst=192.168.1.100?          → eth0 (직접)
```

### Step 3: 본인 PC의 네트워크 정보

```bash
# WSL
ip addr
ip route
cat /etc/resolv.conf
hostname -I
```

```powershell
# Windows
Get-NetIPConfiguration
Get-NetRoute -DestinationPrefix '0.0.0.0/0'
Resolve-DnsName google.com
ipconfig /all
```

본인 IP / 서브넷 마스크 / 게이트웨이 / DNS를 적어보고, 어느 사설 대역에 속하는지 확인.

### Step 4: 패킷의 운명 추적

```bash
# Linux: 어느 인터페이스로 나갈지
ip route get 8.8.8.8
ip route get 10.0.0.1

# Windows
Find-NetRoute -RemoteIPAddress 8.8.8.8
```

---

## 11. ❌ 위험 / ✅ 안전

### 사설 IP를 공인 IP에 쓰지 말 것

```bash
# ❌ 클라우드 보안그룹에서 10.0.0.0/8을 공인 인터넷에서 허용
# (말 안 되지만 실수로 그렇게 쓰는 경우 있음)
aws ec2 authorize-security-group-ingress --cidr 10.0.0.0/8 ...

# ✅ 사설 대역은 VPC 내부에서만 의미
```

### `0.0.0.0`/`::`에 무분별 바인드

```yaml
# Spring application.yml
server:
  # ❌ 모든 인터페이스에 노출 (의도치 않은 공개)
  address: 0.0.0.0

  # ✅ 로컬만 필요하면
  address: 127.0.0.1

  # ✅ 사내망만 필요하면 특정 NIC
  address: 192.168.1.10
```

### NAT를 안전 기능으로 착각

NAT는 **주소 변환**이지 방화벽이 아니다. PAT는 부수적으로 외부에서 내부로 직접 들어오기 어렵게 만들지만, **DNAT 룰만 추가되면 그 바리어는 사라진다**. 보안은 명시적 방화벽(L4 ACL)으로.

---

## 더 읽어볼 자료

- 📘 『TCP/IP Illustrated, Vol. 1』 Ch. 3, 9 (IP, Routing)
- 🔗 RFC 791 (IPv4), RFC 8200 (IPv6), RFC 1918 (사설 IP), RFC 6598 (CGN 사설)
- 🔗 Subnet Calculator: <https://www.subnet-calculator.com/>
- 🔗 Linux `ip` 명령 cheatsheet: <https://access.redhat.com/sites/default/files/attachments/rh_ip_command_cheatsheet_1214_jcs_print.pdf>

---

## 자가 점검

- [ ] `/24`, `/22`, `/16`의 호스트 수를 즉답한다
- [ ] 사설 IP 3대역을 외운다
- [ ] 라우팅 테이블을 보고 어디로 갈지 안다
- [ ] SNAT / DNAT / PAT 차이를 안다
- [ ] PowerShell `New-NetIPAddress`로 임시 IP를 추가해봤다

다음: [`03_tcp_udp_internals.md`](03_tcp_udp_internals.md)
