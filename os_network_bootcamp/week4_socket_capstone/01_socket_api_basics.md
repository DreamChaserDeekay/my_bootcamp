# Day 1 — 소켓 API · Java Socket

## 한 줄 요약

**Berkeley 소켓 API**는 네트워크 통신의 표준 인터페이스. Java의 `Socket`/`ServerSocket`은 이걸 객체로 감싼 것일 뿐, 그 아래는 `socket()`, `bind()`, `listen()`, `accept()`, `connect()`, `send()`/`recv()`, `close()` 시스템 콜이 있다. 이 7개를 알면 모든 소켓 코드가 보이기 시작한다.

## 학습 목표

- [ ] 서버·클라이언트 소켓 흐름을 시스템 콜 시퀀스로 그린다
- [ ] **listen backlog**의 의미와 `SOMAXCONN`을 안다
- [ ] **`SO_REUSEADDR`**, **`SO_KEEPALIVE`**, **`TCP_NODELAY`** 의 의미와 언제 켜는지 안다
- [ ] Java로 블로킹 에코 서버를 작성한다
- [ ] 클라이언트가 끊겼을 때(EOF, RST) 어떻게 감지하는지 안다

---

## 1. Berkeley 소켓 흐름

### 서버

```
   socket()         소켓 fd 생성 (가족=AF_INET, 종류=SOCK_STREAM=TCP)
        │
   bind()           IP:port 할당 (0.0.0.0:8080 또는 127.0.0.1:8080)
        │
   listen(backlog)  연결 대기 큐 만들기 (accept queue)
        │
   accept()         ── 새 연결 fd 받기 (블로킹)
        │           ↑
   read/write       │
        │           │ 반복 (스레드/이벤트 루프)
   close(client)    ─┘
        ▼
   close(server)
```

### 클라이언트

```
   socket()
        │
   connect(server)   3-way handshake
        │
   write/read
        │
   close()
```

### 시스템 콜 그대로

```c
// 서버 (C, 간단화)
int s = socket(AF_INET, SOCK_STREAM, 0);
bind(s, (struct sockaddr*)&addr, sizeof addr);
listen(s, 128);
for (;;) {
    int c = accept(s, NULL, NULL);
    handle(c);
    close(c);
}
```

Java로:

```java
ServerSocket server = new ServerSocket(8080);
while (true) {
    Socket client = server.accept();
    handle(client);
    client.close();
}
```

---

## 2. listen backlog와 SOMAXCONN

`listen(fd, backlog)` 의 `backlog`는 **accept queue 크기**. 클라이언트가 SYN을 보내고 서버가 SYN-ACK로 응답한 뒤 ACK까지 완료된 연결들이 여기 쌓인다. 앱이 accept()를 안 부르면 큐가 차고 → 새 SYN을 무시(=클라이언트 연결 거부).

```bash
# 시스템 한계 확인
cat /proc/sys/net/core/somaxconn
# 4096 (Linux 5.x 기본)

# 늘리기
sudo sysctl -w net.core.somaxconn=65535
```

> **Spring Boot/Tomcat 운영 팁**: `server.tomcat.accept-count` (기본 100). 트래픽 폭주 시 이게 작으면 connection refused. 다만 너무 크면 메모리만 먹고 효과 없음 — 진짜 해법은 워커 스레드/풀.

```yaml
server:
  tomcat:
    accept-count: 100        # backlog
    max-connections: 8192    # 동시 연결 한계
    threads:
      max: 200               # 워커 스레드
```

---

## 3. 소켓 옵션

| 옵션 | 의미 | 언제 켜나 |
|---|---|---|
| **SO_REUSEADDR** | 같은 (IP:port)를 TIME_WAIT 동안에도 bind 허용 | 서버 재시작 시 "Address already in use" 회피 |
| **SO_REUSEPORT** (Linux 3.9+) | 여러 프로세스가 같은 port 동시 bind | 멀티 프로세스 로드밸런싱 |
| **SO_KEEPALIVE** | 유휴 시 주기적으로 keepalive 패킷 | 장수 명 연결 (DB 풀) |
| **TCP_NODELAY** | Nagle 알고리즘 끔 (작은 패킷도 즉시 송신) | 인터랙티브 (SSH, 게임, RPC) |
| **SO_RCVBUF / SO_SNDBUF** | 수신/송신 버퍼 크기 | 고대역폭에서 윈도우 확대 |
| **SO_LINGER** | close 시 보낼 데이터 처리 시간 | 보통 기본 (안 만짐) |
| **TCP_CORK** | 작은 데이터 모아 보내기 (반대 Nagle) | 큰 응답을 한 번에 |

Java로:

```java
ServerSocket s = new ServerSocket();
s.setReuseAddress(true);             // SO_REUSEADDR
s.bind(new InetSocketAddress(8080), 128);

Socket c = new Socket();
c.setKeepAlive(true);                // SO_KEEPALIVE
c.setTcpNoDelay(true);               // TCP_NODELAY
c.setSoTimeout(30000);               // read 타임아웃 30초
c.connect(new InetSocketAddress("example.com", 80), 5000);  // connect 타임아웃 5초
```

### Nagle vs TCP_NODELAY — 흔한 함정

- 기본: Nagle 켜짐 → 작은 데이터를 모아서 보냄 (대역폭 효율)
- 인터랙티브 앱(RPC, 채팅)에서는 응답성 ↓
- **TCP_NODELAY**를 켜면 즉시 송신

> Netty, gRPC 등은 기본으로 `TCP_NODELAY=true`. 자체 소켓 코드에서도 RPC라면 켤 것.

### Keepalive 튜닝

```bash
# Linux 기본 (매우 김 — 2시간 후 처음 probe)
cat /proc/sys/net/ipv4/tcp_keepalive_time     # 7200
cat /proc/sys/net/ipv4/tcp_keepalive_intvl    # 75
cat /proc/sys/net/ipv4/tcp_keepalive_probes   # 9
# → 2시간 + 75초 * 9회 = 약 2시간 11분 만에 끊김 감지

# 운영 권장 (방화벽이 NAT 타임아웃 30분이라 가정)
sudo sysctl -w net.ipv4.tcp_keepalive_time=600     # 10분 idle 후 시작
sudo sysctl -w net.ipv4.tcp_keepalive_intvl=30
sudo sysctl -w net.ipv4.tcp_keepalive_probes=5
```

---

## 4. Java Socket으로 에코 서버

```java
// EchoServerBlocking.java
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class EchoServerBlocking {
    public static void main(String[] args) throws IOException {
        int port = 8080;
        ExecutorService pool = Executors.newFixedThreadPool(50);

        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port), 128);
        System.out.println("Listening on " + port);

        while (true) {
            Socket client = server.accept();
            pool.submit(() -> handle(client));
        }
    }

    static void handle(Socket client) {
        String remote = client.getRemoteSocketAddress().toString();
        System.out.println("Connected: " + remote);
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream()), true)) {

            client.setSoTimeout(60_000);     // 1분 idle 타임아웃
            String line;
            while ((line = in.readLine()) != null) {     // null이면 EOF
                out.println("ECHO: " + line);
            }
            System.out.println("Disconnected: " + remote);
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout: " + remote);
        } catch (SocketException e) {
            // RST 등 — Connection reset
            System.out.println("Reset: " + remote);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

### 동작 확인

```bash
# 컴파일·실행
javac EchoServerBlocking.java
java EchoServerBlocking &

# 다른 터미널: nc로 클라이언트
nc localhost 8080
> hello
< ECHO: hello
> world
< ECHO: world
^C
```

### 클라이언트 끊김 감지

| 클라이언트 동작 | 서버의 read() | 의미 |
|---|---|---|
| `close()` 정상 | `readLine()`이 `null` 반환 | EOF (4-way 정상 종료) |
| `Ctrl+C` (RST) | `SocketException: Connection reset` | RST 수신 |
| 네트워크 끊김 (Wi-Fi 꺼짐 등) | 읽기 무한 대기 → SoTimeout 또는 keepalive로 감지 | half-open |

> **운영 교훈**: 명시적 idle 타임아웃(`setSoTimeout`)이나 keepalive 없이 long-lived 소켓을 쓰면 위 마지막 케이스가 영원히 멈춰있을 수 있다.

---

## 5. 블로킹 모델의 한계

위 코드는 **연결 1개에 스레드 1개**. 50 스레드 풀이면 동시 50 연결까지.

| 항목 | 비용 |
|---|---|
| 스레드 한 개 | 기본 스택 1MB (Linux), 메모리만 50MB |
| 컨텍스트 스위치 | 마이크로초 단위, 1만 스레드면 의미있는 오버헤드 |
| 동시 처리 한계 | 보통 수천~수만 (앱 별, OS 별) |

C10K 문제 (1만 동시 연결)를 해결하려면 → **논블로킹 + IO 멀티플렉싱** (다음 Day).

### 그래도 블로킹이 좋을 때

- 동시 연결 수가 적음 (수백 미만)
- 코드 단순성이 중요
- Java의 **가상 스레드(Project Loom, JDK 21+)** — 블로킹 코드를 그대로 두고 수만 동시 연결 가능
- Spring Boot 3.2+ + JDK 21 + `spring.threads.virtual.enabled=true`

---

## 6. 가상 스레드 미리보기 (JDK 21+)

```java
// EchoServerVirtual.java
import java.io.*;
import java.net.*;

public class EchoServerVirtual {
    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(8080);
        while (true) {
            Socket client = server.accept();
            Thread.ofVirtual().start(() -> handle(client));     // ← 가상 스레드
        }
    }
    static void handle(Socket client) { /* 위와 동일 */ }
}
```

가상 스레드는 OS 스레드 1만 개 같은 한계 없이 수십만 개 가능. 블로킹 IO를 호출하면 JVM이 OS 스레드를 양보. 이전의 reactive 모델 없이도 같은 처리량.

---

## 7. 실습

### Step 1: 위 EchoServerBlocking 띄우고 nc로 시험

```bash
javac EchoServerBlocking.java
java EchoServerBlocking &
nc localhost 8080
```

### Step 2: 시스템 콜로 확인

```bash
# 서버의 PID 알아내고
JPID=$(jps -l | grep EchoServer | awk '{print $1}')

# accept, read, write가 일어나는 시스템 콜 추적
strace -p $JPID -e accept,read,write,close -f
```

### Step 3: 동시 연결 100개 시뮬레이션

```bash
for i in $(seq 1 100); do
    (echo "hello $i" | nc -q 1 localhost 8080) &
done
wait
```

서버 로그에서 100개 모두 받았는지 확인.

### Step 4: 옵션 실험

```java
// TCP_NODELAY 켜고/끄고 비교 — 작은 메시지의 RTT 측정
client.setTcpNoDelay(true);
// vs
client.setTcpNoDelay(false);
```

WireShark로 보면 Nagle에서는 작은 메시지가 묶여서 한 번에 송신됨.

### Step 5: backlog 실험

```java
// 매우 작은 backlog로 시도
ServerSocket s = new ServerSocket();
s.bind(new InetSocketAddress(8080), 2);    // backlog 2
// accept 안 부르고 sleep
Thread.sleep(60000);
```

그 사이 클라이언트 10개 동시 연결 시도 → 일부는 거부 또는 timeout.

---

## 8. ❌ 위험 / ✅ 안전

```java
// ❌ 스트림 close 안 함 → CLOSE_WAIT
Socket c = server.accept();
InputStream is = c.getInputStream();
// 작업 ...
// is.close() 또는 c.close() 호출 누락 — 특히 예외 경로

// ✅ try-with-resources
try (Socket c = server.accept();
     InputStream is = c.getInputStream()) {
    // ...
}
```

```java
// ❌ accept 무한 루프에서 single thread — 한 연결이 오래 끌면 다른 클라이언트 못 받음
while (true) {
    Socket c = server.accept();
    handle(c);    // 같은 스레드에서 처리
}

// ✅ 스레드 풀 또는 가상 스레드
ExecutorService pool = Executors.newFixedThreadPool(50);
while (true) {
    Socket c = server.accept();
    pool.submit(() -> handle(c));
}
```

```java
// ❌ SO_TIMEOUT 없이 read — 클라이언트 멈추면 영원히 블록
String line = reader.readLine();

// ✅
client.setSoTimeout(30_000);
String line = reader.readLine();
```

---

## 더 읽어볼 자료

- 📘 『UNIX Network Programming, Vol. 1』 (W. Richard Stevens) — 소켓의 성서
- 📘 『The Linux Programming Interface』 Ch. 56~61 (Sockets)
- 🔗 `man 7 socket`, `man 7 tcp`
- 🔗 Java SE Documentation — `java.net.Socket`
- 🔗 Brian Goetz, "Virtual Threads": <https://openjdk.org/jeps/444>

---

## 자가 점검

- [ ] 서버 소켓의 7단계(socket/bind/listen/accept/read/write/close) 시스템 콜을 그릴 수 있다
- [ ] listen backlog가 무엇이고 SOMAXCONN이 어디인지 안다
- [ ] SO_REUSEADDR, SO_KEEPALIVE, TCP_NODELAY를 언제 켜는지 안다
- [ ] EOF, RST, half-open을 구별한다
- [ ] try-with-resources가 왜 중요한지 (CLOSE_WAIT, fd leak) 안다

다음: [`02_io_multiplexing_nio.md`](02_io_multiplexing_nio.md)
