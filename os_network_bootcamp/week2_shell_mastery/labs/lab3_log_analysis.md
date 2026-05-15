# Lab 3 — 로그 분석 종합 챌린지

## 시나리오

운영 중인 Spring Boot 앱이 새벽 3시에 갑자기 응답이 느려졌다. nginx 액세스 로그와 앱 로그를 받았다. **30분 안에 원인을 좁혀라.**

## 사용할 샘플 데이터

```bash
mkdir -p ~/lab3 && cd ~/lab3

# nginx access.log (요청 200건)
cat > gen.sh <<'GEN'
#!/usr/bin/env bash
set -euo pipefail

declare -a paths=(/ /api/users /api/orders /api/products /health /admin /api/login)
declare -a methods=("GET" "POST" "GET" "GET" "GET" "GET" "POST")
declare -a uas=(
    "Mozilla/5.0 (Windows NT 10.0)"
    "curl/7.88.0"
    "Mozilla/5.0 (Macintosh)"
    "scanner/1.0"
    "kube-probe/1.27"
)

> access.log
for i in $(seq 1 200); do
    ip="192.168.1.$((RANDOM % 30 + 10))"
    # 새벽 3시에 한 IP 폭주
    if (( RANDOM % 100 < 30 && i > 100 )); then
        ip="10.0.0.1"
    fi
    pi=$((RANDOM % ${#paths[@]}))
    path="${paths[$pi]}"
    method="${methods[$pi]}"
    ua="${uas[$((RANDOM % ${#uas[@]}))]}"
    # 새벽 3시 폭주 + 일부 5xx
    if [[ "$ip" == "10.0.0.1" ]] && (( RANDOM % 3 == 0 )); then
        status=500
        size=512
        rt="3.501"
    else
        status=$(shuf -e 200 200 200 200 200 200 200 401 403 404 500 -n 1)
        size=$((RANDOM % 5000 + 100))
        rt=$(printf "%0.3f" "$(echo "scale=3; ($RANDOM%500+10)/100" | bc)")
    fi
    ts="0$((i / 50 + 1)):$(printf "%02d" $((RANDOM % 60))):$(printf "%02d" $((RANDOM % 60)))"
    [[ $i -gt 100 ]] && ts="03:$(printf "%02d" $((RANDOM % 60))):$(printf "%02d" $((RANDOM % 60)))"
    printf '%s - - [05/May/2026:%s +0900] "%s %s HTTP/1.1" %d %d "-" "%s" rt=%s\n' \
        "$ip" "$ts" "$method" "$path" "$status" "$size" "$ua" "$rt" >> access.log
done

# app.log (Spring Boot)
> app.log
for i in $(seq 1 100); do
    ts="2026-05-05 0$((i/40+1)):$(printf "%02d" $((RANDOM % 60))):$(printf "%02d" $((RANDOM % 60))).$(printf "%03d" $((RANDOM % 1000)))"
    [[ $i -gt 60 ]] && ts="2026-05-05 03:$(printf "%02d" $((RANDOM % 60))):$(printf "%02d" $((RANDOM % 60))).$(printf "%03d" $((RANDOM % 1000)))"
    if (( i > 60 && RANDOM % 4 == 0 )); then
        cat >> app.log <<EOF
$ts ERROR [http-nio-8080-exec-$((RANDOM%50))] c.example.service.OrderService - Failed to process order
org.springframework.dao.DataAccessResourceFailureException: Connection pool exhausted
	at org.springframework.jdbc.support.SQLErrorCodesFactory.getErrorCodes(SQLErrorCodesFactory.java:184)
EOF
    elif (( RANDOM % 5 == 0 )); then
        echo "$ts WARN  [http-nio-8080-exec-$((RANDOM%50))] c.example.service.OrderService - Slow query: 1245ms" >> app.log
    else
        echo "$ts INFO  [http-nio-8080-exec-$((RANDOM%50))] c.example.controller.UserController - OK" >> app.log
    fi
done
GEN
chmod +x gen.sh
./gen.sh
```

---

## 챌린지

다음을 한 줄 명령(또는 짧은 파이프라인)으로 답하라.

### Q1: 새벽 3시대 (03:00-03:59) 요청 수

```bash
# Bash
grep -E '\[05/May/2026:03:' access.log | wc -l

# PowerShell
(Select-String -Path access.log -Pattern '\[05/May/2026:03:').Count
```

### Q2: 새벽 3시대 IP별 요청 수 (톱 5)

```bash
grep -E '\[05/May/2026:03:' access.log |
    awk '{print $1}' |
    sort | uniq -c | sort -rn | head -5
```

```powershell
Get-Content access.log |
    Where-Object { $_ -match '\[05/May/2026:03:' } |
    ForEach-Object { ($_ -split ' ')[0] } |
    Group-Object | Sort Count -Desc | Select -First 5 Count, Name
```

### Q3: 응답코드 분포 (전체)

```bash
awk '{print $9}' access.log | sort | uniq -c | sort -rn
```

```powershell
Get-Content access.log |
    ForEach-Object {
        if ($_ -match '" (\d{3}) ') { $matches[1] }
    } | Group-Object | Sort Count -Desc | Select Count, Name
```

### Q4: 500 응답을 가장 많이 받은 IP

```bash
awk '$9 == 500 {print $1}' access.log | sort | uniq -c | sort -rn | head -3
```

### Q5: 응답시간(`rt=`) 톱 5

```bash
awk -F'rt=' 'NR>0 {print $2, $0}' access.log |
    sort -rn -k1 | head -5
```

```powershell
Get-Content access.log |
    ForEach-Object {
        if ($_ -match 'rt=([\d.]+)') {
            [PSCustomObject]@{ Rt = [double]$matches[1]; Line = $_ }
        }
    } | Sort Rt -Desc | Select -First 5 Rt, Line
```

### Q6: 앱 로그에서 ERROR 라인과 그 다음 3줄 (스택트레이스 시작)

```bash
grep -A 3 ' ERROR ' app.log | head -40
```

```powershell
$lines = Get-Content app.log
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match ' ERROR ') {
        $lines[$i..([Math]::Min($i+3,$lines.Count-1))]
        '---'
    }
}
```

### Q7: 가장 흔한 Exception 종류

```bash
grep -oE 'org\.[a-zA-Z.]+Exception' app.log | sort | uniq -c | sort -rn
```

```powershell
Get-Content app.log |
    Select-String -Pattern 'org\.[a-zA-Z.]+Exception' -AllMatches |
    ForEach-Object { $_.Matches.Value } |
    Group-Object | Sort Count -Desc
```

### Q8: 시간대별 에러 분포 (분 단위)

```bash
grep ' ERROR ' app.log |
    awk '{print substr($2, 1, 5)}' |     # HH:MM
    sort | uniq -c
```

### Q9: 분석 리포트 (JSON)

```bash
cat <<EOF
{
  "total_requests": $(wc -l < access.log),
  "3am_requests": $(grep -cE '\[05/May/2026:03:' access.log),
  "5xx_count": $(awk '$9 ~ /^5/' access.log | wc -l),
  "top_3am_ip": "$(grep -E '\[05/May/2026:03:' access.log | awk '{print $1}' | sort | uniq -c | sort -rn | head -1 | awk '{print $2}')",
  "error_logs": $(grep -c ' ERROR ' app.log)
}
EOF
```

---

## 분석 결론 (모범답안 예시)

위 데이터에서 보통 발견되는 패턴:

1. **`10.0.0.1` IP가 새벽 3시대에 비정상적인 요청 폭주** — 스크래퍼 또는 봇 의심
2. **앱 로그에 `DataAccessResourceFailureException: Connection pool exhausted`** — DB 커넥션 풀 고갈
3. **응답시간(`rt=`)이 3.5초 이상으로 급증** — DB 대기

→ **가설**: 봇 트래픽이 폭주하면서 DB 커넥션 풀이 고갈, 정상 사용자도 영향.

→ **조치**:

- 단기: `10.0.0.1` 차단 (nginx `deny`), 풀 크기 증가
- 중기: 레이트 리밋, 캐시 도입
- 장기: 봇 차단 (CAPTCHA, WAF), DB N+1 쿼리 점검

---

## 회고

- 운영에서는 이런 일이 새벽에 일어났을 때 어디부터 봐야 할까?
  - **time-series**: 시간대별 요청·에러 수
  - **per-IP**: 상위 N개 IP의 행동
  - **resource exhaustion 키워드**: `OutOfMemory`, `Connection pool`, `Too many open files`, `Address already in use`
- ELK/Loki/CloudWatch 같은 도구가 있으면 위 한 줄들이 다 GUI로 가능하지만, **단일 서버에서 빠르게 진단할 때** 위 도구가 필수.

다음: [`lab4_powershell_automation.md`](lab4_powershell_automation.md)
