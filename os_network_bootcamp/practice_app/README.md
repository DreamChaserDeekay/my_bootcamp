# practice_app — OS·네트워크 부트캠프 실습 앱

부트캠프 전반에서 사용할 미니 Java/Spring 앱들이 모여 있다.

## 구성

| 모듈 | 위치 | 용도 |
|---|---|---|
| **echo** | `src/main/java/com/example/netlab/echo/` | 블로킹 vs NIO 에코 서버 비교 (Week 4 Day 1, 2) |
| **chat** | `src/main/java/com/example/netlab/chat/` | Netty 기반 채팅 서버 (Week 4 Day 3, Lab 7) |
| **client** | `src/main/java/com/example/netlab/client/` | RestTemplate vs WebClient 풀 비교 (Week 4 Day 3) |
| **diag** | `src/main/java/com/example/netlab/diag/` | 캡스톤용 진단 엔드포인트 |

## 빌드

```bash
cd practice_app
./gradlew build
```

## 실행 (Spring Boot 메인 앱 — diag 포함)

```bash
./gradlew bootRun

# 또는
java -jar build/libs/netlab-1.0.0.jar
```

기본 8080. 엔드포인트:

- `GET /echo?s=hello` — 단순 에코
- `GET /work?ms=50` — 인위 지연
- `GET /cpu?n=1000000` — CPU 부하
- `GET /actuator/health`, `/actuator/metrics`

## 개별 모듈 실행

### Echo 서버 비교

```bash
# 블로킹 (8090)
./gradlew run --args="echo-blocking"

# NIO (8091)
./gradlew run --args="echo-nio"
```

### Netty 채팅 (8081)

```bash
./gradlew run --args="chat"
```

### 클라이언트 부하 비교

```bash
./gradlew run --args="client-restt"   # RestTemplate (풀 없음)
./gradlew run --args="client-pool"    # RestTemplate + Apache HttpClient 5 풀
./gradlew run --args="client-webc"    # WebClient (Reactor Netty)
```

## 스크립트

`scripts/`에 부하 테스트와 측정 보조 스크립트.

```bash
# Linux/WSL
./scripts/stress_test.sh

# Windows
.\scripts\stress_test.ps1
```

## 디렉터리 구조

```
practice_app/
├── README.md
├── build.gradle
├── src/main/
│   ├── java/com/example/netlab/
│   │   ├── NetLabApp.java           ← Spring Boot 메인 (diag 엔드포인트)
│   │   ├── Main.java                ← CLI 디스패처
│   │   ├── echo/
│   │   │   ├── EchoServerBlocking.java
│   │   │   └── EchoServerNio.java
│   │   ├── chat/
│   │   │   └── ChatServer.java
│   │   ├── client/
│   │   │   ├── ClientRest.java
│   │   │   ├── ClientPool.java
│   │   │   └── ClientWebFlux.java
│   │   └── diag/
│   │       └── DiagController.java
│   └── resources/
│       └── application.yml
└── scripts/
    ├── stress_test.sh
    └── stress_test.ps1
```
