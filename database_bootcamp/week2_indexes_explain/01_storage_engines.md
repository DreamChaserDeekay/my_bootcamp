# Day 1 — 스토리지 엔진 · Clustered Index

## 한 줄 요약

테이블은 디스크에 **페이지(8KB~16KB)** 단위로 저장된다. MySQL InnoDB는 **clustered index** 구조로 PK 자체가 데이터 저장 순서이고, DB2는 기본은 **heap** 구조(PK는 별도 인덱스)이지만 MDC(Multi-Dimensional Clustering)나 CLUSTER 옵션으로 클러스터링 가능하다. 이 차이가 인덱스 비용·PK 설계·페이지 분할 동작 모두에 영향을 미친다.

## 학습 목표

- [ ] 페이지(Page)·익스텐트(Extent)·테이블스페이스(Tablespace) 개념
- [ ] **Clustered index** vs **Secondary index** 차이
- [ ] InnoDB의 PK = clustered index를 이해
- [ ] DB2의 heap + index 구조를 이해
- [ ] PK 설계의 영향 (BIGINT auto-inc vs UUID)
- [ ] 페이지 분할(Page split)과 fill factor

---

## 1. 디스크 구조 — 페이지

| 단위 | 크기 (기본) | 의미 |
|---|---|---|
| Row | 가변 | 하나의 행 |
| **Page (Block)** | 16KB (InnoDB), 4~32KB (DB2 가변) | IO의 단위 |
| Extent | 1MB (InnoDB 64페이지) | 페이지의 묶음, 공간 할당 단위 |
| Segment | 다중 | 한 테이블 또는 인덱스 |
| Tablespace | | 물리 파일들의 묶음 |

> 💡 DB는 한 행만 필요해도 **페이지 단위로 읽는다**. 100바이트 행 1개 = 16KB 페이지 1개 I/O.

### InnoDB 페이지 (16KB 기본)

```
┌─────────────────────────────────────┐
│ Page Header  (38 B)                  │
│ Page Trailer (8 B)                   │
│ Rows (변동)                          │
│ ...                                  │
│ Page Directory (역방향, 슬롯)         │
└─────────────────────────────────────┘
```

### DB2 페이지 (4K/8K/16K/32K 선택, 테이블스페이스별)

```
┌─────────────────────────────────────┐
│ Page Header                          │
│ Slot Directory                       │
│ Rows                                 │
│ Free Space                           │
└─────────────────────────────────────┘
```

페이지 크기 선택:

- **작은 행(< 100B)**: 4K (작은 페이지에 더 많이)
- **큰 행 / OLAP**: 32K (한 페이지에 많이, 캐시 효율)
- 기본 8K가 무난

---

## 2. Clustered Index — 데이터가 인덱스 안에 있다

### InnoDB

```
Clustered Index (= 데이터 + PK)
┌─────────────────────────┐
│ PK   | 다른 컬럼들...     │
│ 1    | Alice, alice@...  │
│ 2    | Bob,   bob@...    │
│ 3    | Carol, carol@...  │
│ ...                     │
└─────────────────────────┘
   ↑ PK 순서로 정렬됨

Secondary Index (예: email)
┌──────────────────┬──────┐
│ email            │ PK   │
├──────────────────┼──────┤
│ alice@...        │ 1    │
│ bob@...          │ 2    │
│ carol@...        │ 3    │
└──────────────────┴──────┘
```

세컨더리 인덱스로 검색 → PK 찾음 → clustered index에서 행 가져옴 (**bookmark lookup**).

### DB2 (기본: heap + 인덱스)

```
Heap (테이블)
┌──────┬─────────────────────────┐
│ RID  │ 행 데이터                 │
├──────┼─────────────────────────┤
│ R1   │ id=2, Bob, bob@...      │
│ R2   │ id=1, Alice, alice@...  │   ← 순서 없음
│ R3   │ id=3, Carol, carol@...  │
└──────┴─────────────────────────┘

PK Index
┌────┬──────┐
│ PK │ RID  │
├────┼──────┤
│ 1  │ R2   │
│ 2  │ R1   │
│ 3  │ R3   │
└────┴──────┘
```

RID (Row Identifier) = 페이지번호 + 슬롯번호.

### DB2의 CLUSTER 인덱스

DB2 인덱스에 `CLUSTER` 옵션을 주면 데이터가 그 인덱스 순서대로 물리 정렬되도록 권유.

```sql
CREATE INDEX ix_orders_created ON orders(created_at) CLUSTER;
```

REORG가 실제로 정렬 적용. 자동 유지 X.

### 차이가 만드는 것

| | InnoDB clustered | DB2 heap |
|---|---|---|
| PK 범위 스캔 | 빠름 (연속 페이지) | 인덱스 + 흩어진 RID 따라가야 |
| 세컨더리 인덱스 크기 | PK 포함 (큰 PK면 큼) | RID만 포함 (작음) |
| 무작위 PK 삽입 | 페이지 분할 잦음 (UUID 위험) | 영향 적음 (heap은 append) |
| 검색 후 row fetch | 1단계 (clustered) 또는 2단계 (secondary) | 항상 2단계 (index → RID → heap) |

---

## 3. PK 설계의 중대성

### InnoDB에서 무작위 PK = 페이지 분할 폭주

```sql
-- ❌ UUID 그대로 PK
CREATE TABLE events (
    id CHAR(36) PRIMARY KEY,   -- 새 row가 무작위 위치에 삽입 → 페이지 분할
    ...
);

-- ✅ auto-increment PK + UUID 별도 (자연 키는 unique index)
CREATE TABLE events (
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid   CHAR(36) NOT NULL UNIQUE,
    ...
);

-- ✅ 또는 UUID v7 (시간순)
-- MySQL 8.0+ : UUID_TO_BIN(uuid, swap_flag=1)
CREATE TABLE events (
    id BINARY(16) PRIMARY KEY,   -- 정렬 가능 UUID
    ...
);
INSERT INTO events VALUES (UUID_TO_BIN(UUID(), 1), ...);
```

### DB2에서는 영향이 덜하지만

PK를 무작위로 두면 **PK 인덱스만** 영향 (데이터는 heap이라 무관). 그래도 정렬 가능한 PK가 더 좋음.

### 큰 PK의 비용

세컨더리 인덱스가 PK를 포함하므로:

```
secondary index (email) = email 컬럼 + PK
```

PK가 BIGINT(8B)이면 가볍지만, CHAR(36) UUID이면 모든 세컨더리 인덱스가 36B씩 추가됨. 인덱스 5개면 한 행당 +180B.

---

## 4. 페이지 분할 (Page Split)

InnoDB:

```
페이지 5: [1, 5, 10, 20, 30]     ← 가득 참
새 행 15 삽입
   ↓
페이지 5: [1, 5, 10, 15]
페이지 6: [20, 30]               ← 분할 발생
```

- 분할 시 **2개 페이지 IO + 인덱스 갱신**
- 무작위 PK는 매번 분할 → 성능 저하

### Fill Factor / PCTFREE

페이지에 빈 공간을 미리 두어 분할을 줄임:

```sql
-- DB2
CREATE TABLE orders (...) PCTFREE 20;
-- 페이지의 20%를 빈 공간으로

-- MySQL InnoDB
ALTER TABLE orders ROW_FORMAT=DYNAMIC;
-- innodb_fill_factor (시스템 변수, 인덱스용)
```

---

## 5. 행 형식 (Row Format)

### InnoDB

| 포맷 | 특징 |
|---|---|
| COMPACT | 옛 기본 (5.7-) |
| **DYNAMIC** (기본 8.0+) | 가변 길이 컬럼 오버플로우 페이지 |
| COMPRESSED | KEY_BLOCK_SIZE로 압축 |
| REDUNDANT | 매우 옛 호환 |

```sql
SHOW TABLE STATUS LIKE 'orders';
-- Row_format 컬럼
```

### DB2

테이블스페이스에 따름. 가변·고정 길이 자동.

```sql
SELECT TABNAME, ROWSIZE FROM SYSCAT.TABLES WHERE TABSCHEMA = 'DB2INST1';
```

---

## 6. 버퍼풀 / Buffer Pool

DB는 자주 쓰는 페이지를 메모리에 캐시.

### MySQL InnoDB Buffer Pool

```sql
SHOW VARIABLES LIKE 'innodb_buffer_pool_size';
-- 기본 128MB. 운영서 RAM의 50~75% 권장

-- 적중률
SHOW GLOBAL STATUS LIKE 'Innodb_buffer_pool_read%';
-- read_requests vs reads (디스크) → 비율로 적중률
```

### DB2 Buffer Pool

```sql
-- 정보
SELECT BPNAME, PAGESIZE, NPAGES FROM SYSCAT.BUFFERPOOLS;

-- 운영
ALTER BUFFERPOOL IBMDEFAULTBP SIZE 50000;     -- 페이지 수 (×PAGESIZE)
```

> 💡 **운영의 첫 번째 튜닝**: 버퍼풀을 RAM에 맞게 키우기. 디스크 IO를 메모리로 옮기는 효과가 가장 큼.

---

## 7. 통계 (Statistics)

옵티마이저는 통계를 보고 비용 추정. 통계가 낡으면 잘못된 계획 선택.

### MySQL

```sql
-- 통계 수집
ANALYZE TABLE orders;

-- 자동
-- innodb_stats_auto_recalc = ON (기본)
-- 10% 이상 변경 시 자동 (innodb_stats_persistent_sample_pages)
```

### DB2

```sql
-- RUNSTATS — 통계 수집
RUNSTATS ON TABLE db2inst1.orders WITH DISTRIBUTION AND DETAILED INDEXES ALL;

-- 또는 자동 (자동 RUNSTATS, 기본 활성)
-- SYSPROC.AUTOMAINT_GET_POLICY('AUTO_RUNSTATS', ...)
```

> ⚠ **운영 사고 사례**: 1000만 행 테이블의 통계가 옛 100만 행 시점이면 옵티마이저가 "이 정도면 풀스캔 빨라"라고 잘못 판단. RUNSTATS / ANALYZE 정기 실행 필수.

---

## 8. 실습

### Step 1: 페이지 구조 확인

```sql
-- MySQL: innodb_buffer_pool 상태
SHOW ENGINE INNODB STATUS\G

-- 테이블 통계
SHOW TABLE STATUS LIKE 'orders';
-- Avg_row_length, Data_length, Index_length

-- 행 한 개당 차지 공간
SELECT
    table_name,
    ROUND(data_length / 1024 / 1024, 2) AS data_mb,
    ROUND(index_length / 1024 / 1024, 2) AS idx_mb,
    table_rows
  FROM information_schema.tables
 WHERE table_schema = 'labdb';
```

```sql
-- DB2: 테이블 / 인덱스 크기
SELECT TABNAME, CARD,
       NPAGES * PAGESIZE / 1024 AS DATA_KB
  FROM SYSCAT.TABLES
 WHERE TABSCHEMA = 'DB2INST1';

-- 인덱스 별 크기
SELECT INDNAME, NLEAF, NLEVELS FROM SYSCAT.INDEXES
 WHERE TABSCHEMA = 'DB2INST1';
```

### Step 2: clustered vs secondary 동작 비교

```sql
-- InnoDB: PK lookup vs 세컨더리 → bookmark
EXPLAIN SELECT * FROM customers WHERE id = 42;          -- clustered
EXPLAIN SELECT * FROM customers WHERE email = 'x@y';    -- secondary → PK → clustered

-- DB2 동등
EXPLAIN PLAN FOR SELECT * FROM customers WHERE id = 42;
```

### Step 3: 페이지 분할 시뮬레이션

```sql
-- 무작위 INSERT (시작 PK가 큰 값) — InnoDB
CREATE TABLE test_seq (id INT PRIMARY KEY, val INT);
CREATE TABLE test_rand (id INT PRIMARY KEY, val INT);

-- 순차 (1, 2, 3, ...)
INSERT INTO test_seq SELECT n, RAND()*100 FROM ... ;

-- 무작위 (100000, 5, 99999, ...)
INSERT INTO test_rand SELECT FLOOR(RAND()*1000000), RAND()*100 FROM ...;

-- 비교 — innodb_buffer_pool_pages_dirty, splits
SHOW STATUS LIKE 'Innodb_pages_split';
```

### Step 4: ANALYZE 효과

```sql
-- MySQL: 통계 강제 갱신
ANALYZE TABLE orders;
EXPLAIN SELECT * FROM orders WHERE customer_id = 42;
-- rows 추정치 확인

-- 강제로 옛 통계로 만들기
SET SESSION optimizer_use_condition_selectivity = 1;
-- 다른 추정치 비교
```

```sql
-- DB2
RUNSTATS ON TABLE db2inst1.orders;
EXPLAIN PLAN FOR SELECT * FROM orders WHERE customer_id = 42;
```

---

## 더 읽어볼 자료

- 📘 『Database Internals』 (Alex Petrov) Ch. 1~7
- 📘 『High Performance MySQL』 (Schwartz) Ch. 5 (Index, schema)
- 🔗 InnoDB Storage Engine: <https://dev.mysql.com/doc/refman/8.4/en/innodb-storage-engine.html>
- 🔗 DB2 Storage architecture: <https://www.ibm.com/docs/en/db2/11.5?topic=database-storage-architecture>

---

## 자가 점검

- [ ] 페이지·익스텐트·테이블스페이스의 의미
- [ ] Clustered vs Secondary 인덱스 구조 그림
- [ ] InnoDB는 PK = clustered, DB2는 heap+PK index임을 안다
- [ ] 무작위 PK가 InnoDB에서 페이지 분할을 일으키는 이유
- [ ] 큰 PK가 세컨더리 인덱스를 비대하게 만드는 이유
- [ ] 통계 갱신(RUNSTATS / ANALYZE)이 왜 중요한지

다음: [`02_btree_index.md`](02_btree_index.md)
