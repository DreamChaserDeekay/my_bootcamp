package com.example.netlab.client;

import org.springframework.web.client.RestTemplate;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * RestTemplate without connection pool (각 호출마다 새 TCP 연결).
 * 부하 시 TIME_WAIT 누적·로컬 포트 고갈을 관찰.
 * 사용: java Main client-restt http://localhost:8080/echo 1000 50
 */
public class ClientRest {
    public static void main(String[] args) throws Exception {
        String url   = args.length > 0 ? args[0] : "http://localhost:8080/echo";
        int requests = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
        int conc     = args.length > 2 ? Integer.parseInt(args[2]) : 50;

        RestTemplate rt = new RestTemplate();   // 풀 없음, 매번 새 연결
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

        double sec = (System.nanoTime() - t0) / 1e9;
        System.out.printf("[restt] ok=%d err=%d time=%.2fs rps=%.1f%n",
            ok.get(), err.get(), sec, ok.get()/sec);
        System.out.println("Tip: 'ss -tan state time-wait | wc -l'로 TIME_WAIT 확인");
    }
}
