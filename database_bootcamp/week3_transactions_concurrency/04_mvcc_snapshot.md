# Day 4 — MVCC · 스냅샷 격리

## 한 줄 요약

**MVCC (Multi-Version Concurrency Control)** 는 같은 행의 **여러 버전을 동시에 유지**해, "읽기는 쓰기를 막지 않고, 쓰기는 읽기를 막지 않는다(Readers don't block writers, writers don't block readers)" 를 실현한다. InnoDB는 **undo log** 로, DB2는 **currently committed** 메커니즘으로 구현. 이 덕분에 격리수준이 높아도 읽기 성능이 유지된다.

## 학습 목표

- [ ] 잠금 기반 격리 vs MVCC 기반 격리 차이
- [ ] InnoDB MVCC 동작 (undo log, read view, hidden columns)
- [ ] DB2 Currently Committed 동작
- [ ] MVCC의 비용 (undo log 크기, vacuum/cleanup)
- [ ] **Long-running transaction**의 위험

---

## 1. 잠금 기반 vs MVCC

### 잠금 기반 (전통 SQL Server 등)

```
T1: SELECT * FROM accounts WHERE id = 1     -- S lock
T2: UPDATE accounts SET balance = ... WHERE id = 1   -- X 요청 → 대기
```

→ 읽기와 쓰기가 서로 막음. 동시성 낮음.

### MVCC (InnoDB, PostgreSQL, Oracle, DB2)

```
T1: SELECT * FROM accounts WHERE id = 1    -- 트랜잭션 시작 시점의 버전 읽음
T2: UPDATE accounts SET balance = ... WHERE id = 1   -- 새 버전 생성, 즉시 진행

  data:
    v1 (balance=1000) ← T1이 보는 버전
    v2 (balance=500)  ← T2가 만든 새 버전, 다른 트랜잭션도 볼 수 있음
```

→ **읽기는 쓰기를 막지 않음**. 동시성 높음.

---

## 2. InnoDB MVCC 구현

### Hidden Columns

각 행에 (사용자가 보지 않는) 두 컬럼 자동:

| 컬럼 | 의미 |
|---|---|
| `DB_TRX_ID` | 마지막 변경한 트랜잭션 ID (6B) |
| `DB_ROLL_PTR` | undo log 포인터 (7B) |

### Undo Log

```
UPDATE accounts SET balance = 500 WHERE id = 1;
-- 1. 현재 행 (balance=1000)을 undo log로 복사
-- 2. 행을 in-place 갱신 (balance=500, trx_id=새값, roll_ptr=undo log 위치)
-- 3. 다른 트랜잭션의 "옛 버전 읽기" 요청 시 roll_ptr 따라 undo log에서 복원
```

### Read View

각 트랜잭션 시작 시 (REPEATABLE READ) 또는 각 쿼리 시작 시 (READ COMMITTED) **Read View** 생성:

```
Read View {
    trx_ids_active: [101, 105, 108]    // 활성 트랜잭션들
    low_limit_id: 110                   // 그 이후 트랜잭션은 미래 → 안 보임
    creator_id: 109                     // 본인
}
```

행을 읽을 때:
- 행의 `trx_id`가 **나보다 작고 + 활성 목록에 없으면** → 보임
- 그렇지 않으면 → undo log 따라 옛 버전 찾음

### 효과

- **읽기는 잠금 없이** 적절한 버전을 가져옴
- 같은 트랜잭션 안에서 같은 쿼리는 **같은 결과** (REPEATABLE READ)

---

## 3. DB2 Currently Committed

DB2의 MVCC는 **Currently Committed** (10.1+) 라는 이름.

### 동작

```
T1: UPDATE accounts SET balance = 500 WHERE id = 1     -- 미커밋
T2: SELECT balance FROM accounts WHERE id = 1
  → 잠금 대기 (옛 동작)?
  → "currently committed" 가 활성화면: undo log의 마지막 커밋 버전(1000) 즉시 반환
```

### 활성화

```sql
-- 데이터베이스 레벨
UPDATE DB CFG FOR labdb USING CUR_COMMIT ON;

-- 확인
SELECT VALUE FROM SYSIBMADM.DBCFG WHERE NAME = 'cur_commit';
```

기본: 10.1+ 새 DB에서 ON, 마이그레이션 DB는 확인 필요.

### 효과

- 읽기가 쓰기 잠금에서 즉시 풀려남
- READ COMMITTED (CS) 격리에서 특히 효과적

---

## 4. MVCC의 비용

### Undo Log 크기

- 매 UPDATE/DELETE마다 옛 버전을 undo log로 복사
- 긴 트랜잭션이 있으면 그 트랜잭션의 read view를 위해 undo log를 **계속 유지**해야 함

### Long-Running Transaction 위험

```sql
-- 세션 1: 분석 트랜잭션 (1시간 실행)
START TRANSACTION;
SELECT * FROM huge_table WHERE ...;     -- 보고서 생성, 천천히

-- 그 사이 다른 세션들의 UPDATE는 모두 undo log에 옛 버전 보존
-- undo log가 폭주 → 디스크 가득 → 성능 저하
```

### MySQL 진단

```sql
SHOW ENGINE INNODB STATUS\G
-- "History list length" — 클수록 미회수 옛 버전 누적
```

```sql
-- 1000 이상은 주목, 10만 이상은 위험
```

### Purge (정리)

InnoDB의 purge 스레드가 더 이상 필요 없는 옛 버전을 정리. 하지만 활성 트랜잭션이 보고 있으면 못 지움.

### 해결

```sql
-- 1. 긴 트랜잭션 식별
SELECT * FROM information_schema.innodb_trx ORDER BY trx_started LIMIT 10;

-- 2. KILL
KILL <thread_id>;

-- 3. 예방
-- 분석 쿼리는 read replica로
-- 보고서 트랜잭션도 빨리 끝내기
-- innodb_lock_wait_timeout, 임계치 모니터링
```

---

## 5. 실험으로 확인

### "읽기는 쓰기를 막지 않음"

```sql
-- 두 세션 모두 InnoDB / DB2 (cur_commit ON)

-- 세션 1
START TRANSACTION;
UPDATE accounts SET balance = 500 WHERE id = 1;
-- 커밋 안 함

-- 세션 2
SELECT balance FROM accounts WHERE id = 1;
-- → 1000 (옛 커밋 버전)   ← 즉시 응답, 잠금 없음

-- 세션 1
COMMIT;

-- 세션 2
SELECT balance FROM accounts WHERE id = 1;
-- → 500 (READ COMMITTED) 또는 (REPEATABLE READ에서 같은 트랜잭션이면) 1000
```

### "각 트랜잭션의 시점 일관성"

```sql
-- MySQL REPEATABLE READ
-- 세션 1
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION;
SELECT balance FROM accounts WHERE id = 1;     -- 1000

-- 세션 2
UPDATE accounts SET balance = 500 WHERE id = 1;
COMMIT;

-- 세션 1 (같은 트랜잭션)
SELECT balance FROM accounts WHERE id = 1;     -- 여전히 1000 (스냅샷 유지)
```

---

## 6. PostgreSQL의 MVCC (비교 학습)

- InnoDB/DB2는 **in-place 갱신 + undo log**
- PostgreSQL은 **새 행 추가 + 옛 행 표시** (no in-place update)
- 결과적으로 PostgreSQL은 `VACUUM`이 필수 (옛 행 정리)

| | InnoDB | DB2 | PostgreSQL |
|---|---|---|---|
| 옛 버전 위치 | undo log | undo log | 같은 테이블 페이지 |
| 정리 | purge 스레드 | 자동 | VACUUM (자동/수동) |
| 갱신 비용 | undo log 쓰기 | undo log 쓰기 | 새 행 + 옛 행 마킹 |

---

## 7. ❌ / ✅

### Long-running 트랜잭션 만들지 말 것

```java
// ❌ 트랜잭션 열고 사용자 입력 대기
@Transactional
public void editAccount(Long id, ...) {
    Account a = repo.findById(id).get();
    // 사용자가 UI에서 폼 작성 — 10분 — 그동안 트랜잭션 열려있음
    a.setBalance(...);
    repo.save(a);
}

// ✅ 짧게 — 조회와 저장을 별도 트랜잭션
public AccountDto load(Long id) {
    return repo.findById(id).map(AccountDto::from).orElseThrow();
}

@Transactional
public void save(Long id, AccountDto dto) {
    Account a = repo.findByIdForUpdate(id);
    a.applyChanges(dto);   // 또는 낙관적 잠금
}
```

### 대량 SELECT를 트랜잭션 안에서 (분석)

```sql
-- ❌ 운영 DB에서 1시간짜리 SELECT
SELECT ... FROM huge_table JOIN ... GROUP BY ...
-- undo log 폭주

-- ✅ 옵션
-- 1. Read replica에서 실행
-- 2. ETL: 별도 분석 DB로
-- 3. 격리수준을 READ UNCOMMITTED (DB2: UR) — 일관성 trade-off
```

### 큰 트랜잭션 안 청크 처리

```java
// ❌
@Transactional
public void migrate() {
    for (Item i : repo.findAll()) {    // 1000만 행
        i.transform();
        repo.save(i);
    }
}

// ✅ 청크 + 트랜잭션 분리
public void migrate() {
    int page = 0;
    while (true) {
        List<Item> chunk = repo.findChunk(page, 1000);
        if (chunk.isEmpty()) break;
        processChunk(chunk);
        page++;
    }
}
@Transactional
void processChunk(List<Item> chunk) { ... }
```

---

## 8. 실습

### Step 1: undo log 영향 측정 (MySQL)

```sql
-- 세션 1: 긴 트랜잭션 (분석 시뮬레이션)
START TRANSACTION;
SELECT COUNT(*) FROM accounts;   -- 트랜잭션 활성화 + 첫 행 읽음

-- 세션 2: 다른 곳에서 빠른 UPDATE 반복
-- 1000회
UPDATE accounts SET balance = balance + 0.01 WHERE id = 1;
-- ...

-- History list length 측정
SHOW ENGINE INNODB STATUS\G
-- "History list length 1000" 같이 증가

-- 세션 1 COMMIT
COMMIT;

-- 다시 측정 — purge 후 줄어듦
SHOW ENGINE INNODB STATUS\G
```

### Step 2: long-running 트랜잭션 찾기

```sql
-- MySQL
SELECT trx_id, trx_started,
       TIMESTAMPDIFF(SECOND, trx_started, NOW()) AS duration_sec,
       trx_query
  FROM information_schema.innodb_trx
 ORDER BY trx_started;

-- DB2
SELECT APPLICATION_HANDLE, START_TIME, EXECUTION_ID,
       TIMESTAMPDIFF(2, CURRENT_TIMESTAMP - START_TIME) AS DURATION_SEC,
       STMT_TEXT
  FROM TABLE(MON_GET_UNIT_OF_WORK(NULL, -2)) U
  JOIN TABLE(MON_GET_ACTIVITY(NULL, -2)) A
       ON U.APPLICATION_HANDLE = A.APPLICATION_HANDLE
 ORDER BY START_TIME;
```

### Step 3: DB2 currently committed ON/OFF 비교

```sql
-- ON (기본 10.1+)
SELECT VALUE FROM SYSIBMADM.DBCFG WHERE NAME = 'cur_commit';

-- OFF
UPDATE DB CFG FOR labdb USING CUR_COMMIT OFF;
-- 세션 재연결

-- 실험
-- 세션 1: UPDATE 후 미커밋
-- 세션 2: SELECT
-- OFF: 대기, ON: 옛 커밋 버전 즉시
```

---

## 더 읽어볼 자료

- 📘 『Database Internals』 (Petrov) Ch. 5 (Transactions and Recovery)
- 🔗 MySQL InnoDB Multi-Versioning: <https://dev.mysql.com/doc/refman/8.4/en/innodb-multi-versioning.html>
- 🔗 DB2 Currently Committed: <https://www.ibm.com/docs/en/db2/11.5?topic=concurrency-currently-committed-semantics-improve-concurrency>

---

## 자가 점검

- [ ] 잠금 기반과 MVCC 기반 격리의 차이
- [ ] InnoDB의 undo log + read view 동작
- [ ] DB2 currently committed의 의미
- [ ] 긴 트랜잭션이 undo log를 누적시키는 이유
- [ ] History list length가 클 때의 위험
- [ ] 분석 쿼리는 운영 DB에서 짧게 또는 read replica로

다음: [`05_spring_transactional.md`](05_spring_transactional.md)
