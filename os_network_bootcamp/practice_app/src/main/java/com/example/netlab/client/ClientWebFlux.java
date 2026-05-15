package com.example.netlab.client;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * WebClient + Reactor Netty 커넥션 풀 — 비동기 + 풀로 가장 높은 처리량.
 */
public class ClientWebFlux {
    public static void main(String[] args) {
        String url   = args.length > 0 ? args[0] : "http://localhost:8080/echo";
        int requests = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
        int conc     = args.length > 2 ? Integer.parseInt(args[2]) : 100;
        int maxPool  = args.length > 3 ? Integer.parseInt(args[3]) : 100;

        ConnectionProvider provider = ConnectionProvider.builder("api-pool")
            .maxConnections(maxPool)
            .pendingAcquireTimeout(Duration.ofSeconds(5))
            .maxIdleTime(Duration.ofSeconds(30))
            .maxLifeTime(Duration.ofMinutes(5))
            .evictInBackground(Duration.ofSeconds(60))
            .build();
        HttpClient httpClient = HttpClient.create(provider)
            .responseTimeout(Duration.ofSeconds(10))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .option(ChannelOption.SO_KEEPALIVE, true);

        WebClient client = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl(url)
            .build();

        long t0 = System.nanoTime();
        long ok = Flux.range(1, requests)
            .flatMap(i -> client.get().retrieve().bodyToMono(String.class)
                                .onErrorReturn(""), conc)
            .filter(s -> !s.isEmpty())
            .count()
            .block();
        double sec = (System.nanoTime() - t0) / 1e9;
        System.out.printf("[webc] ok=%d/%d time=%.2fs rps=%.1f (maxPool=%d conc=%d)%n",
            ok, requests, sec, ok/sec, maxPool, conc);
    }
}
