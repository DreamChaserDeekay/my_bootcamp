# 용어집 (Glossary)

## A

- **ACID** — Atomicity, Consistency, Isolation, Durability
- **ANALYZE TABLE** — MySQL 통계 갱신
- **ARCHIVE LOG** — DB2 PITR을 위한 로그 보관 모드 (vs 순환 로그)
- **AUTO_INCREMENT** (MySQL) / **IDENTITY** (DB2) — 자동 증가 컬럼

## B

- **B+tree** — 대부분 DB 인덱스 구조. leaf만 데이터, leaf끼리 연결
- **Backup, Full / Incremental / Differential** — 백업 종류
- **Binary Log (binlog)** — MySQL 변경 이벤트 로그 (복제·PITR 기반)
- **Bookmark Lookup** — 세컨더리 인덱스 → PK → clustered index 2단계 접근

## C

- **Cardinality** — 컬럼의 유니크 값 개수
- **CHANGE REPLICATION SOURCE** (MySQL 8+) — 복제 대상 설정 (옛 CHANGE MASTER TO)
- **Clustered Index** — InnoDB에서 PK = 데이터 자체 (B+tree leaf가 row)
- **CLOSE_WAIT** — TCP 상태, 앱 close 누락 (Week 3 트랜잭션 안 푸는 케이스와 같이 발생)
- **CLOB / BLOB** — Character / Binary Large Object
- **COALESCE** — NULL이 아닌 첫 값 반환
- **Covering Index** — 인덱스만으로 쿼리 해결 (테이블 페이지 안 봄)
- **CTE (Common Table Expression)** — `WITH name AS (...)`
- **CS (Cursor Stability)** — DB2의 Read Committed에 해당
- **Currently Committed** (DB2) — MVCC 메커니즘

## D

- **db2expln / db2pd / db2top** — DB2 진단 도구
- **DDL / DML / DCL** — 정의 / 조작 / 제어 언어
- **Deadlock** — 순환 대기 → DB 자동 감지·롤백
- **DECIMAL** — 정밀한 십진수 (돈 계산 필수)
- **DETACH PARTITION** (DB2) — 파티션을 별도 테이블로 분리
- **Differential Backup** — 마지막 full 이후 변경분
- **DML / DDL** — Data Manipulation / Definition Language

## E

- **EXPLAIN** — 실행 계획 보기
- **EXPLAIN ANALYZE** (MySQL 8.0.18+) — 실제 실행하며 시간 측정
- **eventual consistency** — 최종 일관성

## F

- **FK (Foreign Key)** — 외래 키
- **FETCH FIRST N ROWS ONLY** — 표준 LIMIT
- **FOR UPDATE** — 행 X 잠금
- **Full Table Scan** — 풀스캔 (큰 테이블에서 위험)
- **FULLTEXT Index** — 전문 검색 인덱스 (MySQL)

## G

- **Gap Lock** — InnoDB의 인덱스 빈 공간 잠금
- **GTID (Global Transaction Identifier)** — MySQL 복제 식별자
- **GROUPING SETS** — 다차원 집계

## H

- **HADR (High Availability Disaster Recovery)** — DB2 복제
- **Hash Join** — 해시 기반 JOIN 알고리즘
- **HikariCP** — Spring Boot 기본 connection pool
- **Histogram** — 컬럼 값 분포 통계 (옵티마이저용)
- **HSJOIN** (DB2) — Hash JOIN

## I

- **IDENTITY** (DB2) — AUTO_INCREMENT 등가
- **INCLUDE** (DB2 인덱스) — leaf에만 컬럼 저장, 검색 키 X
- **InnoDB** — MySQL 기본 스토리지 엔진
- **Index-Only Scan** = Covering scan
- **Isolation Level** — 격리수준
- **IXSCAN** (DB2) — 인덱스 스캔

## J

- **JDBC** — Java Database Connectivity
- **JPA / Hibernate** — Java ORM
- **JOIN** — 테이블 결합 (INNER/LEFT/RIGHT/FULL/CROSS/SELF)

## K

- **Keyset Pagination** = Seek Method — 깊은 페이지에 효율적

## L

- **LIMIT** (MySQL) — 행 제한
- **LOCK ESCALATION** (DB2) — 행 잠금 → 페이지/테이블 잠금 자동 승격
- **LOCKLIST / MAXLOCKS** (DB2) — 잠금 메모리 설정
- **LOCKTIMEOUT** (DB2) — 잠금 대기 타임아웃

## M

- **MERGE** — UPSERT 표준 구문 (DB2 지원, MySQL 미지원)
- **MON_GET_*** (DB2) — 모니터링 함수
- **MVCC (Multi-Version Concurrency Control)** — 여러 버전 동시 유지

## N

- **Nested Loop Join (NLJ)** — JOIN 알고리즘
- **Next-Key Lock** — InnoDB의 행 + gap 결합 잠금
- **NOT EXISTS** — ANTI JOIN의 표준 패턴 (NOT IN 대체)
- **NULL** — 알 수 없는 값 (3가 논리)

## O

- **OFFSET** — 스킵할 행 수
- **OPTIMIZER HINT** — 옵티마이저 제어 (`USE INDEX`, `/*+ ... */`)
- **ORM** — Object-Relational Mapping
- **Optimistic Locking** — @Version 기반, 충돌 시 예외

## P

- **Partition Pruning** — 옵티마이저가 일부 파티션만 스캔
- **PCTFREE** (DB2) — 페이지 빈 공간 비율
- **Performance Schema** (MySQL) — 진단용 메타 스키마
- **Pessimistic Locking** — `FOR UPDATE` 기반
- **PITR (Point-In-Time Recovery)** — 특정 시점까지 복구
- **PK (Primary Key)** — 기본 키
- **Prepared Statement** — 바인딩 변수 사용 (SQL 인젝션 방지)
- **Propagation** — Spring 트랜잭션 7가지 모드

## Q

- **Query Plan** = Execution Plan

## R

- **RANGE Partitioning** — 값 범위로 파티셔닝
- **READ COMMITTED** — 격리수준 (DB2 기본 = CS)
- **REPEATABLE READ** — MySQL InnoDB 기본
- **RS (Read Stability)** (DB2) — 부분적인 REPEATABLE READ
- **RR (Repeatable Read)** (DB2) — 실은 SERIALIZABLE (이름 충돌!)
- **Replication** — 마스터에서 슬레이브로 변경 전파
- **RPO (Recovery Point Objective)** — 손실 허용 시간
- **RTO (Recovery Time Objective)** — 복구 허용 시간
- **RUNSTATS** (DB2) — 통계 갱신

## S

- **Savepoint** — 트랜잭션 내부 부분 롤백 지점
- **Schema** — DB 안의 namespace
- **Seek Method** = Keyset pagination
- **Selectivity** — 결과 행 수 / 전체 행 수
- **SERIALIZABLE** — 가장 엄격한 격리수준
- **SKIP LOCKED** — 잠긴 행 건너뜀 (작업 큐 패턴)
- **Slow Query Log** — MySQL 느린 쿼리 로그
- **SOMAXCONN** — listen backlog 한계 (OS)
- **SQL Injection** — SQL 인젝션 공격
- **STRAIGHT_JOIN** (MySQL) — 옵티마이저 JOIN 순서 무시
- **Stored Procedure** — DB 안 로직
- **sys schema** (MySQL) — 진단 친화 view

## T

- **Tablespace** — 테이블의 물리 저장 단위
- **TIME_WAIT** — TCP 상태 (DB 운영서 풀 고갈 원인)
- **Trigger** — INSERT/UPDATE/DELETE에 자동 실행되는 코드

## U

- **UNDO Log** — InnoDB MVCC 구현 (옛 버전 보관)
- **UNION ALL** — 중복 허용 합집합 (기본)
- **UNION** — 중복 제거 (느림)
- **Upsert** — INSERT or UPDATE
- **UR (Uncommitted Read)** (DB2) — READ UNCOMMITTED
- **utf8mb4** (MySQL) — 진짜 UTF-8 (옛 utf8은 3-byte 제한)

## V

- **@Version** (JPA) — 낙관적 잠금 키
- **VARCHAR** — 가변 문자열

## W

- **WAL (Write-Ahead Log)** — 변경 전 로그 기록 (Durability 구현)
- **Window Function** — `OVER (...)`, 행 보존 + 집계

## X

- **XtraBackup** (Percona) — MySQL 물리 백업

## Y / Z

- **Z/OS DB2** — 메인프레임 DB2 (LUW와 다른 변종)
