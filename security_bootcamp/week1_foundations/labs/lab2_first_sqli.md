# Lab 2 — 첫 SQL Injection 공격과 방어

## 목표
- SQL Injection이 실제로 어떻게 동작하는지 체험
- 취약한 코드 → 공격 성공 → 패치 → 차단 확인의 사이클 한 번 돌리기
- Prepared Statement가 왜 안전한지 *코드 단위에서* 이해

> ⚠ 본 실습은 `vulnerable_app/`(본인이 띄운 로컬 앱) 또는 DVWA에서만.

---

## 1. SQL Injection 빠른 개념

**개발자의 의도**:
```java
"SELECT * FROM users WHERE username = '" + username + "'"
```
사용자가 `alice`를 넣으면:
```sql
SELECT * FROM users WHERE username = 'alice'
```

**공격자의 입력** `' OR '1'='1`:
```sql
SELECT * FROM users WHERE username = '' OR '1'='1'
```
→ 항상 참 → 모든 사용자 반환 → 로그인 우회.

**핵심**: 데이터(`alice`)와 코드(`SELECT ... WHERE ...`) 가 같은 문자열에 섞이면 데이터가 코드로 해석될 수 있다.

---

## 2. `vulnerable_app`의 취약 로그인 분석

`vulnerable_app/src/main/java/com/example/vuln/controller/LoginController.java` (의도적으로 취약):

```java
@PostMapping("/vuln/login")
public String vulnLogin(@RequestParam String username,
                        @RequestParam String password,
                        Model model) {
    String sql = "SELECT * FROM users WHERE username = '" + username
               + "' AND password = '" + password + "'";
    // ❌ 문자열 concat — SQL Injection 취약
    List<User> users = jdbcTemplate.query(sql, new UserRowMapper());
    if (!users.isEmpty()) {
        return "redirect:/dashboard";
    }
    model.addAttribute("error", "로그인 실패");
    return "login";
}
```

## 3. 공격 단계 (Burp Repeater 사용)

### 3.1 정상 로그인 시도
- 브라우저 → http://localhost:8080/vuln/login
- `username: anything`, `password: anything`
- 결과: "로그인 실패"

### 3.2 인증 우회 #1 — Tautology
- `username: admin' --`
- `password: anything`

생성되는 SQL:
```sql
SELECT * FROM users WHERE username = 'admin' --' AND password = 'anything'
```
`--` 이후는 주석. 비밀번호 검증 무력화 → admin 로그인.

### 3.3 인증 우회 #2 — 어떤 사용자도 모를 때
- `username: ' OR '1'='1' --`
- 결과: 첫 번째 사용자(보통 admin) 반환

### 3.4 UNION 기반 정보 추출
별도 검색 폼(`/vuln/search`)에서:
- `q: ' UNION SELECT username, password, NULL FROM users --`
- → 모든 사용자 정보 화면에 표시

### 3.5 Blind SQLi (응답 차이로 추론)
응답이 같아 보일 때 Boolean-based 사용:
- `q: ' AND SUBSTRING(database(), 1, 1)='a' --` → 응답 길이/내용으로 참/거짓 판단
- 자동화: `sqlmap`
  ```bash
  sqlmap -u "http://localhost:8080/vuln/search?q=test" --batch --dbs
  # 본인 로컬 앱에만!
  ```

---

## 4. 방어 — Prepared Statement

```java
@PostMapping("/safe/login")
public String safeLogin(@RequestParam String username,
                        @RequestParam String password,
                        Model model) {
    String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
    List<User> users = jdbcTemplate.query(sql,
        new Object[]{username, password},   // 바인딩
        new UserRowMapper());
    // ✅ 안전: 파라미터는 데이터로만 취급됨
    if (!users.isEmpty()) {
        return "redirect:/dashboard";
    }
    model.addAttribute("error", "로그인 실패");
    return "login";
}
```

### 왜 안전한가?
Prepared Statement는 SQL 문법 트리를 **컴파일 시점에 결정**한다. `?` 자리에 들어오는 값은 무조건 **데이터(상수 노드)** 로 해석되어 절대 SQL 키워드가 되지 못한다. `'`나 `--`가 들어와도 그냥 그 문자열 그대로의 값.

### 비밀번호 비교는 절대 평문으로
위 예제는 평문 비교라 또 다른 취약점. 실전:
```java
// 1. 사용자 조회
User user = userRepo.findByUsername(username);  // bind 변수 사용
// 2. BCrypt 비교
if (user != null && passwordEncoder.matches(password, user.getPasswordHash())) {
    // 로그인 성공
}
```

---

## 5. ORM은 안전한가?

**대체로 안전, 하지만 함정 있음.**

### Spring Data JPA — 메서드 이름 쿼리
```java
// ✅ 안전 (메서드명 → JPQL 자동 생성, 바인딩)
User findByUsername(String username);
```

### `@Query` JPQL — 바인딩 사용 시 안전
```java
// ✅ 안전 (named parameter)
@Query("SELECT u FROM User u WHERE u.username = :name")
User find(@Param("name") String name);
```

### Native Query — 문자열 concat 시 위험
```java
// ❌ 위험
@Query(value = "SELECT * FROM users WHERE username = '" + #{[0]} + "'", nativeQuery = true)
// 또는 EntityManager로 직접 concat
String sql = "SELECT * FROM users WHERE username = '" + username + "'";
em.createNativeQuery(sql).getResultList();
```

### MyBatis (한국 SI 환경에서 매우 흔함)
```xml
<!-- ✅ 안전: #{} 는 prepared statement 바인딩 -->
<select id="findUser">
  SELECT * FROM users WHERE username = #{username}
</select>

<!-- ❌ 위험: ${} 는 문자열 치환 (SQL 문법 자체에 영향) -->
<select id="findUserBad">
  SELECT * FROM users WHERE username = '${username}'
</select>
```

**규칙**: MyBatis는 거의 항상 `#{}` 사용. `${}`는 ORDER BY 컬럼명·테이블명처럼 바인딩 불가한 곳에만 (그리고 **반드시 화이트리스트로 검증**).

---

## 6. 동적 정렬·검색에서의 SQLi (가장 흔한 실수)

```java
// ❌ ORDER BY는 바인딩 불가하여 동적으로 만들어야 함
String sql = "SELECT * FROM products ORDER BY " + sortColumn;
```

공격자가 `sortColumn`을 `(CASE WHEN ... THEN 1 ELSE 0 END)` 등으로 변조 → blind SQLi.

**방어 — 화이트리스트**:
```java
private static final Set<String> ALLOWED_SORT =
    Set.of("name", "price", "created_at");

public List<Product> list(String sortColumn) {
    if (!ALLOWED_SORT.contains(sortColumn)) {
        sortColumn = "created_at";  // 기본값
    }
    String sql = "SELECT * FROM products ORDER BY " + sortColumn;
    return jdbcTemplate.query(sql, new ProductRowMapper());
}
```

Spring Data의 `Sort` 객체도 비슷하게 사용 시 화이트리스트 검증 필요.

---

## 7. DB 권한 분리 (Defense in Depth)

웹 앱이 DDL 권한을 가지면 SQLi 한 번에 `DROP TABLE` 가능. **앱 사용자는 DML만**.

```sql
-- DB2 예시
CREATE USER app_user WITH PASSWORD '...';
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA.TABLE_X TO app_user;
-- DROP, CREATE는 절대 부여 X
-- 가능하면 읽기 전용 사용자(report_user)도 만들어 SELECT만 권한
```

---

## 8. 탐지 — 로깅·모니터링

웹 앱 앞단(WAF)이나 DB에서 다음 패턴 모니터링:
- `' OR `, `UNION SELECT`, `--`, `/*`, `xp_`, `INFORMATION_SCHEMA`, `SLEEP(`, `BENCHMARK(`
- 비정상적으로 긴 입력 (예: 1000자 이상의 파라미터)
- 짧은 시간 내 동일 IP에서 다양한 페이로드

**Spring 차원에서 간단한 입력 길이 검증** (단, 이것만으로는 부족):
```java
@PostMapping("/search")
public String search(@RequestParam @Size(max = 200) String q) {
    // ...
}
```

---

## 9. 실습 체크리스트

- [ ] `vulnerable_app/`의 `/vuln/login`에 `admin' --` 넣어 인증 우회 성공
- [ ] Burp Repeater로 동일 공격 재현
- [ ] sqlmap을 본인 로컬 앱에 돌려 DB 목록 추출
- [ ] `/safe/login` 으로 동일 공격 시도 → 실패 확인
- [ ] 같은 코드를 MyBatis `#{}` 와 `${}` 로 작성해 차이 체험
- [ ] 본인 회사 코드(또는 사이드 프로젝트)에서 문자열 concat SQL 검색
  ```
  grep -r "+.*+" src/ | grep -iE "select|insert|update|delete"
  ```

---

## 정리 — 한 줄 원칙
> **사용자 입력은 절대 SQL 문자열에 concat 하지 않는다. 항상 바인딩 파라미터를 쓴다. 동적 컬럼명은 화이트리스트로 검증한다.**

다음 주(Week 2)에서 NoSQL Injection, LDAP Injection, OS Command Injection도 다룬다.
