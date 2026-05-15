# CTF·실습 플랫폼 가이드

부트캠프 학습과 병행하면 좋은 무료/저비용 플랫폼.

## 1. 입문 (Week 1~2 동안 시작 권장)

### PortSwigger Web Security Academy ⭐⭐⭐
- https://portswigger.net/web-security
- **완전 무료**, 200+ 랩
- 주제별로 정리, 개념 → 실습 → 풀이
- Burp Suite 만든 회사
- **본 부트캠프와 가장 잘 맞음** — 매주 5~10개씩 권장
- 시작점: SQL Injection → Authentication → XSS → CSRF → SSRF → 순서

### TryHackMe ⭐⭐
- https://tryhackme.com
- 가이드가 매우 친절 (초보자 친화)
- 무료 룸 + Premium ($14/월)
- 추천 경로: Pre-Security → Complete Beginner → Web Fundamentals → OWASP Top 10

### OverTheWire Bandit ⭐⭐
- https://overthewire.org/wargames/bandit
- Linux/Shell 기초 — SSH 접속해서 단계별
- 무료, 처음 보안 + 리눅스 동시 공부에 최고

## 2. 중급

### HackTheBox ⭐⭐⭐
- https://hackthebox.com
- "박스"를 침투하는 형식 (가장 인기)
- 무료 티어 + VIP ($14/월)
- **HTB Academy**도 별도 (이론 + 실습 결합)
- 시작: Starting Point Tier 0 → Tier 1 → 그 다음 Active Machines

### Hack The Box Academy — Web Penetration Tester Path
- 본 부트캠프 다음 단계로 강추

### picoCTF ⭐⭐
- https://picoctf.org
- 교육용, 연 1회 라이브 + 상시 연습
- 카테고리별 (Web, Crypto, Forensics, Binary, Reversing)

### Root-Me ⭐⭐
- https://www.root-me.org
- 무료, 다국어
- 챌린지 형식, 빠르게 여러 주제 경험

### CryptoHack ⭐
- https://cryptohack.org
- 암호학 전문 (재미있는 게임 형식)

## 3. 한국

### Dreamhack ⭐⭐
- https://dreamhack.io
- 국내 1위 보안 학습 플랫폼
- 무료 + 유료 강의, CTF, 워게임
- 한국어 자료

### Wishhack
- 일부 워게임·CTF

### 국내 CTF 대회
- **CodeGate** — 한국 대표 (4월경)
- **POC**, **SCTF (Samsung)**, **KIMCHICON**
- **white-hat contest** — KISA·국정원 주최

## 4. 본인 환경에 운영

### 의도적 취약 앱 (학습용 컨테이너)
- **OWASP Juice Shop** ⭐⭐⭐ — 모던 SPA, 100+ 도전, 강력 추천
  ```bash
  docker run -d -p 3000:3000 bkimminich/juice-shop
  ```
- **OWASP WebGoat** ⭐⭐⭐ — **Java/Spring 기반, 본 부트캠프 환경과 가장 가까움**
  ```bash
  docker run -d -p 8082:8080 webgoat/webgoat
  ```
- **DVWA** ⭐⭐ — PHP, 보안 등급(Low/Medium/High/Impossible) 비교 학습
- **Mutillidae**, **bWAPP**, **WebGoat .NET** — 변종
- **HackTheBox - Pwnbox / Pwntools** — 도전적 환경

### 부트캠프 vulnerable_app
[`../vulnerable_app/`](../vulnerable_app/) 직접 띄움.

## 5. CTF 일정·랭킹
- **CTFtime.org** — 일정·팀 랭킹
- **Discord 커뮤니티**: PortSwigger, HackTheBox, Dreamhack 공식

## 6. 학습 페이스 추천

### 부트캠프 진행 중 (4주)
- 매일 30분 → PortSwigger Academy 랩
- 주말 1~2시간 → WebGoat / Juice Shop

### 부트캠프 이후
- **매주 1~2개 랩 또는 한 박스**
- **월 1회 CTF** (CTFtime에서 weekend 대회 골라 팀 참여)
- **분기 1회 깊은 워크샵** (1주일 휴가 내고 HackTheBox Pro Lab 등)

## 7. CTF 활용 팁

- **Writeup 적극 활용**: 못 풀어도 풀이 보고 학습. 다음에 비슷한 문제 풀 수 있게.
- **카테고리 한쪽 편식 X**: Web만 풀면 시야 좁아짐. Forensics·Crypto·Misc도.
- **블로그·노트**: 본인 풀이를 정리하면 면접 자료
- **팀 플레이**: 혼자보다 다양한 관점

## 8. 윤리 재강조
- ✅ 위 플랫폼들은 모두 합법적 학습 환경
- ❌ 외부 사이트·회사 서비스 무단 테스트는 정보통신망법 위반
- ✅ 본인 시스템·사이드 프로젝트 OK
- ✅ Bug Bounty 프로그램은 범위 명시 — 그 안에서만
