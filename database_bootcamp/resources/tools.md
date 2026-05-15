# 도구 (Tools)

## 1. GUI 클라이언트

| 도구 | 용도 | 라이선스 |
|---|---|---|
| **DBeaver Community** | 멀티 DB 통합 (DB2, MySQL, PostgreSQL...) | 무료 |
| **DBeaver PRO** | + AI 어시스턴트 등 | 상용 |
| **MySQL Workbench** | MySQL 공식 | 무료 |
| **IBM Data Studio** | DB2 공식 (Eclipse 기반) | 무료 |
| **HeidiSQL** | MySQL 가벼움 (Windows) | 무료 |
| **DataGrip** | JetBrains, 매우 강력 | 상용 |

## 2. CLI

| 도구 | 특징 |
|---|---|
| `mysql` | MySQL 공식 CLI |
| `mycli` | autocomplete + syntax highlight |
| `db2` | DB2 공식 (CLP) |
| `dbcli` | DB2 친화 (커뮤니티) |
| `mysqldump` | 논리 백업 |
| `mysqlpump` | 병렬 백업 (5.7+) |
| `mysqlbinlog` | binlog 분석·재생 |
| `pt-query-digest` | slow log 분석 (Percona Toolkit) |
| `pt-online-schema-change` | 무중단 스키마 변경 |
| `gh-ost` | GitHub의 무중단 마이그레이션 |

## 3. 모니터링

| 도구 | 용도 |
|---|---|
| **Percona Monitoring and Management (PMM)** | MySQL/MongoDB/PostgreSQL 통합 |
| **MySQL Workbench** | 기본 |
| **Datadog** | SaaS APM |
| **New Relic** | SaaS APM |
| **innotop** | MySQL top-like |
| **mytop** | 옛 도구 |
| **db2top** | DB2 인터랙티브 |
| **db2pd** | DB2 진단 (가장 유용) |
| **Prometheus + Grafana** | 오픈소스 모니터링 |

### Prometheus exporter

```bash
# MySQL
docker run -d -p 9104:9104 \
    --link mysql-lab \
    -e DATA_SOURCE_NAME='exporter:passw0rd@(mysql-lab:3306)/' \
    prom/mysqld-exporter

# DB2
# (공식 exporter 없음, IBM Db2 Data Management Console 사용)
```

## 4. 백업·복구

| 도구 | DB |
|---|---|
| **Percona XtraBackup** | MySQL/MariaDB 물리 백업 (운영 친화) |
| **mysqldump** | MySQL 논리 |
| **MySQL Enterprise Backup (MEB)** | MySQL 공식 상용 |
| **db2 BACKUP** | DB2 |
| **rsync + LVM snapshot** | 일반 |

## 5. 마이그레이션·동기화

| 도구 | 용도 |
|---|---|
| **Flyway** | 스키마 마이그레이션 (개발) |
| **Liquibase** | 위와 동일 (XML/YAML) |
| **MySQL Workbench Migration Wizard** | 타 DB → MySQL |
| **AWS DMS** | 클라우드 마이그레이션 |
| **IBM Data Replication** | DB2 ↔ 타 DB |
| **Debezium** | CDC (Change Data Capture) |
| **Maxwell's Daemon** | MySQL binlog → JSON |

## 6. 부하 테스트

| 도구 | 특징 |
|---|---|
| **sysbench** | 표준 DB 벤치마크 |
| **HammerDB** | TPC-C/TPC-H 벤치마크 |
| **mysqlslap** | MySQL 내장 |
| **JMeter** | DB 시나리오 가능 |
| **k6** | 스크립트 기반 |
| **pgbench** | (PostgreSQL용, 참고) |

```bash
# sysbench OLTP 예
sysbench oltp_read_write \
    --table-size=100000 --tables=10 \
    --mysql-host=localhost --mysql-user=root --mysql-password=passw0rd \
    --mysql-db=labdb --threads=16 --time=60 prepare
sysbench oltp_read_write ... run
sysbench oltp_read_write ... cleanup
```

## 7. 데이터 익명화

| 도구 | 용도 |
|---|---|
| **MySQL `RAND()`** | 단순 무작위 |
| **anonymizer scripts** | PII 제거 |
| **Faker (Python)** | 가짜 데이터 생성 |
| **MockNeat (Java)** | Java용 |

## 8. 정적 분석

| 도구 | 용도 |
|---|---|
| **SonarQube** | SQL 인젝션, 성능 안티패턴 검출 |
| **SQLLint** | SQL 문법·스타일 |
| **DBeaver SQL Editor** | 실시간 검증 |

## 9. JPA / MyBatis 헬퍼

| 도구 | 용도 |
|---|---|
| **P6Spy** | 모든 JDBC SQL 로깅 |
| **Datasource Proxy** | SQL/시간/슬로우 로깅 |
| **HikariCP** | 커넥션 풀 (Spring Boot 기본) |
| **QueryDSL** | 타입 안전 JPA 쿼리 |
| **jOOQ** | 타입 안전 SQL DSL |

## 10. 클라우드 매니지드

| | DB |
|---|---|
| **AWS RDS** | MySQL, PostgreSQL, MariaDB, Oracle, SQL Server |
| **AWS Aurora** | MySQL/PostgreSQL 호환, 분산 스토리지 |
| **Azure Database for MySQL** | MySQL 호환 |
| **GCP Cloud SQL** | MySQL, PostgreSQL, SQL Server |
| **IBM Db2 on Cloud** | DB2 SaaS |
| **PlanetScale** | MySQL 호환 + Vitess (서버리스) |
| **Neon** | PostgreSQL serverless (참고) |
