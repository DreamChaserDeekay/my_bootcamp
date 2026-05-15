# Day 4 — 슬로우 쿼리 · 모니터링 · 성능 인사이트

## 한 줄 요약

운영 DB의 성능 문제는 **측정 → 분석 → 튜닝 → 재측정**의 무한 사이클. 슬로우 쿼리 로그·Performance Schema·db2pd 같은 도구로 데이터를 모으고, 자동화된 진단 SQL로 매일·매주 정기 점검. 추측은 금지, 측정만.

## 학습 목표

- [ ] MySQL **Slow Query Log** 설정·분석
- [ ] MySQL **Performance Schema / sys schema**
- [ ] DB2 **MON_GET_*** 함수, db2pd, db2top
- [ ] 매일 돌릴 진단 SQL 묶음
- [ ] Connection pool 모니터링
- [ ] Spring Boot Actuator + Micrometer + Prometheus 연동

---

## 1. MySQL Slow Query Log

### 활성화

```sql
-- my.cnf
[mysqld]
slow_query_log = ON
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 1.0                       -- 1초 이상
log_queries_not_using_indexes = ON          -- 인덱스 안 탄 것 (개발용)
log_slow_admin_statements = ON              -- ALTER 등도
log_slow_replica_statements = ON            -- 슬레이브에서도

-- 런타임
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 1.0;
SET GLOBAL log_queries_not_using_indexes = ON;
```

### 분석 — pt-query-digest

```bash
# Percona Toolkit
pt-query-digest /var/log/mysql/slow.log | head -100
```

출력:

```
# Profile
# Rank Query ID                            Response time  Calls  R/Call  V/M
# ==== =================================== ============== ===== ======= ====
#    1 0x817BBA12A340BCD93E72FA1...        523.2    32.4%  100  5.232   0.43 SELECT orders
#    2 0xABCD...                           201.5    12.5%   50  4.030   0.31 UPDATE orders
```

상위 쿼리 = 튜닝 우선순위.

### 빠른 분석 (mysqldumpslow)

```bash
mysqldumpslow -s t -t 10 /var/log/mysql/slow.log     # 누적 시간 톱 10
mysqldumpslow -s c -t 10 /var/log/mysql/slow.log     # 호출 횟수
```

---

## 2. Performance Schema / sys

### 활성화 (8.0 기본 ON)

```sql
SHOW VARIABLES LIKE 'performance_schema';
```

### sys schema — 인간 친화 view

```sql
-- 가장 느린 쿼리 (실행 시간 누적 기준)
SELECT * FROM sys.statement_analysis ORDER BY total_latency DESC LIMIT 10;

-- 전체 풀스캔 쿼리
SELECT * FROM sys.statements_with_full_table_scans LIMIT 10;

-- 인덱스 없는 쿼리
SELECT * FROM sys.statements_with_errors_or_warnings;

-- 임시 테이블 만드는 쿼리
SELECT * FROM sys.statements_with_temp_tables;

-- 정렬 비싸게 하는 쿼리
SELECT * FROM sys.statements_with_sorting;

-- 사용 안 되는 인덱스
SELECT * FROM sys.schema_unused_indexes;

-- 큰 테이블
SELECT * FROM sys.schema_table_statistics ORDER BY rows_fetched DESC LIMIT 10;

-- 메모리 사용
SELECT * FROM sys.memory_global_total;
SELECT * FROM sys.memory_by_thread_by_current_bytes ORDER BY current_allocated DESC LIMIT 10;

-- 잠금 대기
SELECT * FROM sys.innodb_lock_waits;
```

> 💡 **sys schema는 운영자의 황금 도구**. 외워둘 가치 큼.

### 일일 점검 쿼리 묶음

```sql
-- 1. 어제 가장 비싼 톱 10 쿼리
SELECT
    DIGEST_TEXT,
    COUNT_STAR,
    ROUND(SUM_TIMER_WAIT/1e12, 2) AS total_sec,
    ROUND(AVG_TIMER_WAIT/1e9, 2) AS avg_ms
  FROM performance_schema.events_statements_summary_by_digest
 ORDER BY SUM_TIMER_WAIT DESC LIMIT 10;

-- 2. 풀스캔 비율 높은 쿼리
SELECT * FROM sys.statements_with_full_table_scans LIMIT 20;

-- 3. 사용 안 되는 인덱스
SELECT * FROM sys.schema_unused_indexes;

-- 4. 큰 테이블 (정리 후보)
SELECT table_schema, table_name,
       ROUND(data_length/1024/1024, 1) AS data_mb,
       ROUND(index_length/1024/1024, 1) AS idx_mb,
       table_rows
  FROM information_schema.tables
 WHERE table_schema NOT IN ('mysql','sys','information_schema','performance_schema')
 ORDER BY data_length + index_length DESC LIMIT 20;

-- 5. 잠금 대기
SELECT * FROM sys.innodb_lock_waits;

-- 6. 현재 실행 중인 쿼리
SELECT * FROM information_schema.processlist WHERE command != 'Sleep' ORDER BY time DESC;
```

---

## 3. DB2 Monitoring

### MON_GET_* 함수 (10.1+)

```sql
-- 가장 비싼 쿼리 (시간 기준)
SELECT
    NUM_EXEC_WITH_METRICS AS execs,
    TOTAL_EXEC_TIME / 1000 AS total_ms,
    STMT_TEXT
  FROM TABLE(MON_GET_PKG_CACHE_STMT(NULL, NULL, NULL, -2))
 ORDER BY TOTAL_EXEC_TIME DESC
 FETCH FIRST 10 ROWS ONLY;

-- 가장 비싼 작업 (현재 실행 중)
SELECT
    APPLICATION_HANDLE, APPLICATION_NAME,
    EXECUTION_ID, ELAPSED_TIME_SEC,
    STMT_TEXT
  FROM TABLE(MON_GET_ACTIVITY(NULL, -2)) A
  JOIN SYSIBMADM.SNAPSTMT S ON A.APPLICATION_HANDLE = S.AGENT_ID
 ORDER BY ELAPSED_TIME_SEC DESC FETCH FIRST 10 ROWS ONLY;

-- 테이블 활동
SELECT TABSCHEMA, TABNAME,
       ROWS_READ, ROWS_INSERTED, ROWS_UPDATED, ROWS_DELETED
  FROM TABLE(MON_GET_TABLE(NULL, NULL, -2))
 ORDER BY ROWS_READ DESC FETCH FIRST 10 ROWS ONLY;

-- 인덱스 사용
SELECT TABNAME, INDNAME,
       INDEX_SCANS, INDEX_ONLY_SCANS, KEY_UPDATES
  FROM TABLE(MON_GET_INDEX(NULL, NULL, -2))
 ORDER BY INDEX_SCANS DESC FETCH FIRST 20 ROWS ONLY;

-- 잠금 대기
SELECT * FROM SYSIBMADM.LOCKWAITS;
```

### db2pd (CLI, 빠른 진단)

```bash
# 현재 활성 트랜잭션
db2pd -d labdb -transactions

# 동적 SQL 캐시
db2pd -d labdb -dynamic

# 잠금
db2pd -d labdb -locks

# 잠금 대기
db2pd -d labdb -wlocks

# 데드락 정보
db2pd -d labdb -recovery

# 메모리
db2pd -dbptnmem

# 버퍼풀
db2pd -d labdb -bufferpools

# 모두 한 번에 덤프
db2pd -d labdb -everything > diag.txt
```

### db2top (인터랙티브 모니터)

```bash
docker exec -it db2-lab su - db2inst1 -c "db2top -d labdb"
```

리눅스 top 비슷한 인터페이스로 세션·SQL·테이블 실시간.

---

## 4. Connection Pool 모니터링

### HikariCP (Spring Boot 기본)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000     # 60초 이상 빌려가면 로그

management:
  metrics:
    enable:
      hikaricp: true
```

```bash
# Actuator 메트릭
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
curl http://localhost:8080/actuator/metrics/hikaricp.connections.pending
curl http://localhost:8080/actuator/metrics/hikaricp.connections.usage
```

핵심 메트릭:

| 메트릭 | 의미 | 위험 |
|---|---|---|
| active | 사용 중 | maxPoolSize에 닿음 |
| pending | 대기 중 | > 0이면 풀 부족 |
| usage | 빌려간 시간 (ms) | 길면 leak 의심 |
| timeout | 풀에서 못 받음 | 발생 = 운영 사고 |

### Leak 감지

```yaml
leak-detection-threshold: 60000
```

60초 이상 빌린 연결 → 스택트레이스 로그. 트랜잭션 누수 추적.

---

## 5. Spring Boot Actuator + Micrometer + Prometheus

### 설정

```yaml
# build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
}

# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 50ms, 200ms, 500ms
```

### 활용

```
GET /actuator/prometheus
# Prometheus가 스크레이프
```

Grafana 대시보드:
- 응답 시간 p50/p95/p99
- DB 풀 상태
- JVM heap/GC
- HTTP 5xx 비율

---

## 6. Slow SQL 알람

```yaml
# Prometheus rule 예
groups:
- name: db
  rules:
  - alert: SlowQueryDetected
    expr: rate(mysql_global_status_slow_queries[5m]) > 1
    labels:
      severity: warning
    annotations:
      summary: "MySQL slow query rate > 1/sec"

  - alert: ReplicationLag
    expr: mysql_slave_lag_seconds > 10
    labels:
      severity: critical
    annotations:
      summary: "Replica lag > 10s"
```

---

## 7. ❌ / ✅

### 측정 없이 추측

```
❌ "느린 거 같으니 인덱스 추가하자"
✅ Slow log 확인 → 톱 쿼리 → EXPLAIN → 데이터 기반 인덱스
```

### 모든 쿼리에 풀스캔 경고

```yaml
log_queries_not_using_indexes = ON     # 개발 환경만!
# 운영에 켜두면 로그 폭주
```

### 로그 회전 안 함

```bash
# /etc/logrotate.d/mysql 설정 확인
/var/log/mysql/*.log {
    daily
    rotate 30
    compress
    missingok
    notifempty
    create 640 mysql adm
    postrotate
        mysqladmin flush-logs
    endscript
}
```

---

## 8. 실습

### Step 1: Slow log 활성화 + 부하 + 분석

```sql
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 0.1;     -- 100ms

-- 일부러 느린 쿼리
SELECT * FROM orders WHERE YEAR(created_at) = 2026;
SELECT * FROM orders WHERE notes LIKE '%test%';

-- 로그 확인
docker exec mysql-lab cat /var/lib/mysql/<hostname>-slow.log
```

### Step 2: sys schema 진단

```sql
-- 모든 핵심 view 한 번씩
SELECT * FROM sys.statement_analysis LIMIT 5;
SELECT * FROM sys.schema_unused_indexes;
SELECT * FROM sys.innodb_lock_waits;
-- 등
```

### Step 3: 자동화 스크립트

```sql
-- 매일 04:00 cron으로
SELECT
    'Top10 expensive queries' AS section, '' AS dummy
UNION ALL
SELECT DIGEST_TEXT, CAST(SUM_TIMER_WAIT/1e9 AS CHAR(20)) AS total_ms
  FROM performance_schema.events_statements_summary_by_digest
 ORDER BY 2 DESC LIMIT 10;
-- 결과를 메일 또는 Slack으로
```

### Step 4: HikariCP 풀 모니터링

```bash
# 부하 도구로 동시 요청 늘려가며
ab -n 1000 -c 50 http://localhost:8080/api/orders

# Actuator 동시 관찰
watch -n 1 'curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.pending | jq'
```

---

## 더 읽어볼 자료

- 🔗 MySQL sys schema: <https://dev.mysql.com/doc/refman/8.4/en/sys-schema.html>
- 🔗 Percona Toolkit: <https://docs.percona.com/percona-toolkit/>
- 🔗 DB2 MON_GET 함수: <https://www.ibm.com/docs/en/db2/11.5?topic=routines-monitor>
- 📘 『High Performance MySQL』 Ch. 9 (Monitoring)

---

## 자가 점검

- [ ] MySQL slow log 활성화 + 분석 도구 (pt-query-digest, mysqldumpslow)
- [ ] sys schema 핵심 view 5개 이상 활용
- [ ] DB2 MON_GET_PKG_CACHE_STMT로 비싼 쿼리 추출
- [ ] db2pd, db2top 사용
- [ ] HikariCP 풀 메트릭 모니터링
- [ ] Spring Actuator + Prometheus 연동

다음: [`05_capstone.md`](05_capstone.md)
