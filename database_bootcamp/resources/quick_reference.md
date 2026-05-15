# Quick Reference — 데이터베이스 한 페이지 치트시트

## 1. 진단 우선 순서

```
느림 / 사고 → 측정 → 분석 → 조치 → 재측정
              ↑
   1) Slow log 추출 (톱 5)
   2) EXPLAIN
   3) 시스템 메트릭 (CPU, IO, 풀, 잠금)
```

## 2. EXPLAIN 즉시 확인 포인트

```
MySQL:
- type: ALL이면 위험 (큰 테이블)
- key: 사용된 인덱스
- rows: 추정치 (실제와 차이 크면 ANALYZE)
- Extra: Using index (Covering), Using filesort/temporary 주의
```

```
DB2 (db2expln):
- TBSCAN = 풀스캔 (위험)
- IXSCAN + FETCH = 일반 인덱스 (Bookmark)
- Index Only Access = Covering
```

## 3. 일일 점검 SQL (MySQL)

```sql
-- 비싼 톱 10
SELECT DIGEST_TEXT, COUNT_STAR, ROUND(SUM_TIMER_WAIT/1e12,2) total_sec
  FROM performance_schema.events_statements_summary_by_digest
 ORDER BY SUM_TIMER_WAIT DESC LIMIT 10;

-- 풀스캔
SELECT * FROM sys.statements_with_full_table_scans LIMIT 10;

-- 미사용 인덱스
SELECT * FROM sys.schema_unused_indexes;

-- 잠금 대기
SELECT * FROM sys.innodb_lock_waits;

-- 큰 테이블
SELECT table_schema, table_name,
       ROUND((data_length+index_length)/1024/1024, 1) AS mb,
       table_rows
  FROM information_schema.tables
 WHERE table_schema NOT IN ('mysql','sys','information_schema','performance_schema')
 ORDER BY data_length+index_length DESC LIMIT 10;
```

## 4. 일일 점검 SQL (DB2)

```sql
-- 비싼 톱 10
SELECT NUM_EXEC_WITH_METRICS, TOTAL_EXEC_TIME/1000 ms, STMT_TEXT
  FROM TABLE(MON_GET_PKG_CACHE_STMT(NULL, NULL, NULL, -2))
 ORDER BY TOTAL_EXEC_TIME DESC FETCH FIRST 10 ROWS ONLY;

-- 잠금 대기
SELECT * FROM SYSIBMADM.LOCKWAITS;

-- 테이블 활동
SELECT TABNAME, ROWS_READ FROM TABLE(MON_GET_TABLE(NULL, NULL, -2))
 ORDER BY ROWS_READ DESC FETCH FIRST 10 ROWS ONLY;

-- 인덱스 사용
SELECT TABNAME, INDNAME, INDEX_SCANS FROM TABLE(MON_GET_INDEX(NULL, NULL, -2))
 ORDER BY INDEX_SCANS DESC FETCH FIRST 10 ROWS ONLY;
```

## 5. 인덱스 무력화 5패턴 (외워두기)

```
1) 함수 씌움      WHERE YEAR(created_at) = 2026
2) 부정 조건       WHERE status <> 'PAID'
3) 좌측 와일드     WHERE name LIKE '%suffix'
4) 형변환          WHERE phone = 1012345678 (phone이 VARCHAR)
5) OR 다른 컬럼   WHERE email = ? OR phone = ?
```

## 6. 잠금 진단

```sql
-- MySQL
SHOW ENGINE INNODB STATUS\G          -- LATEST DETECTED DEADLOCK
SELECT * FROM performance_schema.data_locks;
SELECT * FROM performance_schema.data_lock_waits;

-- DB2
db2pd -d labdb -locks
db2pd -d labdb -wlocks
SELECT * FROM SYSIBMADM.LOCKWAITS;
```

## 7. 격리수준 매트릭스

```
                    UR    CS/RC    RR/(InnoDB)    Serializable
Dirty Read          OK    방지     방지          방지
Non-Repeatable      OK    OK       방지          방지
Phantom             OK    OK       (InnoDB:방지) 방지
Lost Update         OK    OK       OK (직접)     방지
```

## 8. Spring `@Transactional` 골든 룰

```java
@Transactional(rollbackFor = Exception.class)    // ← 항상
public void doIt() {
    // 자기 호출 X
    // 외부 호출 X
    // 짧게
}

@Transactional(readOnly = true)                   // ← 조회 메서드
public Foo get() { ... }
```

## 9. 백업 황금 룰

```
3-2-1 규칙
- 3개 복사본
- 2가지 매체
- 1개 오프사이트

매번 검증 (실제 복구 시뮬레이션)
RPO·RTO 정의 → 그에 맞는 방식
```

## 10. 운영 응급 매뉴얼

| 증상 | 1순위 명령 |
|---|---|
| 슬로우 쿼리 톱 | MySQL: `sys.statement_analysis` / DB2: `MON_GET_PKG_CACHE_STMT` |
| 잠금 대기 | `sys.innodb_lock_waits` / `SYSIBMADM.LOCKWAITS` |
| 데드락 | `SHOW ENGINE INNODB STATUS` / DB2 `LOCK_EVENT` |
| 풀 가득 | `hikaricp.connections.pending` 메트릭 |
| 디스크 가득 | `information_schema.tables` 또는 `SYSCAT.TABLES` |
| 복제 지연 | `SHOW REPLICA STATUS` / `MON_HADR` |
| 통계 낡음 | `ANALYZE TABLE` / `RUNSTATS` |

## 11. 안전 헤더 (양쪽 DB)

```sql
-- 트랜잭션 안에서만 변경
START TRANSACTION;
SELECT COUNT(*) FROM target WHERE condition;     -- 검증
UPDATE target SET ... WHERE condition;
-- 결과 확인
ROLLBACK;
-- 또는 COMMIT
```

## 12. SQL 작성 자가 체크

- [ ] WHERE 컬럼에 인덱스 있는가?
- [ ] 함수 씌우지 않았는가?
- [ ] ORDER BY가 인덱스로 풀리는가?
- [ ] SELECT *을 안 썼는가?
- [ ] DECIMAL을 돈에 썼는가?
- [ ] LIMIT 동반 ORDER BY가 unique한가?
- [ ] BETWEEN이 의도와 맞는가? (inclusive)
- [ ] UNION ALL이 맞는가? (중복 허용)
- [ ] NULL 비교를 IS NULL로 했는가?
- [ ] EXPLAIN 돌려봤는가?
