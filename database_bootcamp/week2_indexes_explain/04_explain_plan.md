# Day 4 — EXPLAIN 해석 (DB2 · MySQL)

## 한 줄 요약

`EXPLAIN`은 옵티마이저가 그 쿼리를 **어떻게 실행할지 알려주는 X-ray**다. 둘 다 형식이 다르지만 본질은 같다: **어느 인덱스를 탔는가, 몇 행을 읽는가, 어떻게 JOIN하는가, 정렬·해시 비용이 있는가**. 이걸 못 읽으면 튜닝 못 한다.

## 학습 목표

- [ ] MySQL `EXPLAIN` / `EXPLAIN ANALYZE` / `EXPLAIN FORMAT=JSON|TREE` 차이
- [ ] DB2 `EXPLAIN PLAN FOR` + `db2expln` 사용
- [ ] **type / access path** 종류와 좋은 것·나쁜 것
- [ ] **rows / cardinality** 추정 의미
- [ ] **Extra** 컬럼 핵심 키워드 (Using index, Using filesort, Using temporary)
- [ ] JOIN 알고리즘: Nested Loop / Hash / Merge

---

## 1. MySQL EXPLAIN — 기본

```sql
EXPLAIN SELECT * FROM orders WHERE customer_id = 42;
```

결과 (테이블 형식):

```
+----+-------------+--------+-------+---------------+------+---------+-------+------+-------------+
| id | select_type | table  | type  | possible_keys | key  | key_len | ref   | rows | Extra       |
+----+-------------+--------+-------+---------------+------+---------+-------+------+-------------+
|  1 | SIMPLE      | orders | ref   | idx_customer  | idx..| 4       | const |   23 | Using where |
+----+-------------+--------+-------+---------------+------+---------+-------+------+-------------+
```

### 핵심 컬럼

| 컬럼 | 의미 |
|---|---|
| **type** | 접근 방식 (가장 중요) — system > const > eq_ref > ref > range > index > **ALL** |
| key | 실제 사용한 인덱스 |
| possible_keys | 후보 인덱스들 |
| **rows** | 추정 읽을 행 수 |
| filtered | rows 중 WHERE로 통과될 % |
| **Extra** | 추가 정보 (Using index, Using filesort 등) |

### type 종류 (좋은 순서)

| type | 의미 |
|---|---|
| `system` | 1행 테이블 |
| `const` | PK/Unique 인덱스로 1행 (`WHERE id = 1`) |
| `eq_ref` | JOIN에서 1행씩 매치 |
| `ref` | 비유니크 인덱스로 N행 |
| `fulltext` | FULLTEXT 인덱스 |
| `ref_or_null` | ref + IS NULL |
| `range` | 범위 스캔 (`WHERE id BETWEEN ...`) |
| `index` | 인덱스 전체 스캔 (테이블보다 작음) |
| `ALL` | **풀 테이블 스캔** ⚠ |

> ⚠ `type=ALL`이 큰 테이블에 보이면 위험 신호.

### Extra — 자주 보는 것

| Extra | 의미 |
|---|---|
| `Using where` | WHERE로 필터링 |
| `Using index` | ⭐ **Covering** — 테이블 안 봄 |
| `Using index condition` | ICP (Index Condition Pushdown) — 인덱스 단에서 추가 필터 |
| `Using temporary` | 임시 테이블 사용 (GROUP BY, DISTINCT, ORDER BY) — 비쌈 |
| `Using filesort` | 정렬 필요 — 메모리 또는 디스크 |
| `Impossible WHERE` | WHERE가 항상 false |
| `Select tables optimized away` | 옵티마이저가 계산해서 끝 (`COUNT(*) FROM t`) |
| `Using join buffer` | JOIN에 buffer 필요 (인덱스 없는 JOIN) |

---

## 2. MySQL EXPLAIN ANALYZE (8.0.18+)

실제 실행해보고 시간 측정. **분석에 진짜 유용**.

```sql
EXPLAIN ANALYZE SELECT c.name, SUM(o.total_amount) AS total
  FROM customers c JOIN orders o ON o.customer_id = c.id
 WHERE o.created_at >= '2026-05-01'
 GROUP BY c.id, c.name
 ORDER BY total DESC LIMIT 10;
```

출력 (트리 형식):

```
-> Limit: 10 row(s)  (actual time=12.345..12.350 rows=10 loops=1)
    -> Sort: total DESC, limit 10  (actual time=12.340..12.341 rows=10 loops=1)
        -> Group aggregate: sum(o.total_amount)  (actual time=...)
            -> Nested loop inner join  (actual time=0.5..10 rows=500 loops=1)
                -> Index range scan on o using idx_orders_created  (cost=... rows=500)
                -> Single-row index lookup on c using PRIMARY (id=o.customer_id)
```

### 좋은 점

- **actual time** = 실제 시간 (예상 cost가 아니라)
- **rows=실제 vs cost rows 추정** — 둘이 크게 다르면 통계 갱신 필요

### EXPLAIN FORMAT 옵션

```sql
EXPLAIN FORMAT=JSON SELECT ...;       -- JSON 상세
EXPLAIN FORMAT=TREE SELECT ...;       -- 트리 (EXPLAIN ANALYZE와 비슷)
EXPLAIN FORMAT=TRADITIONAL SELECT ...; -- 기본 표
```

---

## 3. DB2 EXPLAIN — 세 가지 방법

### (1) EXPLAIN PLAN FOR (테이블 기반)

먼저 EXPLAIN 테이블 생성 (한 번):

```sql
db2 -tvf $HOME/sqllib/misc/EXPLAIN.DDL
```

쿼리 분석:

```sql
EXPLAIN PLAN FOR
SELECT * FROM orders WHERE customer_id = 42;

-- 결과 보기
db2exfmt -d labdb -e DB2INST1 -g TIC -w -1 -n % -s % -# 0 -o explain.txt
cat explain.txt
```

### (2) db2expln (CLI, 빠른 분석)

```bash
docker exec -it db2-lab su - db2inst1
db2expln -d labdb -q "SELECT * FROM orders WHERE customer_id = 42" -t
```

출력:

```
Optimizer Plan:
                Rows
                RETURN
                (   1)
                Cost
                I/O
                |
                23
                FETCH
                (   2)
                7.5
                3
                /----+----\
            23           1000
        IXSCAN          TABLE: DB2INST1
        (   3)          ORDERS
        ...
```

읽는 법: **위에서 아래로 작업 흐름**. `IXSCAN` → `FETCH` → `RETURN`.

### (3) DB2 Visual Explain (Data Studio GUI)

- IBM Data Studio (GUI)
- 또는 DBeaver의 Explain Plan 기능
- 트리 시각화 보기 쉬움

### DB2 주요 노드 (operator)

| 노드 | 의미 |
|---|---|
| **TBSCAN** | 풀 테이블 스캔 ⚠ |
| **IXSCAN** | 인덱스 스캔 |
| **FETCH** | 인덱스에서 RID로 행 가져옴 (Bookmark) |
| **NLJOIN** | Nested Loop Join |
| **MSJOIN** | Merge Scan Join |
| **HSJOIN** | Hash Join |
| **GRPBY** | GROUP BY |
| **SORT** | 정렬 |
| **TEMP** | 임시 테이블 (subquery materialization) |

### 비용 추정

```
Cost: 7.5      ← timerons (DB2 단위)
I/O:  3        ← 예상 IO 수
Rows: 23       ← 예상 결과 행 수
```

**timeron**: 1 timeron ≈ 1ms (대략, 하드웨어마다 다름).

---

## 4. 같은 쿼리, 양쪽 비교

### 쿼리

```sql
SELECT c.name, SUM(o.total_amount) AS total
  FROM customers c JOIN orders o ON o.customer_id = c.id
 WHERE o.status = 'PAID' AND o.created_at >= '2026-05-01'
 GROUP BY c.id, c.name
 ORDER BY total DESC FETCH FIRST 10 ROWS ONLY;
```

### MySQL EXPLAIN ANALYZE

```
-> Limit: 10 row(s)
   -> Sort: total DESC, limit 10
       -> Stream results
           -> Group aggregate: sum(o.total_amount)
               -> Nested loop inner join
                   -> Index range scan on o using idx_orders_status_created
                       index cond: o.status = 'PAID' AND o.created_at >= '2026-05-01'
                   -> Single-row index lookup on c using PRIMARY
```

### DB2 db2expln

```
RETURN
  ↑
SORT (limit 10)
  ↑
GRPBY
  ↑
NLJOIN
  /        \
IXSCAN      IXSCAN
(orders)    (customers PK)
```

→ 본질은 같음. 표현만 다름.

---

## 5. 좋은 계획 vs 나쁜 계획 — 체크리스트

### ✅ 좋은 신호

- type: `const`, `eq_ref`, `ref` (작은 범위)
- Extra: `Using index` (Covering)
- rows: 실제 결과와 비슷한 추정
- JOIN: Nested Loop with index (조인 키에 인덱스)
- 정렬: 인덱스로 처리 (Using filesort 없음)

### ❌ 나쁜 신호

- **type: ALL** (풀 테이블 스캔, 큰 테이블에서)
- **Using temporary** (임시 테이블)
- **Using filesort** (정렬 비용)
- rows 추정이 실제와 10배 이상 차이 (통계 낡음)
- Hash Join 대량 메모리 사용
- 같은 테이블 여러 번 스캔

---

## 6. JOIN 알고리즘

### Nested Loop Join (NLJ)

```
for outer in outer_table:
    for inner in inner_table where inner.fk = outer.id:    -- 인덱스로
        emit (outer, inner)
```

- 한쪽이 작거나 조인 키 인덱스 있을 때 효율적
- 인덱스 없으면 outer × inner = 끔찍

### Hash Join (HSJOIN)

```
build_hash = hash(inner_table)
for outer in outer_table:
    matches = build_hash[outer.fk]
```

- 둘 다 크고 인덱스 없을 때
- 메모리 필요 (work_mem)
- DB2: 기본 지원 / MySQL: 8.0.18+ 지원

### Merge Join

```
sort(left); sort(right);
merge sorted streams
```

- 둘 다 이미 정렬되어 있을 때 (인덱스로)
- 옵티마이저가 가끔 선택

### 운영 진단

```sql
-- MySQL 8.0+
SHOW VARIABLES LIKE 'optimizer_switch';
-- hash_join=on
```

---

## 7. 실제 사례 — "이 쿼리 왜 느려요?"

### 케이스 1

```sql
SELECT * FROM orders WHERE DATE(created_at) = '2026-05-15';
```

EXPLAIN:
```
type: ALL, rows: 10000000, Extra: Using where
```

**진단**: DATE() 함수로 인덱스 무효 → 풀스캔.

**조치**:
```sql
SELECT * FROM orders
 WHERE created_at >= '2026-05-15 00:00:00'
   AND created_at <  '2026-05-16 00:00:00';
-- type: range, rows: 5000, Extra: Using index condition
```

### 케이스 2

```sql
SELECT u.* FROM users u
  JOIN profiles p ON p.user_id = u.id
 WHERE p.country = 'KR'
 ORDER BY u.created_at DESC
 LIMIT 20;
```

EXPLAIN:
```
users    type: ALL,  rows: 1000000
profiles type: ref,  rows: 500
Extra: Using filesort
```

**진단**:
- u가 풀스캔
- 정렬이 인덱스로 안 풀림

**조치 1**: 조인 순서 변경 — 옵티마이저가 profiles → users 방향이면 좋음
```sql
-- profiles의 country로 좁히고 → user_id로 users 점프
ALTER TABLE profiles ADD INDEX idx_country_user (country, user_id);
ALTER TABLE users ADD INDEX idx_created (created_at);
-- 그러나 ORDER BY users.created_at은 정렬 불가
```

**조치 2**: 데이터 모델 변경 — country를 users에 비정규화 후 (country, created_at) 인덱스. 또는 country 별 partition.

### 케이스 3

```sql
SELECT * FROM orders
 WHERE customer_id IN (SELECT id FROM customers WHERE country = 'KR');
```

EXPLAIN (MySQL 5.6):
```
DEPENDENT SUBQUERY: 매 행마다 subquery 실행 → O(N²)
```

**조치**: 8.0+ 옵티마이저 자동 변환되지만, 보장하려면 JOIN으로:
```sql
SELECT o.*
  FROM orders o
  JOIN customers c ON c.id = o.customer_id
 WHERE c.country = 'KR';
```

---

## 8. 실습

### Step 1: 같은 쿼리 양쪽 EXPLAIN

```sql
-- MySQL
EXPLAIN
SELECT c.name, COUNT(*) FROM customers c JOIN orders o ON o.customer_id = c.id
 GROUP BY c.id, c.name ORDER BY COUNT(*) DESC LIMIT 10;

EXPLAIN ANALYZE
SELECT c.name, COUNT(*) FROM customers c JOIN orders o ON o.customer_id = c.id
 GROUP BY c.id, c.name ORDER BY COUNT(*) DESC LIMIT 10;
```

```bash
# DB2
db2expln -d labdb -q "SELECT c.name, COUNT(*) FROM customers c JOIN orders o ON o.customer_id = c.id GROUP BY c.id, c.name ORDER BY 2 DESC FETCH FIRST 10 ROWS ONLY" -t
```

### Step 2: type=ALL을 type=range로 바꾸기

```sql
-- 풀스캔 강제하는 쿼리
EXPLAIN SELECT * FROM orders WHERE YEAR(created_at) = 2026;

-- 인덱스 활용 가능한 형태로 변환
EXPLAIN SELECT * FROM orders
 WHERE created_at >= '2026-01-01' AND created_at < '2027-01-01';

-- 비교: rows, type, Extra
```

### Step 3: 통계가 낡았을 때

```sql
-- 통계 강제 무효화 (MySQL)
ANALYZE TABLE orders DROP HISTOGRAM ON customer_id;     -- 8.0
-- 또는 옵티마이저 변수로 임시 변경

EXPLAIN ...;
-- rows 추정 vs 실제

ANALYZE TABLE orders UPDATE HISTOGRAM ON customer_id WITH 100 BUCKETS;

EXPLAIN ...;
-- 차이 비교
```

### Step 4: FORMAT=JSON으로 상세 보기

```sql
EXPLAIN FORMAT=JSON
SELECT * FROM orders WHERE customer_id = 42 ORDER BY created_at DESC LIMIT 10;
```

JSON에서 `cost_info`, `attached_condition`, `used_columns` 확인.

---

## 더 읽어볼 자료

- 📘 『High Performance MySQL』 (Schwartz) Ch. 6 (Query Optimization)
- 🔗 MySQL EXPLAIN: <https://dev.mysql.com/doc/refman/8.4/en/explain.html>
- 🔗 MySQL EXPLAIN ANALYZE: <https://dev.mysql.com/doc/refman/8.4/en/explain.html#explain-analyze>
- 🔗 DB2 db2expln: <https://www.ibm.com/docs/en/db2/11.5?topic=tools-db2expln>
- 🔗 Use The Index, Luke — Explain plan: <https://use-the-index-luke.com/sql/explain-plan>

---

## 자가 점검

- [ ] MySQL type=ALL을 보면 즉시 위험 신호로 인식한다
- [ ] Extra=Using index (Covering)가 좋은 신호임을 안다
- [ ] EXPLAIN ANALYZE에서 actual vs estimated rows 차이가 통계 문제임을 안다
- [ ] DB2 db2expln 출력에서 IXSCAN, TBSCAN, FETCH의 의미를 안다
- [ ] Nested Loop / Hash / Merge Join을 구별한다
- [ ] 함수 → 범위 변환으로 풀스캔 → 인덱스 스캔 전환했다

다음: [`05_optimizer_hints.md`](05_optimizer_hints.md)
