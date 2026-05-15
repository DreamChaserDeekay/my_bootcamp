package com.example.jvmlab.leak;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/leak")
public class LeakController {

    // ❌ static 컬렉션이 무한 누적 — GC가 못 잡음
    private static final Map<Long, byte[]> HISTORY = new ConcurrentHashMap<>();
    private static final AtomicLong COUNTER = new AtomicLong();

    @GetMapping
    public String leak() {
        long id = COUNTER.incrementAndGet();
        // 매 요청마다 10KB 누적
        HISTORY.put(id, new byte[10 * 1024]);
        return "leaked entry " + id + " (total=" + HISTORY.size() + ")";
    }

    @GetMapping("/big")
    public String big() {
        long id = COUNTER.incrementAndGet();
        // 매 요청마다 1MB
        HISTORY.put(id, new byte[1024 * 1024]);
        return "big entry " + id + " (total=" + HISTORY.size() + ")";
    }

    @GetMapping("/size")
    public String size() {
        return "history.size=" + HISTORY.size();
    }

    @GetMapping("/clear")
    public String clear() {
        int n = HISTORY.size();
        HISTORY.clear();
        System.gc(); // 학습 목적
        return "cleared " + n + " entries";
    }
}
