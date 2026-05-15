package com.example.jvmlab.thread;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlowController {

    @GetMapping("/slow")
    public String slow() throws Exception {
        Thread.sleep(10_000);   // 외부 API 흉내 (blocking 10초)
        return "ok on " + Thread.currentThread();
    }

    @GetMapping("/slow/{ms}")
    public String slowN(@PathVariable long ms) throws Exception {
        Thread.sleep(ms);
        return "ok after " + ms + "ms on " + Thread.currentThread();
    }
}
