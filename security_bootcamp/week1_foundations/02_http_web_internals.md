# Day 2 — HTTP·쿠키·세션·CORS·SOP 내부 동작

> 웹 보안을 이해하려면 **HTTP가 정확히 어떻게 동작하는지** 패킷 단위로 알아야 한다. 추상화에 의존하면 미묘한 공격을 놓친다.

## 1. HTTP 메시지 구조

### 요청
```http
POST /login HTTP/1.1
Host: example.com
User-Agent: Mozilla/5.0
Cookie: JSESSIONID=ABC123; theme=dark
Content-Type: application/x-www-form-urlencoded
Content-Length: 29
Origin: https://example.com
Referer: https://example.com/login

username=alice&password=p%40ss
```

### 응답
```http
HTTP/1.1 302 Found
Set-Cookie: JSESSIONID=NEW456; Path=/; HttpOnly; Secure; SameSite=Lax
Location: /dashboard
Content-Length: 0
```

### 핵심 포인트
- 첫 줄: 메서드·경로·버전
- 헤더: `Name: Value`, **대소문자 구분 안 함**
- 빈 줄 → 바디 시작
- **모든 텍스트는 클라이언트가 변조 가능** — 브라우저 UI는 일부일 뿐

---

## 2. HTTP 메서드 — Safe vs Idempotent

| 메서드 | Safe (읽기 전용) | Idempotent (재실행 동일) | 본문 가능 |
|--------|---|---|---|
| GET | ✅ | ✅ | (관습상 X) |
| HEAD | ✅ | ✅ | X |
| OPTIONS | ✅ | ✅ | X |
| POST | ✘ | ✘ | ✅ |
| PUT | ✘ | ✅ | ✅ |
| DELETE | ✘ | ✅ | ✅ |
| PATCH | ✘ | ✘ | ✅ |

**보안 관점에서 중요한 이유**:
- **GET으로 상태 변경 금지** — CSRF·캐싱·로그 노출 위험 (`/delete?id=123` ❌)
- GET 쿼리스트링은 **서버 액세스 로그·브라우저 히스토리·Referer 헤더**에 그대로 남는다 → 토큰·비밀번호 절대 GET으로 보내지 말 것

---

## 3. HTTP 상태 코드 — 보안 관점

| 코드 | 의미 | 보안 이슈 |
|------|------|---------|
| 200 OK | 성공 | 로그인 실패도 200으로 주는 경우 → 사용자 열거 어려움 |
| 301/302 Redirect | 리다이렉트 | **Open Redirect** 취약점 — `Location`을 외부 URL로 |
| 401 Unauthorized | 인증 필요 | 사용자 존재 여부 노출 주의 |
| 403 Forbidden | 인가 거부 | 401 vs 403 차이로 정보 누출 가능 |
| 404 Not Found | 없음 | 비공개 리소스에 대해 403 대신 404 주는 것이 권장(존재 은닉) |
| 500 Internal Server Error | 서버 에러 | **스택 트레이스 절대 노출 금지** |

---

## 4. 쿠키 (Cookie) — 보안 속성 완전 정리

### Set-Cookie 헤더 예
```http
Set-Cookie: JSESSIONID=ABC; Path=/; Domain=example.com; Max-Age=3600;
            HttpOnly; Secure; SameSite=Lax
```

| 속성 | 의미 | 미설정 시 위험 |
|------|------|------------|
| `HttpOnly` | JS의 `document.cookie`에서 접근 불가 | XSS로 세션 쿠키 탈취 |
| `Secure` | HTTPS에서만 전송 | 중간자(MITM)가 평문에서 탈취 |
| `SameSite=Strict` | 외부 사이트 요청에 절대 전송 안 함 | CSRF |
| `SameSite=Lax` | Top-level GET만 허용 (기본값, 모던 브라우저) | 일부 CSRF |
| `SameSite=None` | 모든 cross-site에 전송 (Secure 필수) | CSRF, 단 cross-origin SSO에 필요 |
| `Domain` | 어느 도메인에서 쿠키를 보낼지 | 너무 넓으면 서브도메인 탈취로 누출 |
| `Path` | 어느 경로에서 보낼지 | 너무 넓으면 무관한 앱에 전송 |
| `Max-Age` / `Expires` | 만료 | 세션 쿠키는 짧을수록 안전 |

### 권장 기본값 (Spring Boot)
```yaml
# application.yml
server:
  servlet:
    session:
      cookie:
        http-only: true
        secure: true        # 운영 환경
        same-site: lax
```

```java
// Spring Security 6.x
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .sessionManagement(s -> s
            .sessionFixation().migrateSession()  // 세션 고정 공격 방어
            .maximumSessions(1)
        )
        // ... 생략
        ;
    return http.build();
}
```

### 쿠키 vs 토큰 저장소 비교 (SSR 환경 기준)

| 저장소 | XSS에 취약 | CSRF에 취약 | 권장 |
|-------|---------|----------|------|
| **HttpOnly Cookie + CSRF Token** | X | X (토큰으로) | ✅ Thymeleaf SSR의 표준 |
| LocalStorage | ✅ 매우 위험 | X | ❌ 세션 토큰 저장 금지 |
| SessionStorage | ✅ | X | ❌ |
| 메모리 (JS 변수) | X (외부 접근 어려움) | X | SPA에서 짧은 수명 토큰 |

---

## 5. 세션 vs 토큰

### 세션 기반 (Spring Security 기본 — SSR 환경에서 권장)
```
1. 로그인 성공 → 서버가 세션 생성, JSESSIONID 쿠키 발급
2. 이후 요청에 쿠키 자동 첨부
3. 서버는 세션 저장소(메모리/Redis)에서 사용자 조회
4. 로그아웃 → 서버 세션 무효화
```
**장점**: 서버에서 즉시 무효화 가능. **단점**: 수평 확장 시 세션 공유 필요(Sticky Session 또는 Redis).

### 토큰 기반 (JWT 등)
```
1. 로그인 성공 → 서버가 서명된 JWT 발급
2. 클라이언트가 매 요청에 Authorization: Bearer <jwt> 첨부
3. 서버는 서명만 검증, 저장소 조회 불필요(stateless)
```
**장점**: 무상태, 확장 용이. **단점**: 즉시 무효화 어려움(블랙리스트 필요), 큰 페이로드, 클라이언트 저장 위치 고민.

> 📌 **부트캠프 권장**: Thymeleaf SSR 환경에서는 **세션 기반**이 단순하고 안전. 외부 API에 토큰을 발급한다면 JWT 별도 학습.

---

## 6. Same-Origin Policy (SOP)

브라우저의 가장 중요한 보안 모델. **다른 출처(origin)의 리소스에 스크립트로 접근할 수 없다.**

### Origin 정의
`scheme + host + port` — 셋 중 하나만 달라도 다른 origin.

| URL A | URL B | 같은 Origin? |
|-------|-------|-----------|
| https://app.com/a | https://app.com/b | ✅ |
| https://app.com | http://app.com | ❌ (scheme) |
| https://app.com | https://api.app.com | ❌ (host) |
| https://app.com | https://app.com:8443 | ❌ (port) |

### SOP가 차단하는 것
- `fetch('https://other.com/api').then(r => r.text())` — 응답 읽기 차단
- `iframe` 내부의 DOM 접근 차단
- `localStorage` 격리

### SOP가 차단하지 않는 것 (그래서 CSRF가 가능)
- `<img src="https://other.com/img.png">` — 요청은 보냄
- `<form action="https://other.com/transfer" method="POST">` — 요청 보내고 리다이렉트 받음
- `<script src="https://other.com/api.js">` — 응답은 읽지 못해도 사이드 이펙트는 발생

---

## 7. CORS (Cross-Origin Resource Sharing)

**SOP를 안전하게 풀어주는 메커니즘.** 서버가 "이 origin이면 응답 읽어도 된다"고 명시.

### Simple Request
GET/POST이고 헤더가 단순하면 바로 요청 보내고 응답 후에 차단 결정.

### Preflight Request
복잡한 요청은 OPTIONS로 사전 질의.
```http
OPTIONS /api HTTP/1.1
Origin: https://app.com
Access-Control-Request-Method: PUT
Access-Control-Request-Headers: X-Custom

→ 응답
HTTP/1.1 204
Access-Control-Allow-Origin: https://app.com
Access-Control-Allow-Methods: GET, PUT, POST
Access-Control-Allow-Headers: X-Custom
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 600
```

### 위험한 CORS 설정 (절대 하지 말 것)
```java
// ❌ 매우 위험
response.setHeader("Access-Control-Allow-Origin", "*");
response.setHeader("Access-Control-Allow-Credentials", "true");
// → 이 조합은 사실 브라우저가 막지만, Origin을 echo 하는 패턴은 더 위험

// ❌ Origin을 그대로 echo
response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
response.setHeader("Access-Control-Allow-Credentials", "true");
// → 어떤 사이트에서든 인증된 요청 가능
```

### 안전한 Spring CORS 설정
```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of("https://app.com", "https://admin.app.com"));  // 명시
    cfg.setAllowedMethods(List.of("GET", "POST"));
    cfg.setAllowedHeaders(List.of("Content-Type", "X-CSRF-Token"));
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cfg);
    return source;
}
```

> 📌 **SSR 환경의 CORS**: Thymeleaf 페이지가 자신의 백엔드와만 통신한다면 CORS 자체가 필요 없다. 외부 API를 제공할 때만 신중하게 설정.

---

## 8. 주요 보안 응답 헤더

매 응답에 붙여야 할 헤더들. Spring Security 6.x는 대부분 기본 설정.

| 헤더 | 효과 | 권장값 |
|------|------|------|
| `Strict-Transport-Security` (HSTS) | HTTPS 강제 | `max-age=31536000; includeSubDomains; preload` |
| `Content-Security-Policy` (CSP) | XSS·인젝션 차단 | (별도 §10) |
| `X-Content-Type-Options` | MIME 스니핑 차단 | `nosniff` |
| `X-Frame-Options` | Clickjacking 차단 | `DENY` 또는 `SAMEORIGIN` |
| `Referrer-Policy` | Referer 누출 제어 | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | 브라우저 기능 제한 | `geolocation=(), camera=()` |
| `Cache-Control` | 민감 정보 캐싱 차단 | `no-store` for 인증 페이지 |

### Spring Security 기본 보안 헤더
```java
http
  .headers(h -> h
    .contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'"))
    .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true))
    .frameOptions(f -> f.deny())
    .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
  );
```

### 헤더 확인 도구
- https://securityheaders.com — URL 넣으면 점수
- 브라우저 DevTools → Network → Headers
- `curl -I https://example.com`

---

## 9. CSP (Content Security Policy) — XSS의 마지막 방어선

브라우저에 "이 사이트에서는 어디서 온 스크립트·스타일만 실행해라"고 명시.

### 예시 정책
```http
Content-Security-Policy:
  default-src 'self';
  script-src 'self' 'nonce-RAND123' https://cdn.jsdelivr.net;
  style-src 'self' 'unsafe-inline';
  img-src 'self' data: https:;
  connect-src 'self';
  frame-ancestors 'none';
  form-action 'self';
  base-uri 'self';
  report-uri /csp-report
```

### 안티패턴
- `'unsafe-inline'` 스크립트 — 인라인 스크립트 허용 = CSP 우회 매우 쉬움
- `'unsafe-eval'` — `eval()` 허용 = 위험
- `*` 와일드카드 — 사실상 CSP 없는 것과 동일

### 권장 패턴 (Thymeleaf)
```java
// 매 요청마다 nonce 생성하여 attribute로 전달
@ControllerAdvice
public class CspNonceAdvice {
    @ModelAttribute
    public void addNonce(Model model, HttpServletRequest req, HttpServletResponse res) {
        String nonce = generateNonce();  // SecureRandom 32바이트 base64
        model.addAttribute("cspNonce", nonce);
        res.setHeader("Content-Security-Policy",
            "default-src 'self'; script-src 'self' 'nonce-" + nonce + "'");
    }
}
```
```html
<!-- Thymeleaf에서 nonce 사용 -->
<script th:attr="nonce=${cspNonce}">
  // 정상 스크립트
</script>
```

---

## 10. Referer / Origin 헤더

| 헤더 | 무엇 | 신뢰 가능? |
|------|------|----------|
| `Referer` | 어느 페이지에서 왔는지 | 클라이언트가 변조 가능. CSRF 보조 검사용 |
| `Origin` | scheme+host+port 만 | POST/PUT/DELETE 요청에 자동 첨부. 변조 어려움 |

CSRF 1차 방어는 토큰, 2차 보조 방어로 Origin 헤더 검증.

---

## 11. 오늘의 실습

### 실습 2.1 — 본인 회사 사이트 헤더 점검
1. https://securityheaders.com 에서 회사 도메인 검색 (외부 노출 사이트만)
2. 점수와 결과를 저장
3. 부족한 헤더 3개를 골라 "왜 필요한가"를 한 줄로 정리

### 실습 2.2 — 쿠키 속성 분석
1. 자주 쓰는 웹사이트(예: github.com, naver.com) 로그인 후 DevTools → Application → Cookies
2. 세션 쿠키의 `HttpOnly`, `Secure`, `SameSite` 값 확인
3. JavaScript 콘솔에서 `document.cookie` 실행 → HttpOnly 쿠키가 안 보이는지 확인

### 실습 2.3 — curl로 HTTP 직접 다뤄보기
```bash
# 헤더만 보기
curl -I https://google.com

# 모든 요청·응답 보기
curl -v https://example.com

# POST with body
curl -X POST -d "user=alice&pass=secret" -c cookies.txt https://example.com/login

# 저장된 쿠키로 후속 요청
curl -b cookies.txt https://example.com/dashboard

# 헤더 변조
curl -H "X-Forwarded-For: 1.2.3.4" -H "User-Agent: AttackerBot" https://example.com
```

---

## 더 읽어볼 자료
- 📘 *HTTP: The Definitive Guide* — David Gourley
- 🔗 MDN HTTP 문서 (한국어 지원 좋음)
- 🔗 OWASP Secure Headers Project
- 🔗 https://content-security-policy.com — CSP 빌더
