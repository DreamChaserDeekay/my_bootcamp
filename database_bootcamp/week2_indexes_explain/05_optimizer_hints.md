# Day 5 — 옵티마이저 · 통계 · 힌트

## 한 줄 요약

옵티마이저는 **통계를 보고 비용을 추정해 가장 싼 계획을 선택**하는 알고리즘이다. 통계가 부정확하거나 모델의 한계로 잘못된 계획을 선택할 때, 힌트로 강제할 수 있다. 그러나 힌트는 **마지막 수단**이다 — 통계 갱신·인덱스 추가·쿼리 재작성이 우선.

## 학습 목표

- [ ] 옵티마이저 동작 원리 (Cost-based)
- [ ] 통계 종류 (cardinality, distribution, histogram)
- [ ] MySQL `ANALYZE TABLE` / DB2 `RUNSTATS` 사용
- [ ] **히스토그램** — 비균등 분포 처리
- [ ] MySQL 힌트 (`USE INDEX`, `STRAIGHT_JOIN`, optimizer hints)
- [ ] DB2 힌트 (`OPTGUIDELINES`, profile)
- [ ] 힌트의 위험과 대안

---

## 1. 옵티마이저 — 무엇을 하나

```
SQL ──> 파싱 ──> 논리적 변환 ──> 비용 추정 ──> 실행 계획 선택
                                  ↑
                              통계 사용
```

### 비용 추정에 쓰는 정보

- 테이블 행 수 (cardinality)
- 컬럼별 유니크 값 수
- 컬럼별 분포 (히스토그램)
- 인덱스 깊이, leaf 페이지 수
- 페이지 크기, 버퍼풀 적중률 추정
- 시스템 자원 (CPU, IO 비용)

---

## 2. 통계 — 가장 중요한 입력

### 통계가 낡으면 무엇이 잘못되나

```sql
-- 테이블이 10만 행이었을 때 통계 수집
-- 그 후 1000만 행으로 증가
-- 통계는 여전히 10만으로 알고 있음

EXPLAIN SELECT * FROM orders WHERE status = 'PAID';
-- 옵티마이저: "10만 중 50%면 5만행 — 풀스캔이 낫겠다"
-- 실제: 1000만 중 50% = 500만행 — 풀스캔도 비싸지만 인덱스로 후처리하면 더 비쌈
```

또는 반대로:

```sql
-- 통계상 1000만 행
-- 실제는 1만 행만 남음 (DELETE 후)
-- 옵티마이저: "1000만 중 1만 — 인덱스가 좋겠다"
-- 실제: 1만 행 → 풀스캔이 더 빠를 수 있음
```

### MySQL 통계

```sql
-- 자동 통계 (기본)
SHOW VARIABLES LIKE 'innodb_stats_auto_recalc';        -- ON
SHOW VARIABLES LIKE 'innodb_stats_persistent';          -- ON

-- 수동
ANALYZE TABLE orders;

-- 히스토그램 (8.0+)
ANALYZE TABLE orders UPDATE HISTOGRAM ON status WITH 100 BUCKETS;
ANALYZE TABLE orders DROP HISTOGRAM ON status;

-- 통계 보기
SHOW INDEX FROM orders;
SELECT * FROM mysql.innodb_table_stats WHERE table_name = 'orders';
SELECT * FROM mysql.innodb_index_stats WHERE table_name = 'orders';
```

### DB2 통계

```sql
-- RUNSTATS
RUNSTATS ON TABLE db2inst1.orders
    WITH DISTRIBUTION AND DETAILED INDEXES ALL;

-- 자동 (기본 ON)
SELECT VALUE FROM SYSIBMADM.DBCFG WHERE NAME = 'auto_runstats';

-- 통계 확인
SELECT TABNAME, CARD, STATS_TIME FROM SYSCAT.TABLES WHERE TABSCHEMA = 'DB2INST1';
SELECT COLNAME, COLCARD, HIGH2KEY, LOW2KEY FROM SYSCAT.COLUMNS
 WHERE TABNAME = 'ORDERS' AND TABSCHEMA = 'DB2INST1';

-- 인덱스 통계
SELECT INDNAME, NLEAF, NLEVELS, FULLKEYCARD FROM SYSCAT.INDEXES
 WHERE TABNAME = 'ORDERS';
```

### 자동 통계의 함정

자동 통계는 **변경 비율이 임계치 초과 시** 갱신:

- MySQL: `innodb_stats_persistent_sample_pages` (기본 20). 적으면 부정확
- DB2: 자동 RUNSTATS는 활동 적을 때 수행 → 운영 피크에는 안 함

**대량 INSERT 후**에는 명시적 `ANALYZE` / `RUNSTATS` 강력 권장.

---

## 3. 히스토그램 — 비균등 분포 처리

### 균등 가정의 함정

옵티마이저 기본 가정: "분포는 균등하다".

```sql
-- status 컬럼:
-- PAID:      90%  (9백만)
-- PENDING:    5%  (50만)
-- CANCELLED:  4%  (40만)
-- FAILED:     1%  (10만)

EXPLAIN SELECT * FROM orders WHERE status = 'FAILED';
-- 균등 가정 (4가지 × 25% = 250만): 풀스캔이 낫다고 판단
-- 실제: 10만 행 → 인덱스가 훨씬 빠름
```

### 히스토그램 추가

```sql
-- MySQL 8+
ANALYZE TABLE orders UPDATE HISTOGRAM ON status WITH 100 BUCKETS;

EXPLAIN SELECT * FROM orders WHERE status = 'FAILED';
-- 히스토그램 사용: "FAILED는 1%" → 인덱스 선택
```

DB2는 `WITH DISTRIBUTION` 옵션으로 자동 distribution 통계 생성.

```sql
RUNSTATS ON TABLE db2inst1.orders WITH DISTRIBUTION ON COLUMNS (status);
```

---

## 4. MySQL 힌트

### 인덱스 힌트

```sql
-- 강제로 특정 인덱스 사용
SELECT * FROM orders USE INDEX (idx_created_at) WHERE customer_id = 42;

-- 강제로 인덱스 무시
SELECT * FROM orders IGNORE INDEX (idx_created_at) WHERE created_at >= '2026-01-01';

-- 반드시 사용 (없으면 에러)
SELECT * FROM orders FORCE INDEX (idx_created_at) WHERE created_at >= '2026-01-01';

-- JOIN 시 특정 인덱스
SELECT * FROM orders o FORCE INDEX FOR JOIN (idx_customer)
  JOIN customers c ON c.id = o.customer_id;
```

### JOIN 순서

```sql
-- 옵티마이저의 자동 재정렬 무시 — FROM 순서대로
SELECT STRAIGHT_JOIN c.name, o.id
  FROM customers c
  JOIN orders o ON o.customer_id = c.id
 WHERE c.country = 'KR';
```

### 옵티마이저 힌트 (5.7+, /*+ ... */)

```sql
SELECT /*+ INDEX(orders idx_customer) */ * FROM orders WHERE customer_id = 42;

-- JOIN 알고리즘 강제
SELECT /*+ HASH_JOIN(o, c) */ * FROM orders o JOIN customers c ON ...;
SELECT /*+ NO_HASH_JOIN(o, c) */ * FROM orders o JOIN customers c ON ...;

-- 인덱스 머지
SELECT /*+ INDEX_MERGE(orders idx_a, idx_b) */ * FROM orders
 WHERE a = 1 OR b = 2;

-- 시스템 변수 임시 변경 (8.0+)
SELECT /*+ SET_VAR(optimizer_switch='hash_join=off') */ ...;
```

### 자주 쓰는 힌트

| 힌트 | 효과 |
|---|---|
| `USE INDEX (idx)` | 그 인덱스 선호 |
| `FORCE INDEX (idx)` | 반드시 사용 |
| `IGNORE INDEX (idx)` | 그 인덱스 회피 |
| `STRAIGHT_JOIN` | JOIN 순서 고정 |
| `/*+ INDEX(t idx) */` | (옵티마이저 힌트) |
| `/*+ NO_INDEX(t idx) */` | |
| `/*+ MAX_EXECUTION_TIME(N) */` | N ms 초과 시 타임아웃 |

---

## 5. DB2 힌트 — OPTGUIDELINES

DB2는 SQL 힌트가 아닌 **별도 XML 가이드라인** 사용. 더 정교하지만 복잡.

### 인라인 가이드라인 (옛 방식)

```sql
SELECT *
  FROM orders
  /*<OPTGUIDELINES><IXSCAN INDEX="IDX_ORDERS_CUSTOMER"/></OPTGUIDELINES>*/
 WHERE customer_id = 42;
```

### Optimization Profile (운영 방식)

XML로 작성하여 EXPLAIN.OPT_PROFILE 테이블에 등록. 쿼리 텍스트와 매치되면 자동 적용.

```xml
<OPTPROFILE>
  <STMTPROFILE ID="my_profile">
    <STMTKEY><![CDATA[SELECT * FROM orders WHERE customer_id = ?]]></STMTKEY>
    <OPTGUIDELINES>
      <IXSCAN INDEX="DB2INST1.IDX_ORDERS_CUSTOMER"/>
    </OPTGUIDELINES>
  </STMTPROFILE>
</OPTPROFILE>
```

```sql
-- 등록
CALL SYSPROC.SET_DBOPTPROFILE('my_profile.xml', 'DBA');
-- 활성화
SET CURRENT OPTIMIZATION PROFILE = 'DBA.MY_PROFILE';
```

### Registry 변수 (전역)

```bash
# 통계 부정확 시 사용 (개발 환경만)
db2set DB2_USE_ALTERNATE_PAGE_CLEANING=ON
db2set DB2_OPT_MAX_TEMPS_KB=...
```

---

## 6. 힌트의 위험

### "당장은 맞지만 미래에 틀린다"

```sql
-- 오늘
SELECT /*+ INDEX(orders idx_created) */ * FROM orders WHERE created_at >= '2026-01-01';
-- 데이터가 1000만 행, status 균등 → 이 인덱스가 좋음

-- 6개월 후
-- 데이터 10억 행, 90%가 OLD status
-- idx_created 풀스캔이 너무 비싸짐. 옵티마이저는 다른 인덱스를 쓰고 싶은데 힌트가 막음
```

### "통계 갱신으로 해결되는 문제를 힌트로 봉합"

→ 진짜 문제(통계)는 안 풀리고, 다른 쿼리에서 또 발생.

### 추천 순서

```
1. 통계 갱신 (ANALYZE / RUNSTATS)
2. 인덱스 검토 (추가·삭제·재설계)
3. 쿼리 재작성 (서브쿼리 → JOIN, 함수 → 범위)
4. 마지막에 힌트
```

힌트를 쓰면 **주석으로 이유와 시점 기록**:

```sql
-- 2026-05-15: 통계 갱신 후에도 옵티마이저가 idx_status를 선택해
-- 풀스캔 비슷한 비용. 통계 분포가 안 맞는 케이스. ANALYZE HISTOGRAM 시도 실패.
-- 임시로 FORCE INDEX. 분기에 재검토.
SELECT * FROM orders FORCE INDEX (idx_customer)
 WHERE customer_id = ? AND status = 'PAID';
```

---

## 7. 옵티마이저 모드 / 설정

### MySQL `optimizer_switch`

```sql
SHOW VARIABLES LIKE 'optimizer_switch';
-- index_merge=on,index_merge_union=on,...,hash_join=on,...

-- 임시 변경 (디버깅용)
SET optimizer_switch = 'hash_join=off';
SELECT ...;
SET optimizer_switch = 'hash_join=on';
```

### DB2 Optimization Level

```sql
-- 0~9, 기본 5
SET CURRENT QUERY OPTIMIZATION = 9;     -- 더 많은 시간 들여 더 좋은 계획 찾기
-- 단 컴파일 시간이 길어짐 — 매번 실행에서는 부적합
```

| Level | 의미 |
|---|---|
| 0 | 최소 |
| 3 | 빠름 |
| 5 | **기본** |
| 7 | 더 좋음 |
| 9 | 모든 가능 검토 (느림) |

---

## 8. 실제 사례

### 사례 1: 통계만 갱신해도 10배 빨라진 쿼리

```sql
-- 처음 (느림)
EXPLAIN SELECT * FROM orders WHERE customer_id = 42;
-- rows: 1000000   ← 잘못된 추정 → 풀스캔

-- 통계 갱신
ANALYZE TABLE orders;
-- 또는 DB2: RUNSTATS ON TABLE db2inst1.orders WITH DISTRIBUTION;

EXPLAIN SELECT * FROM orders WHERE customer_id = 42;
-- rows: 50       ← 정확한 추정 → 인덱스
```

### 사례 2: 비균등 분포 → 히스토그램

```sql
-- 데이터: status 99%는 'OK', 0.5%는 'FAILED'

-- 균등 가정
EXPLAIN SELECT * FROM events WHERE status = 'FAILED';
-- 50% 가정 → 풀스캔

-- 히스토그램
ANALYZE TABLE events UPDATE HISTOGRAM ON status WITH 50 BUCKETS;

EXPLAIN SELECT * FROM events WHERE status = 'FAILED';
-- 0.5% 확인 → 인덱스 사용
```

### 사례 3: 옵티마이저가 잘못된 인덱스 선택

```sql
-- 인덱스 두 개
-- idx_a (created_at)        — 1000만 행, 거의 모든 행 포함
-- idx_b (customer_id, status) — 50행만 매치

EXPLAIN SELECT * FROM orders
 WHERE customer_id = 42 AND status = 'PAID' AND created_at >= '2026-01-01';

-- 옵티마이저가 idx_a 선택 (통계 부정확)
-- → 1000만 행 검사 → 느림

-- 강제
EXPLAIN SELECT * FROM orders FORCE INDEX (idx_b)
 WHERE customer_id = 42 AND status = 'PAID' AND created_at >= '2026-01-01';
-- 50행 즉시
```

**진짜 해결**: idx_a에 의존하지 않도록 인덱스 재설계 + 통계 갱신.

---

## 9. 실습

### Step 1: 통계 효과 측정

```sql
-- 대용량 데이터에 새 데이터 1만 행 INSERT
-- (실제 운영 시뮬레이션)

-- 통계 갱신 전
EXPLAIN ...;
-- 실행 시간 측정

ANALYZE TABLE orders;

-- 갱신 후
EXPLAIN ...;
-- 실행 시간 비교
```

### Step 2: 히스토그램 효과

```sql
-- 매우 편향된 데이터
-- 95%가 status='PAID', 5%가 나머지

EXPLAIN SELECT * FROM orders WHERE status = 'PENDING';
-- 균등 가정 → 풀스캔 가능성

ANALYZE TABLE orders UPDATE HISTOGRAM ON status;

EXPLAIN SELECT * FROM orders WHERE status = 'PENDING';
-- 히스토그램 사용 → 정확한 추정
```

### Step 3: 힌트 비교

```sql
-- 자동 선택
EXPLAIN SELECT * FROM orders WHERE customer_id = 42 AND status = 'PAID';

-- 인덱스 강제
EXPLAIN SELECT * FROM orders FORCE INDEX (idx_customer)
 WHERE customer_id = 42 AND status = 'PAID';

-- 인덱스 회피
EXPLAIN SELECT * FROM orders IGNORE INDEX (idx_customer)
 WHERE customer_id = 42 AND status = 'PAID';

-- 비용·시간 비교
```

### Step 4: DB2 db2expln 깊이 학습

```bash
db2expln -d labdb -t -q "SELECT ..." > plan.txt
# 결과 분석
# IXSCAN cost, FETCH cost, Sort cost 등을 노트에 정리
```

---

## 더 읽어볼 자료

- 📘 『High Performance MySQL』 Ch. 6
- 🔗 MySQL Optimizer Hints: <https://dev.mysql.com/doc/refman/8.4/en/optimizer-hints.html>
- 🔗 MySQL Histogram: <https://dev.mysql.com/doc/refman/8.4/en/histogram-statistics.html>
- 🔗 DB2 Optimization Profiles: <https://www.ibm.com/docs/en/db2/11.5?topic=tuning-optimization-profiles>

---

## 자가 점검

- [ ] 통계가 옵티마이저 결정에 미치는 영향
- [ ] 히스토그램이 필요한 경우 (비균등 분포)
- [ ] MySQL `ANALYZE TABLE` vs DB2 `RUNSTATS`
- [ ] 힌트는 최후의 수단임을 안다
- [ ] 힌트 추가 시 주석으로 이유·시점 기록
- [ ] `optimizer_switch`로 동작 변경 가능함을 안다

이번 주 마무리:

- [`labs/lab3_index_tuning.md`](labs/lab3_index_tuning.md)
- [`labs/lab4_explain_walkthrough.md`](labs/lab4_explain_walkthrough.md)
- [`checklist.md`](checklist.md)
