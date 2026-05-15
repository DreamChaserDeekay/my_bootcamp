# 데이터베이스 트러블슈팅 플레이북

증상별 진단 절차. 운영서에서 바로 사용하도록 구체적 SQL을 적었다.

---

## 원칙

1. **측정만, 추측 X**
2. **재현 가능한 조건 먼저** (특정 쿼리·시간대·사용자)
3. **변경 전 백업·롤백 계획**
4. **재측정으로 확인**

---

## 시나리오 1: "쿼리가 갑자기 느려졌어요"

### 진단

```sql
-- 1) 슬로우 쿼리 확인
-- MySQL
SELECT DIGEST_TEXT, COUNT_STAR, ROUND(SUM_TIMER_WAIT/1e12,2) total_sec, ROUND(AVG_TIMER_WAIT/1e9,2) avg_ms
  FROM performance_schema.events_statements_summary_by_digest
 ORDER BY SUM_TIMER_WAIT DESC LIMIT 10;

-- DB2
SELECT TOTAL_EXEC_TIME/1000 ms, AVG_EXEC_TIME/1000 avg_ms, STMT_TEXT
  FROM TABLE(MON_GET_PKG_CACHE_STMT(NULL, NULL, NULL, -2))
 ORDER BY TOTAL_EXEC_TIME DESC FETCH FIRST 10 ROWS ONLY;

-- 2) 톱 쿼리의 EXPLAIN
EXPLAIN ANALYZE <query>;
-- type, rows, Extra 확인

-- 3) 통계가 낡았는지
SHOW INDEX FROM t;                 -- Cardinality 비교
SELECT TABNAME, CARD, STATS_TIME FROM SYSCAT.TABLES WHERE TABNAME = 'T';
```

### 흔한 원인

| 원인 | 시그널 |
|---|---|
| 통계 낡음 | EXPLAIN rows 추정 vs 실제 차이 큼 |
| 데이터 증가 | 행 수 N배 증가, 인덱스 부족 노출 |
| 옵티마이저 계획 변경 | 비슷한 쿼리가 같은 시점에 동시 느려짐 |
| 통계 데이터 자료형 변화 | 함수 인덱스가 무효화됨 |

### 조치

```sql
-- 통계 갱신
ANALYZE TABLE t;                                                       -- MySQL
RUNSTATS ON TABLE schema.t WITH DISTRIBUTION AND DETAILED INDEXES ALL; -- DB2

-- 히스토그램 (비균등 분포)
ANALYZE TABLE t UPDATE HISTOGRAM ON status WITH 100 BUCKETS;           -- MySQL

-- 인덱스 추가
CREATE INDEX idx_x ON t(...);

-- 쿼리 재작성 (함수 → 범위)
```

---

## 시나리오 2: "데드락 알람이 폭증"

### 진단

```sql
-- MySQL: 가장 최근 데드락
SHOW ENGINE INNODB STATUS\G
-- LATEST DETECTED DEADLOCK 섹션

-- 데드락 카운터
SHOW GLOBAL STATUS LIKE 'Innodb_deadlocks';

-- 모두 로그 기록
SET GLOBAL innodb_print_all_deadlocks = ON;

-- DB2
db2 "SELECT * FROM LOCK_EVENT FETCH FIRST 10 ROWS ONLY"
db2 "SELECT * FROM LOCK_PARTICIPANTS"
```

### 원인 파악

- 어느 SQL 두 개가 충돌?
- 어느 행/인덱스?
- 잠금 모드 (X / IX / Gap)?

### 회피

1. **잠금 순서 일관성** — 항상 작은 ID 먼저
2. **짧은 트랜잭션** — 외부 호출 분리
3. **WHERE 컬럼 인덱스** — 잠금 범위 좁힘
4. **재시도** — Spring Retry
5. **낙관적 잠금** — `@Version`

---

## 시나리오 3: "Connection refused" / "Pool exhausted"

### 진단

```bash
# Spring Actuator
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.pending | jq
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active | jq
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.timeout | jq
```

```sql
-- DB 측 활성 연결
-- MySQL
SHOW PROCESSLIST;
SELECT user, count(*) FROM information_schema.processlist GROUP BY user;

-- DB2
db2 "LIST APPLICATIONS"
SELECT * FROM TABLE(MON_GET_CONNECTION(NULL, -2));
```

### 흔한 원인

| 원인 | 시그널 |
|---|---|
| Connection leak | `hikaricp.connections.usage` 길어짐, leak-detection 로그 |
| 긴 트랜잭션 | 같은 connection 오래 보유 |
| 풀 크기 부족 | pending > 0 자주 |
| DB 측 max_connections 도달 | "Too many connections" |

### 조치

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30                    # 늘리기
      leak-detection-threshold: 60000          # leak 잡기
```

```sql
-- MySQL: 글로벌 한계
SHOW VARIABLES LIKE 'max_connections';
SET GLOBAL max_connections = 500;

-- DB2
UPDATE DBM CFG USING MAX_CONNECTIONS AUTOMATIC;
```

---

## 시나리오 4: "복제 지연 (replica lag)"

### 진단

```sql
-- MySQL
SHOW REPLICA STATUS\G
-- Seconds_Behind_Source

-- DB2 HADR
SELECT HADR_ROLE, HADR_STATE, LOG_HADR_DELAY FROM SYSIBMADM.MON_HADR;
```

### 흔한 원인

- 마스터 쓰기 폭주 (큰 INSERT/UPDATE)
- 슬레이브 단일 SQL 스레드 (옛 MySQL)
- 슬레이브 디스크 IO 한계
- 네트워크 지연

### 조치

```sql
-- MySQL: 병렬 복제 (5.7+)
SET GLOBAL replica_parallel_workers = 8;
SET GLOBAL replica_parallel_type = 'LOGICAL_CLOCK';

-- 슬레이브 read-only 강제 (실수 방지)
SET GLOBAL read_only = ON;
SET GLOBAL super_read_only = ON;
```

큰 작업은 청크로:

```sql
DELETE FROM logs WHERE created_at < '2025-01-01' LIMIT 10000;
-- 반복
```

---

## 시나리오 5: "디스크 가득"

### 진단

```sql
-- 큰 테이블 톱 10
-- MySQL
SELECT table_schema, table_name,
       ROUND((data_length + index_length)/1024/1024/1024, 1) AS total_gb
  FROM information_schema.tables
 WHERE table_schema NOT IN ('mysql','sys','performance_schema','information_schema')
 ORDER BY data_length + index_length DESC LIMIT 10;

-- DB2
SELECT TABSCHEMA, TABNAME, NPAGES*PAGESIZE/1024/1024/1024 AS GB
  FROM SYSCAT.TABLES T JOIN SYSCAT.TABLESPACES S ON T.TBSPACEID = S.TBSPACEID
 WHERE TABSCHEMA NOT LIKE 'SYS%'
 ORDER BY NPAGES DESC FETCH FIRST 10 ROWS ONLY;
```

### 조치

```sql
-- 1. 오래된 데이터 파티션 단위 삭제 (파티션 있으면)
ALTER TABLE logs DROP PARTITION p2024;       -- 즉시

-- 2. 청크 DELETE
DELETE FROM logs WHERE created_at < '2025-01-01' LIMIT 10000;
-- 반복

-- 3. 인덱스 재구성 (조각화)
OPTIMIZE TABLE t;                             -- MySQL
REORG TABLE schema.t;                         -- DB2

-- 4. binary log 정리
PURGE BINARY LOGS BEFORE '2026-04-01';

-- 5. 슬로우 로그 회전
mv slow.log slow.log.old
mysqladmin flush-logs
```

---

## 시나리오 6: "OOM / DB 다운"

### 진단 (사후)

```bash
# OS 로그
dmesg | grep -i oom

# MySQL 에러 로그
tail -100 /var/log/mysql/error.log
# "InnoDB: Out of memory" 등

# DB2
tail /database/db2inst1/sqllib/db2dump/db2diag.log
```

### 흔한 원인

| 원인 | 시그널 |
|---|---|
| 너무 큰 buffer pool | OS 메모리 초과 |
| 비대한 sort_buffer / read_buffer | 동시 많은 connection이 각자 잡음 |
| 비대한 단일 쿼리 (TEMP) | 거대 GROUP BY / 정렬 |
| 메모리 누수 (드뭄) | 시간 따라 증가 |

### 조치

```sql
-- MySQL 메모리 사용
SELECT * FROM sys.memory_global_total;
SELECT * FROM sys.memory_by_thread_by_current_bytes ORDER BY current_allocated DESC LIMIT 10;
```

```ini
# my.cnf — RAM의 70% 정도
innodb_buffer_pool_size = 8G

# 세션별 버퍼는 작게 (connection 100개 × 256MB = 25GB!)
sort_buffer_size = 4M
join_buffer_size = 4M
read_buffer_size = 2M
```

---

## 시나리오 7: "특정 사용자만 느림"

### 진단

```sql
-- MySQL: 사용자별 활동
SELECT user, host, db, command, time, state, info
  FROM information_schema.processlist
 WHERE command != 'Sleep'
 ORDER BY time DESC;

-- 특정 user의 슬로우 쿼리
SELECT * FROM mysql.slow_log WHERE user_host LIKE '%alice%' ORDER BY start_time DESC LIMIT 10;
```

### 원인

- 그 사용자만 잠금 대기 (다른 트랜잭션 영향)
- 그 사용자의 데이터가 특히 큼 (예: 10년 어카운트)
- 권한 검사 자체가 느림

---

## 시나리오 8: "통계가 자꾸 틀려요 (옵티마이저 잘못된 선택)"

### 진단

```sql
EXPLAIN SELECT ... ;
-- rows 추정 vs 실제 (EXPLAIN ANALYZE) 차이 큼
```

### 조치

```sql
-- 1. 통계 갱신
ANALYZE TABLE t;
RUNSTATS ON TABLE schema.t WITH DISTRIBUTION;

-- 2. 히스토그램 (비균등 분포 데이터)
ANALYZE TABLE t UPDATE HISTOGRAM ON status WITH 50 BUCKETS;

-- 3. 인덱스 힌트 (최후 수단, 주석 필수)
SELECT /*+ INDEX(t idx_x) */ ...
SELECT * FROM t FORCE INDEX (idx_x) WHERE ...

-- 4. 옵티마이저 변수 점검
SHOW VARIABLES LIKE 'optimizer_switch';
```

---

## 시나리오 9: "백업이 실패"

### 진단

```bash
# MySQL mysqldump 에러
mysqldump --single-transaction labdb 2> /tmp/dump.err
cat /tmp/dump.err

# 흔한 에러:
# "Couldn't fetch table" → 권한 부족
# "Error 2013: Lost connection" → 타임아웃, 큰 테이블
```

### 조치

```bash
# 타임아웃 늘리기
mysqldump --single-transaction --net_buffer_length=16384 --max_allowed_packet=128M ...

# 큰 테이블만 별도
mysqldump --single-transaction labdb large_table | gzip > large_table.sql.gz
mysqldump --single-transaction --ignore-table=labdb.large_table labdb > rest.sql.gz

# 또는 xtrabackup (물리)
xtrabackup --backup --target-dir=/backup/full ...
```

---

## 시나리오 10: "JPA N+1 폭주"

### 진단

```yaml
# SQL 로깅
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE
```

로그에서 같은 SELECT가 N번 반복되면 N+1.

### 조치

```java
// Fetch join
@Query("SELECT o FROM Order o JOIN FETCH o.customer")
List<Order> findAllWithCustomer();

// EntityGraph
@EntityGraph(attributePaths = "customer")
List<Order> findAll();

// Batch fetch
@BatchSize(size = 100)
@OneToMany(...)
private List<Item> items;
```

---

## 자기만의 노트 추가

본인 환경에서 마주친 사건을 같은 형식으로 추가:

### 2026-MM-DD — 사고 제목

- **증상**:
- **진단 단계**:
- **원인**:
- **조치**:
- **재발 방지**:
