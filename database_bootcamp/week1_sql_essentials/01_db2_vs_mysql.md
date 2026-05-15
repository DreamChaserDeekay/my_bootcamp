# Day 1 — DB2 vs MySQL 큰 그림

## 한 줄 요약

DB2는 **IBM이 만든 엔터프라이즈 RDBMS** (1983~)로 금융권·메인프레임 표준이고, MySQL은 **오픈소스 RDBMS** (1995~, 현재 Oracle 소유)로 웹·스타트업 표준이다. 둘 다 ANSI SQL을 따르지만 **옵티마이저·잠금·운영 도구·내장 함수**에서 다르다. 이 차이를 미리 매핑해두면 두 시스템을 동시에 다룰 때 혼란이 없다.

## 학습 목표

- [ ] DB2와 MySQL의 역사·라이선스·아키텍처 큰 그림을 안다
- [ ] **스토리지 엔진**: DB2(통합) vs MySQL(InnoDB/MyISAM/...) 차이를 안다
- [ ] 데이터 타입·기본 함수의 매핑표를 본다
- [ ] 양쪽에 같은 스키마를 만들고 데이터 한 줄 넣어본다
- [ ] 둘 다에 통하는 ANSI SQL과 방언을 구별한다

---

## 1. 큰 그림 비교

| 항목 | IBM DB2 | MySQL |
|---|---|---|
| 출시 | 1983 (System R 후계) | 1995 |
| 제조사 | IBM | Oracle (구 Sun, 원래 MySQL AB) |
| 라이선스 | 상용 (Community 무료판 있음) | GPLv2 + 상용. **MariaDB**는 fork |
| 주 사용처 | 은행·증권·보험·통신·메인프레임 | 웹·SaaS·스타트업·블로그 (WordPress 등) |
| 플랫폼 | Linux/UNIX/Windows + zOS(메인프레임) | Linux/UNIX/Windows |
| 스토리지 엔진 | 통합 (선택 X) | **InnoDB**(기본), MyISAM, MEMORY, ARCHIVE 등 |
| MVCC | 있음 (currently committed) | 있음 (InnoDB) |
| 트랜잭션 기본 격리수준 | **CS (Cursor Stability)** ≈ Read Committed | **Repeatable Read** |
| 기본 포트 | 50000 | 3306 |
| 식별자 인용 | `"name"` (큰따옴표) | \`name\` (백틱) — DB2도 모드 설정 시 가능 |
| 문자열 리터럴 | `'문자열'` (작은따옴표만) | `'문자열'` 또는 `"문자열"` |
| 대소문자 (기본) | 식별자 대문자 자동 변환 | 테이블명: OS 따라감, 컬럼명: 무시 |
| **AUTO INCREMENT** | `GENERATED ALWAYS AS IDENTITY` | `AUTO_INCREMENT` |
| 현재 시각 | `CURRENT TIMESTAMP` (공백 주의) | `NOW()`, `CURRENT_TIMESTAMP` |
| 페이징 | `OFFSET ... FETCH FIRST n ROWS` | `LIMIT m OFFSET n` |
| 시퀀스 | `CREATE SEQUENCE` (표준) | 8.0+ 부분 지원, 보통 AUTO_INCREMENT |
| CTE (WITH) | 지원 (recursive 포함) | 8.0+ 지원 |
| 윈도우 함수 | 9.7+ | 8.0+ |
| JSON 타입 | 9.7+ (`BLOB` 기반), 11.5+ JSON path | 5.7+ 네이티브 |
| 운영 명령 | `db2`, `db2pd`, `db2top`, `db2expln` | `mysql`, `mysqladmin`, `mysqldump`, `EXPLAIN` |

### 라이선스 주의

- **DB2 Community Edition**은 16GB 메모리·4 cores 무료. 운영 상용 환경은 라이선스 필요.
- **MySQL Community**는 GPL. **MySQL Enterprise**는 상용. Oracle 인수 후 일부 사용자는 **MariaDB** 또는 **Percona Server** 사용.

---

## 2. 스토리지 엔진 — 가장 큰 구조적 차이

### DB2

- **통합 엔진**. 사용자는 엔진 선택권 없음. 모든 테이블이 동일 엔진 위에서 동작.
- 데이터·인덱스가 **테이블스페이스(Table Space)** 에 저장
- 버퍼풀(Buffer Pool) 관리, 페이지 크기(4K/8K/16K/32K) 테이블스페이스별로 선택

### MySQL

- **플러그인 아키텍처**. 테이블마다 엔진 선택.

| 엔진 | 특징 | 언제 |
|---|---|---|
| **InnoDB** | 트랜잭션·MVCC·외래키·crash recovery. **기본** | 거의 모든 경우 ✅ |
| MyISAM | 트랜잭션 X, 빠른 read, table lock | legacy. 신규 ❌ |
| MEMORY | RAM에만. 빠르나 휘발 | 임시 테이블, 캐시 |
| ARCHIVE | 압축, append-only | 로그 보관 |
| CSV | CSV 파일과 매핑 | 데이터 교환 |

> **2026년 시점 결론**: MySQL 신규 테이블은 **무조건 InnoDB**. MyISAM은 만나면 마이그레이션 검토.

```sql
-- MySQL: 엔진 확인
SHOW TABLE STATUS LIKE 'orders';

-- 변경 (대형 테이블에서는 비용 큼)
ALTER TABLE orders ENGINE=InnoDB;
```

---

## 3. 자주 쓰는 데이터 타입 매핑

| 의미 | DB2 | MySQL |
|---|---|---|
| 작은 정수 | `SMALLINT` | `SMALLINT` |
| 정수 (4B) | `INTEGER` | `INT` |
| 큰 정수 (8B) | `BIGINT` | `BIGINT` |
| 십진수 (정밀) | `DECIMAL(p,s)` | `DECIMAL(p,s)` |
| 실수 | `DOUBLE` | `DOUBLE` |
| 고정 문자열 | `CHAR(n)` (공백 패딩) | `CHAR(n)` |
| 가변 문자열 | `VARCHAR(n)` | `VARCHAR(n)` |
| 큰 문자열 | `CLOB`, `VARCHAR(32K)` | `TEXT`, `MEDIUMTEXT`, `LONGTEXT` |
| 큰 바이너리 | `BLOB` | `BLOB`, `MEDIUMBLOB`, `LONGBLOB` |
| 날짜 | `DATE` | `DATE` |
| 시각 | `TIME` | `TIME` |
| 날짜+시각 | `TIMESTAMP` (마이크로초까지) | `DATETIME` 또는 `TIMESTAMP` |
| 불리언 | `BOOLEAN` (11.1+) | `BOOLEAN` (실제는 TINYINT(1)) |
| JSON | `JSON` (11.5+) 또는 `BLOB`+검사제약 | `JSON` (5.7+) |
| 자동증가 | `GENERATED ALWAYS AS IDENTITY` | `AUTO_INCREMENT` |
| UUID | `CHAR(36)` 권장 | `BINARY(16)` 또는 `CHAR(36)` (8.0+ `UUID_TO_BIN`) |

> ⚠ **MySQL `TIMESTAMP` vs `DATETIME`**: TIMESTAMP는 1970~2038 범위 + 자동 변환(서버 timezone), DATETIME은 변환 없음. 금융에서는 **DATETIME 권장** + 항상 UTC 저장.

---

## 4. 첫 스키마 — 양쪽에 만들기

### DB2

```sql
-- DB2: orders, customers
CREATE TABLE customers (
    id          INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_customers_email ON customers(email);

CREATE TABLE orders (
    id            BIGINT       NOT NULL GENERATED ALWAYS AS IDENTITY,
    customer_id   INTEGER      NOT NULL,
    total_amount  DECIMAL(12,2) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_created  ON orders(created_at);
```

### MySQL

```sql
-- MySQL: orders, customers
CREATE TABLE customers (
    id          INT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY ux_customers_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE orders (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    customer_id   INT          NOT NULL,
    total_amount  DECIMAL(12,2) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_orders_customer (customer_id),
    KEY idx_orders_created  (created_at),
    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### 핵심 차이

| 항목 | DB2 | MySQL |
|---|---|---|
| 자동증가 | `GENERATED ALWAYS AS IDENTITY` | `AUTO_INCREMENT` |
| 인덱스 정의 | `CREATE INDEX` 별도 | `CREATE TABLE` 안 또는 별도 |
| 엔진/charset | (자동) | `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` |
| Unique | `UNIQUE INDEX` | `UNIQUE KEY` |

> ⚠ **MySQL은 `utf8`이 아니라 `utf8mb4` 사용**. `utf8`은 historical 3-byte UTF-8 = 이모지·일부 한자 깨짐. 항상 `utf8mb4`.

---

## 5. 같은 작업, 양쪽 비교

### "현재 시각"

```sql
-- DB2
SELECT CURRENT TIMESTAMP FROM SYSIBM.SYSDUMMY1;

-- MySQL
SELECT NOW();
SELECT CURRENT_TIMESTAMP;
```

> DB2는 `FROM` 절이 **필수**. 더미 테이블 `SYSIBM.SYSDUMMY1` 사용. MySQL은 `FROM` 생략 가능.

### "최근 5명의 고객"

```sql
-- 표준 (둘 다)
SELECT id, name FROM customers
ORDER BY created_at DESC
FETCH FIRST 5 ROWS ONLY;

-- DB2: 위 표준 OK
-- MySQL: LIMIT 더 흔함
SELECT id, name FROM customers
ORDER BY created_at DESC
LIMIT 5;
```

### "ID 1, 2, 3 모두 존재"

```sql
-- 둘 다 표준
SELECT id FROM customers WHERE id IN (1, 2, 3);
```

### "이메일에 'gmail' 포함"

```sql
-- 둘 다 표준
SELECT * FROM customers WHERE email LIKE '%gmail%';

-- 대소문자 무시 (방언 차이)
-- DB2: UPPER/LOWER 명시
SELECT * FROM customers WHERE LOWER(email) LIKE '%gmail%';

-- MySQL: 기본 collation이 case-insensitive (ci)
SELECT * FROM customers WHERE email LIKE '%GMAIL%';     -- 매치됨
```

### "오늘 가입한 고객 수"

```sql
-- DB2
SELECT COUNT(*) FROM customers
 WHERE DATE(created_at) = CURRENT DATE;

-- MySQL
SELECT COUNT(*) FROM customers
 WHERE DATE(created_at) = CURDATE();

-- ✅ 더 좋은 패턴 (인덱스 활용)
-- DB2
SELECT COUNT(*) FROM customers
 WHERE created_at >= CURRENT DATE
   AND created_at <  CURRENT DATE + 1 DAY;
-- MySQL
SELECT COUNT(*) FROM customers
 WHERE created_at >= CURDATE()
   AND created_at <  CURDATE() + INTERVAL 1 DAY;
```

> ⚠ **함수를 컬럼에 씌우면 인덱스를 못 탄다.** Week 2에서 자세히.

---

## 6. 자주 쓰는 내장 함수 매핑

| 의미 | DB2 | MySQL |
|---|---|---|
| 길이 | `LENGTH(s)` (byte), `CHARACTER_LENGTH` | `LENGTH` (byte), `CHAR_LENGTH` |
| 대/소문자 | `UPPER`/`LOWER` | `UPPER`/`LOWER` |
| 좌·우 공백 제거 | `TRIM`, `LTRIM`, `RTRIM` | `TRIM`, `LTRIM`, `RTRIM` |
| 부분 문자열 | `SUBSTR(s,start,len)` | `SUBSTRING(s,start,len)` |
| 위치 찾기 | `LOCATE(sub, s)` | `LOCATE(sub, s)` / `INSTR` |
| 치환 | `REPLACE(s, from, to)` | `REPLACE` |
| 연결 | `\|\|` 또는 `CONCAT` | `CONCAT(a,b)` (`\|\|`는 OR로 해석되니 주의) |
| NULL 대체 | `COALESCE`, `IFNULL`, `NVL`(11.1+) | `COALESCE`, `IFNULL` |
| 날짜 더하기 | `dt + 7 DAYS` | `dt + INTERVAL 7 DAY` |
| 날짜 차이 | `DAYS(a) - DAYS(b)` | `DATEDIFF(a, b)` |
| 시간 추출 | `YEAR(dt)`, `MONTH(dt)`, `HOUR(dt)` | 동일 |
| 포맷 | `VARCHAR_FORMAT(dt, 'YYYY-MM-DD')` | `DATE_FORMAT(dt, '%Y-%m-%d')` |
| 정수 캐스팅 | `CAST(x AS INTEGER)` | `CAST(x AS SIGNED)` |
| 라운드 | `ROUND(n, 2)` | `ROUND(n, 2)` |

### 함수의 ❌ / ✅

```sql
-- ❌ DB2 — 연결 연산자가 || 인데 OR로 오해 가능
SELECT 'A' || 'B' FROM SYSIBM.SYSDUMMY1;     -- 'AB'

-- ❌ MySQL — 위와 같이 쓰면 OR이 됨 (PIPES_AS_CONCAT 미설정 시)
SELECT 'A' || 'B';     -- 0 (FALSE OR FALSE)

-- ✅ 양쪽 통용
SELECT CONCAT('A', 'B');
```

---

## 7. ❌ 위험 / ✅ 안전 — 첫 운영 함정

### 함정 1: 문자집합 (charset)

```sql
-- ❌ MySQL에서 utf8 (3-byte) 사용 → 이모지 ⚠ 깨짐
CREATE TABLE msg (body TEXT) CHARSET=utf8;
INSERT INTO msg VALUES ('Hello 😀');   -- Error 1366

-- ✅ utf8mb4
CREATE TABLE msg (body TEXT) CHARSET=utf8mb4;
```

### 함정 2: 타임존

```sql
-- MySQL: TIMESTAMP는 서버 timezone에 따라 변환됨
SET time_zone = '+00:00';
SELECT NOW();    -- UTC

SET time_zone = '+09:00';
SELECT NOW();    -- KST

-- 같은 ts 컬럼이 세션마다 다르게 보일 수 있음.
-- ✅ 일반적으로 DATETIME 사용 + 항상 UTC 저장 + 표시 시 변환
```

### 함정 3: AUTO_INCREMENT의 갭

- INSERT 실패해도 `AUTO_INCREMENT` 값은 소비됨 → 비연속
- 트랜잭션 롤백돼도 소비됨
- → **연속 ID 기대 금지**. 일련번호가 필요하면 별도 시퀀스 테이블 또는 비즈니스 키 생성

```sql
-- ❌ "1, 2, 3 순서대로 나오겠지"
-- ✅ ID는 unique·monotonic만 보장, 갭 없음 보장 X
```

### 함정 4: SELECT *

```sql
-- ❌ 운영에서 SELECT * — 컬럼 추가/삭제 시 깨짐, 불필요한 IO
SELECT * FROM orders WHERE id = 1;

-- ✅ 필요한 컬럼만
SELECT id, customer_id, total_amount, status FROM orders WHERE id = 1;
```

---

## 8. 실습 (Hands-on)

### Step 1: 양쪽 컨테이너 띄우기

```bash
docker compose -f practice_db/docker-compose.yml up -d
docker ps
```

### Step 2: 양쪽 접속

```bash
# DB2
docker exec -it db2-lab su - db2inst1 -c "db2 connect to labdb"
# 프롬프트가 db2 => 으로 바뀜

# MySQL
docker exec -it mysql-lab mysql -uroot -ppassw0rd labdb
```

### Step 3: 스키마 적용

```bash
# DB2
docker exec -i db2-lab su - db2inst1 -c "db2 connect to labdb && db2 -tvf -" < practice_db/sql/db2/schema.sql

# MySQL
docker exec -i mysql-lab mysql -uroot -ppassw0rd labdb < practice_db/sql/mysql/schema.sql
```

### Step 4: "안녕 세계" 쿼리

```sql
-- 양쪽에 동일 데이터 삽입
INSERT INTO customers (name, email) VALUES ('Alice', 'alice@example.com');

-- DB2
SELECT id, name, email, created_at FROM customers;

-- MySQL
SELECT id, name, email, created_at FROM customers;
```

### Step 5: 차이 체감

```sql
-- DB2
SELECT CURRENT TIMESTAMP FROM SYSIBM.SYSDUMMY1;
SELECT 'Hello' || ' ' || 'DB2' FROM SYSIBM.SYSDUMMY1;

-- MySQL
SELECT NOW();
SELECT CONCAT('Hello', ' ', 'MySQL');
```

각자 다른 점을 적어두자.

---

## 더 읽어볼 자료

- 📘 『SQL Performance Explained』 (Markus Winand, 무료 일부): <https://use-the-index-luke.com/>
- 🔗 IBM DB2 v11.5 Knowledge Center: <https://www.ibm.com/docs/en/db2/11.5>
- 🔗 MySQL 8.4 Reference Manual: <https://dev.mysql.com/doc/refman/8.4/en/>
- 🔗 PostgreSQL Wiki 'Don't Do This': <https://wiki.postgresql.org/wiki/Don%27t_Do_This> — 다른 DB 안티패턴도 통용

---

## 자가 점검

- [ ] DB2와 MySQL의 출시 배경과 주 사용처를 안다
- [ ] MySQL의 스토리지 엔진 종류와 신규 테이블에 InnoDB를 쓰는 이유를 안다
- [ ] DB2의 `IDENTITY`와 MySQL의 `AUTO_INCREMENT`의 차이를 안다
- [ ] `utf8` vs `utf8mb4`의 차이를 안다
- [ ] `||`이 DB2와 MySQL에서 다르게 해석됨을 안다
- [ ] 양쪽 컨테이너에 동일 스키마를 적용하고 `SELECT 1`이 동작한다

다음: [`02_join_subquery_cte.md`](02_join_subquery_cte.md)
