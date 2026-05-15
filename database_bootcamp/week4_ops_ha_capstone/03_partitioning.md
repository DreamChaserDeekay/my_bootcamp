# Day 3 — 파티셔닝

## 한 줄 요약

큰 테이블(억 단위 이상)을 **여러 작은 조각으로 나눠** 관리. 대표적으로 RANGE 파티셔닝(날짜·ID 기반) — "오래된 데이터는 별도 파티션으로, 통째로 DROP" 가능. 운영의 batch DELETE 폭주를 막는 가장 효과적인 도구.

## 학습 목표

- [ ] 파티셔닝의 목적과 한계
- [ ] RANGE / LIST / HASH 파티셔닝
- [ ] **Partition pruning** — 옵티마이저가 일부 파티션만 스캔
- [ ] MySQL 파티셔닝 vs DB2 파티셔닝
- [ ] 파티션 운영 (ADD, DROP, REORGANIZE)
- [ ] **샤딩** 과의 차이

---

## 1. 파티셔닝이란

```
큰 테이블 orders (10억 행)
   ↓ 파티셔닝
orders_2023 (1억)  orders_2024 (3억)  orders_2025 (4억)  orders_2026 (2억)
```

각 파티션은 별도 저장. **앱에서는 여전히 `orders`로 보임**.

### 효과

| 효과 | 설명 |
|---|---|
| **Pruning** | `WHERE created_at >= '2026-01-01'` → 2026 파티션만 스캔 |
| **빠른 DELETE** | `DROP PARTITION p_2023` → 1억 행을 즉시 삭제 (락 없음) |
| **인덱스 크기** | 각 파티션의 인덱스가 작음 → 캐시 효율 |
| **백업·복구** | 파티션 단위 |

### 한계

- 모든 파티션에 PK·UNIQUE 컬럼 포함 필수 (MySQL)
- 파티션 간 JOIN은 일반적
- 파티션 키가 항상 WHERE에 있어야 pruning 효과

---

## 2. RANGE 파티셔닝 (가장 흔함)

### MySQL

```sql
CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id INT NOT NULL,
    total_amount DECIMAL(12,2),
    status VARCHAR(20),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id, created_at)    -- 파티션 키 포함 필수
)
PARTITION BY RANGE (YEAR(created_at)) (
    PARTITION p2023 VALUES LESS THAN (2024),
    PARTITION p2024 VALUES LESS THAN (2025),
    PARTITION p2025 VALUES LESS THAN (2026),
    PARTITION p2026 VALUES LESS THAN (2027),
    PARTITION pmax  VALUES LESS THAN MAXVALUE
);
```

### DB2

```sql
CREATE TABLE orders (
    id           BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    customer_id  INT NOT NULL,
    total_amount DECIMAL(12,2),
    status       VARCHAR(20),
    created_at   TIMESTAMP NOT NULL,
    PRIMARY KEY (id, created_at)
)
PARTITION BY RANGE (created_at) (
    STARTING '2023-01-01' ENDING '2024-01-01' EXCLUSIVE EVERY 1 MONTH
);
-- 월 단위 자동 생성 — 매월 12 파티션
```

DB2는 또한:

```sql
PARTITION BY RANGE (created_at) (
    PARTITION p2023 STARTING '2023-01-01' ENDING '2023-12-31',
    PARTITION p2024 STARTING '2024-01-01' ENDING '2024-12-31',
    ...
);
```

---

## 3. Partition Pruning

```sql
-- 2026년만 조회 → 2026 파티션만 스캔
EXPLAIN PARTITIONS
SELECT * FROM orders WHERE created_at >= '2026-01-01' AND created_at < '2027-01-01';
-- partitions: p2026

-- 파티션 키 없는 조회 → 모든 파티션 스캔 (효과 X)
EXPLAIN PARTITIONS
SELECT * FROM orders WHERE customer_id = 42;
-- partitions: p2023,p2024,p2025,p2026,pmax
```

> ⚠ **파티션 키가 WHERE에 없으면 효과 없다.** 파티션 키 선택이 핵심.

---

## 4. 파티션 운영

### 추가 (ADD)

```sql
-- MySQL
ALTER TABLE orders ADD PARTITION (
    PARTITION p2027 VALUES LESS THAN (2028)
);

-- DB2
ALTER TABLE orders ADD PARTITION p2027
    STARTING '2027-01-01' ENDING '2027-12-31';
```

### 삭제 (DROP) — 빠른 대량 삭제

```sql
-- MySQL: 2023년 데이터 통째로 삭제 (instant operation)
ALTER TABLE orders DROP PARTITION p2023;

-- DB2: DETACH (별도 테이블로 분리 후 DROP)
ALTER TABLE orders DETACH PARTITION p2023 INTO old_orders_2023;
DROP TABLE old_orders_2023;
```

**vs 일반 DELETE**:

```sql
DELETE FROM orders WHERE created_at < '2024-01-01';
-- 1억 행 → 1시간 + 트랜잭션 로그 폭주 + 잠금 누적
```

DROP PARTITION = 즉시. 운영의 황금 도구.

### REORGANIZE

```sql
-- 파티션 합치기 (MySQL)
ALTER TABLE orders REORGANIZE PARTITION p2023, p2024 INTO (
    PARTITION p_old VALUES LESS THAN (2025)
);
```

---

## 5. LIST · HASH 파티셔닝

### LIST — 명시적 값

```sql
-- 국가별
CREATE TABLE customers (
    id INT NOT NULL,
    country VARCHAR(2) NOT NULL,
    ...
    PRIMARY KEY (id, country)
)
PARTITION BY LIST COLUMNS (country) (
    PARTITION p_kr VALUES IN ('KR'),
    PARTITION p_us VALUES IN ('US'),
    PARTITION p_asia VALUES IN ('JP','CN','TW'),
    PARTITION p_other VALUES IN (DEFAULT)
);
```

### HASH — 균등 분산

```sql
-- 균등 분산 (사용자 ID로)
CREATE TABLE sessions (
    user_id BIGINT NOT NULL,
    ...
    PRIMARY KEY (user_id)
)
PARTITION BY HASH (user_id) PARTITIONS 8;
```

핵심: **균등 분산만 목적**, partition pruning에 거의 효과 없음 (한 파티션에 매치되는지 모름).

---

## 6. 파티셔닝 vs 샤딩

| | 파티셔닝 | 샤딩 |
|---|---|---|
| 단위 | 한 DB 안의 테이블 분할 | 여러 DB 서버에 데이터 분산 |
| 노드 | 1 노드 | N 노드 |
| 트랜잭션 | 일반 트랜잭션 | 분산 트랜잭션 (복잡) |
| 운영 복잡도 | 낮음 | 매우 높음 |
| 확장 한계 | 단일 노드 (CPU·디스크) | 수평 무한 |

> 일반 OLTP는 보통 **파티셔닝으로 충분**. 진짜 거대한 데이터(수 TB+)에서만 샤딩 검토. 클라우드 매니지드 DB(Aurora, Spanner)가 샤딩 자동 처리.

---

## 7. 실제 사례

### 사례 1: "로그 테이블이 10억 행, DELETE 못 함"

```sql
-- 매월 DELETE FROM logs WHERE created_at < N-3개월
-- → 3시간 걸림 + 디스크 IO 폭주 + 트랜잭션 로그 가득

-- 해결: 월 단위 RANGE 파티셔닝
ALTER TABLE logs PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p202401 VALUES LESS THAN (TO_DAYS('2024-02-01')),
    ...
);

-- 매월 cron으로
ALTER TABLE logs DROP PARTITION p2024xx;     -- 즉시
ALTER TABLE logs ADD PARTITION (...);         -- 새 달
```

### 사례 2: "최근 1년만 자주 조회"

```sql
-- 90% 조회가 created_at >= NOW() - 1Y
-- 옛 데이터는 거의 안 봄

-- 파티셔닝:
-- - 최근 12개월: SSD
-- - 옛 데이터: HDD (DB2 multi-temperature storage 또는 파티션별 테이블스페이스)
```

DB2:
```sql
CREATE TABLE orders (...) PARTITION BY RANGE (created_at) (
    PARTITION p_hot ... IN tbs_ssd,
    PARTITION p_cold ... IN tbs_hdd
);
```

---

## 8. ❌ / ✅

### 파티션 키가 WHERE에 없음

```sql
-- ❌ PRIMARY KEY는 id이고 파티션은 created_at
SELECT * FROM orders WHERE id = 123;
-- → 모든 파티션 스캔 (id는 파티션 키와 무관)

-- ✅ 파티션 키도 함께
SELECT * FROM orders WHERE id = 123 AND created_at >= '2026-01-01';
```

### Unique constraint와 충돌

```sql
-- ❌
PRIMARY KEY (id), PARTITION BY (created_at)
-- → MySQL 에러: unique 컬럼은 모두 파티션 키 포함해야

-- ✅
PRIMARY KEY (id, created_at)
```

### 너무 많은 파티션

```
1000+ 파티션 → 메모리·메타 데이터 부담
보통 100 이하 권장
```

### 변경 위험

```
-- ALTER TABLE ... PARTITION BY ...는 큰 작업 (테이블 재생성)
-- 운영 중에는 거의 불가, pt-online-schema-change 등 사용
```

---

## 9. 실습

### Step 1: 파티션 테이블 만들기

```sql
-- MySQL
CREATE TABLE orders_p LIKE orders;
ALTER TABLE orders_p
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id, created_at),
    PARTITION BY RANGE (YEAR(created_at)) (
        PARTITION p2024 VALUES LESS THAN (2025),
        PARTITION p2025 VALUES LESS THAN (2026),
        PARTITION p2026 VALUES LESS THAN (2027),
        PARTITION pmax  VALUES LESS THAN MAXVALUE
    );

-- 데이터 채우기
INSERT INTO orders_p SELECT * FROM orders;

-- Pruning 확인
EXPLAIN PARTITIONS
SELECT * FROM orders_p WHERE created_at >= '2026-01-01';
```

### Step 2: DROP PARTITION의 위력

```sql
-- 일반 DELETE (orders 테이블 — 비파티션)
SELECT BENCHMARK(1, (SELECT COUNT(*) FROM (SELECT @s := 0 FROM dual) x));
-- 측정용

START TRANSACTION;
DELETE FROM orders WHERE YEAR(created_at) = 2024;
-- 시간 측정
ROLLBACK;

-- vs 파티션 DROP
ALTER TABLE orders_p DROP PARTITION p2024;
-- 즉시 끝
```

### Step 3: 운영 자동화

```bash
#!/bin/bash
# monthly_partition.sh — cron으로 매월 1일 실행

NEXT_MONTH=$(date -d '+1 month' +%Y-%m-01)
TWO_AFTER=$(date -d '+2 month' +%Y-%m-01)
P_NAME="p$(date -d '+1 month' +%Y%m)"

mysql -uroot -p labdb <<EOF
ALTER TABLE orders_p ADD PARTITION (
    PARTITION $P_NAME VALUES LESS THAN (TO_DAYS('$TWO_AFTER'))
);
EOF

# 6개월 이전 파티션 DROP
OLD=$(date -d '-6 month' +%Y%m)
mysql -uroot -p labdb <<EOF
ALTER TABLE orders_p DROP PARTITION p$OLD;
EOF
```

---

## 더 읽어볼 자료

- 🔗 MySQL Partitioning: <https://dev.mysql.com/doc/refman/8.4/en/partitioning.html>
- 🔗 DB2 Range Partitioning: <https://www.ibm.com/docs/en/db2/11.5?topic=partitioning-table>
- 📘 『High Performance MySQL』 Ch. 7

---

## 자가 점검

- [ ] 파티셔닝의 4가지 효과 (pruning, 빠른 DROP, 인덱스 크기, 백업)
- [ ] 파티션 키가 WHERE에 없으면 효과 없음
- [ ] DROP PARTITION이 DELETE보다 압도적으로 빠른 이유
- [ ] MySQL의 partition + unique constraint 충돌
- [ ] 파티셔닝과 샤딩의 차이

다음: [`04_monitoring_slow_logs.md`](04_monitoring_slow_logs.md)
