# Week 2 자가 점검 체크리스트

## OWASP Top 10 — 자신의 코드 매핑
- [ ] A01 Broken Access Control — 도메인 객체 소유자 검사 확인
- [ ] A02 Cryptographic Failures — BCrypt, TLS 1.2+, AES-GCM 사용
- [ ] A03 Injection — Prepared Statement, Thymeleaf `th:text`만
- [ ] A04 Insecure Design — 위협 모델링 1회 이상
- [ ] A05 Misconfiguration — Actuator 보호, 디폴트 패스워드 제거
- [ ] A06 Vulnerable Components — Dependency-Check 통과
- [ ] A07 Auth Failures — 사용자 열거 방지, Rate Limit
- [ ] A08 Integrity Failures — 역직렬화 점검
- [ ] A09 Logging Failures — 보안 이벤트 로그
- [ ] A10 SSRF — URL 검증, IMDSv2

## Spring Security 설정
- [ ] `@EnableMethodSecurity(prePostEnabled = true)`
- [ ] CSRF 활성화 (Spring 기본)
- [ ] PasswordEncoder = Delegating (bcrypt 기본)
- [ ] Session fixation `migrateSession`
- [ ] HSTS, CSP, X-Frame-Options 설정
- [ ] `accessDeniedPage` 또는 핸들러
- [ ] `requiresChannel().anyRequest().requiresSecure()` (운영)

## Thymeleaf 안전성
- [ ] 모든 `th:utext` 사용처 검토
- [ ] 동적 뷰 이름·fragment 없음
- [ ] 사용자 입력 URL에 scheme 검증
- [ ] 스크립트 inline 사용 시 nonce

## 실습 완료
- [ ] vulnerable_app의 SQLi / XSS / CSRF / IDOR / SSRF / 파일업로드 공격 + 패치
- [ ] WebGoat 또는 Juice Shop 챕터 5개 이상
- [ ] PortSwigger Academy 랩 누적 10개 이상
- [ ] 본인 사이드 프로젝트에 보안 PR 1개 이상

## 추가 산출물
- [ ] OWASP Top 10 vs 회사 코드 매트릭스
- [ ] Spring Security 권장 설정 1세트 작성
- [ ] 비밀번호 재설정 흐름 다이어그램 + 보안 검토

## 다음 주 준비
- [ ] Wireshark, Nmap 익숙해진 상태
- [ ] AWS·Docker 기본 셋업
- [ ] 회사 인프라(외부 노출) 인벤토리
