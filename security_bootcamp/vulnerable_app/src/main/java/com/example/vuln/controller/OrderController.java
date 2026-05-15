package com.example.vuln.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * IDOR (Insecure Direct Object Reference) 학습용.
 *
 * /vuln/orders/{id}  — 권한 검사 없음. id 변조로 남의 주문 조회 가능.
 * /safe/orders/{id}  — 쿼리에 사용자명을 함께 포함하여 권한 검사.
 *
 * 학습 편의를 위해 인증 미설정 시 'alice'를 가정.
 */
@Controller
public class OrderController {

    private final JdbcTemplate jdbc;

    @Autowired
    public OrderController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/vuln/orders/{id}")
    public String vulnGet(@PathVariable Long id, Model model) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, owner_username, product, amount FROM orders WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("order", rows.get(0));
        return "vuln/order";
    }

    @GetMapping("/safe/orders/{id}")
    public String safeGet(@PathVariable Long id, Authentication auth, Model model) {
        String me = (auth != null) ? auth.getName() : "alice";
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, owner_username, product, amount FROM orders " +
            " WHERE id = ? AND owner_username = ?", id, me);
        if (rows.isEmpty()) {
            // 존재하지 않거나 본인 것이 아니면 동일하게 404 — 존재 은닉
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("order", rows.get(0));
        return "safe/order";
    }
}
