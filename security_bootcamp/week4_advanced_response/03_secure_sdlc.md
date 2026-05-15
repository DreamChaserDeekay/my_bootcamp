# Day 3 — Secure SDLC · 실전 위협 모델링 · 보안 코드 리뷰

## 1. Secure SDLC — 개발 라이프사이클의 모든 단계

```
[요구사항] → [설계] → [구현] → [검증] → [배포] → [운영] → [폐기]
  abuse       threat   secure    SAST/    Sign &     IR &     data
  cases       model    coding    DAST/    Release    Logging  destroy
                                 PenTest
```

### 단계별 활동
| 단계 | 주요 활동 |
|------|---------|
| 요구사항 | 보안 요구·어뷰즈 케이스·규제 |
| 설계 | 위협 모델링·아키텍처 리뷰·기술 선택 |
| 구현 | 시큐어 코딩 가이드·페어 프로그래밍 |
| 검증 | SAST/DAST/SCA·보안 테스트·코드 리뷰·Pen Test |
| 배포 | 서명·구성 검증·릴리즈 노트 |
| 운영 | 모니터링·패치·취약점 대응·인시던트 |
| 폐기 | 데이터 안전 삭제·키 폐기·계정 정리 |

### 프레임워크
- **OWASP SAMM** — 자가 평가·로드맵
- **BSIMM** — 업계 벤치마크
- **NIST SSDF** (SP 800-218)
- **Microsoft SDL**

---

## 2. 위협 모델링 — 실전 워크플로우

### 2.1 4가지 질문 (Adam Shostack)
1. 무엇을 만드는가? (Data Flow Diagram)
2. 무엇이 잘못될 수 있는가? (STRIDE)
3. 어떻게 대응하는가? (Mitigation)
4. 잘 했는가? (Validation)

### 2.2 1주차에 한 것을 다시 — 신기능 도입할 때마다

#### 단계 1: 시스템 다이어그램
- 컴포넌트, 데이터 흐름, 신뢰 경계 (Trust Boundary)
- 도구: Microsoft Threat Modeling Tool, OWASP Threat Dragon, 또는 손그림

#### 단계 2: STRIDE per element
각 컴포넌트·데이터 흐름에 대해 6개 위협 카테고리 적용:

| 컴포넌트 | S | T | R | I | D | E |
|---------|---|---|---|---|---|---|
| 사용자 → 웹 | 세션 탈취 | 요청 변조 | 부인 | 도청 | DDoS | - |
| 웹 → DB | DB 자격증명 위조 | SQLi | - | 평문 노출 | 풀 고갈 | DB 권한 상승 |
| 외부 API | 가짜 API 호출 | 응답 변조 | - | 키 노출 | API 한도 | - |

#### 단계 3: 미티게이션
각 위협에 대응:
- 기존 통제로 커버되는가?
- 새 통제가 필요한가?
- 수용 가능한 잔여 위험인가?

#### 단계 4: 검증
- 보안 테스트 케이스 작성
- 코드 리뷰 항목
- Pen Test 범위에 포함

### 2.3 다른 방법론
- **PASTA** — 7단계, 비즈니스 중심
- **OCTAVE** — 자산 중심
- **Attack Trees** — 트리로 공격 시나리오 분해
- **LINDDUN** — 프라이버시 위협 (PII 처리 시)
- **STRIDE-LM** — STRIDE + Lateral Movement (네트워크 측면)

---

## 3. 보안 코드 리뷰 체크리스트

### 3.1 입력 처리
- [ ] 모든 사용자 입력에 길이·형식 검증
- [ ] Bean Validation 사용 (`@Valid`, `@NotBlank`, `@Pattern`)
- [ ] 파일 업로드는 타입·크기·이름 검증
- [ ] URL 파라미터 변경 시도 (IDOR 점검)
- [ ] 정수·금액은 BigDecimal·overflow 안전 연산

### 3.2 인증·세션
- [ ] 비밀번호 BCrypt/Argon2
- [ ] 세션 ID 재발급(로그인 시)
- [ ] 로그아웃 시 세션 무효화
- [ ] Rate Limit (특히 로그인·재설정)
- [ ] 사용자 열거 방지

### 3.3 인가
- [ ] `@PreAuthorize` 활성
- [ ] 도메인 객체 소유자 검사
- [ ] 관리자 액션 별도 분리·MFA

### 3.4 출력·렌더링
- [ ] Thymeleaf `th:utext` 사용 없음
- [ ] CSP 헤더 적용
- [ ] 응답에 traceId만, 스택트레이스 X
- [ ] 카드번호·주민번호 마스킹

### 3.5 데이터·암호
- [ ] Prepared Statement만
- [ ] AES-GCM, Nonce 신선
- [ ] 키는 KMS·Secrets Manager
- [ ] TLS 1.2+ only
- [ ] 비밀 코드·로그·git에 없음

### 3.6 외부 통신
- [ ] HTTP 클라이언트 검증 안 끔
- [ ] 외부 URL을 사용자 입력으로 받지 않음 (SSRF)
- [ ] 응답 크기·타임아웃 제한

### 3.7 로깅·모니터링
- [ ] 보안 이벤트(로그인 성공/실패, 권한 거부) 로그
- [ ] 비밀·PII 마스킹
- [ ] 알림 룰 설정

### 3.8 의존성·설정
- [ ] 새 의존성에 CVE 점검
- [ ] 의존성 최신 (또는 의도된 버전 고정)
- [ ] Actuator/Swagger 등 디버그 도구 비공개

---

## 4. PR 단계 보안 리뷰 — 자동화

`.github/CODEOWNERS`:
```
# 보안 민감 경로는 보안 팀 필수 리뷰
src/main/java/com/example/security/   @company/security
src/main/resources/application.yml    @company/security
infra/                                 @company/security
Dockerfile                             @company/security
```

PR 템플릿에 보안 체크리스트:
```markdown
## Security checklist
- [ ] 신규 입력은 검증되는가
- [ ] 인증·인가 영향이 있는가
- [ ] 새 의존성에 CVE 없는가
- [ ] 비밀이 코드에 없는가
- [ ] 위 외 위협 모델링 필요한 변경인가
```

---

## 5. 시큐어 코딩 표준 — Java/Spring

작은 단편들:

```java
// 1. 입력 검증 (서버 측 필수)
@PostMapping("/api/orders")
public ResponseEntity<Long> create(@Valid @RequestBody OrderForm form) { ... }

// 2. Prepared Statement
jdbcTemplate.query("SELECT * FROM t WHERE id = ?", new Object[]{id}, mapper);

// 3. ProcessBuilder는 항상 배열
new ProcessBuilder("convert", input, output).start();

// 4. 안전한 난수
SecureRandom rng = new SecureRandom();
byte[] token = new byte[32];
rng.nextBytes(token);

// 5. 비밀번호 비교는 상수 시간
MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));

// 6. URL 클라이언트
HttpRequest req = HttpRequest.newBuilder(URI.create(safeUrl))
    .timeout(Duration.ofSeconds(5))
    .build();

// 7. Path 안전
Path base = Paths.get("/uploads").toAbsolutePath().normalize();
Path target = base.resolve(name).normalize();
if (!target.startsWith(base)) throw new SecurityException();

// 8. JSON 역직렬화 (Jackson)
ObjectMapper m = JsonMapper.builder()
    .disable(MapperFeature.AUTO_DETECT_FIELDS)
    .build();
// activateDefaultTyping 절대 X

// 9. XML 파서 외부 엔티티 차단
dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

// 10. 예외는 위로 던지고 통합 핸들러에서 처리
```

### Java Secure Coding Guidelines
- Oracle Secure Coding Guidelines for Java SE
- CERT Oracle Secure Coding Standard for Java
- OWASP Java Encoder, ESAPI

---

## 6. 보안 인터뷰·코드 리뷰 패턴

### "왜?" 5번 묻기 — 근본 원인 분석
사고가 났을 때, 단순 패치로 끝내지 말고 깊이 묻기.
1. 왜 사고가 났나? — SQLi 발생
2. 왜 SQLi가 가능했나? — 문자열 concat
3. 왜 문자열 concat을 썼나? — MyBatis ${} 사용
4. 왜 ${}를 썼나? — 동적 컬럼명 필요
5. 왜 화이트리스트 없었나? — 코딩 가이드 부재

→ 결과: 코딩 가이드 추가 + SAST 룰 + 보안 교육.

### 어뷰즈 케이스 사고
정상 케이스 외:
- 음수, 0, MAX_VALUE
- 빈 문자열, 매우 긴 문자열
- 특수문자, 유니코드, RTL, zero-width
- 동시 요청 (race condition)
- 부분 실패 (네트워크 끊김)
- 권한 없는 사용자
- 다른 사용자의 ID

---

## 7. 보안 챔피언 프로그램

각 팀에 **보안 챔피언** 1명. 보안 팀과 개발팀의 다리.
- 정기 미팅
- 신기능 위협 모델링 시 동참
- 코드 리뷰 보안 관점 책임
- 사내 보안 정보 전파

규모 100명 이상 조직에서 효과적.

---

## 8. 실습

### 실습 3.1 — 본인 시스템 위협 모델링 v2
Week 1의 v0.1을 다시 보고, 지난 3주 학습을 반영해 v1.0 작성.

### 실습 3.2 — 보안 PR 체크리스트 적용
본인 사이드 프로젝트 또는 회사 PR 템플릿에 §3 체크리스트 일부 추가.

### 실습 3.3 — 코드 리뷰 시뮬레이션
WebGoat 또는 OWASP Juice Shop 소스에서 취약 코드를 찾아 PR 코멘트 형식으로 리뷰 작성.

### 실습 3.4 — OWASP SAMM 자가 평가
https://owaspsamm.org/assessment/
본인 조직(또는 사이드 프로젝트)의 수준 평가, 다음 단계 목표 설정.

---

## 정리
- 보안은 라이프사이클 전반에 녹여야 지속 가능
- 위협 모델링은 **매 신기능 마다**
- 자동화(SAST/SCA/DAST)와 사람 검토(코드 리뷰·Pen Test)는 **상호 보완**
