# DB2 vs MySQL — 한 페이지 매트릭스

부트캠프 전반에서 가장 자주 참조되는 차이점을 모음. 책상 옆에 두기.

## 1. 큰 그림

| | IBM DB2 11.5 | MySQL 8.4 |
|---|---|---|
| 라이선스 | 상용 (Community 무료) | GPLv2 + 상용 |
| 주 사용처 | 금융·통신·메인프레임 | 웹·SaaS·스타트업 |
| 스토리지 엔진 | 통합 | 플러그인 (InnoDB 기본) |
| 기본 격리수준 | **CS (Read Committed)** | **REPEATABLE READ** |
| MVCC | Currently Committed (10.1+) | Undo log + Read view |
| 기본 포트 | 50000 | 3306 |

## 2. 식별자 / 리터럴

| 의미 | DB2 | MySQL |
|---|---|---|
| 식별자 인용 | `"name"` (큰따옴표) | \`name\` (백틱) |
| 문자열 리터럴 | `'문자열'` (작은따옴표만) | `'문자열'` 또는 `"문자열"` |
| 식별자 대소문자 | 자동 대문자 변환 | OS·collation 따름 |
| 연결 연산자 | `\|\|` 또는 `CONCAT` | `CONCAT` (\|\|는 OR로 해석!) |

## 3. 데이터 타입

| 의미 | DB2 | MySQL |
|---|---|---|
| 자동증가 | `GENERATED ALWAYS AS IDENTITY` | `AUTO_INCREMENT` |
| 시각 (밀리/마이크로) | `TIMESTAMP` | `DATETIME` (TIMESTAMP는 timezone 변환됨) |
| 큰 텍스트 | `CLOB`, `VARCHAR(32K)` | `TEXT`, `MEDIUMTEXT`, `LONGTEXT` |
| 큰 바이너리 | `BLOB` | `BLOB`, `MEDIUMBLOB`, `LONGBLOB` |
| 불리언 | `BOOLEAN` (11.1+) | `BOOLEAN` (실제 TINYINT(1)) |
| JSON | `JSON` (11.5+) | `JSON` (5.7+) |
| UUID | `CHAR(36)` 권장 | `BINARY(16)` + `UUID_TO_BIN()` |

## 4. 자주 쓰는 함수

| 의미 | DB2 | MySQL |
|---|---|---|
| 현재 시각 | `CURRENT TIMESTAMP` (공백) | `NOW()`, `CURRENT_TIMESTAMP` |
| 현재 날짜 | `CURRENT DATE` | `CURDATE()` |
| FROM 절 | 필수 (`FROM SYSIBM.SYSDUMMY1`) | 생략 가능 |
| 부분 문자열 | `SUBSTR(s,start,len)` | `SUBSTRING(s,start,len)` |
| NULL 대체 | `COALESCE`, `NVL` (11.1+) | `COALESCE`, `IFNULL` |
| 날짜 더하기 | `dt + 7 DAYS` | `dt + INTERVAL 7 DAY` |
| 날짜 차이 (일) | `DAYS(a) - DAYS(b)` | `DATEDIFF(a, b)` |
| 포맷 | `VARCHAR_FORMAT(dt, 'YYYY-MM-DD')` | `DATE_FORMAT(dt, '%Y-%m-%d')` |
| 정수 캐스팅 | `CAST(x AS INTEGER)` | `CAST(x AS SIGNED)` |

## 5. SQL 구문

| 의미 | DB2 | MySQL |
|---|---|---|
| 페이징 | `OFFSET m ROWS FETCH FIRST n ROWS ONLY` | `LIMIT n OFFSET m` 또는 위와 동일 (8.0.19+) |
| WITH (CTE) | 9.7+ | 8.0+ |
| 윈도우 함수 | 9.7+ | 8.0+ |
| 재귀 CTE | `WITH RECURSIVE` 또는 그냥 `WITH` | `WITH RECURSIVE` 필수 |
| MERGE | 표준 지원 | 미지원 (INSERT ... ON DUPLICATE KEY 대안) |
| ROLLUP | 표준 | 표준 또는 `WITH ROLLUP` (옛) |
| CUBE | 지원 | **미지원** (GROUPING SETS 우회) |
| GROUPING SETS | 지원 | 8.0+ 제한적 |

## 6. 격리수준 명칭

| 표준 SQL | DB2 | MySQL |
|---|---|---|
| READ UNCOMMITTED | **UR** | READ UNCOMMITTED |
| READ COMMITTED (기본 DB2) | **CS** (Cursor Stability) | READ COMMITTED |
| REPEATABLE READ (기본 MySQL) | **RS** (Read Stability) | REPEATABLE READ (phantom도 막음!) |
| SERIALIZABLE | **RR** (Repeatable Read) ⚠ 이름 충돌 | SERIALIZABLE |

> ⚠ DB2 "RR"이 SQL 표준 RR이 아니라 SERIALIZABLE에 해당. 매우 헷갈림.

## 7. 잠금

| 의미 | DB2 | MySQL InnoDB |
|---|---|---|
| 행 잠금 | ⭕ | ⭕ |
| 페이지 잠금 | ⭕ | ❌ |
| 테이블 잠금 | ⭕ (escalation) | 드뭄 |
| Gap lock | ❌ | ⭕ (REPEATABLE READ) |
| Next-key lock | ❌ | ⭕ (REPEATABLE READ) |
| Lock escalation | ⭕ (LOCKLIST·MAXLOCKS) | ❌ |
| Lock timeout 기본 | -1 (무한) — **변경 필수** | 50초 |

## 8. 명시적 잠금

| 의미 | DB2 | MySQL |
|---|---|---|
| 행 X 잠금 | `SELECT ... FOR UPDATE` | `SELECT ... FOR UPDATE` |
| 행 S 잠금 | `SELECT ... WITH RS USE AND KEEP UPDATE LOCKS` | `SELECT ... FOR SHARE` (8.0+, 옛 LOCK IN SHARE MODE) |
| NOWAIT | `... FOR UPDATE WITH UR` (다소 다름) | `... FOR UPDATE NOWAIT` |
| SKIP LOCKED | `... SKIP LOCKED DATA` | `... FOR UPDATE SKIP LOCKED` |
| 테이블 잠금 | `LOCK TABLE t IN EXCLUSIVE MODE` | `LOCK TABLES t WRITE` |

## 9. 운영 명령

| 작업 | DB2 | MySQL |
|---|---|---|
| 백업 | `BACKUP DATABASE labdb TO /backup` | `mysqldump` / `xtrabackup` |
| 복원 | `RESTORE DATABASE labdb FROM /backup` | `mysql < dump.sql` / `xtrabackup --copy-back` |
| PITR | `ROLLFORWARD DATABASE` | `mysqlbinlog --start-datetime ...` |
| 통계 갱신 | `RUNSTATS ON TABLE t WITH DISTRIBUTION` | `ANALYZE TABLE t` |
| 인덱스 재구성 | `REORG INDEX ix` | (자동) 또는 `ALTER TABLE OPTIMIZE` |
| 슬로우 쿼리 | `MON_GET_PKG_CACHE_STMT` | slow query log + `sys.statement_analysis` |
| 잠금 대기 | `SYSIBMADM.LOCKWAITS`, `db2pd -d X -wlocks` | `sys.innodb_lock_waits`, `SHOW ENGINE INNODB STATUS` |
| 인터랙티브 모니터 | `db2top` | `mytop`, `innotop` |

## 10. 복제·HA

| | DB2 | MySQL |
|---|---|---|
| 표준 | HADR (Primary/Standby) | Replication (Source/Replica) |
| 동기 모드 | SYNC / NEARSYNC / ASYNC / SUPERASYNC | Async / Semi-sync / Group Replication |
| Auto failover | TSA, ACR | Orchestrator, MHA, InnoDB Cluster |
| 클라우드 매니지드 | IBM Db2 on Cloud | AWS RDS, Aurora |

## 11. EXPLAIN

| | DB2 | MySQL |
|---|---|---|
| 명령 | `EXPLAIN PLAN FOR ...` + `db2expln` | `EXPLAIN` / `EXPLAIN ANALYZE` / `EXPLAIN FORMAT=JSON` |
| 풀스캔 키워드 | `TBSCAN` | `type=ALL` |
| 인덱스 스캔 | `IXSCAN` | `type=ref/range/index` |
| Covering | "Index Only Access" | `Extra: Using index` |
| Hash JOIN | `HSJOIN` | `Using join buffer` 또는 hash_join=on (8.0.18+) |
| 시각화 | IBM Data Studio, DBeaver | DBeaver, Workbench, EXPLAIN ANALYZE 트리 |

## 12. 자주 쓰는 SQL 양쪽 동시 표

### 첫 5개 행

```sql
-- DB2 (표준 OK)
SELECT * FROM customers ORDER BY id FETCH FIRST 5 ROWS ONLY;

-- MySQL (둘 다 OK, LIMIT 흔함)
SELECT * FROM customers ORDER BY id LIMIT 5;
```

### 인덱스 친화 시간 범위

```sql
-- DB2
WHERE created_at >= '2026-05-01' AND created_at < '2026-06-01'
-- 또는
WHERE created_at >= CURRENT DATE - 30 DAYS

-- MySQL
WHERE created_at >= '2026-05-01' AND created_at < '2026-06-01'
WHERE created_at >= CURDATE() - INTERVAL 30 DAY
```

### NULL 안전 비교

```sql
-- 양쪽 표준
WHERE col IS NULL
WHERE col IS NOT NULL
-- IS DISTINCT FROM (둘 다 표준)
WHERE a IS DISTINCT FROM b
```

### 안전한 UPDATE 패턴

```sql
-- 잔액이 충분할 때만 차감
UPDATE accounts SET balance = balance - 100
 WHERE id = 1 AND balance >= 100;
-- 영향 행 수 0이면 잔액 부족
```

## 13. 인코딩

```sql
-- DB2: codeset (DB 생성 시)
CREATE DATABASE labdb USING CODESET UTF-8 TERRITORY KR;

-- MySQL
CREATE DATABASE labdb DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- utf8mb4 필수 (이모지·일부 한자)
```

## 14. 면접 / 운영 단골 질문

| Q | A |
|---|---|
| MySQL 기본 격리수준은? | REPEATABLE READ |
| InnoDB RR이 phantom 막는 이유? | next-key lock |
| DB2 RR이 표준 RR이 아니라는데? | DB2 RR = SERIALIZABLE |
| TIME_WAIT 누적의 해결? | 클라이언트 connection pool |
| CLOSE_WAIT 누적은 누구 책임? | 앱 (close 안 함) |
| `NOT IN`을 피하는 이유? | NULL 만나면 결과 빔 |
| `||` 가 두 DB에서 다르다? | DB2=연결, MySQL=OR |
| Covering 인덱스란? | 인덱스만으로 쿼리 해결 (테이블 안 봄) |
