# Day 4 — PowerShell 스크립팅: 고급 함수 · 모듈 · 에러 처리

## 한 줄 요약

PowerShell 함수는 **단순한 서브루틴이 아니라 cmdlet의 자작 버전**이다. `[CmdletBinding()]`과 `param()`만 잘 쓰면 `-Verbose`, `-WhatIf`, `-Confirm`, 파이프라인 입력, 검증 속성까지 그대로 받아 쓸 수 있고, 이는 곧 Bash에서는 100줄 짜리 보일러플레이트가 필요한 일을 0줄로 만들어 준다.

## 학습 목표

- [ ] 고급 함수 (`[CmdletBinding()]`, `[Parameter()]`) 를 작성한다
- [ ] **파이프라인 입력** (`ValueFromPipeline`, `process` 블록)을 활용한다
- [ ] 매개변수 **검증 속성** (`[ValidateNotNullOrEmpty()]`, `[ValidateSet()]`, `[ValidateRange()]`)을 쓴다
- [ ] **에러 처리** (`try/catch/finally`, `throw`, `$ErrorActionPreference`)를 마스터한다
- [ ] **모듈**로 코드를 묶고 배포한다
- [ ] **Pester** 단위 테스트 기초
- [ ] `-WhatIf`/`-Confirm`으로 안전한 운영 스크립트

---

## 1. 기본 함수 vs 고급 함수

### 기본 함수

```powershell
function Add-Numbers {
    param($a, $b)
    return $a + $b
}
Add-Numbers 1 2     # 3
```

### 고급 함수 (cmdlet binding)

```powershell
function Add-Numbers {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][int]$A,
        [Parameter(Mandatory)][int]$B
    )
    $A + $B
}

Add-Numbers -A 1 -B 2

# 이제 자동으로 다음을 지원
Add-Numbers -A 1 -B 2 -Verbose
Add-Numbers -A 1 -B 2 -ErrorAction Stop
Get-Help Add-Numbers
```

### `[CmdletBinding()]`이 자동으로 주는 것

- 공통 매개변수: `-Verbose`, `-Debug`, `-ErrorAction`, `-WarningAction`, `-OutVariable` 등
- 인자 위치·이름·자료형 강제
- `-WhatIf`/`-Confirm` 지원 (별도 설정 필요)

---

## 2. 매개변수와 검증 속성

```powershell
function Get-EmployeeBonus {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [string]$Name,

        [Parameter(Mandatory)]
        [ValidateRange(0, 100000)]
        [decimal]$Salary,

        [ValidateSet('Engineering', 'Sales', 'HR')]
        [string]$Department = 'Engineering',

        [ValidatePattern('^\d{4}-\d{2}-\d{2}$')]
        [string]$HireDate,

        [ValidateScript({ Test-Path $_ })]
        [string]$ConfigPath,

        [int]$Multiplier = 1
    )

    $rate = switch ($Department) {
        'Engineering' { 0.15 }
        'Sales'       { 0.20 }
        default       { 0.10 }
    }
    [math]::Round($Salary * $rate * $Multiplier, 2)
}

Get-EmployeeBonus -Name Alice -Salary 8000 -Department Engineering
# 1200.00
```

### 검증 속성 일람

| 속성 | 용도 |
|---|---|
| `[Parameter(Mandatory)]` | 필수 |
| `[Parameter(Position=0)]` | 위치 인자 |
| `[Parameter(ValueFromPipeline)]` | 파이프 입력 |
| `[Parameter(ValueFromPipelineByPropertyName)]` | 객체의 속성명으로 매핑 |
| `[Parameter(ParameterSetName='A')]` | 매개변수 집합 (상호배타) |
| `[ValidateNotNull()]` | null 금지 |
| `[ValidateNotNullOrEmpty()]` | null/빈문자열/빈배열 금지 |
| `[ValidateLength(min, max)]` | 길이 |
| `[ValidateRange(min, max)]` | 숫자 범위 |
| `[ValidateSet('a','b')]` | 정해진 값만 |
| `[ValidatePattern('regex')]` | 정규식 |
| `[ValidateScript({ ... })]` | 임의 검증 |
| `[AllowNull()]`, `[AllowEmptyString()]`, `[AllowEmptyCollection()]` | Mandatory 예외 |

---

## 3. 파이프라인 입력 — begin / process / end

cmdlet이 진짜로 cmdlet답게 동작하려면 **파이프 입력**을 받아야 한다.

```powershell
function Get-FileSizeKB {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory, ValueFromPipeline, ValueFromPipelineByPropertyName)]
        [Alias('FullName')]            # FileInfo의 FullName 속성도 매핑
        [string[]]$Path
    )

    begin {
        Write-Verbose "Starting..."
        $total = 0
    }

    process {
        foreach ($p in $Path) {
            if (Test-Path $p) {
                $size = (Get-Item $p).Length / 1KB
                $total += $size
                [PSCustomObject]@{
                    Path = $p
                    SizeKB = [math]::Round($size, 2)
                }
            }
        }
    }

    end {
        Write-Verbose "Total: $total KB"
    }
}

# 다양한 방법으로 호출 가능
'C:\file1.txt', 'C:\file2.txt' | Get-FileSizeKB
Get-ChildItem *.log | Get-FileSizeKB                # FullName 매핑됨
Get-FileSizeKB -Path 'C:\file1.txt'
```

### 블록의 의미

| 블록 | 호출 시점 |
|---|---|
| `begin` | 함수 시작 시 한 번 (초기화) |
| `process` | 파이프 객체마다 (또는 직접 호출 시 한 번) |
| `end` | 함수 끝에 한 번 (정리·합계) |
| `clean` (PS 7.3+) | finally처럼, 어떤 종료 경로든 |

> **잊지 말 것**: `process` 안에서 `$Path`는 한 번에 하나씩만 와도 배열로 선언했으면 그대로 배열로 옴. 그래서 `foreach ($p in $Path)` 패턴이 안전.

---

## 4. 에러 처리

### 종결 에러 vs 비종결 에러

PowerShell의 에러는 두 종류:

- **종결 에러(Terminating)**: 즉시 catch로 가거나 스크립트 중단
- **비종결 에러(Non-Terminating)**: 메시지만 출력하고 계속 진행 (기본)

대부분의 cmdlet은 비종결 에러를 낸다. 이를 catch로 잡으려면 `-ErrorAction Stop`이 필요하다.

```powershell
# ❌ catch에 안 잡힘 (비종결)
try {
    Get-Item C:\nonexistent
}
catch {
    "Caught!"
}
# 결과: 빨간 에러만 출력, "Caught!" 안 찍힘

# ✅ 종결로 승격
try {
    Get-Item C:\nonexistent -ErrorAction Stop
}
catch {
    "Caught: $($_.Exception.Message)"
}

# ✅ 또는 전역 설정
$ErrorActionPreference = 'Stop'
try { Get-Item C:\nonexistent }
catch { "Caught" }
```

### try/catch/finally

```powershell
function Invoke-WithRetry {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][scriptblock]$Action,
        [int]$MaxAttempts = 3,
        [int]$DelaySec = 2
    )

    for ($i = 1; $i -le $MaxAttempts; $i++) {
        try {
            return & $Action
        }
        catch [System.Net.WebException] {
            Write-Warning "Network error on attempt $i/$MaxAttempts: $($_.Exception.Message)"
            if ($i -lt $MaxAttempts) { Start-Sleep -Seconds $DelaySec }
        }
        catch {
            Write-Error "Unrecoverable error: $($_.Exception.Message)"
            throw
        }
    }
    throw "All $MaxAttempts attempts failed"
}

# 사용
Invoke-WithRetry -Action {
    Invoke-RestMethod 'https://api.example.com/data' -ErrorAction Stop
}
```

### $Error / $_

```powershell
$Error.Clear()
Get-Item C:\nope -ErrorAction SilentlyContinue
$Error[0]                       # 가장 최근 에러
$Error[0].Exception
$Error[0].Exception.GetType().FullName
$Error[0].ScriptStackTrace
$Error[0].InvocationInfo
```

### throw

```powershell
throw "Simple message"
throw [System.IO.FileNotFoundException]::new("File not found: $path")

# 함수 안에서 매개변수 검증 실패
if ($age -lt 0) {
    throw [System.ArgumentOutOfRangeException]::new('Age', $age, "Age cannot be negative")
}
```

### Write-Error vs throw

| | Write-Error | throw |
|---|---|---|
| 종류 | 비종결 (기본) | 종결 |
| 스택트레이스 | 없음 | 있음 |
| 사용 | 사용자에게 알리고 계속 | 즉시 중단 |

---

## 5. -WhatIf / -Confirm — 안전한 운영 스크립트

```powershell
function Remove-OldLog {
    [CmdletBinding(SupportsShouldProcess, ConfirmImpact='Medium')]
    param(
        [Parameter(Mandatory)][string]$Path,
        [int]$DaysOld = 7
    )

    $cutoff = (Get-Date).AddDays(-$DaysOld)
    $files = Get-ChildItem $Path -File | Where-Object LastWriteTime -lt $cutoff

    foreach ($f in $files) {
        if ($PSCmdlet.ShouldProcess($f.FullName, "Remove file")) {
            Remove-Item $f.FullName
        }
    }
}

# 시뮬레이션
Remove-OldLog -Path C:\logs -WhatIf
# What if: Performing the operation "Remove file" on target "C:\logs\old.log"

# 확인 받기
Remove-OldLog -Path C:\logs -Confirm

# 실제 실행
Remove-OldLog -Path C:\logs -DaysOld 30
```

> **운영 황금률**: 파일 삭제·재시작·계정 변경 등 부수효과가 있는 함수에는 항상 `SupportsShouldProcess` + `ShouldProcess()`를 넣는다. `-WhatIf` 한 번으로 사고를 막을 수 있다.

`ConfirmImpact` 등급:

- `None`: `-Confirm` 안 물어봄 (기본 동작에 영향 없음)
- `Low`: `-Confirm`을 명시할 때만 물어봄
- `Medium`: `$ConfirmPreference`가 Medium 이상이면 물어봄
- `High`: 거의 항상 물어봄 (위험)

---

## 6. 모듈 — 코드 재사용

### 함수를 .ps1 → .psm1로

```powershell
# C:\Users\<you>\Documents\PowerShell\Modules\MyTools\MyTools.psm1
function Get-DiskUsage {
    [CmdletBinding()]
    param([string]$Path = $HOME)
    Get-ChildItem $Path -Recurse -File -ErrorAction SilentlyContinue |
        Measure-Object Length -Sum |
        Select-Object @{N='Path';E={$Path}},
                      @{N='SizeGB';E={[math]::Round($_.Sum/1GB, 2)}}
}

function Test-Port {
    param([string]$Host, [int]$Port, [int]$TimeoutMs = 2000)
    $tcp = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $tcp.ConnectAsync($Host, $Port)
        if ($task.Wait($TimeoutMs)) { $true } else { $false }
    } catch { $false }
    finally { $tcp.Close() }
}

Export-ModuleMember -Function Get-DiskUsage, Test-Port
```

### 매니페스트 (.psd1)

```powershell
New-ModuleManifest `
    -Path 'C:\Users\<you>\Documents\PowerShell\Modules\MyTools\MyTools.psd1' `
    -RootModule 'MyTools.psm1' `
    -ModuleVersion '1.0.0' `
    -Author 'Your Name' `
    -Description 'My utility cmdlets' `
    -FunctionsToExport 'Get-DiskUsage', 'Test-Port'
```

### 사용

```powershell
Import-Module MyTools
Get-Command -Module MyTools
Get-DiskUsage C:\Users\me
Test-Port google.com 443
```

PSModulePath에 있으면 자동 발견:

```powershell
$env:PSModulePath -split ';'
```

### PowerShell Gallery에 게시

```powershell
# 등록 필요
Publish-Module -Name MyTools -NuGetApiKey '<your-key>'
```

---

## 7. Pester — 단위 테스트

Pester는 PowerShell의 JUnit. PS 5.1+에 기본 포함, 최신 5.x로 업그레이드 권장.

```powershell
Install-Module Pester -Force -SkipPublisherCheck
```

```powershell
# MyTools.Tests.ps1
BeforeAll {
    Import-Module "$PSScriptRoot\MyTools.psm1" -Force
}

Describe 'Test-Port' {
    It 'returns $true for an open port' {
        Test-Port -Host 'google.com' -Port 443 | Should -Be $true
    }
    It 'returns $false for a closed port' {
        Test-Port -Host 'localhost' -Port 1 -TimeoutMs 500 | Should -Be $false
    }
}

Describe 'Get-EmployeeBonus' {
    Context 'Engineering with default multiplier' {
        It 'returns 15% of salary' {
            Get-EmployeeBonus -Name X -Salary 1000 -Department Engineering |
                Should -Be 150
        }
    }
    Context 'validation' {
        It 'rejects negative salary' {
            { Get-EmployeeBonus -Name X -Salary -100 } |
                Should -Throw
        }
    }
}
```

```powershell
Invoke-Pester
Invoke-Pester -Output Detailed
Invoke-Pester -CodeCoverage MyTools.psm1
```

### 모킹

```powershell
Describe 'Backup-Database' {
    BeforeEach {
        Mock Invoke-Sqlcmd { return @{ Count = 100 } }
        Mock Copy-Item     { }
    }
    It 'calls Invoke-Sqlcmd once' {
        Backup-Database -Name test
        Assert-MockCalled Invoke-Sqlcmd -Times 1
    }
}
```

---

## 8. 로깅과 트랜스크립트

```powershell
# 세션 전체 기록
Start-Transcript -Path "C:\logs\session_$(Get-Date -Format yyyyMMdd_HHmmss).log" -Append

# ... 작업

Stop-Transcript
```

### 자체 로깅 함수

```powershell
function Write-Log {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Message,
        [ValidateSet('INFO','WARN','ERROR','DEBUG')][string]$Level = 'INFO',
        [string]$Path = "$PSScriptRoot\app.log"
    )
    $line = "{0:s}Z [{1}] {2}" -f (Get-Date).ToUniversalTime(), $Level, $Message
    Add-Content -Path $Path -Value $line -Encoding utf8
    switch ($Level) {
        'ERROR' { Write-Host $line -ForegroundColor Red }
        'WARN'  { Write-Host $line -ForegroundColor Yellow }
        default { Write-Host $line }
    }
}
```

### Verbose / Debug — 빌트인 활용

```powershell
function Do-Work {
    [CmdletBinding()]
    param([string]$Name)
    Write-Verbose "Starting work for $Name"
    Write-Debug "Internal state: x=$x"
    # ...
    Write-Verbose "Completed"
}

Do-Work -Name Alice -Verbose       # Verbose 메시지 표시
Do-Work -Name Alice -Debug         # Debug 메시지 표시 (중단점)
```

---

## 9. 종합 스크립트 예제: 배포

```powershell
<#
.SYNOPSIS
    Spring Boot 앱 배포 + 헬스체크
.EXAMPLE
    .\Deploy-App.ps1 -JarUrl https://repo/app-1.2.0.jar -KeepReleases 5
#>
[CmdletBinding(SupportsShouldProcess, ConfirmImpact='High')]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^https?://')]
    [string]$JarUrl,

    [int]$KeepReleases = 5,
    [string]$AppDir = 'C:\app',
    [string]$ServiceName = 'MyApp',
    [string]$HealthUrl = 'http://localhost:8080/actuator/health',
    [int]$HealthRetries = 10,
    [int]$HealthDelaySec = 3
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Log {
    param([string]$Message, [string]$Level='INFO')
    Write-Host ("[{0}] [{1}] {2}" -f (Get-Date -Format 'HH:mm:ss'), $Level, $Message)
}

try {
    $release  = Get-Date -Format 'yyyyMMdd_HHmmss'
    $relDir   = Join-Path $AppDir "releases\$release"
    Write-Log "Release: $release"

    # 1) 디렉터리 준비
    if ($PSCmdlet.ShouldProcess($relDir, 'Create release dir')) {
        New-Item -Path $relDir -ItemType Directory -Force | Out-Null
    }

    # 2) 다운로드
    Write-Log "Downloading $JarUrl"
    $tmp = New-TemporaryFile
    Invoke-WebRequest -Uri $JarUrl -OutFile $tmp -UseBasicParsing
    if ((Get-Item $tmp).Length -eq 0) { throw "Empty JAR" }

    Copy-Item $tmp.FullName (Join-Path $relDir 'app.jar')
    Remove-Item $tmp

    # 3) current 심볼릭링크 갱신 (Windows는 권한 필요)
    $currentLink = Join-Path $AppDir 'current'
    if (Test-Path $currentLink) {
        if ($PSCmdlet.ShouldProcess($currentLink, 'Replace symlink')) {
            Remove-Item $currentLink -Force -Recurse
        }
    }
    New-Item -ItemType SymbolicLink -Path $currentLink -Target $relDir | Out-Null

    # 4) 서비스 재시작
    if ($PSCmdlet.ShouldProcess($ServiceName, 'Restart service')) {
        Restart-Service $ServiceName -Force
    }

    # 5) 헬스체크
    Write-Log "Health check..."
    $healthy = $false
    for ($i = 1; $i -le $HealthRetries; $i++) {
        try {
            $r = Invoke-RestMethod -Uri $HealthUrl -TimeoutSec 5
            if ($r.status -eq 'UP') { $healthy = $true; break }
        } catch { }
        Write-Log "Attempt $i/$HealthRetries failed" 'WARN'
        Start-Sleep -Seconds $HealthDelaySec
    }
    if (-not $healthy) { throw "Health check failed after $HealthRetries attempts" }

    # 6) 오래된 릴리스 정리
    Get-ChildItem (Join-Path $AppDir 'releases') -Directory |
        Sort-Object Name -Descending |
        Select-Object -Skip $KeepReleases |
        ForEach-Object {
            if ($PSCmdlet.ShouldProcess($_.FullName, 'Remove old release')) {
                Remove-Item $_.FullName -Recurse -Force
            }
        }

    Write-Log "Deploy complete: $release"
}
catch {
    Write-Log "DEPLOY FAILED: $($_.Exception.Message)" 'ERROR'
    Write-Log $_.ScriptStackTrace 'ERROR'
    exit 1
}
```

---

## 10. 실습

### Step 1: 파이프 가능한 함수

`Get-ProcessSize` 함수를 만들어 다음이 모두 동작하게 하라:

```powershell
Get-Process chrome | Get-ProcessSize
'chrome', 'firefox' | Get-ProcessSize
Get-ProcessSize -Name chrome
```

### Step 2: -WhatIf 지원 함수

`Backup-LogFiles` 함수가 7일 이상 된 .log를 .zip으로 묶고 원본 삭제. `SupportsShouldProcess` 추가하고 `-WhatIf`로 시뮬레이션 확인.

### Step 3: Pester 테스트

위 함수의 Pester 테스트 작성. `Mock`으로 `Get-ChildItem`, `Compress-Archive`, `Remove-Item`을 모킹.

### Step 4: 모듈로 묶기

두 함수를 `MyOpsTools` 모듈로 패키징.

---

## 더 읽어볼 자료

- 📘 『PowerShell in Action』 (Bruce Payette)
- 🔗 about_Functions_Advanced: `Get-Help about_Functions_Advanced -ShowWindow`
- 🔗 about_CommonParameters
- 🔗 about_Try_Catch_Finally
- 🔗 Pester: <https://pester.dev/>
- 🔗 PSScriptAnalyzer (ShellCheck 등가): `Install-Module PSScriptAnalyzer`

```powershell
Invoke-ScriptAnalyzer -Path .\Deploy-App.ps1
```

---

## 자가 점검

- [ ] `[CmdletBinding()]`이 무엇을 자동으로 주는지 안다
- [ ] `process` 블록을 활용한 파이프라인 함수를 작성했다
- [ ] `-WhatIf`를 지원하는 함수를 만들었다
- [ ] try/catch에 잘 안 잡히는 비종결 에러를 잡는 방법(`-ErrorAction Stop`)을 안다
- [ ] Pester 테스트 1개 이상 작성 + 통과
- [ ] PSScriptAnalyzer 클린

다음: [`05_powershell_remoting.md`](05_powershell_remoting.md)
