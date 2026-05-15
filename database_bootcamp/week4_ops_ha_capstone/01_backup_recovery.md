# Day 1 — 백업 · 복구

## 한 줄 요약

DB 운영의 첫 번째 보험은 **백업**. 백업은 "찍는 것"보다 "**복구가 진짜 되는지**" 가 핵심이다. 한 번도 안 해본 복구는 백업이 아니다. **RPO (Recovery Point Objective, 손실 허용 시간) / RTO (Recovery Time Objective, 복구 허용 시간)** 를 정한 후 그에 맞는 백업 전략을 짠다.

## 학습 목표

- [ ] 백업 종류 (논리/물리, full/incremental/differential)
- [ ] **RPO / RTO** 의미와 설계
- [ ] **PITR (Point-In-Time Recovery)** — 트랜잭션 로그 기반
- [ ] DB2 백업 명령 (`BACKUP DATABASE`, `RESTORE`, `ROLLFORWARD`)
- [ ] MySQL 백업 (`mysqldump`, `mysqlpump`, Percona XtraBackup, MEB)
- [ ] 백업의 안전한 보관 (오프사이트, 암호화)

---

## 1. 백업 종류

### 논리 vs 물리

| | 논리 (Logical) | 물리 (Physical) |
|---|---|---|
| 내용 | SQL 또는 데이터 표현 (CREATE/INSERT) | 데이터 파일 자체 (페이지 단위) |
| 도구 | mysqldump, mysqlpump, db2 EXPORT | Percona XtraBackup, db2 BACKUP, file copy |
| 크기 | 큼 (텍스트) | 작음 (압축 가능, 메타 적음) |
| 백업 속도 | 느림 | 빠름 |
| 복구 속도 | 느림 (SQL 재실행) | 빠름 (파일 복사) |
| 이식성 | 높음 (다른 버전·OS 가능) | 낮음 (같은 버전·아키텍처) |
| 일부 복구 | 쉬움 (테이블 단위) | 어려움 (전체 또는 테이블스페이스 단위) |

### Full / Incremental / Differential

| | 의미 |
|---|---|
| **Full** | 전체 스냅샷 |
| **Incremental** | 마지막 백업(full 또는 incr) 이후 변경분 |
| **Differential** | 마지막 **full** 이후 변경분 |
| **Cumulative (DB2)** | Differential과 유사 |

### 백업 전략 예

```
일요일 (full)
월~토 (incremental 매일)

복구 시:
1. 일요일 full 복원
2. 월~문제 발생일까지 incremental 순차 적용
3. (PITR) 트랜잭션 로그로 정확한 시점까지
```

---

## 2. RPO · RTO

### 정의

| 지표 | 의미 |
|---|---|
| **RPO** (Recovery Point Objective) | "얼마만큼의 데이터 손실을 허용할 수 있는가" |
| **RTO** (Recovery Time Objective) | "얼마 안에 복구되어야 하는가" |

### 예시

| 시스템 | RPO | RTO |
|---|---|---|
| 결제 시스템 | 0 (손실 X) | 분 단위 |
| 거래 시스템 | < 5초 | 분 단위 |
| 사용자 로그 | 1시간 | 1시간 |
| 분석 데이터 | 24시간 | 1일 |

### RPO = 0 달성

- 동기 복제 (Synchronous replication) — 트랜잭션 커밋 시 슬레이브 확인까지 대기
- DB2 HADR SYNC mode, MySQL Group Replication

### RTO 단축

- 핫 스탠바이 (자동 페일오버)
- 빈번한 백업 (incremental)
- 작은 단위 (테이블·파티션) 복구 가능 설계

---

## 3. MySQL 백업

### mysqldump (논리, 표준 도구)

```bash
# 단일 DB
docker exec mysql-lab mysqldump -uroot -ppassw0rd --single-transaction \
    --routines --triggers --events labdb > backup_$(date +%Y%m%d).sql

# 모든 DB
mysqldump -uroot -p --all-databases --single-transaction > all_backup.sql

# 특정 테이블만
mysqldump -uroot -p labdb orders order_items > orders.sql

# 압축
mysqldump -uroot -p labdb | gzip > backup.sql.gz

# 복원
mysql -uroot -p labdb < backup.sql
gunzip < backup.sql.gz | mysql -uroot -p labdb
```

#### 핵심 옵션

| 옵션 | 효과 |
|---|---|
| `--single-transaction` | InnoDB만, 일관된 스냅샷 (락 없이) |
| `--lock-tables` | MyISAM에 필요 (InnoDB는 불필요) |
| `--master-data=2` | 바이너리 로그 위치를 주석으로 (복제 셋업용) |
| `--routines` | 프로시저·함수 포함 |
| `--triggers` | 트리거 포함 (기본 ON) |
| `--events` | 이벤트 스케줄러 |
| `--quick` | 한 행씩 (메모리 절약) |
| `--hex-blob` | binary 컬럼을 hex로 (안전) |
| `--no-data` | 스키마만 |
| `--no-create-info` | 데이터만 |

### mysqlpump (5.7+, 병렬)

```bash
mysqlpump -uroot -p --default-parallelism=4 labdb > backup.sql
```

### Percona XtraBackup (물리, 핫백업)

```bash
# 백업
xtrabackup --backup --target-dir=/backup/full --user=root --password=...

# 준비 (apply log)
xtrabackup --prepare --target-dir=/backup/full

# 복구 (mysqld 중지 후)
xtrabackup --copy-back --target-dir=/backup/full
```

핵심: **운영 중에도 백업 가능** (락 거의 없음). 대용량에 적합.

### Binary Log + PITR

```sql
-- 활성화 (my.cnf)
[mysqld]
log_bin = mysql-bin
binlog_format = ROW
binlog_expire_logs_seconds = 604800   -- 7일 보관

-- 확인
SHOW VARIABLES LIKE 'log_bin';
SHOW BINARY LOGS;
SHOW BINLOG EVENTS IN 'mysql-bin.000001' LIMIT 10;
```

```bash
# PITR: full 백업 후 binlog에서 특정 시점까지 재생
mysqlbinlog --start-datetime='2026-05-15 10:00:00' \
            --stop-datetime='2026-05-15 11:00:00' \
            mysql-bin.000001 | mysql -uroot -p
```

---

## 4. DB2 백업

### 백업

```sql
-- 오프라인 (DB 사용 중지)
BACKUP DATABASE labdb TO '/backup' WITHOUT PROMPTING;

-- 온라인 (LOGARCHMETH1 설정 필요)
BACKUP DATABASE labdb ONLINE TO '/backup' WITHOUT PROMPTING;

-- Incremental
BACKUP DATABASE labdb ONLINE INCREMENTAL TO '/backup';

-- Delta (incremental of incremental)
BACKUP DATABASE labdb ONLINE INCREMENTAL DELTA TO '/backup';

-- 압축
BACKUP DATABASE labdb COMPRESS;
```

```bash
docker exec -it db2-lab su - db2inst1 -c "db2 BACKUP DATABASE labdb TO /database WITHOUT PROMPTING"
```

### 복구

```sql
RESTORE DATABASE labdb FROM '/backup' WITHOUT PROMPTING;

-- Roll-forward (PITR)
ROLLFORWARD DATABASE labdb TO 2026-05-15-10.30.00.000000 USING LOCAL TIME AND COMPLETE;
ROLLFORWARD DATABASE labdb TO END OF LOGS AND COMPLETE;
```

### 로그 보관 설정

```sql
-- LOGARCHMETH1: 트랜잭션 로그 아카이브 방법
UPDATE DB CFG FOR labdb USING LOGARCHMETH1 DISK:/db2logs;

-- 확인
SELECT VALUE FROM SYSIBMADM.DBCFG WHERE NAME = 'logarchmeth1';
```

순환 로그 모드(기본)에서는 PITR 불가. **archive log 모드**로 전환 필수.

### EXPORT / IMPORT / LOAD (논리)

```sql
-- EXPORT
EXPORT TO orders.del OF DEL SELECT * FROM orders;

-- IMPORT (느림, 트랜잭션 로깅)
IMPORT FROM orders.del OF DEL INSERT INTO orders;

-- LOAD (빠름, 로깅 최소화)
LOAD FROM orders.del OF DEL INSERT INTO orders;
```

---

## 5. 백업 검증 — "안 해보면 백업 아님"

### 백업의 함정

```
"매일 백업 잘 됩니다" 
   ↓
복구 시도 → 손상되었거나 비밀번호 분실 또는 권한 X
   ↓
RPO 0이라 했는데 실제로는 1주일 데이터 손실
```

### 정기 복구 훈련 (Disaster Recovery Drill)

- **분기 1회 이상**: 격리된 환경에 백업 복원, 데이터 검증, 시간 측정
- 결과 기록: RPO/RTO 실측 vs 목표

### 자동 검증 스크립트

```bash
#!/bin/bash
# 백업 → 복원 → 무결성 검사를 매일 자동
DATE=$(date +%Y%m%d)

# 백업
mysqldump -uroot -ppassw0rd --single-transaction labdb > /tmp/$DATE.sql

# 검증 1: 파일 크기
SIZE=$(stat -c%s /tmp/$DATE.sql)
[ $SIZE -lt 1000000 ] && echo "Backup too small!" && exit 1

# 검증 2: 별도 DB에 복원
mysql -uroot -ppassw0rd -e "DROP DATABASE IF EXISTS verify_$DATE; CREATE DATABASE verify_$DATE;"
mysql -uroot -ppassw0rd verify_$DATE < /tmp/$DATE.sql

# 검증 3: 행 수 비교
ORIG=$(mysql -uroot -ppassw0rd labdb -e "SELECT COUNT(*) FROM orders" -ss)
VERIFY=$(mysql -uroot -ppassw0rd verify_$DATE -e "SELECT COUNT(*) FROM orders" -ss)
[ "$ORIG" != "$VERIFY" ] && echo "Row count mismatch!" && exit 1

# 정리
mysql -uroot -ppassw0rd -e "DROP DATABASE verify_$DATE;"

echo "Backup verified: $DATE ($SIZE bytes, $ORIG rows)"
```

---

## 6. 백업 저장 — 3-2-1 규칙

```
3 copies     — 원본 + 백업 2개
2 different  — 서로 다른 매체 (디스크 + 테이프, 또는 디스크 + 클라우드)
1 offsite    — 한 개는 다른 지리적 위치 (재해 대비)
```

### 클라우드 저장

```bash
# AWS S3로 업로드
aws s3 cp backup.sql.gz s3://my-backup-bucket/2026/05/15/
aws s3 cp backup.sql.gz s3://my-backup-bucket/ \
    --storage-class GLACIER         # 장기 보관 (싸지만 복구 느림)

# 라이프사이클 정책: 30일 후 Glacier, 1년 후 삭제
```

### 암호화

```bash
# 비대칭 키로 암호화
gpg --encrypt --recipient backup@example.com backup.sql.gz

# 또는 GPG 대칭
gpg --symmetric backup.sql.gz
# 또는 openssl
openssl enc -aes-256-cbc -in backup.sql.gz -out backup.sql.gz.enc -k SECRET
```

> ⚠ 운영 백업은 **PII 포함**. 보관 시 암호화 필수. GDPR/개인정보보호법.

---

## 7. ❌ / ✅

### "백업 한 번도 복구 안 해본 운영서버"

```
❌ 1년 매일 백업, 한 번도 복구 안 함
   → 실제 사고에서 백업 손상 발견. 데이터 영구 손실.

✅ 분기 1회 복구 훈련. 시간·결과 기록.
```

### "전체 백업만 — incremental 안 씀"

```
❌ 100GB DB 매일 풀백업 → 디스크·네트워크 폭주
✅ 주1 full + 매일 incremental
```

### "백업 보관: 같은 서버"

```
❌ 백업 파일을 같은 디스크에. 디스크 고장 = 다 잃음.
✅ 별도 디스크 + 오프사이트 (NAS, S3, 외부 사이트)
```

### "운영 중 mysqldump 락"

```
❌ mysqldump 옵션 없이 → MyISAM 테이블 락
✅ --single-transaction + InnoDB (락 없음)
```

---

## 8. 실습

### Step 1: MySQL 백업 + 복원

```bash
# 백업
docker exec mysql-lab mysqldump -uroot -ppassw0rd \
    --single-transaction --routines --triggers labdb > /tmp/labdb.sql

ls -la /tmp/labdb.sql

# 데이터 손상 시뮬레이션
docker exec -it mysql-lab mysql -uroot -ppassw0rd labdb -e "DELETE FROM customers WHERE id = 1"

# 복원 (전체 DB 새로 만들기)
docker exec -it mysql-lab mysql -uroot -ppassw0rd -e "DROP DATABASE labdb; CREATE DATABASE labdb;"
docker exec -i mysql-lab mysql -uroot -ppassw0rd labdb < /tmp/labdb.sql

# 확인
docker exec -it mysql-lab mysql -uroot -ppassw0rd labdb -e "SELECT * FROM customers WHERE id = 1"
```

### Step 2: PITR

```sql
-- Binary log 활성화 후
-- 14:00 백업
mysqldump --single-transaction --master-data=2 labdb > backup_14.sql

-- 14:30 데이터 변경
INSERT INTO accounts VALUES (10, 5000);

-- 14:45 실수로 DELETE
DELETE FROM accounts;

-- 복구
-- 1) 14:00 백업 복원
mysql labdb < backup_14.sql

-- 2) 14:30 이후 14:45 직전까지 binlog 재생
mysqlbinlog --start-datetime='2026-05-15 14:00:00' \
            --stop-datetime='2026-05-15 14:44:59' \
            mysql-bin.000001 | mysql labdb
```

### Step 3: DB2 백업

```bash
docker exec -it db2-lab su - db2inst1 -c "db2 connect to labdb && db2 quiesce database immediate force connections && db2 unquiesce database && db2 BACKUP DATABASE labdb TO /database WITHOUT PROMPTING"

ls /database/db2inst1/NODE0000/LABDB/

# 복구 (별도 환경에서)
db2 RESTORE DATABASE labdb FROM /database WITHOUT PROMPTING
```

### Step 4: 자동 검증 스크립트

위 §5 스크립트를 본인 환경에 맞게 작성. cron 또는 systemd timer로 매일.

---

## 더 읽어볼 자료

- 📘 『High Performance MySQL』 Ch. 15 (Backup and Recovery)
- 🔗 MySQL Backup Methods: <https://dev.mysql.com/doc/refman/8.4/en/backup-methods.html>
- 🔗 DB2 Backup: <https://www.ibm.com/docs/en/db2/11.5?topic=recovery-backup-overview>
- 🔗 Percona XtraBackup: <https://docs.percona.com/percona-xtrabackup/8.0/>

---

## 자가 점검

- [ ] RPO와 RTO의 차이
- [ ] 논리 백업 vs 물리 백업 트레이드오프
- [ ] mysqldump `--single-transaction`의 의미
- [ ] DB2 archive log 모드와 PITR
- [ ] 백업은 검증되어야 진짜 백업임을 안다
- [ ] 3-2-1 백업 규칙

다음: [`02_replication.md`](02_replication.md)
