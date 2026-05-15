# Lab 6 — @Transactional 함정 실험

## 목표

- Propagation 7가지 직접 호출
- Self-invocation으로 @Transactional 무력화 재현
- Checked Exception 자동 롤백 안 함 확인
- afterCommit 콜백 활용

---

## 1단계 — 환경

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'com.h2database:h2'                    // 간단히 H2
}
```

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:h2:mem:txlab
    username: sa
  jpa:
    show-sql: true
    hibernate.ddl-auto: create
```

---

## 2단계 — 엔티티 + 리포지토리

```java
@Entity
@Table(name = "accounts")
public class Account {
    @Id @GeneratedValue Long id;
    String owner;
    long balance;
    
    // gettersettersetc
}

public interface AccountRepo extends JpaRepository<Account, Long> {}

@Entity
@Table(name = "audits")
public class Audit {
    @Id @GeneratedValue Long id;
    String message;
    Instant at = Instant.now();
    
    public Audit() {}
    public Audit(String m) { this.message = m; }
}

public interface AuditRepo extends JpaRepository<Audit, Long> {}
```

---

## 3단계 — Propagation REQUIRES_NEW 검증

```java
@Service
public class AccountService {
    @Autowired AccountRepo accounts;
    @Autowired AuditService auditService;
    
    @Transactional
    public void transfer(Long fromId, Long toId, long amount) {
        Account from = accounts.findById(fromId).orElseThrow();
        Account to = accounts.findById(toId).orElseThrow();
        from.balance -= amount;
        to.balance += amount;
        
        auditService.log("transfer " + amount + " " + fromId + " → " + toId);
        
        if (from.balance < 0) throw new RuntimeException("insufficient");
    }
}

@Service
public class AuditService {
    @Autowired AuditRepo auditRepo;
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String msg) {
        auditRepo.save(new Audit(msg));
    }
}
```

```java
@RestController
class TxController {
    @Autowired AccountService svc;
    @Autowired AccountRepo accounts;
    @Autowired AuditRepo audits;
    
    @PostMapping("/setup")
    public String setup() {
        Account a = new Account(); a.owner = "alice"; a.balance = 1000;
        Account b = new Account(); b.owner = "bob"; b.balance = 500;
        accounts.saveAll(List.of(a, b));
        return a.id + "," + b.id;
    }
    
    @PostMapping("/transfer/{from}/{to}/{amt}")
    public String transfer(@PathVariable Long from, @PathVariable Long to, @PathVariable long amt) {
        try {
            svc.transfer(from, to, amt);
            return "ok";
        } catch (Exception e) {
            return "failed: " + e.getMessage();
        }
    }
    
    @GetMapping("/state")
    public Map<String, Object> state() {
        return Map.of(
            "accounts", accounts.findAll(),
            "audits", audits.findAll()
        );
    }
}
```

### 시나리오

```bash
# 1) 초기 상태
curl -X POST http://localhost:8080/setup
# → "1,2"

# 2) 정상 transfer
curl -X POST http://localhost:8080/transfer/1/2/100
curl http://localhost:8080/state | jq
# accounts: alice=900, bob=600
# audits: 1개

# 3) 잔액 부족 (외부 트랜잭션 롤백)
curl -X POST http://localhost:8080/transfer/1/2/9999
curl http://localhost:8080/state | jq
# accounts: alice=900, bob=600    ← 롤백
# audits: 2개                      ← REQUIRES_NEW로 별도 commit!
```

**확인**: 외부 트랜잭션이 롤백돼도 audit은 남음.

---

## 4단계 — Self-Invocation 함정

```java
@Service
public class SelfTxService {
    @Autowired AuditRepo audits;
    
    public void outer() {
        audits.save(new Audit("outer-direct"));
        inner();    // ❌ self-call
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void inner() {
        audits.save(new Audit("inner-via-self"));
        System.out.println("transaction active: " +
            TransactionSynchronizationManager.isActualTransactionActive());
        throw new RuntimeException("rollback inner");
    }
}
```

```bash
curl -X POST http://localhost:8080/self
# → 예외 던지지만 audits에는 둘 다 남음?
```

```java
@PostMapping("/self")
public String selfDemo() {
    try {
        selfTxService.outer();
    } catch (Exception e) {}
    return audits.count() + " audits";
}
```

→ **outer가 트랜잭션 없는 호출**이므로 audit save 두 개 모두 즉시 commit됨 (auto-commit).

→ self-call로 `inner()`의 `@Transactional` 무시 → 새 트랜잭션 안 생김 → 예외에도 롤백 없음.

---

## 5단계 — Checked Exception 자동 롤백 안 함

```java
@Service
public class CheckedExService {
    @Autowired AccountRepo accounts;
    
    @Transactional       // rollbackFor 명시 안 함
    public void doIt() throws CheckedException {
        Account a = accounts.findById(1L).orElseThrow();
        a.balance -= 100;
        // save 자동 (영속성 컨텍스트)
        throw new CheckedException("oops");
    }
    
    public static class CheckedException extends Exception {
        public CheckedException(String m) { super(m); }
    }
}

@PostMapping("/checked")
public String checked() {
    try {
        checkedService.doIt();
    } catch (Exception e) {}
    return "balance=" + accounts.findById(1L).get().balance;
}
```

```bash
curl -X POST http://localhost:8080/checked
# → "balance=800"   (커밋됨! 100 차감 반영)
```

이제 `@Transactional(rollbackFor = Exception.class)`로 변경 후 재시작:

```bash
curl -X POST http://localhost:8080/checked
# → "balance=900"   (롤백)
```

> 운영 코드 점검 — checked exception 던지는 메서드는 모두 `rollbackFor` 명시 권장.

---

## 6단계 — readOnly의 효과

```java
@Service
public class ReadOnlyDemo {
    @Autowired AccountRepo accounts;
    
    @Transactional(readOnly = true)
    public void shouldFail() {
        Account a = accounts.findById(1L).orElseThrow();
        a.balance += 100;        // Hibernate dirty check 무시 (FlushMode=MANUAL)
        // commit 시 flush 안 함 → 변경 없음
    }
}
```

```bash
curl -X POST http://localhost:8080/ro
# accounts.findById(1).balance은 변하지 않음
```

JPA(Hibernate)에서 readOnly가 의미 있음. JDBC만이면 DB에 hint 전달 정도.

---

## 7단계 — afterCommit 콜백

```java
@Service
public class CommitHookService {
    @Autowired AccountRepo accounts;
    
    @Transactional
    public void save(Long id, long bal) {
        Account a = accounts.findById(id).orElseThrow();
        a.balance = bal;
        
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    System.out.println(">>> Commit OK → send notification");
                    // kafka.send(...) 같은 외부 호출
                }
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        System.out.println(">>> Rolled back → don't send");
                    }
                }
            });
    }
    
    @Transactional
    public void saveAndFail(Long id, long bal) {
        Account a = accounts.findById(id).orElseThrow();
        a.balance = bal;
        
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    System.out.println(">>> NEVER (this is after rollback)");
                }
                @Override
                public void afterCompletion(int status) {
                    System.out.println(">>> status = " + status);    // STATUS_ROLLED_BACK
                }
            });
        
        throw new RuntimeException("force rollback");
    }
}
```

성공·실패 케이스 비교.

### @TransactionalEventListener 버전 — 더 깔끔

```java
public record SavedEvent(Long id, long bal) {}

@Service
public class EventService {
    @Autowired ApplicationEventPublisher publisher;
    
    @Transactional
    public void save(Long id, long bal) {
        // ...
        publisher.publishEvent(new SavedEvent(id, bal));
    }
}

@Component
public class SavedListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaved(SavedEvent e) {
        System.out.println("Listener: commit ok, processing " + e);
    }
}
```

---

## 8단계 — TransactionTemplate

```java
@Service
public class TplService {
    @Autowired TransactionTemplate tpl;
    @Autowired AccountRepo accounts;
    
    public void transfer(Long from, Long to, long amount) {
        tpl.execute(status -> {
            Account a = accounts.findById(from).orElseThrow();
            Account b = accounts.findById(to).orElseThrow();
            
            if (a.balance < amount) {
                status.setRollbackOnly();
                return null;
            }
            
            a.balance -= amount;
            b.balance += amount;
            
            // 추가 로직 — 외부 API 호출, 시간 측정 등
            // @Transactional으론 표현하기 까다로운 분기
            
            return null;
        });
    }
}
```

생성자에서 TransactionTemplate 주입:

```java
@Configuration
public class TxConfig {
    @Bean
    public TransactionTemplate txTemplate(PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }
}
```

---

## 산출물

이 lab으로 확인:

- [ ] REQUIRES_NEW로 외부 롤백에도 별도 commit
- [ ] Self-invocation은 @Transactional 무시
- [ ] Checked exception은 기본적으로 자동 롤백 X → `rollbackFor` 필요
- [ ] readOnly는 JPA 환경에서만 dirty-check 비활성화
- [ ] afterCommit 콜백 / @TransactionalEventListener로 외부 시스템 통합
- [ ] TransactionTemplate로 코드 흐름 제어 가능

---

## 다음 단계

[Week 3 Checklist](../checklist.md)
