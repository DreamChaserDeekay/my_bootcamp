# Week 3 — TCP/IP · 네트워크 트러블슈팅

## 주차 목표

- OSI 7계층과 TCP/IP 4계층 모델을 머릿속에 명확히 그린다
- IPv4/IPv6, 서브넷, CIDR, 라우팅, NAT를 안다
- TCP의 3-way handshake, 상태머신, 윈도우·혼잡제어를 이해한다
- DNS와 HTTP, TLS의 동작 흐름을 안다
- 패킷 캡처(tcpdump, Wireshark)와 방화벽(iptables, Windows Firewall)으로 운영 장애를 추적한다

## 일정표

| Day | 주제 | 핵심 산출물 |
|---|---|---|
| 1 | OSI 7계층 · TCP/IP 모델 | 한 페이지 비교표 + Java 코드의 어디가 어느 계층인가 |
| 2 | IP · 서브넷 · 라우팅 · NAT | CIDR 계산, 라우팅 테이블 해석, NAT 통과 시나리오 설명 |
| 3 | TCP · UDP 내부 동작 | 3-way handshake 캡처 분석, TIME_WAIT 이해 |
| 4 | DNS · HTTP · TLS | curl로 DNS→TCP→TLS→HTTP 각 단계 분리 디버깅 |
| 5 | 패킷 캡처 · 방화벽 | tcpdump 필터 자유롭게, iptables/Windows FW 규칙 작성 |

## Java/Spring 개발자를 위한 매핑

| 익숙한 개념 | 이번 주 매핑 |
|---|---|
| `URL("https://api.example.com")` | DNS → TCP/IP → TLS → HTTP 전체 스택 |
| `new Socket(host, port)` | TCP 3-way handshake가 일어남 |
| Spring `RestTemplate`/`WebClient` | 연결 풀, keep-alive, TIME_WAIT 누적 |
| Tomcat `acceptCount`, `maxConnections` | `SOMAXCONN`, listen backlog |
| `application.yml`의 `server.address: 0.0.0.0` | bind, 모든 인터페이스 vs 특정 NIC |
| Kubernetes Service의 ClusterIP | NAT, iptables, kube-proxy |

## 사전 점검

- [ ] Week 1, 2 checklist 모두 ✅
- [ ] WSL2 또는 Linux에 `tcpdump`, `iproute2`, `nmap`, `dnsutils`, `iputils-ping` 설치
- [ ] Windows에 Wireshark 설치
- [ ] PowerShell 7 동작

## 윤리 가드레일 ⚠

- 본 주차의 `nmap`, 패킷 캡처는 **본인 소유 또는 명시적 허가받은 시스템에서만**.
- 사내망에서 무차별 스캔은 정책 위반. 캡스톤 실습은 loopback(127.0.0.1)과 본인 PC만 사용.

다음: [`01_osi_tcpip_model.md`](01_osi_tcpip_model.md)
