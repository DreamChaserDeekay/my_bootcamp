# Lab 2 — SQL 챌린지 (DB2 vs MySQL)

같은 문제를 양쪽 DB로 풀고 차이점을 정리.

## 시나리오

전자상거래 시스템. 다음 스키마(`practice_db/sql/*/schema.sql` 기준).

```
customers(id, name, email, country, created_at)
orders(id, customer_id, total_amount, status, created_at)
order_items(id, order_id, product_id, quantity, unit_price)
products(id, name, category, price, created_at)
```

샘플 데이터: 고객 100명, 주문 500건, 주문항목 1500건, 상품 50개.

---

## Q1 — "이번 달 매출 톱 10 상품"

```sql
-- 양쪽 표준
SELECT p.id, p.name, p.category,
       SUM(oi.quantity * oi.unit_price) AS revenue,
       SUM(oi.quantity) AS sold_qty
  FROM products p
  JOIN order_items oi ON oi.product_id = p.id
  JOIN orders o ON o.id = oi.order_id
 WHERE o.status = 'PAID'
   AND o.created_at >= '2026-05-01'
   AND o.created_at <  '2026-06-01'
 GROUP BY p.id, p.name, p.category
 ORDER BY revenue DESC
 FETCH FIRST 10 ROWS ONLY;       -- MySQL은 LIMIT 10도 가능
```

---

## Q2 — "한 번도 주문 안 한 고객"

```sql
-- 표준 (둘 다)
SELECT c.id, c.name, c.email
  FROM customers c
 WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id);
```

---

## Q3 — "고객별 첫 주문 vs 최근 주문"

```sql
-- 표준 (윈도우 함수, 둘 다 OK)
SELECT customer_id,
       MIN(created_at) AS first_order_at,
       MAX(created_at) AS last_order_at,
       COUNT(*) AS order_count
  FROM orders
 WHERE status = 'PAID'
 GROUP BY customer_id;
```

---

## Q4 — "각 카테고리별 가장 비싼 상품 3개" (윈도우 함수)

```sql
WITH ranked AS (
    SELECT p.*, ROW_NUMBER() OVER (PARTITION BY category ORDER BY price DESC) AS rn
      FROM products p
)
SELECT id, name, category, price FROM ranked WHERE rn <= 3
 ORDER BY category, rn;
```

---

## Q5 — "지난 30일 일별 매출 + 직전 7일 이동 평균"

```sql
-- DB2
WITH daily AS (
    SELECT DATE(created_at) AS d, SUM(total_amount) AS sales
      FROM orders WHERE status = 'PAID'
       AND created_at >= CURRENT DATE - 30 DAYS
     GROUP BY DATE(created_at)
)
SELECT d, sales,
       AVG(sales) OVER (ORDER BY d ROWS BETWEEN 6 PRECEDING AND CURRENT ROW) AS ma7
  FROM daily ORDER BY d;

-- MySQL — DATE(created_at) 부분만 변경 가능
WITH daily AS (
    SELECT DATE(created_at) AS d, SUM(total_amount) AS sales
      FROM orders WHERE status = 'PAID'
       AND created_at >= CURDATE() - INTERVAL 30 DAY
     GROUP BY DATE(created_at)
)
SELECT d, sales,
       AVG(sales) OVER (ORDER BY d ROWS BETWEEN 6 PRECEDING AND CURRENT ROW) AS ma7
  FROM daily ORDER BY d;
```

---

## Q6 — "결제 실패 후 24시간 내 재시도 안 한 고객"

```sql
-- 표준 (anti-join with self)
SELECT DISTINCT failed.customer_id, c.email
  FROM orders failed
  JOIN customers c ON c.id = failed.customer_id
 WHERE failed.status = 'FAILED'
   AND NOT EXISTS (
       SELECT 1 FROM orders retry
        WHERE retry.customer_id = failed.customer_id
          AND retry.status = 'PAID'
          AND retry.created_at > failed.created_at
          AND retry.created_at < failed.created_at + INTERVAL '1' DAY  -- DB2 표준
          -- MySQL: + INTERVAL 1 DAY
       );
```

---

## Q7 — "월별 매출 + ROLLUP으로 전체 합계"

```sql
-- DB2 (표준 ROLLUP)
SELECT
    COALESCE(CAST(YEAR(created_at)  AS VARCHAR(4)), 'TOTAL') AS yr,
    COALESCE(CAST(MONTH(created_at) AS VARCHAR(2)), 'TOTAL') AS mo,
    SUM(total_amount) AS sales
  FROM orders WHERE status = 'PAID'
 GROUP BY ROLLUP (YEAR(created_at), MONTH(created_at))
 ORDER BY yr, mo;

-- MySQL (WITH ROLLUP)
SELECT
    IFNULL(YEAR(created_at), 'TOTAL') AS yr,
    IFNULL(MONTH(created_at), 'TOTAL') AS mo,
    SUM(total_amount) AS sales
  FROM orders WHERE status = 'PAID'
 GROUP BY YEAR(created_at), MONTH(created_at) WITH ROLLUP;
```

---

## Q8 — "재구매율 (LTR 분석)"

```sql
-- 첫 주문에서 7일 이내 두 번째 주문이 있는 고객 비율
WITH first_orders AS (
    SELECT customer_id, MIN(created_at) AS first_at
      FROM orders WHERE status = 'PAID'
     GROUP BY customer_id
),
repeated AS (
    SELECT fo.customer_id
      FROM first_orders fo
     WHERE EXISTS (
           SELECT 1 FROM orders o
            WHERE o.customer_id = fo.customer_id
              AND o.status = 'PAID'
              AND o.created_at > fo.first_at
              AND o.created_at <= fo.first_at + INTERVAL '7' DAY
           )
)
SELECT
    (SELECT COUNT(*) FROM repeated) * 100.0 /
    (SELECT COUNT(*) FROM first_orders) AS repurchase_rate_pct;
```

---

## Q9 — "주문 항목 펼치기 + 누적 금액"

```sql
SELECT o.id, c.name, oi.product_id, p.name AS product, oi.quantity, oi.unit_price,
       oi.quantity * oi.unit_price AS line_total,
       SUM(oi.quantity * oi.unit_price) OVER (PARTITION BY o.id) AS order_total,
       SUM(oi.quantity * oi.unit_price) OVER (PARTITION BY o.id ORDER BY oi.id) AS running_total
  FROM orders o
  JOIN customers c ON c.id = o.customer_id
  JOIN order_items oi ON oi.order_id = o.id
  JOIN products p ON p.id = oi.product_id
 WHERE o.id = 1
 ORDER BY oi.id;
```

---

## Q10 — "데이터 정합성 검사"

```sql
-- 1) order_items의 합과 orders.total_amount가 일치?
SELECT o.id, o.total_amount,
       SUM(oi.quantity * oi.unit_price) AS items_total,
       o.total_amount - SUM(oi.quantity * oi.unit_price) AS diff
  FROM orders o
  JOIN order_items oi ON oi.order_id = o.id
 GROUP BY o.id, o.total_amount
HAVING ABS(o.total_amount - SUM(oi.quantity * oi.unit_price)) > 0.01;

-- 2) FK 위반: 고객이 사라진 주문 (FK 있으면 0이어야 정상)
SELECT o.id FROM orders o
 WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.id = o.customer_id);

-- 3) 음수·0 가격 상품
SELECT * FROM products WHERE price <= 0;

-- 4) 중복 이메일
SELECT email, COUNT(*) FROM customers GROUP BY email HAVING COUNT(*) > 1;
```

---

## 채점 가이드 (자가)

| 기준 | 점수 |
|---|---|
| 10문제 중 양쪽 DB로 모두 정답 | /50 |
| MySQL과 DB2의 방언 차이를 표로 정리 | /20 |
| 윈도우 함수 / CTE / EXISTS를 각 1회 이상 활용 | /15 |
| 인덱스 활용을 의식한 작성 (Day 5 함정 회피) | /15 |
| **합계** | /100 |

70점 이상이면 Week 2로.

---

## 회고

- 같은 문제를 두 DB로 푸는 동안 어느 쪽이 더 직관적이었는가?
- 윈도우 함수를 안 쓰고 같은 결과를 만들 수 있는가? (서브쿼리 + JOIN)
- 본인 회사 환경에서 가장 쓸 만한 패턴 3가지를 메모.

다음: [`../checklist.md`](../checklist.md)
