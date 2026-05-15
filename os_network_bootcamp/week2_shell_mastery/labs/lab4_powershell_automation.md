# Lab 4 — PowerShell 자동화 미니 프로젝트

## 목표

다음 요구사항을 모두 만족하는 `Invoke-SystemAudit.ps1`을 작성한다.

1. 여러 Windows 서버를 대상으로 점검 수행
2. 점검 항목: 디스크 여유 공간, CPU/메모리 사용률, 최근 1시간 에러 이벤트, 만료 임박 인증서
3. 임계치 위반 시 경고 표시, 정상은 OK
4. JSON 리포트 및 HTML 리포트 출력
5. `-WhatIf` 지원
6. Pester 테스트 포함

---

## 1단계: 점검 함수 작성

```powershell
# Invoke-SystemAudit.psm1
function Get-DiskAudit {
    [CmdletBinding()]
    param([int]$WarnFreePercent = 20)
    Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3" |
        ForEach-Object {
            $freePct = if ($_.Size -gt 0) { [math]::Round($_.FreeSpace/$_.Size*100, 1) } else { 0 }
            [PSCustomObject]@{
                Item   = "Disk $($_.DeviceID)"
                Value  = "$freePct%"
                Status = if ($freePct -lt $WarnFreePercent) { 'WARN' } else { 'OK' }
                Detail = "Free: $([math]::Round($_.FreeSpace/1GB,1)) GB / Total: $([math]::Round($_.Size/1GB,1)) GB"
            }
        }
}

function Get-MemAudit {
    [CmdletBinding()]
    param([int]$WarnUsedPercent = 85)
    $os = Get-CimInstance Win32_OperatingSystem
    $usedPct = [math]::Round((($os.TotalVisibleMemorySize - $os.FreePhysicalMemory) / $os.TotalVisibleMemorySize) * 100, 1)
    [PSCustomObject]@{
        Item   = "Memory"
        Value  = "$usedPct%"
        Status = if ($usedPct -gt $WarnUsedPercent) { 'WARN' } else { 'OK' }
        Detail = "Used: $([math]::Round(($os.TotalVisibleMemorySize - $os.FreePhysicalMemory)/1MB,1)) GB / Total: $([math]::Round($os.TotalVisibleMemorySize/1MB,1)) GB"
    }
}

function Get-RecentErrorAudit {
    [CmdletBinding()]
    param([int]$Hours = 1, [int]$WarnCount = 5)
    $since = (Get-Date).AddHours(-$Hours)
    try {
        $errors = Get-WinEvent -FilterHashtable @{
            LogName='System','Application'; Level=1,2; StartTime=$since
        } -ErrorAction Stop
        $count = $errors.Count
    } catch {
        $count = 0
    }
    [PSCustomObject]@{
        Item   = "Recent Errors ($Hours h)"
        Value  = $count
        Status = if ($count -gt $WarnCount) { 'WARN' } else { 'OK' }
        Detail = "Critical+Error events in last $Hours hours"
    }
}

function Get-CertAudit {
    [CmdletBinding()]
    param([int]$WarnDaysLeft = 30)
    Get-ChildItem Cert:\LocalMachine\My -ErrorAction SilentlyContinue |
        Where-Object { $_.NotAfter -gt (Get-Date) } |
        ForEach-Object {
            $daysLeft = ($_.NotAfter - (Get-Date)).Days
            [PSCustomObject]@{
                Item   = "Cert $($_.Subject)"
                Value  = "$daysLeft days"
                Status = if ($daysLeft -lt $WarnDaysLeft) { 'WARN' } else { 'OK' }
                Detail = "Expires $($_.NotAfter.ToString('yyyy-MM-dd'))"
            }
        }
}

Export-ModuleMember -Function Get-DiskAudit, Get-MemAudit, Get-RecentErrorAudit, Get-CertAudit
```

---

## 2단계: 메인 스크립트

```powershell
<#
.SYNOPSIS
    여러 서버에 대해 시스템 점검을 수행하고 리포트 생성
.EXAMPLE
    .\Invoke-SystemAudit.ps1 -Computers SRV01,SRV02 -OutputDir C:\audit
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string[]]$Computers = @($env:COMPUTERNAME),
    [string]$OutputDir   = "$env:TEMP\audit",
    [int]$ThrottleLimit  = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# 모듈 로드
Import-Module "$PSScriptRoot\Invoke-SystemAudit.psm1" -Force

if ($PSCmdlet.ShouldProcess($OutputDir, 'Create output directory')) {
    New-Item -Path $OutputDir -ItemType Directory -Force | Out-Null
}

$auditBlock = {
    @(
        Get-DiskAudit
        Get-MemAudit
        Get-RecentErrorAudit
        Get-CertAudit
    ) | ForEach-Object {
        $_ | Add-Member -NotePropertyName Server -NotePropertyValue $env:COMPUTERNAME -PassThru
    }
}

Write-Host "Auditing $($Computers.Count) server(s)..." -ForegroundColor Cyan

$results = if ($Computers -contains $env:COMPUTERNAME -and $Computers.Count -eq 1) {
    & $auditBlock
} else {
    Invoke-Command -ComputerName $Computers -ScriptBlock $auditBlock -ThrottleLimit $ThrottleLimit
}

# JSON 출력
$jsonPath = Join-Path $OutputDir "audit_$(Get-Date -Format yyyyMMdd_HHmmss).json"
if ($PSCmdlet.ShouldProcess($jsonPath, 'Write JSON report')) {
    $results | ConvertTo-Json -Depth 5 | Set-Content $jsonPath -Encoding utf8
    Write-Host "JSON report: $jsonPath" -ForegroundColor Green
}

# HTML 출력
$htmlPath = Join-Path $OutputDir "audit_$(Get-Date -Format yyyyMMdd_HHmmss).html"
$style = @"
<style>
body { font-family: Segoe UI, sans-serif; }
table { border-collapse: collapse; }
th, td { border: 1px solid #ccc; padding: 4px 8px; }
.WARN { background-color: #ffe5e5; color: #a00; }
.OK { background-color: #e5ffe5; }
</style>
"@
if ($PSCmdlet.ShouldProcess($htmlPath, 'Write HTML report')) {
    $results | Select-Object Server, Item, Value, Status, Detail |
        ConvertTo-Html -Title "System Audit" -Head $style |
        ForEach-Object {
            # 행 상태에 따라 클래스 적용 (간단 치환)
            $_ -replace '<tr><td>(.*?)</td><td>(.*?)</td><td>(.*?)</td><td>WARN</td>', '<tr class="WARN"><td>$1</td><td>$2</td><td>$3</td><td>WARN</td>' `
               -replace '<tr><td>(.*?)</td><td>(.*?)</td><td>(.*?)</td><td>OK</td>',   '<tr class="OK"><td>$1</td><td>$2</td><td>$3</td><td>OK</td>'
        } | Set-Content $htmlPath -Encoding utf8
    Write-Host "HTML report: $htmlPath" -ForegroundColor Green
}

# 요약
$summary = $results | Group-Object Status
Write-Host "`nSummary:" -ForegroundColor Yellow
$summary | ForEach-Object {
    $color = if ($_.Name -eq 'WARN') { 'Red' } else { 'Green' }
    Write-Host "  $($_.Name): $($_.Count)" -ForegroundColor $color
}

# WARN이 있으면 exit code 1 (CI 통합)
if (($summary | Where-Object Name -eq 'WARN').Count -gt 0) { exit 1 }
```

---

## 3단계: Pester 테스트

```powershell
# Invoke-SystemAudit.Tests.ps1
BeforeAll {
    Import-Module "$PSScriptRoot\Invoke-SystemAudit.psm1" -Force
}

Describe 'Get-DiskAudit' {
    BeforeEach {
        Mock Get-CimInstance {
            @(
                [PSCustomObject]@{ DeviceID='C:'; Size=100GB; FreeSpace=10GB }
                [PSCustomObject]@{ DeviceID='D:'; Size=200GB; FreeSpace=80GB }
            )
        } -ParameterFilter { $ClassName -eq 'Win32_LogicalDisk' }
    }

    It 'flags C: as WARN (10% free, default threshold 20%)' {
        $r = Get-DiskAudit
        ($r | Where-Object Item -eq 'Disk C:').Status | Should -Be 'WARN'
    }
    It 'flags D: as OK (40% free)' {
        $r = Get-DiskAudit
        ($r | Where-Object Item -eq 'Disk D:').Status | Should -Be 'OK'
    }
}

Describe 'Get-MemAudit' {
    BeforeEach {
        Mock Get-CimInstance {
            [PSCustomObject]@{
                TotalVisibleMemorySize = 16777216  # 16GB in KB
                FreePhysicalMemory     =  2097152  # 2GB in KB
            }
        } -ParameterFilter { $ClassName -eq 'Win32_OperatingSystem' }
    }
    It 'reports WARN when usage > 85%' {
        $r = Get-MemAudit -WarnUsedPercent 50
        $r.Status | Should -Be 'WARN'
    }
}
```

```powershell
Invoke-Pester -Path . -Output Detailed
```

---

## 4단계: 실행 결과 확인

```powershell
# 자기 자신을 audit
.\Invoke-SystemAudit.ps1 -Verbose

# 임시 디렉터리 확인
ls $env:TEMP\audit\*.html | Sort LastWriteTime -Desc | Select -First 1 | Invoke-Item
```

---

## 5단계 (도전): GitHub Actions 통합

스크립트를 GitHub repo에 두고 `.github/workflows/audit.yml`로 매일 실행:

```yaml
name: Daily System Audit
on:
  schedule:
    - cron: '0 18 * * *'    # UTC 18:00 = KST 03:00
  workflow_dispatch:

jobs:
  audit:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run audit
        shell: pwsh
        run: |
          .\Invoke-SystemAudit.ps1 -OutputDir audit-report
      - uses: actions/upload-artifact@v4
        with:
          name: audit-report
          path: audit-report/
```

---

## 회고 질문

1. 본인 환경에서 어느 임계치가 가장 의미있는가? (운영 환경에 따라 다름)
2. WARN을 단순 임계치 위반이 아닌 **추세 변화**로 잡는다면 어떻게 구현할까? (시계열 저장 → 분석)
3. Linux 서버도 같이 점검하려면? (SSH-based Remoting + 등가 cmdlet)

다음: [`../checklist.md`](../checklist.md)
