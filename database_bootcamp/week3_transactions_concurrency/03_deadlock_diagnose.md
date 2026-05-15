# Day 3 — 데드락 진단·회피

## 한 줄 요약

데드락은 **순환 대기**(A가 B를 기다리고 B가 A를 기다림)이다. DB는 데드락을 자동 감지하고 한 트랜잭션을 **희생자(victim)** 로 롤백한다. 운영에서 데드락 자체는 죄가 아니다 — **데드락 빈도가 높거나, 매번 같은 데드락이 반복**되면 코드 또는 인덱스 문제다.

## 학습 목표

- [ ] 데드락의 정의와 발생 조건
- [ ] DB2와 MySQL의 데드락 자동 감지
- [ ] 데드락 정보 추출 명령
- [ ] 데드락 회피 5가지 패턴 (잠금 순서·짧은 트랜잭션·인덱스·재시도·낙관적 잠금)
- [ ] 의도적 데드락 재현

---

## 1. 데드락이란

```
T1: id=1 잠금 → id=2 요청
T2: id=2 잠금 → id=1 요청
       ↓
   서로 무한 대기 = Deadlock
```

DB는 주기적으로 (또는 매 lock wait) **wait-for graph**를 검사. 사이클 발견 → 한 트랜잭션을 골라 **롤백 + 에러 반환**.

### DB2

```
SQL0911N The current transaction has been rolled back because of a deadlock or timeout.
Reason code "2"   ← 2 = deadlock (68 = timeout)
```

### MySQL

```
ERROR 1213 (40001): Deadlock found when trying to get lock; try restarting transaction.
```

> 두 DB 모두 **희생자 선택은 자동**. 보통 "건드린 행이 적은" 또는 "롤백 비용이 작은" 트랜잭션이 선택됨.

---

## 2. 의도적 재현

### 두 세션 준비. 양쪽 동일 절차.

```sql
-- 데이터
INSERT INTO accounts VALUES (1, 1000), (2, 500);
```

### 시나리오 A: 순서 다른 UPDATE

```sql
-- 세션 1
START TRANSACTION;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
-- id=1 X lock

-- 세션 2
START TRANSACTION;
UPDATE accounts SET balance = balance - 50 WHERE id = 2;
-- id=2 X lock

-- 세션 1 (계속)
UPDATE accounts SET balance = balance + 100 WHERE id = 2;
-- 세션 2의 id=2 잠금 대기...

-- 세션 2
UPDATE accounts SET balance = balance + 50 WHERE id = 1;
-- 세션 1의 id=1 잠금 대기 → Deadlock 감지

-- → 한 세션이 자동 ROLLBACK + 에러
```

### 시나리오 B: 같은 인덱스 페이지에 다른 행 (gap lock)

```sql
-- 세션 1
START TRANSACTION;
SELECT * FROM accounts WHERE id BETWEEN 1 AND 5 FOR UPDATE;
-- next-key lock on (1..5)

-- 세션 2
INSERT INTO accounts (id, ...) VALUES (3, ...);
-- 대기 (gap lock)

-- 세션 1
INSERT INTO accounts (id, ...) VALUES (4, ...);
-- (다른 시나리오 결합 따라) 데드락 발생 가능
```

---

## 3. 데드락 정보 추출

### MySQL

```sql
-- 가장 최근 데드락 (한 개만 보관)
SHOW ENGINE INNODB STATUS\G

-- 출력의 "LATEST DETECTED DEADLOCK" 섹션
-- *** (1) TRANSACTION:
-- ... (잡고 있는 락, 기다리는 락, SQL)
-- *** (2) TRANSACTION:
-- *** WE ROLL BACK TRANSACTION (2)
```

### MySQL — 모든 데드락을 에러 로그에

```sql
SET GLOBAL innodb_print_all_deadlocks = ON;
-- /var/log/mysql/error.log 등에 매번 기록됨
```

### performance_schema

```sql
SELECT * FROM performance_schema.events_transactions_history_long
 WHERE state = 'ROLLED BACK'
 ORDER BY timer_start DESC LIMIT 5;
```

### DB2

```bash
# 데드락 이벤트 모니터 (한 번만 설정)
db2 "CREATE EVENT MONITOR DLMON FOR DEADLOCKS WRITE TO TABLE"
db2 "SET EVENT MONITOR DLMON STATE = 1"

# 발생 시 SYSIBMADM.LOCK_PARTICIPANT 등에 기록
db2 "SELECT * FROM SYSIBMADM.LOCK_PARTICIPANT_ACTIVITIES"
db2 "SELECT * FROM SYSIBMADM.LOCK_PARTICIPANTS"
db2 "SELECT * FROM SYSIBMADM.LOCK_EVENT"
```

### Java/Spring에서 잡기

```java
// Spring 6+
try {
    repository.transferMoney(from, to, amount);
} catch (CannotAcquireLockException | DeadlockLoserDataAccessException e) {
    // 자동 분류 (Spring이 SQLState 기반으로 변환)
    log.warn("Deadlock detected, retrying...");
    // 재시도
}
```

---

## 4. 회피 패턴 5가지

### 1) 잠금 순서 일관성

```java
// ❌ 호출자마다 순서 다름
public void transfer(Long from, Long to, BigDecimal amt) {
    Account a = repo.findByIdForUpdate(from);   // 어떤 호출은 from=1, to=2
    Account b = repo.findByIdForUpdate(to);     // 다른 호출은 from=2, to=1 → 데드락
    ...
}

// ✅ 항상 작은 ID 먼저
public void transfer(Long from, Long to, BigDecimal amt) {
    Long first = Math.min(from, to);
    Long second = Math.max(from, to);
    Account a1 = repo.findByIdForUpdate(first);
    Account a2 = repo.findByIdForUpdate(second);
    ...
}
```

### 2) 짧은 트랜잭션

```java
// ❌ 트랜잭션 안에서 외부 호출, 사용자 입력 대기
@Transactional
public void buy(Long userId, Long productId) {
    Product p = repo.findByIdForUpdate(productId);
    paymentApi.charge(userId, p.getPrice());     // 5초
    p.decreaseStock();
}

// ✅ 결제 먼저, 짧은 트랜잭션 안에서만 잠금
public void buy(Long userId, Long productId) {
    Product p = repo.findById(productId).orElseThrow();
    paymentApi.charge(userId, p.getPrice());     // 트랜잭션 밖
    decreaseStock(productId);
}
@Transactional
void decreaseStock(Long productId) {
    Product p = repo.findByIdForUpdate(productId);
    p.decreaseStock();
}
```

### 3) 인덱스로 잠금 범위 좁히기

```sql
-- ❌ WHERE 컬럼에 인덱스 없음 → 풀스캔 → 광범위 잠금
UPDATE orders SET notes = 'x' WHERE customer_id = 42;
-- customer_id에 인덱스 없으면 모든 행에 일시 락

-- ✅
CREATE INDEX idx_orders_customer ON orders(customer_id);
```

### 4) 재시도 로직

데드락은 **재시도하면 보통 성공**한다. 트랜잭션 단위로 try-catch.

```java
// Spring Retry
@Retryable(
    retryFor = { DeadlockLoserDataAccessException.class, CannotAcquireLockException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 100, multiplier = 2)
)
@Transactional
public void transfer(Long from, Long to, BigDecimal amt) {
    ...
}
```

또는 직접:

```java
for (int i = 0; i < 3; i++) {
    try {
        return repository.transfer(from, to, amt);
    } catch (DeadlockLoserDataAccessException e) {
        if (i == 2) throw e;
        Thread.sleep(100L * (1L << i));   // 지수 백오프
    }
}
```

### 5) 낙관적 잠금 (Optimistic Locking)

데드락 자체를 회피. 충돌이 드물면 가장 좋은 패턴.

```java
@Entity
public class Account {
    @Id Long id;
    BigDecimal balance;
    @Version Long version;
}

// JPA가 UPDATE ... WHERE id = ? AND version = ? 자동 생성
// version 불일치 → OptimisticLockException
```

---

## 5. 데드락 분석 — Innodb Status 읽기

```
*** (1) TRANSACTION:
TRANSACTION 12345, ACTIVE 5 sec starting index read
mysql tables in use 1, locked 1
LOCK WAIT 3 lock struct(s), heap size 1136, 2 row lock(s)
MySQL thread id 100, OS thread handle ..., query id ...
UPDATE accounts SET balance = balance - 100 WHERE id = 2

*** (1) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id ... index PRIMARY of table `labdb`.`accounts`
trx id 12345 lock_mode X locks rec but not gap waiting

*** (2) TRANSACTION:
TRANSACTION 12346, ...
UPDATE accounts SET balance = balance - 50 WHERE id = 1

*** (2) HOLDS THE LOCK(S):
... lock_mode X on id=2

*** (2) WAITING FOR THIS LOCK TO BE GRANTED:
... lock_mode X on id=1

*** WE ROLL BACK TRANSACTION (2)
```

### 읽는 순서

1. 어느 SQL 두 개가 충돌?
2. 어느 행/인덱스에서?
3. 어느 잠금 모드?
4. WHO holds, WHO waits, WHO rolled back

이걸 보고 코드의 트랜잭션 두 개를 찾아 잠금 순서 또는 인덱스 점검.

---

## 6. 실제 사례

### 사례 1: 같은 테이블 다른 행 데드락 (가장 흔함)

```java
// 결제 처리 코드 — 호출 순서 무작위
@Transactional
public void payment(Long buyerId, Long sellerId, BigDecimal amount) {
    Account buyer = repo.findByIdForUpdate(buyerId);
    Account seller = repo.findByIdForUpdate(sellerId);
    buyer.withdraw(amount);
    seller.deposit(amount);
}
```

운영 중 가끔 데드락 → 잠금 순서 일관성 적용.

### 사례 2: 인덱스 없는 UPDATE → gap lock 누적

```java
@Transactional
public void resetExpired() {
    jdbc.update("UPDATE tokens SET valid = false WHERE expires_at < ?", Instant.now());
    // expires_at 인덱스 없음
}
```

다른 트랜잭션이 INSERT 시도하면 gap lock으로 데드락. → expires_at 인덱스 추가.

### 사례 3: 부모-자식 동시 갱신

```sql
-- 트랜잭션 1: 부모 갱신 후 자식 INSERT
UPDATE orders SET total = ? WHERE id = 1;
INSERT INTO order_items (order_id, ...) VALUES (1, ...);

-- 트랜잭션 2: 자식 갱신 후 부모 갱신
UPDATE order_items SET quantity = ? WHERE order_id = 1;
UPDATE orders SET total = ? WHERE id = 1;
```

FK lock + 일반 lock이 교차하여 데드락. → 순서 통일.

---

## 7. 실습

### Step 1: 시나리오 A 직접

위 §2 시나리오 A를 양쪽 DB로 재현. 에러 메시지 확인.

### Step 2: 정보 추출

```sql
-- MySQL
SHOW ENGINE INNODB STATUS\G
-- LATEST DETECTED DEADLOCK 섹션 스크린샷

-- DB2
db2 "SELECT * FROM SYSIBMADM.LOCK_EVENT FETCH FIRST 5 ROWS ONLY"
```

### Step 3: 회피 적용

```java
// 잠금 순서 일관성 패턴을 적용한 transfer 메서드 작성
// 그리고 동시 100 스레드로 무작위 from/to 호출 — 데드락 빈도 측정
```

### Step 4: 재시도

```java
// Spring Retry 활성화 + transfer에 @Retryable
// 의도적 데드락 발생 시 자동 재시도 로그 확인
```

### Step 5: 낙관적 잠금

```java
@Entity
class Account {
    @Id Long id;
    BigDecimal balance;
    @Version Long version;
}

// 동시 100 스레드로 transfer 시도
// OptimisticLockException 빈도 측정
// 어느 패턴이 throughput 더 좋은가?
```

---

## 더 읽어볼 자료

- 🔗 MySQL InnoDB Deadlocks: <https://dev.mysql.com/doc/refman/8.4/en/innodb-deadlocks.html>
- 🔗 DB2 Deadlock event monitor: <https://www.ibm.com/docs/en/db2/11.5?topic=monitors-deadlock>
- 📘 『Designing Data-Intensive Applications』 Ch. 7

---

## 자가 점검

- [ ] 데드락의 정의 (순환 대기)
- [ ] DB가 자동 감지·롤백한다는 사실
- [ ] MySQL SHOW ENGINE INNODB STATUS 출력 해석
- [ ] DB2 SYSIBMADM.LOCK_EVENT 활용
- [ ] 회피 5패턴 (순서·짧은 트랜잭션·인덱스·재시도·낙관적)
- [ ] 데드락 발생 → 재시도가 정상 응답인 이유

다음: [`04_mvcc_snapshot.md`](04_mvcc_snapshot.md)
