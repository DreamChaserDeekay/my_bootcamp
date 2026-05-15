package com.example.vuln.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * SSRF 학습용.
 *
 * /vuln/fetch?url=...  — 임의 URL fetch (메타데이터·내부망 접근 가능)
 * /safe/fetch?url=...  — 스킴·호스트 화이트리스트 + 내부 IP 차단
 */
@Controller
public class FetchController {

    @GetMapping("/vuln/fetch")
    public String vulnFetch(@RequestParam String url, Model model) {
        try {
            URL u = new URL(url);    // file://, http://169.254.169.254/... 모두 허용
            HttpURLConnection con = (HttpURLConnection) u.openConnection();
            con.setConnectTimeout(3000);
            con.setReadTimeout(5000);
            byte[] body = con.getInputStream().readAllBytes();
            model.addAttribute("body", new String(body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            model.addAttribute("body", "Error: " + e.getMessage());
        }
        return "vuln/fetch";
    }

    private static final Set<String> ALLOWED_HOSTS =
        Set.of("api.partner.example.com", "cdn.example.com");

    @GetMapping("/safe/fetch")
    public String safeFetch(@RequestParam String url, Model model) {
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme())) {
                model.addAttribute("body", "https 만 허용됩니다.");
                return "safe/fetch";
            }
            String host = uri.getHost();
            if (host == null || !ALLOWED_HOSTS.contains(host)) {
                model.addAttribute("body", "허용되지 않은 호스트입니다.");
                return "safe/fetch";
            }
            // DNS 응답이 내부 IP면 거부 (DNS Rebinding 일부 방어)
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress a : addrs) {
                if (isInternal(a)) {
                    model.addAttribute("body", "내부 주소는 차단됩니다.");
                    return "safe/fetch";
                }
            }
            URL u = uri.toURL();
            HttpURLConnection con = (HttpURLConnection) u.openConnection();
            con.setConnectTimeout(3000);
            con.setReadTimeout(5000);
            // 리다이렉트 따라가지 않음 — 우회 방지
            con.setInstanceFollowRedirects(false);
            byte[] body = con.getInputStream().readNBytes(1024 * 64); // 응답 크기 제한
            model.addAttribute("body", new String(body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            model.addAttribute("body", "요청 처리 실패");
        }
        return "safe/fetch";
    }

    private boolean isInternal(InetAddress addr) {
        if (addr == null) return true;
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
            return true;
        }
        String ip = addr.getHostAddress();
        return ip.startsWith("169.254.")          // AWS metadata
            || ip.startsWith("100.64.")           // CGNAT
            || ip.startsWith("0.")
            || ip.equals("::1");
    }
}
