# Week 3 자가 점검 체크리스트

## OSI · TCP/IP 모델 (Day 1)

- [ ] OSI 7계층을 처음부터 끝까지 외운다
- [ ] MAC, IP, 포트가 각각 어느 계층의 식별자인지 즉답한다
- [ ] 캡슐화를 그림으로 그릴 수 있다
- [ ] "ping은 가는데 HTTP 안 됨"이 어느 계층 문제인지 즉답한다
- [ ] Java 소켓 코드 한 줄을 보고 어느 계층의 행위인지 매핑한다

## IP · 서브넷 · 라우팅 · NAT (Day 2)

- [ ] `/24`, `/22`, `/16`의 호스트 수를 즉답한다
- [ ] 사설 IP 3대역(`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`)을 외운다
- [ ] 라우팅 테이블을 보고 패킷의 목적지를 추적한다
- [ ] SNAT, DNAT, PAT의 차이를 설명한다
- [ ] PowerShell `Get-NetIPConfiguration`, Linux `ip a`/`ip route`를 능숙하게 쓴다

## TCP · UDP (Day 3)

- [ ] 3-way handshake와 4-way termination을 그린다
- [ ] TIME_WAIT가 왜 2MSL 동안 남는지 두 가지 이유를 댄다
- [ ] CLOSE_WAIT 누적은 앱 측 close 누락임을 안다
- [ ] `ss -tlnp` 출력을 해석한다
- [ ] Java try-with-resources가 CLOSE_WAIT 방지에 중요한 이유를 안다
- [ ] UDP의 8바이트 헤더와 사용처를 안다

## DNS · HTTP · TLS (Day 4)

- [ ] `dig`로 A, AAAA, MX, CNAME, NS를 조회한다
- [ ] HTTP/1.1과 HTTP/2의 핵심 차이를 안다
- [ ] TLS 1.3가 1.2보다 빠른 이유를 설명한다
- [ ] SAN과 CN의 관계, SNI의 역할을 안다
- [ ] `curl -w`로 DNS / TCP / TLS / TTFB 시간을 분리해서 본다
- [ ] `openssl s_client -connect host:443 -servername host`로 인증서를 직접 본다

## 패킷 캡처 · 방화벽 (Day 5)

- [ ] `tcpdump -i any -nn 'host X and port 443'`이 무엇을 캡처할지 즉답
- [ ] Wireshark에서 `tcp.flags.syn == 1` 같은 표시 필터를 작성한다
- [ ] iptables INPUT 체인의 흐름과 룰 순서의 중요성을 안다
- [ ] iptables 변경 전 deadman (`at`)을 거는 습관이 있다
- [ ] PowerShell `New-NetFirewallRule`로 인바운드 룰을 추가했다
- [ ] 캡처의 윤리·법적 경계를 안다

## 종합 실습

- [ ] Lab 5 (캡처 분석): 본인 PC에서 3-way handshake 캡처 + Wireshark 확인
- [ ] Lab 6 (방화벽): 7가지 정책을 iptables/UFW/Windows FW 중 하나로 직접 구현

---

다음: [Week 4 — 소켓 프로그래밍 · OS 내부 · 캡스톤](../week4_socket_capstone/00_overview.md)
