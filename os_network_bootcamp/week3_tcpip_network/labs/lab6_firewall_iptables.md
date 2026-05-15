# Lab 6 — 방화벽 룰 만들기

## 시나리오

회사 신규 EC2 인스턴스(또는 Docker 컨테이너)에 Spring Boot 앱을 띄우려 한다. 다음 정책을 만족해야 한다:

1. SSH(22): 사내 VPN 대역 `10.0.0.0/8`에서만
2. HTTPS(443): 모두
3. HTTP(80): 모두 (443으로 리다이렉트 용)
4. App(8080): localhost와 nginx(10.0.1.10)에서만
5. DB(5432): nginx가 아닌 앱 서버 대역(`10.0.2.0/24`)에서만
6. ping: 사내만, 레이트리밋 1초당 1회
7. 기타 모두 차단

---

## 1. 환경 준비 (Docker로 격리)

호스트의 iptables 망가질까봐 Docker 컨테이너에서 실험:

```bash
docker run --rm -it --name fw-lab --privileged ubuntu:22.04 bash

# 컨테이너 안
apt update && apt install -y iptables iproute2 curl net-tools
```

또는 WSL Ubuntu에서 (호스트 영향 있으니 주의 — 본인 PC만):

```bash
# 시작 전 현재 상태 백업
sudo iptables-save > ~/iptables-before.rules
# 끝나면 복구: sudo iptables-restore < ~/iptables-before.rules
```

---

## 2. 룰 작성

```bash
# 1) 정책 변경 전에 안전망 (5분 후 모두 ACCEPT로)
echo "iptables -F && iptables -P INPUT ACCEPT" | sudo at now + 5 minutes

# 2) 기존 룰 비우기
sudo iptables -F INPUT
sudo iptables -F OUTPUT
sudo iptables -F FORWARD

# 3) 루프백과 ESTABLISHED 먼저 (항상)
sudo iptables -A INPUT -i lo -j ACCEPT
sudo iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# 4) SSH — 사내 VPN만
sudo iptables -A INPUT -p tcp --dport 22 -s 10.0.0.0/8 -j ACCEPT

# 5) HTTP/HTTPS — 모두
sudo iptables -A INPUT -p tcp -m multiport --dports 80,443 -j ACCEPT

# 6) App 8080 — localhost와 nginx
sudo iptables -A INPUT -p tcp --dport 8080 -s 10.0.1.10 -j ACCEPT
# (lo는 위에서 이미 ACCEPT)

# 7) DB 5432 — 앱 서버 대역만
sudo iptables -A INPUT -p tcp --dport 5432 -s 10.0.2.0/24 -j ACCEPT

# 8) ping — 사내만, 레이트리밋
sudo iptables -A INPUT -p icmp --icmp-type echo-request \
    -s 10.0.0.0/8 \
    -m limit --limit 1/sec --limit-burst 5 \
    -j ACCEPT

# 9) 차단 로그 (rate-limit으로 디스크 폭주 방지)
sudo iptables -A INPUT -m limit --limit 5/min --limit-burst 10 \
    -j LOG --log-prefix "FW-DROP: " --log-level 4

# 10) 마지막: 모두 거부
sudo iptables -A INPUT -j DROP

# 정책은 ACCEPT 유지 (chain 끝에 도달하면 위 DROP에 잡힘)
# 또는 명시적으로
# sudo iptables -P INPUT DROP

# 확인
sudo iptables -L INPUT -n -v --line-numbers
```

---

## 3. 테스트

다른 머신(또는 별도 컨테이너)에서:

```bash
# 1) SSH from 10.x.x.x → 성공 가설
ssh user@<target> -o ConnectTimeout=3

# 2) SSH from 외부 → 거부
# (실제로는 못 테스트 — 시나리오 시뮬레이션)
ssh user@<target>     # timeout 또는 connection refused 예상

# 3) HTTP/HTTPS → 성공
curl -I http://<target>
curl -kI https://<target>

# 4) 8080 from localhost → 성공
curl -I http://localhost:8080
# 4-bis) 8080 from 외부 → 거부
curl -I http://<target>:8080

# 5) ping
ping -c 3 <target>
```

---

## 4. UFW로 동일 정책

UFW는 같은 정책을 훨씬 짧게:

```bash
sudo ufw --force reset
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow from 10.0.0.0/8 to any port 22 proto tcp comment 'SSH from VPN'
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow from 10.0.1.10 to any port 8080 proto tcp
sudo ufw allow from 10.0.2.0/24 to any port 5432 proto tcp
sudo ufw limit from 10.0.0.0/8 proto icmp
sudo ufw enable
sudo ufw status verbose
```

`ufw limit`이 레이트리밋(15초당 6회 초과 시 차단)을 자동 적용. iptables보다 훨씬 직관적.

---

## 5. Windows Defender Firewall로 동일 정책

PowerShell 7 (관리자):

```powershell
# 8080을 사내망(10.0.0.0/8)에서만
New-NetFirewallRule -DisplayName "Allow App 8080 from LAN" `
    -Direction Inbound -Protocol TCP -LocalPort 8080 `
    -RemoteAddress 10.0.0.0/8 -Action Allow

# 외부에서는 차단 (Public 프로파일)
Set-NetFirewallProfile -Profile Public -DefaultInboundAction Block

# 특정 IP 차단
New-NetFirewallRule -DisplayName "Block 1.2.3.4" `
    -Direction Inbound -RemoteAddress 1.2.3.4 -Action Block

# 룰 보기
Get-NetFirewallRule -Direction Inbound -Enabled True |
    Where Action -eq Allow |
    Select DisplayName, Profile,
        @{N='Ports';E={($_ | Get-NetFirewallPortFilter).LocalPort}}
```

---

## 6. 영구화

### iptables (Ubuntu)

```bash
sudo apt install -y iptables-persistent
sudo netfilter-persistent save
# /etc/iptables/rules.v4 에 저장됨
```

### UFW

UFW는 자동 영구화. 재부팅 시 적용.

### Windows

PowerShell로 만든 룰은 자동 영구화 (Persistent Store).

---

## 7. 운영 시 흔한 함정

### "정책 DROP" 전에 SSH 허용 안 함

```bash
# ❌ 즉시 SSH 끊김
sudo iptables -P INPUT DROP

# ✅ ESTABLISHED 룰 + SSH 룰 먼저
sudo iptables -A INPUT -m state --state ESTABLISHED -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT
sudo iptables -P INPUT DROP
```

### 룰 순서

iptables는 위에서 아래로 평가하다 첫 매치에서 결정. 더 구체적인 룰을 위로.

```bash
# ❌ 모든 80 허용이 먼저 → 차단 룰이 무의미
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 80 -s 1.2.3.4 -j DROP

# ✅ 거부 먼저
sudo iptables -A INPUT -p tcp --dport 80 -s 1.2.3.4 -j DROP
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
```

### IPv6 잊지 말 것

iptables는 IPv4만. IPv6은 `ip6tables`. UFW와 nftables는 둘 다 다룸.

```bash
sudo ip6tables -L
```

### Docker가 룰을 덮어쓰기

Docker는 `DOCKER` 체인을 자동 생성. 호스트 iptables 규칙을 우회할 수 있음. 운영서버에서는 `iptables-restore` 후 docker daemon 재시작 또는 `--iptables=false` 옵션.

---

## 8. 회고

- 위 7가지 정책 중 본인 환경에 그대로 쓸 만한 것은? (대부분 그렇다 — 단 IP만 바꾸면)
- 클라우드 환경(AWS Security Group, Azure NSG)에서는 어떻게 같은 효과를 낼까? (호스트 방화벽 + 클라우드 방화벽 이중)
- Spring Boot의 `server.address`로도 비슷한 효과(localhost만 bind)를 낼 수 있는데, 방화벽까지 필요한 이유는? (defense in depth — 한 곳이 뚫려도 다른 곳이 막음)

다음: [`../checklist.md`](../checklist.md)
