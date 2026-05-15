# Week 4 — 소켓 프로그래밍 · OS 내부 · 캡스톤

## 주차 목표

- Berkeley 소켓 API를 이해하고 Java Socket으로 구현한다
- 블로킹 vs 논블로킹, IO 멀티플렉싱(select/poll/epoll/IOCP), Java NIO를 안다
- Netty의 EventLoop 모델을 이해하고 채팅 서버 구현
- Spring WebClient/RestTemplate의 내부 동작과 커넥션 풀
- OS 성능 분석 도구 (top/htop/vmstat/iostat/perf, Windows의 Performance Monitor/ETW)
- **캡스톤**: 부하 테스트 · 진단 · 보고서

## 일정표

| Day | 주제 | 핵심 산출물 |
|---|---|---|
| 1 | 소켓 API · Java Socket | 블로킹 에코 서버 |
| 2 | IO 멀티플렉싱 · Java NIO | NIO 에코 서버, epoll/IOCP 차이 설명 |
| 3 | Spring/Netty | Netty 채팅 서버, WebClient pool 설정 |
| 4 | OS 성능 분석 | top/perf로 CPU 핫스팟 찾기, htop 해석 |
| 5 | **캡스톤** | 부하 시나리오 + 트러블슈팅 보고서 |

## Java/Spring 개발자를 위한 매핑

| 익숙한 개념 | 이번 주 내용 |
|---|---|
| `new Socket(host, port)` | Berkeley socket(): TCP 연결 |
| `serverSocket.accept()` | accept() 시스템 콜, listen backlog |
| `InputStream.read()` 블로킹 | 커널이 데이터 도착까지 스레드를 sleep |
| `ServerSocketChannel` (Java NIO) | non-blocking + selector (epoll/IOCP/kqueue) |
| Tomcat의 `NIO Connector` | NIO 기반 connector |
| Netty `EventLoopGroup` | epoll/kqueue/IOCP 추상화 + 이벤트 루프 |
| Spring `WebClient` | 비동기 + Netty |
| `top`에서 java가 CPU 200% | 멀티스레드, GC, busy loop |

다음: [`01_socket_api_basics.md`](01_socket_api_basics.md)
