package com.example.jvmlab.thread;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@RestController
public class RaceController {

    // 4가지 카운터를 동시에 운영하며 정확성 비교
    private int plain;                                       // ❌ race
    private volatile int vol;                                // ❌ volatile만으로 ++ atomic X
    private final AtomicInteger atomic = new AtomicInteger();
    private final LongAdder adder = new LongAdder();

    @GetMapping("/race/{n}")
    public String race(@PathVariable int n) throws Exception {
        plain = 0;
        vol = 0;
        atomic.set(0);
        adder.reset();

        ExecutorService es = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(8);

        for (int t = 0; t < 8; t++) {
            es.submit(() -> {
                for (int i = 0; i < n; i++) {
                    plain++;
                    vol++;
                    atomic.incrementAndGet();
                    adder.increment();
                }
                latch.countDown();
            });
        }
        latch.await();
        es.shutdown();

        int expected = 8 * n;
        return String.format(
            "expected=%d%nplain=%d (loss=%d)%nvolatile=%d (loss=%d)%natomic=%d%nadder=%d%n",
            expected,
            plain, expected - plain,
            vol, expected - vol,
            atomic.get(),
            adder.sum()
        );
    }
}
