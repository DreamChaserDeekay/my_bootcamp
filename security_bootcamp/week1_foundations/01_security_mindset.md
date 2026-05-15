# Day 1 — 보안 마인드셋과 핵심 원칙

## 1. 공격자의 사고방식 (Adversarial Mindset)

개발자는 "어떻게 정상 동작시킬까"를 고민하지만, 공격자는 "**의도하지 않은 동작을 어떻게 만들어낼까**"를 고민한다. 같은 코드를 다른 렌즈로 본다.

### 개발자 vs 공격자 시점 비교

| 코드/기능 | 개발자 시점 | 공격자 시점 |
|----------|------------|------------|
| 로그인 폼 | "ID/PW 입력 받아 인증" | "ID에 `' OR 1=1--` 넣으면? Brute-force는? 비밀번호 재설정 우회는?" |
| 파일 업로드 | "이미지 받아서 S3에 저장" | "`.jsp` 올리면? 10GB 업로드는? `../../etc/passwd`로 경로 탈출은?" |
| URL 파라미터 `?id=123` | "사용자 ID로 조회" | "`?id=124`로 바꾸면 남의 데이터가 보이나? (IDOR)" |
| 에러 메시지 | "사용자 친화적인 안내" | "스택 트레이스에 DB 스키마·서버 경로 노출되나?" |
| HTTP 응답 헤더 | "Content-Type만 신경" | "Server, X-Powered-By로 버전 노출? 보안 헤더는?" |

### 핵심 질문 5가지 (코드 리뷰 시 반드시 던질 것)

1. **신뢰 경계(Trust Boundary)** 가 어디인가? — 사용자 입력은 모두 신뢰 경계 바깥에서 옴
2. 인증·인가는 누가, 언제 검사하는가? — 클라이언트가 검사하면 의미 없음
3. 이 데이터가 위변조되면 어떻게 되는가?
4. 100배 더 큰 입력이 들어오면 어떻게 되는가? — DoS 관점
5. 실패 시 어떻게 페일하는가? — Fail-safe인가, Fail-open인가?

---

## 2. CIA Triad — 정보보안의 3대 축

| 속성 | 의미 | 깨졌을 때 결과 | 대표 통제 |
|-----|------|-------------|---------|
| **Confidentiality (기밀성)** | 인가된 자만 접근 가능 | 정보 유출 | 암호화, 접근 통제, 마스킹 |
| **Integrity (무결성)** | 데이터가 변조되지 않음 | 위·변조, 데이터 손상 | 해시, 서명, 트랜잭션, 감사 로그 |
| **Availability (가용성)** | 필요할 때 사용 가능 | 서비스 중단 | 이중화, 백업, DDoS 방어, Rate Limiting |

### 확장된 모델: Parkerian Hexad
CIA에 **Possession(소유), Authenticity(진정성), Utility(유용성)** 추가. 예: USB를 도난당했지만 데이터는 암호화돼 있다면 기밀성은 유지되어도 *소유성*은 깨진 것.

---

## 3. AAA — 인증·인가·감사

- **Authentication (인증)**: 너는 누구냐? → 비밀번호, OTP, 인증서, 생체
- **Authorization (인가)**: 너는 무엇을 할 수 있냐? → RBAC, ABAC, ACL
- **Accounting/Auditing (감사)**: 너는 무엇을 했냐? → 로그, 모니터링

**자주 혼동되는 포인트**: 인증과 인가는 다른 것. 로그인했다고 모든 권한이 부여되는 게 아니다. **인가는 매 요청마다** 검사해야 한다.

---

## 4. 보안 설계 원칙 (Saltzer & Schroeder 1975 + 현대 확장)

부트캠프 내내 돌아올 핵심 원칙. 외워두자.

### 4.1 Least Privilege (최소 권한)
모든 사용자·프로세스·코드는 **꼭 필요한 최소 권한만** 가진다.
- 예: 웹 앱이 DB에 SELECT만 필요한데 SYSDBA로 접속? → 위반
- 예: 컨테이너를 root로 실행? → 위반

### 4.2 Defense in Depth (심층 방어)
단일 통제에 의존하지 않고 **여러 계층으로 방어**. 하나가 뚫려도 나머지가 막는다.
- WAF + 입력 검증 + Prepared Statement + DB 권한 분리

### 4.3 Fail Securely (안전한 실패)
오류 발생 시 **안전한 기본 상태로**. 인증 검사에서 예외 발생 시 "통과시킴"이 아니라 "차단".

```java
// 나쁨: 예외 시 통과
try {
    if (authService.isAdmin(user)) { return adminView(); }
} catch (Exception e) { return adminView(); }  // ❌ Fail-open

// 좋음: 예외 시 차단
try {
    if (authService.isAdmin(user)) { return adminView(); }
    return forbidden();
} catch (Exception e) { return forbidden(); }   // ✅ Fail-secure
```

### 4.4 Separation of Duties (직무 분리)
하나의 사람·시스템이 전체 위험을 통제하지 못하게.
- 코드 작성자와 배포 승인자 분리
- DB 변경과 백업 운영자 분리

### 4.5 Don't Trust the Client
**클라이언트는 절대 신뢰하지 않는다.** 브라우저는 사용자가 완전히 통제하는 환경.
- 가격 계산을 자바스크립트로? → 변조 가능
- 폼 validation만 클라이언트? → 우회됨
- "관리자 메뉴 숨김"으로만 보호? → URL 직접 요청

### 4.6 Secure by Default
기본 설정이 안전해야 한다. 사용자가 "보안 설정"을 켜야 안전하면 99%는 안 켠다.
- Spring Security는 기본적으로 모든 엔드포인트를 인증 요구함 — 좋은 예
- 일부 옛 프레임워크는 디버그 모드가 기본 ON — 나쁜 예

### 4.7 KISS / Economy of Mechanism
복잡한 보안 메커니즘은 버그를 만든다. **단순한 메커니즘이 검증하기 쉽고 안전**하다.

### 4.8 Open Design (Kerckhoffs's Principle)
보안은 **알고리즘의 비밀**이 아니라 **키의 비밀**에 의존해야 한다.
- 자체 암호화 알고리즘 만들지 말 것. 검증된 표준(AES, RSA, ChaCha20)을 사용.

### 4.9 Zero Trust
"네트워크 내부는 안전하다"는 가정을 버린다. **모든 요청을 매번 검증**한다.

---

## 5. 위협 모델링 — STRIDE

Microsoft가 만든 위협 분류. 새 기능 설계 시 항목별로 "이게 일어나면?"을 따져본다.

| 약자 | 위협 | 깨는 속성 | 예시 |
|------|------|---------|------|
| **S**poofing | 사칭/위장 | Authentication | 다른 사용자로 로그인, IP 위조 |
| **T**ampering | 위변조 | Integrity | 요청 파라미터 변조, DB 직접 변경 |
| **R**epudiation | 부인 | Non-repudiation | "나 안 했음" 주장 (로그 없으면 입증 불가) |
| **I**nformation Disclosure | 정보 노출 | Confidentiality | 에러에 스택 트레이스 노출, IDOR |
| **D**enial of Service | 서비스 거부 | Availability | DDoS, 큰 페이로드, 무한 루프 유도 |
| **E**levation of Privilege | 권한 상승 | Authorization | 일반 사용자 → 관리자 |

### 실전 적용: 게시판 글쓰기 기능

| 위협 | 시나리오 | 대응 |
|------|---------|------|
| S | 다른 사람 이름으로 글 작성 | 세션의 user_id를 서버에서 강제 사용, 폼에 author_id 안 받음 |
| T | 다른 사람 글 수정 (글 ID 변조) | 수정 시 작성자 == 현재 사용자 검사 |
| R | "그 글 내가 안 썼다" 주장 | 작성자·IP·시각 감사 로그 |
| I | 비공개 글 조회 (`?id=123`) | 권한 검사 + 검색 시 본인 글만 노출 |
| D | 10MB 글 1초당 100개 작성 | Rate Limit, 본문 길이 제한 |
| E | 일반 유저가 관리자 글 수정 | 역할 기반 권한 검사 |

---

## 6. 위협 모델링 — DREAD (위험 점수화, 옵션)

위협을 5개 항목 1~10점으로 평가해 우선순위 결정.

- **D**amage Potential
- **R**eproducibility
- **E**xploitability
- **A**ffected Users
- **D**iscoverability

> ⚠ DREAD는 주관성이 높아 최근에는 잘 안 쓰지만, 팀 내 토론 도구로는 유용. CVSS를 더 권장.

### CVSS (Common Vulnerability Scoring System)
취약점에 0~10점 부여하는 산업 표준. NVD·KISA·벤더 어드바이저리는 모두 CVSS로 표기.
- 9.0~10.0: Critical (즉시 패치)
- 7.0~8.9: High
- 4.0~6.9: Medium
- 0.1~3.9: Low

---

## 7. OWASP — 알아둬야 할 자료

> **OWASP (Open Web Application Security Project)** — 웹 보안의 사실상 표준.

| 자료 | 용도 |
|------|------|
| **OWASP Top 10** | 가장 위험한 웹 취약점 10가지 (3~4년마다 갱신, 최신 2021판) |
| **OWASP ASVS** | 보안 검증 표준 (요구사항 체크리스트) |
| **OWASP Cheat Sheets** | 주제별 짧은 가이드 (XSS, SQLi, JWT, …) — 매우 실용적 |
| **OWASP WSTG** | 웹 시큐리티 테스팅 가이드 (테스터·QA용) |
| **OWASP Dependency-Check** | 의존성 취약점 스캐너 |
| **OWASP ZAP** | 자동 웹 취약점 스캐너 |
| **OWASP Juice Shop** | 학습용 의도적 취약 앱 |

📌 **북마크 필수**: https://cheatsheetseries.owasp.org/

---

## 8. 실제 사례 분석

### Case 1: Equifax (2017) — Apache Struts CVE-2017-5638
- **사고**: 1.47억 명 미국인 개인정보(SSN 포함) 유출
- **원인**: Struts2 OGNL Injection 패치를 2개월간 적용 안 함
- **위반 원칙**: Defense in Depth 부재(WAF·세그멘테이션 미흡), 패치 관리 실패
- **교훈**:
  - 의존성 취약점 모니터링은 *제품의 일부*다. (`Dependency-Check`, Snyk, Dependabot)
  - 알려진 취약점이 가장 많이 악용된다. 0-day가 아니라 N-day.

### Case 2: Capital One (2019) — SSRF + IMDS
- **사고**: 1억 600만 명 신용카드 신청자 정보 유출
- **원인**: 잘못 구성된 WAF에서 SSRF → AWS EC2 메타데이터 서비스(IMDSv1) → 임시 자격 증명 탈취 → S3 접근
- **위반 원칙**: Least Privilege (IAM 과다 권한), Zero Trust
- **교훈**:
  - SSRF는 외부 공격 → 내부 자원 접근의 핵심 벡터
  - 클라우드는 IAM이 곧 보안 경계 — IMDSv2 강제, 인스턴스 역할 최소화

### Case 3: SolarWinds (2020) — Supply Chain
- **사고**: SolarWinds Orion 업데이트에 백도어 삽입 → 18,000개 조직 침투 (미 정부·MS 등)
- **원인**: 빌드 파이프라인 침해
- **교훈**:
  - 의존성·빌드 파이프라인·서명 키도 보안 자산
  - SBOM(Software Bill of Materials) 관리 필요

### Case 4: 국내 — 인터파크 (2016)
- **사고**: 1,030만 명 회원 정보 유출
- **원인**: 직원 PC에 스피어 피싱으로 악성코드 → 내부망 침투 → DB 탈취
- **교훈**:
  - 사람이 가장 약한 고리. 보안 교육 + 망 분리 + 권한 분리 필수
  - 외부 공격뿐 아니라 내부 침해 시나리오를 모델링해야 함

### Case 5: 국내 — KISA NIS 침해 시도, 카카오 데이터센터 화재 (2022)
- 가용성 측면에서 단일 IDC 의존이 만든 사회적 영향
- **교훈**: Availability도 보안의 일부. 재해 복구·다중 리전 설계

---

## 9. 오늘의 실습

### 실습 1.1 — 본인 프로젝트의 위협 모델 작성
다음 양식으로 본인의 사이드 프로젝트(또는 회사 시스템 중 1개 기능)의 STRIDE 위협 모델 v0.1을 작성한다.

```markdown
# 시스템: [이름]
## 데이터 흐름
사용자 → 브라우저(Thymeleaf) → Spring 컨트롤러 → Service → DB
                                       ↓
                                   외부 API

## 신뢰 경계
- 사용자 ↔ 웹 (HTTPS 경계)
- 웹 앱 ↔ DB (네트워크 경계)
- 웹 앱 ↔ 외부 API

## STRIDE 분석
| 컴포넌트 | S | T | R | I | D | E |
|---------|---|---|---|---|---|---|
| 로그인 폼 | … | … | … | … | … | … |
| 게시글 작성 | … | … | … | … | … | … |
| 파일 업로드 | … | … | … | … | … | … |
```

### 실습 1.2 — 본인 코드 1개에 핵심 질문 5가지 적용
가장 최근 PR이나 커밋의 코드 1개를 골라, 본 문서 §1의 "코드 리뷰 시 5가지 질문"을 모두 적용하고 답을 적어본다.

---

## 더 읽어볼 자료
- 📘 *Threat Modeling: Designing for Security* — Adam Shostack
- 📘 *Security Engineering* — Ross Anderson (무료 PDF 공개)
- 🔗 OWASP Threat Modeling Cheat Sheet
- 🔗 Microsoft Threat Modeling Tool (도식화 도구)
