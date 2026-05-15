# Quick Reference — 한 페이지 치트시트

운영 중 가장 자주 쓰는 명령만. 책상 옆에 두고 보기.

---

## 1. 어디부터 봐야 할지

```
              [속도 느림]                         [에러]
                  ↓                                  ↓
        ┌─────────┴─────────┐               ┌───────┴────────┐
       CPU?                IO?              앱 로그        OS 로그
        ↓                    ↓                ↓              ↓
  top / mpstat       iostat / iotop       grep ERROR    dmesg
  perf top           lsof +D /...         journalctl    journalctl -k
  jstack             vmstat (wa)
```

USE 메소드: **Utilization, Saturation, Errors** 순서로 CPU → 메모리 → 디스크 → 네트워크.

---

## 2. Linux 자주 쓰는 30선

```bash
# 시스템
uname -a; uptime; free -h; df -h; du -sh */
ps aux | head; pgrep -af java; htop
ss -tlnp; ss -tan state established | wc -l
journalctl -u <svc> -f; dmesg -wT

# 파일·검색
ls -la; ls -lh; tree -L 2
find /var/log -name "*.log" -mtime -1
grep -rn "ERROR" /var/log
rg -t java "TODO"

# 텍스트
awk '{print $1}' | sort | uniq -c | sort -rn | head
sed -i.bak 's/old/new/g' file
jq '.users[] | select(.age > 18)'

# 네트워크 진단 (사다리)
ping <host>
traceroute <host>
nc -zv <host> <port>
dig <host>; dig +trace <host>
curl -v <url>
openssl s_client -connect host:443 -servername host

# 패킷
sudo tcpdump -i any -nn 'port 443' -c 50
sudo tcpdump -i any -w /tmp/c.pcap port 443

# 방화벽
sudo iptables -L -n -v
sudo ufw status
```

## 3. PowerShell 자주 쓰는 30선

```powershell
# 시스템
Get-Process | Sort WS -Desc | Select -First 10
Get-Service | Where Status -eq Running
$PSVersionTable; Get-Host
Get-Counter '\Processor(_Total)\% Processor Time'

# 파일·검색
Get-ChildItem -Recurse -File | Where Length -gt 100MB
Select-String -Path *.log -Pattern "ERROR"
(Get-Content file) -replace 'old','new' | Set-Content file

# JSON·CSV
Get-Content data.json | ConvertFrom-Json
Import-Csv data.csv | Where age -gt 18 | Export-Csv out.csv -NoTypeInfo

# 네트워크
Test-NetConnection <host> -Port 443
Resolve-DnsName <host>
Get-NetTCPConnection -State Listen
Get-NetIPConfiguration
Invoke-RestMethod 'https://api.example.com/users'

# 원격
Enter-PSSession <host>
Invoke-Command -ComputerName srv01,srv02 -ScriptBlock { hostname }

# 방화벽
Get-NetFirewallRule | Where Enabled -eq True
New-NetFirewallRule -DisplayName 'Allow 8080' -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow

# 이벤트
Get-WinEvent -LogName System -MaxEvents 50 | Where LevelDisplayName -eq Error
```

---

## 4. TCP 상태머신 한눈에

```
LISTEN ──accept──> SYN_RCVD ──ack──> ESTABLISHED
                                       │
                                  close(): FIN
                                       ↓
                                  FIN_WAIT_1 ── ack ──> FIN_WAIT_2 ── fin ──> TIME_WAIT (2MSL)
                                       (수동: ESTABLISHED → CLOSE_WAIT → LAST_ACK)
```

- **TIME_WAIT**: active close가 60초. 정상. 많아도 보통 OK
- **CLOSE_WAIT**: 앱이 close 안 함. **버그**. 누수
- **SYN_RCVD 누적**: SYN flood 가능성

---

## 5. CIDR 빠른 계산

| /n | hosts | mask |
|---|---|---|
| /24 | 254 | 255.255.255.0 |
| /23 | 510 | 255.255.254.0 |
| /22 | 1022 | 255.255.252.0 |
| /20 | 4094 | 255.255.240.0 |
| /16 | 65534 | 255.255.0.0 |

사설 IP: `10/8`, `172.16/12`, `192.168/16`, 루프백 `127/8`, 링크로컬 `169.254/16`.

---

## 6. curl 한 줄

```bash
# 타이밍
curl -w "\nDNS:%{time_namelookup}\nTCP:%{time_connect}\nTLS:%{time_appconnect}\nTTFB:%{time_starttransfer}\nTotal:%{time_total}\n" -o /dev/null -s URL

# JSON POST
curl -X POST -H 'Content-Type: application/json' -d '{"x":1}' URL

# 인증
curl -H "Authorization: Bearer $TOKEN" URL

# HTTP/2
curl --http2 -I URL
```

---

## 7. tcpdump BPF

```bash
host 1.2.3.4
port 443
src 1.2.3.4 and dst port 80
tcp[tcpflags] & tcp-syn != 0           # SYN만
'tcp port 80 and (((ip[2:2] - ((ip[0]&0xf)<<2)) - ((tcp[12]&0xf0)>>2)) != 0)'  # HTTP payload만
```

---

## 8. Wireshark display filter

```
ip.addr == 1.2.3.4
tcp.port == 443
tcp.flags.syn == 1
tcp.analysis.retransmission
http.response.code >= 400
tls.handshake.type == 1
dns.qry.name contains "example"
```

---

## 9. Bash 안전 헤더

```bash
#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'
trap 'rm -rf "$TMP"; exit $?' EXIT INT TERM
TMP=$(mktemp -d)
```

## 10. PowerShell 안전 헤더

```powershell
[CmdletBinding(SupportsShouldProcess)]
param(...)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
```

---

## 11. systemd 미니 .service

```ini
[Unit]
Description=My App
After=network.target

[Service]
Type=simple
User=myapp
ExecStart=/usr/bin/java -jar /opt/myapp/app.jar
Restart=on-failure
TimeoutStopSec=30
SuccessExitStatus=143
StandardOutput=journal
StandardError=journal
NoNewPrivileges=true
ProtectSystem=strict
PrivateTmp=true
ReadWritePaths=/var/log/myapp

[Install]
WantedBy=multi-user.target
```

---

## 12. iptables 미니 (서버 보호)

```bash
sudo iptables -F INPUT
sudo iptables -A INPUT -i lo -j ACCEPT
sudo iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 22 -s 10.0.0.0/8 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT
sudo iptables -A INPUT -p icmp -m limit --limit 1/sec -j ACCEPT
sudo iptables -A INPUT -j DROP
# 안전망:
echo "iptables -F && iptables -P INPUT ACCEPT" | sudo at now + 5 minutes
```

---

## 13. Java 진단

```bash
jps -l                              # JVM 목록
jstack <pid>                        # 스레드 덤프
jcmd <pid> Thread.print
jcmd <pid> GC.heap_info
jcmd <pid> VM.native_memory summary
jcmd <pid> JFR.start duration=60s filename=/tmp/r.jfr
jstat -gcutil <pid> 1s
```

---

## 14. 응급 대처 매뉴얼

| 증상 | 1순위 명령 |
|---|---|
| CPU 100% | `top` → `pidstat 1` → `jstack <pid>` |
| 메모리 부족 | `free -h` → `ps aux --sort=-%mem` → `dmesg \| grep -i oom` |
| 디스크 full | `df -h` → `du -h --max-depth=1 / \| sort -h` → `lsof +D /path` |
| 새 연결 거부 | `ss -tln 'sport = :PORT'` (Recv-Q 확인) → `nstat \| grep -i listen` |
| 응답 느림 | `curl -w '...'` → 어느 단계인가? |
| TIME_WAIT 많음 | `ss -tan state time-wait \| wc -l` → 클라이언트 풀 추가 |
| CLOSE_WAIT 많음 | 앱 코드 fd leak 점검 (try-with-resources, RestTemplate 본문 소비) |
| SSH 끊김 | (콘솔 접속) `iptables -L`, `journalctl -u sshd -e` |
