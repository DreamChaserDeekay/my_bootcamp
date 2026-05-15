# Week 1 자가 점검 체크리스트

## 개념 (Concept)
- [ ] CIA Triad를 사례 1개씩 들어 설명 가능
- [ ] 9가지 보안 설계 원칙(Least Privilege ~ Zero Trust)을 각각 한 줄로 설명
- [ ] STRIDE 6개 카테고리를 자기 시스템에 적용
- [ ] HTTP 메서드의 Safe/Idempotent 분류
- [ ] 쿠키 속성 5개(HttpOnly, Secure, SameSite, Domain, Path) 설명
- [ ] SOP와 CORS의 차이, CSRF가 왜 SOP에도 불구하고 가능한지 설명
- [ ] 주요 보안 헤더 6개의 효과 설명
- [ ] CSP에서 `unsafe-inline`이 왜 위험한지 설명

## 도구 (Tool)
- [ ] Burp Suite 내장 브라우저로 트래픽 캡처
- [ ] Repeater로 요청 변조해서 재전송
- [ ] OWASP ZAP로 Automated Scan 1회
- [ ] Nmap으로 본인 로컬 호스트 스캔
- [ ] crt.sh로 본인 도메인 서브도메인 열거
- [ ] curl로 헤더·쿠키·바디 자유롭게 다루기

## 실습 (Hands-on)
- [ ] `vulnerable_app/` Docker 또는 IntelliJ로 실행
- [ ] DVWA / WebGoat / Juice Shop 중 2개 이상 셋업
- [ ] `/vuln/login`에 SQL Injection으로 인증 우회 성공
- [ ] `/safe/login`에서 동일 공격 실패 확인
- [ ] PortSwigger Academy 첫 5개 랩 클리어

## 정찰 (Recon)
- [ ] 본인 회사·사이드 프로젝트 외부 노출 점검 (Shodan/securityheaders)
- [ ] Spring Boot Actuator 노출 여부 확인
- [ ] GitHub에서 본인 도메인 검색 → 누출 확인
- [ ] 본 도메인 서브도메인 인벤토리 정리

## 산출물 (Deliverable)
- [ ] 본인 시스템 STRIDE 위협 모델 v0.1 (1페이지)
- [ ] Attack Surface 워크북 (자산·노출·인증·위험도)
- [ ] securityheaders.com 스크린샷 + 개선 계획

## 다음 주 준비
- [ ] WebGoat에서 다음 챕터 미리 살펴봄: Injection, Authentication, XSS
- [ ] `vulnerable_app/`의 모든 라우트를 한 번씩 호출해봄
