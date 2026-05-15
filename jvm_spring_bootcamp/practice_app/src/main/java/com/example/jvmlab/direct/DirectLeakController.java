package com.example.jvmlab.direct;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/direct")
public class DirectLeakController {

    // ❌ Direct Buffer를 잡고 release 안 함
    // -XX:MaxDirectMemorySize=64m 환경에서 빠르게 OOM 재현
    private static final List<ByteBuffer> KEEP = new ArrayList<>();

    @GetMapping("/leak")
    public String leak() {
        ByteBuffer buf = ByteBuffer.allocateDirect(1024 * 1024); // 1MB direct
        KEEP.add(buf);
        return "direct buffers held=" + KEEP.size() + " (~ " + KEEP.size() + " MB)";
    }

    @GetMapping("/release")
    public String release() {
        int n = KEEP.size();
        KEEP.clear();
        System.gc();        // PhantomReference cleaner trigger
        return "released " + n + " direct buffers (GC required to free)";
    }

    @GetMapping("/info")
    public String info() {
        Runtime r = Runtime.getRuntime();
        return "free=" + (r.freeMemory() / 1024 / 1024) + "MB, total=" + (r.totalMemory() / 1024 / 1024)
                + "MB, max=" + (r.maxMemory() / 1024 / 1024) + "MB%n"
                + "(direct memory는 -XX:NativeMemoryTracking + jcmd로 확인)";
    }
}
