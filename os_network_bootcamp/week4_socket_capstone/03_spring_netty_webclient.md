# Day 3 — Spring · Netty · WebClient

## 한 줄 요약

직접 NIO를 다루는 일은 드물다. 실무에서는 **Netty**(또는 Vert.x)가 모든 함정을 흡수해주고, **Spring WebFlux/WebClient**가 그 위에서 Reactive API를 제공한다. 이번 시간은 그 추상화의 구조와 운영자가 알아야 할 옵션(특히 **커넥션 풀**)을 본다.

## 학습 목표

- [ ] Netty의 EventLoop, Channel, Pipeline, Handler 모델을 안다
- [ ] Spring WebFlux와 WebClient 흐름을 안다
- [ ] WebClient의 **커넥션 풀** 설정을 한다
- [ ] RestTemplate의 한계와 풀 설정 (Apache HttpClient 5)
- [ ] 부하 테스트로 풀 크기 결정 가이드

---

## 1. Netty 핵심 개념

### EventLoopGroup

```
boss(EventLoopGroup, 1~2개 스레드)    worker(EventLoopGroup, CPU * 2)
       │                                    │
   ServerSocketChannel.accept()             │
       │ → 새 Channel 생성                  │
       └────────── 등록 ─────────────────►  │
                                            │ Channel의 모든 이벤트
                                            │ (read/write)를 처리
                                            ▼
                                     ChannelPipeline
                                     (Handler 체인)
```

- **boss EventLoop**: accept만 담당 (보통 1개로 충분)
- **worker EventLoop**: 각 채널의 IO를 담당. 한 채널은 평생 한 worker에 고정.
- 하나의 EventLoop은 **단일 스레드** → 데이터 경쟁 X. 그래서 핸들러는 단일 스레드 가정.

### ChannelPipeline · ChannelHandler

```
Inbound  ──> HandlerA ──> HandlerB ──> HandlerC ──>  (final)
Outbound <── HandlerZ <── HandlerY <── HandlerX <──
```

각 핸들러는 디코더(바이트 → 객체) 또는 인코더(객체 → 바이트), 비즈니스 로직.

### 간단한 채팅 서버 (Netty)

```java
// ChatServer.java
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.*;
import io.netty.handler.codec.string.*;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.nio.charset.StandardCharsets;

public class ChatServer {
    static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public static void main(String[] args) throws InterruptedException {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 128)
             .childOption(ChannelOption.SO_KEEPALIVE, true)
             .childOption(ChannelOption.TCP_NODELAY, true)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new LineBasedFrameDecoder(1024));
                     p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                     p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                     p.addLast(new ChatHandler());
                 }
             });
            ChannelFuture f = b.bind(8081).sync();
            System.out.println("Chat on 8081");
            f.channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    static class ChatHandler extends SimpleChannelInboundHandler<String> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            channels.add(ctx.channel());
            broadcast(ctx, "[" + ctx.channel().remoteAddress() + " joined]");
        }
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            broadcast(ctx, "[" + ctx.channel().remoteAddress() + " left]");
        }
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            broadcast(ctx, ctx.channel().remoteAddress() + ": " + msg);
        }
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
        private void broadcast(ChannelHandlerContext ctx, String msg) {
            for (Channel ch : channels) {
                ch.writeAndFlush(msg + "\n");
            }
        }
    }
}
```

```bash
# Maven 또는 Gradle 의존성 추가 (Gradle 예)
# implementation 'io.netty:netty-all:4.1.115.Final'

# 실행 후 nc 클라이언트 여러 개 띄워서 채팅 확인
nc localhost 8081
```

---

## 2. EventLoop의 황금 규칙

> **EventLoop 위에서 블로킹하지 말 것.**

Netty가 1ms 안에 끝나야 하는 worker 위에서 `Thread.sleep(5000)`이나 DB 쿼리(블로킹) 하면 다른 채널이 멈춤.

```java
// ❌ EventLoop에서 블로킹 DB 호출
@Override
protected void channelRead0(ChannelHandlerContext ctx, String msg) {
    User u = userRepository.findByName(msg);     // JDBC = 블로킹
    ctx.writeAndFlush("Hello " + u);
}

// ✅ 별도 풀에서 실행
private static final ExecutorService dbPool = Executors.newFixedThreadPool(20);

@Override
protected void channelRead0(ChannelHandlerContext ctx, String msg) {
    dbPool.submit(() -> {
        User u = userRepository.findByName(msg);
        ctx.writeAndFlush("Hello " + u);
    });
}

// 더 좋음: 비동기 드라이버 (R2DBC)
@Override
protected void channelRead0(ChannelHandlerContext ctx, String msg) {
    userR2dbcRepo.findByName(msg)
        .subscribe(u -> ctx.writeAndFlush("Hello " + u));
}
```

---

## 3. Spring WebFlux와 WebClient

### WebFlux 전체 모습

```
                                              Netty (이미 worker EventLoop)
                                                      ↑
HTTP 요청 → Netty → DispatcherHandler → Controller → Mono/Flux → Reactor → response
                                                      │
                                             (blocking 호출 금지)
```

- **Spring WebFlux**: Reactive 스택. 기본 서버는 Netty.
- **Spring MVC**: Servlet 스택. Tomcat 기본.

### WebClient 사용

```java
WebClient client = WebClient.builder()
    .baseUrl("https://api.example.com")
    .defaultHeader("Authorization", "Bearer " + token)
    .build();

// GET
Mono<User> user = client.get()
    .uri("/users/{id}", 42)
    .retrieve()
    .bodyToMono(User.class);

// POST
Mono<User> created = client.post()
    .uri("/users")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(new User("Alice"))
    .retrieve()
    .bodyToMono(User.class);

// 동기 호출 (테스트, 단발성 — 운영 코드에서 .block()은 신중히)
User u = user.block();
```

### 커넥션 풀 (Reactor Netty)

```java
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

ConnectionProvider provider = ConnectionProvider.builder("api-pool")
    .maxConnections(200)                          // 풀 최대 연결
    .pendingAcquireTimeout(Duration.ofSeconds(5)) // 풀 가득 시 대기
    .maxIdleTime(Duration.ofSeconds(30))          // 30초 유휴 시 닫기
    .maxLifeTime(Duration.ofMinutes(5))           // 5분마다 강제 갱신
    .evictInBackground(Duration.ofSeconds(60))    // 백그라운드 정리
    .build();

HttpClient httpClient = HttpClient.create(provider)
    .responseTimeout(Duration.ofSeconds(10))
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
    .option(ChannelOption.SO_KEEPALIVE, true);

WebClient client = WebClient.builder()
    .clientConnector(new ReactorClientHttpConnector(httpClient))
    .baseUrl("https://api.example.com")
    .build();
```

### 핵심 설정의 의미

| 옵션 | 의미 | 권장 |
|---|---|---|
| `maxConnections` | 풀의 최대 연결 수 | 트래픽·서버당 받을 수 있는 양 |
| `pendingAcquireMaxCount` | 대기 큐 크기 | 작게 (backpressure) |
| `pendingAcquireTimeout` | 대기 타임아웃 | 1~10초 |
| `maxIdleTime` | 유휴 시 종료 | 30s ~ 5m |
| `maxLifeTime` | 강제 만료 (방화벽 타임아웃 회피) | 5~10m |
| `responseTimeout` | 응답 대기 | 비즈니스 SLA |

> **운영 경험**: `maxLifeTime`을 안 두면 NAT/방화벽이 무음 idle 연결을 끊고 클라이언트는 그걸 모름 → 첫 사용 시 `Connection reset` 폭주. 5분 정도가 안전.

---

## 4. RestTemplate + Apache HttpClient 5

RestTemplate은 deprecation 상태이지만 여전히 많이 쓰임. 기본은 `HttpURLConnection` (재사용 X) → **반드시 풀**을 붙여라.

```java
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.impl.io.*;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

PoolingHttpClientConnectionManager pool = PoolingHttpClientConnectionManagerBuilder.create()
    .setMaxConnTotal(200)
    .setMaxConnPerRoute(50)
    .build();

RequestConfig reqCfg = RequestConfig.custom()
    .setConnectTimeout(Timeout.ofSeconds(5))
    .setResponseTimeout(Timeout.ofSeconds(10))
    .setConnectionRequestTimeout(Timeout.ofSeconds(2))
    .build();

CloseableHttpClient http = HttpClients.custom()
    .setConnectionManager(pool)
    .setDefaultRequestConfig(reqCfg)
    .evictIdleConnections(Timeout.ofSeconds(30))
    .build();

RestTemplate rt = new RestTemplate(new HttpComponentsClientHttpRequestFactory(http));
```

`maxConnPerRoute`: 같은 호스트에 동시 연결 수 한계. 운영에서 이게 작으면 한 호스트로의 요청이 큐에서 대기.

---

## 5. 풀 크기 결정 가이드

| 부하 패턴 | 풀 크기 추정 |
|---|---|
| 동기 호출, RPS=100, p95=200ms | Little's Law: 100 × 0.2 = 20 (여유 +50%) ≈ 30 |
| 비동기 호출 | 응답시간 영향 적음, 호스트별 동시성으로 결정 |
| 백엔드가 풀 N으로 제한 | 그 이상 늘려도 의미 없음 |

> Little's Law: `concurrency = throughput × response_time`. 풀 크기 결정의 핵심.

### 부하 테스트로 검증

```bash
# wrk (간단)
wrk -t 10 -c 100 -d 30s http://api.example.com/users

# JMeter, Gatling, k6 등
# k6 예
k6 run --vus 100 --duration 30s script.js
```

p95, p99, 에러율, RPS, 풀 사용량(`HikariCP`나 Reactor Netty의 메트릭)을 함께 보고 결정.

---

## 6. WebClient 디버깅 로그

```yaml
# application.yml
logging:
  level:
    reactor.netty.http.client: DEBUG     # request/response 디테일
    reactor.netty.resources: DEBUG        # 풀 상태
    org.springframework.web.reactive.function.client: TRACE
```

```java
// 요청·응답 가로채기
WebClient client = WebClient.builder()
    .filter(ExchangeFilterFunction.ofRequestProcessor(req -> {
        log.info("→ {} {}", req.method(), req.url());
        return Mono.just(req);
    }))
    .filter(ExchangeFilterFunction.ofResponseProcessor(res -> {
        log.info("← {}", res.statusCode());
        return Mono.just(res);
    }))
    .build();
```

---

## 7. ❌ 위험 / ✅ 안전

### WebClient에 풀 안 붙임

```java
// ❌ 기본 (전역 공유 풀, 운영에 부적합)
WebClient.create()

// ✅ 명시적 풀
WebClient.builder().clientConnector(...).build()
```

### Mono를 안 subscribe

```java
// ❌ 호출만 하고 subscribe 안 함 → 실제 요청 안 일어남
client.get().uri("/users").retrieve().bodyToMono(User.class);

// ✅ subscribe 또는 block 또는 다른 Flow와 합성
client.get().uri("/users").retrieve().bodyToMono(User.class).subscribe();
```

### WebFlux 코드에서 .block()

```java
@GetMapping("/u/{id}")
public User getUser(@PathVariable Long id) {
    // ❌ EventLoop에서 block → 데드락 가능
    return webClient.get().uri("/users/{id}", id).retrieve().bodyToMono(User.class).block();
}

// ✅ Mono 그대로 반환
@GetMapping("/u/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    return webClient.get().uri("/users/{id}", id).retrieve().bodyToMono(User.class);
}
```

### Netty 핸들러에서 동기 DB 호출

§2 참조.

### 응답 본문 안 소비

```java
// ❌ 본문 안 읽으면 연결 leak (CLOSE_WAIT)
ResponseEntity<Void> r = restTemplate.exchange(...);   // Void라도 본문 소비 필요할 수 있음

// ✅ exchange는 응답 객체 자체를 close (Spring 6+에서 개선)
// 단, ClientHttpResponse를 직접 다루면 try-with-resources
```

---

## 8. 실습

### Step 1: 채팅 서버 실행

위 §1의 ChatServer를 띄우고 nc 5개로 동시 접속.

### Step 2: WebClient 풀 효과 측정

```java
// 풀 없이
WebClient noPool = WebClient.create("http://localhost:8080");

// 풀 with 100 max
ConnectionProvider provider = ConnectionProvider.builder("test").maxConnections(100).build();
HttpClient httpClient = HttpClient.create(provider);
WebClient withPool = WebClient.builder()
    .clientConnector(new ReactorClientHttpConnector(httpClient))
    .baseUrl("http://localhost:8080").build();

// 1000 동시 호출 시간 비교
long t = System.nanoTime();
Flux.range(1, 1000)
    .flatMap(i -> withPool.get().retrieve().bodyToMono(String.class), 100)
    .blockLast();
System.out.println("Took: " + (System.nanoTime() - t)/1e9 + "s");
```

### Step 3: 풀 고갈 시 동작

`pendingAcquireMaxCount`를 작게 두고 부하 → 어떤 예외(`PoolAcquirePendingLimitException`)가 나오는지.

### Step 4: 운영 시뮬레이션

- 백엔드를 임의 50ms 지연하게 만들고
- WebClient로 RPS 1000 부하
- 풀 크기 10 vs 50 vs 200 — 처리량·에러율·p99 비교

---

## 더 읽어볼 자료

- 📘 『Netty in Action』 (Norman Maurer)
- 📘 『Reactive Programming with RxJava』 (Tomasz Nurkiewicz)
- 🔗 Spring WebFlux Reference: <https://docs.spring.io/spring-framework/reference/web/webflux.html>
- 🔗 Reactor Netty 설정: <https://projectreactor.io/docs/netty/release/reference/>
- 🔗 Apache HttpClient 5: <https://hc.apache.org/httpcomponents-client-5.x/>

---

## 자가 점검

- [ ] Netty의 boss vs worker EventLoop의 역할을 안다
- [ ] "EventLoop에서 블로킹 금지"의 의미와 회피법을 안다
- [ ] WebClient의 커넥션 풀 옵션 5개를 즉답한다
- [ ] `maxLifeTime`을 두는 이유 (NAT/방화벽 idle timeout)를 안다
- [ ] Little's Law로 풀 크기 추정한다

다음: [`04_os_performance_tuning.md`](04_os_performance_tuning.md)
