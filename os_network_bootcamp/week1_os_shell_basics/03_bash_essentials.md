# Day 3 — Bash 핵심 명령어 · 파이프 · 리다이렉션

## 한 줄 요약

Bash의 본질은 **텍스트 스트림을 작은 명령어들로 조립하는 파이프라인**이다. 30개 남짓의 명령어와 파이프(`|`)·리다이렉션(`>`, `<`)만 익히면, 로그 분석부터 배포 자동화까지 운영 작업의 80%는 한 줄로 해결할 수 있다.

## 학습 목표

- [ ] Bash의 **stdin/stdout/stderr (FD 0/1/2)** 와 리다이렉션 문법을 안다
- [ ] **파이프(`|`)** 와 **명령 치환(`$(...)`, backtick)** 의 차이를 안다
- [ ] 핵심 30개 명령어를 즉시 떠올린다 (`ls`, `cd`, `pwd`, `cat`, `grep`, ...)
- [ ] `glob` 패턴(`*`, `?`, `[...]`)과 quoting(`""` vs `''`) 차이를 안다
- [ ] **환경변수**, `$PATH`, `.bashrc`/`.bash_profile`의 로딩 순서를 안다
- [ ] **잡 컨트롤** (`&`, `jobs`, `fg`, `bg`, `Ctrl+Z`, `nohup`, `disown`)을 활용한다
- [ ] `man`, `--help`, `tldr`로 막힐 때 스스로 답을 찾는다

---

## 1. 셸이란

| 종류 | 설명 |
|---|---|
| **sh** (Bourne) | 원조, POSIX 표준 |
| **bash** (Bourne Again) | Linux 기본, sh의 슈퍼셋 |
| **zsh** | macOS 기본 (Catalina+), 자동완성 강력 |
| **fish** | 사용자 친화, POSIX와 호환 안 됨 |
| **dash** | 가벼움, Ubuntu의 `/bin/sh` |

> **확인**: `echo $SHELL` 또는 `ps -p $$`. WSL Ubuntu는 보통 bash.

### Java 비유

- **셸 = `main(String[] args)`을 끝없이 받는 REPL**. 각 명령은 하나의 프로세스(또는 빌트인).

---

## 2. 입출력 스트림 — stdin / stdout / stderr

| FD | 이름 | 기본 |
|---|---|---|
| 0 | stdin | 키보드 |
| 1 | stdout | 터미널 |
| 2 | stderr | 터미널 |

### 리다이렉션 문법

```bash
cmd > out.txt          # stdout을 파일로 (덮어쓰기)
cmd >> out.txt         # 추가
cmd 2> err.txt         # stderr을 파일로
cmd > out.txt 2>&1     # stdout과 stderr 둘 다 out.txt로
cmd &> out.txt         # 위와 동일 (bash 단축)
cmd < input.txt        # stdin을 파일에서
cmd > /dev/null 2>&1   # 다 버림 ("쉿!")

# Heredoc (여러 줄 문자열 입력)
cat <<EOF
Hello, $USER
Current dir: $(pwd)
EOF

# Heredoc — 변수 치환 막기 (literal)
cat <<'EOF'
$USER 그대로 출력됨
EOF
```

### 파이프 — stdout을 다음 명령의 stdin으로

```bash
ps aux | grep java | wc -l
# 1. ps aux의 출력이
# 2. grep java의 입력이 되고
# 3. wc -l의 입력이 되어 줄 수 세기
```

#### ⚠ stderr은 파이프로 안 감 (기본)

```bash
# 컴파일 에러 메시지를 grep으로 거르고 싶을 때
gcc broken.c 2>&1 | grep error

# 또는 PIPESTATUS, set -o pipefail (스크립팅에서 중요)
set -o pipefail
cmd_a | cmd_b      # cmd_a가 실패해도 파이프라인 전체가 실패로 인식
```

---

## 3. 핵심 명령어 30선

### 디렉터리·파일

```bash
pwd                          # 현재 경로
cd /path                     # 이동
cd -                         # 직전 경로
cd ~                         # 홈
ls -la                       # 상세 + 숨김
ls -lh                       # 사람이 읽기 쉬운 크기 (1.2K, 3.4M)
ls -lt                       # 최근 수정 순
ls -lS                       # 크기 순

tree -L 2                    # 디렉터리 트리 (depth 2)
mkdir -p a/b/c               # 중간 디렉터리까지 생성
rmdir empty_dir              # 빈 디렉터리만
rm -rf garbage/              # 재귀 강제 삭제 (위험!)
cp -r src/ dst/              # 재귀 복사
cp -p file backup            # 권한·타임스탬프 보존
mv old new                   # 이동/이름변경

touch file.txt               # 파일 생성 또는 mtime 갱신
ln -s target link            # 심볼릭링크
```

### 텍스트 보기·검색

```bash
cat file.txt                 # 전체 출력
less file.log                # 페이저 (q로 종료, /로 검색)
head -n 20 file              # 앞 20줄
tail -n 50 file              # 뒤 50줄
tail -f /var/log/syslog      # 실시간 추적 (-F는 로테이션 추적)

grep "ERROR" app.log
grep -i "error" app.log      # 대소문자 무시
grep -n "TODO" *.java        # 줄번호
grep -r "ERROR" /var/log     # 재귀
grep -v "DEBUG" app.log      # 매치 안 되는 줄만 (제외)
grep -E "error|warn" app.log # 확장 정규식
grep -A 3 -B 1 "Exception"   # 매치 뒤 3줄, 앞 1줄

# ripgrep (rg) — 훨씬 빠름, 권장
rg "ERROR" /var/log
```

### 텍스트 변형

```bash
wc -l file.txt               # 줄 수
wc -w file.txt               # 단어 수
sort                         # 정렬
sort -n                      # 숫자
sort -r                      # 내림차순
sort -u                      # 중복 제거 (uniq 없이도)
sort -k2 -t,                 # CSV의 2번째 컬럼 기준
uniq                         # 인접한 중복 제거 (sort 필요)
uniq -c                      # 카운트 포함

cut -d',' -f1,3 data.csv     # CSV 1,3번째 컬럼
tr 'a-z' 'A-Z' < file.txt    # 소문자 → 대문자
tr -d '\r' < windows.txt     # CR 제거 (Windows → Linux)
tr -s ' '                    # 공백 압축

awk '{print $1}' file        # 첫 번째 필드 (Day 2-2에서 심화)
sed 's/foo/bar/g' file       # foo → bar 치환 (Day 2-2)

paste -d',' a.txt b.txt      # 가로로 붙이기
diff old new                 # 차이
diff -u old new              # 통합 형식 (Git 스타일)
comm -12 sorted_a sorted_b   # 공통 줄
```

### 시스템 정보

```bash
uname -a                     # OS 정보
hostname
whoami                       # 현재 사용자
id                           # uid/gid/그룹들
date                         # 현재 시각
date +%Y-%m-%d_%H-%M-%S      # 포맷 지정 (백업 파일명에 자주 씀)
uptime                       # 가동 시간 + load average
free -h                      # 메모리
df -h                        # 디스크
who                          # 로그인 사용자
last                         # 로그인 이력
```

### 프로세스·네트워크 (다음 주차 미리보기)

```bash
ps aux | grep java
top                          # 실시간 (q로 종료)
htop                         # 더 보기 쉬움
kill <PID>                   # 종료 시그널
pkill -f "java.*MyApp"       # 패턴 매치

ss -tlnp                     # 리스닝 TCP 포트
curl -I https://example.com  # HTTP 헤더만
wget -O file.tar.gz URL      # 다운로드
```

### 패키지·서비스

```bash
# Debian/Ubuntu
apt update && apt upgrade
apt install nginx
apt remove nginx
dpkg -l | grep nginx

# RHEL/CentOS
dnf install nginx
yum install nginx
rpm -qa | grep nginx

# 서비스 (systemd)
sudo systemctl start nginx
sudo systemctl status nginx
sudo systemctl enable nginx       # 부팅 시 자동
journalctl -u nginx -f             # 서비스 로그 실시간
```

---

## 4. Globbing — 와일드카드

| 패턴 | 매치 |
|---|---|
| `*` | 0자 이상 (`.`은 제외, 슬래시 제외) |
| `?` | 정확히 1자 |
| `[abc]` | a, b, c 중 하나 |
| `[!abc]` | a, b, c 제외 |
| `[a-z]` | 범위 |
| `**` | (extglob/zsh) 하위 디렉터리 포함 |

```bash
ls *.log                     # 현재 디렉터리의 .log 파일
ls /var/log/*.log
ls file[12].txt              # file1.txt, file2.txt
ls report-202[3-5]-??.csv    # report-2023-01.csv 등

# bash에서 ** 켜기
shopt -s globstar
ls **/*.java                 # 재귀
```

> ⚠ glob은 셸이 확장. 매치되는 파일이 없으면 `*.log` 그대로 명령에 넘어감(`nullglob` 옵션으로 변경 가능).

---

## 5. Quoting — 이거 모르면 사고 난다

```bash
NAME="Alice"
echo $NAME                   # Alice
echo "$NAME"                 # Alice (변수 확장됨)
echo '$NAME'                 # $NAME (그대로)
echo "`date`"                # 현재 시각 (백틱은 명령 치환)
echo "$(date)"               # 현재 시각 (권장 형식)
echo "Hello, $NAME!"         # Hello, Alice!
```

| 따옴표 | 변수 확장 | 명령 치환 | glob | 용도 |
|---|---|---|---|---|
| `"..."` | ⭕ | ⭕ | ❌ | 변수 포함된 문자열 (가장 흔함) |
| `'...'` | ❌ | ❌ | ❌ | 리터럴 |
| 없음 | ⭕ | ⭕ | ⭕ | 분리·확장 모두 적용 (주의) |

### ❌ 위험 / ✅ 안전

```bash
# 공백 있는 파일명 처리
files="my file.txt"

# ❌ 위험: 공백에서 토큰 분리됨
cp $files /tmp/        # cp가 두 파일로 인식

# ✅ 안전: 따옴표
cp "$files" /tmp/

# ✅ 더 안전한 스크립트: 배열 사용
files=("my file.txt" "another file.txt")
cp "${files[@]}" /tmp/
```

> **황금 규칙**: 변수는 항상 `"$var"`로 쓴다. 정말 split이 필요할 때만 따옴표를 뗀다.

---

## 6. 환경변수와 PATH

```bash
echo $PATH                   # 명령 검색 경로 (콜론 구분)
echo $HOME
echo $USER
echo $SHELL

# 추가
export PATH="$HOME/bin:$PATH"

# 일회성
MY_VAR=hello some_command    # some_command 실행 동안만 MY_VAR 존재

# 영구
echo 'export PATH="$HOME/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc             # 즉시 반영 (또는 . ~/.bashrc)

# 환경 변수 전체
env
printenv PATH
```

### `.bashrc` vs `.bash_profile` (자주 헷갈림)

| 파일 | 로드되는 시점 |
|---|---|
| `~/.bash_profile` (or `~/.profile`) | **로그인 셸** (SSH 접속, tty 로그인) |
| `~/.bashrc` | **인터랙티브 비로그인 셸** (터미널 새 창) |
| `/etc/profile`, `/etc/bashrc` | 시스템 전역 |

권장 패턴: `.bash_profile`에서 `.bashrc`를 source.

```bash
# ~/.bash_profile
if [ -f ~/.bashrc ]; then
    source ~/.bashrc
fi
```

### Java 개발자가 자주 만지는 환경변수

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS="-Xmx2g"
export SPRING_PROFILES_ACTIVE=local
```

---

## 7. 잡 컨트롤 — 백그라운드 실행

```bash
long_running_command &       # 백그라운드로 실행
jobs                         # 잡 목록
fg %1                        # 1번 잡을 포그라운드로
bg %1                        # 1번 잡을 백그라운드로
Ctrl+Z                       # 현재 잡 일시정지
Ctrl+C                       # 현재 잡 종료 (SIGINT)

# 터미널 끊겨도 계속 실행
nohup long_cmd > out.log 2>&1 &
disown -h %1                 # 이미 띄운 잡을 hangup으로부터 분리

# 더 견고하게: tmux/screen
tmux new -s work
# Ctrl+B D 로 detach, tmux attach -t work 로 재진입
```

> **Spring Boot 운영 팁**: `nohup java -jar app.jar &`로 띄우면 SSH 끊어도 살아있지만, **로그 회전·재시작·헬스체크는 없음**. 운영에서는 `systemd` 서비스로 등록 권장 (Day 2-1에서 다룸).

---

## 8. 명령어 체이닝

```bash
cmd1 && cmd2                 # cmd1 성공해야 cmd2 실행
cmd1 || cmd2                 # cmd1 실패해야 cmd2 실행
cmd1 ; cmd2                  # 무조건 둘 다
cmd1 & cmd2 & wait           # 둘 다 백그라운드, 모두 끝날 때까지 대기
(cmd1; cmd2)                 # 서브셸에서 실행 (변수 격리)
{ cmd1; cmd2; }              # 같은 셸에서 그룹화

# 종료 코드
echo $?                      # 직전 명령의 종료 코드 (0 = 성공)
```

### 자주 쓰는 패턴

```bash
# 빌드 → 테스트 → 배포
./gradlew build && ./gradlew test && ./deploy.sh

# 디렉터리 진입 → 작업 → 원래로 (실패해도 안전)
(cd /tmp && cleanup_script.sh)

# 실패 시 알림
backup.sh || mail -s "Backup failed" admin@example.com
```

---

## 9. 명령 치환 vs 프로세스 치환

```bash
# 명령 치환: 출력을 문자열로
TODAY=$(date +%F)
echo "Today is $TODAY"

# 프로세스 치환: 출력을 임시 파일처럼
diff <(sort file1.txt) <(sort file2.txt)
# 두 명령의 출력을 가상 FD로 전달

# 입력으로
wc -l < <(grep ERROR app.log)
```

---

## 10. 자주 쓰는 한 줄 패턴

```bash
# nginx 액세스 로그에서 톱 10 IP
awk '{print $1}' /var/log/nginx/access.log | sort | uniq -c | sort -rn | head

# 5xx 응답 개수
grep ' 5[0-9][0-9] ' /var/log/nginx/access.log | wc -l

# 디스크 사용량 큰 디렉터리
du -h --max-depth=1 /var | sort -h | tail

# 큰 파일 찾기 (운영서버 디스크 가득찼을 때)
find / -size +500M -type f 2>/dev/null | head

# 24시간 내 수정된 conf 파일
find /etc -name "*.conf" -mtime -1

# 환경변수 dump
env | sort

# bash 히스토리 검색
Ctrl+R                          # 역방향 검색
history | grep ssh

# 마지막 명령 인자 재사용
ls /very/long/path
cd !$                           # !$ = 직전 명령의 마지막 인자

# 직전 명령 재실행 (sudo로)
sudo !!
```

---

## 11. ❌ 위험 / ✅ 안전 — 운영 사고 사례

### 사례 1: 변수 안 따옴표

```bash
# ❌ 변수가 비어 있을 때 'rm -rf /'가 됨
DIR_TO_DELETE=
rm -rf $DIR_TO_DELETE/*       # rm -rf /* 와 동일 — 시스템 박살

# ✅
DIR_TO_DELETE=""
rm -rf "${DIR_TO_DELETE:?DIR_TO_DELETE is empty}"/*
# ${var:?msg} — var이 비어있으면 에러로 즉시 종료
```

### 사례 2: 파이프 실패 무시

```bash
# ❌ 첫 명령이 실패해도 종료코드 0
cat file_that_doesnt_exist | grep foo
echo $?                       # 1이지만 자동화에서 잡기 힘듦

# ✅
set -e -o pipefail            # 스크립트 첫 줄
cat file | grep foo            # 어느 한 단계라도 실패하면 즉시 종료
```

### 사례 3: 와일드카드 + sudo

```bash
# ❌ /etc/cron.d/와 cron.daily에 우연히 매치되어 의도 외 파일 삭제
sudo rm /etc/cron.d*

# ✅ 명확히
sudo rm /etc/cron.d/oldjob
```

### 사례 4: history에 비밀번호

```bash
# ❌ 패스워드가 history에 남음
mysql -u root -pSecret123 db_name

# ✅ 프롬프트로
mysql -u root -p db_name

# 또는 명령 앞에 공간 (HISTCONTROL=ignorespace 설정 시 히스토리에서 제외)
 mysql -u root -pSecret123 db_name
```

---

## 12. 실습 (Hands-on)

### Step 1: 로그 분석 한 줄 챌린지

```bash
# 샘플 액세스 로그 만들기
cat > /tmp/access.log <<'EOF'
192.168.1.10 - - [05/Mar/2026:10:00:01] "GET / HTTP/1.1" 200 1024
192.168.1.20 - - [05/Mar/2026:10:00:02] "POST /api/login HTTP/1.1" 401 512
192.168.1.10 - - [05/Mar/2026:10:00:03] "GET /products HTTP/1.1" 200 4096
192.168.1.30 - - [05/Mar/2026:10:00:04] "GET / HTTP/1.1" 500 256
192.168.1.10 - - [05/Mar/2026:10:00:05] "GET /products HTTP/1.1" 200 4096
192.168.1.20 - - [05/Mar/2026:10:00:06] "GET /api/me HTTP/1.1" 200 128
EOF

# 챌린지: 다음을 한 줄로 풀어보기
# 1) 각 IP별 요청 수
awk '{print $1}' /tmp/access.log | sort | uniq -c | sort -rn

# 2) 5xx 응답만 추출
awk '$9 ~ /^5/' /tmp/access.log

# 3) 가장 많이 요청된 경로 톱 3
awk '{print $7}' /tmp/access.log | sort | uniq -c | sort -rn | head -3
```

### Step 2: 환경변수 다듬기

```bash
# 1) 현재 PATH 확인 후 ~/bin을 앞에 추가
mkdir -p ~/bin
echo 'export PATH="$HOME/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc

# 2) ~/bin/hello 만들어서 실행
cat > ~/bin/hello <<'EOF'
#!/bin/bash
echo "Hello, $USER! Time: $(date +%T)"
EOF
chmod +x ~/bin/hello
hello
```

### Step 3: 잡 컨트롤

```bash
# 1) sleep을 백그라운드로
sleep 100 &
jobs

# 2) Ctrl+Z로 vim 일시정지하고 셸로 돌아오기
vim /tmp/test.txt
# Ctrl+Z
jobs
fg %1                         # 다시 vim으로

# 3) nohup으로 SSH 끊어도 계속
nohup sleep 600 > /tmp/sleep.log 2>&1 &
exit                           # SSH 끊어도 sleep은 계속
```

### Step 4: 안전한 스크립트 헤더 익히기

```bash
cat > /tmp/safe.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
# -e: 명령 실패 시 즉시 종료
# -u: 정의되지 않은 변수 사용 시 에러
# -o pipefail: 파이프 중간 명령 실패도 감지
IFS=$'\n\t'  # 공백 split 안 함, 줄/탭만

main() {
    local target="${1:-/tmp}"
    echo "Cleaning up: $target"
    # ... 실제 작업
}

main "$@"
EOF
chmod +x /tmp/safe.sh
```

이 4줄(`set -euo pipefail`, `IFS=$'\n\t'`)이 **Bash 스크립트의 안전 벨트**.

---

## 더 읽어볼 자료

- 📘 『The Linux Command Line』 (William Shotts, 무료: <https://linuxcommand.org/tlcl.php>)
- 📘 『Classic Shell Scripting』 (Robbins, Beebe)
- 🔗 Bash Reference Manual: <https://www.gnu.org/software/bash/manual/>
- 🔗 ShellCheck (Bash 린터, 강력 추천): <https://www.shellcheck.net/>
- 🔗 explainshell.com — 명령어를 토큰별로 설명: <https://explainshell.com/>
- 🔗 tldr — 짧은 예제: `tldr tar`, `tldr find`
- 🎓 MIT 6.NULL "Missing Semester" — <https://missing.csail.mit.edu/>

---

## 자가 점검

- [ ] `cmd > out.txt 2>&1`이 무엇을 의미하는지 한 문장으로 설명할 수 있는가?
- [ ] `"$var"`와 `$var`의 차이를 설명할 수 있는가?
- [ ] nginx 로그에서 톱 10 IP를 한 줄로 추출했는가?
- [ ] `set -euo pipefail`이 왜 필요한지 안다?
- [ ] `.bashrc`와 `.bash_profile`의 로드 시점 차이를 안다?
- [ ] ShellCheck를 한 번 돌려봤는가?

다음: [`04_powershell_object_pipeline.md`](04_powershell_object_pipeline.md)
