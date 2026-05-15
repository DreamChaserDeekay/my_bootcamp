package com.example.vuln.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

/**
 * OS Command Injection 학습용.
 */
@Controller
public class PingController {

    /**
     * ❌ 셸을 거치는 exec(String) — 메타문자 해석됨.
     * 공격 예 (Linux):   host=127.0.0.1; whoami
     * 공격 예 (Windows): host=127.0.0.1 & whoami
     */
    @GetMapping("/vuln/ping")
    public String vulnPing(@RequestParam String host, Model model) {
        String cmd = (System.getProperty("os.name").toLowerCase().contains("win")
                ? "cmd /c ping -n 1 "
                : "ping -c 1 ") + host;
        StringBuilder out = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(cmd);   // 위험
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            p.waitFor();
        } catch (Exception e) {
            out.append("Error: ").append(e.getMessage());
        }
        model.addAttribute("output", out.toString());
        model.addAttribute("executedCmd", cmd);
        return "vuln/ping";
    }

    private static final Pattern HOST_OK =
        Pattern.compile("^[a-zA-Z0-9.\\-]{1,253}$");

    /**
     * ✅ 1) 정규식으로 입력 형식 제한
     *    2) ProcessBuilder 배열 인자 — 셸 거치지 않음 → 메타문자 무력화
     */
    @GetMapping("/safe/ping")
    public String safePing(@RequestParam String host, Model model) {
        if (!HOST_OK.matcher(host).matches()) {
            model.addAttribute("output", "잘못된 호스트 형식입니다.");
            return "safe/ping";
        }
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        String[] cmd = win
            ? new String[]{"ping", "-n", "1", host}
            : new String[]{"ping", "-c", "1", host};

        StringBuilder out = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            p.waitFor();
        } catch (Exception e) {
            out.append("Error");
        }
        model.addAttribute("output", out.toString());
        return "safe/ping";
    }
}
