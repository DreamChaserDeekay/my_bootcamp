package com.example.jvmlab.thread;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeadlockController {

    private static final Object A = new Object();
    private static final Object B = new Object();

    @GetMapping("/deadlock")
    public String trigger() {
        new Thread(() -> {
            synchronized (A) {
                sleep(100);
                synchronized (B) {
                    System.out.println("never reached");
                }
            }
        }, "deadlock-T1").start();

        new Thread(() -> {
            synchronized (B) {
                sleep(100);
                synchronized (A) {
                    System.out.println("never reached");
                }
            }
        }, "deadlock-T2").start();

        return "triggered. run `jstack <pid> | grep -A 30 deadlock` to inspect";
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (Exception ignore) {}
    }
}
