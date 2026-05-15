# Day 1 — ACID · 격리수준 4가지

## 한 줄 요약

ACID는 트랜잭션의 4가지 보장(원자성·일관성·고립성·지속성). 그 중 **고립성(Isolation)** 만 격리수준으로 조절 가능하고, 4단계마다 허용되는 동시성 현상이 다르다. **dirty read · non-repeatable read · phantom · lost update** 4가지를 양쪽 DB에서 직접 재현하면 추상이 구체가 된다.

## 학습 목표

- [ ] ACID 4가지 보장의 의미
- [ ] SQL 표준의 4가지 격리수준
- [ ] 4가지 동시성 이상 현상(anomalies)
- [ ] 격리수준 × 이상 현상 매트릭스
- [ ] DB2 vs MySQL의 **기본 격리수준** 차이
- [ ] 직접 재현: dirty read, non-repeatable read, phantom

---

## 1. ACID

| | 의미 |
|---|---|
| **A**tomicity | 트랜잭션은 전부 성공하거나 전부 실패 (롤백) |
| **C**onsistency | 무결성 제약 항상 유지 (FK, CHECK, 트리거) |
| **I**solation | 동시 실행되는 트랜잭션끼리 서로 안 보이게 |
| **D**urability | 커밋된 변경은 시스템 장애 후에도 유지 |

### 구현 방법 (요약)

- **Atomicity**: undo log (롤백 데이터)
- **Durability**: redo log + WAL (Write-Ahead Log)
- **Consistency**: 제약 조건 + 트리거
- **Isolation**: 잠금 또는 MVCC

> 💡 **A와 D는 항상 보장**. **C는 스키마에 따라**. **I는 격리수준으로 조절 가능**.

---

## 2. 동시성 이상 현상 (Anomalies)

### Dirty Read (오염 읽기)

```
T1: UPDATE balance = 500     (커밋 안 됨)
T2: SELECT balance           → 500   ← T1이 롤백되면 잘못된 값
T1: ROLLBACK
```

> T1의 미커밋 데이터를 T2가 봄.

### Non-Repeatable Read (반복 불가능한 읽기)

```
T1: SELECT balance           → 1000
T2: UPDATE balance = 500
T2: COMMIT
T1: SELECT balance           → 500   ← 같은 트랜잭션 안에서 같은 쿼리가 다른 값
```

> T2의 커밋된 변경을 T1이 같은 트랜잭션 안에서 봄.

### Phantom Read (유령 읽기)

```
T1: SELECT COUNT(*) FROM orders WHERE customer_id = 42   → 5
T2: INSERT INTO orders (customer_id, ...) VALUES (42, ...)
T2: COMMIT
T1: SELECT COUNT(*) FROM orders WHERE customer_id = 42   → 6   ← 새 행 출현
```

> T2의 INSERT가 T1의 쿼리 결과 셋을 바꿈.

### Lost Update (분실된 갱신, 비표준 — REPEATABLE READ에서도 발생 가능)

```
T1: SELECT balance           → 1000
T2: SELECT balance           → 1000
T1: UPDATE balance = 1000-100  → 900
T1: COMMIT
T2: UPDATE balance = 1000-50   → 950   ← T1의 변경이 사라짐
T2: COMMIT
```

> 마지막 writer wins. 첫 트랜잭션의 변경이 분실됨.

---

## 3. 격리수준 4단계

| 격리수준 | Dirty Read | Non-Repeatable Read | Phantom Read |
|---|---|---|---|
| READ UNCOMMITTED | 허용 | 허용 | 허용 |
| **READ COMMITTED** | 방지 | 허용 | 허용 |
| **REPEATABLE READ** | 방지 | 방지 | 허용 (표준), 방지 (InnoDB) |
| SERIALIZABLE | 방지 | 방지 | 방지 |

> 표준 SQL은 위와 같지만 **실제 구현은 DB마다 다름**. InnoDB의 REPEATABLE READ는 사실상 SERIALIZABLE에 가깝게 phantom도 막는다(next-key lock).

### 격리수준 설정

```sql
-- 표준 (둘 다)
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- DB2 (CS = Cursor Stability = Read Committed)
SET CURRENT ISOLATION = CS;
-- UR = Uncommitted Read, CS, RS = Read Stability, RR = Repeatable Read
```

### DB2 격리수준 명칭

| 표준 | DB2 |
|---|---|
| READ UNCOMMITTED | **UR** (Uncommitted Read) |
| READ COMMITTED | **CS** (Cursor Stability) — **기본** |
| REPEATABLE READ | **RS** (Read Stability) |
| SERIALIZABLE | **RR** (Repeatable Read) — 이름 헷갈림 주의 |

> ⚠ DB2 "RR"이 SQL 표준 "REPEATABLE READ"가 아니라 "SERIALIZABLE"에 해당. **이름 충돌이 매우 헷갈림**. DB2 문서에서는 항상 CS, RS, RR 약자 사용 권장.

### 기본 격리수준

| | 기본 |
|---|---|
| DB2 | **CS** (= Read Committed) |
| MySQL InnoDB | **REPEATABLE READ** |
| PostgreSQL (참고) | Read Committed |
| Oracle (참고) | Read Committed |

> InnoDB만 REPEATABLE READ가 기본. 다른 DB와 다르니 주의.

---

## 4. DB2와 MySQL 격리수준 매핑

### DB2

```sql
-- 세션 격리수준
SET CURRENT ISOLATION = CS;        -- Read Committed
SET CURRENT ISOLATION = RS;        -- Read Stability (REPEATABLE READ 일부)
SET CURRENT ISOLATION = RR;        -- Serializable

-- 쿼리별 격리수준 (Isolation Clause)
SELECT * FROM accounts WITH UR;    -- 이 쿼리만 Uncommitted Read
SELECT * FROM accounts WITH RR;    -- 이 쿼리만 Serializable
```

### MySQL

```sql
-- 세션
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- 글로벌
SET GLOBAL TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- 한 트랜잭션
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
START TRANSACTION;
...
COMMIT;

-- 확인
SELECT @@SESSION.transaction_isolation;
SELECT @@GLOBAL.transaction_isolation;
```

---

## 5. 양쪽 DB에서 직접 재현

### 두 세션 준비

```bash
# 터미널 1: MySQL
docker exec -it mysql-lab mysql -uroot -ppassw0rd labdb

# 터미널 2: MySQL (별도)
docker exec -it mysql-lab mysql -uroot -ppassw0rd labdb
```

또는 DBeaver에서 두 SQL 에디터.

### 데이터 준비

```sql
-- 양쪽에 (한 번만)
CREATE TABLE accounts (id INT PRIMARY KEY, balance DECIMAL(10,2));
INSERT INTO accounts VALUES (1, 1000.00), (2, 500.00);
```

---

### 실험 1: Dirty Read

#### MySQL READ UNCOMMITTED

```sql
-- 세션 1
SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
START TRANSACTION;
UPDATE accounts SET balance = 500 WHERE id = 1;
-- 아직 커밋 안 함

-- 세션 2
SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
SELECT balance FROM accounts WHERE id = 1;
-- → 500   ← Dirty Read

-- 세션 1
ROLLBACK;

-- 세션 2
SELECT balance FROM accounts WHERE id = 1;
-- → 1000  ← 원래 값
```

#### MySQL READ COMMITTED (기본 isolation level 4단계 중 하나, 또는 SET으로)

```sql
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
-- 위 실험 다시
-- 세션 2의 SELECT는 1000   ← Dirty Read 방지
```

#### DB2

```sql
-- 세션 1
CONNECT TO labdb;
SET CURRENT ISOLATION = UR;
UPDATE accounts SET balance = 500 WHERE id = 1;

-- 세션 2
SET CURRENT ISOLATION = UR;
SELECT balance FROM accounts WHERE id = 1;
-- 세션 1이 커밋 안 했지만 500 보임 (UR)

SET CURRENT ISOLATION = CS;
SELECT balance FROM accounts WHERE id = 1;
-- 1000 (CS는 dirty 안 봄, 그러나 세션 1 잠금에 따라 대기 또는 currently committed)
```

---

### 실험 2: Non-Repeatable Read

#### MySQL READ COMMITTED

```sql
-- 세션 1
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
SELECT balance FROM accounts WHERE id = 1;
-- → 1000

-- 세션 2
START TRANSACTION;
UPDATE accounts SET balance = 700 WHERE id = 1;
COMMIT;

-- 세션 1 (같은 트랜잭션)
SELECT balance FROM accounts WHERE id = 1;
-- → 700  ← Non-Repeatable Read 발생!
COMMIT;
```

#### MySQL REPEATABLE READ (InnoDB 기본)

```sql
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
-- 위 동일 시퀀스
-- 세션 1의 두 번째 SELECT → 1000 (스냅샷 유지)
```

---

### 실험 3: Phantom Read

#### MySQL REPEATABLE READ (InnoDB는 실제로는 막음!)

```sql
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION;
SELECT COUNT(*) FROM accounts WHERE balance > 100;
-- → 2

-- 세션 2
INSERT INTO accounts VALUES (3, 200);
COMMIT;

-- 세션 1
SELECT COUNT(*) FROM accounts WHERE balance > 100;
-- InnoDB: → 2 (스냅샷 유지)   ← Phantom 막힘
-- 표준 RR: → 3                ← Phantom 발생
```

> InnoDB의 REPEATABLE READ는 phantom도 막는다 — **MVCC + next-key lock** 조합으로. 다른 DB와 다른 점 주의.

#### DB2 RS vs RR

```sql
SET CURRENT ISOLATION = RS;
-- 같은 행은 안 바뀜, 그러나 INSERT는 들어옴 (phantom 가능)

SET CURRENT ISOLATION = RR;
-- phantom도 막음 (다른 트랜잭션이 매치되는 INSERT 시도하면 대기)
```

---

### 실험 4: Lost Update

```sql
-- 양쪽 READ COMMITTED
-- 세션 1
START TRANSACTION;
SELECT balance FROM accounts WHERE id = 1;     -- 1000
-- 100 차감 의도

-- 세션 2 (동시에)
START TRANSACTION;
SELECT balance FROM accounts WHERE id = 1;     -- 1000
-- 50 차감 의도

-- 세션 1
UPDATE accounts SET balance = 1000 - 100 WHERE id = 1;   -- 900
COMMIT;

-- 세션 2
UPDATE accounts SET balance = 1000 - 50 WHERE id = 1;    -- 950
COMMIT;

-- 결과: 950 (세션 1의 -100이 사라짐)
```

### 해결 1: 비관적 잠금 (SELECT FOR UPDATE)

```sql
START TRANSACTION;
SELECT balance FROM accounts WHERE id = 1 FOR UPDATE;     -- 행 잠금
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
COMMIT;
```

### 해결 2: 원자적 UPDATE

```sql
UPDATE accounts SET balance = balance - 100 WHERE id = 1 AND balance >= 100;
-- 영향받은 행 수 확인 → 0이면 잔액 부족
```

### 해결 3: 낙관적 잠금 (Versioning)

```sql
ALTER TABLE accounts ADD COLUMN version INT NOT NULL DEFAULT 0;

-- 읽기
SELECT balance, version FROM accounts WHERE id = 1;   -- (1000, 5)

-- 갱신
UPDATE accounts SET balance = balance - 100, version = version + 1
 WHERE id = 1 AND version = 5;
-- 영향 0이면 다른 트랜잭션이 먼저 갱신 → 재시도
```

JPA에서는 `@Version`:

```java
@Entity
public class Account {
    @Id Long id;
    BigDecimal balance;
    @Version Long version;
}
```

---

## 6. 격리수준 선택 가이드

### Read Committed (대부분의 OLTP)

- 짧은 트랜잭션
- Non-repeatable read를 비즈니스 로직으로 처리 (즉, 한 트랜잭션 안에서 같은 SELECT를 반복하지 않거나, 명시적 잠금)
- 잠금 적음, 성능 좋음

### Repeatable Read (분석·리포트, MySQL 기본)

- 한 트랜잭션 안에서 일관된 스냅샷 필요
- 대량 SELECT 후 가공
- InnoDB: 사실상 Serializable에 가까움 (phantom도 막힘)

### Serializable

- 금융 정산, 재고 차감
- 잠금·재시도 비용
- 일반적으로 비관적 잠금 + Read Committed 조합으로 대체

### Read Uncommitted

- **거의 안 씀**
- 대용량 리포트의 추정치 (정확성 무관)

---

## 7. ❌ 안티패턴

### 격리수준에 의존해 동시성 해결 시도

```java
// ❌ "REPEATABLE READ면 lost update 안 나겠지?"
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void transfer(Long from, Long to, BigDecimal amount) {
    Account a = repo.findById(from).get();
    a.setBalance(a.getBalance().subtract(amount));
    repo.save(a);
}
// REPEATABLE READ도 lost update 막지 못함 (InnoDB는 막지만 표준은 아님)

// ✅ 비관적 또는 낙관적 잠금
@Transactional
public void transfer(Long from, Long to, BigDecimal amount) {
    Account a = repo.findByIdForUpdate(from);   // SELECT ... FOR UPDATE
    a.setBalance(a.getBalance().subtract(amount));
}
```

### 긴 트랜잭션

```java
// ❌ 트랜잭션 안에서 외부 API 호출
@Transactional
public void process(Order o) {
    o.setStatus(SHIPPED);
    repo.save(o);
    paymentApi.charge(o);     // 5초 걸릴 수 있음 — 그동안 행 잠금
}

// ✅ 트랜잭션 분리
public void process(Order o) {
    paymentApi.charge(o);     // 잠금 없는 외부 호출
    markShipped(o);
}
@Transactional
void markShipped(Order o) {
    o.setStatus(SHIPPED);
    repo.save(o);
}
```

---

## 8. 실습

### Step 1: 4가지 현상 직접 재현

위 §5의 실험 1~4를 양쪽 DB로 모두 시도. 각 실험 후 다음 표 채우기:

| 실험 | DB2 CS | DB2 RR | MySQL RC | MySQL RR | MySQL SER |
|---|---|---|---|---|---|
| Dirty Read | | | | | |
| Non-Repeatable | | | | | |
| Phantom | | | | | |
| Lost Update | | | | | |

### Step 2: 격리수준 변경 후 잠금 동작 차이

```sql
-- 세션 1: SELECT FOR UPDATE
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
SELECT * FROM accounts WHERE id = 1 FOR UPDATE;

-- 세션 2: 같은 행 갱신 시도
UPDATE accounts SET balance = 999 WHERE id = 1;
-- → 대기

-- 세션 1
COMMIT;
-- 세션 2 → 진행
```

### Step 3: 낙관적 잠금 (JPA `@Version`)

```java
// Spring 앱에서 시도 — Week 3 Day 5에서 자세히
```

---

## 더 읽어볼 자료

- 📘 『Designing Data-Intensive Applications』 (Martin Kleppmann) Ch. 7 (Transactions)
- 🔗 PostgreSQL 격리수준 문서 (학습용으로 명확): <https://www.postgresql.org/docs/current/transaction-iso.html>
- 🔗 MySQL InnoDB Locking and Transaction Model: <https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-transaction-model.html>
- 🔗 DB2 Isolation Levels: <https://www.ibm.com/docs/en/db2/11.5?topic=issues-isolation-levels>

---

## 자가 점검

- [ ] ACID 4가지 의미를 한 줄씩 설명
- [ ] 4가지 격리수준과 4가지 이상 현상 매트릭스를 외운다
- [ ] DB2 기본 격리수준이 CS (Read Committed)
- [ ] MySQL InnoDB 기본 격리수준이 REPEATABLE READ (다른 DB와 다름)
- [ ] InnoDB의 RR이 phantom도 막는 이유 (next-key lock)
- [ ] Lost Update가 격리수준만으로 안 풀리는 이유
- [ ] DB2 RR과 SQL 표준 RR의 의미 차이 (DB2 RR = Serializable)

다음: [`02_locking_models.md`](02_locking_models.md)
