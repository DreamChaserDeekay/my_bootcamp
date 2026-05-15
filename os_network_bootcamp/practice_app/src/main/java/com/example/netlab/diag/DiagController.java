package com.example.netlab.diag;

import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * 캡스톤 진단용 엔드포인트.
 *  /echo   — 단순 응답 (네트워크 한계 측정)
 *  /work   — 인위 지연 (스레드 풀 한계)
 *  /cpu    — CPU 부하
 *  /info   — JVM/스레드 상태
 */
@RestController
public class DiagController {

    @GetMapping("/echo")
    public String echo(@RequestParam(defaultValue = "hi") String s) {
        return "ECHO:" + s + "\n";
    }

    @GetMapping("/work")
    public String work(@RequestParam(defaultValue = "50") int ms) throws InterruptedException {
        Thread.sleep(ms);
        return "OK:" + ms + "ms\n";
    }

    @GetMapping("/cpu")
    public long cpu(@RequestParam(defaultValue = "1000000") long n) {
        long sum = 0;
        for (long i = 0; i < n; i++) sum += i * i;
        return sum;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        var rt = Runtime.getRuntime();
        var os = ManagementFactory.getOperatingSystemMXBean();
        var thr = ManagementFactory.getThreadMXBean();
        var mem = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("availableProcessors", rt.availableProcessors());
        m.put("systemLoadAverage", os.getSystemLoadAverage());
        m.put("threadCount", thr.getThreadCount());
        m.put("peakThreadCount", thr.getPeakThreadCount());
        m.put("heapUsedMB", mem.getUsed() / (1024 * 1024));
        m.put("heapMaxMB",  mem.getMax()  / (1024 * 1024));
        return m;
    }
}
