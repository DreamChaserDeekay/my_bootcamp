package com.example.vuln.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * SQL Injection 학습용 컨트롤러.
 *
 * /vuln/login  — 문자열 concat 으로 SQLi 가능
 * /safe/login  — Prepared Statement 사용
 */
@Controller
public class LoginController {

    private final JdbcTemplate jdbc;

    @Autowired
    public LoginController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/vuln/login")
    public String loginForm() {
        return "vuln/login";
    }

    /**
     * ❌ 의도적으로 취약 — 절대 운영 코드에 쓰지 말 것.
     * 공격 페이로드 예: username=admin' --   password=anything
     */
    @PostMapping("/vuln/login")
    public String vulnLogin(@RequestParam String username,
                             @RequestParam String password,
                             Model model) {
        // SQL Injection 발생 지점
        String sql = "SELECT id, username, role FROM users WHERE username = '"
            + username + "' AND password = '" + password + "'";
        model.addAttribute("executedSql", sql);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            if (!rows.isEmpty()) {
                model.addAttribute("user", rows.get(0));
                return "vuln/loginSuccess";
            }
        } catch (Exception e) {
            // 학습용으로 에러 메시지를 그대로 노출 (정보 누출 예)
            model.addAttribute("error", "DB error: " + e.getMessage());
            return "vuln/login";
        }
        model.addAttribute("error", "로그인 실패");
        return "vuln/login";
    }

    @GetMapping("/safe/login")
    public String safeLoginForm() {
        return "safe/login";
    }

    /**
     * ✅ Prepared Statement (binding parameters) 사용 — SQLi 차단.
     * 비밀번호 평문 비교는 여전히 부적절하나 SQLi 학습 핵심에 집중.
     * (실전: BCrypt + PasswordEncoder.matches)
     */
    @PostMapping("/safe/login")
    public String safeLogin(@RequestParam String username,
                             @RequestParam String password,
                             Model model) {
        String sql = "SELECT id, username, role FROM users WHERE username = ? AND password = ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, username, password);
        if (!rows.isEmpty()) {
            model.addAttribute("user", rows.get(0));
            return "safe/loginSuccess";
        }
        model.addAttribute("error", "로그인 실패");
        return "safe/login";
    }
}
