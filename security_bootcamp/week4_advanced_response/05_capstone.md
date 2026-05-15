# Day 5 — 캡스톤 프로젝트: 본인 시스템 보안 평가

> 4주간 배운 것을 **실제 시스템에 적용**한다. 산출물은 면접·이력서·내부 발표에도 활용 가능.

## 목표
본인이 선택한 시스템 1개에 대해 **종합 보안 평가 보고서**를 작성한다.
- 본인 사이드 프로젝트 (강추)
- 회사 시스템 일부 (허가 받은 범위)
- 공개 의도적 취약 앱 (OWASP Juice Shop 등)

> ⚠ 회사 시스템은 반드시 **사전 서면 허가**. 범위·시간 명시. 운영 영향 없는 방식으로.

---

## 보고서 구조 (템플릿)

```markdown
# Security Assessment Report — [시스템명]

## 1. Executive Summary
- 시스템 개요
- 평가 기간·범위
- 핵심 발견 사항 3~5개
- 위험 등급 분포

## 2. 시스템 개요
### 2.1 아키텍처 다이어그램
### 2.2 데이터 흐름
### 2.3 신뢰 경계
### 2.4 핵심 자산

## 3. 위협 모델 (STRIDE)
| 컴포넌트 | S | T | R | I | D | E |

## 4. 평가 방법론
- 정보 수집 (OSINT)
- 수동 테스트 (Burp/ZAP)
- 자동화 (SAST/SCA/DAST)
- 코드 리뷰

## 5. 발견 사항 (Findings)
각각:
- ID, 카테고리(OWASP)
- 심각도 (CVSS)
- 설명
- 재현 단계 (PoC 포함)
- 영향
- 권장 대응
- 참고

## 6. 위험 매트릭스
| ID | 카테고리 | 심각도 | 상태 |

## 7. 개선 권고 (우선순위)
- 즉시 (Critical/High)
- 단기 (1~3개월)
- 장기 (구조적 개선)

## 8. 잘 된 점 (Defensive Posture)
- 발견된 좋은 보안 통제도 기록

## 9. 부록
- 도구 출력
- 참고 문헌
- 용어집
```

---

## 단계별 가이드

### Step 1 — 범위 정의 (Day 5 오전)
- 어떤 시스템·서브시스템
- 어떤 자산(코드, 인프라, 운영, 데이터)
- 무엇은 제외 (운영 영향 큰 액티브 스캔 등)
- 도구 사용 범위

### Step 2 — 정보 수집
- 외부 OSINT (Shodan, crt.sh, GitHub)
- DNS, TLS, 헤더 점검 (securityheaders, ssllabs)
- 사이트맵·디렉토리·기술 스택

### Step 3 — 위협 모델링
- 시스템 다이어그램
- STRIDE
- 데이터 흐름 별 위협

### Step 4 — 자동화 스캔
- SAST: Semgrep, SpotBugs+FindSecBugs
- SCA: Dependency-Check, Snyk
- DAST: ZAP Baseline
- 컨테이너: Trivy
- IaC: Checkov
- Secret: gitleaks (히스토리 포함)

### Step 5 — 수동 테스트
- 인증·세션 (Burp로 변조)
- 인가 (IDOR, 권한 상승)
- 입력 검증 (XSS, SQLi, SSRF, 파일 업로드)
- 비즈니스 로직 (이상치 입력, race)
- 에러 처리 (스택트레이스, 정보 노출)

### Step 6 — 코드 리뷰
- 보안 핵심 경로 (인증·인가·암호·외부 호출·파일·역직렬화)
- Week 4 §3 체크리스트 사용

### Step 7 — 발견 정리·재현
- 각 발견 PoC 스크립트 (Burp request, curl 명령)
- 스크린샷
- 영향 평가

### Step 8 — 권고 작성
- 즉시 대응(WAF 룰·설정 변경)
- 코드 수정 PR
- 구조적 개선 (위협 모델·SDLC)

### Step 9 — 발표·공유
- 회사면 보안팀·관련 팀 공유
- 사이드 프로젝트면 GitHub README 또는 블로그

---

## 발견 항목 작성 예

```markdown
### Finding F-005: Stored XSS in 게시글 본문
**Category**: OWASP A03 (Injection - XSS)
**Severity**: High (CVSS 7.4)
**Affected Endpoint**: POST /posts, GET /posts/{id}

**Description**:
게시글 작성 시 본문 입력에 대한 HTML 이스케이프가 적용되지 않아, 다른 사용자가 게시글을 조회할 때 임의의 JavaScript가 실행된다.

**Reproduction**:
1. 일반 사용자로 로그인
2. 게시글 작성 → 본문에 `<script>alert(document.cookie)</script>` 입력
3. 저장 후 다른 사용자(또는 시크릿 모드)로 게시글 조회
4. JavaScript alert 실행 확인

**PoC**:
```http
POST /posts HTTP/1.1
...
title=test&content=%3Cscript%3Ealert(document.cookie)%3C%2Fscript%3E
```

**Impact**:
- 세션 쿠키 탈취(HttpOnly로 차단되나 CSRF 토큰은 추출 가능)
- 화면 변조·피싱 페이지 삽입
- 권한 있는 사용자의 행위 강제 (관리자가 보면 관리 행위)
- CSP 미설정으로 영향 확대

**Recommendation**:
1. `templates/post/view.html`에서 `th:utext="${post.content}"` 를 `th:text="${post.content}"` 로 변경 (line 42)
2. WYSIWYG 입력이 정말 필요하면 OWASP Java HTML Sanitizer로 정제 후 저장
3. CSP 헤더 활성 (script-src 'self' nonce-...)

**Code Diff Suggestion**:
```diff
- <div th:utext="${post.content}"></div>
+ <div th:text="${post.content}" style="white-space: pre-wrap;"></div>
```

**References**:
- OWASP XSS Prevention Cheat Sheet
- Thymeleaf 문서 §6.1 "Using texts"
```

---

## 발견 카테고리 — 부트캠프에서 다룬 것 매핑

| 발견 종류 | 부트캠프 파일 |
|---------|-------------|
| SQL Injection | Week 1 Lab 2, Week 2 §02 |
| XSS | Week 2 §04 |
| CSRF | Week 2 §04 |
| IDOR / Access Control | Week 2 §05 |
| SSRF / XXE / Deser | Week 2 §06 |
| 파일 업로드 | Week 2 §07 |
| 암호·비밀 | Week 2 §08 |
| 에러·로깅 | Week 2 §09 |
| TLS | Week 3 §02 |
| 네트워크 노출 | Week 3 §01, §03 |
| 클라우드 IAM | Week 3 §04 |
| 컨테이너 | Week 3 §05 |
| 의존성 CVE | Week 2 §08 + Week 4 §02 |

---

## 부트캠프 완주 후 추천 다음 단계

### 자격증 (관심 있다면)
- **OSCP** — 실전 펜테스팅 (어려움, 실습 위주)
- **CEH** — 입문
- **CISSP** — 관리자 트랙
- **CKS** — Kubernetes 보안
- 국내: **정보보안기사**

### 다음 학습 트랙
- **Bug Bounty 시작** — HackerOne, Bugcrowd, 국내 라온화이트해커
- **CTF 정기 참여** — DEFCON, 화이트해커 (KISA), Hack The Box Battlegrounds
- **PortSwigger Academy 완주** — 200+ 랩
- **Hack The Box / TryHackMe Pro Labs**
- **모바일 보안** (Android·iOS 펜테스팅)
- **클라우드 심화** (AWS Certified Security)

### 기여
- 본인 회사에 보안 챔피언 자원
- 사내 보안 위키·교육 작성
- OWASP 한국 챕터 참여
- 블로그·발표

---

## 캡스톤 채점 가이드 (자가 평가)

| 항목 | 점수 |
|------|------|
| 위협 모델 (STRIDE) 완성도 | /10 |
| 자동 스캔 도구 5개 이상 적용 | /10 |
| 수동 테스트 8개 카테고리 | /20 |
| 발견 항목 ≥ 5개 (실제 재현) | /20 |
| 각 발견에 PoC + 영향 + 권고 | /15 |
| 코드 수정 제안 (실제 PR 또는 diff) | /10 |
| 보고서 가독성·구조 | /10 |
| 사후 개선 로드맵 | /5 |
| **총점** | /100 |

70점 이상이면 부트캠프 마스터.

---

## 마지막으로

> 보안은 **목적지가 아니라 여정**이다. 어제의 안전이 오늘의 취약이 된다.
> 매주 30분이라도 보안 관련 콘텐츠를 따라가고, 매 PR 마다 보안 질문 5개를 던지자.
> 4주는 끝이 아니라 시작이다.

🎓 **4주 부트캠프 완주, 축하합니다.**
