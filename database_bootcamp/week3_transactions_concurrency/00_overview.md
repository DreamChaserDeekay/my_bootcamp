# Week 3 — 트랜잭션 · 잠금 · 동시성 · Spring

## 주차 목표

- ACID와 4가지 격리수준을 이해한다
- 격리수준별 현상(dirty read, non-repeatable read, phantom)을 양쪽 DB에서 재현한다
- DB2와 MySQL(InnoDB) **잠금 모델 차이**를 안다
- MVCC와 스냅샷 격리의 동작 원리를 이해한다
- **데드락**을 의도적으로 만들고, 진단하고, 회피한다
- Spring `@Transactional`의 propagation·rollbackFor·자기호출 함정을 안다
- JPA·MyBatis·JDBC Template 각각에서 트랜잭션 처리

## 일정표

| Day | 주제 | 핵심 산출물 |
|---|---|---|
| 1 | ACID · 격리수준 4가지 | 양쪽에서 dirty/non-repeatable/phantom 직접 재현 |
| 2 | 잠금 모델 (DB2 vs InnoDB) | row/page/table lock, gap/next-key lock 그림 |
| 3 | 데드락 진단·회피 | 양쪽에서 deadlock 발생 + 분석 명령 |
| 4 | MVCC · 스냅샷 | InnoDB undo log / DB2 currently committed |
| 5 | Spring @Transactional 마스터 | propagation 7가지, 자기호출 함정, JPA·MyBatis·JDBC |

## Java/Spring 매핑

| 익숙한 개념 | DB 트랜잭션 매핑 |
|---|---|
| Java `synchronized` | DB의 잠금 (행/페이지/테이블) |
| AtomicInteger CAS | `SELECT ... FOR UPDATE` 또는 optimistic versioning |
| 분산 락 (Redis, Zookeeper) | DB 잠금 + 격리수준 |
| ConcurrentHashMap | MVCC가 비슷한 효과 (읽기는 락 X) |
| Spring `@Async` 별도 스레드 | 별도 DB 트랜잭션 (REQUIRES_NEW) |

## 사전 점검

- [ ] Week 2 checklist ✅
- [ ] 두 터미널/세션을 동시에 열어 같은 DB 접속 가능
- [ ] DBeaver에서 두 SQL 에디터 동시에 열기 가능

다음: [`01_acid_isolation.md`](01_acid_isolation.md)
