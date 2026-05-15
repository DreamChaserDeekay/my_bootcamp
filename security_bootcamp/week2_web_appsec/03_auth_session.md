# Day 2 — 인증·세션·Spring Security

> 모든 서비스의 출입구. 여기서 무너지면 다른 모든 통제가 의미 없다.

## 1. 인증의 3요소

| 요소 | 예 |
|------|---|
| 알고 있는 것 (Knowledge) | 비밀번호, PIN, 보안 질문 |
| 가지고 있는 것 (Possession) | OTP 디바이스, 휴대폰, 보안 키(YubiKey), 인증서 |
| 자신인 것 (Inherence) | 지문, 얼굴, 홍채 |

**MFA (Multi-Factor)**: 서로 다른 카테고리 2개 이상. (비밀번호 + OTP = MFA, 비밀번호 + 보안질문 = MFA 아님)

---

## 2. 비밀번호 — 안전한 처리

### 2.1 절대 하지 말 것
- 평문 저장
- MD5, SHA1, SHA256 단순 해시 저장 (GPU로 초당 수십억 시도)
- 자체 알고리즘
- 비밀번호를 로그·에러·URL·이메일에 포함
- 동일 PW 재사용 검사 위해 평문 보관

### 2.2 올바른 알고리즘
**Adaptive (work factor 조정 가능) 해시**:
- **bcrypt** — 가장 널리 쓰임. Spring Security 기본.
- **scrypt** — 메모리 hard
- **Argon2id** — 최신 OWASP 권장 (2023~)
- **PBKDF2** — FIPS 인증 필요할 때

### 2.3 Spring Security 적용
```java
@Bean
PasswordEncoder passwordEncoder() {
    // Spring Security 6.x — 마이그레이션 가능한 DelegatingPasswordEncoder가 기본
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    // 저장 형태: "{bcrypt}$2a$10$..."
}

// 회원가입
String hash = passwordEncoder.encode(rawPassword);
userRepo.save(new User(username, hash));

// 로그인 검증 (직접)
if (passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
    // 성공
}
```

**Work factor**: bcrypt는 cost 10이 기본. CPU 발전에 따라 12~14로 올린다. 한 번 해시에 100~300ms 정도가 균형점.
```java
return new BCryptPasswordEncoder(12);
```

### 2.4 비밀번호 정책 (현대적)
NIST SP 800-63B (2017+) 권장:
- 최소 길이 8자 이상 (12자 권장)
- 최대 길이 64자 이상 허용 (긴 패스프레이즈 환영)
- 모든 ASCII·유니코드 허용
- **복잡도 요구(특수문자·숫자 강제) 의무 제거** — 길이가 더 중요
- 주기적 변경 강제 X (침해 의심 시에만)
- 알려진 유출 패스워드 차단 (haveibeenpwned API, 1Password 같은 매니저 권장)
- 사용자에게 패스워드 강도 표시 (`zxcvbn` 라이브러리)

```java
// haveibeenpwned k-anonymity API
String sha1 = sha1Upper(password);
String prefix = sha1.substring(0, 5);
String suffix = sha1.substring(5);
// GET https://api.pwnedpasswords.com/range/{prefix}
// 응답에 suffix가 있으면 → 유출됨, 거부
```

### 2.5 Brute-Force 방어
- **Rate Limiting**: IP/계정당 분당 N회
- **Captcha**: 임계 초과 시 challenge
- **점진적 지연** (300ms → 1s → 5s)
- **계정 잠금** (단, **사용자 열거 공격** 주의 — 존재하지 않는 계정은 잠그면 안 됨)
- **로그인 알림** (이메일·푸시)

```java
// Spring + bucket4j 예
private final Bucket bucket = Bucket.builder()
    .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
    .build();

@PostMapping("/login")
public String login(...) {
    if (!bucket.tryConsume(1)) {
        return "redirect:/login?error=too_many";  // 429
    }
    // ...
}
```

### 2.6 사용자 열거(User Enumeration) 방지
공격자가 "이 계정 있냐 없냐"를 알아내면 brute-force 표적 좁힘.

**누출 채널**:
- 로그인 실패 메시지가 "ID 없음" vs "PW 틀림" 다르면
- 응답 시간 차이 (없는 계정은 BCrypt 검증 안 해서 빠름 → 더미 검증 호출)
- 비밀번호 찾기에서 "해당 이메일 없음"
- 회원가입에서 "이미 가입된 이메일"

**대응**:
- 모든 경우 "ID 또는 비밀번호가 올바르지 않습니다" 동일 메시지
- 존재하지 않는 계정도 BCrypt를 한 번 수행해 시간 일정화
- 비밀번호 찾기: "이메일이 등록되어 있다면 발송됨"

```java
public boolean authenticate(String username, String password) {
    User user = userRepo.findByUsername(username);
    if (user == null) {
        // 더미 해시 비교로 응답 시간 일정화
        passwordEncoder.matches(password, DUMMY_HASH);
        return false;
    }
    return passwordEncoder.matches(password, user.getPasswordHash());
}
```

---

## 3. 세션 관리

### 3.1 세션 ID 생성·전달
- **충분히 긴 무작위** (128bit+, `SecureRandom`)
- HttpOnly + Secure + SameSite 쿠키
- URL에 포함 절대 X (Referer로 누출)

Spring Security 기본 JSESSIONID는 위 조건 충족.

### 3.2 세션 고정(Session Fixation) 공격
공격자가 자기 세션 ID를 피해자에게 강제 → 피해자 로그인 → 공격자가 동일 세션 ID로 접근.

**방어**: 로그인 직후 **세션 ID 재발급**.
```java
http.sessionManagement(s -> s
    .sessionFixation().migrateSession()  // 기본값, 명시적 권장
);
```

### 3.3 세션 수명
- 절대 만료(absolute timeout): N시간 후 강제 로그아웃
- 유휴 만료(idle timeout): 30분 활동 없으면 만료
- 로그아웃 시 서버 측 세션 무효화 (`session.invalidate()`)
- 비밀번호 변경 시 모든 세션 무효화

```yaml
server:
  servlet:
    session:
      timeout: 30m
```

### 3.4 동시 세션 제어
같은 계정 다중 로그인 차단·제한.
```java
http.sessionManagement(s -> s
    .maximumSessions(1)
    .maxSessionsPreventsLogin(false)  // true면 추가 로그인 거부, false면 기존 세션 만료
);
```

### 3.5 분산 환경
서버 N대 운영 시:
- **Sticky session**: LB에서 같은 사용자는 같은 서버로
- **세션 공유**: Redis/Hazelcast (Spring Session)
```java
@EnableRedisHttpSession
```

---

## 4. Spring Security 6.x — 안전한 기본 설정

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF: SSR이면 켠 채로 (Spring 기본 ON)
            .csrf(c -> c
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // /api/** 처럼 토큰 인증 쓰는 곳만 비활성화 가능
                // .ignoringRequestMatchers("/api/public/**")
            )
            // 인가
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/login", "/css/**", "/js/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            // 폼 로그인
            .formLogin(f -> f
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
            )
            // 로그아웃
            .logout(l -> l
                .logoutUrl("/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessUrl("/login?logout")
            )
            // 세션
            .sessionManagement(s -> s
                .sessionFixation().migrateSession()
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            )
            // 보안 헤더
            .headers(h -> h
                .contentSecurityPolicy(c -> c.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data:; " +
                    "frame-ancestors 'none'"))
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true))
                .frameOptions(f -> f.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            // 예외 처리
            .exceptionHandling(e -> e
                .accessDeniedPage("/403")
            );
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

---

## 5. 인가(Authorization) — URL vs Method

### URL 기반
```java
.requestMatchers("/admin/**").hasRole("ADMIN")
```
간단하지만 누락 위험.

### 메서드 기반 (강력 권장)
```java
@Service
public class OrderService {

    @PreAuthorize("hasRole('USER')")
    public Order create(...) { ... }

    @PreAuthorize("hasRole('USER') and @orderService.isOwner(#id, authentication.name)")
    public Order get(@P("id") Long id) { ... }

    @PreAuthorize("hasRole('ADMIN')")
    public List<Order> listAll() { ... }
}
```

### 도메인 객체 인가 (가장 자주 빠뜨림)
**원칙: 조회 시 항상 소유자 검사.**
```java
// ❌ 위험
@GetMapping("/orders/{id}")
public OrderView view(@PathVariable Long id) {
    return orderRepo.findById(id).orElseThrow();  // IDOR!
}

// ✅ 안전
@GetMapping("/orders/{id}")
public OrderView view(@PathVariable Long id, Authentication auth) {
    Order order = orderRepo.findById(id).orElseThrow();
    if (!order.getOwnerUsername().equals(auth.getName())) {
        throw new AccessDeniedException("Not your order");
    }
    return mapToView(order);
}

// ✅ 더 나은 패턴: 쿼리에 사용자 포함
@GetMapping("/orders/{id}")
public OrderView view(@PathVariable Long id, Authentication auth) {
    Order order = orderRepo.findByIdAndOwner(id, auth.getName())
        .orElseThrow(() -> new ResourceNotFoundException());
    return mapToView(order);
}
```

---

## 6. JWT — 알아야 할 함정 (보조 학습)

SSR에서는 기본 세션 권장이지만, API에 JWT를 쓰는 경우 알아둘 점:

### 6.1 흔한 취약점
| 취약점 | 설명 |
|--------|------|
| `alg: none` | 일부 라이브러리가 alg=none 받아들임 → 서명 검증 안 함 |
| RS256 ↔ HS256 confusion | 공개키를 HMAC 비밀로 써서 서명 위조 |
| 약한 시크릿 | brute-force로 시크릿 추출 |
| JKU/X5U 헤더 조작 | 공격자 키 URL로 변경 |
| 무한 수명·블랙리스트 부재 | 탈취 토큰 무효화 불가 |
| 민감정보 포함 | base64만 거치므로 평문이나 다름없음 |

### 6.2 안전 가이드
- 시크릿 32바이트 이상 무작위
- `alg` 고정 검증 (서버에서 RS256만 받음 등)
- 짧은 수명(15분) access + refresh 토큰
- 토큰 폐기 가능한 메커니즘 (Redis 블랙리스트, 버전 카운터)
- HttpOnly 쿠키에 저장 (LocalStorage 절대 X)
- audience(`aud`), issuer(`iss`), `exp`, `nbf` 검증

---

## 7. 비밀번호 재설정 흐름 (취약점 보고서 단골)

### 안전한 흐름
1. 사용자가 이메일 입력 → "등록된 이메일이면 발송됨" (열거 차단)
2. 서버: 32바이트 무작위 토큰 생성, 해시하여 DB 저장 (`SHA-256(token)`)
3. 이메일에 토큰 포함된 링크 (`/reset?token=...`)
4. 토큰: **만료 15~60분**, **1회용**, **사용자별**
5. 새 비밀번호 설정 → 토큰 무효화, **모든 세션 종료**
6. 이메일로 "비밀번호 변경됨" 알림

### 흔한 실수
- 토큰이 user_id (`?token=user_42`) — 예측 가능
- 토큰을 DB에 평문 저장 (DB 유출 시 즉시 악용)
- 만료 없음
- 사용된 토큰 재사용 가능
- 비밀번호 변경 후 세션 유지

---

## 8. OAuth 2.0 / OIDC — 짧게

외부 IdP(구글, 카카오 등)로 로그인할 때.

### 흐름 (Authorization Code with PKCE)
1. 사용자 → 클라이언트(웹앱) → "구글로 로그인"
2. 클라이언트가 인증 URL로 리다이렉트 (state, code_challenge 포함)
3. 사용자가 구글에서 동의 → 콜백 URL로 code 반환
4. 클라이언트(백엔드)가 code + code_verifier로 access token 교환
5. ID Token(JWT)에서 사용자 정보 추출, 자체 세션 생성

### 함정
- `state` 검증 안 함 → CSRF
- redirect_uri 검증 안 함 → 토큰 탈취 (open redirect 와 결합)
- Implicit Flow 사용 (deprecated, code+PKCE 사용)
- `nonce` 미검증

Spring `oauth2Login()` 사용 시 대부분 자동.

---

## 9. 실습

### 실습 3.1 — `vulnerable_app`의 약한 인증 패치
- 평문 PW 저장 → BCrypt
- 에러 메시지 다른 부분 → 통일
- Brute-force 무차단 → Rate Limit 추가

### 실습 3.2 — Session Fixation 시도
1. Burp에서 로그인 전 JSESSIONID 기록
2. 로그인 후 JSESSIONID 확인
3. 두 값이 다른지 확인 (다르면 `migrateSession` 동작)

### 실습 3.3 — Spring Security 설정 리뷰
본인 회사 또는 사이드 프로젝트의 `SecurityConfig`를 본 문서 §4와 비교:
- [ ] CSRF 켜져 있나
- [ ] HSTS 설정
- [ ] CSP 설정
- [ ] 세션 고정 방어
- [ ] PasswordEncoder DelegatingPasswordEncoder
- [ ] `@PreAuthorize` 활성화
- [ ] 도메인 객체 소유자 검사

### 실습 3.4 — 비밀번호 재설정 흐름 보안 점검
본인 회사 시스템 또는 외부 서비스 1개의 비밀번호 재설정 흐름을:
1. 이메일 토큰 길이·예측 가능성
2. 만료 시간
3. 1회용인지
4. 사용 후 세션 처리

---

## 정리
> 인증·세션은 **표준 라이브러리(Spring Security)** 와 **검증된 기본값**을 쓰는 것이 거의 항상 정답. 직접 구현 유혹을 피하고, 대신 *내가 라이브러리 어떤 기능을 어떻게 켜고 있는지* 정확히 알자.
