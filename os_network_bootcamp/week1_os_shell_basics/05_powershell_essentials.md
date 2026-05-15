# Day 5 — PowerShell 핵심 cmdlet · 실행 정책 · 프로파일

## 한 줄 요약

객체 파이프라인을 익혔으면 이제 **실전에서 자주 쓰는 cmdlet 50선**, **실행 정책(Execution Policy)** 의 의미와 위험, **프로파일(`$PROFILE`)** 로 환경을 다듬는 법까지 익혀 PowerShell을 "쓸 수 있는 도구"로 만들자.

## 학습 목표

- [ ] 파일·레지스트리·서비스·이벤트로그 등 PowerShell의 **PSDrive** 추상화를 안다
- [ ] 자주 쓰는 cmdlet 50개를 카테고리별로 파악한다
- [ ] **실행 정책 4종**의 의미와 차이, 우회 방법과 그 위험을 안다
- [ ] `$PROFILE`로 환경을 커스터마이즈한다
- [ ] PSReadLine으로 셸 사용성을 끌어올린다
- [ ] PowerShell의 `Invoke-WebRequest`/`Invoke-RestMethod`로 HTTP를 다룬다 (curl 대체)
- [ ] **secret 관리** 기초 (SecureString, SecretManagement 모듈)

---

## 1. PSDrive — "모든 것이 드라이브"

Linux의 `/proc`이 커널 정보를 파일처럼 보여주듯, PowerShell은 **PSDrive**라는 추상화로 파일·레지스트리·환경변수·함수·변수·인증서 등을 **동일한 cmdlet으로** 접근하게 한다.

```powershell
Get-PSDrive
# Name      Provider    Root
# ----      --------    ----
# C         FileSystem  C:\
# D         FileSystem  D:\
# Cert      Certificate \
# Env       Environment
# HKCU      Registry    HKEY_CURRENT_USER
# HKLM      Registry    HKEY_LOCAL_MACHINE
# Alias     Alias
# Function  Function
# Variable  Variable
```

같은 cmdlet으로 자원 종류를 가리지 않는다:

```powershell
# 파일
Get-ChildItem C:\Users
Get-Item C:\Windows\notepad.exe

# 환경변수
Get-ChildItem env:
Get-Item env:PATH
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# 레지스트리
Get-ChildItem HKLM:\SOFTWARE
Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion' |
    Select-Object ProductName, BuildLab

# 인증서
Get-ChildItem Cert:\LocalMachine\My
```

### Java 비유

- Spring의 `@Value("${spring.datasource.url}")`처럼, **PSDrive는 자원의 통일된 주소 체계**.

---

## 2. 자주 쓰는 cmdlet 50선

### 파일·디렉터리

| cmdlet | 역할 |
|---|---|
| `Get-ChildItem` (gci, ls, dir) | 목록 |
| `Get-Item` (gi) | 단일 항목 |
| `Set-Location` (cd, sl) | 디렉터리 이동 |
| `New-Item` (ni) | 파일/디렉터리 생성 |
| `Copy-Item` (cp, cpi, copy) | 복사 |
| `Move-Item` (mv, mi, move) | 이동 |
| `Remove-Item` (rm, ri, del) | 삭제 |
| `Rename-Item` (ren, rni) | 이름 변경 |
| `Get-Content` (gc, cat, type) | 파일 읽기 |
| `Set-Content` (sc) | 파일 쓰기 (덮어쓰기) |
| `Add-Content` (ac) | 파일 추가 |
| `Out-File` | 출력 → 파일 |
| `Test-Path` | 존재 확인 |
| `Resolve-Path` | 절대 경로 |

```powershell
# 디렉터리 생성
New-Item -Path C:\temp\demo -ItemType Directory -Force

# 파일 생성
New-Item -Path C:\temp\demo\hello.txt -ItemType File -Value "Hello"

# 1GB 빈 파일 만들기 (디스크 테스트)
fsutil file createnew C:\temp\big.dat 1073741824

# 안전한 삭제
if (Test-Path C:\temp\demo) {
    Remove-Item C:\temp\demo -Recurse -Force
}

# 텍스트 한 줄씩 처리
Get-Content C:\logs\app.log | Where-Object { $_ -match "ERROR" }

# 큰 파일은 스트리밍
Get-Content C:\logs\big.log -ReadCount 1000 -Wait    # tail -f 비슷
```

### 검색·필터링

| cmdlet | 역할 |
|---|---|
| `Select-String` (sls) | grep 대체 |
| `Where-Object` (?, where) | 조건 필터 |
| `Select-Object` (select) | 컬럼 선택 |
| `Sort-Object` (sort) | 정렬 |
| `Group-Object` (group) | 그룹화 |
| `Measure-Object` (measure) | 집계 |
| `Compare-Object` (compare, diff) | 차이 비교 |

```powershell
# 디렉터리에서 'TODO' 검색
Select-String -Path C:\src\*.java -Pattern "TODO"

# 정규식
Select-String -Path *.log -Pattern '\d{3}\.\d{3}\.\d{3}\.\d{3}' | Select Line, Filename

# 매치 컨텍스트
Select-String "Exception" *.log -Context 2,3   # 매치 앞 2, 뒤 3줄
```

### 프로세스·서비스

| cmdlet | 역할 |
|---|---|
| `Get-Process` (ps, gps) | 프로세스 목록 |
| `Start-Process` (start) | 프로세스 시작 |
| `Stop-Process` (kill, spps) | 프로세스 종료 |
| `Wait-Process` | 종료까지 대기 |
| `Get-Service` (gsv) | 서비스 목록 |
| `Start-Service` / `Stop-Service` / `Restart-Service` | 서비스 제어 |
| `Set-Service` | 시작 모드 변경 |
| `Get-EventLog` / `Get-WinEvent` | 이벤트 로그 |

```powershell
# 프로세스 시작
Start-Process notepad
Start-Process java -ArgumentList "-jar","app.jar" -WorkingDirectory "C:\app"

# 종료
Stop-Process -Name chrome -Force

# 서비스
Get-Service | Where-Object Status -eq Running
Restart-Service Spooler

# 이벤트로그 (system, application)
Get-WinEvent -LogName System -MaxEvents 10
Get-WinEvent -FilterHashtable @{LogName='Application'; Level=2}  # Error
```

### 네트워크

| cmdlet | 역할 |
|---|---|
| `Test-Connection` | ping 대체 |
| `Test-NetConnection` (tnc) | tcp connect 테스트 |
| `Get-NetIPAddress` | IP 주소 |
| `Get-NetIPConfiguration` | ipconfig 대체 |
| `Get-NetTCPConnection` | netstat 대체 |
| `Get-NetUDPEndpoint` | UDP 리스닝 |
| `Get-NetRoute` | 라우팅 테이블 |
| `Resolve-DnsName` | nslookup 대체 |
| `Invoke-WebRequest` (iwr) | HTTP 요청 (헤더, status 포함) |
| `Invoke-RestMethod` (irm) | REST API (JSON 자동 파싱) |

```powershell
# Week 3에서 자세히. 미리보기만.
Test-NetConnection google.com -Port 443
Resolve-DnsName google.com
Get-NetTCPConnection -State Listen | Sort LocalPort

Invoke-RestMethod https://api.github.com/users/octocat |
    Select-Object name, company, public_repos
```

### 환경·정보

```powershell
Get-Host                   # 호스트 정보
$PSVersionTable            # PowerShell 버전
Get-Culture                # 로케일
Get-TimeZone
[System.Environment]::OSVersion
Get-CimInstance Win32_OperatingSystem | Select Caption, Version, BuildNumber
Get-ComputerInfo           # 종합 (느림)
```

### 자료 변환

| cmdlet | 역할 |
|---|---|
| `ConvertTo-Json` / `ConvertFrom-Json` | JSON ↔ 객체 |
| `ConvertTo-Csv` / `ConvertFrom-Csv` | CSV |
| `ConvertTo-Xml` / `ConvertFrom-Xml` | XML |
| `Import-Csv` / `Export-Csv` | CSV 파일 |
| `Import-Clixml` / `Export-Clixml` | PowerShell 직렬화 (객체 보존) |
| `ConvertTo-Html` | HTML 리포트 |
| `Format-Table` (ft) | 표 |
| `Format-List` (fl) | 세로 리스트 |
| `Out-GridView` (ogv) | 인터랙티브 GUI 표 |

```powershell
# 객체를 CSV로
Get-Process | Select Name, Id, WS | Export-Csv -Path procs.csv -NoTypeInformation

# CSV 읽어 객체로
Import-Csv procs.csv | Sort {[int]$_.WS} -Descending | Select -First 5

# JSON 왕복
@{ name="Alice"; age=30 } | ConvertTo-Json | Set-Content user.json
$user = Get-Content user.json | ConvertFrom-Json
$user.name
```

---

## 3. 실행 정책 (Execution Policy)

### 무엇을 막는가

PowerShell은 무심코 받은 `.ps1` 스크립트가 실행되어 시스템이 망가지는 사고를 줄이려 **서명되지 않은 스크립트의 실행을 기본 차단**한다. 이게 실행 정책.

> ⚠ **착각 금지**: 실행 정책은 **보안 기능이 아니다**. MS 공식 문서가 명시: "Execution Policy는 신뢰 경계, 보호 장벽이 아니다." 결정한 공격자는 우회 가능. **조심성 없는 사용자가 더블클릭으로 .ps1을 실행하는 사고를 줄이는 게 목적**.

### 정책 종류

| 정책 | 설명 |
|---|---|
| `Restricted` | 모든 스크립트 차단. 기본값 (Windows 클라이언트). 대화형 명령만 가능. |
| `AllSigned` | 신뢰된 발급자가 서명한 스크립트만 |
| `RemoteSigned` | 로컬 스크립트는 OK, 인터넷에서 받은 건 서명 필요. **권장** |
| `Unrestricted` | 모두 허용 (인터넷 출처는 경고만) |
| `Bypass` | 아무것도 안 막음, 경고도 없음 |
| `Default` | OS 기본값 사용 |

### 조회·변경

```powershell
# 현재 정책
Get-ExecutionPolicy
Get-ExecutionPolicy -List       # 스코프별 (MachinePolicy, UserPolicy, Process, CurrentUser, LocalMachine)

# 변경 (현재 사용자만)
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

# 일회성 우회 (한 세션만)
powershell -ExecutionPolicy Bypass -File .\script.ps1

# 한 프로세스만 Bypass
pwsh -NoProfile -ExecutionPolicy Bypass -File .\script.ps1
```

### "인터넷에서 다운로드한 스크립트가 안 돌아갈 때"

NTFS의 **Zone.Identifier 대체 스트림**으로 인터넷 출처가 표시됨.

```powershell
# 차단 해제 (인터넷 출처 마킹 제거)
Unblock-File -Path .\downloaded.ps1
```

### ❌ 위험 / ✅ 안전

```powershell
# ❌ 위험: 모든 호스트에서 무조건 Bypass
Set-ExecutionPolicy Bypass -Scope LocalMachine    # 시스템 전체에 영향

# ✅ 권장: 사용자 범위, RemoteSigned
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

---

## 4. 프로파일 ($PROFILE)

Bash의 `.bashrc`에 해당. 셸 시작 시 자동 로드되는 스크립트.

```powershell
# 프로파일 경로 (여러 개)
$PROFILE | Format-List * -Force

# 가장 흔한 것: 현재 사용자, 현재 호스트
$PROFILE
# C:\Users\<you>\Documents\PowerShell\Microsoft.PowerShell_profile.ps1     (PS 7)
# C:\Users\<you>\Documents\WindowsPowerShell\Microsoft.PowerShell_profile.ps1  (PS 5.1)

# 없으면 만들기
if (!(Test-Path $PROFILE)) {
    New-Item -Path $PROFILE -ItemType File -Force
}
notepad $PROFILE
```

### 권장 프로파일 예시

```powershell
# ~/Documents/PowerShell/Microsoft.PowerShell_profile.ps1

# 별칭 / 함수
function ll { Get-ChildItem -Force @args }
function .. { Set-Location .. }
function gs { git status @args }
function gl { git log --oneline -20 @args }

# 환경변수
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# PSReadLine — 셸 사용성 핵심
Import-Module PSReadLine
Set-PSReadLineOption -EditMode Emacs                # 또는 Vi
Set-PSReadLineOption -PredictionSource HistoryAndPlugin
Set-PSReadLineOption -PredictionViewStyle ListView
Set-PSReadLineKeyHandler -Key Tab -Function MenuComplete

# 색상
$PSStyle.FileInfo.Directory = "`e[38;5;39m"

# Git posh-git (선택)
if (Get-Module -ListAvailable posh-git) {
    Import-Module posh-git
}

# 시작 메시지
Write-Host "$(Get-Date -Format 'yyyy-MM-dd HH:mm')  PowerShell $($PSVersionTable.PSVersion)" -ForegroundColor Cyan
```

---

## 5. HTTP 다루기 — Invoke-WebRequest / Invoke-RestMethod

| cmdlet | 차이 |
|---|---|
| `Invoke-WebRequest` (iwr) | **HTTP 응답 객체 전체**: 헤더, 상태코드, 본문. HTML 파싱 가능 |
| `Invoke-RestMethod` (irm) | 응답이 JSON/XML이면 **자동으로 PS 객체로 파싱**. 본문만 |

```powershell
# 헤더와 상태코드까지
$r = Invoke-WebRequest https://example.com
$r.StatusCode
$r.Headers['Content-Type']
$r.Content[0..200] -join ''       # 처음 200자

# JSON API
$repos = Invoke-RestMethod 'https://api.github.com/users/octocat/repos'
$repos | Select name, language, stargazers_count | Sort stargazers_count -Desc

# POST
$body = @{ title = "Hello"; body = "world" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri https://httpbin.org/post `
    -ContentType 'application/json' -Body $body

# 인증 헤더
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Uri 'https://api.example.com/me' -Headers $headers

# 다운로드
Invoke-WebRequest -Uri 'https://example.com/file.zip' -OutFile 'C:\temp\file.zip'

# 실제 curl을 쓰고 싶으면 .exe 명시 (PS는 curl을 iwr 별칭으로 가림)
curl.exe -sS https://example.com
```

> **PS 5.1 함정**: `Invoke-WebRequest`가 IE 엔진을 사용하여 첫 실행 시 매우 느림. `-UseBasicParsing` 옵션을 추가하거나, 가능하면 PS 7로 업그레이드. PS 7은 HttpClient 사용.

### TLS 1.2 강제 (PS 5.1에서 가끔 필요)

```powershell
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
```

---

## 6. 비밀번호와 시크릿 다루기

### SecureString

```powershell
# 프롬프트로 입력 (메모리에 평문이 안 남음)
$pwd = Read-Host "Password" -AsSecureString

# 사용
$cred = New-Object System.Management.Automation.PSCredential('alice', $pwd)
Invoke-RestMethod -Uri https://api.example.com -Authentication Basic -Credential $cred
```

### SecretManagement (권장)

```powershell
Install-Module Microsoft.PowerShell.SecretManagement, Microsoft.PowerShell.SecretStore
Register-SecretVault -Name LocalStore -ModuleName Microsoft.PowerShell.SecretStore -DefaultVault

Set-Secret -Name MyApiKey -Secret "super-secret-token"
$key = Get-Secret -Name MyApiKey -AsPlainText
```

### ❌ 위험 / ✅ 안전

```powershell
# ❌ 평문 패스워드 하드코딩
$password = "P@ssw0rd!"
Invoke-RestMethod -Uri ... -Headers @{ Authorization = "Bearer $password" }

# ✅ SecretStore 또는 환경변수
$key = Get-Secret -Name MyApiKey -AsPlainText
# 또는
$key = $env:MY_API_KEY
```

---

## 7. 스크립트 실행과 .ps1

```powershell
# .ps1 만들기
@'
param([string]$Name = "World")
Write-Host "Hello, $Name!"
'@ | Set-Content hello.ps1

# 실행 (현재 디렉터리는 PATH에 없으므로 .\ 필수)
.\hello.ps1                # 실행 정책 RemoteSigned면 OK
.\hello.ps1 -Name "Alice"

# 다른 디렉터리에서
& 'C:\scripts\hello.ps1' -Name "Bob"

# 도트 소싱 (현재 스코프에서 실행 — 변수·함수 캡처)
. .\hello.ps1
```

### 스크립트 헤더 권장

```powershell
<#
.SYNOPSIS
    파일 정리 스크립트
.DESCRIPTION
    7일 이상 된 .log를 archive로 이동
.EXAMPLE
    .\cleanup.ps1 -Path C:\logs -DaysOld 7
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Path,
    [int]$DaysOld = 7,
    [switch]$WhatIf
)

Set-StrictMode -Version Latest      # 정의 안 된 변수 사용 시 에러
$ErrorActionPreference = 'Stop'     # 어떤 cmdlet 실패든 즉시 종료

# ... 본문
```

`Set-StrictMode -Version Latest` + `$ErrorActionPreference = 'Stop'` 가 Bash의 `set -euo pipefail`에 해당.

---

## 8. 자주 쓰는 실전 한 줄

```powershell
# 가장 많이 메모리 쓰는 5개 프로세스
Get-Process | Sort WS -Desc | Select -First 5 Name, Id, @{N='MB';E={[int]($_.WS/1MB)}}

# 마지막 100건의 system error 이벤트
Get-WinEvent -LogName System -MaxEvents 100 |
    Where-Object LevelDisplayName -eq 'Error' |
    Select TimeCreated, Id, Message |
    Format-Table -Wrap

# 디스크 가장 큰 폴더 톱 10 (사용자 폴더)
Get-ChildItem $HOME -Directory |
    ForEach-Object {
        $size = (Get-ChildItem $_.FullName -Recurse -File -ErrorAction SilentlyContinue |
                 Measure-Object Length -Sum).Sum
        [PSCustomObject]@{
            Path = $_.FullName
            SizeMB = [math]::Round($size/1MB, 1)
        }
    } | Sort SizeMB -Desc | Select -First 10

# 특정 포트를 점유한 프로세스
Get-NetTCPConnection -LocalPort 8080 |
    Select LocalPort, OwningProcess,
        @{N='Name';E={(Get-Process -Id $_.OwningProcess).Name}}

# 모든 USB 장치
Get-PnpDevice -Class USB | Select Status, FriendlyName

# 최근 7일 안 잠긴 사용자 (도메인 환경)
Get-LocalUser | Where { $_.Enabled -and $_.LastLogon -lt (Get-Date).AddDays(-7) }
```

---

## 9. 실습 (Hands-on)

### Step 1: 실행 정책 확인 후 변경

```powershell
Get-ExecutionPolicy -List
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

### Step 2: 프로파일 만들기

```powershell
if (!(Test-Path $PROFILE)) { New-Item -Path $PROFILE -ItemType File -Force }
notepad $PROFILE
```

위 §4 예시를 붙여넣고 새 PowerShell 창에서 별칭이 동작하는지 확인.

### Step 3: 첫 스크립트

```powershell
@'
[CmdletBinding()]
param([string]$LogDir = "$env:TEMP\logs", [int]$DaysOld = 7)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (!(Test-Path $LogDir)) {
    New-Item -Path $LogDir -ItemType Directory -Force | Out-Null
    "Created $LogDir"
}

$cutoff = (Get-Date).AddDays(-$DaysOld)
$old = Get-ChildItem $LogDir -File | Where-Object LastWriteTime -lt $cutoff
$old | ForEach-Object {
    Write-Verbose "Removing $($_.FullName)"
    Remove-Item $_.FullName -Verbose
}
"Removed $($old.Count) files"
'@ | Set-Content $HOME\cleanup.ps1

.\cleanup.ps1 -LogDir C:\temp\logs -DaysOld 7 -Verbose
```

### Step 4: GitHub API 사용

```powershell
$repos = Invoke-RestMethod 'https://api.github.com/users/microsoft/repos?per_page=100'
$repos |
    Select-Object name, language, stargazers_count |
    Sort-Object stargazers_count -Descending |
    Select-Object -First 10 |
    Format-Table
```

### Step 5: 시크릿 저장

```powershell
Install-Module Microsoft.PowerShell.SecretManagement, Microsoft.PowerShell.SecretStore -Scope CurrentUser
Register-SecretVault -Name LocalStore -ModuleName Microsoft.PowerShell.SecretStore -DefaultVault
Set-Secret -Name TestSecret -Secret "hello"
Get-Secret -Name TestSecret -AsPlainText
```

---

## 더 읽어볼 자료

- 📘 『Learn PowerShell Scripting in a Month of Lunches』 (Don Jones)
- 📘 『PowerShell 101』 (Mike F. Robbins, 무료 ebook): <https://leanpub.com/powershell101>
- 🔗 `about_Execution_Policies`: `Get-Help about_Execution_Policies`
- 🔗 PSReadLine 문서: <https://learn.microsoft.com/powershell/module/psreadline>
- 🔗 SecretManagement: <https://learn.microsoft.com/powershell/utility-modules/secretmanagement/overview>
- 🎓 Microsoft Learn — Automate administrative tasks: <https://learn.microsoft.com/training/paths/powershell/>

---

## 자가 점검

- [ ] `$PROFILE`이 어디에 있고 무엇을 하는지 안다?
- [ ] 실행 정책 4종을 보안 관점에서 비교할 수 있다?
- [ ] `Invoke-WebRequest`와 `Invoke-RestMethod`의 차이가 무엇인지?
- [ ] PSReadLine으로 `Ctrl+R` 검색을 설정했는가?
- [ ] `Set-StrictMode` + `$ErrorActionPreference='Stop'`을 모든 스크립트 헤더에 넣을 준비가 됐는가?

이번 주 마무리: [`labs/lab1_wsl_setup.md`](labs/lab1_wsl_setup.md) → [`labs/lab2_shell_warmup.md`](labs/lab2_shell_warmup.md) → [`checklist.md`](checklist.md)
