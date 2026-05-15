# Week 2 — 웹 애플리케이션 보안 (Spring/Thymeleaf 특화)

## 한 줄 요약
**OWASP Top 10 2021**을 Spring Boot + Thymeleaf 환경에서 실제로 공격하고 방어한다. 이 주가 부트캠프의 핵심.

## 학습 목표
- [ ] OWASP Top 10 2021을 모두 자신의 코드 사례와 함께 설명
- [ ] SQLi / XSS / CSRF / SSRF / XXE / IDOR / 역직렬화 공격을 모두 실습
- [ ] Thymeleaf의 안전한 사용법(자동 이스케이프, `th:utext` 위험, SpEL Injection) 숙지
- [ ] Spring Security 6.x로 인증·인가·CSRF·세션 안전하게 구성
- [ ] BCrypt, 안전한 비밀번호 정책, 비밀번호 재설정 흐름 설계
- [ ] 파일 업로드·다운로드의 모든 함정 회피
- [ ] 안전한 에러 처리·로깅
- [ ] **`vulnerable_app/`의 모든 취약점을 공격하고 패치**

## 일정 (5일)

| 일 | 내용 | 파일 |
|----|------|------|
| Day 1 | OWASP Top 10 개관 + Injection 심화 | [01_owasp_top10.md](01_owasp_top10.md), [02_injection.md](02_injection.md) |
| Day 2 | 인증·세션·Spring Security | [03_auth_session.md](03_auth_session.md) |
| Day 3 | XSS / CSRF / Thymeleaf 특화 | [04_xss_csrf_thymeleaf.md](04_xss_csrf_thymeleaf.md) |
| Day 4 | 접근 통제(IDOR/BAC), SSRF, XXE, 역직렬화, 파일 업로드 | [05_access_control.md](05_access_control.md), [06_ssrf_xxe_deser.md](06_ssrf_xxe_deser.md), [07_file_upload.md](07_file_upload.md) |
| Day 5 | 안전한 의존성 관리, 비밀번호·암호학 적용, 에러·로깅 | [08_crypto_secrets.md](08_crypto_secrets.md), [09_logging_errors.md](09_logging_errors.md) |

## 실습 산출물
- `vulnerable_app/`의 모든 라우트를 1) 공격 성공 2) 패치 적용
- Spring Security 안전 설정 1세트 (config 클래스)
- 본인 사이드 프로젝트 1개의 보안 PR

## Week 2 체크리스트
[checklist.md](checklist.md)
