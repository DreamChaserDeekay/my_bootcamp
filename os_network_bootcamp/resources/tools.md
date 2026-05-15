# 도구 목록 (Tools)

부트캠프 전반에서 등장한 도구를 카테고리별로 정리. 운영 도구상자.

## 1. 셸·텍스트 처리

| 도구 | 용도 | 설치 |
|---|---|---|
| **bash** | Linux 기본 셸 | 기본 포함 |
| **zsh** | 강력한 셸 (macOS 기본) | `apt install zsh` |
| **PowerShell 7** | Windows + cross-platform | `winget install Microsoft.PowerShell` |
| **tmux** | 터미널 멀티플렉서 | `apt install tmux` |
| **screen** | tmux 이전 표준 | 기본 포함 |
| **fzf** | 퍼지 파인더 | `apt install fzf` |
| **bat** | cat의 syntax highlighting | `apt install bat` (Ubuntu는 `batcat`) |
| **ripgrep (rg)** | 빠른 grep | `apt install ripgrep` |
| **fd** | 빠른 find | `apt install fd-find` |
| **jq** | JSON 처리 | `apt install jq` |
| **yq** | YAML 처리 | `pip install yq` |
| **miller (mlr)** | CSV/TSV/JSON 변환 | `apt install miller` |
| **shellcheck** | bash 정적분석 | `apt install shellcheck` |
| **PSScriptAnalyzer** | PowerShell 정적분석 | `Install-Module PSScriptAnalyzer` |
| **PSReadLine** | PS 라인 편집기 | 7+ 기본, `Install-Module PSReadLine` |

## 2. 시스템·프로세스

| 도구 | 용도 |
|---|---|
| **htop / btop** | top의 시각화 |
| **ps / pidof / pgrep / pkill** | 프로세스 조작 |
| **strace** | 시스템 콜 추적 |
| **ltrace** | 라이브러리 콜 추적 |
| **bpftrace** | eBPF 기반 추적 (커널 4.9+) |
| **lsof** | 열린 파일·소켓 |
| **fuser** | 어느 프로세스가 파일을 잡고 있나 |
| **systemctl / journalctl** | systemd 제어·로그 |
| **dmesg** | 커널 메시지 |
| **uptime / w / who / last** | 사용자·로드 |
| **Process Explorer** (Win) | Sysinternals, htop 등가 |
| **Process Monitor** (Win) | strace 등가 |

## 3. 메모리·CPU·디스크

| 도구 | 용도 |
|---|---|
| **free / vmstat** | 메모리 추세 |
| **slabtop** | 커널 slab 캐시 |
| **mpstat / sar** | CPU 통계 (sysstat 패키지) |
| **pidstat** | 프로세스별 시계열 |
| **iostat** | 디스크 IO |
| **iotop** | 프로세스별 IO |
| **perf** | CPU 핫스팟 분석 |
| **async-profiler** | Java 전용, 매우 강력 |
| **Performance Monitor (perfmon)** (Win) | 시계열 카운터 |
| **PerfView** (Win) | .NET 분석 |

## 4. 네트워크

| 도구 | 용도 |
|---|---|
| **ping** | L3 도달성 |
| **traceroute / mtr / tracert** | L3 경로 |
| **dig / nslookup / drill** | DNS |
| **Resolve-DnsName** (Win) | DNS |
| **nc (netcat) / ncat** | 만능 소켓 도구 |
| **socat** | nc + 토네이도 |
| **curl / wget** | HTTP 클라이언트 |
| **Invoke-WebRequest / Invoke-RestMethod** (Win) | curl 등가 |
| **httpie** | curl의 친화 버전 |
| **ss** | netstat의 후계 |
| **netstat** | 옛것, 여전히 보임 |
| **Get-NetTCPConnection** (Win) | ss 등가 |
| **tcpdump** | CLI 패킷 캡처 |
| **tshark** | Wireshark CLI |
| **Wireshark** | GUI 패킷 분석 |
| **nmap** | 포트·서비스 스캔 |
| **iperf3** | 대역폭 측정 |
| **ethtool** | NIC 통계 |
| **iftop / nload / nethogs / bmon** | 인터페이스·프로세스별 트래픽 |

## 5. 방화벽

| 도구 | 용도 |
|---|---|
| **iptables** | Linux 패킷 필터 (전통) |
| **nftables** | iptables 후계 |
| **ufw** | Ubuntu 친화 래퍼 |
| **firewalld** | RHEL/CentOS 기본 |
| **New-NetFirewallRule** (Win) | Windows Defender Firewall |

## 6. 부하 테스트

| 도구 | 특징 |
|---|---|
| **wrk** | 가장 가볍고 빠름. C 작성. 단순 시나리오에 최적 |
| **wrk2** | wrk의 정확한 RPS 제어 |
| **ab (Apache Bench)** | 옛것, 한계 있음 |
| **hey** | wrk의 Go 버전 |
| **k6** | JavaScript 시나리오. 운영서 인기 |
| **JMeter** | Java GUI, 풍부한 옵션. 무겁다 |
| **Gatling** | Scala DSL, 시각화 |
| **Locust** | Python |
| **vegeta** | Go, 정확한 attack rate |
| **hping3** | 저수준 패킷 (SYN flood 시뮬레이션) |

## 7. JVM 분석

| 도구 | 용도 |
|---|---|
| **jps** | JVM 프로세스 목록 |
| **jstack** | 스레드 덤프 |
| **jcmd** | 만능 진단 (heap, threads, JFR, NMT) |
| **jstat** | GC 통계 |
| **jmap** | 힙 덤프 (구식, jcmd 권장) |
| **JFR (Java Flight Recorder)** | 무거운 분석 없이 항상 켤 수 있음 |
| **JMC (JDK Mission Control)** | JFR 분석 GUI |
| **VisualVM** | 실시간 모니터 |
| **async-profiler** | flame graph |
| **Eclipse MAT** | 힙 덤프 분석 |

## 8. 컨테이너·가상화

| 도구 | 용도 |
|---|---|
| **Docker / Podman** | 컨테이너 |
| **WSL2** | Windows에서 Linux |
| **VirtualBox / VMware** | VM |
| **Multipass** | 가벼운 Ubuntu VM |

## 9. SSH·원격

| 도구 | 용도 |
|---|---|
| **OpenSSH** | SSH 클라이언트·서버 |
| **mosh** | 지연·끊김에 강한 SSH |
| **sshfs** | SSH로 원격 디렉터리 마운트 |
| **PowerShell Remoting** | WinRM/SSH |
| **Ansible** | 선언적 다중 호스트 관리 |
| **GNU parallel** | 명령 병렬 실행 |

## 10. 모니터링·관측

| 도구 | 용도 |
|---|---|
| **Prometheus** | 메트릭 수집 |
| **Grafana** | 시각화 |
| **Loki** | 로그 집계 |
| **Elasticsearch + Kibana** | 로그 분석 |
| **Jaeger / Zipkin** | 분산 트레이싱 |
| **Datadog / New Relic** | SaaS APM |
| **Sentry** | 에러 트래킹 |

## 11. 보안 진단

| 도구 | 용도 |
|---|---|
| **openssl** | TLS, 인증서, 암호 |
| **nmap + scripts** | 포트·취약점 스캔 |
| **nikto** | 웹 취약점 |
| **gobuster / ffuf** | 디렉터리 브루트포스 |
| **Burp Suite** | 웹 프록시 |
| **OWASP ZAP** | 무료 웹 스캐너 |

## 12. 클라우드 CLI

| 도구 | 용도 |
|---|---|
| **aws** | AWS CLI |
| **gcloud** | GCP CLI |
| **az** | Azure CLI |
| **kubectl** | Kubernetes |
| **terraform** | IaC |
| **helm** | k8s 패키지 |
