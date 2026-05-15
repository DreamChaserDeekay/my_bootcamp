# Day 5 — PowerShell Remoting · WinRM · SSH · JEA

## 한 줄 요약

PowerShell Remoting은 **원격 머신에서 cmdlet을 그대로 실행하고 결과를 객체로 받아오는** 메커니즘이다. WinRM이 전통 전송, PowerShell 7부터는 SSH로도 가능. 수십~수백 대 서버에 명령을 병렬로 흘리고, JEA로 권한을 제약된 명령으로만 위임할 수 있다.

## 학습 목표

- [ ] **WinRM** 활성화 방법과 보안 의미를 안다
- [ ] `Enter-PSSession`(대화형) vs `Invoke-Command`(일회성/병렬) vs `New-PSSession`(영구 세션) 차이를 안다
- [ ] PowerShell 7의 **SSH 기반 Remoting** 셋업
- [ ] **CredSSP·Kerberos·HTTPS** 인증의 차이와 권장
- [ ] **JEA (Just Enough Administration)** 의 개념과 셋업
- [ ] Linux도 함께 다루는 환경에서 PowerShell ↔ SSH 비교

---

## 1. WinRM 기초

**WinRM(Windows Remote Management)**: HTTP/HTTPS 기반의 Windows 원격 관리 프로토콜 (포트 5985/5986). PowerShell Remoting의 기본 전송 계층.

### 활성화

```powershell
# 관리자 PowerShell — 원격에서 받을 쪽(서버)
Enable-PSRemoting -Force
# 자동으로 다음을 수행:
# - WinRM 서비스 시작
# - 부팅 시 자동 시작 설정
# - 방화벽 규칙 (Public 프로파일 제외)
# - SDDL 기본 권한 (Administrators 그룹)

# 확인
Test-WSMan
Test-WSMan -ComputerName SERVER01
Get-Service WinRM
```

### 트러스트 호스트 (도메인 외)

```powershell
# 클라이언트 쪽 — 도메인 외 서버에 연결할 때만 필요
Set-Item WSMan:\localhost\Client\TrustedHosts -Value 'SERVER01,SERVER02' -Force
# 또는 모두 신뢰 (위험!)
Set-Item WSMan:\localhost\Client\TrustedHosts -Value '*' -Force

Get-Item WSMan:\localhost\Client\TrustedHosts
```

> ⚠ **`TrustedHosts *`는 절대 금지** — 모든 호스트를 신뢰하는 의미. 사고가 났을 때 변명이 안 됨.

---

## 2. 원격 세션 3가지

### Enter-PSSession — 대화형 (SSH 비슷)

```powershell
Enter-PSSession -ComputerName SERVER01 -Credential (Get-Credential)
# [SERVER01]: PS C:\>
Get-Service Spooler
# 모든 명령이 원격에서 실행됨
Exit-PSSession
```

### Invoke-Command — 일회성, 병렬

```powershell
# 한 대
Invoke-Command -ComputerName SERVER01 -ScriptBlock { Get-Process }

# 여러 대 — 자동 병렬
Invoke-Command -ComputerName @('SRV01','SRV02','SRV03') -ScriptBlock {
    Get-Service Spooler | Select Name, Status
}
# 결과는 PSComputerName 속성이 추가되어 어느 서버에서 왔는지 알 수 있음

# 스크립트 파일을 원격에서 실행
Invoke-Command -ComputerName SERVER01 -FilePath .\Audit.ps1

# 로컬 변수 전달
$threshold = 100
Invoke-Command -ComputerName SERVER01 -ScriptBlock {
    param($t)
    Get-Process | Where WS -gt ($t * 1MB)
} -ArgumentList $threshold

# 또는 PS 7 신문법
Invoke-Command -ComputerName SERVER01 -ScriptBlock {
    Get-Process | Where WS -gt ($using:threshold * 1MB)
}
# $using: 으로 로컬 변수 참조
```

### New-PSSession — 영구 세션 (여러 명령 + 상태 유지)

```powershell
$sess = New-PSSession -ComputerName SRV01, SRV02 -Credential (Get-Credential)

Invoke-Command -Session $sess -ScriptBlock { $temp = "something" }
Invoke-Command -Session $sess -ScriptBlock { $temp }   # 이전 변수 유지

# 파일 복사 (PSSession 통해 — 방화벽 우회 가능)
Copy-Item -Path C:\local.zip -ToSession $sess -Destination C:\remote.zip
Copy-Item -FromSession $sess -Path C:\remote.log -Destination C:\local.log

Remove-PSSession $sess
```

---

## 3. 인증 방법

| 방식 | 포트 | 암호화 | 사용처 |
|---|---|---|---|
| **Negotiate (Kerberos/NTLM)** | 5985 HTTP | 메시지 레벨 | 도메인 내부 기본 |
| **Basic** | 5985/5986 | HTTPS 필수 | 비도메인, 권장 X |
| **HTTPS (TLS)** | 5986 | 채널 레벨 | 도메인 외부, 권장 |
| **CredSSP** | 5985 | + 자격증명 위임 | "이중 홉" 필요 시 (주의) |
| **Kerberos** | 5985 | | 도메인 환경 권장 |
| **Certificate** | 5986 | | 무인증 가능 |

### HTTPS 리스너 만들기 (권장)

```powershell
# 서버에서 — 자체서명 인증서 (실제는 도메인 CA 사용)
$cert = New-SelfSignedCertificate -DnsName "server01.example.com" -CertStoreLocation Cert:\LocalMachine\My
winrm create winrm/config/Listener?Address=*+Transport=HTTPS '@{Hostname="server01.example.com"; CertificateThumbprint="'+$cert.Thumbprint+'"}'

# 방화벽
New-NetFirewallRule -DisplayName 'WinRM HTTPS' -Direction Inbound -Protocol TCP -LocalPort 5986 -Action Allow

# 클라이언트에서 — 인증서 신뢰 후
Invoke-Command -ComputerName server01.example.com -UseSSL -Credential (Get-Credential) -ScriptBlock { hostname }
```

### CredSSP의 위험

CredSSP는 **사용자 자격증명을 원격 서버에 그대로 위임**한다. 원격 서버가 침해되면 자격증명이 통째로 새어나간다. "이중 홉(double hop)" 문제(원격 A에서 다시 다른 B에 접속) 외에는 사용하지 말 것.

**대안**:

- Kerberos delegation (제한된 위임)
- PowerShell의 `-Authentication Kerberos` + Constrained Delegation
- 더 좋은 방식: **JEA**로 미리 정의된 작업만 수행

---

## 4. PowerShell 7 SSH 기반 Remoting (cross-platform)

PowerShell 7부터는 SSH를 전송 계층으로 사용 가능. **Windows ↔ Windows뿐 아니라 Linux ↔ Windows ↔ Linux** 가능.

### 서버 측 설정 (Linux 또는 Windows)

```bash
# Linux Ubuntu
sudo apt install -y openssh-server powershell

# sshd_config 수정
sudo vim /etc/ssh/sshd_config
# 다음 한 줄 추가
# Subsystem powershell /usr/bin/pwsh -sshs -NoLogo

sudo systemctl restart sshd
```

```powershell
# Windows 서버
# OpenSSH Server 설치
Get-WindowsCapability -Online | Where Name -like 'OpenSSH.Server*' | Add-WindowsCapability -Online
Start-Service sshd
Set-Service sshd -StartupType Automatic

# C:\ProgramData\ssh\sshd_config 편집
# Subsystem  powershell  C:/Program Files/PowerShell/7/pwsh.exe -sshs -NoLogo
Restart-Service sshd
```

### 클라이언트에서 사용

```powershell
Enter-PSSession -HostName user@server01.example.com
Invoke-Command -HostName user@server01,user@server02 -ScriptBlock { Get-Process }
$sess = New-PSSession -HostName user@server01
```

### SSH vs WinRM

| | WinRM | SSH |
|---|---|---|
| 플랫폼 | Windows 중심 | Cross-platform |
| 포트 | 5985/5986 | 22 |
| 키 인증 | 인증서 옵션 | 표준 SSH key |
| 도메인 통합 | 매끄러움 (Kerberos) | 직접 키 관리 |
| 방화벽 친화 | 별도 룰 | 보통 이미 열려 있음 |
| 추천 | Windows-only 환경 | 혼합/cross-platform |

---

## 5. 병렬 실행과 작업 관리

### Invoke-Command -ThrottleLimit

```powershell
$servers = 1..50 | ForEach-Object { "srv$('{0:D2}' -f $_)" }
Invoke-Command -ComputerName $servers -ThrottleLimit 10 -ScriptBlock {
    Get-Service Spooler | Select Status
}
# 동시 10대씩 실행
```

### -AsJob (백그라운드)

```powershell
$job = Invoke-Command -ComputerName $servers -AsJob -ScriptBlock {
    Get-CimInstance Win32_LogicalDisk |
        Where DriveType -eq 3 |
        Select DeviceID, @{N='FreeGB';E={[int]($_.FreeSpace/1GB)}}
}

# 진행 확인
Get-Job
Receive-Job $job -Keep | Format-Table

# 완료 대기
$job | Wait-Job | Receive-Job
```

### PS 7: ForEach-Object -Parallel

```powershell
$servers = Get-Content servers.txt
$servers | ForEach-Object -Parallel {
    Invoke-Command -ComputerName $_ -ScriptBlock { hostname; (Get-Service spooler).Status }
} -ThrottleLimit 20
```

---

## 6. JEA (Just Enough Administration)

전체 admin 권한을 주지 않고, **미리 허용한 cmdlet/매개변수만** 실행 가능하게 하는 구성. 운영의 보안 핵심.

### 흐름

1. **역할 능력(Role Capability) `.psrc`** — 허용 cmdlet/함수/매개변수 정의
2. **세션 구성(Session Configuration) `.pssc`** — 누가 어떤 역할로 접속하나
3. 서버에 등록 → 사용자는 그 엔드포인트에 접속하면 제한된 셸 받음

### 1) 역할 능력 작성

```powershell
# C:\Modules\WebOpsTools\RoleCapabilities\WebOps.psrc
New-PSRoleCapabilityFile -Path C:\Modules\WebOpsTools\RoleCapabilities\WebOps.psrc `
    -VisibleCmdlets `
        'Get-Service', `
        @{ Name='Restart-Service'; Parameters=@{ Name='Name'; ValidateSet='W3SVC','MyApp' } }, `
        'Get-EventLog' `
    -VisibleFunctions 'Get-DiskUsage' `
    -VisibleAliases 'gs','rs'
```

### 2) 세션 구성

```powershell
New-PSSessionConfigurationFile -Path C:\JEA\WebOps.pssc `
    -SessionType RestrictedRemoteServer `
    -RunAsVirtualAccount `
    -RoleDefinitions @{
        'CONTOSO\WebOpsTeam' = @{ RoleCapabilities = 'WebOps' }
    } `
    -TranscriptDirectory 'C:\JEA-Transcripts'
```

### 3) 등록

```powershell
Register-PSSessionConfiguration -Path C:\JEA\WebOps.pssc -Name 'WebOps' -Force
Restart-Service WinRM
```

### 4) 사용자 접속

```powershell
Enter-PSSession -ComputerName SERVER01 -ConfigurationName WebOps -Credential (Get-Credential)
# 이제 Get-Service, Restart-Service (Name=W3SVC,MyApp만), Get-EventLog만 가능
# Remove-Item, Stop-Computer 등은 안 보임
```

### 감사 (transcript)

`TranscriptDirectory`에 모든 명령·출력이 기록되어, 누가 무엇을 했는지 사후 추적 가능. 운영 보안 의 핵심.

---

## 7. Linux와 함께 — SSH 명령 동시 실행

PowerShell이 아닌 Linux도 같이 보면:

```bash
# Bash + GNU Parallel
parallel -j 10 ssh {} 'hostname; uptime' ::: srv01 srv02 srv03

# Ansible (선언적)
ansible all -i hosts -m shell -a 'systemctl status nginx'

# 단순한 for
for h in $(cat hosts); do ssh "$h" 'hostname'; done
```

### PowerShell ↔ Linux 동시 통제 시나리오

```powershell
# 윈도우 서버 (WinRM)
$winServers = 'WIN-SRV01','WIN-SRV02'
$winResult = Invoke-Command -ComputerName $winServers -ScriptBlock { hostname }

# Linux 서버 (SSH)
$linuxServers = 'user@lnx-srv01','user@lnx-srv02'
$linuxResult = Invoke-Command -HostName $linuxServers -ScriptBlock { hostname }

$all = $winResult + $linuxResult
$all | Sort PSComputerName
```

---

## 8. ❌ 위험 / ✅ 안전

### TrustedHosts 무한 신뢰

```powershell
# ❌ 모든 호스트 신뢰
Set-Item WSMan:\localhost\Client\TrustedHosts -Value '*'

# ✅ 구체적 호스트 또는 와일드카드 한정
Set-Item WSMan:\localhost\Client\TrustedHosts -Value '*.internal.example.com'
```

### HTTP로 자격증명

```powershell
# ❌ HTTP + Basic 인증 — 자격증명이 네트워크에 평문 흐름 가능
Invoke-Command -ComputerName srv01 -UseSSL:$false -Credential $c -Authentication Basic

# ✅ HTTPS 또는 Kerberos
Invoke-Command -ComputerName srv01.example.com -UseSSL -Credential $c
```

### 무차별 -AsJob

```powershell
# ❌ 동시 500대 -- 메모리 폭발, 타겟 서버 throttle
Invoke-Command -ComputerName $bigList -AsJob -ScriptBlock { ... }

# ✅ ThrottleLimit으로 동시성 제어
Invoke-Command -ComputerName $bigList -ThrottleLimit 20 -AsJob -ScriptBlock { ... }
```

### CredSSP 남용

```powershell
# ❌ 모든 곳에 CredSSP — 자격증명 위임 함부로
Enable-WSManCredSSP -Role Client -DelegateComputer '*'

# ✅ JEA + Constrained Delegation
```

---

## 9. 실습

### Step 1: 로컬 루프백 Remoting

WSL 또는 두 번째 PC가 없어도 같은 PC로 테스트 가능.

```powershell
# 관리자 PowerShell
Enable-PSRemoting -Force

# 자기 자신에 접속
Enter-PSSession -ComputerName localhost
hostname
Exit-PSSession

Invoke-Command -ComputerName localhost -ScriptBlock { Get-Process | Sort WS -Desc | Select -First 3 Name, Id, WS }
```

### Step 2: SSH-기반 Remoting (Linux WSL 활용)

```powershell
# Windows에서 WSL에 SSH로 PowerShell 접속
# 1) WSL에서 pwsh 설치
wsl -d Ubuntu-22.04 bash -c "sudo apt install -y powershell openssh-server"

# 2) WSL sshd_config에 Subsystem powershell 추가
wsl -d Ubuntu-22.04 bash -c "echo 'Subsystem powershell /usr/bin/pwsh -sshs -NoLogo' | sudo tee -a /etc/ssh/sshd_config"
wsl -d Ubuntu-22.04 sudo service ssh restart

# 3) WSL의 IP 확인
$wslIp = (wsl hostname -I).Trim()

# 4) PowerShell 7에서 접속
Enter-PSSession -HostName "user@$wslIp"
```

### Step 3: 여러 서버 풀링

가상 호스트 목록을 만들어 병렬 정보 수집:

```powershell
$hosts = 'localhost' * 5     # 5번 localhost 호출
Invoke-Command -ComputerName $hosts -ThrottleLimit 3 -ScriptBlock {
    [PSCustomObject]@{
        Host = $env:COMPUTERNAME
        Date = Get-Date
        ProcessCount = (Get-Process).Count
        FreeMemGB = [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB, 2)
    }
}
```

### Step 4: 간단한 JEA 셋업

위 §6의 단계를 실제 수행해보고, 일반 사용자로 접속하여 제한된 명령만 실행 가능한지 확인.

---

## 더 읽어볼 자료

- 📘 『PowerShell Remoting in Depth』 (Don Jones, Tobias Weltner)
- 📘 『Secrets of PowerShell Remoting』 (무료: <https://devops-collective-inc.gitbook.io/secrets-of-powershell-remoting/>)
- 🔗 about_Remote, about_Remote_FAQ
- 🔗 PowerShell SSH Remoting: <https://learn.microsoft.com/powershell/scripting/learn/remoting/ssh-remoting-in-powershell>
- 🔗 JEA 개요: <https://learn.microsoft.com/powershell/scripting/learn/remoting/jea/overview>

---

## 자가 점검

- [ ] `Enter-PSSession`, `Invoke-Command`, `New-PSSession`의 차이를 한 줄씩 설명한다
- [ ] `-ThrottleLimit`을 안 쓰면 무슨 문제가 생기는지 안다
- [ ] CredSSP의 위험 두 가지를 안다
- [ ] PS 7에서 SSH 기반 Remoting을 셋업한 적 있다
- [ ] JEA의 목적을 한 문장으로 설명한다 (least privilege를 위한 제한된 셸)

이번 주 마무리:

- [`labs/lab3_log_analysis.md`](labs/lab3_log_analysis.md)
- [`labs/lab4_powershell_automation.md`](labs/lab4_powershell_automation.md)
- [`checklist.md`](checklist.md)
