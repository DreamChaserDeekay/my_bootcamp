# Week 4 자가 점검 체크리스트

## 소켓 API · Java Socket (Day 1)

- [ ] 서버 7단계(socket/bind/listen/accept/read/write/close) 시스템 콜을 그릴 수 있다
- [ ] listen backlog와 `SOMAXCONN`의 관계를 안다
- [ ] SO_REUSEADDR, SO_KEEPALIVE, TCP_NODELAY를 언제 켜는지 안다
- [ ] EOF, RST, half-open을 구별한다
- [ ] try-with-resources의 중요성(CLOSE_WAIT/fd leak 방지) 안다

## IO 멀티플렉싱 · Java NIO (Day 2)

- [ ] 네 가지 IO 모델(블로킹·논블로킹·다중화·비동기)을 구별한다
- [ ] select/poll의 O(N) vs epoll의 O(1) 차이를 설명한다
- [ ] edge-triggered vs level-triggered의 차이를 안다
- [ ] Java NIO `Selector` + `SocketChannel`로 코드를 짤 수 있다
- [ ] `Buffer.flip()`이 왜 필요한지 안다

## Spring · Netty · WebClient (Day 3)

- [ ] Netty의 boss vs worker EventLoop의 역할을 안다
- [ ] EventLoop에서 블로킹 금지의 의미와 회피법을 안다
- [ ] WebClient의 커넥션 풀 옵션을 안다 (maxConnections, maxIdleTime, maxLifeTime)
- [ ] `maxLifeTime`을 두는 이유(NAT/방화벽 idle timeout)를 안다
- [ ] Little's Law로 풀 크기를 추정한다

## OS 성능 분석 (Day 4)

- [ ] USE 메소드의 U/S/E를 안다
- [ ] `top`의 load average, wa, st 컬럼을 해석한다
- [ ] `free`의 `available`이 진짜 가용 메모리임을 안다
- [ ] `iostat -xz`에서 await, %util의 의미를 안다
- [ ] Java가 200% CPU일 때 어느 스레드인지 찾는 절차를 안다
- [ ] 파일 디스크립터 한계가 어디 있는지 안다 (`ulimit -n`, systemd `LimitNOFILE`)

## 캡스톤 (Day 5)

- [ ] 부하 시나리오 설계 (가설 + 메트릭)
- [ ] 기준선 측정 완성 (RPS, p95, CPU, 메모리, 소켓 상태, GC)
- [ ] 계층별 진단 (앱·JVM·소켓·TCP·OS)
- [ ] 적어도 2가지 튜닝 적용 + 재측정
- [ ] 보고서 작성 (`REPORT.md`)

## 전체 부트캠프 졸업 점검

- [ ] Linux 셸로 로그 분석을 한 줄에 한다
- [ ] PowerShell 객체 파이프라인을 자유롭게 쓴다
- [ ] systemd 서비스 파일을 직접 작성한다
- [ ] OSI 7계층 각각의 책임을 즉답한다
- [ ] CIDR 계산, 라우팅 표 해석, NAT 종류를 안다
- [ ] TCP 상태머신 (특히 TIME_WAIT, CLOSE_WAIT)을 안다
- [ ] DNS → TCP → TLS → HTTP 한 사이클을 분리 진단한다
- [ ] tcpdump · Wireshark · iptables · Windows Firewall을 사용한다
- [ ] Java Socket, NIO, Netty, WebClient를 차이점과 함께 설명한다
- [ ] USE 메소드로 한 시스템을 진단한다
- [ ] 캡스톤 보고서를 작성했다

---

축하합니다. 부트캠프 졸업입니다.

다음 단계는 [`../week4_socket_capstone/05_capstone.md`](05_capstone.md) 의 "다음 단계" 섹션 참조.
