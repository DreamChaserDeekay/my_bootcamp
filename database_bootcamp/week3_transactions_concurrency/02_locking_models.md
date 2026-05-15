# Day 2 — 잠금 모델 (DB2 vs InnoDB)

## 한 줄 요약

DB는 동시성을 위해 **잠금(Lock)** 을 쓴다. DB2는 기본 **행 잠금 + 페이지/테이블 잠금으로 확장 (lock escalation)** 모델이고, InnoDB는 **행 잠금 + gap lock + next-key lock** 모델이다. 같은 SQL이 양쪽에서 다른 잠금 동작을 한다. 운영의 데드락·대기 문제는 이 차이를 모르면 풀 수 없다.

## 학습 목표

- [ ] 잠금의 종류 (shared/exclusive, 행/페이지/테이블)
- [ ] DB2의 lock escalation
- [ ] InnoDB의 row lock / gap lock / next-key lock
- [ ] `SELECT ... FOR UPDATE` / `FOR SHARE` 동작
- [ ] **lock wait**과 timeout 설정
- [ ] 현재 잠금 보는 명령 (`SHOW ENGINE INNODB STATUS`, `db2pd -d ... -locks`)

---

## 1. 잠금의 기본 두 종류

| 종류 | 다른 트랜잭션의 |
|---|---|
| **Shared (S, 공유)** | S 허용, X 차단. 보통 SELECT 시 |
| **Exclusive (X, 배타)** | S, X 모두 차단. UPDATE/DELETE/INSERT 시 |

### 호환성 매트릭스

| | S 보유 | X 보유 |
|---|---|---|
| S 요청 | ⭕ | ❌ 대기 |
| X 요청 | ❌ 대기 | ❌ 대기 |

> 즉, **읽기끼리는 충돌 안 함**, 쓰기는 모두와 충돌.

---

## 2. 잠금의 단위 (granularity)

### DB2

| 단위 | 설명 |
|---|---|
| **Row Lock** | 한 행만. 가장 세밀 (lock 개수 많아짐) |
| **Page Lock** | 한 페이지 (수십 행). 중간 |
| **Table Lock** | 전체 테이블. 가장 굵음 |
| Intent Lock (IS/IX) | 하위 단위에 락 있음을 표시 |

DB2는 자동으로 단위를 결정. 행이 너무 많아지면 **lock escalation** 발생:

```
1000행 row lock → 임계치 (LOCKLIST·MAXLOCKS) 초과 → table lock으로 escalation
```

이때 다른 트랜잭션이 그 테이블 전체에 못 접근 → 운영 사고.

### InnoDB

| 단위 | 설명 |
|---|---|
| **Record Lock** | 인덱스 레코드만 |
| **Gap Lock** | 인덱스 레코드 사이의 빈 공간 |
| **Next-Key Lock** | Record + Gap 결합 (REPEATABLE READ에서 phantom 방지) |
| Insert Intention Lock | INSERT 의도 표시 |
| Table Lock (드뭄) | LOCK TABLES, ALTER TABLE 시 |

> InnoDB는 **기본적으로 row lock만**. lock escalation 없음. 대신 lock 개수가 많아지면 메모리·관리 비용 증가.

---

## 3. DB2 Lock Escalation

```sql
-- 100,000행 UPDATE
UPDATE orders SET status = 'X' WHERE created_at < '2025-01-01';

-- DB2: 처음에는 100,000 row lock 요청
-- LOCKLIST 메모리 한계 도달 → table lock으로 escalation
-- 운영 로그:
-- ADM5500W  ... Lock list memory ... lock escalation occurred.
```

### 모니터링

```sql
-- 에스컬레이션 횟수
SELECT LOCK_ESCALS FROM SYSIBMADM.SNAPDB;

-- 설정
SELECT VALUE FROM SYSIBMADM.DBCFG WHERE NAME IN ('locklist', 'maxlocks');
-- locklist: 락 메모리 총량 (4KB 페이지 수)
-- maxlocks: 한 앱이 사용 가능한 비율 (%)
```

### 회피

```sql
-- 1. LOCKLIST 늘리기
UPDATE DB CFG FOR labdb USING LOCKLIST 8192;
UPDATE DB CFG FOR labdb USING MAXLOCKS 60;

-- 2. 배치를 청크로 나누기
DELETE FROM orders WHERE created_at < '2025-01-01' FETCH FIRST 1000 ROWS ONLY;
-- 반복

-- 3. 명시적 표 잠금 (의도적)
LOCK TABLE orders IN EXCLUSIVE MODE;
```

---

## 4. InnoDB의 Gap Lock · Next-Key Lock

### Gap Lock

```
인덱스 leaf:  (5, 10, 15, 20)
gap:        (-∞, 5) (5, 10) (10, 15) (15, 20) (20, +∞)
```

Gap Lock은 **gap에 INSERT를 막는 잠금**. 행 자체는 잠그지 않음.

### Next-Key Lock = Record + Gap

```
WHERE id BETWEEN 5 AND 15 FOR UPDATE
→ Record lock on (5, 10, 15) + Gap lock on (5,10), (10,15)
→ 그 범위에 INSERT 차단
```

이게 **REPEATABLE READ에서 phantom을 막는 메커니즘**.

### 예시: phantom 차단

```sql
-- 세션 1
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION;
SELECT * FROM orders WHERE customer_id = 42 FOR UPDATE;
-- → (customer_id, ...) 인덱스에 next-key lock

-- 세션 2
INSERT INTO orders (customer_id, ...) VALUES (42, ...);
-- → 대기 (gap lock 때문에)
```

### Gap Lock의 함정

운영에서 **의도치 않은 데드락 원인**의 1번.

```sql
-- 세션 1
START TRANSACTION;
SELECT * FROM orders WHERE id = 100 FOR UPDATE;
-- id=100이 없으면? → gap lock on (..., 100, ...)

-- 세션 2
INSERT INTO orders (id, ...) VALUES (101, ...);
-- → gap에 들어가려고 대기
```

### Gap Lock 비활성화

```sql
-- READ COMMITTED는 gap lock 사용 안 함
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
-- 단 phantom 발생 가능, 트레이드오프
```

> **운영 팁**: 대량 동시 INSERT 워크로드에서 데드락 심하면 격리수준을 READ COMMITTED로 낮추는 검토 가치 있음.

---

## 5. SELECT ... FOR UPDATE / FOR SHARE

### 명시적 행 잠금

```sql
-- 표준 (둘 다)
SELECT * FROM accounts WHERE id = 1 FOR UPDATE;       -- X lock
SELECT * FROM accounts WHERE id = 1 FOR SHARE;        -- S lock (MySQL 8+)
-- MySQL 8 이전: LOCK IN SHARE MODE
```

### NOWAIT / SKIP LOCKED (둘 다 지원)

```sql
-- 잠금 대기 안 함, 즉시 에러
SELECT * FROM orders WHERE id = 1 FOR UPDATE NOWAIT;

-- 잠금된 행은 건너뛰기 (잡 큐 구현에 유용)
SELECT * FROM jobs WHERE status = 'PENDING'
 ORDER BY id FETCH FIRST 1 ROWS ONLY FOR UPDATE SKIP LOCKED;
```

> ⚠ `SKIP LOCKED`는 잡 분산 처리(작업 큐)의 황금 패턴.

### DB2 명시적 잠금

```sql
-- 행 잠금
SELECT * FROM accounts WHERE id = 1 FOR UPDATE;
SELECT * FROM accounts WHERE id = 1 WITH RS USE AND KEEP UPDATE LOCKS;

-- 테이블 잠금
LOCK TABLE accounts IN EXCLUSIVE MODE;
LOCK TABLE accounts IN SHARE MODE;
```

---

## 6. Lock Timeout

### MySQL `innodb_lock_wait_timeout`

```sql
SHOW VARIABLES LIKE 'innodb_lock_wait_timeout';      -- 기본 50초

-- 세션 변경
SET SESSION innodb_lock_wait_timeout = 5;

-- 타임아웃 시
-- ERROR 1205 (HY000): Lock wait timeout exceeded; try restarting transaction
```

### DB2 `LOCKTIMEOUT`

```sql
-- 기본 -1 (무한 대기)
SELECT VALUE FROM SYSIBMADM.DBCFG WHERE NAME = 'locktimeout';

UPDATE DB CFG FOR labdb USING LOCKTIMEOUT 10;        -- 10초

-- 세션 (deprecated 형태지만 작동)
SET CURRENT LOCK TIMEOUT 5;

-- 타임아웃 시
-- SQL0911N The current transaction has been rolled back because of a deadlock or timeout.
-- Reason code "68"   ← 68 = lock timeout
```

> ⚠ DB2 운영 기본 `LOCKTIMEOUT = -1` (무한 대기)는 위험. **15~60초** 권장. 무한 대기는 운영서버에서 한 트랜잭션이 모든 것을 멈출 수 있음.

---

## 7. 현재 잠금 보기

### MySQL

```sql
-- 8.0+ : performance_schema
SELECT
    object_schema, object_name, lock_type, lock_mode, lock_status,
    lock_data, OWNER_THREAD_ID
  FROM performance_schema.data_locks;

-- 잠금 대기
SELECT * FROM performance_schema.data_lock_waits;

-- 또는 sys 스키마
SELECT * FROM sys.innodb_lock_waits;

-- 옛: SHOW ENGINE INNODB STATUS\G
-- LATEST DETECTED DEADLOCK, TRANSACTIONS 섹션
```

### DB2

```bash
# 현재 잠금
db2pd -d labdb -locks

# 잠금 대기 중
db2pd -d labdb -wlocks

# 잠금 보유자·대기자 함께
db2 "SELECT * FROM SYSIBMADM.LOCKS_HELD"
db2 "SELECT * FROM SYSIBMADM.LOCKWAITS"

# 더 자세히
db2pd -d labdb -tcbstats index
```

---

## 8. 양쪽 잠금 동작 비교 실험

### 시나리오: WHERE에 인덱스 없는 UPDATE

```sql
-- 데이터 준비
INSERT INTO orders ... 1000행
-- 컬럼 status에 인덱스 없음

-- 세션 1
START TRANSACTION;
UPDATE orders SET status = 'X' WHERE status = 'PENDING';
-- 매치되는 행 + (인덱스 없으므로) 풀스캔 → 모든 행 검사 → 매치 안 되는 행에도 잠시 락 가능

-- 세션 2
UPDATE orders SET status = 'Y' WHERE id = 999;
-- 매치 안 되는 행이지만, 세션 1이 풀스캔 중 잠시 락 잡으면 대기
```

### MySQL InnoDB

- READ COMMITTED: 검사 중인 행에만 일시 락
- REPEATABLE READ: 일부 케이스에서 풀스캔 중인 행들에 락 (gap lock 포함)

### DB2

- 풀스캔 시 행마다 락 잡음
- 많아지면 lock escalation → 테이블 락

> **교훈**: WHERE의 컬럼에 **반드시 인덱스**. 잠금 범위가 폭주.

---

## 9. ❌ / ✅

### 인덱스 없는 WHERE에 UPDATE/DELETE

```sql
-- ❌ status에 인덱스 없으면 모든 행 검사 → 모든 행에 일시 락
UPDATE orders SET notes = 'foo' WHERE status = 'PENDING';

-- ✅ 인덱스 추가 또는 PK로 좁히기
CREATE INDEX idx_status ON orders(status);
```

### 큰 배치 DELETE/UPDATE

```sql
-- ❌ 100만 행 한 번에 — escalation, 긴 트랜잭션, redo log 폭주
DELETE FROM logs WHERE created_at < '2025-01-01';

-- ✅ 청크로
DELETE FROM logs WHERE created_at < '2025-01-01' FETCH FIRST 10000 ROWS ONLY;
-- 반복 (스크립트)
```

### 잠금 잡고 외부 호출

```java
// ❌ 트랜잭션 안에 외부 API
@Transactional
public void process(Long orderId) {
    Order o = repo.findByIdForUpdate(orderId);   // X lock
    paymentApi.charge(o);                        // 5초 — 그동안 행 잠금
    o.setStatus(PAID);
}

// ✅ 결제는 트랜잭션 밖
public void process(Long orderId) {
    PaymentResult r = paymentApi.charge(orderId);
    markPaid(orderId, r);
}
@Transactional
void markPaid(Long orderId, PaymentResult r) { ... }
```

### NOWAIT / SKIP LOCKED 활용 안 함

```sql
-- ❌ 잠금 대기 무한 (또는 timeout)
SELECT * FROM jobs WHERE status = 'PENDING' FOR UPDATE;

-- ✅ 잡 큐 패턴
SELECT * FROM jobs WHERE status = 'PENDING'
 ORDER BY id FETCH FIRST 1 ROWS ONLY FOR UPDATE SKIP LOCKED;
```

---

## 10. 실습

### Step 1: 두 세션으로 잠금 대기 재현

```sql
-- 세션 1
START TRANSACTION;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
-- 커밋 안 함

-- 세션 2
UPDATE accounts SET balance = balance + 100 WHERE id = 1;
-- 대기...

-- 잠금 현황 (별도 세션)
-- MySQL:
SELECT * FROM performance_schema.data_lock_waits\G

-- 세션 1
COMMIT;
-- 세션 2 진행
```

### Step 2: NOWAIT / SKIP LOCKED

```sql
-- 세션 1: 1번 행 잠금
START TRANSACTION;
SELECT * FROM accounts WHERE id = 1 FOR UPDATE;

-- 세션 2
SELECT * FROM accounts WHERE id = 1 FOR UPDATE NOWAIT;
-- 즉시 에러

SELECT * FROM accounts FOR UPDATE SKIP LOCKED;
-- 1번은 건너뛰고 2번, 3번 반환
```

### Step 3: gap lock 효과

```sql
-- MySQL REPEATABLE READ
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;

-- 세션 1
START TRANSACTION;
SELECT * FROM orders WHERE customer_id = 42 FOR UPDATE;
-- (customer_id = 42 인덱스에 next-key lock)

-- 세션 2
INSERT INTO orders (customer_id, ...) VALUES (42, ...);
-- → 대기

-- 격리수준 변경
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
-- 위 다시
-- 세션 2 INSERT는 통과 (gap lock 없음)
```

### Step 4: DB2 lock escalation 강제

```sql
-- 작은 LOCKLIST로 설정
UPDATE DB CFG FOR labdb USING LOCKLIST 100 MAXLOCKS 10;

-- 큰 UPDATE
UPDATE orders SET notes = 'x' WHERE 1=1;
-- 로그 확인: ADM5500W lock escalation
```

---

## 더 읽어볼 자료

- 🔗 MySQL InnoDB Locks: <https://dev.mysql.com/doc/refman/8.4/en/innodb-locking.html>
- 🔗 DB2 Lock Modes: <https://www.ibm.com/docs/en/db2/11.5?topic=management-lock-modes>
- 📘 『High Performance MySQL』 Ch. 1 (Transactions, Concurrency)

---

## 자가 점검

- [ ] S/X 잠금의 호환성 매트릭스
- [ ] DB2의 lock escalation 메커니즘과 회피
- [ ] InnoDB의 row / gap / next-key lock 차이
- [ ] REPEATABLE READ가 InnoDB에서 phantom을 막는 메커니즘 (next-key)
- [ ] `SELECT ... FOR UPDATE NOWAIT` / `SKIP LOCKED` 활용
- [ ] 운영서 `LOCKTIMEOUT = -1`이 위험한 이유
- [ ] WHERE 컬럼에 인덱스 없으면 잠금 범위가 커지는 이유

다음: [`03_deadlock_diagnose.md`](03_deadlock_diagnose.md)
