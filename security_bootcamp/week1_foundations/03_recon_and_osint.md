# Day 3 — 정찰(Reconnaissance) · OSINT · 포트 스캔

> **모든 공격은 정찰에서 시작한다.** 공격자가 무엇을 알아내는지 알면, 무엇을 숨겨야 할지 안다.

## 1. 정찰의 두 종류

| 종류 | 방법 | 흔적 |
|------|------|------|
| **Passive Reconnaissance** | 공개 정보만 수집 (검색, WHOIS, GitHub) | 거의 안 남음 |
| **Active Reconnaissance** | 대상에 직접 패킷 전송 (스캔, 디렉토리 brute) | 로그 남음 |

> ⚠ 본인이 소유한 자산이나 명시적 허가가 있는 자산만 Active Recon 가능.

---

## 2. 공격자가 파악하는 것 (Attack Surface)

```
1. 도메인·서브도메인  → 어떤 서비스가 있나?
2. IP 대역            → 어떤 호스트들이 있나?
3. 오픈 포트          → 어떤 서비스가 띄워져 있나?
4. 서비스·버전        → 알려진 CVE는?
5. 기술 스택          → Spring/Tomcat/Nginx 버전, JS 라이브러리
6. 직원 이메일·이름    → 피싱·소셜 엔지니어링·Brute-force 사용자명 후보
7. GitHub 누출        → 코드·키·내부 URL
8. 클라우드 자산       → 잘못 공개된 S3 버킷·EC2 IP
9. 디렉토리·파일      → admin/, .git/, .env, backup/
10. 인증 메커니즘     → 로그인 흐름, 2FA 여부
```

---

## 3. OSINT (Open Source Intelligence) 기법

### 3.1 검색 엔진 도크 (Google Dorking)
구글 검색의 특수 연산자.

| 도크 | 효과 |
|------|------|
| `site:example.com` | 특정 도메인만 |
| `inurl:admin` | URL에 admin 포함 |
| `intitle:"index of"` | 디렉토리 인덱싱 노출 |
| `filetype:pdf site:example.com` | 특정 도메인의 PDF |
| `"DB_PASSWORD=" filetype:env` | .env 파일 노출 |
| `ext:sql intext:INSERT` | SQL 덤프 노출 |
| `cache:example.com` | 구글 캐시 |
| `-www site:example.com` | www 제외한 서브도메인 |

**Google Hacking Database (GHDB)**: https://www.exploit-db.com/google-hacking-database

### 3.2 서브도메인 열거
- **crt.sh** — 인증서 투명성 로그에서 SAN 추출
  ```
  https://crt.sh/?q=%25.example.com
  ```
- **VirusTotal** — Subdomain 탭
- **Subfinder, Amass, Sublist3r** — 자동화 도구
- **chaos.projectdiscovery.io** — 큰 데이터셋

### 3.3 GitHub OSINT
공격자가 가장 좋아하는 보물창고.

```
# 검색어 예
"company.com" password
"company.com" api_key
"company.com" jdbc:db2
org:your-company filename:.env
```

**gitleaks**, **trufflehog** 같은 도구로 자동 탐지.

### 3.4 Shodan / Censys
"인터넷의 검색 엔진" — IP·포트·배너 기반.

- **Shodan**: https://www.shodan.io
  - `port:9200 country:KR` — 한국의 공개 Elasticsearch
  - `org:"Your Company"` — 회사 자산
- **Censys**: https://search.censys.io
- **FOFA / ZoomEye** — 중국 기반 대안

### 3.5 Wayback Machine
삭제된 페이지·과거 디렉토리 구조 복원.
- https://web.archive.org

### 3.6 WHOIS / DNS
```bash
whois example.com
dig example.com ANY
nslookup -type=mx example.com   # 메일 서버
dig +short TXT example.com      # SPF, DMARC, 인증 토큰들
```

---

## 4. Nmap — 포트 스캔의 표준

### 설치 (Windows)
- https://nmap.org/download.html — 공식 설치
- WSL에서: `sudo apt install nmap`

### 기본 사용법

```bash
# 가장 흔히 쓰는 조합 (서비스·버전·OS 검출)
nmap -sV -sC -O -oN scan.txt target.com

# 옵션 설명
-sS    # SYN 스캔(스텔스, 기본). root 필요
-sT    # TCP Connect 스캔. 권한 없을 때
-sU    # UDP 스캔(느림)
-sV    # 서비스/버전 검출
-sC    # 기본 NSE 스크립트 실행
-O     # OS 핑거프린트
-A     # -sV -sC -O --traceroute 한방에
-p-    # 모든 65535 포트
-p 80,443,8080  # 특정 포트
-T0~5  # 속도(0=가장 느린·은밀, 5=가장 빠름)
-Pn    # 핑 응답 무시 (ICMP 차단된 호스트)
-oA name  # 모든 포맷으로 저장(.nmap, .xml, .gnmap)
--script vuln  # 취약점 스크립트
```

### Nmap NSE 스크립트 (강력)
```bash
# HTTP 헤더·디렉토리
nmap --script http-headers,http-enum -p 80,443 target

# SSL/TLS 점검
nmap --script ssl-enum-ciphers -p 443 target

# SMB 취약점
nmap --script smb-vuln* -p 445 target

# 모든 vuln 스크립트(공격적)
nmap --script vuln target
```

### 출력 해석 예
```
PORT     STATE SERVICE  VERSION
22/tcp   open  ssh      OpenSSH 7.6p1 Ubuntu 4ubuntu0.3
80/tcp   open  http     nginx 1.14.0
443/tcp  open  https    nginx 1.14.0
3306/tcp open  mysql    MySQL 5.7.33   ← DB가 외부 노출! 위험
8080/tcp open  http-proxy
```
**즉시 확인할 것**: DB·관리 포트(3306, 5432, 6379, 27017, 9200 등)가 외부에서 열려 있으면 사고.

---

## 5. 디렉토리·파일 발견

### 도구
- **ffuf** — 빠름, 모던
- **gobuster** — 클래식
- **dirsearch** — 파이썬

### 예
```bash
ffuf -u https://target.com/FUZZ -w /usr/share/wordlists/dirb/common.txt -fc 404
```

### 흔히 노출되는 위험 경로
```
/.git/                  ← 소스 코드 전체 노출
/.env                   ← 비밀 키
/backup.zip, /dump.sql
/admin, /administrator
/api/v1/swagger-ui, /actuator   ← Spring Actuator 노출
/phpmyadmin, /pma
/wp-admin
/.well-known/
```

> 📌 **Spring Boot 주의**: `/actuator/env`, `/actuator/heapdump`, `/actuator/mappings`가 인증 없이 열려 있으면 모든 환경 변수·메모리 덤프 유출. 반드시 인증 보호 또는 production-ready endpoint 제한.

---

## 6. 기술 스택 핑거프린트

### 브라우저 도구
- **Wappalyzer** (Chrome 확장) — 라이브러리·프레임워크 자동 감지
- **BuiltWith** — https://builtwith.com

### 헤더 분석
```http
Server: Apache/2.4.41              ← 버전 노출
X-Powered-By: PHP/7.4.3            ← 절대 켜놓지 말 것
X-AspNet-Version: 4.0.30319
X-Generator: WordPress 5.8
Set-Cookie: JSESSIONID=...         ← Java/Tomcat 시그널
```

### 에러 페이지·디폴트 페이지
- Tomcat의 기본 에러 페이지 → 버전 노출
- Whitelabel Error Page → Spring Boot

### Spring/Java 식별 시그널
- `JSESSIONID` 쿠키
- `X-Application-Context: application:prod:8080` (옛 Spring Boot)
- `/actuator/*` 경로 응답
- HTML에 `_csrf` 토큰

### 방어
- `server.servlet.session.cookie.name`을 `JSESSIONID`에서 변경
- `server.error.include-stacktrace=never`
- `server.error.whitelabel.enabled=false` (커스텀 에러 페이지 사용)
- 헤더에서 `Server`, `X-Powered-By` 제거

```yaml
# application.yml
server:
  error:
    include-message: never
    include-stacktrace: never
    include-binding-errors: never
  servlet:
    session:
      cookie:
        name: SID
```

---

## 7. 자동화 — Recon Frameworks

- **Amass** — OWASP, 서브도메인 종합
- **ReconFTW** — 통합 워크플로우
- **Sn1per** — 자동 스캔(공격적)
- **OWASP Maryam** — OSINT 프레임워크

> 본인 자산에 대해 한번 돌려보면 "내가 모르게 노출된 것"이 자주 발견된다.

---

## 8. 방어 관점 — Attack Surface Reduction

| 영역 | 줄이는 방법 |
|------|----------|
| 서브도메인 | 사용 안 하는 와일드카드·옛 서비스 정리, DNS 청소 |
| 포트 | 외부에서 닿는 포트는 80/443만. 관리 포트는 VPN/SSH 터널 |
| 정보 노출 | 에러 페이지 커스터마이즈, 헤더 제거, robots.txt 신경 |
| GitHub | private repo, secret scanning, pre-commit hook(gitleaks) |
| 클라우드 | S3 public access block, EC2 SG 점검, AWS Config |
| Actuator | `management.endpoints.web.exposure.include=health,info`만 |
| 디렉토리 인덱싱 | nginx `autoindex off`, Tomcat `listings=false` |

---

## 9. 오늘의 실습

### 실습 3.1 — 본인 도메인 OSINT
**(본인 소유 도메인에만)**
1. crt.sh 에서 서브도메인 목록 추출
2. WHOIS·DNS 레코드 확인
3. GitHub에서 `"yourdomain.com"` 검색
4. 노출된 정보 정리

### 실습 3.2 — 로컬 Nmap 연습
```bash
# 자기 자신 스캔
nmap -sV -sC -p- 127.0.0.1

# Docker 컨테이너 스캔
docker run -d --name nginx-test -p 8080:80 nginx
nmap -sV -p 8080 127.0.0.1
docker rm -f nginx-test
```

### 실습 3.3 — Spring Boot Actuator 점검
본인 회사 앱(또는 사이드 프로젝트)이 다음 경로에 응답하는지 확인:
- `/actuator`
- `/actuator/health`
- `/actuator/env`
- `/actuator/mappings`
- `/actuator/heapdump`

응답한다면 즉시 `management.endpoints.web.exposure.include` 정리.

### 실습 3.4 — 공격자 시뮬레이션 워크북 만들기
다음 표를 자신의 서비스에 대해 채워본다.

| 자산 | 외부 노출? | 인증 필요? | 발견 방법 | 위험도 |
|------|---------|---------|---------|------|
| 메인 웹 | ✅ | 일부 | 도메인 | Low |
| 관리자 페이지 | ? | ? | ? | ? |
| API | ? | ? | ? | ? |
| Actuator | ? | ? | ? | ? |
| DB | ? | ? | ? | ? |
| Redis | ? | ? | ? | ? |
| GitHub | ? | ? | ? | ? |

---

## 더 읽어볼 자료
- 📘 *Open Source Intelligence Techniques* — Michael Bazzell
- 🔗 OSINT Framework — https://osintframework.com
- 🔗 Nmap Network Scanning — 공식 문서가 책처럼 좋음
- 🔗 https://hackertarget.com — 온라인 정찰 도구 모음
