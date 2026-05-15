# Lab 3 — 인덱스 튜닝 챌린지

## 시나리오

전자상거래 운영 DB. "메인 화면이 느려요"라는 제보. 다음 5개 쿼리가 가장 자주 호출된다. **각각에 최적 인덱스를 설계**하고 적용 전후 EXPLAIN을 비교하라.

## 사전 준비

데이터 양 늘리기 (1만~10만 행 권장):

```sql
-- MySQL (재귀 CTE로 대량 생성)
INSERT INTO customers (name, email, country)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n < 10000
)
SELECT CONCAT('User', n), CONCAT('user', n, '@example.com'),
       ELT(1+FLOOR(RAND()*5), 'KR','US','JP','CN','GB')
  FROM seq;

INSERT INTO orders (customer_id, total_amount, status, created_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n < 50000
)
SELECT FLOOR(1+RAND()*10000),
       ROUND(10 + RAND()*1000, 2),
       ELT(1+FLOOR(RAND()*4), 'PAID','PENDING','CANCELLED','FAILED'),
       DATE_ADD('2025-01-01', INTERVAL FLOOR(RAND()*500) DAY)
  FROM seq;
```

DB2는 유사 방법으로 행 추가 (또는 외부 generator 사용).

ANALYZE 잊지 말 것:

```sql
-- MySQL
ANALYZE TABLE customers, orders;

-- DB2
RUNSTATS ON TABLE db2inst1.orders WITH DISTRIBUTION;
RUNSTATS ON TABLE db2inst1.customers WITH DISTRIBUTION;
```

---

## 쿼리 1: 고객별 최근 주문 20개

```sql
SELECT id, status, total_amount, created_at
  FROM orders
 WHERE customer_id = ?
 ORDER BY created_at DESC
 FETCH FIRST 20 ROWS ONLY;
```

**예상 풀이**

1. EXPLAIN 시도
2. 인덱스 없으면 풀스캔
3. 후보 인덱스:
   - `(customer_id)` — 기본
   - `(customer_id, created_at DESC)` — 정렬까지 처리
   - `(customer_id, created_at DESC) INCLUDE (status, total_amount)` — Covering (DB2)
   - `(customer_id, created_at, status, total_amount)` — Covering (MySQL)

```sql
-- DB2
CREATE INDEX idx_orders_my ON orders(customer_id, created_at DESC)
    INCLUDE (status, total_amount);

-- MySQL
CREATE INDEX idx_orders_my ON orders(customer_id, created_at, status, total_amount);
```

검증:
```sql
EXPLAIN SELECT id, status, total_amount, created_at
  FROM orders WHERE customer_id = 42
 ORDER BY created_at DESC LIMIT 20;
-- Extra: Using index (Covering)
-- type: ref
```

---

## 쿼리 2: PAID 주문 전체 매출

```sql
SELECT SUM(total_amount) FROM orders WHERE status = 'PAID';
```

**고민**

- status는 cardinality 낮음
- 그러나 전체 50%가 PAID라면 풀스캔 더 빠를 수 있음
- Covering 인덱스 (status, total_amount)면 인덱스 풀스캔만으로 가능

```sql
CREATE INDEX idx_orders_status_amt ON orders(status, total_amount);
```

→ status='PAID' 영역만 스캔, 테이블 안 봄.

히스토그램 추가:
```sql
ANALYZE TABLE orders UPDATE HISTOGRAM ON status WITH 50 BUCKETS;
```

---

## 쿼리 3: 특정 기간 매출

```sql
SELECT SUM(total_amount) FROM orders
 WHERE status = 'PAID'
   AND created_at >= '2026-01-01' AND created_at < '2026-02-01';
```

**후보**

```sql
-- 옵션 A
CREATE INDEX idx_a ON orders(status, created_at, total_amount);
-- status equality → created_at range → Covering

-- 옵션 B
CREATE INDEX idx_b ON orders(created_at, status, total_amount);
-- created_at range가 첫 컬럼이면 status는 후처리
```

**A가 보통 더 좋음** (equality 먼저).

확인:
```sql
EXPLAIN SELECT SUM(total_amount) FROM orders
 WHERE status = 'PAID'
   AND created_at >= '2026-01-01' AND created_at < '2026-02-01';
```

---

## 쿼리 4: 국가별 최근 7일 매출 톱 5

```sql
SELECT c.country, SUM(o.total_amount) AS rev
  FROM customers c JOIN orders o ON o.customer_id = c.id
 WHERE o.status = 'PAID'
   AND o.created_at >= CURRENT_DATE - INTERVAL '7' DAY
 GROUP BY c.country
 ORDER BY rev DESC
 FETCH FIRST 5 ROWS ONLY;
```

**고민**

- JOIN 양쪽 끝에 좋은 인덱스 필요
- orders: (status, created_at, customer_id, total_amount) — Covering
- customers: PK (id) 사용 가능. country도 필요

```sql
CREATE INDEX idx_orders_paid_recent ON orders(status, created_at, customer_id, total_amount);
-- customers.id는 PK 이미 사용
```

검증:
```sql
EXPLAIN ...;
-- orders: ref/range, Using index
-- customers: eq_ref on PK
```

---

## 쿼리 5: 검색 (이메일 LIKE)

```sql
SELECT id, name, email FROM customers WHERE email LIKE 'alice%';
```

**후보**

- 좌측 와일드카드 X, 우측만 → 인덱스 활용 가능

```sql
CREATE INDEX idx_customers_email ON customers(email);
-- 이미 unique index 있으면 활용됨
```

만약 `LIKE '%alice%'`라면? → 인덱스 불가. **FULLTEXT** (MySQL) 또는 검색 엔진.

```sql
-- MySQL FULLTEXT
ALTER TABLE customers ADD FULLTEXT INDEX ft_email (email);
SELECT * FROM customers WHERE MATCH(email) AGAINST('alice');
```

---

## 보너스: 인덱스 비용 vs 효과 측정

### 단계 1: 인덱스 없을 때

```sql
-- 5 쿼리 각각 실행 시간 측정 (BENCHMARK 또는 stopwatch)

-- 쓰기 (INSERT 1000회 시간)
```

### 단계 2: 위에서 만든 모든 인덱스 추가

```sql
-- 5개 인덱스 모두 추가
```

### 단계 3: 측정

```sql
-- 5 쿼리 각각 다시
-- 쓰기 1000회 시간 측정 — 느려졌나?
```

### 단계 4: 비교

| | 인덱스 X | 인덱스 5개 |
|---|---|---|
| Q1 시간 | | |
| Q2 시간 | | |
| Q3 시간 | | |
| Q4 시간 | | |
| Q5 시간 | | |
| INSERT 1000회 | | |
| 인덱스 총 크기 | | |

**트레이드오프**: 읽기 N배 빨라짐 vs 쓰기 X% 느려짐.

---

## 회고

- 가장 효과 큰 인덱스는?
- 가장 큰 인덱스는?
- "이런 패턴이면 이 인덱스" 자신만의 룰을 3가지 정리.

다음: [`lab4_explain_walkthrough.md`](lab4_explain_walkthrough.md)
