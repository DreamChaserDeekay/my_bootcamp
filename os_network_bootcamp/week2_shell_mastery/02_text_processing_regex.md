# Day 2 — 정규식 · grep · sed · awk · jq

## 한 줄 요약

운영 일과의 절반은 **텍스트 변환**이다. 정규식과 `grep`/`sed`/`awk`/`jq` 네 개 도구만 익히면, 로그 파싱·CSV 정제·JSON 가공·일괄 치환을 모두 한 줄에 담을 수 있다.

## 학습 목표

- [ ] 기본 정규식(BRE)과 확장 정규식(ERE), PCRE의 차이를 안다
- [ ] `grep`의 핵심 옵션(`-E`, `-P`, `-v`, `-A/B/C`, `-c`, `-l`)을 안다
- [ ] `sed`로 치환·삭제·삽입을 자유롭게 한다
- [ ] `awk`의 패턴-액션 모델, 내장 변수 (`NR`, `NF`, `$0`, `$1`...), `BEGIN`/`END`를 안다
- [ ] `jq`로 JSON을 필터·변형한다
- [ ] PowerShell의 `Select-String`, `-replace`, `-match`, `ConvertFrom-Json`로 같은 작업을 한다

---

## 1. 정규식 기초

### 정규식 종류

| 종류 | 도구 | 특징 |
|---|---|---|
| **BRE** (Basic) | `grep`, `sed` (기본) | `()`, `{}`, `+`, `?`, `\|` 모두 `\` 필요 |
| **ERE** (Extended) | `grep -E`, `sed -E`, `egrep`, `awk` | 위 메타문자 그대로 |
| **PCRE** | `grep -P`, ripgrep, Java/JS/Python | look-ahead, named group 등 강력 |

> Python, Java, JavaScript, ripgrep(`rg`), PowerShell 정규식은 모두 PCRE 계열. 학습 효과가 가장 큰 것은 PCRE.

### 메타문자

| 패턴 | 의미 |
|---|---|
| `.` | 임의의 1자 (개행 제외, 옵션으로 포함 가능) |
| `^` | 행 시작 |
| `$` | 행 끝 |
| `\b` | 단어 경계 |
| `[abc]` | a, b, c 중 하나 |
| `[^abc]` | a, b, c 아닌 것 |
| `[a-z]` | 범위 |
| `*` | 0회 이상 |
| `+` | 1회 이상 |
| `?` | 0회 또는 1회 |
| `{n}` | 정확히 n회 |
| `{n,m}` | n~m회 |
| `\|` | OR |
| `()` | 그룹 + 캡처 |
| `(?:...)` | 그룹 (캡처 안 함) |
| `(?P<name>...)` (Python) / `(?<name>...)` (PCRE) | 명명 캡처 |
| `\d` | 숫자 (PCRE) |
| `\s` | 공백 (PCRE) |
| `\w` | 단어 문자 (PCRE) |

### 자주 쓰는 패턴

```regex
# IPv4 (대략)
\b(\d{1,3}\.){3}\d{1,3}\b

# 더 엄밀한 IPv4 (각 옥텟 0~255)
\b((25[0-5]|2[0-4]\d|[01]?\d?\d)\.){3}(25[0-5]|2[0-4]\d|[01]?\d?\d)\b

# 이메일 (단순화)
\b[\w.+-]+@[\w-]+\.[\w.-]+\b

# 한국 휴대폰
\b01[016789]-?\d{3,4}-?\d{4}\b

# ISO 8601 날짜
\b\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:?\d{2})?\b

# UUID
\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b
```

> **연습 도구**: <https://regex101.com/> (PCRE flavor, 강력 추천)

---

## 2. grep — 찾기

### 옵션 정리

| 옵션 | 의미 |
|---|---|
| `-i` | 대소문자 무시 |
| `-v` | 매치 안 되는 줄만 |
| `-n` | 줄번호 표시 |
| `-c` | 매치된 줄 수만 |
| `-l` | 매치된 파일명만 |
| `-L` | 매치 안 된 파일명만 |
| `-r`, `-R` | 재귀 |
| `-E` | 확장 정규식 |
| `-P` | PCRE (Perl 호환) |
| `-F` | 고정 문자열 (정규식 아님, 빠름) |
| `-w` | 단어 경계 |
| `-A 3` | 매치 뒤 3줄 |
| `-B 1` | 매치 앞 1줄 |
| `-C 2` | 앞뒤 2줄 |
| `-o` | 매치된 부분만 출력 |
| `--include="*.java"` | 패턴 매치 파일만 |
| `--exclude-dir=node_modules` | 디렉터리 제외 |

### 자주 쓰는 패턴

```bash
# 단순 검색
grep ERROR app.log

# 대소문자 무시 + 줄번호
grep -in "exception" app.log

# 정규식
grep -E '\b50[0-9]\b' access.log              # 500번대 HTTP 상태코드
grep -P '(?<=user=)\w+' app.log               # 'user=' 뒤의 사용자명만 (PCRE lookbehind)

# 재귀 검색
grep -rn "TODO" src/ --include="*.java"

# 매치 안 되는 것만 (Java import 없는 줄들)
grep -v '^import' MyClass.java

# 컨텍스트 (디버깅에서 매우 유용)
grep -B 2 -A 5 "OutOfMemoryError" app.log

# 카운트만
grep -c "ERROR" app.log                       # 에러 줄 수
grep -rc "TODO" src/ --include="*.java"       # 파일별 카운트

# 매치 부분만 추출
grep -oE '\b([0-9]{1,3}\.){3}[0-9]{1,3}\b' access.log | sort -u
```

### ripgrep (`rg`) — grep의 현대적 대체

```bash
# 거의 모든 면에서 grep보다 빠름, .gitignore 자동 적용
rg "ERROR" .
rg -t java "TODO"          # Java 파일만
rg -e "pat1" -e "pat2"     # 여러 패턴
rg --hidden                # 숨김 파일 포함
rg --no-ignore             # .gitignore 무시
```

설치: `apt install ripgrep` 또는 `winget install BurntSushi.ripgrep.MSVC`.

---

## 3. sed — 스트림 편집기

### 기본 구조

```
sed [옵션] '주소{명령}' file
```

- 주소: 줄번호 (`5`), 범위 (`5,10`), 정규식 (`/pattern/`), 마지막 (`$`)
- 명령: `s/A/B/` 치환, `d` 삭제, `p` 출력, `i\` 삽입, `a\` 추가

### 자주 쓰는 패턴

```bash
# 치환 (가장 흔함)
sed 's/foo/bar/' file.txt          # 각 줄 첫 번째만
sed 's/foo/bar/g' file.txt         # 모두
sed -i 's/foo/bar/g' file.txt      # in-place (파일 직접 수정)
sed -i.bak 's/foo/bar/g' file.txt  # 백업 .bak 만들고 수정

# 확장 정규식
sed -E 's/[0-9]+/<NUM>/g' file.txt

# 캡처 그룹
sed -E 's/^([^:]+):.*/\1/' /etc/passwd     # 사용자명만

# 줄 삭제
sed '5d' file.txt                  # 5번째 줄 삭제
sed '/^#/d' config.conf            # # 시작 줄 삭제 (주석 제거)
sed '/^$/d' file.txt               # 빈 줄 삭제
sed '5,10d' file.txt               # 5~10번 줄

# 특정 줄만 출력
sed -n '5,10p' file.txt
sed -n '/ERROR/p' app.log

# 텍스트 추가
sed '5a\새 줄' file.txt            # 5번째 줄 뒤에 추가
sed '1i\헤더' file.txt             # 1번째 줄 앞에 삽입

# 여러 명령
sed -e 's/foo/bar/' -e 's/baz/qux/' file
sed '
    s/foo/bar/
    s/baz/qux/
' file
```

### 실전 예

```bash
# Spring 설정에서 패스워드를 ***로 마스킹
sed -E 's/(password[[:space:]]*[:=][[:space:]]*).*/\1***/' application.properties

# CSV에서 줄번호 추가
sed = file.csv | sed 'N;s/\n/,/'

# Windows 줄바꿈 → Unix
sed -i 's/\r$//' file.txt
# 또는
dos2unix file.txt
```

### ❌ 위험 / ✅ 안전

```bash
# ❌ 백업 없이 in-place
sed -i 's/important/wrong/g' production.conf    # 되돌리기 어려움

# ✅ 백업 만들기
sed -i.bak 's/important/wrong/g' production.conf
diff production.conf.bak production.conf
```

---

## 4. awk — 표 형식 데이터의 왕

### 모델

```
awk 'PATTERN { ACTION }' file
```

각 줄을 자동으로 필드(`$1`, `$2`, ...)로 분리하고, 패턴에 매치되면 액션 실행.

### 내장 변수

| 변수 | 의미 |
|---|---|
| `$0` | 전체 줄 |
| `$1`, `$2`, ... | n번째 필드 |
| `NF` | 현재 줄의 필드 수 (`$NF`는 마지막 필드) |
| `NR` | 현재까지 처리한 줄 번호 |
| `FS` | 입력 필드 구분자 (기본 공백/탭) |
| `OFS` | 출력 필드 구분자 |
| `RS` | 입력 레코드 구분자 (기본 `\n`) |
| `ORS` | 출력 레코드 구분자 |
| `FILENAME` | 현재 파일 이름 |

### 자주 쓰는 패턴

```bash
# 필드 출력
awk '{print $1}' file
awk '{print $1, $3}' file
awk '{print $NF}' file              # 마지막 필드

# 필드 구분자
awk -F: '{print $1}' /etc/passwd    # ':'로 구분
awk -F, '{print $2}' data.csv

# 조건
awk '$3 > 100' file
awk '$1 ~ /^ERROR/' app.log
awk '/^ERROR/ {print $0}' app.log
awk 'NR > 1' file.csv               # 헤더 제외

# 출력 형식
awk -F, '{printf "%-15s %5d\n", $1, $2}' data.csv

# 집계 (가장 강력한 기능)
awk '{sum += $1} END {print sum}' numbers.txt
awk -F, 'NR>1 {sum[$3] += $4} END {for (k in sum) print k, sum[k]}' employees.csv

# 평균
awk '{sum += $1; n++} END {if (n) print sum/n}'

# BEGIN/END
awk 'BEGIN {print "Start"; FS=","} {print $1} END {print "End"}'
```

### 실전 예

```bash
# nginx access.log 분석
# 톱 10 IP
awk '{print $1}' access.log | sort | uniq -c | sort -rn | head

# 메서드별 카운트
awk '{print $6}' access.log | sed 's/"//' | sort | uniq -c | sort -rn

# 응답시간 평균 (가정: 마지막 컬럼)
awk '{sum += $NF; n++} END {print "avg:", sum/n}' access.log

# 5xx 에러만 필터
awk '$9 ~ /^5/' access.log

# 두 컬럼 합계
awk -F, 'NR>1 {sum += $4} END {print "total salary:", sum}' employees.csv

# 중복 행 제거 (sort | uniq 보다 빠를 수 있음)
awk '!seen[$0]++' file
```

### 다중 파일 처리

```bash
# 헤더 한 번만, 나머지 데이터만
awk 'NR==1 || FNR>1' *.csv > combined.csv

# FNR: 각 파일별 줄번호
# NR:  전체 누적 줄번호
```

---

## 5. jq — JSON의 grep + awk + sed

### 설치

```bash
sudo apt install jq            # Linux
winget install jqlang.jq       # Windows
brew install jq                # macOS
```

### 기본 사용

```bash
# 정렬 출력 (pretty print)
echo '{"name":"Alice","age":30}' | jq .

# 필드 추출
echo '{"name":"Alice","age":30}' | jq '.name'
# "Alice"

# 따옴표 제거
echo '{"name":"Alice"}' | jq -r '.name'
# Alice

# 배열 인덱싱
echo '[1,2,3]' | jq '.[0]'
echo '[1,2,3]' | jq '.[-1]'        # 마지막

# 모든 요소 순회
echo '[{"id":1},{"id":2}]' | jq '.[].id'
# 1
# 2

# 객체 재구성
curl -s https://api.github.com/users/octocat | jq '{user: .login, repos: .public_repos}'

# 필터링 (배열에서)
cat data.json | jq '.users[] | select(.age > 18)'

# map / reduce
cat data.json | jq '[.users[] | .salary] | add'    # 합계
cat data.json | jq '[.users[] | .salary] | add / length'    # 평균

# 정렬 / 그룹
cat data.json | jq '[.users[] | {name, age}] | sort_by(.age) | reverse'
cat data.json | jq 'group_by(.department) | map({dept: .[0].department, count: length})'

# CSV 변환
cat users.json | jq -r '.users[] | [.name, .age, .email] | @csv'

# 키 존재 확인 / 기본값
cat data.json | jq '.user.address // "unknown"'

# 환경변수 주입
cat data.json | jq --arg name "alice" '.users[] | select(.name == $name)'
```

### 실전 예

```bash
# kubectl 출력에서 Pod 이름과 상태만
kubectl get pods -o json | jq -r '.items[] | "\(.metadata.name)\t\(.status.phase)"'

# AWS CLI 출력에서 인스턴스 ID
aws ec2 describe-instances | jq -r '.Reservations[].Instances[] | .InstanceId'

# Spring Actuator 메트릭에서 특정 값
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq '.measurements[0].value'

# 큰 JSON 파일에서 톱 10
jq '.[] | {name: .name, score: .score}' data.json |
    jq -s 'sort_by(.score) | reverse | .[0:10]'
```

---

## 6. PowerShell에서 같은 작업

| Linux | PowerShell |
|---|---|
| `grep "ERROR" app.log` | `Select-String "ERROR" app.log` |
| `grep -v "DEBUG" app.log` | `Get-Content app.log \| Where-Object { $_ -notmatch "DEBUG" }` |
| `grep -c "ERROR"` | `(Select-String "ERROR" app.log).Count` |
| `sed 's/foo/bar/g' f` | `(Get-Content f) -replace 'foo','bar' \| Set-Content f` |
| `awk '{print $1}' f` | `Get-Content f \| ForEach-Object { ($_ -split ' ')[0] }` |
| `awk -F,` | `Import-Csv` (헤더 있을 때) 또는 `-split ','` |
| `cat j.json \| jq '.x'` | `Get-Content j.json \| ConvertFrom-Json \| Select -Expand x` |

### PowerShell 정규식 연산자

```powershell
"Hello World" -match "W(\w+)"    # $true, $Matches[0]/[1] 채워짐
$Matches[1]                       # "orld"

"Hello World" -replace "World","PowerShell"
"Hello World" -replace "(\w+) (\w+)", '$2 $1'

# 모든 매치
$text = "ip 192.168.1.1 and 10.0.0.1"
[regex]::Matches($text, '\b\d+\.\d+\.\d+\.\d+\b') | ForEach-Object { $_.Value }

# 컬렉션에서 매치 행만
Get-Content app.log | Where-Object { $_ -match '\bERROR\b' }
```

### PowerShell JSON 처리

```powershell
# 파싱
$data = Get-Content data.json | ConvertFrom-Json

# 접근
$data.users[0].name
$data.users | Where-Object age -gt 18

# 변형
$data.users |
    Where-Object age -gt 18 |
    Select-Object name, email |
    ConvertTo-Json -Depth 5

# 깊은 키 안전 접근 (PS 7+)
$data?.user?.address ?? "unknown"
```

---

## 7. ❌ 위험 / ✅ 안전 — 운영 함정

### 사례 1: 정규식의 그리디 매칭

```bash
# ❌ 욕심쟁이 — 의도하지 않은 매치
echo "<b>hello</b> <b>world</b>" | sed -E 's/<b>.*<\/b>/X/'
# 결과: X        ← 전체가 하나로 매치됨

# ✅ 비욕심쟁이 (PCRE) 또는 부정문자집합
echo "<b>hello</b> <b>world</b>" | grep -oP '<b>.*?</b>'
echo "<b>hello</b> <b>world</b>" | sed -E 's/<b>[^<]*<\/b>/X/g'
```

### 사례 2: sed -i 백업 없이

```bash
# ❌
sed -i 's/important_value/wrong/g' /etc/nginx/nginx.conf

# ✅
sudo cp /etc/nginx/nginx.conf /etc/nginx/nginx.conf.bak
sudo sed -i 's/important_value/wrong/g' /etc/nginx/nginx.conf
sudo nginx -t                       # 적용 전 검증
sudo nginx -s reload
```

### 사례 3: awk가 숫자가 아닌 걸 더함

```bash
# CSV에 빈 칸이 있으면 0으로 처리됨
awk -F, '{sum += $3} END {print sum}' messy.csv
# 잘못된 결과 가능

# ✅ 명시적 검증
awk -F, '$3 ~ /^[0-9.]+$/ {sum += $3} END {print sum}' messy.csv
```

---

## 8. 실습 (Hands-on)

### Step 1: nginx 로그 분석 종합

```bash
cat > /tmp/access.log <<'EOF'
192.168.1.10 - - [05/May/2026:10:00:01 +0900] "GET / HTTP/1.1" 200 1024 "-" "Mozilla/5.0"
192.168.1.20 - - [05/May/2026:10:00:02 +0900] "POST /api/login HTTP/1.1" 401 512 "-" "curl/7.0"
192.168.1.10 - - [05/May/2026:10:00:03 +0900] "GET /products HTTP/1.1" 200 4096 "-" "Mozilla/5.0"
192.168.1.30 - - [05/May/2026:10:00:04 +0900] "GET / HTTP/1.1" 500 256 "-" "Mozilla/5.0"
192.168.1.10 - - [05/May/2026:10:00:05 +0900] "GET /products HTTP/1.1" 200 4096 "-" "Mozilla/5.0"
192.168.1.20 - - [05/May/2026:10:00:06 +0900] "POST /api/login HTTP/1.1" 200 128 "-" "curl/7.0"
192.168.1.40 - - [05/May/2026:10:00:07 +0900] "GET /admin HTTP/1.1" 403 256 "-" "scanner/1.0"
EOF

# 1) 톱 IP
awk '{print $1}' /tmp/access.log | sort | uniq -c | sort -rn

# 2) 응답코드 분포
awk '{print $9}' /tmp/access.log | sort | uniq -c | sort -rn

# 3) 4xx/5xx만
awk '$9 ~ /^[45]/ {print}' /tmp/access.log

# 4) User-Agent별
awk -F'"' '{print $6}' /tmp/access.log | sort | uniq -c | sort -rn

# 5) IP별 4xx 횟수 (스캐너 의심)
awk '$9 ~ /^4/ {print $1}' /tmp/access.log | sort | uniq -c | sort -rn
```

### Step 2: JSON 변환

```bash
# 위 로그를 JSON으로
awk '{
    printf "{\"ip\":\"%s\",\"status\":%s,\"path\":\"%s\"}\n", $1, $9, $7
}' /tmp/access.log | jq -s '.'
```

### Step 3: sed로 conf 마스킹

```bash
cat > /tmp/app.properties <<'EOF'
spring.datasource.url=jdbc:postgresql://db.example.com:5432/mydb
spring.datasource.username=admin
spring.datasource.password=SuperSecret123!
api.key=abcd1234efgh5678
server.port=8080
EOF

# 패스워드와 API 키 마스킹
sed -E 's/(password|key)[[:space:]]*=.*/\1=***/i' /tmp/app.properties
```

### Step 4: PowerShell로 같은 분석

```powershell
$log = Get-Content '/tmp/access.log'  # WSL에서 가져오기 또는 /mnt 경유

# 톱 IP
$log | ForEach-Object { ($_ -split ' ')[0] } |
    Group-Object | Sort Count -Desc |
    Select Count, Name

# 4xx/5xx
$log | Where-Object { $_ -match '" [45]\d\d ' }
```

---

## 더 읽어볼 자료

- 📘 『sed & awk』 (O'Reilly, Dale Dougherty)
- 📘 『Mastering Regular Expressions』 (Jeffrey Friedl)
- 🔗 regex101: <https://regex101.com>
- 🔗 awk one-liners: <https://catonmat.net/awk-one-liners-explained>
- 🔗 jq playground: <https://jqplay.org/>
- 🔗 ripgrep User Guide: <https://github.com/BurntSushi/ripgrep/blob/master/GUIDE.md>

---

## 자가 점검

- [ ] BRE와 ERE, PCRE의 차이를 안다
- [ ] `grep -P 'lookahead'` 같은 PCRE 기능을 활용했다
- [ ] `sed -i.bak`으로 안전한 in-place 편집을 한다
- [ ] `awk -F, '$3>100 {sum+=$4} END {print sum}'` 같은 한 줄을 즉시 쓴다
- [ ] `jq 'map(select(.status >= 400))'`을 자유롭게 쓴다
- [ ] PowerShell의 `-match`, `-replace`, `ConvertFrom-Json`을 같이 익혔다

다음: [`03_bash_scripting.md`](03_bash_scripting.md)
