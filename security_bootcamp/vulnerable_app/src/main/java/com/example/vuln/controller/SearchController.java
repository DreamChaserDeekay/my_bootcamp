package com.example.vuln.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UNION-based SQLi 및 동적 정렬 SQLi 학습.
 */
@Controller
public class SearchController {

    private final JdbcTemplate jdbc;

    @Autowired
    public SearchController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * ❌ UNION-based SQLi 가능.
     * 공격 예: q=' UNION SELECT id, username || ':' || password, role FROM users --
     */
    @GetMapping("/vuln/search")
    public String vulnSearch(@RequestParam(required = false) String q, Model model) {
        if (q == null || q.isEmpty()) {
            model.addAttribute("results", List.of());
            return "vuln/search";
        }
        String sql = "SELECT id, title, author FROM posts WHERE content LIKE '%" + q + "%'";
        model.addAttribute("executedSql", sql);
        try {
            model.addAttribute("results", jdbc.queryForList(sql));
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("results", List.of());
        }
        return "vuln/search";
    }

    /**
     * ✅ Prepared Statement + LIKE 와일드카드 escape.
     */
    @GetMapping("/safe/search")
    public String safeSearch(@RequestParam(required = false) String q, Model model) {
        if (q == null || q.isEmpty()) {
            model.addAttribute("results", List.of());
            return "safe/search";
        }
        String escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String sql = "SELECT id, title, author FROM posts WHERE content LIKE ? ESCAPE '\\'";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, "%" + escaped + "%");
        model.addAttribute("results", rows);
        return "safe/search";
    }

    private static final Set<String> ALLOWED_SORT =
        Set.of("id", "title", "author", "created_at");

    /**
     * ❌ ORDER BY SQLi: 동적 컬럼명을 그대로 사용.
     * 공격 예: sort=(case when 1=1 then title else author end)
     */
    @GetMapping("/vuln/postsBySort")
    public String vulnSorted(@RequestParam(defaultValue = "id") String sort, Model model) {
        String sql = "SELECT id, title, author FROM posts ORDER BY " + sort;
        model.addAttribute("executedSql", sql);
        try {
            model.addAttribute("results", jdbc.queryForList(sql));
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "vuln/search";
    }

    /**
     * ✅ 화이트리스트로 컬럼명 검증.
     */
    @GetMapping("/safe/postsBySort")
    public String safeSorted(@RequestParam(defaultValue = "id") String sort, Model model) {
        if (!ALLOWED_SORT.contains(sort)) {
            sort = "id";
        }
        String sql = "SELECT id, title, author FROM posts ORDER BY " + sort;
        model.addAttribute("results", jdbc.queryForList(sql));
        return "safe/search";
    }
}
