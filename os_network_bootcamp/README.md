# 운영체제·셸·네트워크 부트캠프 (OS · Shell · Network Bootcamp)

> **대상**: Java/Spring 개발자 중 OS·네트워크 기초가 약하거나, CLI(Linux/PowerShell)·TCP/IP·소켓을 체계적으로 다지고 싶은 분
> **기간**: 4주 × 5일 = 20 Day (집중 코스) + 캡스톤
> **전제**: Windows 10/11 또는 macOS, WSL2 또는 Docker 사용 가능, JDK 17+, PowerShell 7+

---

## 왜 이 부트캠프인가

> "스프링 코드는 짤 줄 아는데, 막상 운영서버에서 `Connection refused`가 뜨면 무엇부터 봐야 할지 모르겠다."
> "tcpdump 한 번 떠보라는데 옵션을 외운 적이 없다."
> "PowerShell은 cmd처럼 쓰는 게 다였는데, 객체 파이프라인이 뭔지 진짜로 모른다."

이 부트캠프는 **그 격차를 메우기 위한** 4주 집중 학습입니다. 다음 능력을 목표로 합니다.

| 영역 | 졸업 시점 능력 |
|---|---|
| **Linux 셸** | Bash 스크립트로 로그 파싱·배포 자동화·systemd 서비스 작성 가능 |
| **PowerShell** | 객체 파이프라인 이해, 함수·모듈 작성, WinRM 원격 관리 |
| **TCP/IP** | `ss`/`netstat`/`tcpdump`로 운영 장애 원인 추적, 방화벽·NAT 구성 |
| **소켓 프로그래밍** | Java Socket·NIO·Netty 차이를 설명하고 채팅 서버 구현 가능 |
| **OS 내부** | 프로세스·스레드·시그널·메모리·파일디스크립터 동작 이해 |

---

## 학습 흐름

```
Week 1: 운영체제 기초 · 셸 입문 (Bash & PowerShell 동시 학습)
   ↓
Week 2: 셸 마스터리 (Linux 운영 + PowerShell 자동화 심화)
   ↓
Week 3: TCP/IP · 네트워크 트러블슈팅 (패킷부터 방화벽까지)
   ↓
Week 4: 소켓 프로그래밍 · OS 내부 · 캡스톤
```

각 주차는 **5 Day × 약 2~4시간**으로 설계되어 있으며, 평일 저녁이나 주말에 소화 가능합니다.

---

## 디렉토리 구조

```
os_network_bootcamp/
├── README.md                       ← 본 파일
├── week1_os_shell_basics/          ← 운영체제 기초 + Bash/PowerShell 입문
│   ├── 00_overview.md
│   ├── 01_os_kernel_process.md
│   ├── 02_linux_filesystem_permission.md
│   ├── 03_bash_essentials.md
│   ├── 04_powershell_object_pipeline.md
│   ├── 05_powershell_essentials.md
│   ├── labs/
│   │   ├── lab1_wsl_setup.md
│   │   └── lab2_shell_warmup.md
│   └── checklist.md
├── week2_shell_mastery/            ← Linux 운영 + PowerShell 심화
│   ├── 00_overview.md
│   ├── 01_processes_signals_systemd.md
│   ├── 02_text_processing_regex.md
│   ├── 03_bash_scripting.md
│   ├── 04_powershell_scripting.md
│   ├── 05_powershell_remoting.md
│   ├── labs/
│   │   ├── lab3_log_analysis.md
│   │   └── lab4_powershell_automation.md
│   └── checklist.md
├── week3_tcpip_network/            ← TCP/IP 모델 + 트러블슈팅
│   ├── 00_overview.md
│   ├── 01_osi_tcpip_model.md
│   ├── 02_ip_subnet_routing.md
│   ├── 03_tcp_udp_internals.md
│   ├── 04_dns_http_tls.md
│   ├── 05_packet_capture_firewall.md
│   ├── labs/
│   │   ├── lab5_tcpdump_wireshark.md
│   │   └── lab6_firewall_iptables.md
│   └── checklist.md
├── week4_socket_capstone/          ← 소켓 프로그래밍 + OS 성능 + 캡스톤
│   ├── 00_overview.md
│   ├── 01_socket_api_basics.md
│   ├── 02_io_multiplexing_nio.md
│   ├── 03_spring_netty_webclient.md
│   ├── 04_os_performance_tuning.md
│   ├── 05_capstone.md
│   ├── labs/
│   │   └── lab7_chat_server.md
│   └── checklist.md
├── practice_app/                   ← Java/Spring 소켓·네트워크 예제
│   ├── README.md
│   ├── build.gradle
│   ├── src/main/java/com/example/netlab/
│   │   ├── NetLabApp.java
│   │   ├── echo/                   ← 블로킹 vs NIO 에코 서버 비교
│   │   ├── chat/                   ← Netty 기반 채팅 서버
│   │   ├── client/                 ← WebClient/RestTemplate 비교
│   │   └── diag/                   ← 진단용 엔드포인트
│   └── scripts/
│       ├── stress_test.sh
│       └── stress_test.ps1
└── resources/
    ├── tools.md
    ├── books_and_courses.md
    ├── glossary.md
    ├── quick_reference.md          ← 한 페이지 치트시트
    ├── linux_command_cheatsheet.md
    ├── powershell_cheatsheet.md
    └── troubleshooting_playbook.md
```

---

## 사전 준비물

### 1) 공통

- **JDK 17+** (Eclipse Temurin 권장): `java -version`
- **Git**: `git --version`
- **VS Code** 또는 IntelliJ IDEA

### 2) Linux 실습 환경 (택 1)

- **WSL2 + Ubuntu 22.04** (Windows 사용자에게 강력 권장)

  ```powershell
  # 관리자 PowerShell
  wsl --install -d Ubuntu-22.04
  wsl --set-default-version 2
  ```

- **Docker Desktop** (대안): `docker run -it --rm ubuntu:22.04 bash`
- **VirtualBox + Ubuntu ISO** (전통적 가상머신)

### 3) PowerShell 7

```powershell
# winget 사용 (Windows 10 1809+)
winget install --id Microsoft.PowerShell -e
# 또는 직접 다운로드: https://github.com/PowerShell/PowerShell/releases
```

> **주의**: Windows에 기본 설치된 `powershell.exe`는 **Windows PowerShell 5.1**입니다. 본 부트캠프는 **PowerShell 7.x(`pwsh.exe`)** 기준으로 작성하지만, 5.1과의 차이점은 매 주제마다 표기합니다.

### 4) 네트워크 도구

- **Wireshark**: <https://www.wireshark.org/>
- **nmap**: WSL `sudo apt install nmap`, Windows `winget install nmap`
- **curl / wget**: Linux 기본, Windows PS7에는 `curl.exe` 별칭(주의: PS의 `curl`은 `Invoke-WebRequest` 별칭이므로 항상 `curl.exe`로 호출)

---

## 학습 가이드

### 매일의 흐름

1. **개념 문서 읽기 (30~60분)** — 이론 + 코드 예제
2. **실습 (60~120분)** — 직접 명령어 입력, 결과 비교
3. **체크리스트 확인 (10분)** — 그 주의 핵심을 자가점검

### 주차별 마무리

- 주차 끝에 **`checklist.md`** 체크박스 모두 체크 가능해야 다음 주로
- **Week 4 캡스톤**은 최소 2일 이상 투자

### 코드 예제 컨벤션

```bash
# ❌ 위험·비권장 패턴
chmod 777 /etc/shadow

# ✅ 안전·권장 패턴
sudo chmod 640 /etc/shadow
```

```powershell
# ❌ 객체 깨뜨리기 (문자열 강제)
Get-Process | Out-String | Select-String "chrome"

# ✅ 객체 그대로 파이프
Get-Process | Where-Object Name -eq "chrome"
```

---

## 운영체제·환경별 주의

### Windows + WSL2 사용자에게 (대다수 대상)

- WSL2의 Linux는 **별도 IP**를 가집니다. `localhost`는 Windows ↔ WSL 사이에서 자동 포워딩되지만, 외부에서 WSL의 서비스에 접근하려면 별도 설정 필요(Week 3에서 다룸).
- 파일시스템: WSL에서 `/mnt/c/...`로 Windows 디스크 접근 가능하나, **Linux 네이티브 경로(`~/`)에 두면 10배 빠릅니다.**
- 줄바꿈 차이: Windows `CRLF` vs Linux `LF`. Git `core.autocrlf=input` 권장.

### PowerShell 사용자에게

- **5.1 vs 7.x** 차이는 매 챕터에서 명시. 가능하면 7.x 사용.
- 스크립트 실행이 막힐 때: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` (그 영향과 위험은 Week 1에서 설명)
- `&&`, `||`, `?:`, `??`는 **PS 7+ 전용**입니다. 5.1에서는 `; if ($?) { ... }`.

### 보안·윤리 가드레일

- 본 부트캠프의 `nmap`, `tcpdump`, 패킷 캡처 실습은 **자신이 소유하거나 명시적 허가를 받은 네트워크/시스템**에서만 수행하십시오.
- 사내망에서 스캔·캡처 시 사내 보안 정책 위반 가능 — 사전 합의 필수.
- 캡스톤에서 다루는 부하 테스트는 본인 PC의 로컬 환경(loopback)에서만 진행.

---

## 다음 단계 / 심화 경로

이 부트캠프를 마친 뒤 추천 경로:

- **시스템 프로그래밍**: 『Linux Programming Interface』 (Michael Kerrisk)
- **네트워크 심화**: 『TCP/IP Illustrated, Vol. 1』 (Stevens)
- **Netty 심화**: 『Netty in Action』
- **운영·SRE**: Google SRE Book, Brendan Gregg의 『Systems Performance』
- **자격증**: LPIC-1, RHCSA, CCNA, AWS SysOps Administrator

---

## 시작하기

[`week1_os_shell_basics/00_overview.md`](week1_os_shell_basics/00_overview.md) 로 이동하여 1주차를 시작하세요.

---

## 라이선스 / 사용

내부 학습·복습용. 외부 공유 시 출처 표기 권장. 인용된 표준·문서(OWASP, RFC, MSDN, man pages)는 각 페이지의 "더 읽어볼 자료"에 링크.
