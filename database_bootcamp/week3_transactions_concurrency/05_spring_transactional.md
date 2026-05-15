# Day 5 — Spring `@Transactional` 마스터

## 한 줄 요약

`@Transactional`은 한 줄짜리 어노테이션이지만 **AOP 프록시**가 뒤에서 일하기에 함정이 많다. **자기 호출**, **rollbackFor 기본값**, **propagation 7가지**, **JPA·MyBatis·JDBC 각각의 트랜잭션 매니저**까지 알아야 운영 사고 안 친다.

## 학습 목표

- [ ] Spring 트랜잭션의 작동 원리 (AOP 프록시)
- [ ] `@Transactional`의 7가지 propagation
- [ ] `rollbackFor`의 기본값 함정 (RuntimeException만 롤백)
- [ ] **자기 호출(self-invocation)** 함정
- [ ] JPA·MyBatis·JDBC 한 트랜잭션에 묶기
- [ ] **읽기 전용** 트랜잭션 (`readOnly = true`)
- [ ] Spring Data JPA의 자동 트랜잭션

---

## 1. Spring 트랜잭션의 동작

```java
@Service
public class OrderService {

    @Transactional
    public void placeOrder(OrderDto dto) {
        ...
    }
}
```

내부:

```
컨테이너 시작 시 → AOP 프록시 생성
호출 시 → 프록시가 트랜잭션 시작 → 메서드 실행 → 정상이면 commit, RuntimeException이면 rollback

@Service ← (프록시) ← @Service의 실제 인스턴스
```

핵심: **프록시 메서드 호출**에서만 트랜잭션이 시작된다. 같은 클래스 안의 메서드 호출(`this.placeOrder()`)은 프록시를 안 거침.

---

## 2. 자기 호출 함정 (가장 흔한 사고)

```java
@Service
public class OrderService {

    public void placeOrder(OrderDto dto) {
        validate(dto);
        saveInternal(dto);     // ← 같은 클래스의 트랜잭션 메서드
    }

    @Transactional
    public void saveInternal(OrderDto dto) {
        ...
    }
}
```

> `saveInternal()`은 **트랜잭션 안 열림**. `this.saveInternal()`는 프록시 안 거침.

### 해결

```java
// 방법 1: 메서드를 별도 빈으로 분리
@Service
public class OrderService {
    private final OrderInternalService internal;
    public void placeOrder(OrderDto dto) {
        validate(dto);
        internal.saveInternal(dto);   // 다른 빈 호출 → 프록시 거침
    }
}

@Service
public class OrderInternalService {
    @Transactional
    public void saveInternal(OrderDto dto) { ... }
}

// 방법 2: AopContext (자기 프록시) — 비권장이지만 동작
@Service
public class OrderService {
    public void placeOrder(OrderDto dto) {
        validate(dto);
        ((OrderService) AopContext.currentProxy()).saveInternal(dto);
    }
}
// @EnableAspectJAutoProxy(exposeProxy = true) 필요

// 방법 3: TransactionTemplate
private final TransactionTemplate tx;
public void placeOrder(OrderDto dto) {
    validate(dto);
    tx.executeWithoutResult(status -> {
        // saveInternal 로직
    });
}
```

---

## 3. rollbackFor 기본값 함정

`@Transactional`은 기본으로 **`RuntimeException`과 `Error`만 롤백**. **Checked Exception은 롤백 안 함!**

```java
@Transactional
public void doSomething() throws IOException {
    repo.save(...);
    if (...) throw new IOException("oops");   // ❌ 커밋됨!
}
```

### 해결

```java
@Transactional(rollbackFor = Exception.class)   // 모든 예외 롤백
public void doSomething() throws IOException { ... }

// 또는 특정 예외만
@Transactional(rollbackFor = { IOException.class, BusinessException.class })

// 또는 noRollbackFor
@Transactional(noRollbackFor = NotFoundException.class)
```

> **권장**: 팀 표준으로 `@Transactional(rollbackFor = Exception.class)` 강제 또는 커스텀 어노테이션.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Transactional(rollbackFor = Exception.class)
public @interface TxOnException { }

// 사용
@TxOnException
public void doSomething() throws IOException { ... }
```

---

## 4. Propagation 7가지

호출되는 메서드의 트랜잭션 동작 결정.

| Propagation | 부모 트랜잭션 있을 때 | 부모 트랜잭션 없을 때 |
|---|---|---|
| **REQUIRED** (기본) | 합류 | 새로 시작 |
| **REQUIRES_NEW** | 부모 일시 정지, 새 트랜잭션 | 새로 시작 |
| **NESTED** | 부모 안 savepoint | 새로 시작 |
| **SUPPORTS** | 합류 | 트랜잭션 없이 |
| **NOT_SUPPORTED** | 부모 일시 정지, 트랜잭션 없이 | 트랜잭션 없이 |
| **MANDATORY** | 합류 | **예외!** |
| **NEVER** | **예외!** | 트랜잭션 없이 |

### 자주 쓰는 패턴

#### REQUIRED (기본) — 대부분의 경우

```java
@Transactional
public void parent() {
    child1();   // 같은 트랜잭션
    child2();   // 같은 트랜잭션
}
```

#### REQUIRES_NEW — 로그·감사·알림

```java
@Transactional
public void placeOrder(OrderDto dto) {
    orderRepo.save(...);
    auditService.log("order placed");   // 별도 트랜잭션 — 주문 실패해도 감사 로그는 남음
    throw new RuntimeException("oops");
    // orderRepo.save는 롤백, audit log는 살아남음
}

// auditService
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void log(String msg) { ... }
```

> ⚠ REQUIRES_NEW는 **새 DB 연결**을 잡음. 부모 트랜잭션은 일시 정지 (suspend). 풀이 작으면 데드락 위험 (부모가 자식 끝나길 기다리는데 풀에 자리 없음).

#### NESTED — savepoint

```java
@Transactional
public void process() {
    repo.save(a);
    try {
        nested();   // savepoint 설정
    } catch (Exception e) {
        // 자식만 롤백, 부모는 계속
    }
    repo.save(b);
}

@Transactional(propagation = Propagation.NESTED)
public void nested() { ... }
```

> JPA에서는 NESTED 지원이 제한적 (Hibernate가 자동으로 REQUIRES_NEW로 처리하는 경우). JDBC/MyBatis에서 더 잘 동작.

---

## 5. readOnly 트랜잭션

```java
@Transactional(readOnly = true)
public OrderDto getOrder(Long id) { ... }
```

효과:

- JPA: flush 안 함, dirty check 스킵 → 빠름
- DB 측 힌트 (DB2: `WITH UR` 비슷 효과, MySQL은 트랜잭션 자체는 동일하지만 옵티마이저 힌트)
- Read replica로 라우팅하는 도구가 있다면 활용

> **권장**: 모든 조회 메서드에 `readOnly = true`. 빠르고 안전.

---

## 6. JPA · MyBatis · JDBC 함께

### Spring Boot 기본

같은 DataSource를 쓰면 **하나의 트랜잭션**으로 묶임.

```java
@Service
public class OrderService {
    private final JpaRepo jpa;          // JPA
    private final OrderMapper mybatis;  // MyBatis
    private final JdbcTemplate jdbc;    // JDBC

    @Transactional
    public void mixedOps(Long id) {
        Order o = jpa.findById(id).get();
        mybatis.updateSomething(o.getId());
        jdbc.update("UPDATE ... WHERE id = ?", id);
        // 모두 같은 트랜잭션
    }
}
```

### JPA의 1차 캐시 함정

```java
@Transactional
public void issue() {
    Order o = jpaRepo.findById(1L).get();
    o.setStatus("PAID");
    // jpaRepo.save(o) 안 해도 OK — dirty check로 flush 시점에 UPDATE

    // 그러나 MyBatis로 SELECT하면 1차 캐시 무관 → 옛 데이터 봄
    Order fresh = mybatis.findById(1L);    // status="PENDING" (DB는 아직 안 변함!)

    // 해결: 명시적 flush
    em.flush();
    Order fresh2 = mybatis.findById(1L);   // status="PAID"
}
```

> **운영 흔한 사고**: JPA와 MyBatis 섞어 쓸 때 **1차 캐시 + 지연 flush** 때문에 한쪽이 다른 쪽의 변경을 못 보는 현상.

### 해결

- 같은 트랜잭션 안에서 JPA와 MyBatis를 섞을 때 **명시적 flush** 또는
- 트랜잭션을 분리 (REQUIRES_NEW) 또는
- 한 가지 ORM으로 통일

---

## 7. JPA의 N+1과 트랜잭션

```java
@Transactional(readOnly = true)
public List<OrderDto> list() {
    List<Order> orders = orderRepo.findAll();
    return orders.stream()
        .map(o -> new OrderDto(o.getId(), o.getCustomer().getName()))   // N번 추가 쿼리
        .toList();
}
```

각 `getCustomer()` 호출이 별도 SELECT → **N+1 쿼리**. 트랜잭션 안이라 가능하지만 (LazyInitializationException 안 남), 성능 폭망.

### 해결

```java
// fetch join
@Query("SELECT o FROM Order o JOIN FETCH o.customer")
List<Order> findAllWithCustomer();

// 또는 EntityGraph
@EntityGraph(attributePaths = "customer")
List<Order> findAll();

// 또는 별도 쿼리로 한 번에
```

> N+1은 트랜잭션 길어지고 DB 부담 ↑. Week 2의 EXPLAIN으로 본 잠금·인덱스 문제와 결합되면 대형 사고.

---

## 8. 락 + JPA — 비관적 vs 낙관적

### 비관적 (DB 잠금)

```java
public interface AccountRepo extends JpaRepository<Account, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Account findByIdForUpdate(@Param("id") Long id);
}

@Transactional
public void transfer(Long from, Long to, BigDecimal amt) {
    Long first = Math.min(from, to);
    Long second = Math.max(from, to);
    Account a = repo.findByIdForUpdate(first);
    Account b = repo.findByIdForUpdate(second);
    if (from < to) {
        a.withdraw(amt); b.deposit(amt);
    } else {
        b.deposit(amt); a.withdraw(amt);
    }
}
```

### 낙관적 (@Version)

```java
@Entity
public class Account {
    @Id Long id;
    BigDecimal balance;
    @Version Long version;
}

@Transactional
public void transfer(Long from, Long to, BigDecimal amt) throws RetryableException {
    Account a = repo.findById(from).get();
    Account b = repo.findById(to).get();
    a.withdraw(amt);
    b.deposit(amt);
    // commit 시 UPDATE ... WHERE id=? AND version=?
    // 다른 트랜잭션이 먼저 갱신하면 OptimisticLockException
}

// 재시도
@Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 5,
           backoff = @Backoff(delay = 50, multiplier = 2))
public void transferWithRetry(Long from, Long to, BigDecimal amt) {
    transfer(from, to, amt);
}
```

### 어느 쪽?

| | 비관적 | 낙관적 |
|---|---|---|
| 충돌 빈도 | 높음 | 낮음 |
| 잠금 비용 | 큼 (DB 락 보유) | 없음 |
| 충돌 시 | 대기 | 예외 + 재시도 |
| 트랜잭션 길이 | 짧아야 | 길어도 OK (충돌 시점에 검출) |

> 보통 **낙관적이 처음 시도**. 충돌이 잦으면 비관적으로.

---

## 9. MyBatis와 트랜잭션

```java
@Mapper
public interface OrderMapper {
    Order selectById(@Param("id") Long id);
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    // For Update — MyBatis도 그대로 SQL 작성
    @Select("SELECT * FROM accounts WHERE id = #{id} FOR UPDATE")
    Account selectByIdForUpdate(@Param("id") Long id);
}

@Service
public class TransferService {
    private final OrderMapper mapper;

    @Transactional
    public void transfer(Long from, Long to, BigDecimal amt) {
        Account a = mapper.selectByIdForUpdate(from);
        Account b = mapper.selectByIdForUpdate(to);
        // ...
        mapper.updateBalance(from, a.getBalance().subtract(amt));
        mapper.updateBalance(to, b.getBalance().add(amt));
    }
}
```

MyBatis는 1차 캐시 (`<select>` 결과 캐싱)가 있지만 기본 트랜잭션 범위. `flushCache="true"` 또는 트랜잭션 분리로 우회 가능.

---

## 10. JDBC Template과 트랜잭션

```java
@Service
public class JdbcTransferService {
    private final JdbcTemplate jdbc;

    @Transactional
    public void transfer(Long from, Long to, BigDecimal amt) {
        // SELECT FOR UPDATE 직접
        Account a = jdbc.queryForObject(
            "SELECT * FROM accounts WHERE id = ? FOR UPDATE",
            new BeanPropertyRowMapper<>(Account.class),
            from
        );
        jdbc.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", amt, from);
        jdbc.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amt, to);
    }
}
```

---

## 11. 트랜잭션 모니터링

### Spring Actuator + Micrometer

```yaml
management:
  endpoints:
    web:
      exposure:
        include: metrics
```

```
GET /actuator/metrics/hikaricp.connections.active
GET /actuator/metrics/jdbc.connections.active
GET /actuator/metrics/spring.transaction.committed
GET /actuator/metrics/spring.transaction.rolledback
```

### Slow Transaction Logger

```yaml
spring:
  datasource:
    hikari:
      leak-detection-threshold: 60000   # 60초 이상 connection 잡고 있으면 로그
```

### P6Spy 또는 Datasource Proxy로 SQL 로깅

```xml
<!-- p6spy -->
<dependency>
    <groupId>com.github.gavlyukovskiy</groupId>
    <artifactId>p6spy-spring-boot-starter</artifactId>
</dependency>
```

```yaml
spring:
  datasource:
    url: jdbc:p6spy:mysql://...
  p6spy:
    enable-logging: true
    multiline: true
```

---

## 12. ❌ 안티패턴

### `Exception`만 catch + 무시

```java
// ❌ 트랜잭션이 RuntimeException 안 던지면 롤백 안 됨
@Transactional
public void process() {
    try {
        riskyOp();
    } catch (Exception e) {
        log.error("oops", e);
        // 트랜잭션이 정상 종료 = COMMIT
    }
}

// ✅ 다시 던지거나 상태 표시
@Transactional
public void process() {
    try {
        riskyOp();
    } catch (Exception e) {
        log.error("oops", e);
        throw new BusinessException(e);   // 롤백
    }
}
```

### `@Async` + `@Transactional` 같이 사용

```java
// ❌ @Async는 별도 스레드 → 별도 트랜잭션 컨텍스트
@Async
@Transactional
public void asyncTask() { ... }
// 동작은 하지만 호출자 트랜잭션과 무관. 의도 명확히 해야.
```

### 트랜잭션 안 외부 호출

```java
// ❌ 트랜잭션 안 외부 API 5초 — 5초간 DB 락
@Transactional
public void process() {
    repo.save(...);
    externalApi.call();
    repo.save(...);
}

// ✅ 분리
public void process() {
    PreparedData pd = externalApi.call();
    saveBoth(pd);
}
@Transactional
void saveBoth(PreparedData pd) { ... }
```

---

## 13. 실습

### Step 1: 자기 호출 함정 재현

```java
@Service
public class TestService {
    public void outer() {
        inner();
    }
    @Transactional
    public void inner() {
        // SQL 로깅으로 트랜잭션 시작/커밋 확인
        // outer() 호출 시 → inner의 @Transactional 적용 안 됨
    }
}
```

P6Spy 또는 SQL 로그로 트랜잭션 시작이 어디서 일어나는지 확인.

### Step 2: rollbackFor 함정 재현

```java
@Transactional
public void example1() throws IOException {
    repo.save(...);
    throw new IOException("oops");
}
// COMMIT됨 — DB에 행 남음

@Transactional(rollbackFor = Exception.class)
public void example2() throws IOException {
    repo.save(...);
    throw new IOException("oops");
}
// ROLLBACK
```

### Step 3: REQUIRES_NEW 효과

```java
@Service
public class A {
    @Transactional
    public void outer() {
        repo.save(...);
        b.audit();         // REQUIRES_NEW
        throw new RuntimeException();   // outer 롤백
    }
}

@Service
public class B {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit() { ... }
}
// outer 롤백, audit은 살아남는지 확인
```

### Step 4: JPA + MyBatis 1차 캐시 동기화

```java
@Transactional
public void mix() {
    Order o = jpaRepo.findById(1L).get();
    o.setStatus("PAID");
    em.flush();   // 명시적 flush
    Order fresh = mybatis.findById(1L);
    assert fresh.getStatus().equals("PAID");
}
```

### Step 5: 낙관적 잠금 + 재시도

```java
@Entity public class Counter { @Id Long id; long value; @Version Long version; }

// 100 스레드 동시에 value++
// OptimisticLockingFailureException 빈도 측정
// Retry 적용 후 throughput 비교
```

---

## 더 읽어볼 자료

- 📘 『Pro Spring 6』 (Iuliana Cosmina) — Transaction 챕터
- 🔗 Spring Transaction Management: <https://docs.spring.io/spring-framework/reference/data-access/transaction.html>
- 🔗 Hibernate 락: <https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#locking>

---

## 자가 점검

- [ ] AOP 프록시 동작 원리 (자기 호출이 안 통하는 이유)
- [ ] `@Transactional` 기본 rollbackFor 함정
- [ ] propagation 7가지 중 자주 쓰는 3가지 (REQUIRED, REQUIRES_NEW, NESTED)
- [ ] `readOnly = true`의 효과
- [ ] JPA `@Version` 으로 낙관적 잠금 + 재시도
- [ ] JPA와 MyBatis 혼용 시 1차 캐시 함정과 flush
- [ ] 트랜잭션 안 외부 호출 금지

이번 주 마무리:

- [`labs/lab5_isolation_demo.md`](labs/lab5_isolation_demo.md)
- [`labs/lab6_deadlock_reproduce.md`](labs/lab6_deadlock_reproduce.md)
- [`checklist.md`](checklist.md)
