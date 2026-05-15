package com.example.vuln.controller;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Stored XSS 학습용 게시판.
 *
 * /vuln/board : 게시글 입력을 그대로 저장, 출력 시 th:utext 사용 → XSS
 * /safe/board : th:text 사용 (자동 이스케이프), 위지윅 입력은 OWASP Sanitizer
 */
@Controller
public class BoardController {

    private final JdbcTemplate jdbc;

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.IMAGES)
            .and(new HtmlPolicyBuilder()
                .allowElements("p", "br", "strong", "em", "ul", "ol", "li")
                .toFactory());

    @Autowired
    public BoardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ❌ 취약: th:utext 로 출력 (templates/vuln/board.html 에서)
    @GetMapping("/vuln/board")
    public String vulnList(Model model) {
        List<Map<String, Object>> posts =
            jdbc.queryForList("SELECT id, author, title, content FROM posts ORDER BY id DESC");
        model.addAttribute("posts", posts);
        return "vuln/board";
    }

    @PostMapping("/vuln/board")
    public String vulnAdd(@RequestParam String author,
                          @RequestParam String title,
                          @RequestParam String content) {
        jdbc.update("INSERT INTO posts(author, title, content) VALUES(?, ?, ?)",
                author, title, content);
        return "redirect:/vuln/board";
    }

    // ✅ 안전: th:text 사용. 위지윅이 필요하면 OWASP Sanitizer 거친 결과만 utext.
    @GetMapping("/safe/board")
    public String safeList(Model model) {
        List<Map<String, Object>> posts =
            jdbc.queryForList("SELECT id, author, title, content FROM posts ORDER BY id DESC");
        model.addAttribute("posts", posts);
        return "safe/board";
    }

    @PostMapping("/safe/board")
    public String safeAdd(@RequestParam String author,
                          @RequestParam String title,
                          @RequestParam String content) {
        // 입력 단계에서도 길이 검증
        if (author == null || author.length() > 50) author = "anonymous";
        if (title == null || title.isEmpty() || title.length() > 200) {
            return "redirect:/safe/board";
        }
        if (content == null) content = "";
        if (content.length() > 10_000) content = content.substring(0, 10_000);

        // 위지윅 입력이라 가정해 sanitize 후 저장 (또는 그냥 plain 으로 저장 후 출력에서 escape)
        String safe = POLICY.sanitize(content);
        jdbc.update("INSERT INTO posts(author, title, content) VALUES(?, ?, ?)",
                author, title, safe);
        return "redirect:/safe/board";
    }
}
