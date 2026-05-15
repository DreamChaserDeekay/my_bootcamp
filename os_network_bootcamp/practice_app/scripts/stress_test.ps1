<#
.SYNOPSIS
    practice_app에 부하를 가하고 시스템 상태를 캡처
.EXAMPLE
    .\stress_test.ps1 -Url 'http://localhost:8080/work?ms=50' -Duration 60 -Concurrency 200
#>
[CmdletBinding()]
param(
    [string]$Url = 'http://localhost:8080/work?ms=50',
    [int]$Duration = 60,        # 초
    [int]$Concurrency = 200,
    [string]$OutDir = ".\data\$(Get-Date -Format yyyyMMdd_HHmmss)"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

New-Item -Path $OutDir -ItemType Directory -Force | Out-Null
Write-Host "Output: $OutDir" -ForegroundColor Cyan
Write-Host "URL: $Url ($Concurrency conns × ${Duration}s)" -ForegroundColor Cyan

# JVM 프로세스 찾기
$jvm = Get-Process java -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jvm) {
    Write-Error "java process not found. Start: ./gradlew bootRun"
    return
}
Write-Host "JVM pid: $($jvm.Id)"

# 백그라운드 카운터 측정
$counterJob = Start-Job -ScriptBlock {
    param($d, $out)
    Get-Counter -Counter '\Processor(_Total)\% Processor Time',
                       '\Memory\Available MBytes',
                       '\TCPv4\Connections Established',
                       '\TCPv4\Connections in TIME-WAIT' `
        -SampleInterval 1 -MaxSamples $d |
        Export-Csv -Path $out -NoTypeInformation
} -ArgumentList $Duration, "$OutDir\counters.csv"

# 간단한 부하 (PowerShell native, 정확도보다는 동작 확인용)
Write-Host "Running load..." -ForegroundColor Yellow
$ok = 0; $err = 0
$start = Get-Date

$jobs = 1..$Concurrency | ForEach-Object {
    Start-Job -ScriptBlock {
        param($url, $until)
        $ok = 0; $err = 0
        while ((Get-Date) -lt $until) {
            try {
                Invoke-RestMethod -Uri $url -TimeoutSec 5 | Out-Null
                $ok++
            } catch { $err++ }
        }
        [PSCustomObject]@{ ok = $ok; err = $err }
    } -ArgumentList $Url, (Get-Date).AddSeconds($Duration)
}

# 진행 상황 표시
while (($jobs | Where-Object State -eq 'Running').Count -gt 0) {
    Start-Sleep -Seconds 5
    $elapsed = (Get-Date) - $start
    Write-Host ("Elapsed: {0:N0}s" -f $elapsed.TotalSeconds)
}

# 결과 수집
$results = $jobs | ForEach-Object { Receive-Job $_; Remove-Job $_ }
$ok  = ($results | Measure-Object ok  -Sum).Sum
$err = ($results | Measure-Object err -Sum).Sum

$counterJob | Wait-Job | Out-Null
Receive-Job $counterJob | Out-Null
Remove-Job $counterJob

$dur = (Get-Date) - $start
$rps = $ok / $dur.TotalSeconds

Write-Host "`n=== Summary ===" -ForegroundColor Green
Write-Host ("Total time: {0:N1}s" -f $dur.TotalSeconds)
Write-Host ("OK: $ok, ERR: $err")
Write-Host ("RPS: {0:N1}" -f $rps)
Write-Host ("Logs: $OutDir")

# 마무리 상태
Write-Host "`nFinal TCP states:" -ForegroundColor Yellow
Get-NetTCPConnection | Group-Object State | Select Count, Name | Sort Count -Desc | Format-Table

Write-Host "`nJVM info:" -ForegroundColor Yellow
try {
    Invoke-RestMethod 'http://localhost:8080/info' | Format-List
} catch {
    Write-Warning "Could not reach /info: $($_.Exception.Message)"
}
