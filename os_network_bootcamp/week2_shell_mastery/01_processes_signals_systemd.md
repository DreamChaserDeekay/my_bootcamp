# Day 1 — 프로세스 · 시그널 · systemd

## 한 줄 요약

운영서버에서 **앱을 안전하게 띄우고, 죽을 때 깨끗이 종료시키고, 다시 살리는** 모든 일은 **시그널**과 **init 시스템(systemd)** 위에서 일어난다. Spring Boot가 SIGTERM에 우아하게 종료되는지, systemd 서비스 파일을 어떻게 쓰는지 모르면 운영을 할 수 없다.

## 학습 목표

- [ ] Linux 시그널 30개 중 핵심 10개를 안다 (`TERM`, `INT`, `KILL`, `HUP`, `USR1`, `USR2`, `STOP`, `CONT`, `CHLD`, `PIPE`)
- [ ] `kill`, `pkill`, `killall`의 차이와 위험을 안다
- [ ] systemd 유닛 파일(`.service`)을 직접 작성해 Spring Boot 앱을 서비스로 등록한다
- [ ] `journalctl`로 서비스 로그를 다룬다
- [ ] systemd timer로 cron을 대체한다
- [ ] Windows 측에서 동등한 개념 (Windows Service, Scheduled Task)을 안다

---

## 1. 시그널이란

시그널은 **프로세스 간 비동기 메시지**. OS가 보내거나, 다른 프로세스가 `kill` 시스템 콜로 보낸다.

### 핵심 시그널

| 번호 | 이름 | 기본 동작 | 의미 | 잡을 수 있나 |
|---|---|---|---|---|
| 1 | SIGHUP | 종료 | 터미널 끊김 / 설정 재로드 관례 | ⭕ |
| 2 | SIGINT | 종료 | **Ctrl+C** | ⭕ |
| 3 | SIGQUIT | 코어 덤프 | Ctrl+\ | ⭕ |
| 9 | SIGKILL | **강제 종료** | 절대 막을 수 없음 | ❌ |
| 11 | SIGSEGV | 코어 덤프 | 잘못된 메모리 접근 | ⭕ (보통은 처리 후 종료) |
| 13 | SIGPIPE | 종료 | 깨진 파이프에 쓰기 | ⭕ |
| 15 | SIGTERM | 종료 | **우아한 종료 요청** (default `kill`) | ⭕ |
| 17 | SIGCHLD | 무시 | 자식이 상태 변경 | ⭕ |
| 18 | SIGCONT | 계속 | 정지된 프로세스 재개 | - |
| 19 | SIGSTOP | 정지 | **막을 수 없는 정지** | ❌ |
| 20 | SIGTSTP | 정지 | **Ctrl+Z** | ⭕ |

> ⚠ **SIGKILL과 SIGSTOP은 절대 잡을 수 없다.** 그 외 모든 시그널은 응용이 핸들러를 설치해 처리 가능.

### `kill` 사용법

```bash
# 기본 SIGTERM
kill 1234

# 시그널 명시
kill -TERM 1234
kill -9 1234
kill -SIGKILL 1234
kill -HUP 1234         # 일부 데몬에서 설정 재로드

# 시그널 번호와 이름 매핑
kill -l                # 전체 목록
kill -l TERM           # 15

# 이름으로
pkill -TERM -f "java.*MyApp"
killall nginx          # 이름 일치하는 모든 프로세스

# 자기 자신의 프로세스 그룹에
kill 0
```

### ❌ 위험 / ✅ 안전

```bash
# ❌ -9 남발: 앱이 finally·shutdown hook 실행 못 함 → 데이터 손실, DB 락 누수
kill -9 $(pgrep -f java)

# ✅ 단계적
kill -TERM $(pgrep -f java)
# 30초 대기
sleep 30
# 아직 살아있으면 -9
kill -KILL $(pgrep -f java) 2>/dev/null || true
```

### Java 앱에서 시그널 처리

```java
// Spring Boot는 기본적으로 SIGTERM에 graceful shutdown
// application.yml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

// 수동으로 hook 등록
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    log.info("Shutting down: flushing cache, closing DB...");
    cacheManager.flush();
    dataSource.close();
}));
```

---

## 2. trap — 셸에서 시그널 처리

```bash
#!/usr/bin/env bash
set -euo pipefail

cleanup() {
    echo "Cleaning up..."
    rm -f "$tmpfile"
}

# 어떤 종료 경로든(정상·INT·TERM) cleanup 호출
trap cleanup EXIT
trap 'echo "Caught Ctrl+C"; exit 130' INT

tmpfile=$(mktemp)
echo "data" > "$tmpfile"
sleep 60     # 여기서 Ctrl+C 또는 SIGTERM → cleanup 실행
```

### 에러 트랩 (디버깅용)

```bash
trap 'echo "ERROR at line $LINENO, command: $BASH_COMMAND"; exit 1' ERR
```

---

## 3. systemd — Linux의 init 시스템

`systemd`는 PID 1로 모든 프로세스의 조상. 서비스(`.service`), 타이머(`.timer`), 마운트(`.mount`), 소켓(`.socket`) 등 여러 유닛 타입 제공.

### 주요 명령

```bash
# 서비스 제어
sudo systemctl start nginx
sudo systemctl stop nginx
sudo systemctl restart nginx
sudo systemctl reload nginx          # SIGHUP 보내기 (앱이 지원해야)
sudo systemctl enable nginx          # 부팅 시 자동 시작
sudo systemctl disable nginx
sudo systemctl status nginx

# 상태 확인
systemctl list-units --type=service
systemctl list-units --type=service --state=failed
systemctl is-active nginx
systemctl is-enabled nginx

# 부팅 분석
systemd-analyze blame                # 부팅 시 느린 서비스
systemd-analyze critical-chain

# 로그 (journalctl)
journalctl -u nginx -f               # 실시간
journalctl -u nginx --since "1 hour ago"
journalctl -u nginx -p err           # err 레벨 이상만
journalctl -u nginx --since today -p warning
journalctl --vacuum-time=7d          # 오래된 로그 정리
journalctl --disk-usage
```

---

## 4. Spring Boot 앱을 systemd 서비스로

### 1) 유닛 파일 작성

`/etc/systemd/system/myapp.service`:

```ini
[Unit]
Description=My Spring Boot App
After=network.target
Wants=network-online.target

[Service]
Type=simple
User=myapp
Group=myapp
WorkingDirectory=/opt/myapp
ExecStart=/usr/bin/java -Xms512m -Xmx2g -jar /opt/myapp/app.jar
ExecStop=/bin/kill -TERM $MAINPID
TimeoutStopSec=30
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

# 환경변수
Environment="SPRING_PROFILES_ACTIVE=prod"
EnvironmentFile=-/etc/myapp/env

# 로그
StandardOutput=journal
StandardError=journal
SyslogIdentifier=myapp

# 보안 강화 (sandboxing)
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ReadWritePaths=/var/log/myapp /opt/myapp/data
ProtectHome=true
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX
RestrictNamespaces=true
LockPersonality=true

# 리소스 한계
LimitNOFILE=65536
LimitNPROC=4096

[Install]
WantedBy=multi-user.target
```

### 2) 적용

```bash
sudo systemctl daemon-reload     # 유닛 파일 변경 후 필수
sudo systemctl enable --now myapp
sudo systemctl status myapp
sudo journalctl -u myapp -f
```

### 핵심 옵션 표

| 옵션 | 의미 |
|---|---|
| `Type=` | `simple` (기본), `forking`, `oneshot`, `notify`, `dbus` |
| `Restart=` | `no`, `on-failure`, `on-abnormal`, `always` |
| `RestartSec=` | 재시작 간격 |
| `TimeoutStopSec=` | SIGTERM 후 SIGKILL까지의 시간 (graceful shutdown 시간) |
| `User=`, `Group=` | 실행 사용자 |
| `Environment=`, `EnvironmentFile=` | 환경변수 |
| `LimitNOFILE=` | 파일디스크립터 한계 (소켓 많을 때 중요) |
| `ProtectSystem=` | `strict`면 `/`, `/usr`, `/efi`, `/boot` 모두 ro |

### ❌ 위험 / ✅ 안전

```ini
# ❌ root로 실행 + 보안 옵션 없음
[Service]
User=root
ExecStart=/usr/bin/java -jar /opt/myapp/app.jar

# ✅ 전용 사용자 + sandboxing
[Service]
User=myapp
Group=myapp
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
ReadWritePaths=/var/log/myapp
```

> **운영 팁**: `SuccessExitStatus=143`은 SIGTERM(15) 종료 시 128+15=143이 성공으로 간주되게 함. 안 넣으면 정상 종료가 "failed"로 표시.

---

## 5. systemd timer (cron 대체)

`/etc/systemd/system/backup.timer`:

```ini
[Unit]
Description=Daily backup

[Timer]
OnCalendar=daily               # 매일 0시
OnCalendar=*-*-* 03:00:00      # 매일 새벽 3시
RandomizedDelaySec=600         # 0~10분 무작위 지연 (서버 분산)
Persistent=true                # 시스템이 꺼져있던 동안 놓친 실행 보충

[Install]
WantedBy=timers.target
```

`/etc/systemd/system/backup.service`:

```ini
[Unit]
Description=Daily backup task

[Service]
Type=oneshot
ExecStart=/usr/local/bin/backup.sh
```

활성화:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now backup.timer
systemctl list-timers
```

### cron vs systemd timer

| 항목 | cron | systemd timer |
|---|---|---|
| 문법 | `0 3 * * *` | `OnCalendar=*-*-* 03:00:00` (사람이 읽기 쉬움) |
| 로그 | mail 또는 별도 리다이렉션 필요 | journal에 자동 |
| 의존성 | 없음 | `After=`, `Requires=`로 표현 |
| 누락 보충 | 안 됨 | `Persistent=true` |
| 멀티 서버 분산 | 직접 sleep | `RandomizedDelaySec=` |
| 디버깅 | `/var/log/cron`, mail | `journalctl -u xxx.service` |

---

## 6. journalctl 마스터

```bash
# 시간 범위
journalctl --since "2026-05-15 10:00" --until "2026-05-15 11:00"
journalctl --since "yesterday"
journalctl --since "1 hour ago"

# 우선순위 필터 (RFC 5424)
journalctl -p err            # error 이상
journalctl -p warning..err   # warning ~ err

# 부팅별
journalctl --list-boots
journalctl -b -1             # 이전 부팅
journalctl -b 0              # 현재 부팅

# 출력 형식
journalctl -u nginx -o json-pretty
journalctl -u nginx -o cat   # 메시지만

# 디스크 사용
journalctl --disk-usage
sudo journalctl --vacuum-time=2weeks
sudo journalctl --vacuum-size=500M

# 영구화 (기본은 부팅 시 휘발일 수 있음)
sudo mkdir -p /var/log/journal
sudo systemd-tmpfiles --create --prefix /var/log/journal
```

### 로그 영구 보관 설정

`/etc/systemd/journald.conf`:

```ini
[Journal]
Storage=persistent
SystemMaxUse=2G
SystemMaxFileSize=200M
MaxRetentionSec=4weeks
```

---

## 7. Windows 대응 — Windows Service / Scheduled Task

### Windows Service (PowerShell)

```powershell
# 서비스 목록·제어 (cmdlet — Day 4에서 Spring Boot용 NSSM 등도 다룸)
Get-Service
Get-Service | Where-Object Status -eq Running
Start-Service Spooler
Stop-Service Spooler -Force
Set-Service Spooler -StartupType Manual

# CIM 더 자세히
Get-CimInstance Win32_Service | Where Name -eq Spooler |
    Select Name, State, StartMode, PathName, StartName

# Java 앱을 서비스로 등록 (NSSM 사용 권장)
# https://nssm.cc
nssm install MyApp "C:\Program Files\Java\jdk-17\bin\java.exe" "-jar C:\app\app.jar"
nssm set MyApp AppStdout C:\logs\myapp.out.log
nssm set MyApp AppStderr C:\logs\myapp.err.log
nssm start MyApp
```

### Scheduled Task (cron/timer 대응)

```powershell
# 작업 생성
$action  = New-ScheduledTaskAction -Execute 'powershell.exe' `
            -Argument '-NoProfile -File C:\scripts\backup.ps1'
$trigger = New-ScheduledTaskTrigger -Daily -At 3am
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopOnIdleEnd
$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest

Register-ScheduledTask -TaskName "DailyBackup" `
    -Action $action -Trigger $trigger -Settings $settings -Principal $principal

# 조회·제어
Get-ScheduledTask
Get-ScheduledTask -TaskName DailyBackup | Get-ScheduledTaskInfo
Start-ScheduledTask DailyBackup
```

### Windows ↔ Linux 매핑표

| 개념 | Linux | Windows |
|---|---|---|
| init/서비스 매니저 | systemd | Service Control Manager (SCM) |
| 서비스 정의 | `.service` 유닛 파일 | 레지스트리 `HKLM\...\Services\<name>` |
| 로그 집계 | journald | Event Log |
| 정기 작업 | cron / systemd timer | Task Scheduler |
| 시그널 | SIGTERM/SIGKILL | `ServiceController.Stop()`, `TerminateProcess` |
| 우아한 종료 | SIGTERM → TimeoutStopSec | `OnStop()` 콜백, `Stop-Service -Force` |

---

## 8. 실제 사례

### "kill -9 했더니 DB 데이터가 깨졌어요"

- Spring Boot의 `@Transactional` 코드가 커밋 직전에 SIGKILL 받음 → 일부 데이터만 들어가 정합성 깨짐.
- **해결**: `Restart=on-failure` + `TimeoutStopSec=60` + 앱은 graceful shutdown 활성화 + DB는 트랜잭션으로 atomicity 보장. 운영 SOP에 `kill -9` 금지.

### "재부팅 후 앱이 안 살아나요"

- `systemctl start app`만 하고 `enable`을 안 함 → 재부팅 시 자동 시작 안 됨.
- `systemctl enable --now app` 한 줄로 둘 다 처리.

### "로그가 안 보여요"

- 앱이 stdout/stderr로 안 쓰고 자체 파일에 씀 → journal에 안 잡힘.
- Spring Boot의 `logging.file.name`을 빼고 stdout로 보내거나, journal 대신 그 파일을 추적.

---

## 9. 실습 (Hands-on)

### Step 1: 간단한 데몬을 systemd로

```bash
# 가짜 데몬 작성
sudo tee /usr/local/bin/heartbeat.sh > /dev/null <<'EOF'
#!/usr/bin/env bash
trap 'echo "Caught SIGTERM, exiting cleanly"; exit 0' TERM
while true; do
    echo "heartbeat $(date)"
    sleep 5
done
EOF
sudo chmod +x /usr/local/bin/heartbeat.sh

# 유닛 파일
sudo tee /etc/systemd/system/heartbeat.service > /dev/null <<'EOF'
[Unit]
Description=Heartbeat demo

[Service]
Type=simple
ExecStart=/usr/local/bin/heartbeat.sh
Restart=on-failure
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now heartbeat
sudo systemctl status heartbeat
sudo journalctl -u heartbeat -f
# Ctrl+C로 journal 보기 종료

sudo systemctl stop heartbeat   # SIGTERM 처리되는지 확인
sudo systemctl disable heartbeat
```

### Step 2: 시그널 핸들러 비교

```bash
# Bash로 SIGTERM 잡기
cat > /tmp/sig.sh <<'EOF'
#!/usr/bin/env bash
trap 'echo TERM; exit 0' TERM
trap 'echo INT; exit 0'  INT
while true; do sleep 1; done
EOF
chmod +x /tmp/sig.sh
/tmp/sig.sh &
PID=$!
sleep 2
kill -TERM $PID
wait
```

### Step 3: timer로 주기 작업

위 §5의 backup timer를 직접 만들고 `systemctl list-timers`로 다음 실행 시각 확인.

### Step 4: Windows Scheduled Task

```powershell
# 1분 뒤 메모장 띄우는 일회성 작업
$action = New-ScheduledTaskAction -Execute 'notepad.exe'
$trigger = New-ScheduledTaskTrigger -Once -At ((Get-Date).AddMinutes(1))
Register-ScheduledTask -TaskName "TestNotepad" -Action $action -Trigger $trigger
# 1분 뒤 메모장 뜨면 성공
Unregister-ScheduledTask -TaskName "TestNotepad" -Confirm:$false
```

---

## 더 읽어볼 자료

- 📘 『The Linux Programming Interface』 Ch. 20~22 (Signals), Ch. 37 (Daemons)
- 🔗 systemd 공식 문서: <https://systemd.io/>
- 🔗 `man 5 systemd.unit`, `man 5 systemd.service`, `man 5 systemd.timer`
- 🔗 Lennart Poettering, "Writing systemd Service Files": <https://www.freedesktop.org/software/systemd/man/systemd.service.html>
- 🔗 Windows Service 모범 사례: <https://learn.microsoft.com/dotnet/core/extensions/windows-service>

---

## 자가 점검

- [ ] SIGTERM과 SIGKILL의 결정적 차이를 한 문장으로 설명한다
- [ ] `Restart=on-failure` + `TimeoutStopSec`을 운영서버에서 왜 함께 써야 하는지 안다
- [ ] `journalctl -u myapp --since "1 hour ago" -p err`이 무엇을 보여줄지 즉시 안다
- [ ] systemd timer로 cron job을 옮기는 방법을 안다
- [ ] Windows `Register-ScheduledTask`로 일회성 작업을 만들어봤다

다음: [`02_text_processing_regex.md`](02_text_processing_regex.md)
