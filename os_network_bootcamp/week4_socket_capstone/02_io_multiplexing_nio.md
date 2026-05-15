# Day 2 — IO 멀티플렉싱 · Java NIO

## 한 줄 요약

C10K(1만 동시 연결) 문제는 "스레드 한 개 = 연결 한 개" 모델이 풀 수 없다. 해법은 **한 스레드가 여러 fd를 감시하는 IO 멀티플렉싱**: Linux의 **epoll**, Windows의 **IOCP**, BSD의 **kqueue**. Java NIO는 이를 추상화한 `Selector`를 제공한다.

## 학습 목표

- [ ] 블로킹 / 논블로킹 / 다중화 / 비동기의 4가지 IO 모델을 구별한다
- [ ] **select / poll / epoll / IOCP** 의 차이와 시간 복잡도를 안다
- [ ] Java NIO의 핵심 (`Selector`, `Channel`, `Buffer`)를 안다
- [ ] NIO 기반 에코 서버를 작성한다
- [ ] **edge-triggered vs level-triggered**, **C10M 문제** 키워드를 안다

---

## 1. 네 가지 IO 모델

| 모델 | 설명 | 예 |
|---|---|---|
| **블로킹 (BIO)** | 호출이 데이터 올 때까지 대기 | Java `Socket`, `InputStream.read()` |
| **논블로킹 (NIO)** | 데이터 없으면 즉시 -1/EAGAIN 반환, 폴링 필요 | `fcntl(fd, O_NONBLOCK)` |
| **IO 다중화 (Multiplexing)** | 한 호출로 여러 fd 감시 | select, poll, epoll, IOCP, Java `Selector` |
| **비동기 (AIO)** | OS가 작업 완료 시 콜백 | POSIX AIO, Windows IOCP, Java AIO |

### Reactor 패턴

대부분의 고성능 서버는 **Reactor** 패턴:

```
┌─────────────┐
│  Selector   │  ← 한 스레드가 N개의 fd 감시
│  (epoll)    │
└──────┬──────┘
       │ "fd 7번에 읽을 거 있음"
       ▼
   디스패치 → 핸들러
```

Netty의 `EventLoop`, Node.js의 event loop, nginx의 worker 모두 이 모델.

---

## 2. select / poll / epoll / IOCP

### select (POSIX 표준)

- fd 비트셋을 커널에 매번 복사
- 최대 fd 수 `FD_SETSIZE` (보통 1024)
- 어느 fd가 준비됐는지 알려면 전부 순회 (**O(N)**)
- 1만 연결에서 매우 느림

### poll

- select보다 fd 수 제한 없음 (배열로 전달)
- 여전히 매번 전체 배열 복사 + 순회 (**O(N)**)

### epoll (Linux 2.6+)

- 커널에 fd를 한 번 등록 (`epoll_ctl`) → 이후 `epoll_wait`로 준비된 fd만 가져옴 (**O(1)** 또는 O(이벤트수))
- 1만, 10만 연결도 효율적
- **edge-triggered (ET)** vs **level-triggered (LT)** 두 가지 모드

| | LT | ET |
|---|---|---|
| 알림 시점 | 데이터 있는 한 매번 | 상태 변화 시 한 번 |
| 사용 난이도 | 쉬움 (기본) | 어려움 (다 읽기 전엔 다시 안 옴) |
| 성능 | 보통 | 약간 우수 |

### kqueue (BSD, macOS)

- epoll의 BSD 버전. 더 일반화된 이벤트 시스템.

### IOCP (Windows I/O Completion Port)

- **진짜 비동기**. 커널이 IO 완료 시 큐에 메시지를 넣고 워커 스레드가 가져감.
- Windows에서 고성능 서버는 모두 IOCP 사용 (.NET, libuv, Netty의 Windows)

### 시간 복잡도 비교

| | fd 등록 | 이벤트 대기 |
|---|---|---|
| select | O(1) | O(N) 매번 |
| poll | O(1) | O(N) 매번 |
| **epoll** | O(log N) (한 번) | **O(이벤트 수)** |
| **kqueue** | O(log N) | O(이벤트 수) |
| **IOCP** | O(1) | O(이벤트 수) |

> 1만 fd 중 10개만 active일 때, select/poll은 1만 번 확인, epoll/kqueue/IOCP는 10번만.

---

## 3. Java NIO 핵심

### Channel · Buffer · Selector

```
┌──────────┐      ┌────────┐      ┌──────────┐
│ Channel  │ ←─→  │ Buffer │ ←─→  │ Selector │
└──────────┘      └────────┘      └──────────┘
   (소켓, 파일)    (메모리 영역)     (이벤트 감시자)
```

- **Channel**: NIO의 fd 추상화 (`SocketChannel`, `ServerSocketChannel`, `FileChannel`)
- **Buffer**: 데이터를 담는 메모리 영역 (`ByteBuffer`)
- **Selector**: 여러 channel의 IO 이벤트를 한 스레드에서 감시

### Buffer 동작

```
position │      limit                   capacity
   ↓         ↓                              ↓
[X X X X . . . . . . . . . . . . . . . . . . ]
            ←─ remaining ─→
```

- `put()` → position 증가
- `flip()` → limit = position, position = 0 (읽기 모드로 전환)
- `clear()` → position=0, limit=capacity (쓰기 모드)
- `compact()` → 안 읽은 데이터를 앞으로 이동

```java
ByteBuffer buf = ByteBuffer.allocate(1024);
channel.read(buf);          // 쓰기 (소켓 → 버퍼)
buf.flip();                 // 읽기 모드
while (buf.hasRemaining()) {
    System.out.print((char) buf.get());
}
buf.clear();                // 다시 쓰기 모드
```

> 가장 흔한 NIO 버그는 `flip()` 빼먹기. 데이터 흘러가는 방향 잘 따라가야.

---

## 4. NIO 에코 서버

```java
// EchoServerNio.java
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public class EchoServerNio {
    public static void main(String[] args) throws IOException {
        int port = 8080;

        Selector selector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(port));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Listening on " + port);
        ByteBuffer buf = ByteBuffer.allocate(1024);

        while (true) {
            selector.select();      // 블로킹: 이벤트 올 때까지
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();

                if (!key.isValid()) continue;

                if (key.isAcceptable()) {
                    SocketChannel client = ((ServerSocketChannel) key.channel()).accept();
                    client.configureBlocking(false);
                    client.register(selector, SelectionKey.OP_READ);
                    System.out.println("Connected: " + client.getRemoteAddress());
                }

                if (key.isReadable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    buf.clear();
                    int n;
                    try {
                        n = client.read(buf);
                    } catch (IOException e) {
                        // 강제 종료 (RST)
                        n = -1;
                    }
                    if (n == -1) {
                        // EOF
                        System.out.println("Disconnected: " + client.getRemoteAddress());
                        client.close();
                        continue;
                    }
                    buf.flip();
                    // 에코
                    ByteBuffer reply = ByteBuffer.allocate(buf.remaining() + 6);
                    reply.put("ECHO: ".getBytes());
                    reply.put(buf);
                    reply.flip();
                    client.write(reply);
                }
            }
        }
    }
}
```

### 핵심 포인트

- **단일 스레드**가 수천 연결 처리
- `configureBlocking(false)` 가 핵심
- `selector.select()`이 epoll/IOCP를 호출
- 이벤트 처리 후 `it.remove()` 안 하면 같은 이벤트 또 처리됨 (흔한 버그)

### 한계

- 한 핸들러가 오래 걸리면 **다른 모든 연결 블록**
- → 워커 풀 분리 또는 Netty의 EventLoop 다수 사용

---

## 5. Java NIO.2 (AIO) — 진짜 비동기

```java
import java.nio.channels.*;
import java.util.concurrent.*;

AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open()
    .bind(new InetSocketAddress(8080));

server.accept(null, new CompletionHandler<>() {
    @Override
    public void completed(AsynchronousSocketChannel client, Object attachment) {
        server.accept(null, this);    // 다음 accept 등록
        ByteBuffer buf = ByteBuffer.allocate(1024);
        client.read(buf, buf, new CompletionHandler<>() {
            @Override
            public void completed(Integer n, ByteBuffer b) {
                b.flip();
                client.write(b);
            }
            @Override
            public void failed(Throwable t, ByteBuffer b) { }
        });
    }
    @Override
    public void failed(Throwable t, Object attachment) { }
});
```

> 실무에서 직접 쓰는 일은 드묾. Netty가 더 편함.

---

## 6. C10K · C10M

- **C10K**: 한 서버에서 1만 동시 연결 — 1999년 Dan Kegel이 제기. epoll/IOCP로 해결.
- **C10M**: 1천만 동시 연결 — 더 어려움. 커널 우회(DPDK, kernel bypass), 사용자공간 TCP 스택(mTCP), 또는 분산 클러스터.

> 일반 앱에서 C10M까지 갈 일은 거의 없음. C10K도 클러스터로 수평확장 하는 게 보통.

---

## 7. ❌ 위험 / ✅ 안전

### Selector의 `it.remove()` 누락

```java
// ❌ 같은 이벤트가 무한 반복
while (it.hasNext()) {
    SelectionKey key = it.next();
    // ... 처리
    // it.remove() 빠짐 — 다음 select에서 또 이 이벤트가 옴
}

// ✅
while (it.hasNext()) {
    SelectionKey key = it.next();
    it.remove();
    // ... 처리
}
```

### NIO에서 read 결과 처리

```java
int n = channel.read(buf);
// n > 0: 데이터 읽음
// n == 0: 데이터 없음 (논블로킹에서 정상)
// n == -1: EOF, 클라이언트 close()
```

EOF를 무시하면 무한루프 + CPU 100%.

### NIO 코드는 본질적으로 복잡함

직접 작성 후 1000줄 넘어가면 **Netty로 갈아탈 때**. Netty는 위 모든 함정을 흡수.

---

## 8. 실습

### Step 1: 위 EchoServerNio 띄우고 부하 시험

```bash
javac EchoServerNio.java
java EchoServerNio &

# 동시 연결 1000개
for i in $(seq 1 1000); do
    (echo "msg $i" | nc -q 1 localhost 8080) &
done
wait
```

블로킹 버전과 메모리·스레드 수 비교:

```bash
ps -p $(pgrep -f EchoServerNio) -o pid,vsz,rss,nlwp
# nlwp: 스레드 수 — NIO는 적음
```

### Step 2: strace로 epoll_wait 확인

```bash
strace -p $(jps | grep EchoServerNio | awk '{print $1}') -e 'epoll_*' 2>&1 | head -20
# epoll_create1, epoll_ctl, epoll_wait가 보임
```

### Step 3: blocking vs NIO 처리량 비교

```bash
# JMeter 또는 wrk
wrk -t 10 -c 1000 -d 30s http://localhost:8080/    # NIO 서버에 HTTP라 안 맞을 수 있음
```

차이를 메모리·CPU·동시 연결 측면에서 정리.

### Step 4: epoll_wait이 시간 절약하는 모습

```bash
# select 사용하는 옛 코드(예: nc -k)
strace -c -e select,read,write nc -l -k 8080 &
# 같은 클라이언트로 부하

# epoll 사용 코드 (대부분의 현대 데몬)
strace -c -e epoll_wait,read,write nginx-master &
```

`time` 컬럼이 epoll에서 훨씬 적게 나옴.

---

## 더 읽어볼 자료

- 📘 『Java NIO』 (Ron Hitchens) — NIO 이해의 고전
- 📘 『The Linux Programming Interface』 Ch. 63 (Alternative I/O Models)
- 🔗 Dan Kegel, "The C10K problem": <http://www.kegel.com/c10k.html>
- 🔗 `man 7 epoll`
- 🔗 IOCP 개요: <https://learn.microsoft.com/windows/win32/fileio/i-o-completion-ports>
- 🎓 Robert Engels, "Building scalable network apps": <https://www.javaperformance.com/>

---

## 자가 점검

- [ ] 네 가지 IO 모델(블로킹·논블로킹·다중화·비동기)을 구별한다
- [ ] select/poll의 O(N) 한계와 epoll의 O(1) 차이를 설명한다
- [ ] edge-triggered vs level-triggered의 차이를 안다
- [ ] Java NIO `Selector` + `SocketChannel`로 코드를 짤 수 있다
- [ ] `Buffer.flip()`이 왜 필요한지 안다
- [ ] `it.remove()` 빼먹으면 무슨 일이 일어나는지 안다

다음: [`03_spring_netty_webclient.md`](03_spring_netty_webclient.md)
