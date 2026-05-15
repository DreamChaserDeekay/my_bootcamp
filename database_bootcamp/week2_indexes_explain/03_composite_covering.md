# Day 3 — 복합 인덱스 · Covering · 함수 인덱스

## 한 줄 요약

복합 인덱스(여러 컬럼)는 **컬럼 순서가 결정적**이다. (A, B) 인덱스는 A=? 조건이나 A=? AND B=? 조건에서는 효과적이지만 B=?만으로는 못 탄다. Covering 인덱스는 **인덱스 leaf에 필요한 컬럼을 모두 포함**시켜서 테이블 페이지에 접근하지 않게 만든다. 이 두 기법만 잘 써도 쿼리 성능이 10배~100배 차이 난다.

## 학습 목표

- [ ] 복합 인덱스의 **leftmost prefix** 규칙
- [ ] 컬럼 순서 결정 기준 (선택도, equality 먼저, sort 마지막)
- [ ] **Covering Index** = 인덱스만으로 쿼리 해결 (Index-Only Scan)
- [ ] DB2 `INCLUDE` / MySQL은 인덱스에 그냥 추가
- [ ] **함수 기반 인덱스** (DB2/MySQL 8+)
- [ ] 정렬 방향이 다른 컬럼 인덱스

---

## 1. 복합 인덱스 — leftmost prefix 규칙

```sql
CREATE INDEX idx_a_b_c ON t(a, b, c);
```

이 인덱스는 다음 조건들에서 효과적:

| 쿼리 | 인덱스 사용 |
|---|---|
| `WHERE a = ?` | ⭕ 사용 |
| `WHERE a = ? AND b = ?` | ⭕ 사용 |
| `WHERE a = ? AND b = ? AND c = ?` | ⭕ 사용 (가장 좋음) |
| `WHERE a = ? AND c = ?` | △ a만 사용 (c는 후처리) |
| `WHERE b = ?` | ❌ 사용 안 함 |
| `WHERE c = ?` | ❌ |
| `WHERE b = ? AND c = ?` | ❌ |
| `WHERE a > ? AND b = ?` | △ a 범위 + b는 후처리 |

> 💡 **leftmost prefix**: 인덱스의 왼쪽부터 연속해서 사용한 만큼만 효과적.

### 왜 그런가 — B-tree 구조

```
idx_a_b_c:  (a, b, c) 순으로 정렬된 키
leaf:  (1,1,1)→(1,1,5)→(1,2,3)→(1,3,1)→(2,1,1)→...

WHERE a=1 AND b=2: 트리 탐색으로 (1,2,*) 영역 찾기 가능 ⭕
WHERE b=2:         a 값을 모르니 트리 탐색 불가 ❌
```

### 시각화: 전화번호부

성+이름 순으로 정렬된 전화번호부에서:
- "김씨 중 철수": 즉시 찾음 (성으로 좁히고 이름으로 좁힘)
- "철수 (성 무관)": 처음부터 끝까지 다 봐야 함

---

## 2. 컬럼 순서 결정 가이드

### 1순위: Equality 조건 먼저, 범위는 나중

```sql
-- 자주 쓰는 쿼리
WHERE customer_id = ? AND created_at >= ?

-- ✅ equality(customer_id) → range(created_at)
CREATE INDEX ON orders(customer_id, created_at);

-- ❌ range가 앞에 오면 customer_id 활용 못 함
CREATE INDEX ON orders(created_at, customer_id);
```

### 2순위: 선택도 높은 것 먼저 (보통)

선택도 = "값을 알면 결과가 얼마나 좁혀지는가". 높을수록 인덱스 트리에서 빨리 좁혀짐.

```sql
-- email은 unique (선택도 매우 높음)
-- status는 4가지 값 (선택도 낮음)
-- WHERE email = ? AND status = 'PAID'

-- ✅
CREATE INDEX ON users(email, status);

-- 그러나 만약 항상 status='PAID' 조건만 추가되고 email만 검색한다면
-- email 단독 인덱스로도 충분
```

### 3순위: ORDER BY 컬럼

```sql
-- 자주: WHERE customer_id = ? ORDER BY created_at DESC LIMIT 20
CREATE INDEX ON orders(customer_id, created_at DESC);
-- 정렬 단계 생략
```

### 4순위: SELECT 컬럼 — Covering (다음 섹션)

---

## 3. Covering Index — Index-Only Scan

### 일반 인덱스 스캔

```
1. Index에서 키 찾음
2. PK/RID 얻음
3. 테이블 페이지에서 행 가져옴   ← bookmark lookup
```

### Covering 인덱스

```
인덱스 leaf에 SELECT 컬럼이 다 있음 → 2번에서 끝
"테이블 페이지를 안 읽음" → Index-Only Scan
```

### 예시

```sql
-- 자주: SELECT id, name FROM users WHERE email = ?
-- 인덱스가 (email)만 있으면: email 찾고 → PK 찾고 → 테이블에서 name 가져옴

-- ✅ Covering — name까지 인덱스에 포함
-- DB2
CREATE INDEX idx_users_email ON users(email) INCLUDE (name);

-- MySQL — INCLUDE 문법 없음, 그냥 컬럼 추가
CREATE INDEX idx_users_email_name ON users(email, name);
-- 또는 (email, id, name) 등 — InnoDB는 PK가 자동 포함되어 있음
```

### EXPLAIN으로 확인

```sql
EXPLAIN SELECT id, name FROM users WHERE email = '...';
-- MySQL: Extra: "Using index"   ← Covering!
-- DB2 db2expln: Index Only Access
```

### Covering의 효과

- 큰 테이블에서 **bookmark lookup 비용 제거** → 5~10배 빨라질 수 있음
- 단점: 인덱스가 커짐 (저장공간, 쓰기 비용)

### 운영 사례

```sql
-- 인기 쿼리: 최근 30일 PAID 주문의 customer_id, total_amount
SELECT customer_id, SUM(total_amount)
  FROM orders
 WHERE status = 'PAID'
   AND created_at >= CURRENT_DATE - INTERVAL '30' DAY
 GROUP BY customer_id;

-- ✅ Covering 인덱스 (status, created_at, customer_id, total_amount)
-- DB2
CREATE INDEX idx_orders_paid_recent ON orders(status, created_at)
    INCLUDE (customer_id, total_amount);

-- MySQL
CREATE INDEX idx_orders_paid_recent
    ON orders(status, created_at, customer_id, total_amount);
```

→ 인덱스만 스캔하고 테이블 안 봄.

---

## 4. INCLUDE (DB2) vs 컬럼 추가 (MySQL)

| | DB2 INCLUDE | MySQL 컬럼 추가 |
|---|---|---|
| 효과 | leaf에 컬럼 저장, **검색 키는 아님** | leaf에 컬럼 저장, **검색 키도 됨** |
| leftmost prefix | INCLUDE 컬럼은 prefix에 안 들어감 | 모든 컬럼이 prefix에 들어감 |
| 인덱스 크기 | 작음 (검색 트리는 가벼움) | 큼 (모든 컬럼이 트리에) |

```sql
-- DB2: 검색은 email로만, 결과에 name 포함
CREATE INDEX ix1 ON users(email) INCLUDE (name);
-- email로 검색 ⭕, name으로 검색 ❌, email+name으로 ⭕ 

-- MySQL: email, name 둘 다 검색 키
CREATE INDEX ix1 ON users(email, name);
-- email로 검색 ⭕, email+name으로 ⭕, name 단독 ❌
```

> 💡 INCLUDE는 "Covering만 필요하고 검색 키로는 의미 없을 때" 인덱스를 가볍게 유지. DB2의 장점.

---

## 5. 함수 기반 인덱스

```sql
-- ❌ 함수 씌우면 일반 인덱스 무력
WHERE UPPER(email) = 'ALICE@EXAMPLE.COM'

-- ✅ 함수 인덱스
-- DB2 (10.5+)
CREATE INDEX idx_email_upper ON users (UPPER(email));

-- MySQL 8.0+
CREATE INDEX idx_email_upper ON users ((UPPER(email)));
-- 괄호 두 겹 주의: ((expression))

-- 또는 generated column 사용 (MySQL)
ALTER TABLE users
    ADD COLUMN email_upper VARCHAR(255) GENERATED ALWAYS AS (UPPER(email)) STORED,
    ADD INDEX idx_email_upper (email_upper);
```

### 다른 활용

```sql
-- 날짜 부분 인덱스 (월별 집계가 잦을 때)
CREATE INDEX idx_orders_month ON orders ((MONTH(created_at)));    -- MySQL 8+
-- 그러나 보통은 created_at 범위 인덱스가 더 일반적

-- JSON 컬럼 인덱스 (MySQL 5.7+ generated)
ALTER TABLE products
    ADD COLUMN meta_color VARCHAR(20)
    GENERATED ALWAYS AS (JSON_UNQUOTE(meta->'$.color')) STORED,
    ADD INDEX idx_meta_color (meta_color);
```

---

## 6. 인덱스 정렬 방향

### 같은 방향만 정렬

```sql
-- ORDER BY a ASC, b ASC
-- ✅ (a, b) 인덱스로 정렬 생략

-- ORDER BY a DESC, b DESC
-- ✅ (a, b) 인덱스 역방향으로 정렬 생략

-- ORDER BY a ASC, b DESC
-- ❌ (a, b) 또는 (a DESC, b DESC)로는 정렬 못 함 → 별도 sort
-- ✅ 명시적 (a ASC, b DESC) 인덱스 필요

-- MySQL 8+: 컬럼별 정렬 지정 가능
CREATE INDEX idx_x ON t(a ASC, b DESC);

-- DB2: 9.7+ 지원
CREATE INDEX idx_x ON t(a ASC, b DESC);
```

> MySQL 5.7 이하에서는 컬럼별 정렬을 인덱스에 표기해도 무시. 8.0부터 진짜 지원.

---

## 7. 부분 인덱스 (Filtered Index) — 한쪽만 지원

### DB2: 부분 인덱스 지원

```sql
-- 활성 사용자만 인덱스에 포함 (인덱스 크기 ↓)
CREATE INDEX idx_users_active ON users(email) WHERE status = 'ACTIVE';
```

### MySQL: 미지원

대체:
- generated column + 인덱스로 우회
- 또는 별도 테이블 분리

---

## 8. ❌ 안티패턴

### 컬럼마다 인덱스 — 단일 컬럼 인덱스 남발

```sql
-- ❌ 자주 같이 검색되는데 각각 인덱스
CREATE INDEX idx_customer ON orders(customer_id);
CREATE INDEX idx_status   ON orders(status);
CREATE INDEX idx_created  ON orders(created_at);

-- 쿼리: WHERE customer_id = ? AND status = 'PAID' AND created_at >= ?
-- → 옵티마이저가 인덱스 merge 또는 한 개만 사용. 효율 ↓

-- ✅ 복합 인덱스
CREATE INDEX idx_x ON orders(customer_id, status, created_at);
```

### 너무 많은 인덱스

```
INSERT 한 번 = 인덱스 N개 = leaf N번 갱신
```

10개 이상이면 검토 대상.

### 중복 인덱스

```sql
-- ❌
CREATE INDEX idx_a   ON t(a);
CREATE INDEX idx_a_b ON t(a, b);
-- idx_a는 idx_a_b로 대체 가능 (leftmost prefix). 중복.

-- ✅ idx_a 삭제
```

### 외래 키 컬럼에 인덱스 빠짐

```sql
CREATE TABLE orders (
    ...
    customer_id INT NOT NULL,
    CONSTRAINT fk_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);
-- ❌ MySQL은 FK 컬럼에 자동 인덱스 (대부분), DB2는 자동 X — 명시 필요
-- 확인 후 없으면
CREATE INDEX idx_orders_customer ON orders(customer_id);
```

부모 테이블에서 DELETE/UPDATE 시 자식의 FK 컬럼 인덱스가 없으면 풀스캔 → 무거운 락.

---

## 9. 실제 사례

### 사례 1: "주문 목록 화면이 느려요"

```sql
-- 매번 호출되는 쿼리
SELECT id, customer_id, status, total_amount, created_at
  FROM orders
 WHERE customer_id = ?
   AND status IN ('PAID', 'PENDING')
 ORDER BY created_at DESC
 LIMIT 20;
```

분석:
- equality: customer_id
- IN: status (작은 set)
- range/sort: created_at DESC

**최적 인덱스**:

```sql
-- DB2
CREATE INDEX idx_orders_my ON orders(customer_id, status, created_at DESC)
    INCLUDE (total_amount);

-- MySQL
CREATE INDEX idx_orders_my ON orders(customer_id, status, created_at, total_amount);
```

EXPLAIN: Using index (Covering). ORDER BY 정렬 생략.

### 사례 2: "월별 매출 리포트가 느려요"

```sql
SELECT DATE_FORMAT(created_at, '%Y-%m') AS ym, SUM(total_amount)
  FROM orders
 WHERE status = 'PAID'
   AND created_at >= '2026-01-01'
 GROUP BY DATE_FORMAT(created_at, '%Y-%m');
```

- 함수 `DATE_FORMAT`이 그룹화에 사용 → 인덱스 못 탐
- → Generated column + 인덱스 또는 함수 인덱스

```sql
-- MySQL 8+
ALTER TABLE orders
    ADD COLUMN year_month CHAR(7) GENERATED ALWAYS AS (DATE_FORMAT(created_at, '%Y-%m')) STORED,
    ADD INDEX idx_ym_status (year_month, status, total_amount);
```

### 사례 3: "Covering 효과 확인"

```sql
-- Before
EXPLAIN SELECT id, name FROM users WHERE email = ?;
-- type=ref, rows=1, Extra=NULL (bookmark lookup)

-- After
ALTER TABLE users ADD INDEX idx_email_name (email, name);

EXPLAIN SELECT id, name FROM users WHERE email = ?;
-- type=ref, rows=1, Extra="Using index"   ← Covering
```

---

## 10. 실습

### Step 1: 컬럼 순서 실험

```sql
-- 두 가지 인덱스 만들고 비교
CREATE INDEX idx_a_b ON orders(customer_id, status);
CREATE INDEX idx_b_a ON orders(status, customer_id);

-- 쿼리
EXPLAIN SELECT * FROM orders WHERE customer_id = 42 AND status = 'PAID';
-- 어느 인덱스를 선택?
-- ALTER TABLE ... ALTER INDEX ... INVISIBLE 으로 하나씩 끄고 비교
```

### Step 2: Covering 효과

```sql
-- Before
EXPLAIN SELECT id, customer_id, total_amount FROM orders WHERE customer_id = 42;

CREATE INDEX idx_cov ON orders(customer_id, total_amount);
-- MySQL: id는 PK라 자동 포함됨

-- After
EXPLAIN SELECT id, customer_id, total_amount FROM orders WHERE customer_id = 42;
-- Extra: Using index   확인
```

### Step 3: 함수 인덱스

```sql
-- MySQL 8+
CREATE INDEX idx_email_lower ON users ((LOWER(email)));

EXPLAIN SELECT * FROM users WHERE LOWER(email) = 'alice@example.com';
-- 인덱스 사용 확인
```

### Step 4: 중복·미사용 인덱스 찾기

```sql
-- MySQL: 중복 인덱스 후보
SELECT t.table_schema, t.table_name, GROUP_CONCAT(t.index_name)
  FROM information_schema.statistics t
 GROUP BY t.table_schema, t.table_name, t.column_name, t.seq_in_index
HAVING COUNT(*) > 1;

-- 더 좋은 도구: percona-toolkit의 pt-duplicate-key-checker
```

---

## 더 읽어볼 자료

- 📘 『Use The Index, Luke』 — Chapter 2 (Where Clause), Chapter 3 (Performance and Scalability)
- 🔗 MySQL Multi-Column Indexes: <https://dev.mysql.com/doc/refman/8.4/en/multiple-column-indexes.html>
- 🔗 DB2 Index include columns: <https://www.ibm.com/docs/en/db2/11.5?topic=indexes-include-columns>

---

## 자가 점검

- [ ] (a, b, c) 인덱스에서 `WHERE b=?`만으로 못 타는 이유
- [ ] 복합 인덱스 컬럼 순서 결정 우선순위 (equality → range → order by)
- [ ] Covering 인덱스가 무엇이고 왜 빠른지
- [ ] DB2 INCLUDE와 MySQL 컬럼 추가의 차이
- [ ] 함수 인덱스가 필요한 경우
- [ ] FK 컬럼에 인덱스 없으면 부모 변경이 느린 이유

다음: [`04_explain_plan.md`](04_explain_plan.md)
