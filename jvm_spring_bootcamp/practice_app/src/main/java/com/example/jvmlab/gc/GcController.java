package com.example.jvmlab.gc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/gc")
public class GcController {

    @GetMapping("/humongous")
    public String humongous() {
        // ❌ G1의 region 절반 이상 = Humongous → Old에 직접 → Full GC 유발
        // -Xmx256m 환경에서 region 크기는 약 1MB, 따라서 1MB 이상이 humongous
        byte[] huge = new byte[2 * 1024 * 1024];   // 2MB
        // 즉시 release되지만 humongous는 단편화·G1 부담
        return "allocated " + huge.length + " bytes (humongous)";
    }

    @GetMapping("/burst")
    public String burst() {
        // 짧은 시간에 많은 객체 → Young GC 폭주
        List<byte[]> tmp = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            tmp.add(new byte[10 * 1024]);
        }
        return "allocated " + tmp.size() + " × 10KB";
    }

    @GetMapping("/oldgen")
    public String oldgen() {
        // 살아남는 객체를 빠르게 만들어 Old로 승격
        List<byte[]> survivors = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            survivors.add(new byte[100 * 1024]);   // 100KB × 100 = 10MB
        }
        // ThreadLocal에 잡아 살아남게
        OLD_SURVIVORS.set(survivors);
        return "kept " + survivors.size() + " × 100KB in ThreadLocal (will be promoted to Old)";
    }

    private static final ThreadLocal<List<byte[]>> OLD_SURVIVORS = new ThreadLocal<>();
}
