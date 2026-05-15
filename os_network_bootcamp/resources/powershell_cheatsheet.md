# PowerShell 치트시트

PowerShell 7 기준. 5.1 차이는 주석으로 표기.

## 1. 탐험 3총사

```powershell
Get-Help <cmd> -Examples
Get-Help <cmd> -Full
Get-Help <cmd> -Online              # 브라우저로
Update-Help                         # 한 번 실행

Get-Command *process*               # 패턴
Get-Command -Verb Get
Get-Command -Module Microsoft.PowerShell.Management
Get-Verb                            # 승인 동사 목록

Get-Process | Get-Member            # 객체 속성/메서드
Get-Process | Get-Member -MemberType Property
```

## 2. 변수·자료형

```powershell
$x = 42                             # int
$y = "hello"                        # string
$z = @(1,2,3)                       # array
$h = @{ k = "v"; n = 42 }           # hashtable
$null

[int]$x = "42"                      # 강제 변환
[string]$y = 42

# 자동 변수
$_              # 파이프 현재 객체 ($PSItem)
$?              # 직전 성공 여부
$args           # 인자
$PSVersionTable
$PSScriptRoot
$Error          # 에러 배열
$env:PATH       # 환경변수
```

## 3. 파이프 핵심 (Where/Select/Sort/Group/Measure/ForEach)

```powershell
Get-Process | Where-Object WorkingSet -gt 100MB
Get-Process | Where { $_.Name -like "chr*" -and $_.CPU -gt 10 }

Get-Process | Select-Object Name, Id, WorkingSet -First 5
Get-Process | Select Name, @{N='MB';E={[int]($_.WS/1MB)}}
Get-Process | Select -Unique Name
Get-Process | Select -Expand Name           # 단일 열만

Get-Process | Sort-Object WS -Descending
Get-Process | Sort Name, Id

Get-Service | Group-Object Status

Get-ChildItem *.log | Measure-Object Length -Sum -Average -Max

Get-Process | ForEach-Object { $_.Name }
Get-Process | ForEach-Object -Parallel { ... } -ThrottleLimit 5     # PS 7+
```

## 4. 파일·디렉터리

```powershell
Get-Location; Set-Location C:\temp        # pwd, cd
Get-ChildItem -Force                       # ls -la (숨김 포함)
Get-ChildItem -Recurse -File -Filter *.log
Get-Item C:\Windows\notepad.exe

New-Item -Path C:\temp\d -ItemType Directory -Force
New-Item -Path C:\temp\f.txt -ItemType File -Value "hi"
Copy-Item src dst
Move-Item old new
Remove-Item -Recurse -Force trash/         # 위험!
Rename-Item old new

Get-Content file.txt
Get-Content file.txt -Tail 50 -Wait        # tail -f
Set-Content file "hello"                   # 덮어쓰기
Add-Content file "more"                    # append
Out-File -Path file.txt -Encoding utf8     # 인코딩 명시 (PS 5.1 권장)

Test-Path C:\path
Resolve-Path .\rel
```

## 5. 검색·치환

```powershell
Select-String -Path *.log -Pattern "ERROR"
Select-String "TODO" -Path src\*.java -Context 2,3

"Hello World" -match "W(\w+)"
$Matches[1]                                # "orld"

"Hello World" -replace "World","PowerShell"
"a,b,c" -split ","
@("a","b","c") -join ","

# 정규식 추출
[regex]::Matches($text, '\b\d+\.\d+\.\d+\.\d+\b') | ForEach-Object Value
```

## 6. JSON·CSV·XML

```powershell
# JSON
@{ name="Alice"; age=30 } | ConvertTo-Json
'{"name":"Alice"}' | ConvertFrom-Json
Get-Content data.json | ConvertFrom-Json

# CSV
Import-Csv data.csv | Where age -gt 18
$data | Export-Csv out.csv -NoTypeInformation

# XML
[xml]$x = Get-Content config.xml
$x.root.element
```

## 7. 프로세스·서비스

```powershell
Get-Process | Sort WS -Desc | Select -First 10
Get-Process firefox
Start-Process notepad
Start-Process java -ArgumentList "-jar","app.jar" -WorkingDirectory C:\app
Stop-Process -Name chrome -Force
Wait-Process -Id 1234

Get-Service | Where Status -eq Running
Start-Service Spooler
Stop-Service Spooler -Force
Restart-Service Spooler
Set-Service Spooler -StartupType Manual
```

## 8. 이벤트 로그

```powershell
Get-WinEvent -LogName System -MaxEvents 50
Get-WinEvent -FilterHashtable @{LogName='System'; Level=2; StartTime=(Get-Date).AddHours(-1)}
Get-WinEvent -ListLog *
```

## 9. 네트워크

```powershell
Get-NetIPConfiguration
Get-NetIPAddress -InterfaceAlias Ethernet -AddressFamily IPv4
New-NetIPAddress -InterfaceAlias Ethernet -IPAddress 192.168.1.50 -PrefixLength 24
Remove-NetIPAddress -IPAddress 192.168.1.50

Get-NetRoute
Find-NetRoute -RemoteIPAddress 8.8.8.8
New-NetRoute -DestinationPrefix 10.0.0.0/8 -NextHop 192.168.1.1 -InterfaceIndex 12

Get-NetNeighbor                            # ARP

Test-NetConnection google.com -Port 443    # ping + TCP
Test-Connection google.com -Count 4
Resolve-DnsName google.com
Resolve-DnsName google.com -Type MX
Clear-DnsClientCache

Get-NetTCPConnection -State Listen
Get-NetTCPConnection -LocalPort 8080
Get-NetUDPEndpoint

# HTTP
Invoke-WebRequest -Uri https://example.com -OutFile out.html
Invoke-WebRequest -Uri URL -Method Post -Body $bodyJson -ContentType 'application/json'
Invoke-RestMethod -Uri https://api.github.com/users/octocat
Invoke-RestMethod URL -Headers @{ Authorization = "Bearer $token" }

# curl.exe (.NET HttpClient 직접)
curl.exe -sS https://example.com
```

## 10. 방화벽

```powershell
Get-NetFirewallProfile
Set-NetFirewallProfile -Profile Public -Enabled True -DefaultInboundAction Block

Get-NetFirewallRule -Enabled True | Select DisplayName, Direction, Action

New-NetFirewallRule -DisplayName "Allow 8080" `
    -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow

New-NetFirewallRule -DisplayName "Allow from LAN" `
    -Direction Inbound -RemoteAddress 10.0.0.0/8 -Protocol TCP -LocalPort 8080 -Action Allow

Remove-NetFirewallRule -DisplayName "Allow 8080"
```

## 11. PSDrive (자원 통일 접근)

```powershell
Get-PSDrive                                # 목록
Get-ChildItem env:                         # 환경변수
$env:JAVA_HOME = "C:\jdk-17"

Get-ChildItem HKLM:\SOFTWARE
Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion'

Get-ChildItem Cert:\LocalMachine\My
```

## 12. 원격

```powershell
Enable-PSRemoting -Force                   # 활성화 (관리자)

Enter-PSSession -ComputerName SRV01 -Credential (Get-Credential)
Exit-PSSession

Invoke-Command -ComputerName SRV01,SRV02 -ScriptBlock { hostname }
Invoke-Command -ComputerName $list -ThrottleLimit 10 -ScriptBlock { ... }
Invoke-Command -ComputerName SRV01 -FilePath .\audit.ps1

$sess = New-PSSession -ComputerName SRV01
Invoke-Command -Session $sess -ScriptBlock { $var = "hi" }
Invoke-Command -Session $sess -ScriptBlock { $var }
Copy-Item -ToSession $sess -Path C:\local -Destination C:\remote
Remove-PSSession $sess

# SSH (PS 7+, server측 sshd_config Subsystem 설정 필요)
Enter-PSSession -HostName user@host
Invoke-Command -HostName user@host -ScriptBlock { Get-Process }
```

## 13. 스크립팅

```powershell
function Get-Foo {
    [CmdletBinding(SupportsShouldProcess)]
    param(
        [Parameter(Mandatory)][string]$Name,
        [ValidateRange(1,100)][int]$Count = 10,
        [Parameter(ValueFromPipeline)][object]$Input
    )
    Set-StrictMode -Version Latest
    $ErrorActionPreference = 'Stop'

    begin   { ... }
    process {
        if ($PSCmdlet.ShouldProcess($Name, 'Do something')) {
            try { ... }
            catch { Write-Error $_.Exception.Message; throw }
        }
    }
    end     { ... }
}
```

## 14. 에러 처리

```powershell
try { Get-Item C:\nope -ErrorAction Stop }
catch [System.Management.Automation.ItemNotFoundException] { ... }
catch { Write-Error $_; throw }
finally { ... }

$ErrorActionPreference = 'Stop'            # 비종결을 종결로
Get-Item C:\nope -ErrorAction SilentlyContinue
```

## 15. 모듈

```powershell
Get-Module -ListAvailable
Import-Module MyTools
Install-Module Pester -Scope CurrentUser -Force
Update-Module Pester
Remove-Module MyTools
Find-Module *azure*

# 자작 모듈 위치
$env:PSModulePath -split ';'
# 보통 ~/Documents/PowerShell/Modules/<name>/<name>.psm1
```

## 16. 자주 쓰는 한 줄

```powershell
# 메모리 톱 10
Get-Process | Sort WS -Desc | Select -First 10 Name, @{N='MB';E={[int]($_.WS/1MB)}}

# 디스크 큰 폴더
Get-ChildItem $HOME -Directory | ForEach { [PSCustomObject]@{
    Path=$_.FullName
    SizeMB=[math]::Round((Get-ChildItem $_ -Recurse -File -EA SilentlyContinue | Measure Length -Sum).Sum/1MB,1)
}} | Sort SizeMB -Desc | Select -First 10

# 마지막 100 system error
Get-WinEvent -LogName System -MaxEvents 100 | Where LevelDisplayName -eq Error | Select TimeCreated, Id, Message

# 특정 포트의 프로세스
Get-NetTCPConnection -LocalPort 8080 | Select LocalPort, OwningProcess,
    @{N='Name';E={(Get-Process -Id $_.OwningProcess).Name}}

# JSON으로 출력
Get-Process | Sort WS -Desc | Select -First 5 Name, Id, WS | ConvertTo-Json

# 모든 USB 디바이스
Get-PnpDevice -Class USB | Where Status -eq OK | Select FriendlyName
```

## 17. 5.1 ↔ 7.x 호환

```powershell
# 5.1에서 안 되는 7+ 문법
A && B            # → A; if ($?) { B }
A || B            # → A; if (-not $?) { B }
$x ? $y : $z      # → if ($x) { $y } else { $z }
$a ?? $b          # → if ($null -ne $a) { $a } else { $b }
$obj?.x?.y        # → if ($obj -and $obj.x) { $obj.x.y }

# 5.1의 Out-File 기본 인코딩이 UTF-16 LE BOM → 명시
Out-File -Encoding utf8

# 7에서 5.1 모듈 호환 모드
Import-Module ActiveDirectory -UseWindowsPowerShell
```

## 18. 프로파일 (`$PROFILE`)

```powershell
# 위치 확인
$PROFILE

# 권장 내용
function ll { Get-ChildItem -Force @args }
function .. { Set-Location .. }
function gs { git status @args }

Set-PSReadLineOption -EditMode Emacs
Set-PSReadLineOption -PredictionSource HistoryAndPlugin
Set-PSReadLineOption -PredictionViewStyle ListView
Set-PSReadLineKeyHandler -Key Tab -Function MenuComplete

$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

## 19. 실행 정책

```powershell
Get-ExecutionPolicy -List
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned       # 권장
powershell -ExecutionPolicy Bypass -File .\script.ps1     # 일회성
Unblock-File .\downloaded.ps1                              # 인터넷 출처 해제
```

## 20. 테스트 (Pester)

```powershell
Install-Module Pester -Force -SkipPublisherCheck

# foo.Tests.ps1
Describe 'Get-Foo' {
    It 'returns OK' {
        Get-Foo -Name X | Should -Be 'OK'
    }
}

Invoke-Pester
Invoke-Pester -Output Detailed
Invoke-Pester -CodeCoverage MyTools.psm1
```
