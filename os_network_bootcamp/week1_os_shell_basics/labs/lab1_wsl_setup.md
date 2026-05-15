# Lab 1 — WSL2 / Docker 실습 환경 셋업

## 목표

본 부트캠프의 Linux 실습을 위한 WSL2 + Ubuntu 22.04, Docker, 필수 도구를 설치하고 동작을 확인한다.

## 소요 시간

약 30~60분 (네트워크 속도에 따라)

---

## 1. WSL2 설치 (Windows 10 1903+ / 11)

### 1) 한 줄 설치

관리자 PowerShell:

```powershell
wsl --install
# 재부팅 후 Ubuntu 자동 설치 + 사용자 계정 생성
```

이미 일부 설치되어 있다면:

```powershell
wsl --install -d Ubuntu-22.04
wsl --set-default-version 2
wsl --update              # WSL 커널 업데이트
wsl --status
```

### 2) 설치 확인

```powershell
wsl -l -v
#   NAME            STATE           VERSION
# * Ubuntu-22.04    Running         2
```

### 3) WSL 진입

```powershell
wsl                       # 기본 배포판
wsl -d Ubuntu-22.04
```

WSL 안에서:

```bash
uname -a
cat /etc/os-release
# Ubuntu 22.04.x LTS
```

---

## 2. WSL Ubuntu 초기 설정

```bash
sudo apt update && sudo apt upgrade -y

sudo apt install -y \
    curl wget vim git tmux htop tree \
    net-tools dnsutils iputils-ping iproute2 \
    tcpdump nmap jq unzip zip \
    build-essential gdb strace ltrace \
    python3 python3-pip
```

### Java 17 (Eclipse Temurin)

```bash
sudo apt install -y wget apt-transport-https gnupg
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install -y temurin-17-jdk
java -version
```

---

## 3. WSL ↔ Windows 상호 운용

| 작업 | 방법 |
|---|---|
| Linux 파일을 Windows에서 보기 | 탐색기 주소창에 `\\wsl$\Ubuntu-22.04\home\<user>` |
| Windows 파일을 Linux에서 보기 | `/mnt/c/Users/<you>/` |
| Linux에서 Windows 명령 실행 | `cmd.exe /c dir`, `notepad.exe file.txt`, `code .` |
| Windows에서 WSL 명령 실행 | `wsl ls /tmp`, `wsl -e bash -c 'echo $HOME'` |
| VS Code로 WSL 폴더 열기 | WSL에서 `code .` (VS Code Remote-WSL 자동 설치) |

### 성능 주의 ⚠

```bash
# ❌ 느림: Windows 디스크 경유
cd /mnt/c/Users/me/work
git clone <repo>            # 매우 느림

# ✅ 빠름: Linux 네이티브 디스크
cd ~
git clone <repo>            # 10배 이상 빠름
```

---

## 4. Docker Desktop (선택)

WSL2 백엔드 사용 권장.

```powershell
winget install Docker.DockerDesktop
# 또는 https://www.docker.com/products/docker-desktop/
```

Docker Desktop 설치 후 **Settings → General → "Use WSL 2 based engine"** 체크.

WSL에서:

```bash
docker --version
docker run --rm hello-world
```

---

## 5. PowerShell 7 (pwsh) 설치

```powershell
winget install --id Microsoft.PowerShell -e
```

`pwsh` 실행 후:

```powershell
$PSVersionTable
# PSVersion 7.x.x
```

### PSReadLine 갱신

```powershell
Install-Module PSReadLine -Force -SkipPublisherCheck
```

---

## 6. Wireshark (네트워크 캡처용, Week 3에서 사용)

```powershell
winget install WiresharkFoundation.Wireshark
```

WSL에서는 Linux 캡처가 가능하지만, GUI는 Windows의 Wireshark를 쓰는 편이 편함. `tcpdump`로 캡처한 `.pcap`을 Windows로 옮겨 열면 됨.

```bash
# WSL에서 캡처
sudo tcpdump -i any -w /tmp/capture.pcap -c 100 port 80

# Windows에서 열기
explorer.exe /tmp/capture.pcap     # WSL 경로 자동 변환
```

---

## 7. 동작 확인 체크리스트

```bash
# Linux
uname -a
cat /etc/os-release
java -version
git --version
docker --version 2>/dev/null || echo "docker (optional)"
which tcpdump nmap jq
```

```powershell
# PowerShell 7
$PSVersionTable.PSVersion
java -version
git --version
docker --version 2>$null
```

모두 출력되면 환경 셋업 완료.

---

## 트러블슈팅

### WSL이 안 켜질 때

```powershell
# Hyper-V / Virtual Machine Platform 켜기
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
# 재부팅

# BIOS에서 가상화 (Intel VT-x / AMD-V) 활성화 확인
```

### WSL 디스크 용량이 안 줄어들 때

WSL의 ext4.vhdx는 한 번 커지면 자동으로 안 줄어듦. 압축:

```powershell
wsl --shutdown
# %LOCALAPPDATA%\Packages\CanonicalGroupLimited.Ubuntu*\LocalState\ext4.vhdx
diskpart
> select vdisk file="C:\Users\<you>\AppData\Local\Packages\...\LocalState\ext4.vhdx"
> attach vdisk readonly
> compact vdisk
> detach vdisk
> exit
```

### CRLF 충돌

```bash
git config --global core.autocrlf input    # WSL 권장
```

---

## 다음

[`lab2_shell_warmup.md`](lab2_shell_warmup.md) — 간단한 셸 워밍업 챌린지
