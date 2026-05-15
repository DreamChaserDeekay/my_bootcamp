# Day 2 — JOIN · 서브쿼리 · CTE

## 한 줄 요약

복잡한 리포트 쿼리의 99%는 **JOIN, 서브쿼리, CTE** 세 가지 도구의 조합이다. 단순 INNER JOIN을 넘어 **LEFT/SEMI/ANTI JOIN**, **상관 서브쿼리**, **재귀 CTE**까지 익히면 SQL로 표현 못할 비즈니스 로직은 거의 없어진다.

## 학습 목표

- [ ] JOIN의 5가지 종류(INNER, LEFT/RIGHT, FULL, CROSS, SELF)를 안다
- [ ] **SEMI JOIN** (`EXISTS`)과 **ANTI JOIN** (`NOT EXISTS`)을 안다
- [ ] 서브쿼리 위치별 종류 (스칼라, 인라인 뷰, 상관) 차이
- [ ] CTE (`WITH ... AS`) 작성, 가독성 vs 성능 트레이드오프
- [ ] 재귀 CTE로 계층 구조(조직도·카테고리) 탐색
- [ ] DB2·MySQL 양쪽에서 같은 결과를 얻는다

---

## 1. JOIN 5가지

### 그림으로

```
  A    B
 ┌──┐ ┌──┐
 │  │ │  │
 └──┘ └──┘
```

| JOIN | 결과 |
|---|---|
| **INNER** | A ∩ B (양쪽 매치) |
| **LEFT** (= LEFT OUTER) | A 전체 + 매치되는 B (없으면 NULL) |
| **RIGHT** | LEFT의 좌우 바꿈 (보통 LEFT로 통일) |
| **FULL** (= FULL OUTER) | A ∪ B (한쪽 없으면 NULL) |
| **CROSS** | A × B 카티시안 (모든 조합) |
| **SELF** | 같은 테이블끼리 (별칭으로 구분) |

### 예제 (DB2·MySQL 공통, 표준)

```sql
-- 고객별 주문 수 (주문 없는 고객도 포함)
SELECT c.id, c.name, COUNT(o.id) AS order_count
  FROM customers c
  LEFT JOIN orders o ON o.customer_id = c.id
 GROUP BY c.id, c.name;

-- 주문은 있는데 고객이 없는 데이터(고아 레코드 진단)
SELECT o.id
  FROM orders o
  LEFT JOIN customers c ON c.id = o.customer_id
 WHERE c.id IS NULL;

-- 자기 자신과 JOIN (같은 부서 동료)
SELECT e1.name AS me, e2.name AS coworker
  FROM employees e1
  JOIN employees e2 ON e1.dept_id = e2.dept_id
                   AND e1.id <> e2.id;
```

### USING vs ON

```sql
-- ON: 명시적
SELECT * FROM a JOIN b ON a.id = b.a_id;

-- USING: 양쪽 컬럼명이 같을 때
SELECT * FROM a JOIN b USING (id);

-- ⚠ DB2: USING 지원 (v9+)
-- ⚠ MySQL: 지원
-- 운영 코드에서는 ON 권장 (USING은 출력 컬럼이 합쳐져 혼란 가능)
```

---

## 2. SEMI JOIN / ANTI JOIN — JOIN 대신 EXISTS

### 의미

- **SEMI JOIN**: "B에 매치되는 A만, 그러나 B의 컬럼은 안 가져옴, 중복 X"
- **ANTI JOIN**: "B에 매치되지 않는 A만"

### 표준 패턴: `EXISTS` / `NOT EXISTS`

```sql
-- SEMI: 주문이 있는 고객만
SELECT c.id, c.name
  FROM customers c
 WHERE EXISTS (
       SELECT 1 FROM orders o
        WHERE o.customer_id = c.id
       );

-- ANTI: 주문이 한 번도 없는 고객
SELECT c.id, c.name
  FROM customers c
 WHERE NOT EXISTS (
       SELECT 1 FROM orders o
        WHERE o.customer_id = c.id
       );
```

### JOIN으로 했을 때의 함정

```sql
-- ❌ DISTINCT 안 하면 고객이 주문 수만큼 중복
SELECT c.id, c.name
  FROM customers c
  JOIN orders o ON o.customer_id = c.id;

-- ⚠ DISTINCT는 정렬·집계 비용
SELECT DISTINCT c.id, c.name
  FROM customers c
  JOIN orders o ON o.customer_id = c.id;

-- ✅ EXISTS — 중복 X, 옵티마이저가 이른 종료(early termination)
SELECT c.id, c.name
  FROM customers c
 WHERE EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id);
```

> 💡 **EXISTS는 첫 매치에서 멈춘다**. JOIN+DISTINCT보다 보통 빠름.

### IN vs EXISTS

```sql
-- IN
SELECT * FROM customers
 WHERE id IN (SELECT customer_id FROM orders);

-- EXISTS
SELECT * FROM customers c
 WHERE EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id);
```

| | IN | EXISTS |
|---|---|---|
| NULL 동작 | `IN`은 NULL 만나면 결과 깨짐 (`NULL`이 됨) | 영향 적음 |
| 옵티마이저 | 보통 유사 (둘 다 semi join으로 변환됨) | 보통 유사 |
| 가독성 | 단순할 때 간결 | 복잡한 조건에 유리 |

> 결론: **NOT IN은 거의 항상 피하기**. NULL 한 개라도 있으면 전체가 비어버림. `NOT EXISTS` 또는 `LEFT JOIN + IS NULL` 사용.

```sql
-- ❌ 위험: orders.customer_id에 NULL 하나라도 있으면 결과 빔
SELECT * FROM customers
 WHERE id NOT IN (SELECT customer_id FROM orders);

-- ✅
SELECT * FROM customers c
 WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id);
```

---

## 3. 서브쿼리 — 위치별 3가지

### (1) 스칼라 서브쿼리 — SELECT 절에 한 값

```sql
SELECT
    c.id,
    c.name,
    (SELECT COUNT(*) FROM orders o WHERE o.customer_id = c.id) AS order_count,
    (SELECT MAX(created_at) FROM orders o WHERE o.customer_id = c.id) AS last_order_at
  FROM customers c;
```

> ⚠ 매 행마다 서브쿼리가 실행될 수 있음 → 큰 테이블에선 비쌈. JOIN + GROUP BY로 바꿔보고 둘 다 EXPLAIN.

### (2) 인라인 뷰 — FROM 절의 서브쿼리

```sql
SELECT t.customer_id, t.cnt
  FROM (
       SELECT customer_id, COUNT(*) AS cnt
         FROM orders
        GROUP BY customer_id
       ) t
 WHERE t.cnt > 10;

-- 더 깔끔: CTE
WITH t AS (
    SELECT customer_id, COUNT(*) AS cnt
      FROM orders
     GROUP BY customer_id
)
SELECT customer_id, cnt FROM t WHERE cnt > 10;
```

### (3) 상관 서브쿼리 — 외부 쿼리 참조

```sql
-- 각 부서에서 평균 급여보다 많이 받는 사원
SELECT e.name, e.salary, e.dept_id
  FROM employees e
 WHERE e.salary > (
       SELECT AVG(e2.salary)
         FROM employees e2
        WHERE e2.dept_id = e.dept_id     -- 외부 e를 참조
       );
```

상관 서브쿼리는 행마다 평가됨. 윈도우 함수(`AVG() OVER (PARTITION BY dept_id)`)가 더 빠를 때 많음. Day 3에서 다룸.

---

## 4. CTE — Common Table Expression

`WITH name AS (...)`. 임시 결과를 이름 붙여 재사용.

### 가독성 비교

```sql
-- ❌ 서브쿼리 중첩 (읽기 어려움)
SELECT *
  FROM (
       SELECT customer_id, AVG(total_amount) AS avg_amt
         FROM (
              SELECT customer_id, total_amount
                FROM orders
               WHERE status = 'PAID'
              ) paid
        GROUP BY customer_id
       ) t
 WHERE t.avg_amt > 100;

-- ✅ CTE로 분리
WITH paid_orders AS (
    SELECT customer_id, total_amount
      FROM orders
     WHERE status = 'PAID'
),
avg_per_customer AS (
    SELECT customer_id, AVG(total_amount) AS avg_amt
      FROM paid_orders
     GROUP BY customer_id
)
SELECT *
  FROM avg_per_customer
 WHERE avg_amt > 100;
```

> 💡 CTE는 SQL의 **함수 추출**. 작은 의미 단위로 나누면 읽고·디버깅하기 쉬움.

### 양쪽 지원

- DB2: **9.7+** 지원 (현행 11.x OK)
- MySQL: **8.0+** 지원 (5.7 이하 X — 인라인 뷰로 대체)

### 여러 CTE 체이닝

```sql
WITH
recent_orders AS (
    SELECT * FROM orders WHERE created_at >= CURRENT_DATE - INTERVAL '30' DAY  -- 표준
),
top_customers AS (
    SELECT customer_id, SUM(total_amount) AS spent
      FROM recent_orders
     GROUP BY customer_id
     ORDER BY spent DESC
     FETCH FIRST 10 ROWS ONLY
)
SELECT c.name, tc.spent
  FROM top_customers tc
  JOIN customers c ON c.id = tc.customer_id;
```

### DB2 / MySQL 30일 차이 방언

```sql
-- 표준 (둘 다 지원)
created_at >= CURRENT_DATE - INTERVAL '30' DAY

-- DB2
created_at >= CURRENT DATE - 30 DAYS

-- MySQL
created_at >= CURDATE() - INTERVAL 30 DAY
```

---

## 5. 재귀 CTE — 계층 구조

### 사례: 조직도 (employee → manager_id)

```sql
-- 표준 (둘 다)
WITH RECURSIVE org_chart (id, name, manager_id, level) AS (
    -- Anchor: CEO (manager가 NULL)
    SELECT id, name, manager_id, 1 AS level
      FROM employees
     WHERE manager_id IS NULL

    UNION ALL

    -- Recursive: 부모를 찾은 사원
    SELECT e.id, e.name, e.manager_id, oc.level + 1
      FROM employees e
      JOIN org_chart oc ON e.manager_id = oc.id
)
SELECT level, id, name, manager_id FROM org_chart
 ORDER BY level, id;
```

| 결과 |
|---|
| level=1: CEO |
| level=2: VP, VP |
| level=3: Director, Director, Director |
| ... |

### 차이: `RECURSIVE` 키워드

- DB2: `WITH RECURSIVE` 또는 `WITH` (자동 인식)
- MySQL: `WITH RECURSIVE` **필수**

### 카테고리 트리

```sql
WITH RECURSIVE cat_tree (id, name, parent_id, path) AS (
    SELECT id, name, parent_id, CAST(name AS VARCHAR(1000))
      FROM categories
     WHERE parent_id IS NULL

    UNION ALL

    SELECT c.id, c.name, c.parent_id,
           CAST(ct.path || ' > ' || c.name AS VARCHAR(1000))  -- DB2
           -- MySQL: CONCAT(ct.path, ' > ', c.name)
      FROM categories c
      JOIN cat_tree ct ON c.parent_id = ct.id
)
SELECT id, name, path FROM cat_tree
 ORDER BY path;
```

### 재귀 안전장치

```sql
-- 무한 루프 방지: 최대 깊이 제한
WHERE oc.level < 10
```

```sql
-- MySQL: 시스템 변수
SET SESSION cte_max_recursion_depth = 1000;     -- 기본 1000
```

> ⚠ **무한 재귀** = 무한 루프. 데이터에 순환 참조 있으면 DB가 멈춤. 항상 깊이 제한.

---

## 6. ❌ 안티패턴 / ✅ 권장

### 안티 1: 카르테시안 폭발

```sql
-- ❌ ON 절 빠짐 → CROSS JOIN
SELECT * FROM customers c, orders o;     -- 10만 × 100만 = 1000억 행

-- ✅
SELECT * FROM customers c
  JOIN orders o ON o.customer_id = c.id;
```

> 항상 `JOIN ... ON ...` 명시. 콤마 조인은 옛 문법, 사고 원인.

### 안티 2: WHERE에 OUTER JOIN의 우측 조건

```sql
-- ❌ LEFT JOIN이 INNER JOIN으로 바뀜 (의도와 다름)
SELECT c.*, o.*
  FROM customers c
  LEFT JOIN orders o ON o.customer_id = c.id
 WHERE o.status = 'PAID';     -- 주문 없는 고객 제외됨

-- ✅ 조건을 ON으로
SELECT c.*, o.*
  FROM customers c
  LEFT JOIN orders o ON o.customer_id = c.id
                     AND o.status = 'PAID';
```

### 안티 3: 너무 큰 IN 리스트

```sql
-- ❌ 1000개 ID
WHERE id IN (1, 2, 3, ..., 1000)

-- ✅ 임시 테이블 또는 VALUES
SELECT * FROM customers WHERE id IN (
    SELECT id FROM TABLE(VALUES (1),(2),(3),...) AS t(id)    -- 표준
);
-- 또는 임시 테이블 만들고 JOIN
```

### 안티 4: COUNT(*) vs COUNT(컬럼) vs EXISTS

```sql
-- 단순 존재 확인
-- ❌ 큰 테이블에서 비쌈
SELECT COUNT(*) FROM orders WHERE customer_id = 42;

-- ✅
SELECT 1 FROM orders WHERE customer_id = 42 FETCH FIRST 1 ROWS ONLY;
-- 또는
SELECT EXISTS (SELECT 1 FROM orders WHERE customer_id = 42);    -- MySQL 8+
```

---

## 7. 실제 사례

### 사례: "30일간 주문이 없는 휴면 고객 추출"

```sql
-- ✅ ANTI JOIN
SELECT c.id, c.name, c.email
  FROM customers c
 WHERE NOT EXISTS (
       SELECT 1
         FROM orders o
        WHERE o.customer_id = c.id
          AND o.created_at >= CURRENT_DATE - INTERVAL '30' DAY
       );
```

### 사례: "고객별 누적 결제액 + 최근 주문일"

```sql
-- ✅ JOIN + GROUP BY
SELECT c.id, c.name,
       COALESCE(SUM(o.total_amount), 0) AS total_spent,
       MAX(o.created_at)                AS last_order_at
  FROM customers c
  LEFT JOIN orders o ON o.customer_id = c.id
 GROUP BY c.id, c.name;
```

### 사례: "각 부서에서 가장 비싼 주문 1건"

```sql
-- 윈도우 함수가 정답 (Day 3에서)
-- 일단 CTE + 서브쿼리로
WITH ranked AS (
    SELECT o.*, c.name, c.dept_id,
           ROW_NUMBER() OVER (PARTITION BY c.dept_id ORDER BY o.total_amount DESC) AS rn
      FROM orders o
      JOIN customers c ON c.id = o.customer_id
)
SELECT * FROM ranked WHERE rn = 1;
```

---

## 8. 실습

### Step 1: 샘플 데이터 채우기

```sql
-- 양쪽 DB
INSERT INTO customers (name, email) VALUES
    ('Alice', 'alice@example.com'),
    ('Bob',   'bob@example.com'),
    ('Carol', 'carol@example.com');

INSERT INTO orders (customer_id, total_amount, status) VALUES
    (1, 100.00, 'PAID'),
    (1, 150.50, 'PAID'),
    (1,  30.00, 'CANCELLED'),
    (2,  80.00, 'PAID');
-- (Carol은 주문 없음)
```

### Step 2: 챌린지

다음을 양쪽 DB에 작성·실행:

1. 모든 고객과 그들의 주문 합계 (주문 없는 고객은 0)
2. PAID 상태 주문이 1건 이상 있는 고객
3. 한 번도 주문 안 한 고객
4. 가장 많이 산 고객 1명 (이름·총액)
5. 첫 주문과 마지막 주문의 간격 (일 단위)

### Step 3: 재귀 CTE — 카테고리 트리

```sql
-- 양쪽
CREATE TABLE categories (
    id INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    parent_id INTEGER,
    PRIMARY KEY (id)
);

INSERT INTO categories VALUES
    (1, 'Electronics', NULL),
    (2, 'Computers', 1),
    (3, 'Laptops', 2),
    (4, 'Desktops', 2),
    (5, 'Phones', 1),
    (6, 'Smartphones', 5);

-- 챌린지: 'Electronics' 의 모든 하위 카테고리를 path와 함께
```

---

## 더 읽어볼 자료

- 📘 『SQL Cookbook』 (Anthony Molinaro) — 패턴 모음
- 🔗 Modern SQL — <https://modern-sql.com/> — CTE·윈도우 함수 비교
- 🔗 MySQL 8 CTE: <https://dev.mysql.com/doc/refman/8.4/en/with.html>
- 🔗 DB2 11.5 Recursive CTE: <https://www.ibm.com/docs/en/db2/11.5?topic=queries-recursive>

---

## 자가 점검

- [ ] LEFT JOIN의 결과를 그림으로 그릴 수 있다
- [ ] `NOT IN` 대신 `NOT EXISTS`를 쓸 줄 안다
- [ ] CTE로 복잡 쿼리를 단계로 나눈다
- [ ] 재귀 CTE로 계층 구조를 탐색했다
- [ ] LEFT JOIN의 우측 WHERE 조건이 결과를 INNER로 만드는 함정을 안다

다음: [`03_window_functions.md`](03_window_functions.md)
