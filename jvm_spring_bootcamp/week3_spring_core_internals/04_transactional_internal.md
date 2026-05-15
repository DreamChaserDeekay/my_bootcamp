# Day 4 — @Transactional 내부

## 한 줄 요약

`@Transactional`은 AOP 어드바이스 `TransactionInterceptor`로 구현. 메서드 진입 시 `PlatformTransactionManager`가 트랜잭션 시작, 종료 시 commit/rollback. **Propagation**으로 트랜잭션 합성 정책을 결정.

## 학습 목표

- [ ] `@Transactional` 한 호출의 흐름을 코드 수준에서 그린다
- [ ] Propagation 7가지의 동작
- [ ] Isolation의 의미 (DB의 그것과 같음)
- [ ] rollbackFor 규칙 (RuntimeException만 자동 롤백)
- [ ] readOnly의 정확한 효과 (Hibernate vs JDBC)
- [ ] PlatformTransactionManager·DataSourceTransactionManager·JpaTransactionManager 차이

---

## @Transactional 호출의 흐름

```
client.callService()
    │
    ▼
OrderService$$Proxy.place(o)            ← Spring AOP 프록시
    │
    ▼
TransactionInterceptor.invoke()         ← @Transactional 어드바이스
    │
    │ 1. TransactionAttribute 파싱 (propagation, isolation, ...)
    │ 2. PlatformTransactionManager에서 트랜잭션 시작
    │    - DataSource에서 Connection 가져옴
    │    - autoCommit=false 설정
    │    - TransactionSynchronizationManager에 바인딩
    │ 3. try {
    │
    ▼
realOrderService.place(o)               ← 실제 메서드
    │
    │ jdbcTemplate.update(...) 또는 entityManager.persist(...)
    │    ↓ DataSourceUtils.getConnection() 
    │    ↓ TSM에서 같은 Connection 가져옴 (트랜잭션 공유)
    │
    ▲
    │ }
    │ catch (RuntimeException e) {
    │ 4. rollbackFor 매칭 검사 → rollback
    │ }
    │ commit (성공 시)
    │ 5. Connection 반환, TSM에서 unbind
    │
    ▼
사용자 코드
```

### 핵심 컴포넌트

| | 무엇 |
|---|---|
| **TransactionInterceptor** | `@Transactional` 어드바이스의 본체. AOP MethodInterceptor |
| **PlatformTransactionManager** | begin/commit/rollback 추상 |
| **DataSourceTransactionManager** | JDBC 구현 |
| **JpaTransactionManager** | JPA(EntityManager) 구현 |
| **HibernateTransactionManager** | Hibernate Session 구현 |
| **TransactionSynchronizationManager** | 현재 스레드의 Connection·EntityManager 보관 (ThreadLocal) |

---

## TransactionAttribute 파싱

```java
@Transactional(
    propagation = Propagation.REQUIRED,
    isolation = Isolation.READ_COMMITTED,
    timeout = 30,
    readOnly = false,
    rollbackFor = {Exception.class},
    noRollbackFor = {SkipException.class}
)
public void doIt() { ... }
```

런타임에 어노테이션 파싱 → `TransactionAttribute` 객체 → 인터셉터가 사용.

---

## Propagation — 7가지

| Propagation | 트랜잭션 있을 때 | 트랜잭션 없을 때 |
|---|---|---|
| **REQUIRED** (기본) | 참여 | 새로 시작 |
| **REQUIRES_NEW** | 기존 일시정지 + 새로 시작 | 새로 시작 |
| **SUPPORTS** | 참여 | 트랜잭션 없이 실행 |
| **NOT_SUPPORTED** | 일시정지 + 트랜잭션 없이 실행 | 트랜잭션 없이 |
| **NEVER** | **예외** | 트랜잭션 없이 |
| **MANDATORY** | 참여 | **예외** |
| **NESTED** | savepoint 사용 (JDBC만) | 새로 시작 |

### 가장 자주 쓰는 셋

#### REQUIRED (기본)
```java
@Transactional               // = @Transactional(propagation = REQUIRED)
public void doIt() {
    inner();                 // outer의 트랜잭션 안에서
}

@Transactional
public void inner() { ... }  // 같은 트랜잭션 참여
```

전체가 하나의 commit/rollback 단위.

#### REQUIRES_NEW
```java
@Transactional
public void outer() {
    saveLog();    // ← REQUIRES_NEW → 별도 트랜잭션
    throw new RuntimeException();
    // outer 롤백, 그러나 saveLog는 이미 커밋됨
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void saveLog() { ... }
```

> 로깅·감사·메트릭 같은 부수 작업에 유용 — 주 트랜잭션 실패해도 로그는 남김.

#### NESTED
```java
@Transactional
public void outer() {
    try {
        nested();
    } catch (Exception e) {
        // nested만 롤백, outer는 계속
    }
    save();    // outer에 포함
}

@Transactional(propagation = Propagation.NESTED)
public void nested() { ... }
```

JDBC savepoint 사용. JPA·Hibernate는 미지원.

### Propagation 함정 — Self-Invocation

```java
@Service
class A {
    @Transactional
    public void outer() {
        inner();    // ❌ self-call → @Transactional(REQUIRES_NEW) 무시!
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void inner() { ... }
}
```

해결: 클래스 분리. (Day 3 참조)

---

## Isolation

DB의 격리수준과 동일.

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
```

| 값 | DB 격리수준 |
|---|---|
| `DEFAULT` | DB 기본 (MySQL = RR, DB2 = CS) |
| `READ_UNCOMMITTED` | dirty read 허용 |
| `READ_COMMITTED` | non-repeatable read 허용 |
| `REPEATABLE_READ` | phantom 허용 (표준 정의) |
| `SERIALIZABLE` | 모든 anomaly 차단 |

> DB 격리수준은 `database_bootcamp/week3` 참조.

---

## Timeout

```java
@Transactional(timeout = 30)    // 초 단위
```

JDBC `setQueryTimeout(30)`을 통해 DB에 전달. 30초 안에 commit 못 하면 예외.

**주의**: DB 드라이버가 timeout 지원해야 함. 일부 드라이버는 무시.

---

## readOnly — 정확한 효과

```java
@Transactional(readOnly = true)
public List<User> findAll() { ... }
```

`readOnly = true`가 보장하는 것:

| 환경 | 보장 |
|---|---|
| Hibernate | `FlushMode = MANUAL` → flush 안 일어남, dirty check 안 함 → **빠름** |
| JDBC `Connection.setReadOnly(true)` | DB에 힌트 (DB가 최적화·복제 전용 라우팅 등) |

**보장 안 함**: 사용자가 직접 UPDATE 쿼리 실행하는 것까지 막진 않음 (DB 권한·실제 DB의 read-only 모드와 다름).

---

## rollbackFor — 자동 롤백 규칙

기본:
- **RuntimeException** → 롤백
- **Error** → 롤백
- **Checked Exception** → **롤백 안 함** (commit!)

```java
@Transactional
public void doIt() throws IOException {
    // ...
    if (...) throw new IOException();    // ❌ checked → 커밋됨!
}

// ✅
@Transactional(rollbackFor = Exception.class)
public void doIt() throws IOException {
    // 이제 IOException도 롤백
}
```

> 자주 까먹는 함정. 운영서 데이터 손상의 흔한 원인.

### 추천: 클래스에 일괄 적용

```java
@Transactional(rollbackFor = Exception.class)
public abstract class BaseService { ... }
```

또는 자기만의 메타 어노테이션:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Transactional(rollbackFor = Exception.class)
public @interface AppTransactional {
}
```

---

## PlatformTransactionManager 구현체

### DataSourceTransactionManager (JDBC)

```java
@Bean
public PlatformTransactionManager txManager(DataSource ds) {
    return new DataSourceTransactionManager(ds);
}
```

JdbcTemplate·MyBatis와 함께. EntityManager 없음.

### JpaTransactionManager (JPA)

```java
@Bean
public PlatformTransactionManager txManager(EntityManagerFactory emf) {
    return new JpaTransactionManager(emf);
}
```

JPA 사용 시. EntityManager의 트랜잭션을 관리. **JDBC 트랜잭션도 같이 관리** (한 DataSource).

### JtaTransactionManager (분산)

여러 DataSource에 걸친 분산 트랜잭션. XA 프로토콜. **거의 안 쓰임** — Saga 패턴이 대안.

> Spring Boot AutoConfiguration이 알아서 선택 — JPA 있으면 JpaTransactionManager, 아니면 DataSourceTransactionManager.

---

## TransactionSynchronizationManager — 스레드 ThreadLocal

```java
// 트랜잭션 활성 여부
TransactionSynchronizationManager.isActualTransactionActive();

// 현재 트랜잭션 이름
TransactionSynchronizationManager.getCurrentTransactionName();

// readOnly 여부
TransactionSynchronizationManager.isCurrentTransactionReadOnly();

// 트랜잭션 동기화 콜백 등록 (commit 후 작업)
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        // 트랜잭션 커밋 후
        kafkaTemplate.send(event);
    }
});
```

**afterCommit** 패턴은 운영서에서 매우 유용:

```java
@Transactional
public void placeOrder(Order o) {
    repo.save(o);
    
    // DB 커밋된 후에야 메시지 보냄 (실패 시 보내지 않음)
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            public void afterCommit() {
                kafka.send("orders", o);
            }
        });
}
```

> DB와 Kafka의 이중기장(double-write) 방지의 가장 간단한 방법.

---

## TransactionTemplate — 프로그래밍 방식

```java
@Service
public class OrderService {
    @Autowired TransactionTemplate template;
    
    public Order place(Order o) {
        return template.execute(status -> {
            try {
                Order saved = repo.save(o);
                return saved;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
    }
}
```

@Transactional의 self-invocation 문제·메서드 단위 한계 우회 가능.

---

## 운영 사례

### 사례 1 — Checked Exception에서 롤백 안 됨

```java
@Transactional
public void process() throws BusinessException {
    repo.save(...);
    if (...) throw new BusinessException();   // ❌ 커밋됨!
}
```

조치: `@Transactional(rollbackFor = Exception.class)`. 또는 BusinessException을 `RuntimeException`으로.

### 사례 2 — readOnly가 효과 없는 듯

```java
@Transactional(readOnly = true)
public List<User> findAll() {
    List<User> users = repo.findAll();
    users.get(0).setName("changed");        // ❌ Hibernate가 flush할까?
    return users;
}
```

`readOnly = true`면 Hibernate가 flush 안 함 → DB에 변경 안 됨. 그러나 명시적 `entityManager.flush()`는 막지 않음 → 매우 혼란.

**원칙**: readOnly 트랜잭션 안에서 절대 변경하지 않는다.

### 사례 3 — REQUIRES_NEW 데드락

```java
@Transactional
public void outer() {
    Account a = repo.findById(1);             // 트랜잭션 1에서 SELECT
    audit.log(...);                           // ← REQUIRES_NEW → 새 트랜잭션
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void log(...) {
    Account a = repo.findById(1);             // 같은 행 → ❌ 트랜잭션 1이 잠금 잡은 행
                                              // → 영원히 대기 → 결국 데드락 timeout
}
```

조치: REQUIRES_NEW 안에서 outer가 잡은 자원 건드리지 않기.

---

## 실습 (Hands-on)

### 1단계 — Propagation 실험

```java
@Service
public class PropagationDemo {
    @Autowired AccountRepo repo;
    @Autowired LogService log;
    
    @Transactional
    public void outerRequired(int id) {
        repo.deposit(id, 100);
        log.audit("deposit");
        throw new RuntimeException("rollback!");
    }
}

@Service
class LogService {
    @Autowired AuditRepo auditRepo;
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(String msg) {
        auditRepo.save(new Audit(msg));
    }
}
```

`outerRequired` 호출 결과:
- `accounts`: 변경 없음 (롤백)
- `audits`: msg 한 줄 추가됨 (별도 트랜잭션 커밋)

→ REQUIRES_NEW의 효과 확인.

### 2단계 — Self-Invocation 함정

```java
@Service
public class Self {
    @Autowired AccountRepo repo;
    
    public void outer(int id) {
        repo.deposit(id, 100);
        inner(id);                          // self-call
    }
    
    @Transactional
    public void inner(int id) {
        System.out.println("transaction active: " +
            TransactionSynchronizationManager.isActualTransactionActive());
    }
}
```

`outer()` 호출 → `inner()`의 출력: `transaction active: false` (AOP 우회).

### 3단계 — afterCommit 콜백

```java
@Service
public class OrderService {
    @Autowired OrderRepo repo;
    @Autowired KafkaTemplate<String, String> kafka;
    
    @Transactional
    public void place(Order o) {
        repo.save(o);
        
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                public void afterCommit() {
                    kafka.send("orders", o.toString());
                    System.out.println("Sent to Kafka after commit");
                }
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        System.out.println("Rolled back, not sending");
                    }
                }
            });
    }
}
```

성공 케이스와 예외 던지는 케이스 비교.

### 4단계 — TransactionTemplate

```java
@Service
public class TplService {
    @Autowired TransactionTemplate tpl;
    @Autowired AccountRepo repo;
    
    public void transfer(int from, int to, long amount) {
        tpl.execute(status -> {
            try {
                repo.withdraw(from, amount);
                if (amount > 1000) {
                    status.setRollbackOnly();
                    return null;
                }
                repo.deposit(to, amount);
                return null;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
    }
}
```

`@Transactional`로 못 하는 시점·범위 제어 가능.

---

## 더 읽어볼 자료

- 📘 『Pro Spring 6』 — Transaction 챕터 (Cosmina)
- 📘 『High-Performance Java Persistence』 — Vlad Mihalcea, JPA·트랜잭션의 본질
- 🔗 [Spring Reference — Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- 🔗 [Vlad Mihalcea — @Transactional 가이드](https://vladmihalcea.com/spring-transactional-annotation/)
- 🎓 김영한 — "스프링 DB 2편" (트랜잭션·예외 처리)
- 🔗 `TransactionInterceptor` 소스 — Spring 코드에서
