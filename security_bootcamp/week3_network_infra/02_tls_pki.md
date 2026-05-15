# Day 2 — TLS · PKI · 인증서

> 모든 보안의 기초. 잘못된 TLS = 평문이나 다름없다.

## 1. TLS가 보장하는 것

| 속성 | 어떻게 |
|------|------|
| **기밀성** | 대칭키로 암호화 (AES-GCM 등) |
| **무결성** | MAC/AEAD 태그 |
| **인증** | 서버 인증서 (옵션: 클라이언트 인증서) |

**보장하지 않는 것**:
- 서버 측 코드의 보안 (XSS는 여전히 가능)
- 발신자가 누구인지 (CA가 신뢰할 수 있다는 가정 위)
- 메타데이터 (SNI로 어떤 사이트 갔는지 일부 노출 — Encrypted ClientHello가 해결 중)

---

## 2. TLS 1.3 핸드셰이크 (간단히)

```
Client                                  Server
  |                                       |
  | ---ClientHello (cipher, key share)--->|
  |                                       |
  |<--ServerHello, Certificate, Finished--|  (1-RTT)
  |    (서버 키, 시그니처)                  |
  |                                       |
  | ---Finished, App Data---------------->|
```

TLS 1.2는 2-RTT. **TLS 1.3은 0-RTT 옵션 있음** (리플레이 공격 주의).

---

## 3. 인증서·PKI

### 3.1 인증서 체인
```
Root CA (Let's Encrypt ISRG Root X1)
   └─ Intermediate (R3)
        └─ End-entity (yourdomain.com)
```

브라우저는 **Root CA**만 신뢰. End-entity → Intermediate → Root까지 체인이 유효해야 통과.

### 3.2 인증서 내용
```bash
openssl s_client -connect example.com:443 -servername example.com < /dev/null
openssl x509 -in cert.pem -text -noout
```

**SAN (Subject Alternative Name)**: 인증서가 어떤 도메인에 유효한지. 와일드카드 `*.example.com` 가능 (하나의 레벨만).

### 3.3 인증서 발급 — Let's Encrypt + ACME
무료, 자동화 가능.
```bash
# certbot 표준
sudo certbot --nginx -d example.com -d www.example.com

# DNS-01 (와일드카드 가능)
sudo certbot certonly --dns-cloudflare -d "*.example.com"
```

### 3.4 OCSP / CRL
인증서 폐기 확인. **OCSP Stapling**: 서버가 OCSP 응답을 인증서와 함께 제공 → 클라이언트가 CA에 별도 요청 안 함.

### 3.5 인증서 투명성 (CT) — 모든 발급 인증서가 공개됨
- https://crt.sh 에서 모든 발급 이력 조회 가능
- 공격자가 발급한 fake 인증서도 보임 (모니터링 가능)
- 본인 도메인 발급 알림 설정: certspotter, Facebook CT Monitor

---

## 4. 약한 TLS — 점검 포인트

### 4.1 SSL Labs A+ 받기
https://www.ssllabs.com/ssltest/

A+ 위한 조건:
- TLS 1.2+ only (1.0/1.1 disable)
- Strong cipher suite ordering
- 2048-bit RSA 또는 ECDSA
- **HSTS** `max-age >= 6 months`, includeSubDomains, preload
- OCSP Stapling
- No mixed content
- No known vulnerabilities (Heartbleed, POODLE, BEAST, ROBOT, …)

### 4.2 알려진 TLS 취약점
| CVE/이름 | 무엇 | 대응 |
|---------|------|------|
| Heartbleed (CVE-2014-0160) | OpenSSL 메모리 노출 | 패치 |
| POODLE | SSLv3 | SSLv3 비활성 |
| BEAST | TLS 1.0 CBC | TLS 1.2+ |
| FREAK / Logjam | 약한 export cipher | 비활성 |
| ROBOT | RSA padding oracle | 패치 + ECDHE |
| Sweet32 | 3DES 64bit | 3DES 비활성 |
| LUCKY13 | CBC timing | AEAD(GCM) 사용 |

### 4.3 nginx 권장 설정
Mozilla SSL Configuration Generator 사용: https://ssl-config.mozilla.org

```nginx
# Modern (TLS 1.3 only)
ssl_protocols TLSv1.3;
ssl_prefer_server_ciphers off;

# Intermediate (TLS 1.2+)
ssl_protocols TLSv1.2 TLSv1.3;
ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:...;
ssl_prefer_server_ciphers off;

ssl_session_timeout 1d;
ssl_session_cache shared:MozSSL:10m;
ssl_session_tickets off;

# OCSP stapling
ssl_stapling on;
ssl_stapling_verify on;
resolver 1.1.1.1 8.8.8.8 valid=60s;
resolver_timeout 2s;

add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload" always;
```

### 4.4 testssl.sh
종합 테스트 도구.
```bash
docker run --rm drwetter/testssl.sh https://example.com
```

---

## 5. mTLS (Mutual TLS)

서버뿐 아니라 **클라이언트도 인증서로 인증**. 마이크로서비스간 통신, B2B API에 사용.

```yaml
# Spring Boot
server:
  ssl:
    client-auth: need        # 또는 want
    trust-store: classpath:truststore.p12
    trust-store-password: ...
```

Zero Trust 네트워크에서 핵심. Service Mesh (Istio, Linkerd) 가 자동으로 처리.

---

## 6. Certificate Pinning

클라이언트가 특정 CA만 신뢰 (모바일 앱에 흔함). MITM 방어 강화.

**주의**: Pin이 잘못되면 모든 클라이언트 접근 불가. Backup pin 필수.

---

## 7. 인증서 만료 — 사고 단골

대형 사고: Spotify, LinkedIn, GitHub 등이 인증서 만료로 다운된 적 있음.

### 대응
- 자동 갱신 (Let's Encrypt + certbot --nginx)
- 만료 모니터링 (Prometheus blackbox exporter, Datadog, UptimeRobot)
- 알림 30일·7일·1일 전
- Slack/PagerDuty 연동

```bash
# 만료일 점검
echo | openssl s_client -connect example.com:443 -servername example.com 2>/dev/null | \
  openssl x509 -noout -enddate
```

---

## 8. TLS 클라이언트 측 (개발자 흔한 실수)

### 8.1 검증 끄기 — 절대 금지
```java
// ❌
SSLContext sc = SSLContext.getInstance("TLS");
sc.init(null, trustAllManagers, new SecureRandom());
HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

// 또는
HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
```

### 8.2 사내 CA 추가 — 안전한 방법
JDK truststore에 사내 CA 추가:
```bash
keytool -import -alias mycompany-ca -file mycompany-ca.crt \
  -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit
```
또는 Spring `RestTemplate`에 별도 SSLContext:
```java
SSLContext sslContext = SSLContextBuilder.create()
    .loadTrustMaterial(myTruststore, null)
    .build();
HttpClient client = HttpClients.custom().setSSLContext(sslContext).build();
```

---

## 9. 실습

### 실습 2.1 — SSL Labs 점검
회사 외부 도메인을 https://www.ssllabs.com/ssltest/ 에 입력. 결과 분석.

### 실습 2.2 — testssl.sh 자가 점검
```bash
docker run --rm drwetter/testssl.sh https://yourdomain.com > tls_report.txt
```

### 실습 2.3 — 인증서 모니터링
crt.sh에서 본인 도메인 검색 → 발급 이력 확인. Cert Spotter 알림 등록.

### 실습 2.4 — Let's Encrypt + nginx 실습
로컬에 nginx + Let's Encrypt (Staging) 발급 → A+ 받기

### 실습 2.5 — 클라이언트 코드 점검
```
grep -rn "trustAllCerts\|setDefaultHostnameVerifier\|verify.*true" src/
```
발견 시 즉시 제거 또는 정당한 사내 CA 등록으로 대체.

---

## 정리
- TLS 1.3 + 강한 cipher + HSTS + 자동 갱신 + 모니터링 = A+
- 클라이언트 측은 절대 검증 끄지 말 것
- 인증서 발급 모니터링으로 도메인 탈취 조기 탐지
