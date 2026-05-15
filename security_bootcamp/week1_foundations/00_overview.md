# Week 1 — 보안 기초·HTTP·도구 셋업

## 한 줄 요약
**공격자처럼 생각하는 법을 익히고**, 웹이 실제로 어떻게 동작하는지 패킷 단위로 보고, 부트캠프 내내 쓸 도구·실습 환경을 셋업한다.

## 학습 목표
이 주가 끝나면 다음을 **혼자 할 수 있어야** 한다.

- [ ] CIA Triad, AAA, Least Privilege 등 핵심 보안 원칙을 사례와 함께 설명
- [ ] STRIDE 기반으로 간단한 시스템의 위협 모델 작성
- [ ] HTTP 요청·응답 구조, 쿠키 속성, CORS·SOP를 정확히 설명
- [ ] Burp Suite로 자신의 로컬 앱 트래픽을 가로채고 변조
- [ ] OWASP ZAP의 자동 스캔으로 취약점 리포트 생성
- [ ] Nmap으로 포트·서비스·OS 핑거프린트 수행
- [ ] 로컬 취약 실습 앱(`vulnerable_app/`)을 띄우고 첫 공격(SQL Injection) 성공·차단

## 일정 (5일, 평일 기준)

| 일 | 내용 | 파일 |
|----|------|------|
| Day 1 | 보안 마인드셋·CIA·위협 모델링 | [01_security_mindset.md](01_security_mindset.md) |
| Day 2 | HTTP·쿠키·세션·CORS·SOP 정확히 이해 | [02_http_web_internals.md](02_http_web_internals.md) |
| Day 3 | 정찰(Recon)·OSINT·Nmap | [03_recon_and_osint.md](03_recon_and_osint.md) |
| Day 4 | Burp Suite / OWASP ZAP 마스터 | [04_proxy_tools.md](04_proxy_tools.md) |
| Day 5 | 실습 환경 구축 + 첫 공격 | [labs/lab1_setup.md](labs/lab1_setup.md), [labs/lab2_first_sqli.md](labs/lab2_first_sqli.md) |

## 실습 산출물
- `vulnerable_app/` 로컬 실행 성공 (Docker 또는 IntelliJ)
- Burp Suite로 가로챈 요청 스크린샷 1장
- 본인 사이드 프로젝트의 STRIDE 위협 모델 v0.1 (1페이지)

## Week 1 체크리스트
[checklist.md](checklist.md) 참조.
