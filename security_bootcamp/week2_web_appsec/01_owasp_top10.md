# Day 1 (1/2) — OWASP Top 10 2021 개관

> OWASP Top 10은 "가장 위험한 10가지"가 아니라 "**가장 흔히 발견되고 영향이 큰 카테고리**"다. 거의 모든 보안 사고가 이 안에 들어간다.

## 한눈에 보기

| 순위 | 카테고리 | 핵심 한 줄 | 대표 공격 |
|------|--------|--------|--------|
| **A01** | Broken Access Control | "남의 것을 볼 수 있다" | IDOR, 권한 우회 |
| **A02** | Cryptographic Failures | "잘못된 암호화·평문 노출" | 평문 저장, 약한 알고리즘, TLS 미적용 |
| **A03** | Injection | "데이터가 코드가 된다" | SQLi, XSS, OS command, LDAP |
| **A04** | Insecure Design | "근본 설계가 나쁘다" | 비즈니스 로직 우회, 위협 모델 부재 |
| **A05** | Security Misconfiguration | "기본 설정·디버그 노출" | Actuator 노출, S3 public, 기본 패스워드 |
| **A06** | Vulnerable & Outdated Components | "라이브러리 CVE 방치" | Log4Shell, Struts |
| **A07** | Identification & Auth Failures | "인증 자체가 깨짐" | 약한 PW, 세션 고정, 무차별 |
| **A08** | Software & Data Integrity Failures | "검증 없는 업데이트·역직렬화" | 신뢰 안 된 소스 dependency, Java deser |
| **A09** | Security Logging & Monitoring Failures | "모르거나 늦게 안다" | 로그 없음, 알림 없음 |
| **A10** | Server-Side Request Forgery | "서버가 우리 대신 어디든 요청" | 메타데이터 서비스, 내부 망 스캔 |

> 💡 2017 vs 2021 변화: XSS는 A03 Injection으로 통합, SSRF가 새로 추가, "Insecure Design"이라는 메타 카테고리 신설.

---

## 카테고리별 — Spring/Thymeleaf 맥락

### A01 — Broken Access Control (29%로 가장 흔함)
**증상**: 인증된 사용자가 자기 권한 밖 리소스 접근.
- **IDOR**: `GET /orders/123` 에서 `id`만 바꿔 남의 주문 조회
- **수직 권한 상승**: 일반 사용자 → 관리자 API 호출 성공
- **수평 권한 상승**: 사용자 A가 B의 데이터 조회·수정
- **강제 브라우징**: URL을 알면 인증 화면 우회

**Spring 대응 포인트**:
- `@PreAuthorize("hasRole('ADMIN')")` / `@PostAuthorize` 적극 사용
- 도메인 객체 접근 시 **소유자 검사 필수** (`order.userId == currentUserId`)
- URL 기반 매칭만으로 부족, 메서드 레벨 보안

```java
// ❌ URL만 보호
http.authorizeHttpRequests(a -> a
    .requestMatchers("/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated());

// ✅ + 메서드 보안 + 소유자 검사
@PreAuthorize("@orderService.isOwner(#id, authentication.name)")
@GetMapping("/orders/{id}")
public OrderView view(@PathVariable Long id) { ... }
```

### A02 — Cryptographic Failures
- HTTPS 미적용·혼합 컨텐츠
- 비밀번호 MD5/SHA1 저장 (반드시 BCrypt/Argon2/scrypt/PBKDF2)
- 자체 만든 암호화 (절대 금지)
- 약한 난수 (`Math.random()`, `Random` → `SecureRandom` 사용)
- ECB 모드, IV 재사용
- 키 하드코딩

### A03 — Injection
- SQL, NoSQL, OS Command, LDAP, XPath, **JNDI/Log4Shell**, SpEL (Thymeleaf!)
- XSS도 여기 포함

### A04 — Insecure Design
**가장 모호하지만 가장 중요.** 코드 수준이 아닌 **설계** 수준 결함.
- 비밀번호 재설정 토큰을 user_id로 사용 (예측 가능)
- 한도 검사 없는 인출 API
- 송금 한도가 클라이언트에서 결정
- 위협 모델링 안 한 신기능
- **방어**: 위협 모델링, 보안 요구사항, 테스트 케이스에 어뷰즈 케이스 포함

### A05 — Security Misconfiguration
- Spring Actuator 무인증 노출
- `application.properties`에 `logging.level.root=DEBUG` 운영 반영
- 기본 관리자 계정 미변경 (Tomcat manager, Jenkins admin)
- 디렉토리 인덱싱
- 불필요한 HTTP 메서드 열림 (TRACE, PUT)
- CORS 와일드카드
- 에러 페이지 스택트레이스

### A06 — Vulnerable & Outdated Components
**Log4Shell(CVE-2021-44228)** 이 좋은 예. Spring4Shell, Struts2, OpenSSL HeartBleed.
- Snyk, Dependabot, OWASP Dependency-Check, GitHub Advanced Security
- Java: Maven/Gradle BOM, Spring Boot dependency management
- **Software Bill of Materials (SBOM)**: `cyclonedx-maven-plugin`

### A07 — Identification & Authentication Failures
- Brute-force 미차단 (Rate Limit)
- 자동완성·세션 고정
- 비밀번호 정책 빈약
- 다단계 인증 부재
- 비밀번호 재설정 흐름 결함 (이메일 토큰 예측, 사용자 열거)

### A08 — Software & Data Integrity Failures
- Maven Central 외 비공식 저장소 사용
- CI/CD 파이프라인에서 빌드 결과물 무결성 검증 안 함
- **Java Deserialization** 취약점 (Apache Commons Collections)
- Auto-update 시 서명 검증 안 함

### A09 — Security Logging & Monitoring Failures
- 인증 실패·권한 거부·관리자 행위 로그 부재
- 로그에 민감정보 평문(PII, 카드번호)
- 로그 변조 가능 (append-only 아님)
- 알림 미설정 (1년 후에야 발견)

### A10 — Server-Side Request Forgery (SSRF)
- 사용자 입력 URL을 서버가 fetch
- 클라우드 메타데이터 서비스 노출 (AWS `169.254.169.254`)
- 내부 망 포트 스캔 (`http://10.0.0.1:8080/`)

---

## OWASP Top 10 외에 알아야 할 것

- **OWASP API Security Top 10** — API 전용 (BOLA, Excessive Data Exposure)
- **OWASP Mobile Top 10**
- **OWASP LLM Top 10** (2023~) — Prompt Injection, Insecure Output Handling
- **CWE Top 25** — 코드 결함 분류 (MITRE)

---

## 학습 순서 (이번 주)

1. Injection → 가장 흔하고 영향 크다
2. Auth/Session → 모든 서비스의 출입구
3. XSS/CSRF → 클라이언트 측 공격
4. Access Control → 비즈니스 로직 결함
5. SSRF/Deserialization → 백엔드 깊은 곳
6. Crypto/Logging/Misconfig → 전반

---

## 오늘의 실습

- [ ] OWASP 공식: https://owasp.org/Top10/ 를 한 번 통독 (영문/한글 둘 다 있음)
- [ ] 본인 회사 서비스에 Top 10 각 항목별 "있다/없다/모름" 표 만들기
- [ ] WebGoat에서 A01 챕터 첫 5문제 풀기
