# SQL 치트시트 — DB2 & MySQL 양쪽

## 1. 데이터 정의 (DDL)

```sql
-- 테이블 생성
-- DB2
CREATE TABLE t (
    id INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT TIMESTAMP,
    PRIMARY KEY (id)
);

-- MySQL
CREATE TABLE t (
    id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 인덱스
CREATE INDEX idx_t_name ON t(name);
CREATE UNIQUE INDEX ux_t_email ON t(email);
CREATE INDEX idx_t_composite ON t(a, b, c);

-- DB2 INCLUDE (Covering)
CREATE INDEX idx_t ON t(a) INCLUDE (b, c);

-- 함수 인덱스
-- DB2
CREATE INDEX idx_lower ON t (LOWER(email));
-- MySQL 8+
CREATE INDEX idx_lower ON t ((LOWER(email)));

-- 외래 키
ALTER TABLE orders ADD CONSTRAINT fk_o_c
    FOREIGN KEY (customer_id) REFERENCES customers(id);

-- 컬럼 추가
ALTER TABLE t ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

-- 인덱스 삭제
DROP INDEX idx_t_name;                    -- DB2
DROP INDEX idx_t_name ON t;               -- MySQL
```

## 2. 조회 (SELECT)

```sql
-- 기본
SELECT id, name FROM customers WHERE country = 'KR' ORDER BY name LIMIT 10;

-- 페이징 (둘 다 표준)
SELECT * FROM orders ORDER BY id OFFSET 100 ROWS FETCH FIRST 20 ROWS ONLY;
-- MySQL 단축
SELECT * FROM orders ORDER BY id LIMIT 20 OFFSET 100;

-- DISTINCT
SELECT DISTINCT country FROM customers;

-- 조건
WHERE col = ?
WHERE col IN (?,?,?)
WHERE col BETWEEN ? AND ?
WHERE col LIKE 'prefix%'
WHERE col IS NULL
WHERE col IS NOT NULL
WHERE EXISTS (SELECT 1 FROM ...)
WHERE NOT EXISTS (...)

-- 결합
SELECT ... FROM a JOIN b ON a.id = b.a_id;
SELECT ... FROM a LEFT JOIN b ON a.id = b.a_id;
SELECT ... FROM a FULL OUTER JOIN b ON a.id = b.a_id;     -- DB2 ⭕, MySQL ❌

-- 그룹화·집계
SELECT customer_id, COUNT(*), SUM(amount)
  FROM orders
 GROUP BY customer_id
HAVING COUNT(*) > 5;

-- ROLLUP
SELECT customer_id, status, SUM(amount) FROM orders
 GROUP BY ROLLUP (customer_id, status);
-- MySQL 옛 문법
GROUP BY customer_id, status WITH ROLLUP;

-- CTE
WITH t AS (
    SELECT ... FROM ...
)
SELECT ... FROM t WHERE ...;

-- 재귀 CTE
WITH RECURSIVE tree (id, parent_id, lvl) AS (
    SELECT id, parent_id, 1 FROM cat WHERE parent_id IS NULL
    UNION ALL
    SELECT c.id, c.parent_id, t.lvl + 1
      FROM cat c JOIN tree t ON c.parent_id = t.id
)
SELECT * FROM tree ORDER BY lvl, id;

-- 윈도우 함수
SELECT id, customer_id,
       ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY created_at DESC) AS rn,
       LAG(amount, 1, 0) OVER (PARTITION BY customer_id ORDER BY created_at) AS prev_amt,
       SUM(amount) OVER (PARTITION BY customer_id
                         ORDER BY created_at
                         ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_sum
  FROM orders;
```

## 3. 변경 (DML)

```sql
INSERT INTO t (a, b) VALUES (?, ?);
INSERT INTO t (a, b) VALUES (?, ?), (?, ?), (?, ?);   -- 다중

-- MySQL: UPSERT
INSERT INTO t (id, value) VALUES (?, ?)
ON DUPLICATE KEY UPDATE value = VALUES(value);
-- 또는 8.0.19+
INSERT INTO t (id, value) VALUES (?, ?) AS new
ON DUPLICATE KEY UPDATE value = new.value;

-- DB2: MERGE
MERGE INTO t AS tgt
USING (VALUES (?, ?)) AS src(id, value) ON tgt.id = src.id
WHEN MATCHED THEN UPDATE SET value = src.value
WHEN NOT MATCHED THEN INSERT (id, value) VALUES (src.id, src.value);

UPDATE t SET status = 'PAID' WHERE id = ?;
UPDATE t SET balance = balance - ?, version = version + 1 WHERE id = ? AND balance >= ?;

DELETE FROM t WHERE created_at < ?;
DELETE FROM t WHERE id IN (SELECT id FROM other_table);

-- TRUNCATE (DDL, 빠름)
TRUNCATE TABLE t;                             -- DB2: REUSE STORAGE 옵션
TRUNCATE TABLE t IMMEDIATE;                   -- DB2 (커밋)
```

## 4. 트랜잭션

```sql
-- 표준
START TRANSACTION;        -- 또는 BEGIN; (MySQL)
-- 작업
COMMIT;
-- 또는
ROLLBACK;

-- Savepoint
SAVEPOINT sp1;
-- 작업
ROLLBACK TO SAVEPOINT sp1;
RELEASE SAVEPOINT sp1;

-- 격리수준
-- 표준
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
-- DB2
SET CURRENT ISOLATION = CS;
-- MySQL
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- 명시적 잠금
SELECT * FROM t WHERE id = ? FOR UPDATE;
SELECT * FROM t WHERE id = ? FOR SHARE;        -- MySQL 8+
SELECT * FROM t WHERE id = ? FOR UPDATE NOWAIT;
SELECT * FROM t FOR UPDATE SKIP LOCKED;        -- 작업 큐 패턴
```

## 5. 데이터 타입 매핑

| 의미 | DB2 | MySQL |
|---|---|---|
| 정수 | INTEGER, BIGINT | INT, BIGINT |
| 십진 | DECIMAL(p,s) | DECIMAL(p,s) |
| 문자열 가변 | VARCHAR(n) | VARCHAR(n) |
| 큰 텍스트 | CLOB | TEXT/MEDIUMTEXT/LONGTEXT |
| 날짜+시간 | TIMESTAMP | DATETIME |
| 자동증가 | GENERATED ALWAYS AS IDENTITY | AUTO_INCREMENT |
| JSON | JSON (11.5+) | JSON (5.7+) |

## 6. 자주 쓰는 함수

| 의미 | DB2 | MySQL |
|---|---|---|
| 길이 (문자) | `CHARACTER_LENGTH(s)` | `CHAR_LENGTH(s)` |
| 부분 문자열 | `SUBSTR(s,start,len)` | `SUBSTRING(s,start,len)` |
| 치환 | `REPLACE(s,from,to)` | `REPLACE(s,from,to)` |
| 연결 | `\|\| ` 또는 `CONCAT` | `CONCAT(...)` |
| 좌·우 공백 | `TRIM`, `LTRIM`, `RTRIM` | 같음 |
| 대·소문자 | `UPPER`, `LOWER` | 같음 |
| 현재 시각 | `CURRENT TIMESTAMP` | `NOW()` |
| 현재 날짜 | `CURRENT DATE` | `CURDATE()` |
| 더하기 | `dt + 7 DAYS` | `dt + INTERVAL 7 DAY` |
| 차이 (일) | `DAYS(a) - DAYS(b)` | `DATEDIFF(a, b)` |
| 추출 | `YEAR(dt)`, `MONTH(dt)` | 같음 |
| 포맷 | `VARCHAR_FORMAT(dt, 'YYYY-MM-DD')` | `DATE_FORMAT(dt, '%Y-%m-%d')` |
| NULL 대체 | `COALESCE` | `COALESCE`, `IFNULL` |
| 라운드 | `ROUND(n, 2)` | `ROUND(n, 2)` |
| 절대값 | `ABS(n)` | `ABS(n)` |
| 모듈로 | `MOD(a, b)` | `a % b`, `MOD(a, b)` |
| 캐스팅 | `CAST(x AS INTEGER)` | `CAST(x AS SIGNED)` |

## 7. 운영 SQL

```sql
-- 시스템 정보
-- MySQL
SHOW VARIABLES LIKE 'innodb_buffer_pool_size';
SHOW STATUS LIKE 'Innodb_buffer_pool_read%';
SHOW ENGINE INNODB STATUS\G

-- DB2
SELECT VALUE FROM SYSIBMADM.DBCFG WHERE NAME = 'locklist';
SELECT * FROM SYSIBMADM.SNAPDB;

-- 통계 갱신
-- MySQL
ANALYZE TABLE t;
ANALYZE TABLE t UPDATE HISTOGRAM ON col WITH 100 BUCKETS;

-- DB2
RUNSTATS ON TABLE schema.t WITH DISTRIBUTION AND DETAILED INDEXES ALL;

-- EXPLAIN
-- MySQL
EXPLAIN SELECT ...;
EXPLAIN ANALYZE SELECT ...;
EXPLAIN FORMAT=JSON SELECT ...;

-- DB2
EXPLAIN PLAN FOR SELECT ...;
-- + db2expln -d labdb -q "..." -t
```

## 8. JSON

```sql
-- MySQL
SELECT data->'$.name' FROM t;
SELECT JSON_EXTRACT(data, '$.name') FROM t;
SELECT JSON_UNQUOTE(data->'$.name') FROM t;       -- 따옴표 제거
UPDATE t SET data = JSON_SET(data, '$.color', 'red');

-- DB2 (11.5+)
SELECT JSON_VALUE(data, '$.name') FROM t;
SELECT JSON_QUERY(data, '$.address') FROM t;
```

## 9. 페이지네이션 패턴

```sql
-- ❌ 깊은 OFFSET (느림)
SELECT * FROM orders ORDER BY id DESC OFFSET 100000 ROWS FETCH FIRST 20 ROWS ONLY;

-- ✅ 키 기반
SELECT * FROM orders
 WHERE id < :last_id_from_prev_page
 ORDER BY id DESC
 FETCH FIRST 20 ROWS ONLY;

-- 복합 키 (created_at 동점 처리)
SELECT * FROM orders
 WHERE (created_at, id) < (:last_created_at, :last_id)
 ORDER BY created_at DESC, id DESC
 FETCH FIRST 20 ROWS ONLY;
```

## 10. 흔한 한 줄

```sql
-- N+1 회피: fetch join (JPA 등) 또는 IN 일괄
SELECT * FROM customers WHERE id IN (?,?,?,?);

-- 톱 N + 그룹별
WITH ranked AS (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY g ORDER BY x DESC) rn FROM t
)
SELECT * FROM ranked WHERE rn <= 3;

-- 중복 제거 + 최신만
WITH ranked AS (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY email ORDER BY updated_at DESC) rn FROM users
)
SELECT * FROM ranked WHERE rn = 1;

-- 안전한 잔액 차감 (lost update 방지)
UPDATE accounts SET balance = balance - ?
 WHERE id = ? AND balance >= ?;
-- 영향 행 수 == 0 이면 잔액 부족

-- 작업 큐 (skip locked)
SELECT * FROM jobs WHERE status = 'PENDING'
 ORDER BY id FETCH FIRST 1 ROWS ONLY FOR UPDATE SKIP LOCKED;
```
