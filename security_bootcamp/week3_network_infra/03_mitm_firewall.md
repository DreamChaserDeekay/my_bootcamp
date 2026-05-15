# Day 3 — MITM·방화벽·세그멘테이션·VPN·Zero Trust

## 1. MITM (Man-in-the-Middle) 분류

| 위치 | 방법 |
|------|------|
| 로컬 LAN | ARP spoofing, 가짜 Wi-Fi AP |
| 라우터/ISP | 라우팅 변경, BGP hijack |
| DNS | DNS poisoning, 가짜 resolver |
| TLS | 가짜 CA 인증서 (사용자 신뢰 시) |
| HTTP | 평문이라면 누구든 |

### 1.1 ARP Spoofing 시연 (이론)
도구: `ettercap`, `bettercap`. **본인 격리 랩에서만.**
```
sudo bettercap -iface eth0
> set arp.spoof.targets 192.168.1.10
> arp.spoof on
> net.sniff on
```

### 1.2 가짜 Wi-Fi (Evil Twin)
공항·카페에서 같은 SSID로 가짜 AP. **공용 Wi-Fi에서 VPN 사용 권장**.

### 1.3 SSL Stripping
HTTPS 강제 안 된 사이트에서 사용자가 `http://` 입력 → 공격자가 HTTP 유지, 서버와는 HTTPS → 평문 노출. **HSTS preload로 차단**.

---

## 2. VPN

### 2.1 기업 VPN의 용도
- 내부 자원 접근 (관리 페이지, DB)
- 외부 작업 시 ISP·LAN 신뢰 안 함
- IP 화이트리스트

### 2.2 종류
- **IPSec** — 전통적
- **OpenVPN** — TLS 기반
- **WireGuard** — 모던, 빠름, 코드 적음 (4K LoC)
- **Cloudflare Tunnel / Tailscale** — Zero Trust Network Access

### 2.3 VPN의 한계
"VPN만 있으면 안전" 함정. VPN 안 트래픽이 다 신뢰되면 침해 한 번에 모두 노출. → **Zero Trust**.

---

## 3. 방화벽·세그멘테이션

### 3.1 계층별 방화벽
| 계층 | 도구 |
|------|------|
| 네트워크 (L3/L4) | iptables, nftables, AWS Security Group, NACL |
| 애플리케이션 (L7) | WAF (Cloudflare, AWS WAF, ModSecurity) |
| 호스트 | UFW, Windows Defender Firewall |
| 컨테이너 | NetworkPolicy (K8s) |

### 3.2 세그멘테이션 모델
```
[Internet]
   ↓ (FW + WAF)
[DMZ]                ← 외부 노출 (LB, WAF, Bastion)
   ↓ (FW)
[Application Tier]   ← Spring 앱
   ↓ (FW)
[Data Tier]          ← DB, Redis
```
계층 간 최소 포트만 허용. DB는 외부에서 절대 닿지 않음.

### 3.3 마이크로 세그멘테이션 (Zero Trust)
"내부 신뢰" 폐기. 서비스간 호출도 mTLS·정책 검증.

도구: AWS Security Group + Network ACL, K8s NetworkPolicy, Istio AuthorizationPolicy, Cilium.

### 3.4 Bastion / Jump Host
관리 접근은 베스천만 거쳐서. SSH가 아닌 **SSM Session Manager**(AWS), **IAP**(GCP) 권장 (포트 안 열고 IAM으로 접근).

---

## 4. WAF (Web Application Firewall)

### 4.1 무엇을 하나
- 알려진 페이로드 시그니처 차단 (SQLi, XSS, RCE 패턴)
- Rate Limiting
- Geo Blocking
- 봇 차단
- 핫픽스: 패치 전 임시 차단

### 4.2 옵션
- **Cloudflare WAF** (가장 쉽다, 무료~상용)
- **AWS WAF** (AWS 통합)
- **ModSecurity + OWASP CRS** (오픈소스)
- **Imperva, F5** (엔터프라이즈)

### 4.3 WAF의 한계
- 비즈니스 로직 결함은 못 막음 (IDOR, race condition)
- 우회 가능 (인코딩, 케이스 변형)
- False positive로 정상 사용자 차단
- WAF "있다"는 사실로 만족하면 안 됨 → 본질 통제는 코드

### 4.4 WAF 룰 학습 — OWASP CRS Paranoia Level
PL 1~4. 높을수록 엄격, false positive ↑. 운영은 PL2부터, 모니터 후 점진적.

---

## 5. DDoS — 방어

### 5.1 종류
| 종류 | 예 |
|------|---|
| Volumetric | UDP/ICMP flood, amplification (DNS, NTP) |
| Protocol | SYN flood, Ping of Death |
| Application | HTTP flood, Slowloris |

### 5.2 방어
- **CDN/Anti-DDoS 서비스**: Cloudflare, AWS Shield, Akamai
- **Rate limiting** (앞단)
- **Caching**: 정적 컨텐츠 캐시로 origin 부하 감소
- **Auto-scaling**
- **Anycast DNS**

---

## 6. SSH 보안

가장 흔한 관리 인터페이스. 잘못하면 큰일.

### 6.1 권장 설정 (`/etc/ssh/sshd_config`)
```
Port 22                                # 또는 비표준 포트 (보안 ≠ 은닉)
PermitRootLogin no
PasswordAuthentication no              # 키 인증만
PubkeyAuthentication yes
ChallengeResponseAuthentication no
KbdInteractiveAuthentication no
AllowUsers deploy admin
MaxAuthTries 3
LoginGraceTime 30
ClientAliveInterval 300
ClientAliveCountMax 2
Protocol 2
```

### 6.2 키 관리
- ED25519 권장 (RSA는 4096+)
- 키에 패스프레이즈
- ssh-agent / 1Password SSH agent
- HW 보안 키 (YubiKey)
- 정기 회전, 퇴사자 키 즉시 제거
- **개인키를 절대 git에 commit 안 함**

### 6.3 Bastion 패턴
```
[Operator] → SSH → [Bastion] → SSH → [App Server (내부망)]
```
Bastion에 로그·MFA. AWS SSM이면 포트 열지 않고도 가능.

### 6.4 무차별 차단
- **fail2ban**: 실패한 SSH 시도 N회 이상이면 자동 IP 차단
- 외부 노출 SSH는 가능하면 IP 화이트리스트 또는 VPN 뒤로

---

## 7. SSO·인증 인프라

### 7.1 표준
- **SAML 2.0** — 엔터프라이즈, XML 기반
- **OIDC / OAuth 2.0** — 모던, JSON·JWT
- **LDAP/AD** — 내부 디렉토리

### 7.2 IdP 옵션
- Okta, Auth0 (상용)
- Keycloak (오픈소스, 강력)
- AWS Cognito, Azure AD

### 7.3 권장
- **모든 직원 SSO + MFA** (TOTP 또는 WebAuthn)
- **Just-in-Time provisioning**
- **자동 deprovisioning** (퇴사 시 즉시)
- **권한 검토**: 분기별 (특히 admin·DB 접근)

---

## 8. 실제 사례

### 2017 — Equifax (네트워크 측면)
WAF는 있었으나 패치 안 함. 네트워크 세그멘테이션 부족으로 DB까지 도달. **A06 + 네트워크 설계 결합 사고**.

### 2019 — Capital One (네트워크 측면)
WAF가 잘못 구성되어 SSRF 가능. + IAM 과다 권한. **앞단(WAF)·뒤단(IAM) 모두 약했음**.

### 2020 — Solarwinds
공격자가 *합법적인 업데이트* 경로로 들어옴. 외부 perimeter 무용지물. → Zero Trust + 행위 기반 탐지 필요.

### 2023 — Okta 침해 (지원 시스템)
지원 직원의 개인 구글 계정에 회사 자격증명 저장 → 탈취 → 회사 시스템 접근. **세그멘테이션 + 개인 계정 사용 금지**.

---

## 9. 실습

### 실습 3.1 — 본인 회사 외부 노출 종합 점검
- Shodan + Censys로 회사 IP 검색
- 외부에서 닿는 포트 인벤토리 작성
- "이 포트가 왜 열려 있는가?" 답할 수 없으면 닫기

### 실습 3.2 — Bettercap (격리망)
홈랩 VM 두 대로 ARP spoofing 시연 (절대 회사망에서 X)

### 실습 3.3 — WAF 룰 학습
ModSecurity + OWASP CRS를 nginx 앞에 붙이고 vulnerable_app에 공격해서 차단 로그 확인.

### 실습 3.4 — SSH 하드닝
본인 VPS 또는 사이드 서버:
- 비밀번호 인증 끄기
- root 로그인 끄기
- fail2ban 설치
- ssh-audit으로 평가
  ```bash
  pip install ssh-audit
  ssh-audit yourdomain.com
  ```
