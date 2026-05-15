# Day 4 — PowerShell 객체 파이프라인

## 한 줄 요약

Bash가 **텍스트 스트림**을 흘려보내는 데 비해, PowerShell은 **.NET 객체**를 흘려보낸다. `grep`/`awk`/`sed` 없이도 속성과 메서드로 직접 다룰 수 있고, 객체의 타입을 모르더라도 `Get-Member`로 들여다보는 순간 무엇이 되든 자유롭게 변형·필터링·정렬할 수 있다. 이 차이를 **체화**하는 것이 PowerShell 학습의 출발이다.

## 학습 목표

- [ ] PowerShell의 모든 출력은 **객체(object)** 라는 점을 이해한다
- [ ] cmdlet의 **동사-명사(Verb-Noun)** 명명 규약을 안다 (`Get-`, `Set-`, `New-`, `Remove-`, ...)
- [ ] `Get-Member`, `Get-Help`, `Get-Command` 세 가지 "탐험 도구"를 자유롭게 쓴다
- [ ] `Where-Object`, `Select-Object`, `Sort-Object`, `Group-Object`, `Measure-Object`, `ForEach-Object`를 자유롭게 조합한다
- [ ] PowerShell 5.1과 7.x의 주요 차이를 안다
- [ ] **Windows PowerShell (`powershell.exe`)** vs **PowerShell Core (`pwsh.exe`)** 를 구분한다

---

## 1. 가장 큰 차이 — 텍스트 vs 객체

### Bash

```bash
$ ps -e -o pid,comm | head -3 | awk '$1 > 100 {print $2}'
# 1) ps가 텍스트를 출력
# 2) head가 텍스트를 자르고
# 3) awk가 텍스트를 다시 파싱해서 1열을 숫자로 비교
```

여러 단계에서 **텍스트를 다시 파싱**해야 한다. 컬럼 위치가 바뀌면 깨진다.

### PowerShell

```powershell
PS> Get-Process | Where-Object Id -gt 100 | Select-Object Name
# 1) Get-Process가 객체 시퀀스를 반환
# 2) Where-Object가 객체의 Id 속성으로 필터
# 3) Select-Object가 Name 속성을 뽑음 — 파싱 불필요
```

객체이므로 `$_.Id`, `$_.Name`, `$_.WorkingSet` 등 속성에 직접 접근. **포맷이 바뀌어도 깨지지 않음.**

### 즉시 체감 — 출력의 정체 확인

```powershell
$result = Get-Process
$result.GetType().FullName
# System.Object[]    ← 객체 배열

$result[0].GetType().FullName
# System.Diagnostics.Process    ← .NET의 Process 클래스

$result[0] | Get-Member
# 어떤 속성/메서드가 있는지 전부 보임
```

> **체감 포인트**: Bash에서 `ps`의 출력을 보고 5번째 컬럼이 RSS라는 걸 "알아야" 했다면, PowerShell에서는 `WorkingSet` 속성이라고 이름이 붙어있다.

---

## 2. cmdlet 명명 규약

```
동사-명사 (Verb-Noun)
```

| 동사 | 의미 | 예 |
|---|---|---|
| `Get-` | 조회 | `Get-Process`, `Get-Service`, `Get-ChildItem` |
| `Set-` | 변경 | `Set-Location`, `Set-Content`, `Set-ItemProperty` |
| `New-` | 생성 | `New-Item`, `New-Object`, `New-PSSession` |
| `Remove-` | 삭제 | `Remove-Item`, `Remove-Module` |
| `Start-` / `Stop-` | 시작/종료 | `Start-Service`, `Stop-Process` |
| `Test-` | 검증 | `Test-Path`, `Test-NetConnection` |
| `Invoke-` | 실행 | `Invoke-WebRequest`, `Invoke-Command`, `Invoke-RestMethod` |
| `Out-` | 출력 | `Out-Host`, `Out-File`, `Out-Null` |

승인된 동사 전체: `Get-Verb`

### Bash 명령어와의 대응

| 작업 | Bash | PowerShell | 별칭(주의) |
|---|---|---|---|
| 디렉터리 보기 | `ls` | `Get-ChildItem` | `ls`, `dir`, `gci` |
| 디렉터리 이동 | `cd` | `Set-Location` | `cd`, `sl` |
| 현재 경로 | `pwd` | `Get-Location` | `pwd`, `gl` |
| 파일 내용 | `cat` | `Get-Content` | `cat`, `type`, `gc` |
| 복사 | `cp` | `Copy-Item` | `cp`, `copy`, `cpi` |
| 이동 | `mv` | `Move-Item` | `mv`, `move`, `mi` |
| 삭제 | `rm` | `Remove-Item` | `rm`, `del`, `ri` |
| 새 파일/디렉터리 | `touch`/`mkdir` | `New-Item` | `ni` |
| 검색 | `grep` | `Select-String` | `sls` |
| 환경변수 | `env`/`echo $VAR` | `Get-ChildItem env:` / `$env:VAR` | |
| 명령어 위치 | `which` | `Get-Command` | `gcm` |
| 도움말 | `man` | `Get-Help` | `help`, `man` |

> ⚠ **별칭의 함정**: `ls`, `cat`, `cp`는 별칭일 뿐 진짜 명령은 cmdlet. 스크립트에서는 **별칭 사용 금지**(가독성·이식성·정적분석 모두 패배). 인터랙티브에서만 별칭 사용.

> ⚠ **`curl`은 함정**: PowerShell 5.1/7에서 `curl`은 `Invoke-WebRequest` 별칭. 실제 cURL을 쓰려면 `curl.exe`.

---

## 3. 탐험 3총사 — 모르는 것을 만났을 때

### Get-Help

```powershell
Get-Help Get-Process
Get-Help Get-Process -Examples
Get-Help Get-Process -Full
Get-Help Get-Process -Online        # 브라우저로 MS Docs 열기

# 키워드로 찾기
Get-Help process                    # 'process'가 들어간 항목들
```

`Get-Help`가 비어 있으면 한 번만 실행:

```powershell
Update-Help            # 도움말 다운로드 (관리자 권한)
Update-Help -UICulture en-US -Force
```

### Get-Command

```powershell
Get-Command Get-Process
Get-Command *process*               # 'process'를 포함하는 모든 명령
Get-Command -Module Microsoft.PowerShell.Management
Get-Command -Verb Get               # Get으로 시작하는 모든 명령
Get-Command -CommandType Function   # 함수만
```

### Get-Member

```powershell
Get-Process | Get-Member            # Process 객체가 가진 속성/메서드
Get-Process | Get-Member -MemberType Property
Get-Process | Get-Member -MemberType Method

# 단축
Get-Process | gm
```

이 세 가지면 모르는 cmdlet이 와도 진행할 수 있다.

---

## 4. 파이프라인의 빵과 버터 — Where, Select, Sort, Group, Measure, ForEach

### Where-Object — 필터링 (Bash의 `grep`/`awk` 조건)

```powershell
# 기본 문법
Get-Process | Where-Object { $_.WorkingSet -gt 100MB }

# 단축 문법 (PS 3.0+)
Get-Process | Where-Object WorkingSet -gt 100MB
Get-Process | Where Name -eq "chrome"

# 단축 별칭
Get-Process | ? Name -eq chrome     # 인터랙티브에서만, 스크립트에서는 ❌
```

비교 연산자:

| 연산자 | 의미 | 예 |
|---|---|---|
| `-eq` | equal | `$x -eq 5` |
| `-ne` | not equal | |
| `-gt` / `-ge` | greater (or equal) | |
| `-lt` / `-le` | less (or equal) | |
| `-like` | 와일드카드 (`*`, `?`) | `Name -like "chr*"` |
| `-match` | 정규식 | `Name -match "^[A-Z]"` |
| `-contains` | 컬렉션 포함 | `@(1,2,3) -contains 2` |
| `-in` | 좌변이 우변에 포함 | `2 -in @(1,2,3)` |

대소문자 구별: `-eq`는 기본 case-insensitive. `-ceq`, `-clike`로 강제 case-sensitive.

### Select-Object — 컬럼 뽑기·이름 바꾸기·계산 컬럼

```powershell
# 속성 일부만
Get-Process | Select-Object Name, Id, WorkingSet

# 처음/마지막 N개
Get-Process | Sort WS -Descending | Select-Object -First 5
Get-Process | Select-Object -Last 3
Get-Process | Select-Object -Skip 10 -First 5

# 계산 컬럼 (별칭과 함께)
Get-Process | Select-Object Name, Id, @{
    Name = 'MemoryMB'
    Expression = { [math]::Round($_.WorkingSet / 1MB, 2) }
}

# 중복 제거
Get-Process | Select-Object Name -Unique

# 객체를 풀어헤치기 (Bash의 cut -d, -f1 같은 효과)
"alice,30,seoul" -split "," | Select-Object -First 1
```

### Sort-Object

```powershell
Get-Process | Sort-Object CPU -Descending
Get-Process | Sort-Object Name, Id          # 다중 키
Get-ChildItem | Sort-Object Length -Desc | Select -First 5
```

### Group-Object

```powershell
# 상태별 서비스 개수
Get-Service | Group-Object Status

# 확장자별 파일 개수
Get-ChildItem -Recurse | Group-Object Extension | Sort Count -Desc
```

### Measure-Object — 집계 (sum/avg/min/max)

```powershell
Get-ChildItem *.log | Measure-Object Length -Sum -Average -Maximum

Get-ChildItem -Recurse C:\Logs |
    Measure-Object Length -Sum |
    Select-Object @{N='TotalGB';E={ [math]::Round($_.Sum / 1GB, 2) }}
```

### ForEach-Object — 객체마다 작업

```powershell
# 각 프로세스를 순회하며
Get-Process | ForEach-Object { "$($_.Name) uses $($_.WS / 1MB) MB" }

# 파일 일괄 이름 변경
Get-ChildItem *.txt | ForEach-Object {
    Rename-Item $_ "$($_.BaseName)-backup.txt"
}

# 단축
Get-Process | % { $_.Kill() }        # 인터랙티브용
```

> **PS 7.x 신기능**: `ForEach-Object -Parallel`로 병렬 실행 (Bash의 `xargs -P`와 유사).

```powershell
1..10 | ForEach-Object -Parallel { Start-Sleep 1; "$_ done" } -ThrottleLimit 5
```

---

## 5. 종합 예제 — Bash와 비교

### "현재 메모리 사용량 톱 5 프로세스"

```bash
# Bash
ps -eo rss,comm --sort=-rss | head -6
```

```powershell
# PowerShell
Get-Process |
    Sort-Object WorkingSet -Descending |
    Select-Object -First 5 Name, Id, @{N='MB';E={[math]::Round($_.WorkingSet/1MB,1)}}
```

### "특정 디렉터리에서 *.log 파일 중 100MB 초과만"

```bash
find /var/log -name "*.log" -size +100M
```

```powershell
Get-ChildItem -Path C:\Logs -Filter *.log -Recurse |
    Where-Object Length -gt 100MB |
    Select-Object FullName, @{N='MB';E={[math]::Round($_.Length/1MB)}}
```

### "사용 중인 포트의 프로세스 찾기"

```bash
ss -tlnp | grep ':8080'
```

```powershell
Get-NetTCPConnection -State Listen |
    Where-Object LocalPort -eq 8080 |
    Select-Object LocalAddress, LocalPort, OwningProcess,
                  @{N='ProcessName';E={(Get-Process -Id $_.OwningProcess).Name}}
```

---

## 6. PowerShell 5.1 vs 7.x — 무엇이 다른가

| 기능 | PS 5.1 (Windows PowerShell) | PS 7.x (PowerShell Core) |
|---|---|---|
| 실행 파일 | `powershell.exe` | `pwsh.exe` |
| 런타임 | .NET Framework | .NET (cross-platform) |
| 동작 OS | Windows만 | Windows, Linux, macOS |
| `&&`, `\|\|` 연산자 | ❌ | ⭕ |
| 삼항 `? :`, `??`, `?.` | ❌ | ⭕ |
| `ForEach-Object -Parallel` | ❌ | ⭕ |
| `Invoke-WebRequest` 성능 | 느림(IE 엔진) | 빠름 (HttpClient) |
| ANSI 색상 (`$PSStyle`) | ❌ | ⭕ |
| 호환성 모드 (`-UseWindowsPowerShell`) | N/A | ⭕ |
| Windows 모듈 자동 로드 | 기본 가능 | 호환성 레이어 필요 (`AD`, `GroupPolicy` 등) |

> **권장**: 학습 및 새 스크립트는 **PS 7.x**. 기존 Windows-only 모듈을 써야 할 때만 5.1.

### 호환성 모드

```powershell
# PS 7에서 Windows PowerShell 5.1 명령 실행
Import-Module ActiveDirectory -UseWindowsPowerShell
```

---

## 7. 변수와 자동 변수

```powershell
# 변수
$name = "Alice"
$count = 10
$list = @(1, 2, 3)
$hash = @{ Key1 = "Val1"; Key2 = 42 }

# 변수 타입 강제
[int]$x = "42"
[string]$y = 42
[datetime]$now = "2026-05-15"

# 자동 변수 (PS가 채워주는 변수)
$_         # 파이프라인 현재 객체 ($PSItem과 동일)
$?         # 직전 명령 성공 여부 ($true/$false)
$PSItem    # $_와 동일
$args      # 함수/스크립트 인자 배열
$PSVersionTable  # PowerShell 버전
$PSScriptRoot    # 현재 .ps1의 디렉터리
$Error           # 발생한 에러 배열 (최근 것이 $Error[0])
$null            # null 리터럴
$true, $false    # 불리언
```

### 변수의 스코프

```powershell
# Global, Script, Local
$global:counter = 0
$script:total = 100
```

---

## 8. 흔한 함정과 안전 패턴

### 함정 1: 단일 객체와 배열

```powershell
# 디렉터리에 파일이 1개면 배열이 아니라 단일 객체로 반환됨
$files = Get-ChildItem *.log
$files.Count        # 파일이 1개면 .Count가 없을 수도

# ✅ 항상 배열로 강제
$files = @(Get-ChildItem *.log)
$files.Count        # 0이든 1이든 N이든 안전
```

### 함정 2: 자동 변환(Coercion)

```powershell
"5" + 3             # "53" (문자열 연결)
5 + "3"             # 8 (숫자로 변환)

# 명시적으로
[int]"5" + 3        # 8
"$(5 + 3)"          # "8"
```

### 함정 3: 출력이 의도와 다름 (배열로 펼쳐짐)

```powershell
function Get-Stuff {
    $arr = @(1, 2, 3)
    return $arr        # 호출자는 배열을 받는다고 생각
}
$x = Get-Stuff
$x.GetType()           # Object[]

# 단일 객체로 반환하고 싶을 때
function Get-Item-NoUnroll {
    return , @(1, 2, 3)   # 쉼표 = 단일 요소 배열로 감싸기
}
```

### 함정 4: `Write-Host`는 파이프에 안 흘러감

```powershell
function Bad-Func {
    Write-Host "Hello"    # 화면에만 출력, 캡처 불가
}

function Good-Func {
    "Hello"               # 또는 Write-Output "Hello"
}

$result = Good-Func       # "Hello" 캡처됨
$result = Bad-Func        # $null
```

### 함정 5: `Out-File`의 기본 인코딩

```powershell
# ❌ PS 5.1에서는 UTF-16 LE BOM(기본). Linux 도구가 못 읽음
"hello" | Out-File C:\out.txt

# ✅ UTF-8
"hello" | Out-File C:\out.txt -Encoding UTF8

# 더 권장 (PS 7에서는 기본이 utf8NoBOM)
"hello" | Set-Content C:\out.txt -Encoding utf8
```

> **PS 5.1**: 거의 모든 cmdlet의 기본 인코딩이 UTF-16 또는 Default(시스템 코드페이지). 다른 도구와 주고받을 때 늘 명시.
> **PS 7+**: 기본이 `utf8NoBOM`. 훨씬 안전.

---

## 9. ❌ 위험 / ✅ 안전 — 실전 패턴

```powershell
# ❌ 객체를 문자열로 강제하면 다음 단계가 깨짐
Get-Process | Out-String | Select-String "chrome"

# ✅ 객체 그대로
Get-Process | Where-Object Name -eq "chrome"
```

```powershell
# ❌ ForEach로 일일이 처리 (느리고 장황)
$result = @()
foreach ($p in Get-Process) {
    if ($p.WorkingSet -gt 100MB) {
        $result += $p.Name
    }
}

# ✅ 파이프라인 (빠르고 짧음)
$result = Get-Process | Where-Object WorkingSet -gt 100MB | Select-Object -Expand Name
```

```powershell
# ❌ 외부 프로세스에 객체를 던지면 .ToString()이 호출되어 손실
Get-Process | nodejs.exe

# ✅ 명시적 변환
Get-Process | ConvertTo-Json | nodejs.exe
```

---

## 10. 실습 (Hands-on)

### Step 1: 객체임을 체험

```powershell
$p = Get-Process | Select-Object -First 1
$p.GetType()
$p | Get-Member | Select-Object Name, MemberType | Format-Table

# 메서드 직접 호출
$p.Refresh()
$p.PriorityClass
```

### Step 2: 메모리 톱 10

```powershell
Get-Process |
    Sort-Object WorkingSet -Descending |
    Select-Object -First 10 Name, Id,
        @{N='MB';E={[math]::Round($_.WorkingSet/1MB,1)}},
        @{N='Threads';E={$_.Threads.Count}}
```

### Step 3: 디스크 큰 파일 찾기 (NTFS)

```powershell
Get-ChildItem C:\Users\$env:USERNAME -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object Length -gt 100MB |
    Sort-Object Length -Descending |
    Select-Object -First 10 FullName,
        @{N='SizeMB';E={[math]::Round($_.Length/1MB,1)}}
```

### Step 4: 서비스 상태 분류

```powershell
Get-Service |
    Group-Object Status |
    Select-Object Name, Count |
    Sort-Object Count -Descending
```

### Step 5: JSON으로 출력 (다른 도구와 연동)

```powershell
Get-Process |
    Select-Object Name, Id, WorkingSet |
    Sort WorkingSet -Desc |
    Select -First 5 |
    ConvertTo-Json
```

---

## 더 읽어볼 자료

- 📘 『PowerShell in Action』 (Bruce Payette) — 핵심 바이블
- 📘 『Learn PowerShell in a Month of Lunches』 (Don Jones, Jeff Hicks) — 입문자에게 강추
- 🔗 PowerShell Docs: <https://learn.microsoft.com/powershell/>
- 🔗 about_Topics (개념 도움말): `Get-Help about_Variables`, `Get-Help about_Operators` 등
- 🔗 PowerShell Gallery: <https://www.powershellgallery.com/>
- 🎓 Microsoft Learn — PowerShell: <https://learn.microsoft.com/training/powershell/>

---

## 자가 점검

- [ ] PowerShell의 출력이 "텍스트가 아니라 객체"임을 동료에게 설명할 수 있는가?
- [ ] `Get-Member`로 모르는 객체의 속성을 찾아낼 수 있는가?
- [ ] `Where`, `Select`, `Sort`, `Group`, `Measure`, `ForEach`를 조합해 한 줄 분석을 작성할 수 있는가?
- [ ] PS 5.1과 7.x의 차이 3가지를 즉시 말할 수 있는가?
- [ ] `Out-File`의 기본 인코딩 함정을 안다?

다음: [`05_powershell_essentials.md`](05_powershell_essentials.md)
