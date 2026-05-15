package com.example.netlab.client;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * RestTemplate + Apache HttpClient 5 풀 — 연결 재사용으로 TIME_WAIT 누적 회피.
 * 같은 부하에서 ClientRest와 비교할 것.
 */
public class ClientPool {
    public static void main(String[] args) throws Exception {
        String url   = args.length > 0 ? args[0] : "http://localhost:8080/echo";
        int requests = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
        int conc     = args.length > 2 ? Integer.parseInt(args[2]) : 50;
        int maxPool  = args.length > 3 ? Integer.parseInt(args[3]) : 100;

        PoolingHttpClientConnectionManager mgr = PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(maxPool)
            .setMaxConnPerRoute(maxPool)
            .build();
        RequestConfig req = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(5))
            .setResponseTimeout(Timeout.ofSeconds(10))
            .setConnectionRequestTimeout(Timeout.ofSeconds(2))
            .build();
        CloseableHttpClient hc = HttpClients.custom()
            .setConnectionManager(mgr)
            .setDefaultRequestConfig(req)
            .evictIdleConnections(Timeout.ofSeconds(30))
            .build();
        RestTemplate rt = new RestTemplate(new HttpComponentsClientHttpRequestFactory(hc));

        ExecutorService pool = Executors.newFixedThreadPool(conc);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger err = new AtomicInteger();
        long t0 = System.nanoTime();

        CountDownLatch latch = new CountDownLatch(requests);
        for (int i = 0; i < requests; i++) {
            pool.submit(() -> {
                try {
                    rt.getForObject(url, String.class);
                    ok.incrementAndGet();
                } catch (Exception e) {
                    err.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(5, TimeUnit.MINUTES);
        pool.shutdownNow();
        hc.close();

        double sec = (System.nanoTime() - t0) / 1e9;
        System.out.printf("[pool] ok=%d err=%d time=%.2fs rps=%.1f (maxPool=%d)%n",
            ok.get(), err.get(), sec, ok.get()/sec, maxPool);
    }
}
