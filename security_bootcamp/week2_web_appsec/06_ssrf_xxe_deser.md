# Day 4 (2/3) — SSRF · XXE · 역직렬화

> 서버 내부 깊은 곳을 노리는 공격들. 외부 공격자가 **내부 자원**에 손을 뻗는다.

## 1. SSRF (Server-Side Request Forgery)

### 1.1 원리
서버가 사용자 입력 기반으로 **다른 URL에 요청**할 때, 공격자가 그 URL을 조작.

```java
// ❌ 위험
@GetMapping("/fetch")
public String fetch(@RequestParam String url) {
    return new RestTemplate().getForObject(url, String.class);
}
```
공격: `?url=http://169.254.169.254/latest/meta-data/iam/security-credentials/`

### 1.2 SSRF로 노리는 것
| 대상 | 결과 |
|------|------|
| 클라우드 메타데이터 (AWS 169.254.169.254, GCP, Azure) | IAM 자격증명 탈취 |
| 내부망 서비스 (10.x.x.x, 172.16-31, 192.168) | 외부에서 볼 수 없는 관리 페이지 |
| localhost (`127.0.0.1`, `[::1]`) | 같은 서버의 Redis, Elasticsearch, admin |
| Java RMI (`rmi://...`) / LDAP | RCE 가능성 |
| `file://` | 파일 읽기 |
| `gopher://` (옛 자바) | TCP 프로토콜 임의 송신 |

### 1.3 Capital One 사고 (2019)
**SSRF + IMDSv1 = 1억 600만 명 유출.** 잘못된 WAF 룰 → SSRF → AWS EC2 메타데이터 → 임시 자격증명 → S3.

### 1.4 우회 기법 (공격자가 사용)
- `http://127.0.0.1` 차단 → `http://127.1`, `http://2130706433` (decimal), `http://0x7f.0x0.0x0.0x1`
- `localhost` 차단 → `localtest.me`, `127-0-0-1.nip.io`
- DNS Rebinding: 처음에는 정상 IP, 두 번째 조회에서 내부 IP
- 30x 리다이렉트로 우회

### 1.5 방어

```java
// ✅ 1. 화이트리스트로 허용 도메인 제한
private static final Set<String> ALLOWED = Set.of("api.partner.com", "cdn.app.com");

public String fetch(String url) {
    URI uri = URI.create(url);
    if (!"https".equals(uri.getScheme())) throw new IllegalArgumentException();
    if (!ALLOWED.contains(uri.getHost())) throw new IllegalArgumentException();
    return restTemplate.getForObject(uri, String.class);
}

// ✅ 2. 사설망 IP 차단
private boolean isInternal(InetAddress addr) {
    return addr.isAnyLocalAddress()
        || addr.isLoopbackAddress()
        || addr.isLinkLocalAddress()
        || addr.isSiteLocalAddress()  // 10.x, 172.16-31.x, 192.168.x
        || addr.getHostAddress().startsWith("169.254.")  // metadata
        || addr.getHostAddress().startsWith("100.64.");  // CGNAT
}

public String fetch(String url) {
    URI uri = URI.create(url);
    // DNS 조회 후 검사 (DNS rebinding 방지: 검사한 IP로 직접 연결)
    InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
    for (InetAddress a : addresses) {
        if (isInternal(a)) throw new IllegalArgumentException("Internal");
    }
    return ...;
}
```

### 1.6 인프라 레벨 방어
- **Egress 방화벽**: 앱 서버가 외부로 나가는 트래픽을 화이트리스트 도메인만 허용
- **AWS IMDSv2 강제**: 토큰 기반, SSRF 무력화
  ```bash
  aws ec2 modify-instance-metadata-options --instance-id ... --http-tokens required
  ```
- **HTTP 클라이언트에 리다이렉트 제한**: redirect를 따라가지 않거나, 같은 origin만
- **응답 길이·시간 제한**

---

## 2. XXE (XML External Entity)

### 2.1 원리
XML 파서가 외부 엔티티 참조를 처리하면 서버에서 임의 파일·URL 접근.

```xml
<?xml version="1.0"?>
<!DOCTYPE foo [
  <!ENTITY xxe SYSTEM "file:///etc/passwd">
]>
<user><name>&xxe;</name></user>
```

서버가 `name`을 응답에 표시하면 → `/etc/passwd` 내용 노출. SSRF로도 활용.

### 2.2 Java 기본 파서가 위험
JAXP, JDOM, dom4j 등 옛 버전 기본 설정이 외부 엔티티 허용.

### 2.3 방어 — 모든 파서에 외부 엔티티 비활성화

```java
public Document parseXml(InputStream in) throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    // 핵심: 외부 엔티티 처리 끄기
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
    dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    dbf.setXIncludeAware(false);
    dbf.setExpandEntityReferences(false);
    return dbf.newDocumentBuilder().parse(in);
}
```

OWASP Cheat Sheet "XXE Prevention" 참조 — 파서별 설정 다름.

### 2.4 우회·확장
- SOAP, SVG, DOCX, XLSX 등 **내부에 XML 가진 포맷** 모두 잠재 위험
- 외부 DTD가 차단되면 Parameter Entity로 우회 (Blind XXE → OOB)

### 2.5 더 나은 해결책
**JSON으로 마이그레이션.** XML이 꼭 필요하지 않으면 JSON. JSON은 외부 엔티티 개념 자체가 없음.

---

## 3. Java Deserialization — RCE의 지름길

### 3.1 원리
Java `ObjectInputStream.readObject()`로 신뢰할 수 없는 데이터를 역직렬화하면 **임의 코드 실행 가능**.

```java
// ❌ 매우 위험
ObjectInputStream ois = new ObjectInputStream(request.getInputStream());
Object obj = ois.readObject();
```

### 3.2 Gadget Chain
Apache Commons Collections, Spring, Hibernate 등 라이브러리에 있는 **클래스들의 readObject 사이드 이펙트를 체이닝**하여 `Runtime.exec()` 까지 도달.

도구 **ysoserial** 이 페이로드를 자동 생성:
```bash
java -jar ysoserial.jar CommonsCollections5 'calc.exe' > payload.bin
```

### 3.3 발견 가능한 곳
- Java RMI
- JMX
- Apache Commons HTTP File Upload
- JBoss/WebLogic 관리 인터페이스
- Spring `RMIServiceExporter`, `HessianServiceExporter`
- Redis/RabbitMQ payload가 Java 객체일 때
- 세션을 Java 직렬화로 저장 (옛 패턴)

### 3.4 방어

**1순위: Java 직렬화 자체를 안 쓴다.** JSON(Jackson), Protobuf, Avro.

직렬화 불가피하면:
- **ObjectInputFilter** (Java 9+) — 허용 클래스 화이트리스트
```java
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
    "com.myapp.dto.*;java.lang.*;!*"
);
ObjectInputStream ois = new ObjectInputStream(in);
ois.setObjectInputFilter(filter);
```
- **JEP 290** 전역 필터 (`jdk.serialFilter` 시스템 프로퍼티)
- 무결성 서명 (HMAC)으로 변조 방지

### 3.5 Jackson — 안전하지만 함정 있음
```java
ObjectMapper mapper = new ObjectMapper();
// ❌ Default typing 켜면 Java deser와 비슷한 위험
mapper.enableDefaultTyping();  // 또는 activateDefaultTyping
mapper.readValue(input, Object.class);
```

Jackson CVE 다수: `CVE-2019-12384`, `CVE-2020-9548` 등. 기본 typing 켜는 것 절대 금지. 모든 polymorphic은 `@JsonTypeInfo` + `@JsonSubTypes` 화이트리스트.

---

## 4. SSRF·XXE 결합 — 클라우드 자격증명 탈취 시연

가상 시나리오 (AWS):
1. 앱이 SVG 업로드 받음 → 미리보기 렌더
2. SVG 안 XXE: `<!ENTITY x SYSTEM "http://169.254.169.254/latest/meta-data/iam/security-credentials/role-name">`
3. 응답에 자격증명 노출 → AWS CLI에 주입
4. S3·DynamoDB 등 IAM 권한 대로 접근

방어:
- 이미지 파서를 신뢰할 수 있는 라이브러리로 (libvips, ImageMagick은 옛날엔 위험했지만 최근 강화됨)
- SVG는 별도 sanitizer 또는 비허용
- 컨테이너에 IMDSv2 강제
- IAM Role 최소 권한

---

## 5. 실습

### 실습 6.1 — SSRF
`vulnerable_app`의 `/vuln/fetch?url=...`:
- `?url=http://169.254.169.254/` (메타데이터 흉내) — 응답 확인
- `?url=http://localhost:8080/actuator/env`
- `?url=file:///etc/hostname`
- 패치된 `/safe/fetch`에 동일 시도 → 차단

### 실습 6.2 — XXE
`vulnerable_app/xxe-poc.xml`을 만들고 `/vuln/xml` 엔드포인트에 POST:
```xml
<?xml version="1.0"?>
<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///c:/windows/win.ini">]>
<note>&xxe;</note>
```
응답에 파일 내용 나타나는지 확인.

### 실습 6.3 — Deserialization 데모
WebGoat의 "Deserialization" 레슨 풀기. ysoserial로 페이로드 생성.

### 실습 6.4 — 본인 코드 점검
```
grep -rn 'ObjectInputStream\|readObject\|XMLDecoder' src/main/java/
grep -rn 'DocumentBuilderFactory\|SAXParser' src/main/java/
grep -rn 'RestTemplate\|WebClient\|HttpURLConnection' src/main/java/
```
- ObjectInputStream 발견 시 → 다른 방식으로 교체
- XML 파서 발견 시 → secure feature 설정 확인
- HTTP 클라이언트 발견 시 → URL이 어디서 오는지, 검증되는지 확인
