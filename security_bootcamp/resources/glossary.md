# 보안 용어집 (Glossary)

> 학습 중 모르는 용어 만나면 여기서 빠르게 확인.

## A
- **A&A** — Authentication & Authorization
- **ACE** — Arbitrary Code Execution. 임의 코드 실행
- **ACL** — Access Control List
- **AES** — Advanced Encryption Standard. 대칭 키 표준 (128/192/256)
- **APT** — Advanced Persistent Threat. 장기 표적 공격
- **ARP** — Address Resolution Protocol. L2에서 IP→MAC 해석
- **ASLR** — Address Space Layout Randomization. 메모리 주소 무작위화
- **ASVS** — OWASP Application Security Verification Standard
- **ATT&CK** — MITRE의 공격 TTP 분류 프레임워크
- **AV** — Antivirus

## B
- **BCP/DR** — Business Continuity Plan / Disaster Recovery
- **BOLA** — Broken Object Level Authorization (API Top 10 #1)
- **BOF** — Buffer Overflow
- **BSIMM** — Building Security In Maturity Model
- **bcrypt** — 비밀번호 해시 알고리즘 (work factor 조정)

## C
- **CA** — Certificate Authority
- **CIA Triad** — Confidentiality, Integrity, Availability
- **C2** — Command and Control (악성코드 통신 서버)
- **Canary (Stack)** — 스택 무결성 보호 값
- **CASB** — Cloud Access Security Broker
- **CDN** — Content Delivery Network
- **CHACHA20** — 스트림 암호 (TLS 1.3)
- **Clickjacking** — UI 위장 클릭 유도
- **CORS** — Cross-Origin Resource Sharing
- **CRL** — Certificate Revocation List
- **CRS** — Core Rule Set (OWASP, ModSecurity 룰)
- **CSP** — Content Security Policy
- **CSRF/XSRF** — Cross-Site Request Forgery
- **CT** — Certificate Transparency
- **CTF** — Capture The Flag
- **CVE** — Common Vulnerabilities and Exposures (식별자)
- **CVSS** — Common Vulnerability Scoring System (0~10 점수)
- **CWE** — Common Weakness Enumeration (분류)

## D
- **DAST** — Dynamic Application Security Testing
- **DDoS** — Distributed Denial of Service
- **DEP** — Data Execution Prevention (NX와 유사)
- **DKIM/DMARC/SPF** — 메일 인증
- **DLP** — Data Loss Prevention
- **DMZ** — Demilitarized Zone (외부와 내부 사이 영역)
- **DNS** — Domain Name System
- **DoH/DoT** — DNS over HTTPS / TLS
- **DoS** — Denial of Service
- **DREAD** — Damage·Reproducibility·Exploitability·Affected·Discoverability

## E
- **ECB / CBC / GCM / CTR** — 블록 암호 운용 모드
- **ECDHE** — Elliptic Curve Diffie-Hellman Ephemeral (키 교환)
- **EDR** — Endpoint Detection and Response
- **ELK** — Elasticsearch + Logstash + Kibana
- **ESAPI** — Enterprise Security API (OWASP)

## F
- **FIDO/WebAuthn** — 모던 무비밀번호 인증 표준
- **FIM** — File Integrity Monitoring
- **Forensics** — 디지털 증거 수집·분석
- **FUD** — Fully Undetectable (악성코드)

## G
- **GDPR** — EU 일반 개인정보보호규정
- **GPO** — Group Policy Object (Windows)
- **GuardDuty** — AWS 위협 탐지 서비스

## H
- **HIDS / HIPS** — Host Intrusion Detection / Prevention
- **HMAC** — Hash-based MAC
- **HSM** — Hardware Security Module
- **HSTS** — HTTP Strict Transport Security
- **Honeypot** — 가짜 자원으로 공격 유인·탐지

## I
- **IaC** — Infrastructure as Code (Terraform 등)
- **IAM** — Identity and Access Management
- **IAST** — Interactive Application Security Testing
- **IDOR** — Insecure Direct Object Reference
- **IDS / IPS** — Intrusion Detection / Prevention System
- **IMDS** — Instance Metadata Service (AWS)
- **IoC** — Indicator of Compromise (해시·IP 등)
- **IR** — Incident Response
- **ISMS-P** — 한국 정보보호·개인정보보호 관리체계

## J
- **JEP 290** — Java 직렬화 필터링
- **JNDI** — Java Naming and Directory Interface (Log4Shell)
- **JWT** — JSON Web Token
- **JWE / JWS** — JSON Web Encryption / Signature

## K
- **KEV** — Known Exploited Vulnerabilities (CISA 카탈로그)
- **KMS** — Key Management Service
- **K8s** — Kubernetes

## L
- **LDAP** — Lightweight Directory Access Protocol
- **LFI / RFI** — Local / Remote File Inclusion
- **LOLBins** — Living Off the Land Binaries (정상 도구를 악용)

## M
- **MAC** — Message Authentication Code (또는 Media Access Control)
- **MFA** — Multi-Factor Authentication
- **MITM** — Man-in-the-Middle
- **MITRE ATT&CK** — 위협 TTPs 프레임워크
- **mTLS** — Mutual TLS

## N
- **NACL** — Network ACL (AWS, 서브넷 레벨)
- **NIST** — National Institute of Standards and Technology
- **NX bit** — No Execute (메모리 실행 차단)

## O
- **OIDC** — OpenID Connect
- **OPA** — Open Policy Agent (정책 엔진)
- **OSCP** — Offensive Security Certified Professional
- **OSINT** — Open Source Intelligence
- **OWASP** — Open Worldwide Application Security Project

## P
- **PAM** — Privileged Access Management
- **PASTA** — Process for Attack Simulation and Threat Analysis
- **PE** — Privilege Escalation (또는 Portable Executable)
- **PII** — Personally Identifiable Information
- **PoC** — Proof of Concept
- **PKI** — Public Key Infrastructure
- **POSTMORTEM** — 사후 분석

## R
- **RASP** — Runtime Application Self-Protection
- **RBAC / ABAC** — Role / Attribute Based Access Control
- **RCE** — Remote Code Execution
- **ROP** — Return-Oriented Programming

## S
- **SAML** — Security Assertion Markup Language
- **SAMM** — OWASP Software Assurance Maturity Model
- **SAST** — Static Application Security Testing
- **SBOM** — Software Bill of Materials
- **SCA** — Software Composition Analysis
- **SDL/SDLC** — Secure Development Lifecycle
- **SecOps** — Security Operations
- **Sev** — Severity (사고 등급)
- **SIEM** — Security Information and Event Management
- **SLSA** — Supply-chain Levels for Software Artifacts
- **SNI** — Server Name Indication
- **SOAR** — Security Orchestration, Automation, Response
- **SOC** — Security Operations Center
- **SOP** — Same-Origin Policy
- **SQLi** — SQL Injection
- **SSDF** — NIST Secure Software Development Framework
- **SSO** — Single Sign-On
- **SSRF** — Server-Side Request Forgery
- **SSTI** — Server-Side Template Injection
- **STRIDE** — Spoofing·Tampering·Repudiation·Info Disclosure·DoS·Elevation

## T
- **TLS** — Transport Layer Security
- **TOCTOU** — Time-of-Check to Time-of-Use (race)
- **TTPs** — Tactics, Techniques, Procedures

## U
- **UAC** — User Account Control (Windows)
- **UAF** — Use-After-Free
- **UDP** — User Datagram Protocol

## V
- **VPC** — Virtual Private Cloud
- **VPN** — Virtual Private Network

## W
- **WAF** — Web Application Firewall
- **WebAuthn** — 무비밀번호 인증 W3C 표준
- **WSTG** — OWASP Web Security Testing Guide

## X
- **XSS** — Cross-Site Scripting
- **XXE** — XML External Entity

## Z
- **Zero Day** — 공개되지 않은 미패치 취약점
- **Zero Trust** — "내부도 신뢰 안 함" 네트워크 모델
