# Lab 4 — EXPLAIN 실전 워크스루

같은 쿼리를 양쪽 DB로 EXPLAIN하고 옵티마이저의 결정을 해석.

## 1. 단순 점 조회

```sql
EXPLAIN SELECT * FROM customers WHERE id = 42;
```

**예상**

- MySQL: type=const, rows=1, Extra=NULL
- DB2: IXSCAN on PK + FETCH

해석: PK lookup, 1 IO.

---

## 2. 인덱스 없는 컬럼 조회

```sql
EXPLAIN SELECT * FROM customers WHERE name = 'Alice';
```

**예상**: type=ALL → 풀스캔

```sql
-- 인덱스 추가 후
CREATE INDEX idx_customers_name ON customers(name);
EXPLAIN SELECT * FROM customers WHERE name = 'Alice';
-- type=ref, rows=N
```

---

## 3. JOIN 두 테이블

```sql
EXPLAIN
SELECT c.name, o.id, o.total_amount
  FROM customers c JOIN orders o ON o.customer_id = c.id
 WHERE c.country = 'KR' AND o.status = 'PAID';
```

**관찰 포인트**

- 어느 쪽이 먼저 스캔되나? (driving table)
- JOIN 알고리즘은? (Nested Loop / Hash)
- 조인 키에 인덱스 있나?
- 옵티마이저가 country로 customers 좁힐까, status로 orders 좁힐까?

**튜닝**

```sql
-- customers: (country)
CREATE INDEX idx_customers_country ON customers(country);

-- orders: (customer_id, status)
CREATE INDEX idx_orders_cust_status ON orders(customer_id, status);
```

다시 EXPLAIN.

---

## 4. GROUP BY + ORDER BY

```sql
EXPLAIN
SELECT customer_id, COUNT(*), SUM(total_amount)
  FROM orders
 WHERE status = 'PAID'
 GROUP BY customer_id
 ORDER BY SUM(total_amount) DESC
 LIMIT 10;
```

**관찰**

- Extra에 `Using temporary; Using filesort` 있나?
- 인덱스가 그룹화·정렬을 도울 수 있나?

**튜닝**

```sql
CREATE INDEX idx_paid_cust ON orders(status, customer_id, total_amount);
```

ORDER BY가 SUM이라 인덱스로는 못 풂. filesort 필연.

---

## 5. 서브쿼리

```sql
EXPLAIN
SELECT * FROM customers
 WHERE id IN (SELECT customer_id FROM orders WHERE status = 'PAID');
```

**관찰**

- MySQL 8.0+은 자동으로 semi-join으로 변환
- 5.6/5.7은 DEPENDENT SUBQUERY로 매 행마다 실행 — 위험

**튜닝**: JOIN으로 변환 또는 EXISTS

```sql
EXPLAIN
SELECT DISTINCT c.* FROM customers c
  JOIN orders o ON o.customer_id = c.id AND o.status = 'PAID';
-- vs
EXPLAIN
SELECT c.* FROM customers c
 WHERE EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id AND o.status = 'PAID');
```

---

## 6. 깊은 OFFSET

```sql
EXPLAIN
SELECT * FROM orders ORDER BY created_at DESC OFFSET 10000 ROWS FETCH FIRST 20 ROWS ONLY;
-- MySQL: LIMIT 20 OFFSET 10000
```

**관찰**

- rows 추정이 매우 큼
- ORDER BY 컬럼 인덱스 있어도 OFFSET 10000은 결국 10020행 스캔

**튜닝**: 키 기반 페이지네이션 (Week 1 Day 4)

---

## 7. 함수가 컬럼에

```sql
EXPLAIN SELECT * FROM orders WHERE DATE(created_at) = '2026-05-15';
-- type=ALL

EXPLAIN SELECT * FROM orders
 WHERE created_at >= '2026-05-15' AND created_at < '2026-05-16';
-- type=range, Using index
```

---

## 8. OR 조건

```sql
EXPLAIN SELECT * FROM customers
 WHERE email = 'alice@example.com' OR name = 'Alice';

-- 인덱스 둘 다 있을 때:
-- MySQL: index_merge로 풀 수 있음 (Extra: Using union)
-- 또는 풀스캔 (옵티마이저 판단)
```

```sql
-- UNION ALL로 분리
EXPLAIN
SELECT * FROM customers WHERE email = 'alice@example.com'
UNION ALL
SELECT * FROM customers WHERE name = 'Alice' AND email <> 'alice@example.com';
-- 각각 인덱스 활용
```

---

## 9. NULL 처리

```sql
EXPLAIN SELECT * FROM customers WHERE deleted_at IS NULL;
-- 인덱스 있으면 type=ref (null도 인덱스됨)

EXPLAIN SELECT * FROM customers WHERE deleted_at IS NOT NULL;
-- 보통 분포에 따라 결정
```

---

## 10. 종합: 운영 화면 쿼리

```sql
EXPLAIN
SELECT o.id, o.created_at, o.total_amount, c.name, c.email,
       (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.id) AS item_count
  FROM orders o
  JOIN customers c ON c.id = o.customer_id
 WHERE o.status = 'PAID'
   AND o.created_at >= '2026-05-01'
   AND c.country = 'KR'
 ORDER BY o.created_at DESC
 FETCH FIRST 50 ROWS ONLY;
```

분석 단계:

1. JOIN driving table?
2. 인덱스 활용?
3. 서브쿼리 비용? (N+1 위험)
4. ORDER BY가 인덱스로 풀리나?
5. Covering 가능?

**튜닝 후보**:

```sql
-- orders
CREATE INDEX idx_orders_paid_recent ON orders(status, created_at, customer_id, total_amount);

-- customers
CREATE INDEX idx_customers_country ON customers(country);

-- order_items (FK 인덱스)
CREATE INDEX idx_oi_order ON order_items(order_id);

-- 서브쿼리를 JOIN+GROUP BY로 변경 (선택)
```

---

## EXPLAIN 해석 체크리스트

각 EXPLAIN 결과에 대해 답하기:

- [ ] type/access path는 무엇인가? (ALL이면 위험)
- [ ] 사용된 인덱스는?
- [ ] rows 추정은 실제와 비슷한가? (ANALYZE 후 다시)
- [ ] Extra에 Using temporary 또는 Using filesort 있나?
- [ ] JOIN 순서는 합리적인가?
- [ ] Covering index 활용 가능한가? (Using index)
- [ ] 옵티마이저가 잘못된 인덱스를 선택했나? (IGNORE INDEX로 비교)

---

## 회고

본인의 운영서버 슬로우 쿼리 1~2개를 가져와 같은 절차 수행:

1. EXPLAIN
2. 해석
3. 인덱스 또는 쿼리 재작성
4. EXPLAIN 비교
5. 실측 시간 비교

다음: [`../checklist.md`](../checklist.md)
