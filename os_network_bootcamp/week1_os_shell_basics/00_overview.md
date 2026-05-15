# Week 1 — 운영체제 기초 · 셸 입문

## 주차 목표

- 운영체제(OS)가 무엇을 해주는지, **커널·프로세스·스레드·시스템 콜**의 개념을 설명한다
- Linux 파일시스템 구조와 **권한 모델(rwx, suid, umask)** 을 이해한다
- Bash의 **핵심 30개 명령어**와 **파이프·리다이렉션** 을 자유롭게 쓴다
- PowerShell의 **객체 파이프라인**이 Bash와 어떻게 다른지 설명한다
- PowerShell 5.1 vs 7.x 차이와 **실행 정책(Execution Policy)** 을 안다

## 일정표

| Day | 주제 | 핵심 산출물 |
|---|---|---|
| 1 | OS·커널·프로세스·시스템 콜 | `ps`/`Get-Process`로 프로세스 트리 그리기 |
| 2 | Linux 파일시스템·권한·소유권 | suid/sticky bit, umask 시연 가능 |
| 3 | Bash 핵심 명령어 + 파이프·리다이렉션 | `nginx` 로그에서 top 10 IP 추출 |
| 4 | PowerShell 객체 파이프라인 | `Get-Process \| Sort \| Select`로 메모리 톱 5 |
| 5 | PowerShell 핵심 cmdlet + 실행 정책 | `.ps1` 스크립트 작성·서명·실행 |

## 사전 점검

- [ ] WSL2 또는 Ubuntu VM이 동작한다 (`uname -a` 가능)
- [ ] PowerShell 7 설치됨 (`$PSVersionTable.PSVersion`이 7.x)
- [ ] JDK 17+ 설치 (`java -version`)
- [ ] VS Code 또는 IntelliJ 준비

## Java/Spring 개발자를 위한 사전 매핑

| 익숙한 개념 (Java) | 이번 주에 배울 OS 개념 |
|---|---|
| JVM 프로세스 (`java -jar app.jar`) | OS의 프로세스, PID, 부모-자식 관계 |
| `Thread` 클래스 | OS의 스레드, 스레드 vs 프로세스 차이 |
| `new FileInputStream(...)` 실패 시 IOException | 권한(`-rw-`), 시스템 콜 `open()`이 반환하는 errno |
| `System.exit(0)` | exit code, 부모 프로세스가 `wait()`로 수거 |
| Maven/Gradle 환경변수 (`JAVA_HOME`) | 셸의 환경변수, `export`, `.bashrc` |
| Spring `application.yml` 프로파일 | 셸 프로파일 (`.bashrc` vs `.bash_profile`) |

## 본 주차의 실습 환경 준비

```bash
# Linux (WSL2)
sudo apt update
sudo apt install -y curl wget vim htop tree net-tools dnsutils
```

```powershell
# Windows PowerShell 7
$PSVersionTable                    # 버전 확인
Get-Module -ListAvailable | Select Name, Version | Out-String   # 모듈 확인
Install-Module -Name PSReadLine -Force   # 라인 편집기 최신화 (이미 있으면 갱신)
```

## 첫 발걸음

[`01_os_kernel_process.md`](01_os_kernel_process.md) 부터 시작.
