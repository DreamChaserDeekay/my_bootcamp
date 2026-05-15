# Day 3 — Bash 스크립팅: 함수 · 트랩 · 에러 처리 · 인자 파싱

## 한 줄 요약

운영 스크립트의 차이는 **에러 처리와 멱등성(idempotency)** 이다. 100줄 짜리 배포 스크립트가 50줄에서 실패해도 시스템이 깨지지 않고, 같은 스크립트를 두 번 돌려도 안전하게 끝나도록 만드는 패턴을 익힌다.

## 학습 목표

- [ ] **안전 헤더** `set -euo pipefail; IFS=$'\n\t'`를 반사적으로 쓴다
- [ ] 함수 정의·지역 변수(`local`)·반환값을 안다
- [ ] **`trap EXIT`** 으로 정리 코드를 보장한다
- [ ] `getopts` 또는 수동 파싱으로 인자를 다룬다
- [ ] 배열 (`arr=(a b c)`, `${arr[@]}`, `${#arr[@]}`)을 자유롭게 쓴다
- [ ] 로깅·드라이런(`--dry-run`)·확인 프롬프트 패턴을 안다
- [ ] **ShellCheck**로 정적분석한다

---

## 1. 안전 헤더

```bash
#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

# 또는 더 자세히
set -e          # 명령 실패 시 즉시 종료
set -u          # 정의되지 않은 변수 사용 시 에러
set -o pipefail # 파이프 중 어느 단계라도 실패하면 전체 실패
set -E          # ERR trap이 함수·서브셸에서도 동작
set -o errtrace # 위와 동일
# set -x        # 디버깅: 실행 명령 출력
```

| 옵션 | 막는 사고 |
|---|---|
| `-e` | 중간 명령 실패를 무시하고 다음으로 진행 |
| `-u` | 오타나 변수명 변경 누락 |
| `-o pipefail` | `cmd1 \| cmd2`에서 cmd1 실패를 못 보고 cmd2 성공만 봄 |
| `IFS=$'\n\t'` | 공백 포함 파일명에서 토큰 분리 사고 |

### `set -e`의 함정

`set -e`는 만능이 아니다. 다음은 종료시키지 않는다:

```bash
set -e
if cmd; then echo ok; fi    # if 조건은 검사용으로 간주, -e 무시
cmd || true                  # 명시적으로 || 붙으면 무시
cmd1 | cmd2                  # pipefail 없으면 마지막만 봄
```

그래서 **`-eo pipefail`을 함께** 쓰는 것이 표준.

---

## 2. 함수와 지역 변수

```bash
# 함수 정의 (두 가지 문법, 보통 첫 번째)
my_func() {
    local name="$1"           # 지역변수 — 함수 종료 후 사라짐
    local count="${2:-10}"    # 기본값
    echo "Hello, $name (x$count)"
}

# 호출
my_func "Alice" 5

# 반환값 (exit code, 0=성공)
is_positive() {
    [[ "$1" -gt 0 ]]
}

if is_positive "$num"; then
    echo positive
fi

# 결과를 반환하고 싶을 때 (echo + 명령 치환)
get_count() {
    echo 42
}
COUNT=$(get_count)

# 변수에 직접 (Bash 4.3+, nameref)
get_into() {
    local -n out_var=$1
    out_var="result"
}
get_into MY_VAR
echo "$MY_VAR"  # result
```

### `local`을 빼먹지 말 것

```bash
# ❌ 전역 오염
counter=0
do_work() {
    counter=$((counter + 1))   # 호출자의 counter를 망친다
    # ...
}

# ✅ 명시적 지역
do_work() {
    local counter=0
    counter=$((counter + 1))
}
```

---

## 3. trap — 종료 시 정리 보장

```bash
#!/usr/bin/env bash
set -euo pipefail

TMP_DIR=$(mktemp -d)

cleanup() {
    local rc=$?
    rm -rf "$TMP_DIR"
    [[ $rc -ne 0 ]] && echo "Failed with exit $rc" >&2
    return $rc
}

trap cleanup EXIT
trap 'echo "Interrupted by Ctrl+C"; exit 130' INT TERM

# ... 작업
cp -r data/* "$TMP_DIR/"
process "$TMP_DIR"
# 스크립트가 어떻게 끝나든 (정상, 에러, Ctrl+C) cleanup이 실행됨
```

### 트랩 시그널

| 시그널 | 용도 |
|---|---|
| `EXIT` | 어떤 식으로든 스크립트 종료 시 (가장 흔함) |
| `ERR` | `-e`로 인한 실패 시 (디버깅용) |
| `INT` | Ctrl+C |
| `TERM` | kill |
| `HUP` | 부모 셸 종료 |

### 디버깅 트랩

```bash
trap 'echo "Error at line $LINENO: $BASH_COMMAND (rc=$?)" >&2' ERR
```

---

## 4. 조건문과 비교

### `[[ ... ]]` 권장 (Bash 확장)

```bash
if [[ "$x" == "hello" ]]; then ...; fi
if [[ "$x" != "" ]]; then ...; fi
if [[ -z "$x" ]]; then ...; fi          # 비었나
if [[ -n "$x" ]]; then ...; fi          # 안 비었나
if [[ "$x" =~ ^[0-9]+$ ]]; then ...; fi # 정규식
if [[ "$a" == "$b" || "$c" == "x" ]]; then ...; fi
```

### 숫자 비교

```bash
if (( a > b )); then ...; fi
if [[ "$a" -gt "$b" ]]; then ...; fi    # 둘 다 가능
n=$((a + b))
((counter++))
```

### 파일 테스트

| 연산자 | 의미 |
|---|---|
| `-f file` | 일반 파일 존재 |
| `-d dir` | 디렉터리 존재 |
| `-e path` | 경로 존재 (종류 무관) |
| `-r file` | 읽기 가능 |
| `-w file` | 쓰기 가능 |
| `-x file` | 실행 가능 |
| `-L file` | 심볼릭 링크 |
| `-s file` | 크기 > 0 |
| `f1 -nt f2` | f1이 f2보다 새로움 |
| `f1 -ot f2` | f1이 f2보다 오래됨 |

```bash
if [[ -d /var/log/myapp ]]; then
    echo "log dir exists"
fi
```

---

## 5. 루프

```bash
# for
for f in *.log; do
    echo "$f"
done

# 숫자 범위
for i in {1..10}; do echo "$i"; done
for i in $(seq 1 10); do echo "$i"; done
for ((i=0; i<10; i++)); do echo "$i"; done

# while
while read -r line; do
    echo ">$line<"
done < file.txt

# while + 명령
while IFS=',' read -r id name dept; do
    echo "$id: $name in $dept"
done < employees.csv

# until
until ping -c1 google.com &>/dev/null; do sleep 5; done

# break / continue
for i in {1..10}; do
    [[ $i -eq 5 ]] && break
    [[ $((i % 2)) -eq 0 ]] && continue
    echo "$i"
done
```

### ❌ 자주 틀리는 패턴

```bash
# ❌ ls 출력을 파이프로 (공백 있는 파일에서 깨짐)
for f in $(ls *.log); do ...; done

# ✅ glob 직접
for f in *.log; do ...; done

# ✅ 또는 find + -print0
find /var/log -name "*.log" -print0 | while IFS= read -r -d '' f; do
    echo "$f"
done
```

---

## 6. 인자 파싱

### getopts (단문자 옵션)

```bash
#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<EOF
Usage: $0 [-v] [-n COUNT] [-o OUTPUT] FILE...
  -v          verbose
  -n COUNT    count (default 10)
  -o OUTPUT   output dir
EOF
    exit 1
}

verbose=0
count=10
output="."

while getopts ":vn:o:h" opt; do
    case "$opt" in
        v) verbose=1 ;;
        n) count="$OPTARG" ;;
        o) output="$OPTARG" ;;
        h) usage ;;
        \?) echo "Invalid option: -$OPTARG" >&2; usage ;;
        :)  echo "Option -$OPTARG requires an argument" >&2; usage ;;
    esac
done
shift $((OPTIND - 1))

# 위치 인자
[[ $# -lt 1 ]] && usage
files=("$@")

echo "verbose=$verbose, count=$count, output=$output, files=${files[*]}"
```

### 수동 파싱 (장문 옵션 `--name`)

```bash
while [[ $# -gt 0 ]]; do
    case "$1" in
        -v|--verbose) verbose=1; shift ;;
        -n|--count)   count="$2"; shift 2 ;;
        --count=*)    count="${1#*=}"; shift ;;
        -h|--help)    usage ;;
        --) shift; break ;;
        -*) echo "Unknown option: $1" >&2; usage ;;
        *)  files+=("$1"); shift ;;
    esac
done
```

---

## 7. 배열 · 연관배열 · 해시

```bash
# 일반 배열
arr=("a" "b" "c")
echo "${arr[0]}"           # a
echo "${arr[@]}"           # a b c   (모든 요소)
echo "${#arr[@]}"          # 3        (길이)
arr+=("d")                 # 추가
unset 'arr[1]'

# 인덱스
echo "${!arr[@]}"          # 0 2 3 (b가 사라져서)

# 슬라이스
echo "${arr[@]:1:2}"       # b c

# 연관배열 (Bash 4+)
declare -A colors=([red]=#ff0000 [green]=#00ff00)
echo "${colors[red]}"
colors[blue]=#0000ff
for k in "${!colors[@]}"; do
    echo "$k -> ${colors[$k]}"
done

# 명령 출력을 배열로
mapfile -t lines < file.txt
readarray -t lines < <(grep ERROR app.log)
```

### 따옴표 주의 (다시)

```bash
arr=("with space" "another")
# ❌ 토큰 분리됨
for x in ${arr[@]}; do echo "$x"; done

# ✅ 따옴표
for x in "${arr[@]}"; do echo "$x"; done
```

---

## 8. 로깅·드라이런·확인 — 운영 스크립트 패턴

```bash
#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=0
LOG_LEVEL="${LOG_LEVEL:-INFO}"

log()   { echo "[$(date +'%F %T')] [$1] ${*:2}" >&2; }
info()  { log INFO "$@"; }
warn()  { log WARN "$@"; }
error() { log ERROR "$@"; }
debug() { [[ "$LOG_LEVEL" == "DEBUG" ]] && log DEBUG "$@" || true; }

run() {
    info "RUN: $*"
    if [[ "$DRY_RUN" -eq 1 ]]; then
        info "[DRY-RUN] skipped"
        return 0
    fi
    "$@"
}

confirm() {
    local prompt="$1"
    read -r -p "$prompt [y/N]: " reply
    [[ "$reply" =~ ^[Yy]$ ]]
}

# 사용
info "Starting deploy"

if confirm "Continue?"; then
    run rm -rf /opt/myapp/old
    run cp -r dist /opt/myapp/new
fi
```

---

## 9. 멱등성 (idempotency)

같은 스크립트를 N번 실행해도 결과가 같아야 한다.

```bash
# ❌ 두 번 실행하면 두 번 추가됨
echo "alice ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers.d/alice

# ✅ 존재 검사
if ! grep -q "^alice " /etc/sudoers.d/alice 2>/dev/null; then
    echo "alice ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers.d/alice
fi

# 더 안전: 별도 파일에 덮어쓰기
cat > /etc/sudoers.d/alice <<'EOF'
alice ALL=(ALL) NOPASSWD: ALL
EOF
chmod 440 /etc/sudoers.d/alice
```

```bash
# ❌ 디렉터리가 이미 있으면 에러
mkdir /opt/myapp

# ✅
mkdir -p /opt/myapp

# ❌ 심볼릭링크가 이미 있으면 에러
ln -s /opt/myapp-1.2 /opt/myapp-current

# ✅
ln -sfn /opt/myapp-1.2 /opt/myapp-current
```

---

## 10. ShellCheck — 정적분석

```bash
sudo apt install shellcheck
shellcheck deploy.sh
```

샘플:

```bash
#!/bin/bash
files=$(ls *.log)         # SC2046, SC2086, SC2207
for f in $files; do
    cp $f /tmp/           # SC2086 (unquoted)
done
```

ShellCheck 출력:

```
In deploy.sh line 2:
files=$(ls *.log)
       ^----^ SC2046: Quote this to prevent word splitting.
              SC2207: Prefer mapfile or read -a to split command output

In deploy.sh line 4:
    cp $f /tmp/
       ^^ SC2086: Double quote to prevent globbing and word splitting.
```

> ShellCheck는 모든 Bash 스크립트에 돌릴 가치 있음. CI에 포함 권장.

---

## 11. 종합 예제: 배포 스크립트

```bash
#!/usr/bin/env bash
#
# deploy.sh — Spring Boot 앱을 배포하고 헬스체크 후 트래픽 전환
#
set -euo pipefail
IFS=$'\n\t'

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
APP_NAME="myapp"
APP_USER="myapp"
APP_DIR="/opt/$APP_NAME"
JAR_URL=""
DRY_RUN=0
KEEP_RELEASES=5

log() { printf '[%s] [%s] %s\n' "$(date +'%F %T')" "$1" "${*:2}" >&2; }
info()  { log INFO  "$@"; }
warn()  { log WARN  "$@"; }
error() { log ERROR "$@"; }

usage() {
    cat <<EOF
Usage: $0 -u JAR_URL [-d] [-k N]
  -u URL   JAR 파일 다운로드 URL (필수)
  -d       dry-run
  -k N     유지할 릴리스 개수 (기본 5)
EOF
    exit 1
}

while getopts ":u:dk:h" opt; do
    case "$opt" in
        u) JAR_URL="$OPTARG" ;;
        d) DRY_RUN=1 ;;
        k) KEEP_RELEASES="$OPTARG" ;;
        h) usage ;;
        \?) error "Invalid: -$OPTARG"; usage ;;
        :)  error "Missing arg for -$OPTARG"; usage ;;
    esac
done

[[ -z "$JAR_URL" ]] && { error "JAR_URL is required"; usage; }

run() {
    info "RUN: $*"
    [[ "$DRY_RUN" -eq 0 ]] && "$@"
}

TMP_DIR=$(mktemp -d)
cleanup() {
    local rc=$?
    rm -rf "$TMP_DIR"
    [[ $rc -ne 0 ]] && error "Deploy failed with exit $rc"
    return $rc
}
trap cleanup EXIT
trap 'error "Interrupted"; exit 130' INT TERM

# 1) 다운로드 및 검증
RELEASE=$(date -u +%Y%m%d_%H%M%S)
RELEASE_DIR="$APP_DIR/releases/$RELEASE"
info "Deploying release: $RELEASE"

run mkdir -p "$RELEASE_DIR"
run curl -fsSL "$JAR_URL" -o "$TMP_DIR/app.jar"
[[ -s "$TMP_DIR/app.jar" ]] || { error "Empty JAR"; exit 1; }

# 2) 검사 (간단 — manifest 존재 등)
run unzip -p "$TMP_DIR/app.jar" META-INF/MANIFEST.MF | head -5

# 3) 배치
run cp "$TMP_DIR/app.jar" "$RELEASE_DIR/app.jar"
run chown -R "$APP_USER:$APP_USER" "$RELEASE_DIR"

# 4) 심볼릭링크 atomically 교체
run ln -sfn "$RELEASE_DIR" "$APP_DIR/current"

# 5) 서비스 재시작
run systemctl restart "$APP_NAME"
sleep 5

# 6) 헬스체크 (10회 재시도)
for i in {1..10}; do
    if curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
        info "Health check passed"
        break
    fi
    warn "Health check $i/10 failed, retrying..."
    sleep 3
    [[ $i -eq 10 ]] && { error "Health check failed"; exit 1; }
done

# 7) 오래된 릴리스 정리
cd "$APP_DIR/releases"
ls -t | tail -n +"$((KEEP_RELEASES + 1))" | xargs -r rm -rf

info "Deploy complete: $RELEASE"
```

### 이 스크립트가 보여주는 패턴

1. **set -euo pipefail** 안전 헤더
2. **getopts** 인자 파싱
3. **trap EXIT** 정리 보장
4. **dry-run** 지원
5. **로깅 함수**
6. **mktemp** 임시 디렉터리 (예측 가능한 경로 X)
7. **ln -sfn**으로 atomic 심볼릭링크 교체
8. **헬스체크 재시도 루프**
9. **오래된 릴리스 정리**

---

## 12. 실습

### Step 1: 첫 스크립트

위 §11의 deploy.sh를 본인 환경에 맞게 수정하고 dry-run 실행.

### Step 2: ShellCheck 적용

```bash
shellcheck deploy.sh
```

지적된 모든 경고를 수정.

### Step 3: 직접 만들어보기

다음 요구사항으로 `backup-db.sh` 작성:

- 인자: `-d DB_NAME` 필수, `-o OUT_DIR` (기본 `/var/backup/db`)
- pg_dump로 백업 (또는 docker exec를 가정해도 OK)
- 압축 (gzip)
- 30일 지난 백업 삭제
- 실패 시 Slack/이메일 알림 (echo로 대체 가능)
- dry-run 지원
- ShellCheck 통과

---

## 더 읽어볼 자료

- 📘 『Pro Bash Programming』 (Chris F.A. Johnson)
- 📘 『Classic Shell Scripting』 (O'Reilly)
- 🔗 Greg's Bash Wiki: <https://mywiki.wooledge.org/BashGuide>
- 🔗 ShellCheck Wiki: <https://www.shellcheck.net/wiki/>
- 🔗 Defensive BASH Programming: <https://kfirlavi.herokuapp.com/blog/2012/11/14/defensive-bash-programming>

---

## 자가 점검

- [ ] 안전 헤더(`set -euo pipefail`)를 모든 스크립트에 넣는다
- [ ] `trap cleanup EXIT` 패턴을 안다
- [ ] 함수에 `local` 빼먹지 않는다
- [ ] `getopts`로 옵션을 파싱했다
- [ ] 멱등성 있는 스크립트를 작성했다
- [ ] ShellCheck를 돌려서 0 warning 만들었다

다음: [`04_powershell_scripting.md`](04_powershell_scripting.md)
