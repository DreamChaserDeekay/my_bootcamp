# Day 5 (1/2) — 암호학·비밀 관리·의존성

## 1. 암호학 — 개발자가 알아야 할 만큼만

> **자체 알고리즘 만들지 말 것. 검증된 라이브러리를 정확한 모드로.**

### 1.1 대칭 vs 비대칭

| 종류 | 키 | 속도 | 용도 |
|------|---|------|------|
| 대칭 | 동일 키로 암복호화 | 빠름 | 대용량 데이터 |
| 비대칭 | 공개키/개인키 | 느림 | 키 교환, 서명 |

### 1.2 표준 알고리즘 — 무엇을 쓸 것인가

| 용도 | 권장 | 피하기 |
|------|------|--------|
| 대칭 암호 | **AES-256-GCM**, ChaCha20-Poly1305 | DES, 3DES, RC4, ECB |
| 비대칭 암호 | RSA-OAEP (≥2048), ECDH (P-256+) | RSA-PKCS#1 v1.5 (특정 컨텍스트) |
| 서명 | RSA-PSS, Ed25519, ECDSA P-256 | RSA-PKCS#1 v1.5 패딩 (예전 권장) |
| 해시 | SHA-256, SHA-3, BLAKE2 | MD5, SHA-1 |
| HMAC | HMAC-SHA-256 | 평문 prefix 비교 |
| 비밀번호 해시 | Argon2id, bcrypt, scrypt, PBKDF2 | SHA만 |
| 난수 | `SecureRandom` | `Math.random()`, `Random` |
| TLS | 1.3 (1.2 with 강한 cipher 한정 OK) | 1.0/1.1 (deprecated), SSL all |

### 1.3 모드 — 가장 흔한 실수

**ECB 모드는 절대 안 됨.** 같은 평문이 같은 암호문이 되어 패턴 노출.

![ECB는 패턴 보존](https://en.wikipedia.org/wiki/File:Tux.jpg) (Linux 마스코트 Tux로 시연되는 유명한 예)

**AES-CBC는 IV 재사용·예측 가능하면 위험.** Padding oracle 가능.

**AES-GCM이 거의 항상 정답.** Authenticated encryption (무결성+기밀성), nonce 12바이트.

```java
public byte[] encrypt(byte[] plaintext, SecretKey key) throws Exception {
    byte[] nonce = new byte[12];
    new SecureRandom().nextBytes(nonce);
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
    byte[] ct = c.doFinal(plaintext);
    // 결과: nonce(12) || ciphertext || tag(16)
    return ByteBuffer.allocate(12 + ct.length).put(nonce).put(ct).array();
}
```

**Nonce 재사용 절대 금지.** GCM은 같은 키+nonce에 두 번 쓰면 평문 복원 가능.

### 1.4 키 관리
- 키를 코드·DB에 하드코딩 X
- 키는 **KMS** (AWS KMS, GCP KMS, HashiCorp Vault, Azure Key Vault)에서
- 정기적인 키 회전 (rotation)
- 키 사용 감사 로그

### 1.5 비교는 상수 시간으로 (Timing Attack)
HMAC·서명 검증은 짧은 시간 차로 일부 정보 누출 가능.
```java
// ❌ 위험: 한 바이트라도 다르면 즉시 false
if (!receivedMac.equals(expectedMac)) ...

// ✅ 안전: 상수 시간 비교
MessageDigest.isEqual(receivedMac, expectedMac);
```

### 1.6 자주 보는 잘못된 사용

| 잘못 | 올바른 |
|------|------|
| `Random` 으로 토큰 생성 | `SecureRandom` |
| MD5/SHA-1로 비밀번호 | bcrypt + salt |
| AES-128-ECB | AES-256-GCM |
| RSA-1024 | RSA-2048+ 또는 ECC |
| 자체 패딩 구현 | 표준 라이브러리에 맡김 |
| 키 = "MyAppSecretKey123" | KMS / 환경변수 |
| Base64 = 암호화 | 인코딩 ≠ 암호화 |

---

## 2. TLS / HTTPS

### 2.1 무엇이 보호되는가
- **In transit 기밀성** (도청 방어)
- **무결성** (변조 방어)
- **서버 신원 확인** (피싱 일부 방어)

### 2.2 권장 설정
- **TLS 1.3 only** 또는 1.2 + 강한 cipher
- **HSTS** preload
- **Strong cipher suites** (ECDHE+AES-GCM 또는 ChaCha20)
- **OCSP Stapling**
- 인증서: Let's Encrypt 자동 갱신, ACME

검증: https://www.ssllabs.com/ssltest/

### 2.3 Spring Boot TLS
```yaml
server:
  port: 8443
  ssl:
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PW}
    key-store-type: PKCS12
    protocol: TLS
    enabled-protocols: TLSv1.3,TLSv1.2
    ciphers: TLS_AES_256_GCM_SHA384, TLS_CHACHA20_POLY1305_SHA256, ...
```
또는 reverse proxy(nginx, Cloudflare)에서 TLS 종료.

### 2.4 HTTPS 강제 + HSTS
```java
http.requiresChannel(c -> c.anyRequest().requiresSecure());
http.headers(h -> h.httpStrictTransportSecurity(hsts -> hsts
    .maxAgeInSeconds(31536000)
    .includeSubDomains(true)
    .preload(true)
));
```

### 2.5 인증서 검증 — 클라이언트 측
외부 API 호출 시 인증서 검증을 절대 끄지 말 것:
```java
// ❌ 절대 금지 (개발에서도 위험)
trustAllCertificates();
hostnameVerifier((h, s) -> true);
```
사내 CA가 필요하면 해당 CA만 추가, 검증은 유지.

---

## 3. 비밀 관리 (Secrets Management)

### 3.1 안티패턴
- `application.properties`에 `db.password=...` 평문 (git에 commit)
- `.env` 파일 git 포함
- 코드에 `String key = "AKIA..."`
- 슬랙·이메일에 키 평문
- 로그에 키 출력 (객체 toString)

### 3.2 옵션 비교

| 방법 | 장점 | 단점 |
|------|------|------|
| 환경 변수 | 간단 | 프로세스 환경에서 노출 가능 |
| 외부 비밀 관리 (Vault, AWS Secrets Manager) | 회전·감사 | 의존성 추가 |
| K8s Secret | 기본 | 기본은 base64 (encryption at rest 옵션) |
| Sealed Secrets, SOPS | git에 암호화 보관 | 키 관리 별도 |

### 3.3 Spring 통합
```yaml
# AWS Secrets Manager (spring-cloud-aws)
spring:
  config:
    import: aws-secretsmanager:my-app/db,aws-secretsmanager:my-app/api

# Vault
spring:
  cloud:
    vault:
      uri: https://vault.example.com
      authentication: APPROLE
```

### 3.4 비밀 누출 탐지
- **gitleaks** — 커밋 사전 탐지
- **trufflehog** — 히스토리 스캔
- GitHub Secret Scanning — 무료
- 사전 commit hook 설정:
  ```bash
  pre-commit install
  ```

### 3.5 누출 시 대응
1. **즉시 회전**: 키 무효화, 새 키 발급
2. 로그·감사 분석: 누구·언제·어디서 사용했나
3. 영향 범위 (어느 시스템 접근 가능)
4. Git 히스토리 정리(`git filter-repo`) — **다 무의미하다고 가정하고 회전 우선**
5. 사후 분석 (Postmortem)

---

## 4. 의존성 관리 — A06

### 4.1 도구

| 도구 | 무엇 |
|------|------|
| **OWASP Dependency-Check** | 무료, Maven/Gradle 플러그인 |
| **Snyk** | 상용+무료 티어, 자세한 리포트 |
| **GitHub Dependabot** | GitHub 통합, 자동 PR |
| **Renovate** | Bot, 정교한 정책 |
| **Trivy** | 컨테이너 + 의존성 |
| **OWASP DependencyTrack** | SBOM 관리 서버 |

### 4.2 Gradle 적용
```groovy
plugins {
    id 'org.owasp.dependencycheck' version '9.0.7'
}

dependencyCheck {
    failBuildOnCVSS = 7.0
    suppressionFile = 'dependency-check-suppressions.xml'
}
```
```bash
./gradlew dependencyCheckAnalyze
```

### 4.3 SBOM 생성
```groovy
plugins {
    id 'org.cyclonedx.bom' version '1.8.2'
}
```
```bash
./gradlew cyclonedxBom
# build/reports/bom.json
```

### 4.4 라이브러리 평가
새 의존성 추가 전:
- 최근 릴리즈 (1년 이상 무업데이트면 의심)
- 커뮤니티 크기 (GitHub stars, 이슈 응답)
- CVE 이력
- 의존성의 의존성(transitive) 개수
- 라이센스 (GPL 등)

### 4.5 대형 사고 (의존성)
- **Log4Shell** (2021): Apache Log4j 2.0~2.14, JNDI lookup → RCE
- **Spring4Shell** (2022): Spring Framework, DataBinder
- **node-ipc protestware** (2022): 메인테이너가 일부 IP에 파일 삭제 코드 삽입
- **ua-parser-js** (2021): npm 메인테이너 계정 탈취 → 백도어
- **event-stream** (2018): npm 메인테이너 위임 후 백도어

→ Supply chain 보안 (Week 4 DevSecOps에서 더).

---

## 5. 실습

### 실습 8.1 — 비밀번호 해시 적용
`vulnerable_app`의 평문 비밀번호를 BCrypt로 마이그레이션.

### 실습 8.2 — Dependency-Check 실행
본인 사이드 프로젝트 또는 부트캠프 vulnerable_app에:
```bash
./gradlew dependencyCheckAnalyze
```
리포트(`build/reports/dependency-check-report.html`) 확인. High 이상 CVE 정리.

### 실습 8.3 — 비밀 스캔
```bash
# gitleaks 설치 (Windows: scoop, choco 또는 binary)
gitleaks detect --source . -v
```
누출 발견 시 어떻게 대응할지 절차 작성.

### 실습 8.4 — TLS 점검
- https://www.ssllabs.com/ssltest/ 에서 회사 도메인 (외부 노출 시)
- A 등급 이하면 어떤 항목이 부족한지 파악
