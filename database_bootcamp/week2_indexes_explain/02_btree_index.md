# Day 2 — B-tree 인덱스 동작 원리

## 한 줄 요약

DB 인덱스의 95%는 **B+tree**다. "정렬된 키를 가진 다단 트리"이고, **로그 시간**에 검색·범위 조회가 가능하다. 어떤 쿼리에서 인덱스를 타고 어떤 쿼리에서 못 타는지는 결국 "트리 탐색이 가능한 형태인가"로 결정된다.

## 학습 목표

- [ ] B-tree(B+tree) 구조와 검색·삽입·삭제 동작
- [ ] **선형 탐색**(full scan) vs **이진 탐색**(인덱스)의 비용 차이
- [ ] 인덱스가 도움이 되는 4가지 패턴 (점 조회·범위·정렬·집계)
- [ ] 인덱스가 무력화되는 5가지 패턴
- [ ] **Selectivity** 와 **Cardinality** — 옵티마이저가 인덱스를 거부하는 이유
- [ ] 양쪽 DB에서 인덱스 만들고 EXPLAIN 비교

---

## 1. B+tree 구조

```
                Root
              [50 | 100]
              /    |    \
             /     |     \
         [10|30] [70|90] [120|150]    ← Branch (Internal)
          / | \   / | \   / | \
       leaves with key + (pointer or row data)
       ↔ ↔ ↔ ↔ ↔ ↔ ↔ ↔ ↔ ↔ ↔ ↔
       (leaf끼리 양방향 연결 → 범위 스캔 빠름)
```

### 특징

| 항목 | 설명 |
|---|---|
| 차수 | 한 노드에 여러 키 (보통 100~1000) |
| 깊이 | 1억 행에서 보통 3~4 단계 |
| 검색 | O(log N), 디스크 IO 3~4번 |
| 정렬 | leaf가 정렬되어 있음 → 범위 스캔 효율 |
| 삽입 | leaf 찾고 페이지 분할 가능 |

> 💡 1억 행에서도 **인덱스 검색이 4번의 IO**로 끝난다. 풀스캔은 10만 페이지 = 10만 IO. 차이는 천문학적.

### B-tree vs B+tree

- **B-tree**: 내부 노드에도 데이터
- **B+tree**: 데이터는 leaf에만, 내부는 키만 → leaf끼리 연결 → 범위 스캔에 강함

대부분의 DB가 **B+tree**. (이름은 그냥 B-tree로 부르는 경향)

---

## 2. 풀스캔 vs 인덱스 스캔 비용 비교

### 풀스캔 (Full Table Scan)

```
모든 페이지를 순차 읽음 → O(N)
1억 행 ÷ 100행/페이지 = 100만 페이지
페이지 = 16KB → 16GB 읽기 (메모리 안 들어가면 디스크)
```

### 인덱스 점 조회

```
B+tree 탐색: Root → Branch → Branch → Leaf → 행
IO: 3~4번
시간: 수십 마이크로초
```

### 옵티마이저 결정

- 결과 행 수가 적으면 → **인덱스**
- 결과 행 수가 많으면 (대략 전체의 5~20% 이상) → **풀스캔**
  - 인덱스로 찾고 bookmark lookup하는 비용 > 풀스캔
- 통계가 부정확하면 잘못된 선택 (Day 5에서 자세히)

---

## 3. 인덱스가 도움이 되는 4가지 패턴

### (1) 점 조회 (Equality)

```sql
WHERE id = 42                        -- ⭐ PK는 자동
WHERE email = 'alice@example.com'    -- email에 인덱스 있으면
```

### (2) 범위 조회 (Range)

```sql
WHERE created_at >= '2026-05-01' AND created_at < '2026-06-01'
WHERE price BETWEEN 100 AND 500
WHERE total_amount > 1000
```

### (3) 정렬 (ORDER BY)

```sql
SELECT * FROM orders ORDER BY created_at DESC LIMIT 10;
-- created_at에 인덱스가 있으면 정렬 안 함 (이미 leaf 순서)
```

### (4) 집계 / 그룹화

```sql
SELECT customer_id, COUNT(*) FROM orders GROUP BY customer_id;
-- customer_id에 인덱스가 있으면 sort 없이 그룹화
```

---

## 4. 인덱스가 무력화되는 5가지 패턴

### (1) 컬럼에 함수 / 연산

```sql
-- ❌ created_at 인덱스 무효
WHERE YEAR(created_at) = 2026
WHERE created_at + INTERVAL 1 DAY > NOW()
WHERE UPPER(email) = 'ALICE@EXAMPLE.COM'

-- ✅
WHERE created_at >= '2026-01-01' AND created_at < '2027-01-01'
WHERE email = 'alice@example.com'   (대소문자 처리는 collation 또는 함수 인덱스)
```

### (2) 부정 조건

```sql
-- ❌ 인덱스 약함 (전체에서 큰 비중)
WHERE status <> 'PAID'
WHERE id NOT IN (1, 2, 3)

-- ✅ 가능하면 긍정 조건으로
WHERE status IN ('PENDING', 'CANCELLED', 'FAILED')
```

### (3) LIKE 좌측 와일드카드

```sql
-- ❌
WHERE name LIKE '%suffix'

-- ✅
WHERE name LIKE 'prefix%'
```

### (4) 데이터 타입 불일치 (암시적 형변환)

```sql
-- phone이 VARCHAR
-- ❌ 좌변이 INT로 캐스팅됨
WHERE phone = 1012345678

-- ✅
WHERE phone = '1012345678'
```

### (5) OR — 컬럼이 각각 다를 때

```sql
-- ❌ 둘 다 인덱스 있어도 풀스캔 가능
WHERE email = 'x@y' OR phone = '010-...'

-- ✅ UNION ALL
SELECT * FROM users WHERE email = 'x@y'
UNION ALL
SELECT * FROM users WHERE phone = '010-...' AND email <> 'x@y'
```

---

## 5. Selectivity (선택도)

```
selectivity = 결과 행 수 / 전체 행 수
```

| selectivity | 인덱스 효과 |
|---|---|
| 0.001 (1000행에서 1행) | ⭐⭐⭐ 인덱스 매우 효과적 |
| 0.05 (5%) | ⭐⭐ 인덱스 유리 |
| 0.2 (20%) | ⭐ 비슷 |
| 0.5+ | ❌ 풀스캔이 나음 |

### Cardinality (카디널리티)

```
cardinality = 컬럼의 유니크 값 개수
```

- 높은 cardinality (UNIQUE한 컬럼) → 인덱스 효과적 (예: email, id)
- 낮은 cardinality (boolean, status) → 인덱스 거의 무용
  - 예외: 매우 편향된 분포 (status='ERROR'가 0.1%) → 부분/조건부 인덱스

```sql
-- 컬럼별 카디널리티 확인
-- MySQL
SHOW INDEX FROM orders;
-- Cardinality 컬럼

-- DB2
SELECT COLNAME, COLCARD FROM SYSCAT.COLUMNS WHERE TABNAME='ORDERS';
```

### 옵티마이저 결정

옵티마이저는 통계에서 **추정 selectivity**를 보고 인덱스 vs 풀스캔 결정. 통계가 부정확하면 잘못된 선택.

---

## 6. 인덱스 추가·삭제

### DB2

```sql
-- 단일
CREATE INDEX idx_orders_customer ON orders(customer_id);

-- Unique
CREATE UNIQUE INDEX ux_customers_email ON customers(email);

-- 복합 (Day 3에서 자세히)
CREATE INDEX idx_orders_cust_created ON orders(customer_id, created_at DESC);

-- 옵션: CLUSTER, NOT ENFORCED, INCLUDE
CREATE INDEX ix_x ON t(a) INCLUDE (b, c);   -- b,c를 leaf에 같이 저장 (Covering)

-- 삭제
DROP INDEX idx_orders_customer;
```

### MySQL

```sql
-- 인라인
CREATE TABLE orders (..., KEY idx_orders_customer (customer_id));

-- ALTER
ALTER TABLE orders ADD INDEX idx_orders_customer (customer_id);
CREATE INDEX idx_orders_customer ON orders(customer_id);   -- 동등

-- Unique
CREATE UNIQUE INDEX ux_customers_email ON customers(email);
ALTER TABLE customers ADD UNIQUE KEY ux_customers_email (email);

-- 복합
ALTER TABLE orders ADD INDEX idx_orders_cust_created (customer_id, created_at);

-- 함수 인덱스 (MySQL 8.0+)
ALTER TABLE users ADD INDEX idx_email_upper ((UPPER(email)));

-- 인비저블 인덱스 (실험용, 8.0+)
ALTER TABLE orders ALTER INDEX idx_x INVISIBLE;
ALTER TABLE orders ALTER INDEX idx_x VISIBLE;

-- 삭제
DROP INDEX idx_orders_customer ON orders;
ALTER TABLE orders DROP INDEX idx_orders_customer;
```

### 운영 시 인덱스 추가 주의

- 큰 테이블에서 `CREATE INDEX`는 **테이블 락 + 시간** 필요
- **MySQL 8 Online DDL** / **DB2 ONLINE**으로 비차단
- MySQL `pt-online-schema-change`, `gh-ost` 같은 도구

```sql
-- MySQL Online DDL
ALTER TABLE orders ADD INDEX idx_x (col), ALGORITHM=INPLACE, LOCK=NONE;
```

---

## 7. 인덱스 비용

인덱스는 공짜가 아니다.

| 비용 | 영향 |
|---|---|
| 저장 공간 | 보통 테이블 크기의 10~50% (인덱스 합) |
| INSERT 시 추가 IO | 인덱스 N개 → leaf 페이지 N개 갱신 |
| UPDATE 시 | 변경된 컬럼이 인덱스에 있으면 인덱스 갱신 |
| DELETE 시 | 인덱스에서도 제거 |
| 통계 유지 | RUNSTATS / ANALYZE 시간 |

> 💡 인덱스가 너무 많으면 쓰기가 느려진다. **읽기 95% / 쓰기 5%** 워크로드면 많이 둬도 OK, **쓰기 위주**면 보수적으로.

---

## 8. 사용 안 되는 인덱스 찾기 (정리)

### MySQL

```sql
-- performance_schema 활성화 필요
SELECT object_schema, object_name, index_name
  FROM performance_schema.table_io_waits_summary_by_index_usage
 WHERE index_name IS NOT NULL
   AND count_star = 0
   AND object_schema NOT IN ('mysql','performance_schema','information_schema')
 ORDER BY object_schema, object_name;
```

→ count_star = 0인 인덱스는 한 번도 사용 안 됨. 후보 삭제 대상.

### DB2

```sql
-- 마지막 사용 시각
SELECT TABSCHEMA, TABNAME, INDNAME, LASTUSED
  FROM SYSCAT.INDEXES
 WHERE TABSCHEMA = 'DB2INST1'
 ORDER BY LASTUSED;
-- LASTUSED가 오래 전이면 후보
```

---

## 9. 실습

### Step 1: 인덱스 없이 vs 있고

```sql
-- 큰 테이블 가정 (또는 practice_db에 1만 행 만들어두기)
EXPLAIN SELECT * FROM orders WHERE customer_id = 42;
-- type/scan 확인

-- 인덱스 추가
CREATE INDEX idx_orders_customer ON orders(customer_id);

-- 다시
EXPLAIN SELECT * FROM orders WHERE customer_id = 42;
-- type=ref/eq_ref 로 변함, rows 추정 줄어듦
```

### Step 2: 인덱스 무력화 함정 체험

```sql
-- 인덱스 있는 created_at에서 함수 사용
EXPLAIN SELECT * FROM orders WHERE YEAR(created_at) = 2026;
-- → 풀스캔

-- 범위로 변환
EXPLAIN SELECT * FROM orders
 WHERE created_at >= '2026-01-01' AND created_at < '2027-01-01';
-- → 인덱스 사용
```

### Step 3: 카디널리티 측정

```sql
-- MySQL
SHOW INDEX FROM orders;     -- Cardinality 컬럼 확인

-- 컬럼별 distinct count
SELECT
    COUNT(DISTINCT customer_id) AS card_customer,
    COUNT(DISTINCT status)      AS card_status,
    COUNT(*)                    AS total
  FROM orders;
-- card_status가 매우 낮으면 status 단독 인덱스는 보통 효과 적음
```

### Step 4: invisible index (MySQL 8)

```sql
-- 인덱스 잠시 끄고 영향 측정 (운영 점검 용)
ALTER TABLE orders ALTER INDEX idx_orders_customer INVISIBLE;
EXPLAIN SELECT * FROM orders WHERE customer_id = 42;
-- 풀스캔 발생 확인 → 인덱스가 정말 효과 있었음 증명

ALTER TABLE orders ALTER INDEX idx_orders_customer VISIBLE;
```

### Step 5: 인덱스 비용 측정

```sql
-- 인덱스 N개 추가 후 INSERT 속도 비교
CREATE INDEX idx_a ON orders(...);
CREATE INDEX idx_b ON orders(...);
-- 5개쯤 만들고

-- INSERT 시간 측정
SET timing ON;
INSERT INTO orders SELECT ... FROM ...;
-- 인덱스 N개일 때 vs 0개일 때 비교
```

---

## 더 읽어볼 자료

- 📘 『Use The Index, Luke』 (Markus Winand, 무료): <https://use-the-index-luke.com/> — 최고의 인덱스 학습서
- 📘 『Database Internals』 (Petrov) Ch. 2 (B-tree)
- 🔗 MySQL InnoDB Indexes: <https://dev.mysql.com/doc/refman/8.4/en/innodb-indexes.html>
- 🔗 DB2 Indexes: <https://www.ibm.com/docs/en/db2/11.5?topic=schemas-indexes>

---

## 자가 점검

- [ ] B+tree의 leaf가 정렬되어 있음을 안다 (범위 스캔이 빠른 이유)
- [ ] 1억 행에서 인덱스 검색이 3~4 IO임을 안다
- [ ] selectivity와 cardinality 차이를 안다
- [ ] 인덱스 무력화 5가지 패턴을 즉시 떠올린다
- [ ] 인덱스 비용 (쓰기·저장공간·유지) 을 안다
- [ ] 사용 안 되는 인덱스를 찾는 쿼리를 가지고 있다

다음: [`03_composite_covering.md`](03_composite_covering.md)
