# Day 3 — 윈도우 함수 (Window Functions)

## 한 줄 요약

GROUP BY는 **여러 행을 한 행으로 압축**하지만, 윈도우 함수는 **각 행을 유지하면서 주변 행의 집계 결과를 같이 보여준다**. "이 고객의 이번 주문이 그의 전체 주문 중 몇 번째인가", "같은 부서 사람과의 급여 차이", "지난 7일 누적 매출" 같은 질문에 직접 답할 수 있게 한다.

## 학습 목표

- [ ] `OVER (PARTITION BY ... ORDER BY ...)` 구문을 이해한다
- [ ] 순위 함수: `ROW_NUMBER`, `RANK`, `DENSE_RANK`, `NTILE`
- [ ] 이동 함수: `LAG`, `LEAD`, `FIRST_VALUE`, `LAST_VALUE`
- [ ] 누적 집계: `SUM/AVG/COUNT OVER (... ROWS BETWEEN ...)`
- [ ] **프레임(Frame)** 의 의미: `ROWS` vs `RANGE`
- [ ] DB2와 MySQL 양쪽에서 동일하게 동작함을 확인 (DB2 9.7+, MySQL 8.0+)

---

## 1. GROUP BY vs 윈도우 — 결정적 차이

### 같은 데이터

```
orders
─────────────────────────────────────────
id  customer_id  total_amount  created_at
─────────────────────────────────────────
1     1             100.00       2026-01-01
2     1             150.50       2026-01-05
3     2              80.00       2026-01-02
4     1              30.00       2026-01-10
5     2              90.00       2026-01-15
```

### GROUP BY — 행이 줄어듦

```sql
SELECT customer_id, SUM(total_amount) AS total
  FROM orders
 GROUP BY customer_id;

-- 결과: 2 행
-- 1    280.50
-- 2    170.00
```

### 윈도우 함수 — 행이 유지됨

```sql
SELECT id, customer_id, total_amount,
       SUM(total_amount) OVER (PARTITION BY customer_id) AS customer_total
  FROM orders;

-- 결과: 5 행 (모두 유지) + customer_total 컬럼
-- 1   1   100.00   280.50
-- 2   1   150.50   280.50
-- 3   2    80.00   170.00
-- 4   1    30.00   280.50
-- 5   2    90.00   170.00
```

> 💡 윈도우 함수는 "**원래 행을 잃지 않고, 각 행에 집계 결과를 덧붙인다**".

---

## 2. OVER 절의 구성

```sql
<함수>(...) OVER (
    [PARTITION BY 컬럼들]    -- 그룹화 (선택)
    [ORDER BY 컬럼들]        -- 정렬 (이동·누적 함수에 필수)
    [<프레임 정의>]          -- 프레임 (선택, ORDER BY 있을 때 기본 RANGE UNBOUNDED PRECEDING)
)
```

- **PARTITION BY 없으면**: 전체가 한 윈도우
- **ORDER BY 없으면**: 순서 없음 (순위 함수는 비의미)

---

## 3. 순위 함수

| 함수 | 동점 처리 | 다음 순위 |
|---|---|---|
| `ROW_NUMBER()` | 동점이어도 무조건 1, 2, 3, ... | 연속 |
| `RANK()` | 동점은 같은 순위 | 다음은 건너뜀 (1,1,3) |
| `DENSE_RANK()` | 동점은 같은 순위 | 다음은 +1 (1,1,2) |
| `NTILE(n)` | n개 버킷으로 분할 | 1~n |

```sql
SELECT name, score,
       ROW_NUMBER() OVER (ORDER BY score DESC) AS rn,
       RANK()       OVER (ORDER BY score DESC) AS rk,
       DENSE_RANK() OVER (ORDER BY score DESC) AS drk
  FROM exam;

-- name   score  rn  rk  drk
-- Alice   95    1   1   1
-- Bob     95    2   1   1
-- Carol   90    3   3   2
-- Dan     85    4   4   3
```

### "각 부서별 톱 3" — 윈도우의 진수

```sql
-- CTE + ROW_NUMBER
WITH ranked AS (
    SELECT e.*,
           ROW_NUMBER() OVER (PARTITION BY dept_id
                              ORDER BY salary DESC) AS rn
      FROM employees e
)
SELECT * FROM ranked WHERE rn <= 3;
```

> 옛날에는 상관 서브쿼리로 짰던 것. 윈도우 함수로 짧고 빠르게.

---

## 4. 이동 함수 — LAG / LEAD

이전·다음 행의 값을 가져옴.

```sql
-- 각 주문의 이전 주문 금액 (같은 고객 기준)
SELECT id, customer_id, total_amount, created_at,
       LAG(total_amount)  OVER (PARTITION BY customer_id ORDER BY created_at) AS prev_amt,
       LEAD(total_amount) OVER (PARTITION BY customer_id ORDER BY created_at) AS next_amt
  FROM orders;
```

### 응용: "이전 주문과의 간격"

```sql
SELECT id, customer_id, created_at,
       LAG(created_at) OVER (PARTITION BY customer_id ORDER BY created_at) AS prev_at,
       -- 일 단위 차이
       (CAST(created_at AS DATE) -
        CAST(LAG(created_at) OVER (PARTITION BY customer_id ORDER BY created_at) AS DATE))
           AS days_since_prev
  FROM orders;

-- MySQL은 DATEDIFF
SELECT id, customer_id, created_at,
       DATEDIFF(created_at,
                LAG(created_at) OVER (PARTITION BY customer_id ORDER BY created_at)
       ) AS days_since_prev
  FROM orders;
```

### `LAG(컬럼, n, default)`

```sql
LAG(total_amount, 1, 0) OVER (...)   -- 1 행 이전, 없으면 0
LAG(total_amount, 2)    OVER (...)   -- 2 행 이전
```

---

## 5. FIRST_VALUE / LAST_VALUE

윈도우 안에서 첫·마지막 값.

```sql
SELECT id, customer_id, total_amount,
       FIRST_VALUE(total_amount) OVER (PARTITION BY customer_id ORDER BY created_at) AS first_order_amt,
       LAST_VALUE (total_amount) OVER (PARTITION BY customer_id ORDER BY created_at
                                       ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS last_order_amt
  FROM orders;
```

> ⚠ **LAST_VALUE의 함정**: 기본 프레임이 "처음~현재 행"이라서 LAST가 "지금까지의 마지막" = 현재 행이 됨. 진짜 끝까지 보려면 **ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING** 명시.

---

## 6. 누적·이동 평균 — 프레임의 본격 활용

### 누적 합계 (running total)

```sql
-- 각 고객의 주문을 시간순으로, 누적 결제액
SELECT id, customer_id, total_amount, created_at,
       SUM(total_amount) OVER (PARTITION BY customer_id
                               ORDER BY created_at
                               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
           AS running_total
  FROM orders;
```

### 이동 평균 (moving average)

```sql
-- 직전 6일(현재 포함 7일) 이동 평균 매출
SELECT order_date, daily_sales,
       AVG(daily_sales) OVER (ORDER BY order_date
                              ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)
           AS ma7
  FROM daily_sales_summary;
```

### 프레임 옵션

```sql
ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW         -- 누적 (시작 ~ 현재)
ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING -- 전체
ROWS BETWEEN 6 PRECEDING AND CURRENT ROW                 -- 직전 6 + 현재 = 7개
ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING                 -- 앞뒤 1개씩 = 3개
RANGE BETWEEN INTERVAL '7' DAY PRECEDING AND CURRENT ROW -- (DB2/표준) 시간 범위
```

### ROWS vs RANGE

| | ROWS | RANGE |
|---|---|---|
| 단위 | 행 개수 | ORDER BY 값의 범위 |
| 예 | "이전 5행" | "지난 7일" |
| 동점 처리 | 무관 | 같은 ORDER BY 값은 같은 윈도우 |

```sql
-- ROWS: 이전 5행
ROWS BETWEEN 5 PRECEDING AND CURRENT ROW

-- RANGE: 같은 날짜의 모든 행 + 그 이전 5일 안의 모든 행
RANGE BETWEEN INTERVAL '5' DAY PRECEDING AND CURRENT ROW
```

> ⚠ MySQL 8.x는 `RANGE BETWEEN INTERVAL ... PRECEDING`을 일부 버전에서 제한적으로 지원. 8.0.2+ 부터 RANGE numeric/temporal 모두 OK.

---

## 7. 윈도우 함수 활용 패턴 (북마크 가치)

### "지난 N건 평균과의 비교"

```sql
SELECT id, customer_id, total_amount,
       AVG(total_amount) OVER (PARTITION BY customer_id
                               ORDER BY created_at
                               ROWS BETWEEN 5 PRECEDING AND 1 PRECEDING) AS prev5_avg,
       total_amount - AVG(total_amount) OVER (...) AS diff_from_avg
  FROM orders;
```

### "각 그룹에서 N등까지"

```sql
WITH ranked AS (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY group_id ORDER BY score DESC) rn
      FROM scores
)
SELECT * FROM ranked WHERE rn <= 3;
```

### "중복 제거 + 가장 최신만 유지"

```sql
-- email별 가장 최신 row만
WITH ranked AS (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY email ORDER BY updated_at DESC) rn
      FROM users
)
SELECT * FROM ranked WHERE rn = 1;
```

### "동점은 같은 등수, 다음은 건너뛰지 않음" → DENSE_RANK

### "전체에서 상위 10%만"

```sql
WITH ntiled AS (
    SELECT *, NTILE(10) OVER (ORDER BY score DESC) AS decile FROM scores
)
SELECT * FROM ntiled WHERE decile = 1;
```

### "각 행에 전체 합계 같이"

```sql
SELECT id, amount, SUM(amount) OVER () AS grand_total,
       amount * 100.0 / SUM(amount) OVER () AS pct
  FROM revenue;
```

### "퍼센타일"

```sql
-- DB2/MySQL 8+
PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY salary) OVER (PARTITION BY dept_id)
```

---

## 8. ❌ / ✅

### LAST_VALUE의 함정

```sql
-- ❌ 보통 직관과 다른 결과
SELECT id, total_amount,
       LAST_VALUE(total_amount) OVER (PARTITION BY customer_id ORDER BY created_at) AS last_amt
  FROM orders;
-- 각 행마다 "그 행까지의 마지막"이라 = 자기자신

-- ✅
LAST_VALUE(total_amount) OVER (PARTITION BY customer_id ORDER BY created_at
                                ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
```

### 윈도우 함수는 WHERE에 못 씀

```sql
-- ❌
SELECT *, ROW_NUMBER() OVER (...) AS rn FROM orders WHERE rn = 1;
-- "rn" unknown column / not found

-- ✅ 서브쿼리/CTE로 한 단계 감싸기
WITH ranked AS (SELECT *, ROW_NUMBER() OVER (...) AS rn FROM orders)
SELECT * FROM ranked WHERE rn = 1;
```

이유: 평가 순서가 WHERE → GROUP BY → HAVING → 윈도우 → ORDER BY → LIMIT.

### "성능 이슈"

윈도우 함수는 정렬을 동반. 큰 테이블에서는 ORDER BY 컬럼에 인덱스 필요.

```sql
-- (PARTITION BY customer_id ORDER BY created_at)에 좋은 인덱스:
CREATE INDEX idx_orders_cust_created ON orders(customer_id, created_at);
```

> Week 2 인덱스에서 자세히.

---

## 9. 실제 사례

### 사례 1: 부정 결제 탐지 — "1분 안에 같은 카드로 N건 이상"

```sql
WITH timestamped AS (
    SELECT id, card_id, amount, created_at,
           LAG(created_at, 4) OVER (PARTITION BY card_id ORDER BY created_at) AS five_ago
      FROM payments
)
SELECT *
  FROM timestamped
 WHERE five_ago IS NOT NULL
   AND (CAST(created_at AS TIMESTAMP) - CAST(five_ago AS TIMESTAMP)) < INTERVAL '1' MINUTE;
```

### 사례 2: 콜드 스타트 분석 — "고객의 첫 7일 동안의 주문 수"

```sql
WITH first_order AS (
    SELECT customer_id, MIN(created_at) AS first_at
      FROM orders
     GROUP BY customer_id
)
SELECT o.customer_id, COUNT(*) AS orders_in_first_week
  FROM orders o
  JOIN first_order f ON f.customer_id = o.customer_id
 WHERE o.created_at < f.first_at + INTERVAL '7' DAY
 GROUP BY o.customer_id;

-- 또는 윈도우 함수
SELECT customer_id, COUNT(*) AS orders_in_first_week FROM (
    SELECT *,
           MIN(created_at) OVER (PARTITION BY customer_id) AS first_at
      FROM orders
) t
WHERE created_at < first_at + INTERVAL '7' DAY
GROUP BY customer_id;
```

### 사례 3: 갭 분석 — "주문 안 한 기간이 30일 이상인 고객"

```sql
WITH gaps AS (
    SELECT customer_id, created_at,
           LAG(created_at) OVER (PARTITION BY customer_id ORDER BY created_at) AS prev_at
      FROM orders
)
SELECT DISTINCT customer_id
  FROM gaps
 WHERE prev_at IS NOT NULL
   AND DATEDIFF(created_at, prev_at) > 30;     -- MySQL
   -- DB2: DAYS(created_at) - DAYS(prev_at) > 30
```

---

## 10. 실습

### Step 1: 샘플 데이터 추가

```sql
-- 주문 더 많이 추가 (실습용)
INSERT INTO orders (customer_id, total_amount, status, created_at) VALUES
    (1, 100.00, 'PAID',      '2026-01-01 10:00:00'),
    (1,  50.00, 'PAID',      '2026-01-03 11:00:00'),
    (1, 200.00, 'PAID',      '2026-01-10 14:00:00'),
    (2,  80.00, 'PAID',      '2026-01-02 09:00:00'),
    (2,  90.00, 'PAID',      '2026-01-15 16:00:00'),
    (2, 120.00, 'CANCELLED', '2026-01-20 18:00:00'),
    (3,  30.00, 'PAID',      '2026-01-05 13:00:00');
```

### Step 2: 챌린지 (양쪽 DB에 모두)

1. 각 고객의 주문을 시간순으로 정렬하고 순번을 매기기
2. 각 고객의 누적 결제액 (시간순)
3. 각 주문이 그 고객의 평균 주문 금액보다 얼마나 큰지/작은지
4. 각 고객의 가장 최근 PAID 주문만 1건씩
5. 일별 매출 합계 + 직전 7일 이동 평균

### Step 3: 인덱스와 함께

```sql
-- 윈도우 함수의 ORDER BY 컬럼에 인덱스
CREATE INDEX idx_orders_cust_created ON orders(customer_id, created_at);

-- EXPLAIN 비교
EXPLAIN
WITH ranked AS (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY created_at DESC) rn
      FROM orders
)
SELECT * FROM ranked WHERE rn = 1;
```

---

## 더 읽어볼 자료

- 📘 『SQL Window Functions for Beginners』 (Cathy Tanimura)
- 🔗 Modern SQL — Window Functions: <https://modern-sql.com/feature/window-functions>
- 🔗 MySQL 8 Window Functions: <https://dev.mysql.com/doc/refman/8.4/en/window-functions.html>
- 🔗 DB2 OLAP specification: <https://www.ibm.com/docs/en/db2/11.5?topic=specifications-olap>
- 🎓 Use The Index, Luke — Order By / Window: <https://use-the-index-luke.com/sql/clustering>

---

## 자가 점검

- [ ] GROUP BY와 윈도우 함수의 결과 행 수 차이를 설명한다
- [ ] ROW_NUMBER, RANK, DENSE_RANK 차이를 동점 예제로 안다
- [ ] LAG/LEAD로 이전/다음 행을 가져온다
- [ ] ROWS와 RANGE 프레임 차이를 안다
- [ ] LAST_VALUE의 함정을 안다 (기본 프레임 문제)
- [ ] 윈도우 함수를 WHERE에 못 쓰는 이유 (평가 순서)

다음: [`04_pagination_aggregation.md`](04_pagination_aggregation.md)
