# Day 5 — SQL 함정 · 안티패턴 10선

## 한 줄 요약

10년 차도 빠지는 SQL 함정 10가지. 외워두면 코드 리뷰의 70%가 자동화된다.

## 학습 목표

- [ ] NULL 비교의 3가지 함정
- [ ] 함수를 컬럼에 씌우면 안 되는 이유 (인덱스 무효화)
- [ ] 암시적 형변환의 위험
- [ ] DISTINCT 남용의 신호
- [ ] 부동소수점으로 돈 계산 금지
- [ ] 트랜잭션 격리수준에 따른 OFFSET 페이징의 비일관성
- [ ] LIKE 와일드카드 위치
- [ ] DELETE/UPDATE without WHERE
- [ ] ORDER BY 없는 LIMIT의 비결정성
- [ ] UNION vs UNION ALL

---

## 함정 1: NULL 비교

```sql
-- ❌ NULL이 NULL이 아니라고?
SELECT * FROM users WHERE deleted_at = NULL;     -- 0 행
SELECT * FROM users WHERE deleted_at <> NULL;    -- 0 행

-- ✅
SELECT * FROM users WHERE deleted_at IS NULL;
SELECT * FROM users WHERE deleted_at IS NOT NULL;
```

### 3가 논리 (Three-valued logic)

`NULL`은 "알 수 없음(unknown)". 비교 결과도 `UNKNOWN` → WHERE에서는 거짓 취급.

```sql
SELECT NULL = NULL;          -- UNKNOWN (NULL)
SELECT NULL <> NULL;         -- UNKNOWN
SELECT NULL = 1;             -- UNKNOWN
SELECT NULL OR TRUE;         -- TRUE
SELECT NULL AND FALSE;       -- FALSE
SELECT NULL AND TRUE;        -- UNKNOWN
```

### NOT IN의 함정 (다시)

```sql
-- ❌ orders에 customer_id=NULL이 하나라도 있으면 결과 빔
SELECT * FROM customers
 WHERE id NOT IN (SELECT customer_id FROM orders);

-- ✅
SELECT c.* FROM customers c
 WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id);
```

### COALESCE / IFNULL

```sql
-- 표준
SELECT COALESCE(nickname, name, '익명') FROM users;

-- MySQL
SELECT IFNULL(nickname, '익명') FROM users;

-- DB2 (옛 Oracle 호환)
SELECT NVL(nickname, '익명') FROM users;
```

---

## 함정 2: 함수를 컬럼에 씌우면 인덱스 무효화

```sql
-- ❌ created_at에 인덱스가 있어도 FUNCTION이 씌워지면 풀스캔
SELECT * FROM orders WHERE YEAR(created_at) = 2026;
SELECT * FROM orders WHERE DATE(created_at) = '2026-05-15';
SELECT * FROM users  WHERE UPPER(email) = 'ALICE@EXAMPLE.COM';

-- ✅ 범위로 변환
SELECT * FROM orders
 WHERE created_at >= '2026-01-01'
   AND created_at <  '2027-01-01';

SELECT * FROM orders
 WHERE created_at >= '2026-05-15 00:00:00'
   AND created_at <  '2026-05-16 00:00:00';

-- ✅ 또는 함수 인덱스(둘 다 지원, 표현 다름)
-- DB2
CREATE INDEX idx_email_upper ON users (UPPER(email));
-- MySQL 8+
CREATE INDEX idx_email_upper ON users ((UPPER(email)));
```

### 산술 연산도 동일

```sql
-- ❌
WHERE price * 1.1 > 1000

-- ✅
WHERE price > 1000 / 1.1
```

---

## 함정 3: 암시적 형변환

```sql
-- 컬럼 phone이 VARCHAR(20)일 때
-- ❌ 숫자 비교 → 좌변 컬럼이 INT로 캐스팅됨 → 인덱스 무효화 + 결과 이상
SELECT * FROM users WHERE phone = 01012345678;

-- ✅
SELECT * FROM users WHERE phone = '01012345678';
```

### MySQL의 무서운 캐스팅

```sql
-- MySQL: 문자열을 숫자로 캐스팅하면 앞의 숫자만 사용
SELECT '123abc' = 123;     -- TRUE (!)
SELECT 'abc' = 0;          -- TRUE (!)

-- 그래서
SELECT * FROM users WHERE name = 0;    -- name이 문자면 name='abc'→0=0 이라 매치되는 결과 다수
```

### Java/Spring 함정

```java
// ❌ Long을 문자열 컬럼에 넣음
String sql = "SELECT * FROM users WHERE user_no = " + userNo;
// → JdbcTemplate가 자동 캐스팅하면서 인덱스 무효

// ✅ 바인딩 + 타입 일치
jdbc.queryForList("SELECT * FROM users WHERE user_no = ?", new Object[] { userNo });
// 컬럼 타입에 맞춰 binding (CHAR/VARCHAR면 String, INT면 Integer/Long)
```

---

## 함정 4: DISTINCT 남용은 JOIN 잘못된 신호

```sql
-- ❌ JOIN 잘못 짜서 중복 → DISTINCT로 가림
SELECT DISTINCT c.id, c.name
  FROM customers c
  JOIN orders o ON o.customer_id = c.id;

-- ✅ EXISTS로 의도를 정확히
SELECT c.id, c.name
  FROM customers c
 WHERE EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id);
```

> DISTINCT는 비용 (정렬·해시). 진짜로 중복 제거가 필요할 때만.

---

## 함정 5: 돈 = 부동소수점 ❌

```sql
-- ❌ FLOAT/DOUBLE로 금액
CREATE TABLE payments (amount DOUBLE);
INSERT INTO payments VALUES (0.1), (0.2);
SELECT SUM(amount) FROM payments;
-- 0.30000000000000004    ← 반올림 오차

-- ✅ DECIMAL
CREATE TABLE payments (amount DECIMAL(15, 2));
SELECT SUM(amount) FROM payments;
-- 0.30                    ← 정확
```

### Java 매핑

```java
// ❌
double amount;

// ✅
BigDecimal amount;

// JPA
@Column(precision = 15, scale = 2)
private BigDecimal amount;
```

> 금융권에서는 **양보 불가**. PCI DSS·전자금융감독규정에서도 정합성 필수.

---

## 함정 6: 트랜잭션 격리수준과 페이지네이션

격리수준이 READ COMMITTED일 때, 같은 데이터를 페이지별로 읽는 동안 다른 트랜잭션이 INSERT/DELETE하면:

```
페이지 1: A, B, C (커밋된 INSERT가 들어옴)
페이지 2: B, C, D  ← B, C 중복! D는 본래 A 위치였던 행

OFFSET 0  LIMIT 3 → A, B, C
                    이때 다른 트랜잭션이 A 앞에 새 행 X를 추가
OFFSET 3  LIMIT 3 → C, D, E  ← C 중복!
```

### 해법

- **REPEATABLE READ** + 같은 트랜잭션 안에서 페이징
- 또는 **키 기반** 페이지네이션 (offset 안 씀)
- 또는 스냅샷 isolation

> Week 3에서 자세히.

---

## 함정 7: LIKE의 와일드카드 위치

```sql
-- ❌ 좌측 와일드카드 → 인덱스 무효화
WHERE name LIKE '%suffix';

-- ✅ 우측만 와일드카드 → 인덱스 사용 가능
WHERE name LIKE 'prefix%';
```

### 좌측 와일드카드가 꼭 필요할 때

- **풀텍스트 인덱스** (DB2: TEXT SEARCH, MySQL: FULLTEXT)
- 또는 **역순 컬럼** 인덱스 (CREATE INDEX ON t (REVERSE(name)))
- 또는 검색엔진 (Elasticsearch)

```sql
-- MySQL FULLTEXT
ALTER TABLE articles ADD FULLTEXT INDEX (body);
SELECT * FROM articles WHERE MATCH(body) AGAINST('spring boot');
```

---

## 함정 8: WHERE 없는 UPDATE/DELETE — 사고의 단골

```sql
-- ❌ 운영 콘솔에서 실수
UPDATE users SET status = 'INACTIVE';     -- 모든 사용자가...
DELETE FROM orders;                        -- 모든 주문 삭제

-- ✅ 안전망
-- 1) BEGIN; ROLLBACK 가능 환경
BEGIN;
UPDATE users SET status = 'INACTIVE' WHERE last_login < '2025-01-01';
SELECT COUNT(*) FROM users WHERE status = 'INACTIVE';   -- 검증
-- 정상이면 COMMIT, 아니면 ROLLBACK
COMMIT;

-- 2) SELECT 먼저 — 영향받을 행을 확인
SELECT COUNT(*) FROM users WHERE last_login < '2025-01-01';
-- 100 만 정상이면
UPDATE users SET status = 'INACTIVE' WHERE last_login < '2025-01-01';

-- 3) MySQL: --safe-updates 모드
-- ~/.my.cnf [client] safe-updates = 1
-- → 키 또는 LIMIT 없는 UPDATE/DELETE 거부
```

### Spring 운영서

```java
// ❌ Repository에 위험한 메서드 둠
@Modifying
@Query("DELETE FROM Order")
void deleteAll();

// ✅ 명확한 조건
@Modifying
@Query("DELETE FROM Order o WHERE o.createdAt < :cutoff")
int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
```

---

## 함정 9: ORDER BY 없는 LIMIT — 비결정적

```sql
-- ❌ 매번 같은 결과 보장 X
SELECT * FROM orders LIMIT 10;

-- ✅
SELECT * FROM orders ORDER BY id LIMIT 10;
```

> MySQL InnoDB는 보통 clustered index 순서로 반환되지만 **보장 아님**. JOIN/병렬/옵티마이저 변경으로 달라질 수 있음.

### "최신 10개"

```sql
-- ❌ created_at 동점이면 같은 row가 다음에 다르게 나옴
SELECT * FROM orders ORDER BY created_at DESC LIMIT 10;

-- ✅ 항상 unique한 보조 키
SELECT * FROM orders ORDER BY created_at DESC, id DESC LIMIT 10;
```

---

## 함정 10: UNION vs UNION ALL

| | UNION | UNION ALL |
|---|---|---|
| 중복 제거 | ⭕ (DISTINCT 적용) | ❌ |
| 비용 | 정렬·해시 | 단순 결합 |
| 의미 | 합집합 | concatenation |

```sql
-- ❌ 의도와 다른 비용
SELECT * FROM table_2024 UNION SELECT * FROM table_2025;
-- 정렬·중복 제거 후 반환 — 큰 테이블에서 매우 비쌈

-- ✅ 중복 없다면
SELECT * FROM table_2024 UNION ALL SELECT * FROM table_2025;
```

> **기본 선택은 UNION ALL**. 중복 제거가 진짜 필요할 때만 UNION.

---

## 보너스 — 자주 보는 5가지

### 1) `SELECT *`

```sql
-- ❌
SELECT * FROM huge_table;     -- 컬럼 추가 시 깨짐, IO·메모리 낭비, 인덱스 only scan 불가

-- ✅
SELECT id, name, email FROM huge_table;
```

### 2) IN 리스트가 너무 큼

```sql
-- ❌ 10,000개 ID
WHERE id IN (1, 2, 3, ..., 10000)
-- → 옵티마이저가 어려워함, 파싱 비용

-- ✅ 임시 테이블 또는 JOIN
INSERT INTO #tmp_ids ...
SELECT u.* FROM users u JOIN #tmp_ids t ON t.id = u.id;
```

### 3) 같은 서브쿼리 반복

```sql
-- ❌ 같은 서브쿼리를 3번
SELECT *, (SELECT COUNT(*) FROM orders o WHERE o.customer_id = c.id) AS cnt
  FROM customers c
 WHERE (SELECT COUNT(*) FROM orders o WHERE o.customer_id = c.id) > 0;

-- ✅ CTE 또는 JOIN
WITH cnt AS (SELECT customer_id, COUNT(*) AS n FROM orders GROUP BY customer_id)
SELECT c.*, cnt.n FROM customers c JOIN cnt ON cnt.customer_id = c.id WHERE cnt.n > 0;
```

### 4) 인덱스 안 타는 OR

```sql
-- ❌ A와 B에 각각 인덱스 있어도 OR이면 풀스캔 가능
WHERE email = 'x@y.com' OR phone = '010-...';

-- ✅ UNION ALL로 (각각 인덱스 활용)
SELECT * FROM users WHERE email = 'x@y.com'
UNION ALL
SELECT * FROM users WHERE phone = '010-...' AND email <> 'x@y.com';
```

### 5) 시간 비교에 BETWEEN

```sql
-- ❌ BETWEEN은 inclusive
WHERE created_at BETWEEN '2026-05-01' AND '2026-05-31';
-- 5월 31일 00:00:00 까지만 (5/31 데이터 누락)

-- ✅
WHERE created_at >= '2026-05-01'
  AND created_at <  '2026-06-01';
```

---

## 실습 — 안티패턴 찾기

다음 쿼리들의 문제를 찾고 안전한 패턴으로 고쳐라.

### 문제 1

```sql
SELECT * FROM orders
 WHERE customer_id NOT IN (SELECT id FROM customers WHERE country = 'KR');
```

→ `country='KR'`이거나 country가 NULL인 행이 customers에 있으면 결과 망가짐. → `NOT EXISTS`.

### 문제 2

```sql
SELECT * FROM orders
 WHERE YEAR(created_at) = 2026 AND MONTH(created_at) = 5;
```

→ 함수 → 인덱스 무효 → 범위로.

### 문제 3

```sql
SELECT customer_id, name, MAX(total_amount)
  FROM orders o JOIN customers c ON c.id = o.customer_id
 GROUP BY customer_id;
```

→ MySQL 5.6 비표준. `c.name`이 GROUP BY에 없음.

### 문제 4

```sql
DELETE FROM logs WHERE created_at < '2026-01-01';
```

→ 한 번에 수백만 행이면 락 길고 트랜잭션 로그 폭주. 청크로 나눠야:

```sql
-- 청크 삭제 (MySQL)
DELETE FROM logs WHERE created_at < '2026-01-01' LIMIT 10000;
-- 반복 (스크립트로)

-- 또는 파티셔닝 + DROP PARTITION
```

### 문제 5

```sql
SELECT DISTINCT c.* FROM customers c
  JOIN orders o ON o.customer_id = c.id
 WHERE o.total_amount > 100;
```

→ DISTINCT 남용. EXISTS로.

---

## 더 읽어볼 자료

- 📘 『SQL Antipatterns』 (Bill Karwin) — 본 챕터의 확장판
- 🔗 Use The Index, Luke: <https://use-the-index-luke.com/>
- 🔗 MySQL 8 SQL modes: <https://dev.mysql.com/doc/refman/8.4/en/sql-mode.html>

---

## 자가 점검

- [ ] `NULL = NULL`이 NULL인 이유를 설명한다
- [ ] `NOT IN`을 피하고 `NOT EXISTS`를 쓴다
- [ ] 함수가 컬럼에 씌워질 때 어떻게 범위로 변환할지 즉시 생각한다
- [ ] DECIMAL을 돈에 쓰는 이유를 안다
- [ ] WHERE 없는 UPDATE/DELETE를 절대 직접 실행 안 한다
- [ ] UNION ALL이 기본임을 안다

이번 주 마무리:

- [`labs/lab1_environment_setup.md`](labs/lab1_environment_setup.md) (이미 했다면 패스)
- [`labs/lab2_sql_challenge.md`](labs/lab2_sql_challenge.md)
- [`checklist.md`](checklist.md)
