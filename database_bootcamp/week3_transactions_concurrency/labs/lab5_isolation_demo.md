# Lab 5 — 격리수준 시연

두 세션을 동시에 열고 양쪽 DB로 격리수준 4단계 × 이상 현상 4가지를 모두 시연하고 매트릭스 작성.

## 환경

DBeaver 또는 두 개의 터미널.

## 데이터

```sql
CREATE TABLE accounts (id INT PRIMARY KEY, balance DECIMAL(10,2));
INSERT INTO accounts VALUES (1, 1000), (2, 500), (3, 200);
```

---

## 매트릭스 채우기

각 셀에 "발생/방지" 기록.

| | UR/READ_UNCOMMITTED | CS/READ_COMMITTED | RS/(InnoDB)REPEATABLE_READ | RR/SERIALIZABLE |
|---|---|---|---|---|
| **Dirty Read** | DB2: ? / MySQL: ? | | | |
| **Non-Repeatable** | | | | |
| **Phantom** | | | | |
| **Lost Update** | | | | |

---

## 실험 절차 (각 셀)

### 격리수준 설정

```sql
-- MySQL 세션
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT @@SESSION.transaction_isolation;

-- DB2 세션
SET CURRENT ISOLATION = CS;
VALUES CURRENT ISOLATION;
```

### Dirty Read 시도

```sql
-- 세션 1
START TRANSACTION;
UPDATE accounts SET balance = 9999 WHERE id = 1;
-- 커밋 X

-- 세션 2
SELECT balance FROM accounts WHERE id = 1;
-- 9999면 dirty 발생, 1000이면 방지

-- 세션 1
ROLLBACK;
```

### Non-Repeatable Read 시도

```sql
-- 세션 1
START TRANSACTION;
SELECT balance FROM accounts WHERE id = 1;     -- 1000

-- 세션 2 (별도)
START TRANSACTION;
UPDATE accounts SET balance = 500 WHERE id = 1;
COMMIT;

-- 세션 1 (같은 트랜잭션)
SELECT balance FROM accounts WHERE id = 1;
-- 1000 → 방지 / 500 → 발생
COMMIT;
```

### Phantom Read 시도

```sql
-- 세션 1
START TRANSACTION;
SELECT COUNT(*) FROM accounts;     -- 3

-- 세션 2
START TRANSACTION;
INSERT INTO accounts VALUES (4, 100);
COMMIT;

-- 세션 1
SELECT COUNT(*) FROM accounts;
-- 3 → 방지 / 4 → 발생
COMMIT;
```

### Lost Update 시도

```sql
-- 두 세션 동시에
-- 세션 1
START TRANSACTION;
SELECT balance FROM accounts WHERE id = 1;     -- 1000

-- 세션 2
START TRANSACTION;
SELECT balance FROM accounts WHERE id = 1;     -- 1000

-- 세션 1
UPDATE accounts SET balance = 1000 - 100 WHERE id = 1;
COMMIT;

-- 세션 2
UPDATE accounts SET balance = 1000 - 50 WHERE id = 1;
COMMIT;

-- 결과 확인
SELECT balance FROM accounts WHERE id = 1;
-- 850이 정상 — 만약 950이면 Lost Update
```

> ⚠ Lost Update는 모든 격리수준에서 위처럼 "직접 값 설정" 패턴이면 발생. `balance = balance - 100` 식 atomic UPDATE는 방지.

---

## 결과 표 (예시 — 실제로 양쪽 직접 해보기)

| | UR | CS | RS / RR(InnoDB) | RR/Serializable |
|---|---|---|---|---|
| **Dirty Read** | 발생 | 방지 | 방지 | 방지 |
| **Non-Repeatable** | 발생 | 발생 | 방지 | 방지 |
| **Phantom** | 발생 | 발생 | (InnoDB:방지, 표준:발생) | 방지 |
| **Lost Update (직접)** | 발생 | 발생 | 발생 | 방지 (또는 락대기/롤백) |

---

## 추가 실험

### 5분 LongRunning 트랜잭션의 영향

```sql
-- 세션 1: 트랜잭션 열고 가만히
START TRANSACTION;
SELECT * FROM accounts;
-- 5분 대기 (sleep 또는 그냥 두기)

-- 세션 2: 그동안 UPDATE 반복
UPDATE accounts SET balance = balance + 0.01 WHERE id = 1;
-- 100회 반복

-- History list length 측정 (MySQL)
SHOW ENGINE INNODB STATUS\G

-- 세션 1
COMMIT;

-- 측정 다시
SHOW ENGINE INNODB STATUS\G
-- History list length 감소
```

### DB2 currently committed 비교

```sql
UPDATE DB CFG FOR labdb USING CUR_COMMIT OFF;
-- 재연결

-- 세션 1: UPDATE 미커밋
-- 세션 2: SELECT
-- OFF면 잠금 대기, ON이면 옛 버전 즉시
```

---

## 회고

- 본인 운영 코드의 격리수준은? (`@Transactional(isolation = ...)` 명시 안 했으면 DB 기본값 = MySQL은 RR, DB2는 CS)
- 명시적 잠금(`SELECT ... FOR UPDATE`) 또는 낙관적 잠금 없이 격리수준에만 의존하는 코드가 있는가?
- 가장 흔한 동시성 이상 현상은? (보통 lost update)

다음: [`lab6_deadlock_reproduce.md`](lab6_deadlock_reproduce.md)
