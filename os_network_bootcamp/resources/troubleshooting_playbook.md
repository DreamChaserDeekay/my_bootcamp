# 운영 트러블슈팅 플레이북

증상별 진단·조치 절차. 운영서버에서 바로 쓸 수 있도록 구체적인 명령을 적었다. 본인 경험을 더해가며 업데이트하길 권한다.

---

## 일반 원칙

1. **추측 금지, 측정만.** 한 가설 → 한 측정.
2. **계층별로 좁히기**: 앱 → JVM → 소켓 → TCP → OS.
3. **변경 전에 백업**, 변경 후에 **재측정**.
4. 운영서버에서 destructive 명령은 **deadman switch** (예: `at`로 5분 후 복구 예약) 후 실행.

---

## 시나리오 1: CPU 100%

### 진단

```bash
# 1. 누가 100%?
top -bn1 | head -15
ps aux --sort=-%cpu | head

# 2. Java인 경우 — 어느 스레드?
JPID=<java-pid>
TOP_TID=$(ps -L -p $JPID -o tid,pcpu | sort -rk2 | head -2 | tail -1 | awk '{print $1}')
HEX=$(printf '%x' $TOP_TID)
jstack $JPID | grep "nid=0x$HEX" -A 30

# 3. 시스템 콜 분포
strace -p $JPID -c -f -e all 2>&1 | head -30

# 4. perf 핫스팟
sudo perf top -p $JPID
```

### 흔한 원인

| 원인 | 시그널 |
|---|---|
| Busy loop | jstack에서 RUNNABLE 무한 |
| GC | jstat gcutil OU 100%, FGC 빈번 |
| JIT 컴파일 | 시작 직후만, 자연 안정화 |
| Spin lock | top의 sys% 높음 |
| 잘못된 정규식 catastrophic backtrack | 특정 입력 시 spike |

### 조치

- Busy loop: 코드 fix (Thread.sleep, await)
- GC: 힙 늘리기, GC 알고리즘 변경 (ZGC, G1)
- Spin lock: 동기화 알고리즘 재검토

---

## 시나리오 2: 메모리 누수 의심

### 진단

```bash
# 추세
while true; do
    ps -p $(pgrep -f myapp) -o rss=,%mem= --no-headers
    sleep 10
done

# JVM 힙
jcmd <pid> GC.heap_info
jstat -gcutil <pid> 5s

# 힙 덤프 (운영 영향 있음 — 짧게)
jcmd <pid> GC.heap_dump /tmp/heap_$(date +%s).hprof
# 분석: Eclipse MAT

# 네이티브 메모리
jcmd <pid> VM.native_memory baseline
sleep 600
jcmd <pid> VM.native_memory summary.diff
```

### 흔한 원인

| 원인 | 시그널 |
|---|---|
| 캐시 leak (Map에 무한 추가) | Old Gen 계속 증가 |
| ThreadLocal leak | 스레드풀 + 누적 |
| 네이티브 leak (Netty Direct Buffer) | RSS↑, 힙은 정상 |
| Connection leak | fd 수↑, CLOSE_WAIT |

### 조치

- 힙 덤프에서 dominator tree 확인 → 의외의 큰 객체
- WeakReference, Caffeine 같은 적절한 캐시
- try-with-resources 빠짐없이

---

## 시나리오 3: 새 연결 거부 / "Connection refused"

### 진단

```bash
# 서버가 살아 있나
ss -tlnp 'sport = :8080'                # LISTEN 확인
ps aux | grep -i myapp

# accept queue 가득?
ss -tln 'sport = :8080'                  # Recv-Q 컬럼
nstat | grep -E "ListenOverflows|ListenDrops"

# 방화벽
sudo iptables -L INPUT -n -v | head
sudo ufw status

# 클라이언트 끝
nc -zv <server> 8080
curl -v --connect-timeout 5 http://<server>:8080/
```

### 흔한 원인

| 원인 | 시그널 |
|---|---|
| 앱 안 떠있음 | ss에 LISTEN 없음 |
| 다른 인터페이스에 bind | server.address가 127.0.0.1 |
| 방화벽 | iptables에 DROP/REJECT |
| accept queue overflow | nstat ListenOverflows 증가 |
| 클라이언트 측 라우팅 | traceroute로 확인 |

### 조치

- `server.address: 0.0.0.0` (필요한 경우만, 보안 점검)
- `server.tomcat.accept-count` 증가
- 방화벽 룰 수정

---

## 시나리오 4: 응답 느림 (p95 spike)

### 진단

```bash
# 단계별 시간
curl -w "DNS:%{time_namelookup} TCP:%{time_connect} TLS:%{time_appconnect} TTFB:%{time_starttransfer} Total:%{time_total}\n" -o /dev/null -s URL

# 서버측 — GC?
jstat -gcutil <pid> 1s

# 스레드 풀 가득?
curl http://localhost:8080/actuator/metrics/tomcat.threads.busy
curl http://localhost:8080/actuator/metrics/tomcat.threads.config.max

# DB 풀
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
curl http://localhost:8080/actuator/metrics/hikaricp.connections.pending
```

### 흔한 원인

| 원인 | 시그널 |
|---|---|
| GC pause | jstat FGCT 증가, GC 로그 |
| 스레드 풀 고갈 | tomcat.threads.busy = max |
| DB 풀 고갈 | hikaricp.connections.pending > 0 |
| 외부 API slow | 분산 트레이싱, OpenTelemetry |
| N+1 쿼리 | DB slow query log |
| 디스크 IO 대기 | iostat await↑, top wa% |

### 조치

- 풀 크기 조정 (Little's Law)
- 캐싱
- 비동기/배치 처리

---

## 시나리오 5: TIME_WAIT 폭증

### 진단

```bash
ss -tan state time-wait | wc -l

# 누가? (어느 4-tuple)
ss -tan state time-wait | head

# 로컬 포트 고갈 위험
cat /proc/sys/net/ipv4/ip_local_port_range
```

### 조치

```bash
# 일시
sudo sysctl -w net.ipv4.tcp_tw_reuse=1     # 안전, RFC 1323
sudo sysctl -w net.ipv4.ip_local_port_range="10000 65535"

# 영구
sudo tee -a /etc/sysctl.d/99-tuning.conf <<'EOF'
net.ipv4.tcp_tw_reuse = 1
net.ipv4.ip_local_port_range = 10000 65535
EOF
sudo sysctl -p /etc/sysctl.d/99-tuning.conf
```

진짜 해법: **클라이언트 측 connection pool**. (RestTemplate + Apache HttpClient 5, WebClient + Reactor Netty)

---

## 시나리오 6: CLOSE_WAIT 누적

### 진단

```bash
ss -tan state close-wait | wc -l
ss -tap state close-wait | grep myapp     # 어느 프로세스

# fd 한계
ls /proc/<pid>/fd | wc -l
cat /proc/<pid>/limits | grep -i open
```

### 원인

- **앱이 close()를 안 함**. 거의 항상.
- Spring `RestTemplate` 응답 본문 안 소비 → connection leak
- Java InputStream.close 누락
- HikariCP `leakDetectionThreshold` 미설정

### 조치

- 코드 수정: try-with-resources, finally
- HikariCP: `leakDetectionThreshold: 60000`
- 임시: 앱 재시작 + 모니터링

---

## 시나리오 7: 디스크 가득

### 진단

```bash
df -h
df -ih                                     # inode

# 어디가 큰가
sudo du -h --max-depth=1 / 2>/dev/null | sort -h | tail
sudo du -h --max-depth=1 /var | sort -h | tail
sudo du -h --max-depth=1 /var/log | sort -h | tail

# 큰 파일 톱
sudo find / -type f -size +500M 2>/dev/null

# 삭제된 파일이 잡혀있나? (지운 줄 알았는데 디스크 안 풀림)
sudo lsof | grep deleted | sort -k7 -rn | head
# → 해당 프로세스 재시작 또는 truncate
```

### 자주 차는 곳

- `/var/log` → logrotate 미적용
- `/var/log/journal` → `journalctl --vacuum-size=500M`
- `/tmp` → 정리 cron 또는 systemd-tmpfiles
- `/var/lib/docker` → `docker system prune -af`
- `/var/lib/postgresql` → vacuum 또는 pg_basebackup 잔여

### 조치

```bash
# logrotate 즉시 실행
sudo logrotate -f /etc/logrotate.conf

# journal 정리
sudo journalctl --vacuum-time=7d
sudo journalctl --vacuum-size=500M

# Docker
docker system df
docker system prune -af --volumes

# 삭제된 fd 잡힘
sudo truncate -s 0 /proc/<pid>/fd/<fd-num>
# 더 안전: 프로세스 재시작
```

---

## 시나리오 8: SSH 끊김 / 새 SSH 못 들어옴

### 콘솔로만 접근 가능한 상황

```bash
# 방화벽 상태
sudo iptables -L INPUT -n -v
sudo ufw status

# sshd 상태
sudo systemctl status sshd
sudo journalctl -u sshd -e

# fd 한계 (max user processes 초과)
ulimit -u
cat /proc/<sshd-pid>/limits

# /etc/hosts.allow / hosts.deny
cat /etc/hosts.allow /etc/hosts.deny
```

### 조치

- 방화벽 풀기 (안전망 한 번 더): `sudo iptables -F INPUT`
- sshd 재시작
- 메모리/fd 가득이면 다른 프로세스 정리

> **예방**: iptables 룰 변경 시 항상 `at` deadman 걸기:
>
> ```bash
> echo "iptables -F && iptables -P INPUT ACCEPT" | sudo at now + 5 minutes
> # 룰 변경
> # 새 SSH로 접속 확인
> # sudo atrm <jobid>
> ```

---

## 시나리오 9: DNS 안 풀림

### 진단

```bash
# 1. /etc/hosts (개발 잔재 잊었나)
grep example.com /etc/hosts

# 2. resolver
cat /etc/resolv.conf
systemd-resolve --status                 # systemd-resolved

# 3. 직접
dig example.com
dig @1.1.1.1 example.com
dig @8.8.8.8 example.com

# 4. 권한 서버까지
dig +trace example.com
```

### 흔한 원인

- 잘못된 nameserver
- VPN 또는 회사 DNS 우선순위
- /etc/hosts 잔재
- DNS 캐시 (`systemd-resolve --flush-caches`)

---

## 시나리오 10: 인증서 만료 또는 검증 실패

### 진단

```bash
# 만료일
openssl s_client -connect host:443 -servername host < /dev/null 2>/dev/null | \
    openssl x509 -enddate -noout

# 체인 (intermediate 누락 흔함)
openssl s_client -connect host:443 -servername host -showcerts < /dev/null

# 자세히
curl -vI https://host 2>&1 | grep -E "SSL|certificate"

# Java 측
keytool -list -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit | head
```

### 흔한 원인

- 중간 CA 미포함 → fullchain.pem 설치
- 자체서명 인증서 + Java truststore에 없음
- 시계 어긋남 (NTP 안 돎)
- SNI 미지정 (옛 Java)

### 조치

```bash
# Java truststore에 추가
sudo keytool -import -trustcacerts -alias myca -file myca.pem \
    -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit

# 또는 앱에 truststore 지정
-Djavax.net.ssl.trustStore=/path/to/store.jks \
-Djavax.net.ssl.trustStorePassword=secret

# Spring Boot 3.1+ SSL bundle
spring.ssl.bundle.jks.mybundle.truststore.location=...
```

---

## 시나리오 11: PowerShell 스크립트가 안 돌아감

### 증상

```
.\script.ps1 : 이 시스템에서 스크립트를 실행할 수 없으므로...
```

### 진단·조치

```powershell
Get-ExecutionPolicy -List

# 사용자 범위로 변경 (권장)
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

# 인터넷에서 받은 스크립트 — 차단 마크
Unblock-File .\script.ps1

# 일회성
powershell -ExecutionPolicy Bypass -File .\script.ps1
```

### 다른 증상: "The term ... is not recognized"

```powershell
Get-Command <cmd>                       # 어디 있나
Get-Module -ListAvailable               # 모듈 있나
Import-Module <module>
```

---

## 응급 키트 — 한 번에 정보 수집

`linux-diag.sh`:

```bash
#!/usr/bin/env bash
set -uo pipefail
out=/tmp/diag_$(hostname)_$(date +%s).txt
{
    echo "=== uname ==="
    uname -a
    echo "=== uptime ==="
    uptime
    echo "=== free ==="
    free -h
    echo "=== df ==="
    df -h
    echo "=== top ==="
    top -bn1 | head -20
    echo "=== ps top mem ==="
    ps aux --sort=-%mem | head
    echo "=== ps top cpu ==="
    ps aux --sort=-%cpu | head
    echo "=== ss summary ==="
    ss -s
    echo "=== ss listen ==="
    ss -tlnp
    echo "=== ip ==="
    ip a
    ip route
    echo "=== dmesg tail ==="
    dmesg | tail -30
    echo "=== last journal err ==="
    journalctl -p err -n 30 --no-pager
} > "$out" 2>&1
echo "Saved: $out"
```

`Get-Diag.ps1`:

```powershell
$out = "$env:TEMP\diag_$(hostname)_$(Get-Date -Format yyyyMMdd_HHmmss).txt"
@(
    "=== System ==="
    Get-ComputerInfo OsName, OsVersion, OsBootDevice, OsLastBootUpTime | Format-List | Out-String
    "=== Memory ==="
    Get-CimInstance Win32_OperatingSystem |
        Select TotalVisibleMemorySize, FreePhysicalMemory | Format-List | Out-String
    "=== Disk ==="
    Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3" |
        Select DeviceID, @{N='FreeGB';E={[int]($_.FreeSpace/1GB)}},
               @{N='SizeGB';E={[int]($_.Size/1GB)}} | Format-Table | Out-String
    "=== Top CPU ==="
    Get-Process | Sort CPU -Desc | Select -First 10 Name, CPU, WS | Format-Table | Out-String
    "=== TCP listen ==="
    Get-NetTCPConnection -State Listen | Sort LocalPort | Format-Table | Out-String
    "=== Recent errors ==="
    Get-WinEvent -LogName System -MaxEvents 30 |
        Where LevelDisplayName -in 'Error','Warning' |
        Select TimeCreated, Id, Message | Format-Table -Wrap | Out-String
) | Set-Content $out
"Saved: $out"
```

---

## 본인 운영 노트

> ⬇️ 이 아래는 본인이 마주친 사고를 그때그때 추가하세요.

### 예: 2026-MM-DD — 사고 제목

- **증상**:
- **가설**:
- **측정**:
- **원인**:
- **조치**:
- **재발 방지**:
