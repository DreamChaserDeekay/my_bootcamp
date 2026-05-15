# Lab 7 — Netty 채팅 서버 + 클라이언트

## 목표

1. Day 3에서 본 Netty 채팅 서버를 띄운다
2. 여러 클라이언트로 채팅 동작 확인
3. 부하 줘서 메모리·스레드 수·CPU 측정
4. 같은 기능을 블로킹 IO로 구현하고 비교

---

## 1. Netty 채팅 서버 (Day 3 §1)

`practice_app/src/main/java/com/example/netlab/chat/ChatServer.java` 참고.

빌드·실행:

```bash
cd practice_app
./gradlew bootRun        # 또는 java -jar build/libs/...
```

기본 8081에 채팅 서버.

---

## 2. 다중 클라이언트로 테스트

여러 터미널에서:

```bash
nc localhost 8081
> Alice: hello
< [client1: joined]
< Alice: hello
```

다른 터미널:

```bash
nc localhost 8081
> Bob: hi
< [client2: joined]
< Alice: hello
< Bob: hi
```

브로드캐스트가 모든 접속자에게 가는지 확인.

---

## 3. 부하 시뮬레이션

```bash
# 1000 동시 접속, 각각 5초간 메시지 보냄
for i in $(seq 1 1000); do
    (
        coproc nc localhost 8081
        for j in 1 2 3 4 5; do
            echo "user$i msg $j" >&${COPROC[1]}
            sleep 0.1
        done
        sleep 1
        exec {COPROC[1]}>&-
    ) &
done
wait
echo "Done"
```

서버에서:

```bash
# 메모리·스레드 측정
JPID=$(jps | grep ChatServer | awk '{print $1}')
ps -p $JPID -o pid,vsz,rss,nlwp,pcpu
jcmd $JPID Thread.print | grep -c '"' # 스레드 수
jcmd $JPID GC.heap_info
```

기록:

| 동시 접속 | RSS | 스레드 수 | CPU% |
|---|---|---|---|
| 0 | _ | _ | _ |
| 100 | _ | _ | _ |
| 500 | _ | _ | _ |
| 1000 | _ | _ | _ |

Netty의 스레드 수는 거의 변하지 않음 (EventLoop는 CPU * 2 정도).

---

## 4. 블로킹 IO로 같은 기능 구현

```java
// ChatServerBlocking.java
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServerBlocking {
    static final Set<PrintWriter> clients = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(8082);
        ExecutorService pool = Executors.newCachedThreadPool();
        System.out.println("Blocking chat on 8082");
        while (true) {
            Socket s = server.accept();
            pool.submit(() -> handle(s));
        }
    }

    static void handle(Socket s) {
        try (s;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            clients.add(out);
            broadcast("[" + s.getRemoteSocketAddress() + " joined]");
            String line;
            while ((line = in.readLine()) != null) {
                broadcast(s.getRemoteSocketAddress() + ": " + line);
            }
            clients.remove(out);
            broadcast("[" + s.getRemoteSocketAddress() + " left]");
        } catch (IOException e) {
            // ignore
        }
    }

    static void broadcast(String msg) {
        for (PrintWriter c : clients) c.println(msg);
    }
}
```

실행 후 같은 부하:

| 동시 접속 | RSS | 스레드 수 | CPU% |
|---|---|---|---|
| 0 | _ | _ | _ |
| 100 | _ | _ | _ |
| 500 | _ | _ | _ |
| 1000 | _ | _ | _ |

블로킹 버전은 **연결당 1 스레드** → 1000 연결이면 1000 스레드. 메모리 ↑↑.

---

## 5. 결과 비교

| | Netty (NIO) | Blocking |
|---|---|---|
| 1000 동시 접속 메모리 | ~150MB | ~1GB |
| 스레드 수 | 8 | 1000 |
| CPU | ~40% | ~60% |
| 코드 줄 수 | 80 | 40 |

Netty는 작성은 약간 복잡하지만 자원 효율은 훨씬 좋음.

---

## 6. 가상 스레드로 (JDK 21+)

```java
// ChatServerVirtual.java — Blocking 코드를 그대로, Thread만 가상 스레드
public class ChatServerVirtual {
    static final Set<PrintWriter> clients = ConcurrentHashMap.newKeySet();
    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(8083);
        while (true) {
            Socket s = server.accept();
            Thread.ofVirtual().start(() -> handle(s));     // ← 변경 한 줄
        }
    }
    static void handle(Socket s) { /* 위와 동일 */ }
    static void broadcast(String msg) { /* 위와 동일 */ }
}
```

같은 부하:

| | Netty | Blocking | Virtual Threads |
|---|---|---|---|
| 메모리 | ~150MB | ~1GB | ~200MB |
| 코드 | 복잡 | 단순 | 단순 |

가상 스레드: Netty 수준 자원, 블로킹 코드 단순성. JDK 21+ 권장.

---

## 7. 회고

- Netty 코드가 복잡하지만 EventLoop 모델을 이해하면 다른 reactive 시스템도 이해된다 (Node.js, libuv, asyncio)
- 가상 스레드는 게임 체인저. 새 프로젝트는 Reactive 대신 가상 스레드 + 블로킹 IO 고려
- 블로킹 모델은 동시 연결 수가 적은 사내 도구 정도라면 여전히 OK

다음: [`../checklist.md`](../checklist.md)
