package com.example.devopslab;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.Map;

@RestController
public class HelloController {

    @Value("${GREETING:Hello}")
    private String greeting;

    @Value("${spring.profiles.active:default}")
    private String profile;

    @GetMapping("/")
    public Map<String, Object> hello() {
        return Map.of(
            "message", greeting,
            "profile", profile,
            "host", System.getenv().getOrDefault("HOSTNAME", "unknown"),
            "uptime", ManagementFactory.getRuntimeMXBean().getUptime()
        );
    }

    @GetMapping("/env")
    public Map<String, String> env() {
        return Map.of(
            "JAVA_OPTS", System.getenv().getOrDefault("JAVA_TOOL_OPTIONS", "-"),
            "PROFILE", profile,
            "MAX_HEAP", String.valueOf(Runtime.getRuntime().maxMemory() / 1024 / 1024) + "MB"
        );
    }
}
