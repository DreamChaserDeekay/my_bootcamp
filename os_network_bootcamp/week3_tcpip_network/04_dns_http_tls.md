# Day 4 — DNS · HTTP · TLS

## 한 줄 요약

웹 한 번 요청에는 **DNS → TCP → TLS → HTTP** 네 단계가 일어난다. 어디가 느린지·왜 실패했는지를 분리해서 보는 능력이 운영의 핵심이다.

## 학습 목표

- [ ] DNS의 계층 구조와 레코드 타입을 안다 (A, AAAA, CNAME, MX, TXT, SRV)
- [ ] `dig`, `Resolve-DnsName`으로 한 단계씩 추적한다
- [ ] HTTP/1.0 vs 1.1 vs 2 vs 3의 차이를 안다
- [ ] HTTP 헤더의 주요 항목과 캐싱 헤더(`Cache-Control`, `ETag`, `Last-Modified`)를 안다
- [ ] TLS handshake 흐름을 안다 (TLS 1.2, 1.3 차이 포함)
- [ ] 인증서·체인·SAN·OCSP·HSTS 기본 개념을 안다
- [ ] `curl -v`, `openssl s_client`로 각 단계를 분리 디버깅한다

---

## 1. DNS — 이름을 IP로

### 계층 구조

```
.                          (Root, 13개 서버 클러스터)
├── com.                   (TLD)
│   ├── google.com.        (Authoritative)
│   ├── example.com.
│   └── ...
├── kr.
│   └── naver.com.
└── ...
```

### 풀이 흐름 (recursive lookup)

```
Client → Local Resolver (ISP DNS, 1.1.1.1 등)
            ↓
         Root (".") — "com.은 어디?"
            ↓
         TLD ("com.") — "google.com.은 어디?"
            ↓
         Authoritative — "A 레코드: 142.250.207.110"
            ↓
         Local Resolver가 캐시 후 응답
Client에 반환
```

### 레코드 타입

| 타입 | 의미 | 예 |
|---|---|---|
| **A** | IPv4 주소 | `example.com → 93.184.216.34` |
| **AAAA** | IPv6 주소 | `example.com → 2606:2800:220:1::` |
| **CNAME** | 별칭 (다른 이름으로) | `www.example.com → example.com` |
| **MX** | 메일 서버 | `example.com → 10 mail.example.com` |
| **TXT** | 텍스트 (SPF, DKIM, 검증) | `v=spf1 include:_spf.google.com` |
| **NS** | 권한 네임서버 | |
| **SOA** | 도메인 권한 시작 | |
| **SRV** | 서비스+포트 (Kerberos, SIP) | |
| **PTR** | 역방향 (IP → 이름) | |
| **CAA** | 발급 가능 CA 제한 | |

### TTL과 캐싱

- 각 레코드에는 TTL(초). 클라이언트·Resolver가 그 시간 동안 캐시
- 짧은 TTL: 자주 바뀌는 (CDN, 장애 조치). DNS 부하 ↑
- 긴 TTL: 안정적. 변경 시 전 세계 반영에 시간 걸림

### dig — DNS의 황금 도구

```bash
# 기본
dig example.com
dig example.com A
dig example.com AAAA
dig example.com MX
dig example.com TXT
dig example.com ANY

# 특정 서버에 질의
dig @1.1.1.1 example.com
dig @8.8.8.8 example.com

# 추적 (root부터)
dig +trace example.com

# 짧게
dig +short example.com
dig +short example.com MX

# 역방향
dig -x 93.184.216.34
```

### nslookup (전통)

```bash
nslookup example.com
nslookup -type=MX example.com
nslookup example.com 1.1.1.1
```

### PowerShell

```powershell
Resolve-DnsName example.com
Resolve-DnsName example.com -Type MX
Resolve-DnsName example.com -Server 1.1.1.1
Resolve-DnsName 93.184.216.34 -Type PTR

# 로컬 캐시
Get-DnsClientCache
Clear-DnsClientCache
```

---

## 2. DNS 트러블슈팅

### "도메인이 안 풀려요"

```bash
# 1. /etc/hosts 우선 확인 (Windows: C:\Windows\System32\drivers\etc\hosts)
cat /etc/hosts | grep example.com

# 2. 어느 DNS 서버 쓰나
cat /etc/resolv.conf

# 3. 그 DNS가 응답하나
dig @<dns-server> example.com

# 4. 다른 DNS는?
dig @1.1.1.1 example.com
dig @8.8.8.8 example.com

# 5. 권한 서버 직접 조회 (TTL 캐시 문제 회피)
dig +trace example.com
```

### "DNS는 되는데 IP가 잘못된 곳을 가리켜요"

- 캐시 문제: `sudo systemd-resolve --flush-caches`, `ipconfig /flushdns`
- 권한 서버는 맞나? `dig +trace`로 확인
- CDN/GeoDNS가 다른 지역 IP를 반환할 수도 (정상)

### /etc/hosts — 디버깅 친구

```bash
# DNS 우회 (테스트용)
sudo vim /etc/hosts
# 추가:
# 192.168.1.100 myapp.local

# Windows
notepad C:\Windows\System32\drivers\etc\hosts
```

⚠ /etc/hosts에 임시로 넣은 항목을 그대로 두면 DNS 변경 후에도 옛 IP로 가서 디버깅 지옥. **반드시 작업 후 원복**.

---

## 3. HTTP 기본

### 요청·응답 구조

```
요청:
GET /users/42 HTTP/1.1
Host: api.example.com
User-Agent: curl/7.88.0
Accept: application/json

응답:
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 42
Cache-Control: max-age=60

{"id":42,"name":"Alice"}
```

### 메서드

| 메서드 | 의미 | 멱등? | 안전? |
|---|---|---|---|
| GET | 조회 | ⭕ | ⭕ |
| POST | 생성·임의 동작 | ❌ | ❌ |
| PUT | 전체 교체 | ⭕ | ❌ |
| PATCH | 부분 수정 | ❌(설계 따라) | ❌ |
| DELETE | 삭제 | ⭕ | ❌ |
| HEAD | 헤더만 | ⭕ | ⭕ |
| OPTIONS | 지원 메서드 확인 (CORS preflight) | ⭕ | ⭕ |

### 상태 코드

| 범위 | 의미 | 자주 보는 것 |
|---|---|---|
| 1xx | 정보 | 100 Continue, 101 Switching Protocols |
| 2xx | 성공 | 200 OK, 201 Created, 204 No Content |
| 3xx | 리다이렉트 | 301 Moved Permanently, 302 Found, 304 Not Modified |
| 4xx | 클라이언트 에러 | 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 429 Too Many Requests |
| 5xx | 서버 에러 | 500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable, 504 Gateway Timeout |

### 헤더 — 핵심만

| 헤더 | 의미 |
|---|---|
| `Host` | 가상호스트 (필수, HTTP/1.1) |
| `Content-Type` | 본문 MIME 타입 |
| `Content-Length` | 본문 바이트 수 |
| `Transfer-Encoding: chunked` | 청크 인코딩 |
| `Connection: keep-alive` | 연결 재사용 |
| `Authorization: Bearer ...` | 인증 토큰 |
| `Cookie` / `Set-Cookie` | 쿠키 |
| `Cache-Control: max-age=60` | 캐시 정책 |
| `ETag` / `If-None-Match` | 조건부 GET (304 활용) |
| `Last-Modified` / `If-Modified-Since` | 시간 기반 캐시 |
| `Accept-Encoding: gzip, br` | 압축 협상 |
| `User-Agent` | 클라이언트 식별 |
| `Referer` | 출처 (오타 그대로 표준화됨) |
| `Origin` | CORS의 출처 |
| `X-Forwarded-For` | 프록시 뒤 실제 IP (커스텀) |

---

## 4. HTTP 버전 비교

| 항목 | HTTP/1.0 | HTTP/1.1 | HTTP/2 | HTTP/3 |
|---|---|---|---|---|
| 연결 재사용 | ❌ (Connection: close) | ⭕ (keep-alive) | ⭕ (멀티플렉싱) | ⭕ |
| 멀티플렉싱 | ❌ | ❌ (HOL blocking) | ⭕ (스트림) | ⭕ |
| 헤더 압축 | ❌ | ❌ | HPACK | QPACK |
| 서버 푸시 | ❌ | ❌ | ⭕ (사실상 폐기) | ⭕ |
| 전송 | TCP | TCP | TCP | **UDP (QUIC)** |
| TLS | 옵션 | 옵션 | 사실상 필수 | 필수 |
| HOL blocking | TCP+HTTP 둘 다 | HTTP 레벨 | TCP 레벨 | 없음 |

### HTTP/1.1 keep-alive

```http
Connection: keep-alive
Keep-Alive: timeout=60, max=100
```

같은 TCP 연결에서 여러 요청 처리. 연결 수립 비용(handshake) 절약.

### HTTP/2 멀티플렉싱

한 TCP 연결에서 여러 요청/응답을 **스트림**으로 동시에. HOL blocking이 HTTP 레벨에서는 해결. 단, TCP 레벨 패킷 손실은 모든 스트림에 영향.

### HTTP/3 (QUIC)

UDP 위에 신뢰성·암호화를 재구현. TCP 핸드셰이크 + TLS 핸드셰이크를 0~1 RTT로 합침.

---

## 5. curl 마스터

### 기본

```bash
curl https://example.com/                       # GET, 본문만
curl -i https://example.com/                    # 헤더 포함
curl -I https://example.com/                    # HEAD (헤더만)
curl -v https://example.com/                    # verbose (handshake, 헤더 모두)
curl -vvv https://example.com/                  # 더 자세히
curl -s ...                                     # silent (진행바 끔)
curl -L https://example.com/                    # 리다이렉트 따라가기

# 메서드
curl -X POST -d '{"name":"Alice"}' \
    -H "Content-Type: application/json" \
    https://api.example.com/users

# JSON 일반화
curl -H "Content-Type: application/json" \
    --data-binary @body.json \
    https://api.example.com/

# 인증
curl -u user:pass https://api.example.com/
curl -H "Authorization: Bearer $TOKEN" https://api.example.com/

# 쿠키
curl -c cookies.txt -b cookies.txt https://example.com/

# 파일 업로드
curl -F file=@./report.pdf https://upload.example.com/

# 다운로드 (resume)
curl -O https://example.com/big.zip
curl -C - -O https://example.com/big.zip

# 타이밍 분석 (매우 유용)
curl -w "@-" -o /dev/null -s https://example.com/ <<'EOF'
DNS:        %{time_namelookup}s
Connect:    %{time_connect}s
TLS:        %{time_appconnect}s
TTFB:       %{time_starttransfer}s
Total:      %{time_total}s
EOF
```

### 디버깅 시 첫 명령

```bash
curl -v https://example.com/ 2>&1 | head -50
# *   Trying 93.184.216.34:443...
# * Connected to example.com (93.184.216.34) port 443
# * TLSv1.3 (OUT), TLS handshake, Client hello
# ...
# > GET / HTTP/2
# > Host: example.com
# ...
# < HTTP/2 200
# < content-type: text/html
```

---

## 6. TLS — 전송 보안

### 목표

1. **기밀성**: 도청 방지 (대칭 암호)
2. **무결성**: 변조 방지 (MAC)
3. **인증**: 상대가 진짜 그 도메인인가 (인증서)

### TLS 1.2 vs 1.3

| | TLS 1.2 | TLS 1.3 |
|---|---|---|
| 핸드셰이크 | 2 RTT | **1 RTT** (0-RTT 옵션) |
| 암호 협상 | 많음, 옛것 포함 | 5개 AEAD만 |
| 옛 알고리즘 | RC4, SHA1, DES, RSA key exchange 가능 | 모두 제거 |
| Forward Secrecy | 옵션 | 필수 |
| 헤더 암호화 | 부분 | 거의 다 |

### TLS 1.3 handshake (단순화)

```
Client                          Server
  │── ClientHello (key share) ─►│
  │                              │
  │◄─ ServerHello (key share)    │
  │   {Cert, CertVerify, Finished} (서버 인증서 등 모두 암호화됨)
  │                              │
  │── Finished ─────────────────►│
  │                              │
  │   ─── Application Data ──    │
```

### TLS 1.2 handshake (전통)

```
Client                          Server
  │── ClientHello ──────────────►│
  │◄─ ServerHello                 │
  │◄─ Certificate                 │
  │◄─ ServerKeyExchange (DH 등)   │
  │◄─ ServerHelloDone             │
  │── ClientKeyExchange ────────►│
  │── ChangeCipherSpec ─────────►│
  │── Finished ─────────────────►│
  │◄─ ChangeCipherSpec            │
  │◄─ Finished                    │
  │   ─── Application Data ──    │
```

---

## 7. 인증서 (X.509)

### 체인

```
[Root CA] (브라우저·OS에 미리 포함)
    └─ [Intermediate CA]
            └─ [Server Cert] (example.com 용)
                    └─ 서버가 보냄 (인증서 + 중간 CA)
                    └─ 클라이언트가 Root까지 검증
```

> ⚠ **체인 미포함**: 서버가 중간 CA를 같이 안 보내면 일부 클라이언트(특히 Java, mobile)에서 "untrusted" 에러. 항상 fullchain 설치.

### 인증서 필드

```
Subject: CN=example.com
Subject Alternative Names: DNS:example.com, DNS:www.example.com
Issuer: CN=Let's Encrypt Authority X3
Validity: 2026-01-01 ~ 2026-04-01
Public Key: RSA 2048 / ECDSA P-256
Signature: SHA256 with RSA
Extensions:
  - Key Usage: Digital Signature, Key Encipherment
  - Extended Key Usage: Server Authentication
  - Subject Alternative Name (SAN)
```

> **CN(Common Name)은 사실상 deprecated**. 브라우저는 **SAN**만 본다. SAN에 도메인이 없으면 안 됨.

### 검사하기

```bash
# 서버에 접속해 인증서 가져오기
openssl s_client -connect example.com:443 -servername example.com < /dev/null 2>/dev/null | \
    openssl x509 -text -noout

# 만료일만
openssl s_client -connect example.com:443 -servername example.com < /dev/null 2>/dev/null | \
    openssl x509 -enddate -noout

# 파일에 저장된 인증서
openssl x509 -in cert.pem -text -noout

# 체인 검증
openssl s_client -connect example.com:443 -servername example.com -showcerts < /dev/null
```

### PowerShell

```powershell
# 원격 인증서
$req = [System.Net.WebRequest]::Create("https://example.com")
$req.GetResponse() | Out-Null
$cert = $req.ServicePoint.Certificate
$cert | Format-List Subject, Issuer, NotBefore, NotAfter

# 로컬 인증서 저장소
Get-ChildItem Cert:\LocalMachine\My
Get-ChildItem Cert:\LocalMachine\Root | Select Subject, NotAfter
```

---

## 8. SNI · ALPN · HSTS — 알아둘 용어

### SNI (Server Name Indication)

TLS ClientHello에 호스트명을 포함. 한 IP에 여러 도메인이 있을 때 서버가 어느 인증서 줄지 결정.

```bash
# SNI 없이 (옛 클라이언트)
openssl s_client -connect 1.2.3.4:443 < /dev/null

# SNI 명시
openssl s_client -connect 1.2.3.4:443 -servername example.com < /dev/null
```

### ALPN (Application-Layer Protocol Negotiation)

TLS 핸드셰이크 중에 HTTP/2 또는 HTTP/1.1을 협상.

```bash
# ALPN 지정
curl -v --http2 https://example.com/
openssl s_client -alpn h2,http/1.1 -connect example.com:443 -servername example.com
```

### HSTS (HTTP Strict Transport Security)

```http
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
```

브라우저에게 "이 도메인은 1년간 무조건 HTTPS로만 접속해라" 강제. preload 리스트에 등록되면 첫 접속부터 HTTPS.

### OCSP / CRL

인증서 폐기 확인. OCSP Stapling으로 서버가 미리 응답을 첨부하면 빠름.

---

## 9. ❌ 위험 / ✅ 안전

### 인증서 검증 끄지 말 것

```java
// ❌ "테스트라서" 검증 끔 → 운영에 그대로 배포되어 MITM 위험
TrustManager[] trustAll = { new X509TrustManager() {
    public X509Certificate[] getAcceptedIssuers() { return null; }
    public void checkClientTrusted(X509Certificate[] c, String a) {}
    public void checkServerTrusted(X509Certificate[] c, String a) {}
}};
SSLContext ctx = SSLContext.getInstance("TLS");
ctx.init(null, trustAll, null);

// ✅ 자체서명 인증서를 truststore에 추가
KeyStore ks = KeyStore.getInstance("JKS");
ks.load(new FileInputStream("my-truststore.jks"), "changeit".toCharArray());
TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
tmf.init(ks);
```

```bash
# ❌
curl -k https://internal-api.example.com/

# ✅
curl --cacert /etc/ssl/certs/our-internal-ca.pem https://internal-api.example.com/
```

### 옛 TLS 끄기

서버에서 TLS 1.0, 1.1 비활성화. **TLS 1.2+** 만 허용 (PCI DSS 요구사항).

```nginx
ssl_protocols TLSv1.2 TLSv1.3;
ssl_ciphers HIGH:!aNULL:!MD5;
ssl_prefer_server_ciphers off;
```

### DNS hijacking 주의

공용 Wi-Fi에서 DNS 응답이 조작될 수 있음. DNS over HTTPS(DoH), DNS over TLS(DoT) 권장. 운영서버에서는 신뢰된 DNS만 사용.

---

## 10. 실습

### Step 1: dig 마라톤

```bash
dig naver.com
dig naver.com AAAA
dig naver.com MX
dig naver.com TXT
dig naver.com NS

dig +short google.com
dig +trace google.com | head -20
```

### Step 2: curl로 단계 분리

```bash
curl -w "\nDNS:%{time_namelookup}s\nConnect:%{time_connect}s\nTLS:%{time_appconnect}s\nTTFB:%{time_starttransfer}s\nTotal:%{time_total}s\n" \
    -o /dev/null -s https://www.google.com/

# 결과 예:
# DNS:    0.012s
# Connect: 0.087s
# TLS:    0.165s
# TTFB:   0.234s
# Total:  0.298s
```

각 단계의 시간 의미:

- DNS lookup
- TCP connect (handshake 완료까지)
- TLS handshake 완료까지
- TTFB (Time To First Byte): 응답 첫 바이트
- Total

### Step 3: 인증서 검사

```bash
# 본인이 자주 쓰는 사이트의 인증서 만료일
for host in google.com github.com naver.com; do
    expire=$(openssl s_client -connect $host:443 -servername $host < /dev/null 2>/dev/null | \
             openssl x509 -enddate -noout)
    echo "$host -> $expire"
done

# PowerShell
@('google.com','github.com','naver.com') | ForEach-Object {
    try {
        $req = [Net.WebRequest]::Create("https://$_")
        $req.GetResponse() | Out-Null
        [PSCustomObject]@{
            Host = $_
            Subject = $req.ServicePoint.Certificate.Subject
            Expires = $req.ServicePoint.Certificate.GetExpirationDateString()
        }
    } catch { [PSCustomObject]@{ Host = $_; Error = $_.Exception.Message } }
}
```

### Step 4: HTTP 헤더 디테일

```bash
curl -I https://github.com/
curl -I --http2 https://github.com/
curl -v --http1.1 https://github.com/ 2>&1 | grep -E "^(<|>) "
```

`Cache-Control`, `Strict-Transport-Security`, `Content-Security-Policy` 등 보안 헤더 확인.

### Step 5: Spring Boot 클라이언트 디버깅

```yaml
# application.yml
logging:
  level:
    org.apache.http: DEBUG       # Apache HttpClient
    reactor.netty.http.client: DEBUG  # WebClient
    javax.net.ssl: DEBUG         # TLS (시끄러움)
```

---

## 더 읽어볼 자료

- 📘 『High Performance Browser Networking』 (Ilya Grigorik, 무료: <https://hpbn.co>)
- 📘 『Bulletproof SSL and TLS』 (Ivan Ristić)
- 🔗 SSL Labs (서버 등급): <https://www.ssllabs.com/ssltest/>
- 🔗 RFC 8446 — TLS 1.3
- 🔗 RFC 9110 — HTTP Semantics
- 🔗 Cloudflare's "How TLS works": <https://blog.cloudflare.com/tls-1-3-overview-and-q-and-a/>

---

## 자가 점검

- [ ] `dig`의 `+trace`가 무엇을 보여주는지 안다
- [ ] HTTP/1.1과 HTTP/2의 핵심 차이를 안다
- [ ] TLS 1.3가 1.2보다 빠른 이유를 설명한다
- [ ] SAN과 CN의 관계, SNI의 역할을 안다
- [ ] `curl -w`로 단계별 시간을 분석한다
- [ ] `openssl s_client`로 인증서를 직접 본다

다음: [`05_packet_capture_firewall.md`](05_packet_capture_firewall.md)
