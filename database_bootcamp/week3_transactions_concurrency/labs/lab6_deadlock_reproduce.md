# Lab 6 — 데드락 재현 · 진단 · 회피

목표:

1. 양쪽 DB에서 데드락 의도적 발생
2. 진단 명령으로 원인 파악
3. 회피 패턴 적용 후 재시도 시 성공

---

## 1. 데이터 준비

```sql
CREATE TABLE accounts (id INT PRIMARY KEY, balance DECIMAL(10,2));
INSERT INTO accounts VALUES (1, 1000), (2, 500), (3, 200);
```

---

## 2. 데드락 시나리오 A: 순서 다른 UPDATE

### 두 세션

#### MySQL

```sql
-- 세션 1
START TRANSACTION;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;

-- 세션 2 (별도 connection)
START TRANSACTION;
UPDATE accounts SET balance = balance + 100 WHERE id = 2;

-- 세션 1 (계속)
UPDATE accounts SET balance = balance + 100 WHERE id = 2;
-- → 대기

-- 세션 2
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
-- → Deadlock 감지! 한 쪽이 즉시 롤백
-- ERROR 1213 (40001): Deadlock found
```

#### DB2

```sql
-- 세션 1
UPDATE accounts SET balance = balance - 100 WHERE id = 1;

-- 세션 2
UPDATE accounts SET balance = balance + 100 WHERE id = 2;

-- 세션 1
UPDATE accounts SET balance = balance + 100 WHERE id = 2;

-- 세션 2
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
-- SQL0911N reason code "2"
```

---

## 3. 진단

### MySQL

```sql
SHOW ENGINE INNODB STATUS\G
```

출력에서 `LATEST DETECTED DEADLOCK` 섹션 찾기. 두 트랜잭션의 SQL과 락 상태 확인.

다음 정보 메모:
- 어느 SQL 두 개가 충돌?
- 어느 행/인덱스에서?
- 누가 victim(롤백 대상)?

### DB2

```bash
# 데드락 이벤트 모니터 설정 (한 번)
db2 "CREATE EVENT MONITOR DLMON FOR LOCKING WRITE TO TABLE"
db2 "SET EVENT MONITOR DLMON STATE = 1"

# 이벤트 발생 후 조회
db2 "SELECT * FROM LOCK_EVENT FETCH FIRST 5 ROWS ONLY"
db2 "SELECT * FROM LOCK_PARTICIPANTS FETCH FIRST 10 ROWS ONLY"
db2 "SELECT * FROM LOCK_PARTICIPANT_ACTIVITIES FETCH FIRST 10 ROWS ONLY"
```

또는 db2diag.log 확인:
```bash
docker exec db2-lab tail -100 /database/config/db2inst1/sqllib/db2dump/db2diag.log | grep -A 20 deadlock
```

---

## 4. 회피 패턴 1 — 잠금 순서 일관성

### Java로 구현

```java
@Service
public class TransferService {
    @Transactional
    public void transfer(Long from, Long to, BigDecimal amount) {
        // 항상 작은 ID 먼저
        Long first = Math.min(from, to);
        Long second = Math.max(from, to);

        Account a1 = repo.findByIdForUpdate(first);
        Account a2 = repo.findByIdForUpdate(second);

        // 실제 from/to 구분해 차감/가산
        if (from.equals(first)) {
            a1.setBalance(a1.getBalance().subtract(amount));
            a2.setBalance(a2.getBalance().add(amount));
        } else {
            a2.setBalance(a2.getBalance().subtract(amount));
            a1.setBalance(a1.getBalance().add(amount));
        }
    }
}

public interface AccountRepo extends JpaRepository<Account, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Account findByIdForUpdate(@Param("id") Long id);
}
```

### 테스트

```java
@Test
void noDeadlock() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
        // 무작위 from/to
        Long from = ThreadLocalRandom.current().nextLong(1, 4);
        Long to = ThreadLocalRandom.current().nextLong(1, 4);
        if (from.equals(to)) continue;
        futures.add(pool.submit(() -> service.transfer(from, to, BigDecimal.ONE)));
    }
    for (Future<?> f : futures) f.get();
    // 데드락 없이 완료
}
```

---

## 5. 회피 패턴 2 — 재시도

```java
@Retryable(
    retryFor = {
        DeadlockLoserDataAccessException.class,
        CannotAcquireLockException.class
    },
    maxAttempts = 3,
    backoff = @Backoff(delay = 100, multiplier = 2)
)
@Transactional
public void transfer(Long from, Long to, BigDecimal amount) {
    // 위 로직
}

@Recover
public void recover(Exception e, Long from, Long to, BigDecimal amount) {
    log.error("Transfer failed after retries: {} -> {} ({})", from, to, amount);
    throw new TransferFailedException(e);
}
```

---

## 6. 회피 패턴 3 — 낙관적 잠금

```java
@Entity
public class Account {
    @Id Long id;
    BigDecimal balance;
    @Version Long version;
}

@Transactional
public void transfer(Long from, Long to, BigDecimal amount) {
    Account a = repo.findById(from).orElseThrow();
    Account b = repo.findById(to).orElseThrow();
    a.setBalance(a.getBalance().subtract(amount));
    b.setBalance(b.getBalance().add(amount));
    // commit 시 자동으로 UPDATE ... WHERE id=? AND version=?
    // 동시 충돌 → OptimisticLockingFailureException
}

@Retryable(
    retryFor = OptimisticLockingFailureException.class,
    maxAttempts = 5,
    backoff = @Backoff(delay = 50)
)
public void transferWithRetry(Long from, Long to, BigDecimal amount) {
    transfer(from, to, amount);
}
```

---

## 7. 비교 측정

| 패턴 | 데드락 빈도 | throughput | 응답 시간 |
|---|---|---|---|
| 기본 (재시도 X) | 높음 | 낮음 | 가변 (에러) |
| 잠금 순서 일관 | 0 | 높음 | 안정 |
| 비관적 + 재시도 | 낮음 | 중 | 가변 |
| 낙관적 + 재시도 | 0 (대신 옵티미스틱 충돌) | 높음 (낮은 충돌 시) | 가변 |

본인 환경에서 실측 후 표 채우기.

---

## 8. 회고

- 운영 코드에 데드락 빈도가 얼마인가? (로그에서 grep)
- 잠금 순서가 일관적인가? 아니라면 어디서 깨지는가?
- 낙관적 잠금이 가능한 엔티티는?

다음: [`../checklist.md`](../checklist.md)
