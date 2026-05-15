# 도구 모음 (Tools Reference)

## 정찰·OSINT
- **crt.sh** — CT 로그 기반 서브도메인
- **Shodan / Censys / FOFA** — 인터넷 자산 검색
- **Subfinder / Amass / Sublist3r** — 서브도메인 열거
- **gitleaks / trufflehog** — git 비밀 스캔
- **theHarvester** — 이메일·도메인 수집
- **Wayback Machine** — 과거 페이지

## 스캔
- **Nmap / Masscan / Naabu / RustScan** — 포트
- **nuclei** — 템플릿 기반 취약점 탐색
- **ffuf / gobuster / dirsearch** — 디렉토리·파일 발견
- **wfuzz** — 파라미터 fuzz

## 프록시·웹 분석
- **Burp Suite** — 핵심 (Community/Pro)
- **OWASP ZAP** — 오픈소스 대안
- **mitmproxy / mitmweb** — CLI/Web 프록시
- **Postman / Insomnia / HTTPie / curl** — API 요청

## SAST (정적 분석)
- **SonarQube + SonarLint**
- **Semgrep** (무료, 빠름)
- **CodeQL** (GitHub)
- **SpotBugs + FindSecBugs** (Java)
- **Snyk Code**
- **Fortify / Checkmarx / Veracode** (상용)

## SCA (의존성)
- **OWASP Dependency-Check**
- **Snyk Open Source**
- **GitHub Dependabot**
- **Renovate**
- **OWASP DependencyTrack** (서버)

## 컨테이너·IaC
- **Trivy** — 이미지 + 의존성 + IaC
- **Grype / Syft** (Anchore)
- **Docker Scout**
- **Checkov / tfsec / Terrascan**
- **Kubescape / kube-bench / kube-hunter**
- **Falco** — 런타임 행위
- **Cosign / Sigstore** — 이미지 서명

## DAST·자동 스캔
- **OWASP ZAP (CI)**
- **Burp Scanner (Pro)**
- **Nuclei**
- **Acunetix / Netsparker** (상용)

## 네트워크·인프라
- **Wireshark / tshark** — 패킷 분석
- **tcpdump**
- **bettercap / ettercap** (LAN MITM, 격리망)
- **testssl.sh** — TLS 점검
- **ssh-audit**
- **Lynis** — Linux audit
- **Prowler / ScoutSuite** — 클라우드 audit
- **Pacu** — AWS 공격 시뮬레이션 프레임워크

## 사고 대응·포렌식
- **Volatility** — 메모리
- **Autopsy / Sleuth Kit** — 디스크
- **YARA** — 시그니처
- **plaso / log2timeline** — 타임라인
- **REMnux / Flare-VM** — 분석용 OS

## 학습용 의도적 취약 앱
- **OWASP Juice Shop** — 모던, 100+ 도전
- **OWASP WebGoat** — Java/Spring 기반 (강추)
- **DVWA** — PHP, 보안 등급 비교
- **Mutillidae / Bricks / VulnHub**
- **HackTheBox / TryHackMe** — 종합 플랫폼
- **PortSwigger Web Security Academy** — 웹 최고

## 비밀·키 관리
- **HashiCorp Vault**
- **AWS Secrets Manager / SSM Parameter Store**
- **Azure Key Vault / GCP Secret Manager**
- **SOPS** (Mozilla) — 파일 암호화
- **Sealed Secrets** (K8s)
- **1Password / Bitwarden / KeePassXC** — 개인용

## 인증·아이덴티티
- **Keycloak** — 오픈소스 IdP
- **Okta / Auth0** — 상용 IdP
- **Authelia / Authentik** — 자가 호스팅
- **YubiKey** — HW 보안 키

## CTF·실습 플랫폼
- **PortSwigger Web Security Academy** (무료, 강력)
- **HackTheBox** (무료 티어 있음)
- **TryHackMe** (가이드 잘 됨)
- **OverTheWire Bandit** (Linux/Shell)
- **picoCTF** (교육용)
- **CryptoHack** (암호학)
- **pwnable.kr** (시스템·바이너리)
- **Root-Me**
- **CTFtime.org** — 일정 모음

## 한국 자원
- **KISA** — 가이드라인·정책 (https://www.kisa.or.kr)
- **KrCERT** — 침해사고 대응
- **Wishack/dreamhack** — 국내 CTF·교육
- **라온화이트해커**, **AhnLab**, **시큐아이** — 사설 보안 컨퍼런스
- **CodeGate** — 한국 대표 CTF
- **POC, KIMCHICON** — 보안 컨퍼런스
