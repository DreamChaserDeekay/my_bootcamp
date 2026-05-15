# Day 5 (2/2) — 안전한 로깅·에러 처리

## 1. 에러 처리 — 정보 누출의 단골

### 1.1 절대 노출하지 말 것
- 스택 트레이스
- DB 에러 메시지 (테이블·컬럼명)
- 파일 시스템 경로
- 내부 IP·호스트명
- 라이브러리·버전 정보
- 사용자 열거 가능한 메시지 차이

### 1.2 Spring Boot 기본 — Whitelabel Error Page 점검
```yaml
server:
  error:
    include-message: never               # 기본 never 권장
    include-stacktrace: never            # 절대 never
    include-binding-errors: never
    include-exception: false
    whitelabel:
      enabled: true                      # 커스텀 시 false + /error 핸들러
```

### 1.3 통일된 예외 처리
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException e) {
        return ResponseEntity.status(404)
            .body(new ApiError("NOT_FOUND", "요청하신 자원을 찾을 수 없습니다."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException e) {
        return ResponseEntity.status(403)
            .body(new ApiError("FORBIDDEN", "접근 권한이 없습니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.status(400)
            .body(new ApiError("VALIDATION", "입력값이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unexpected error [{}] {} {}", traceId, req.getMethod(), req.getRequestURI(), e);
        // 로그에는 자세히, 응답에는 traceId만
        return ResponseEntity.status(500)
            .body(new ApiError("INTERNAL", "처리 중 오류가 발생했습니다. 문의 시 ID: " + traceId));
    }
}
```

**핵심**: 로그는 자세히, 응답은 추상적으로. **traceId/correlationId**로 추후 디버깅.

### 1.4 SSR 페이지 에러
Thymeleaf 환경에서 `/error` 페이지를 커스텀:
```java
@Controller
public class CustomErrorController implements ErrorController {
    @RequestMapping("/error")
    public String handleError(HttpServletRequest req, Model model) {
        Integer status = (Integer) req.getAttribute("javax.servlet.error.status_code");
        if (status != null) {
            if (status == 404) return "errors/404";
            if (status == 403) return "errors/403";
        }
        return "errors/500";
    }
}
```

`templates/errors/500.html`:
```html
<h1>오류가 발생했습니다</h1>
<p>잠시 후 다시 시도해 주세요.</p>
<p>문의 ID: <span th:text="${traceId}"></span></p>
<!-- 스택트레이스·에러 메시지 절대 표시 X -->
```

---

## 2. 로깅 — 무엇을 / 무엇을 안 / 어떻게

### 2.1 반드시 로그해야 할 이벤트 (보안)
- 로그인 성공·실패
- 비밀번호 변경
- 권한 변경·관리자 행위
- 권한 거부(`403`)
- 의심스러운 입력(SQL 패턴, 비정상 파라미터)
- 결제·송금·환불 등 금전 거래
- 파일 업로드·다운로드
- 외부 시스템 호출 결과 (실패 포함)
- 에러 (500)

각 이벤트에:
- timestamp (ISO-8601, UTC)
- traceId
- 사용자 ID (인증된 경우) — 민감하면 hash
- IP (X-Forwarded-For 검증 후)
- User-Agent
- 메서드·경로
- 결과·상태 코드

### 2.2 절대 로그하지 말 것
- 비밀번호 (해시도 가급적 X)
- 신용카드 번호, CVV
- 주민등록번호, 여권번호
- API 키, 액세스 토큰, 세션 ID
- 개인정보 PII (이름, 주소, 전화번호) — 마스킹 또는 ID로
- 의료·금융 민감정보

### 2.3 자동 마스킹
Logback에 정규식 필터:
```xml
<conversionRule conversionWord="msg" converterClass="ch.qos.logback.classic.pattern.MessageConverter"/>

<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <fieldNames>
            <message>message</message>
        </fieldNames>
    </encoder>
    <filter class="ch.qos.logback.core.filter.EvaluatorFilter">
        <!-- 카드번호 패턴 자동 마스킹 -->
    </filter>
</appender>
```
또는 객체 toString 오버라이드:
```java
public class User {
    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', email='" + mask(email) + "'}";
        // password, ssn 등 제외
    }
}
```

### 2.4 구조화된 로깅 (Structured Logging)
JSON 로그가 검색·집계에 유리.
```java
log.info("user.login.success", kv("userId", id), kv("ip", ip));
// → {"event":"user.login.success", "userId":"...", "ip":"...", "ts":"..."}
```

### 2.5 로그 무결성·보존
- **append-only**: 운영 로그는 수정 불가
- **중앙 집중**: ELK, Splunk, CloudWatch, Loki (단일 서버 로그는 침해 시 지움)
- **보존 기간**: 침입 발견까지 평균 200일 이상 → 최소 1년 권장 (산업·규제 따라 다름)
- **법적 요건**: 개인정보보호법(국내) — 침해 사고 시 신고에 로그 필요

---

## 3. 모니터링·알림 — A09

로그만 남기고 안 보면 의미 없음.

### 3.1 알림 대상 이벤트
- 로그인 실패 임계 초과 (예: 5분간 50회)
- 권한 거부 폭주
- 5xx 에러율 급증
- 비정상 트래픽 (단일 IP에서 10x 평소)
- DB 연결 풀 고갈
- 디스크·메모리 임계
- 신규 admin 계정·역할 변경
- SSL 인증서 만료 임박

### 3.2 도구
- **Prometheus + Grafana** + Alertmanager
- **Datadog**, New Relic
- **Sentry** — 에러 추적 (스택트레이스 안전하게)
- **CloudWatch Alarms** (AWS)
- **WAF + IDS** 로그도 모니터링 대상

### 3.3 SOAR / SIEM
규모 커지면:
- **SIEM**: Splunk, Elastic Security, Wazuh — 상관 분석
- **SOAR**: 자동 대응 (자동 IP 차단 등)

---

## 4. 입력 검증 — Bean Validation

```java
public class UserSignupForm {
    @NotBlank
    @Size(min = 3, max = 30)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$")
    private String username;

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @NotBlank
    @Size(min = 12, max = 128)
    private String password;
}
```

```java
@PostMapping("/signup")
public String signup(@Valid @ModelAttribute UserSignupForm form, BindingResult br) {
    if (br.hasErrors()) {
        return "signup";  // 같은 페이지로 (Thymeleaf에서 error 표시)
    }
    // ...
}
```

### 검증의 두 가지 의미
- **데이터 무결성**: 정상 사용자 보호 (에러 메시지)
- **보안**: 공격 차단의 첫 줄 (단, 의존하지 말 것 — 다층 방어)

---

## 5. Rate Limiting — DoS·Brute-force 방어

### 5.1 적용 대상
- 로그인
- 비밀번호 재설정 요청
- 회원가입 (스팸)
- 검색·OTP·인증번호
- 비싼 API (보고서 생성, 통계)
- 모든 외부 노출 API (전체 IP·계정당)

### 5.2 라이브러리
- **Bucket4j** (자바, 토큰 버킷)
- **Resilience4j RateLimiter**
- **Spring Cloud Gateway**의 RequestRateLimiter
- 인프라 레벨: **nginx limit_req**, **Cloudflare**, **AWS WAF**

### 5.3 예
```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final LoadingCache<String, Bucket> buckets =
        Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .build(ip -> Bucket.builder()
                .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
                .build());

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String ip = req.getRemoteAddr();   // X-Forwarded-For 처리는 trust 후
        Bucket b = buckets.get(ip);
        if (!b.tryConsume(1)) {
            res.setStatus(429);
            res.getWriter().write("Too Many Requests");
            return;
        }
        chain.doFilter(req, res);
    }
}
```

---

## 6. 실습

### 실습 9.1 — 에러 응답 점검
본인 회사 또는 사이드 프로젝트의 운영 환경에서:
- `/nonexistent` → 응답 확인
- 잘못된 JSON POST → 응답 확인
- DB 에러를 일부러 발생시켜 응답 확인 (필드명 노출?)

### 실습 9.2 — 로그 점검
최근 로그 1주일치 grep:
```
grep -iE "password|api_key|token|secret" application.log | head
grep -iE "[0-9]{13,16}" application.log  # 카드번호 패턴
```
발견 시 즉시 마스킹 추가.

### 실습 9.3 — 알림 룰 1개 구현
"5분간 5xx가 5% 이상" 같은 룰을 Prometheus/CloudWatch에서 만들고 Slack 연동.

### 실습 9.4 — Rate Limit
`/login`에 분당 5회 제한 적용. Burp Intruder로 100회 시도 → 429 확인.

---

## 정리 — 다층 방어 정리표

| 계층 | 통제 |
|------|------|
| 네트워크 | 방화벽, WAF, DDoS, TLS, mTLS |
| 인증·세션 | MFA, BCrypt, 세션 ID 재발급, 만료 |
| 인가 | RBAC + 도메인 객체 소유자 검사 + Method Security |
| 입력 | Bean Validation, 길이·형식, 화이트리스트 |
| 처리 | Prepared Statement, ProcessBuilder array, SafeXML |
| 출력 | th:text, HTML escape, JSON, CSP |
| 저장 | 암호화 at rest, KMS, 백업 |
| 로그·모니터 | 구조화 로그, 마스킹, 알림 |
| 응답 | 보안 헤더, 추상 에러 메시지 |
| 운영 | 패치, 의존성 스캔, Pen Test |
