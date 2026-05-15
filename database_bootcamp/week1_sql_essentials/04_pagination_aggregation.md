# Day 4 — 페이징 · 집계 · ROLLUP/CUBE

## 한 줄 요약

페이징은 **얕은 페이지(OFFSET 0~100)는 둘 다 빠르고, 깊은 페이지(OFFSET 100000+)는 둘 다 느리다**. 진짜 해법은 키 기반 페이지네이션(seek method). 집계는 GROUP BY 위에 `ROLLUP`·`CUBE`·`GROUPING SETS`로 다차원 합계를 한 쿼리에 담을 수 있다.

## 학습 목표

- [ ] DB2/MySQL의 페이징 표준·방언을 안다
- [ ] **OFFSET 페이지네이션의 한계**와 **키 기반(seek)** 페이지네이션을 안다
- [ ] `GROUP BY`의 평가 순서를 안다 (FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT)
- [ ] `HAVING`과 `WHERE`의 차이
- [ ] `ROLLUP`, `CUBE`, `GROUPING SETS`로 다차원 집계
- [ ] `COUNT(*)` vs `COUNT(컬럼)` vs `COUNT(DISTINCT ...)` 차이

---

## 1. 페이지네이션

### 표준 (둘 다)

```sql
SELECT ...
  FROM ...
 ORDER BY ...
OFFSET 100 ROWS
 FETCH FIRST 20 ROWS ONLY;
```

### MySQL 단축

```sql
SELECT ... ORDER BY ... LIMIT 20 OFFSET 100;
SELECT ... ORDER BY ... LIMIT 100, 20;     -- offset, count (헷갈리는 순서, 주의)
```

### DB2 — 표준 그대로

```sql
SELECT ... ORDER BY ... OFFSET 100 ROWS FETCH FIRST 20 ROWS ONLY;
```

### Spring Data JPA가 만드는 SQL

```sql
-- Pageable(page=5, size=20) → OFFSET 100 + FETCH FIRST 20
```

---

## 2. OFFSET 페이지네이션의 함정

```sql
-- 페이지 5000, 한 페이지 20개
SELECT * FROM orders ORDER BY created_at DESC
OFFSET 100000 ROWS FETCH FIRST 20 ROWS ONLY;
```

### 왜 느린가

DB는 **100,020 행을 읽고 100,000 행을 버린다**. OFFSET이 클수록 비용 증가.

### "정확한 총 개수" 추가 비용

```sql
-- 사용자에게 "5만 페이지 중 5000페이지" 표시하려면
SELECT COUNT(*) FROM orders;     -- 별도 쿼리, 큰 테이블에서 비쌈
```

### MySQL의 `SQL_CALC_FOUND_ROWS` (8.0.17부터 deprecated)

```sql
SELECT SQL_CALC_FOUND_ROWS * FROM orders LIMIT 20;
SELECT FOUND_ROWS();
```

→ deprecated. `COUNT(*)` 따로 호출 권장.

---

## 3. 키 기반 페이지네이션 (Seek Method)

이전 페이지의 **마지막 키**를 기억하고, "그 키보다 작은(또는 큰) 행만" 가져오기.

```sql
-- 첫 페이지
SELECT id, customer_id, total_amount, created_at
  FROM orders
 ORDER BY created_at DESC, id DESC
 FETCH FIRST 20 ROWS ONLY;
-- → 마지막 행의 (created_at='2026-05-15 09:00:00', id=12345) 기억

-- 다음 페이지
SELECT id, customer_id, total_amount, created_at
  FROM orders
 WHERE (created_at, id) < ('2026-05-15 09:00:00', 12345)
 ORDER BY created_at DESC, id DESC
 FETCH FIRST 20 ROWS ONLY;
```

### 장점

- **OFFSET이 0**: 항상 빠름 (인덱스 lookup + 20행 스캔)
- 페이지가 깊어져도 비용 일정

### 단점

- **임의 페이지로 점프 불가** (5000페이지 직접 이동 X)
- 정렬 키가 유일성 보장 필요 (created_at 동점 가능 → id 보조 키)
- 사용자 UX가 다름 (보통 "더보기"/"다음" 패턴)

### Java/Spring 적용

```java
// Spring Data JPA - "Slice"가 키 기반에 가까움 (count 안 함)
Slice<Order> findByCreatedAtLessThanOrderByCreatedAtDescIdDesc(
    LocalDateTime cursor, Pageable pageable);
```

> ⚠ **운영 진실**: 사내 통계·관리자 화면이 "전체 페이지 표시"를 요구해도, 100만+ 데이터에서는 **무한 스크롤 + 정확한 카운트 없음** 패턴이 권장. 카운트가 꼭 필요하면 분리된 통계 테이블에 캐시.

---

## 4. GROUP BY 평가 순서

```sql
SELECT customer_id, COUNT(*) AS cnt
  FROM orders
 WHERE status = 'PAID'        -- 1) 행 필터
 GROUP BY customer_id          -- 2) 그룹화
 HAVING COUNT(*) > 5           -- 3) 그룹 필터
 ORDER BY cnt DESC             -- 4) 정렬
 FETCH FIRST 10 ROWS ONLY;     -- 5) 페이징
```

### WHERE vs HAVING

| | WHERE | HAVING |
|---|---|---|
| 적용 시점 | GROUP BY 전 | GROUP BY 후 |
| 사용 가능한 것 | 원본 컬럼 | 원본 컬럼 + 집계 함수 |
| 사용 시 | 행 필터 | 그룹 필터 |

```sql
-- ❌ WHERE에 집계 함수 사용 — 에러
SELECT customer_id, COUNT(*) FROM orders
 WHERE COUNT(*) > 5 GROUP BY customer_id;

-- ✅ HAVING으로
SELECT customer_id, COUNT(*) FROM orders
 GROUP BY customer_id HAVING COUNT(*) > 5;
```

### 흔한 함정: SELECT 절의 컬럼

```sql
-- ❌ name이 GROUP BY에 없으면 에러 (표준 SQL)
SELECT customer_id, name, COUNT(*) FROM orders o JOIN customers c ON c.id=o.customer_id
 GROUP BY customer_id;

-- ✅ name도 GROUP BY
SELECT c.id, c.name, COUNT(*) FROM orders o JOIN customers c ON c.id=o.customer_id
 GROUP BY c.id, c.name;
```

> ⚠ **MySQL 5.6 이하**는 위 ❌ 쿼리가 (무작위 name) 실행됨. ONLY_FULL_GROUP_BY 모드가 8.0부터 기본 ON. 옛 코드 마이그레이션 시 주의.

---

## 5. ROLLUP · CUBE · GROUPING SETS

다차원 집계를 한 쿼리로.

### ROLLUP — 계층적 합계

```sql
-- 부서별·직급별 + 부서별 소계 + 전체 합계
SELECT dept_id, position, SUM(salary) AS total
  FROM employees
 GROUP BY ROLLUP (dept_id, position);

-- 결과:
-- dept_id  position  total
-- 1        Junior    5000      ← 1번부서 Junior
-- 1        Senior    8000      ← 1번부서 Senior
-- 1        NULL      13000     ← 1번부서 소계
-- 2        Junior    4500
-- 2        Senior    7500
-- 2        NULL      12000     ← 2번부서 소계
-- NULL     NULL      25000     ← 전체 합계
```

### CUBE — 모든 차원 조합

```sql
-- 부서별·직급별 + 부서별 소계 + 직급별 소계 + 전체 합계
SELECT dept_id, position, SUM(salary)
  FROM employees
 GROUP BY CUBE (dept_id, position);
```

### GROUPING SETS — 원하는 차원만 명시

```sql
-- 부서별 합계, 직급별 합계, 전체 합계 (총 3 종류)
SELECT dept_id, position, SUM(salary)
  FROM employees
 GROUP BY GROUPING SETS ((dept_id), (position), ());
```

### `GROUPING()` 함수 — 소계 표시 구분

```sql
SELECT
    CASE WHEN GROUPING(dept_id) = 1 THEN '전체'
         ELSE CAST(dept_id AS VARCHAR(10)) END AS dept,
    CASE WHEN GROUPING(position) = 1 THEN '부서소계'
         ELSE position END AS pos,
    SUM(salary)
  FROM employees
 GROUP BY ROLLUP (dept_id, position);
```

### DB2 vs MySQL 지원

| | DB2 | MySQL |
|---|---|---|
| ROLLUP | ⭕ | ⭕ |
| CUBE | ⭕ | ❌ (없음, GROUPING SETS로 우회) |
| GROUPING SETS | ⭕ | 8.0.1+ (실은 ROLLUP만 진정 지원, CUBE/SETS는 제한적) |

> MySQL은 `WITH ROLLUP` (`GROUP BY ... WITH ROLLUP`) 옛 문법이 있고, 8.0+ 부터 ROLLUP의 표준 문법도 지원. CUBE는 미지원.

```sql
-- MySQL WITH ROLLUP
SELECT dept_id, position, SUM(salary) FROM employees
 GROUP BY dept_id, position WITH ROLLUP;
```

---

## 6. COUNT 변형들

```sql
-- 전체 행 수 (NULL 포함)
SELECT COUNT(*) FROM orders;

-- 특정 컬럼이 NULL이 아닌 행 수
SELECT COUNT(customer_id) FROM orders;

-- 중복 제거
SELECT COUNT(DISTINCT customer_id) FROM orders;

-- 조건부 카운트 (CASE)
SELECT
    COUNT(*) AS total,
    COUNT(CASE WHEN status = 'PAID'      THEN 1 END) AS paid_count,
    COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled_count,
    SUM(CASE WHEN status = 'PAID' THEN total_amount ELSE 0 END) AS paid_total
  FROM orders;

-- MySQL: 더 짧게
SELECT
    COUNT(*) AS total,
    SUM(status = 'PAID')      AS paid_count,        -- TRUE=1
    SUM(status = 'CANCELLED') AS cancelled_count
  FROM orders;
-- DB2는 boolean 식이 컬럼에 못 들어가니 CASE WHEN 필수
```

### COUNT(*) 성능

| DB | 비용 |
|---|---|
| MySQL InnoDB | **항상 풀 스캔** (MVCC 때문에 정확한 카운트는 가변). 옛 MyISAM은 캐시함 |
| DB2 | 통계 기반 추정 가능, 또는 인덱스 풀 스캔 |
| PostgreSQL (참고) | 인덱스만 스캔 가능 (visibility map) |

→ **대용량에서 정확 카운트는 비싸다.** 대안: 캐시 테이블, 근사치 사용.

---

## 7. ❌ / ✅

### "총 페이지 수" 표시의 비용

```sql
-- ❌ 100만 행 테이블에서 매 요청마다
SELECT COUNT(*) FROM orders WHERE customer_id = 42;

-- ✅ 1) 캐시 (Redis), 2) 통계 테이블, 3) 키 기반 페이지네이션
```

### MySQL의 `WHERE` + `LIMIT`의 의외

```sql
-- 잘못 알면 위험
SELECT * FROM orders LIMIT 100;

-- ❌ ORDER BY 없으면 결과 순서가 비결정적
-- 한 번은 A, 다음은 B를 줄 수 있음 (InnoDB clustered index 순서지만 보장 X)

-- ✅ 항상 ORDER BY와 함께
SELECT * FROM orders ORDER BY id DESC LIMIT 100;
```

### GROUP BY에서 SELECT 절 컬럼 함정

```sql
-- ❌ MySQL 5.6: name이 무작위로 결정됨 (옛 비표준)
SELECT customer_id, name, MAX(total_amount) FROM orders
 GROUP BY customer_id;

-- ✅ 명시적
SELECT customer_id, MAX(total_amount), MAX(name) FROM orders GROUP BY customer_id;
-- 또는 윈도우 함수로
```

### DISTINCT vs GROUP BY

```sql
-- 둘 다 같은 결과, 둘 다 보통 같은 실행계획
SELECT DISTINCT customer_id FROM orders;
SELECT customer_id FROM orders GROUP BY customer_id;
```

가독성·의도에 따라 선택. 둘 다 정렬·해시 비용 있음.

---

## 8. 실전 케이스

### "월별 매출 + 누계"

```sql
SELECT
    EXTRACT(YEAR  FROM created_at) AS yr,
    EXTRACT(MONTH FROM created_at) AS mo,
    SUM(total_amount) AS monthly_total,
    SUM(SUM(total_amount)) OVER (ORDER BY EXTRACT(YEAR FROM created_at),
                                          EXTRACT(MONTH FROM created_at)) AS running_total
  FROM orders
 WHERE status = 'PAID'
 GROUP BY EXTRACT(YEAR FROM created_at), EXTRACT(MONTH FROM created_at)
 ORDER BY yr, mo;
```

### "상위 5% 고객"

```sql
-- 톱 5%
WITH ranked AS (
    SELECT customer_id, SUM(total_amount) AS spent,
           NTILE(20) OVER (ORDER BY SUM(total_amount) DESC) AS tile
      FROM orders WHERE status = 'PAID'
     GROUP BY customer_id
)
SELECT * FROM ranked WHERE tile = 1;
```

### "각 부서의 직급별 + 부서 소계"

```sql
SELECT
    COALESCE(CAST(dept_id AS VARCHAR(10)), 'TOTAL') AS dept,
    COALESCE(position, 'SUB-TOTAL') AS position,
    COUNT(*) AS headcount,
    SUM(salary) AS total_salary
  FROM employees
 GROUP BY ROLLUP (dept_id, position)
 ORDER BY dept_id, position;
```

---

## 9. 실습

### Step 1: 페이지네이션 두 방식 비교

```sql
-- 데이터 많이 (실습용)
-- DB2/MySQL 둘 다 — 100만 행 만들기는 무거우니 1만행
-- (실제 운영 환경 가정 학습)

-- OFFSET 깊은 페이지
SELECT * FROM orders ORDER BY created_at DESC
OFFSET 9000 ROWS FETCH FIRST 20 ROWS ONLY;
-- 실행시간 측정

-- Seek
SELECT * FROM orders
 WHERE (created_at, id) < ('2026-...', 9020)
 ORDER BY created_at DESC, id DESC
 FETCH FIRST 20 ROWS ONLY;
-- 실행시간 비교
```

### Step 2: 다차원 집계 (양쪽)

```sql
-- DB2 (CUBE)
SELECT customer_id, status, COUNT(*), SUM(total_amount)
  FROM orders
 GROUP BY CUBE (customer_id, status);

-- MySQL (CUBE 없음 → GROUPING SETS 우회 또는 UNION ALL)
SELECT customer_id, status, COUNT(*), SUM(total_amount) FROM orders GROUP BY customer_id, status
UNION ALL
SELECT customer_id, NULL, COUNT(*), SUM(total_amount) FROM orders GROUP BY customer_id
UNION ALL
SELECT NULL, status, COUNT(*), SUM(total_amount) FROM orders GROUP BY status
UNION ALL
SELECT NULL, NULL, COUNT(*), SUM(total_amount) FROM orders;
```

### Step 3: 조건부 집계 (MySQL 단축 비교)

```sql
-- DB2 (CASE 사용)
SELECT
    COUNT(*) AS total,
    COUNT(CASE WHEN status = 'PAID'      THEN 1 END) AS paid,
    COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled
  FROM orders;

-- MySQL (SUM with boolean)
SELECT
    COUNT(*) AS total,
    SUM(status = 'PAID')      AS paid,
    SUM(status = 'CANCELLED') AS cancelled
  FROM orders;
```

---

## 더 읽어볼 자료

- 🔗 Markus Winand, "Pagination done the right way": <https://use-the-index-luke.com/sql/partial-results/fetch-next-page>
- 🔗 MySQL 8 GROUPING: <https://dev.mysql.com/doc/refman/8.4/en/group-by-modifiers.html>
- 🔗 DB2 OLAP: <https://www.ibm.com/docs/en/db2/11.5?topic=conditions-grouping-sets>

---

## 자가 점검

- [ ] OFFSET 페이지네이션의 성능 한계를 설명한다
- [ ] 키 기반(seek) 페이지네이션의 장단점을 안다
- [ ] WHERE와 HAVING의 차이를 안다
- [ ] ROLLUP, CUBE, GROUPING SETS의 차이를 안다
- [ ] MySQL이 CUBE를 지원하지 않음을 안다
- [ ] DISTINCT와 GROUP BY가 보통 같은 실행계획임을 안다

다음: [`05_sql_pitfalls.md`](05_sql_pitfalls.md)
