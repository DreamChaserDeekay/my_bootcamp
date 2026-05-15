# 용어집 (Glossary)

부트캠프 전체에서 등장한 용어. 한국어 + 영어 병기, 필요시 비유.

## A

- **ACK (Acknowledgment)** — TCP에서 수신 확인. seq 번호로 무엇까지 받았는지 알림
- **ACL (Access Control List)** — 자원에 대한 권한 목록. Linux의 9비트 권한보다 세밀
- **ALPN (Application-Layer Protocol Negotiation)** — TLS 핸드셰이크 중 HTTP/2 등 협상
- **ARP (Address Resolution Protocol)** — IPv4 ↔ MAC 매핑
- **AsyncIO** — 비동기 IO. Linux IO_uring, Windows IOCP

## B

- **BPF (Berkeley Packet Filter)** — 패킷 필터링 가상머신. tcpdump의 필터에 사용
- **eBPF** — 확장 BPF, 커널 내부 안전 프로그래밍. 추적·관측의 미래
- **Backlog** — accept queue 크기. `listen(fd, backlog)`
- **Buffer** (NIO) — 데이터를 담는 메모리 영역. `position/limit/capacity`

## C

- **CIDR (Classless Inter-Domain Routing)** — `192.168.1.0/24` 표기법
- **CGI / FastCGI** — 웹 서버와 외부 프로세스의 통신 규약 (옛것)
- **CLOSE_WAIT** — TCP 상태. 상대가 close 후 내가 close 안 한 상태. **앱 버그 신호**
- **CN (Common Name)** — 인증서 주체. SAN으로 대체됨
- **Context Switching** — 프로세스/스레드 전환. CPU 시간 비용
- **CredSSP** — PowerShell Remoting의 자격증명 위임. 위험
- **CRLF** — Windows 줄바꿈 `\r\n`. Linux는 LF (`\n`)

## D

- **DNS (Domain Name System)** — 이름 → IP 변환. UDP/53, 큰 응답은 TCP
- **DNAT (Destination NAT)** — 포트포워딩 방향의 NAT
- **DPDK** — Data Plane Development Kit. 커널 우회 고성능 네트워크
- **Daemon** — 백그라운드 서비스 프로세스
- **dig** — DNS 도구. nslookup의 후계

## E

- **epoll** — Linux의 IO 다중화. O(1)
- **EOF (End of File)** — 입력 종료. `read()`의 -1 또는 `readLine()`의 null
- **ESTABLISHED** — TCP 상태. 양방향 통신 가능
- **EventLoop** — 단일 스레드가 N개 fd의 이벤트를 처리하는 패턴

## F

- **fd (File Descriptor)** — Linux의 자원 핸들. 소켓·파일·파이프 모두 fd
- **FHS (Filesystem Hierarchy Standard)** — Linux 디렉터리 구조 표준
- **FIN** — TCP 종료 시그널 비트
- **fork()** — 프로세스 복제 시스템 콜

## G

- **GC (Garbage Collection)** — JVM의 메모리 회수. 종류: G1, ZGC, Parallel
- **glob** — 셸 와일드카드 (`*`, `?`, `[...]`)

## H

- **Heap Dump** — JVM 힙 메모리 스냅샷. MAT로 분석
- **Heredoc** — `<<EOF ... EOF` 셸의 인라인 입력
- **HOL Blocking (Head-of-Line)** — 큐의 앞 요소가 나머지를 막음. HTTP/1.1과 TCP에서 발생
- **HSTS (HTTP Strict Transport Security)** — 강제 HTTPS 헤더
- **HTTP/2** — 멀티플렉싱·헤더 압축. TCP 위
- **HTTP/3** — QUIC(UDP) 위. HOL blocking 해소

## I

- **ICMP** — IP 위의 제어 프로토콜. ping이 사용
- **IOCP (I/O Completion Port)** — Windows의 진짜 비동기 IO
- **iptables** — Linux 패킷 필터. nftables로 이전 중
- **ISN (Initial Sequence Number)** — TCP handshake에서 시작 seq 번호

## J

- **JCMD** — JVM 진단 명령. heap/thread/JFR 등
- **JFR (Java Flight Recorder)** — JVM 내장 프로파일러
- **journalctl** — systemd 로그 도구

## K

- **kqueue** — BSD/macOS의 IO 다중화. epoll 등가
- **Kernel Space** — 권한 있는 메모리 영역. 커널만 접근

## L

- **Little's Law** — `concurrency = throughput × response_time`. 풀 크기 결정
- **listen()** — 서버 소켓을 연결 대기 상태로
- **Load Average** — Linux 부하 지표 (1/5/15분). 코어 수와 비교

## M

- **MAC Address** — 데이터링크 계층 식별자. 48 bit
- **MSL (Maximum Segment Lifetime)** — TCP 세그먼트 수명. 보통 30초
- **MSS (Maximum Segment Size)** — TCP 한 세그먼트 데이터 최대. 보통 1460
- **MTU (Maximum Transmission Unit)** — 이더넷 프레임 최대. 보통 1500

## N

- **NAT (Network Address Translation)** — IP 변환. SNAT/DNAT/PAT
- **Netty** — Java 비동기 네트워크 프레임워크
- **NIC (Network Interface Card)** — 네트워크 카드
- **NIO (Non-blocking IO)** — Java의 IO 다중화 API
- **nl** (PowerShell) — `Get-NetTCPConnection`의 별칭은 없지만 자주 쓰임
- **nslookup** — DNS 도구 (옛것, dig 권장)
- **Nagle Algorithm** — 작은 패킷 모으기. TCP_NODELAY로 끔

## O

- **OCSP** — 인증서 폐기 확인. Stapling으로 가속
- **OOM (Out Of Memory)** — Linux 커널이 메모리 부족 시 프로세스 죽임
- **OSI 7계층** — 학문적 네트워크 모델

## P

- **PAT (Port Address Translation, Masquerade)** — 여러 사설 IP를 하나의 공인 IP로
- **PCRE (Perl Compatible Regular Expressions)** — 가장 강력한 정규식 방언
- **PID (Process ID)** — 프로세스 식별자
- **PowerShell Remoting** — WinRM 또는 SSH 전송으로 원격 PS 실행
- **PSDrive** — PowerShell의 자원 추상화 (파일·레지스트리·환경변수)
- **process block** (PowerShell) — 파이프 입력 처리 블록

## Q

- **QUIC** — Google이 만든 UDP 기반 신뢰성 프로토콜. HTTP/3의 기반

## R

- **Reactor Pattern** — 단일 스레드 이벤트 루프 모델
- **Reactive Streams** — 비동기 backpressure 표준 (Reactor, RxJava)
- **RSS (Resident Set Size)** — 프로세스의 실제 물리 메모리 사용량
- **RST** — TCP 강제 종료 비트
- **RTT (Round Trip Time)** — 패킷 왕복 시간

## S

- **SAN (Subject Alternative Name)** — 인증서의 도메인 목록. CN 대체
- **Selector** (Java NIO) — 여러 채널 이벤트 감시자
- **Signal** — Linux 프로세스 간 비동기 메시지 (SIGTERM, SIGKILL...)
- **SIGTERM** — 우아한 종료 요청 (kill 기본)
- **SIGKILL** — 강제 종료 (9). 막을 수 없음
- **SNI (Server Name Indication)** — TLS에서 호스트명 명시
- **SO_REUSEADDR / SO_KEEPALIVE / TCP_NODELAY** — 소켓 옵션
- **SOMAXCONN** — Linux 시스템 백로그 한계
- **strace** — 시스템 콜 추적
- **suid / sgid / sticky bit** — Linux 특수 권한
- **systemd** — 현대 Linux init 시스템

## T

- **TCP Window** — 흐름 제어. 수신/혼잡 윈도우
- **tcpdump** — CLI 패킷 캡처
- **TIME_WAIT** — TCP 상태. active close 측 2MSL 대기
- **TLS (Transport Layer Security)** — SSL의 후계. 암호·인증·무결성
- **trap** (bash) — 시그널/EXIT 핸들러
- **try-with-resources** (Java) — 자동 close

## U

- **UDP** — 무신뢰 데이터그램 프로토콜
- **UFW (Uncomplicated Firewall)** — Ubuntu 친화적 iptables 래퍼
- **umask** — 새 파일의 권한 차감 마스크
- **USE Method** — Brendan Gregg의 성능 분석 (Utilization, Saturation, Errors)

## V

- **vmstat** — 메모리·CPU·IO 추세
- **Virtual Threads** — JDK 21+의 경량 스레드 (Project Loom)

## W

- **WebClient** (Spring) — Reactive HTTP 클라이언트. Netty 기반
- **WebFlux** (Spring) — Reactive 웹 스택
- **WinRM** — Windows Remote Management. PowerShell Remoting의 기본 전송
- **WSL2** — Windows에서 Linux 커널 실행

## X

- **X.509** — 인증서 표준 형식

## Z

- **Zombie process** — 종료했지만 부모가 wait() 안 한 프로세스
- **zero-copy** — 사용자 공간 복사 없이 데이터 전송. `sendfile()`, `transferTo()`
