# Lab 1 — 실습 환경 구축

## 목표
부트캠프 전체에서 사용할 격리된 실습 환경 구축. 모든 공격·방어 실습은 이 환경에서만.

## 1. 필수 도구 설치 체크리스트

### Windows 호스트
- [ ] **JDK 17+** — `winget install Microsoft.OpenJDK.17` 또는 https://adoptium.net
- [ ] **Git** — `winget install Git.Git`
- [ ] **Docker Desktop** — https://docs.docker.com/desktop/install/windows-install/ (WSL2 백엔드)
- [ ] **IntelliJ IDEA Community** — https://www.jetbrains.com/idea/download
- [ ] **Burp Suite Community** — https://portswigger.net/burp/communitydownload
- [ ] **OWASP ZAP** — https://www.zaproxy.org/download
- [ ] **Postman** 또는 Insomnia — API 테스트
- [ ] **Nmap** — https://nmap.org/download.html
- [ ] **Wireshark + Npcap** — https://www.wireshark.org

### 옵션 (Week 3 이후)
- [ ] **WSL2 Ubuntu** — 일부 보안 도구는 Linux에서만 잘 동작
  ```powershell
  wsl --install -d Ubuntu
  ```
- [ ] **VirtualBox** 또는 **VMware** — Kali Linux VM용
- [ ] **Kali Linux** ISO — https://www.kali.org/get-kali/

## 2. 격리 네트워크 만들기

```powershell
# Docker 네트워크 생성 (실습용)
docker network create --subnet=172.30.0.0/16 lab-net

# 확인
docker network inspect lab-net
```

이후 모든 실습 컨테이너는 `--network lab-net`으로 실행.

## 3. 학습용 취약 앱 컨테이너 띄우기

### 3.1 OWASP Juice Shop (필수)
모던 웹 앱 형태의 의도적 취약 앱. 100+ 도전과제.
```powershell
docker run -d --name juice-shop --network lab-net -p 3000:3000 bkimminich/juice-shop
```
→ http://localhost:3000

### 3.2 DVWA (Damn Vulnerable Web Application)
PHP 기반. 보안 등급(Low/Medium/High/Impossible) 비교 학습용.
```powershell
docker run -d --name dvwa --network lab-net -p 8081:80 vulnerables/web-dvwa
```
→ http://localhost:8081 (admin/password)

### 3.3 WebGoat (Java/Spring 기반 — 강력 추천)
**우리에게 가장 가까운 학습 앱.** OWASP가 만든 Java 기반 의도적 취약 앱.
```powershell
docker run -d --name webgoat --network lab-net -p 8082:8080 -p 9090:9090 webgoat/webgoat
```
→ http://localhost:8082/WebGoat

### 3.4 본 부트캠프의 `vulnerable_app/`
Spring Boot + Thymeleaf 직접 만든 취약 앱. 본인 손으로 띄움.
```powershell
cd security_bootcamp/vulnerable_app
./gradlew bootRun
```
→ http://localhost:8080

자세한 가이드: [`../../vulnerable_app/README.md`](../../vulnerable_app/README.md)

## 4. Burp 프록시 연동 테스트

1. Burp Suite 실행
2. Proxy → Open browser
3. 내장 브라우저에서 `http://localhost:3000` 접속
4. Burp Proxy → HTTP history에 요청이 잡히는지 확인

## 5. CTF 플랫폼 계정 만들기

부트캠프 진행 중 매주 1~2개씩 풀어볼 것.
- [ ] **PortSwigger Web Security Academy** — https://portswigger.net/web-security (무료, 강력)
- [ ] **TryHackMe** — https://tryhackme.com (가이드 잘 되어 있음)
- [ ] **HackTheBox** — https://hackthebox.com (난이도 높음, 무료 티어 있음)
- [ ] **OverTheWire Bandit** — https://overthewire.org/wargames/bandit (Linux/Shell 기초)
- [ ] **PicoCTF** — https://picoctf.org (교육용, 매년 라이브 CTF)

## 6. 학습 기록 디렉토리

```powershell
mkdir security_journal
cd security_journal
git init
```

매일 배운 것·실습 결과·발견한 취약점을 마크다운으로 기록. 면접 포트폴리오로도 활용.

## 7. 환경 검증

다음 명령이 모두 동작하면 환경 OK.

```powershell
java --version            # 17 이상
docker --version
docker ps                 # juice-shop, dvwa, webgoat 보여야 함
curl -I http://localhost:3000   # juice-shop 200 OK
nmap -V
```

## 8. 안전 수칙 (다시)

- 🚫 **공격 실습 시 컨테이너 외부 네트워크 차단**: Docker `--network lab-net` 사용
- 🚫 **회사 VPN 켠 상태에서 공격 도구 실행 금지**
- 🚫 **외부 사이트 스캔 금지**
- ✅ **모든 실습은 로컬·격리망에서**
- ✅ **실습 끝나면 `docker stop` 으로 끔**

## 다음
[Lab 2 — 첫 SQL Injection](lab2_first_sqli.md)
