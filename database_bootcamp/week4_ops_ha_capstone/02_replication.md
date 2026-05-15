# Day 2 — 복제 · HA (High Availability)

## 한 줄 요약

DB 복제는 **읽기 분산 + HA + 백업의 보완**을 한 번에 제공. MySQL은 **Replication** (마스터 → 슬레이브, 또는 Group Replication), DB2는 **HADR** (Primary → Standby) 가 표준. 동기/비동기 모드에 따라 RPO·성능 트레이드오프가 달라진다.

## 학습 목표

- [ ] MySQL Replication: 비동기 / 반동기 / 동기 (그룹)
- [ ] DB2 HADR: SYNC / NEARSYNC / ASYNC / SUPERASYNC
- [ ] 마스터-슬레이브 셋업 (Docker compose)
- [ ] **읽기 분산** (write to master, read from slave)
- [ ] 페일오버 / 페일백
- [ ] 복제 지연 (lag) 측정과 모니터링

---

## 1. 왜 복제?

| 목적 | 효과 |
|---|---|
| **HA** | 마스터 장애 시 슬레이브 승격 |
| **읽기 분산** | 슬레이브에 SELECT 분산 → 마스터 부하 ↓ |
| **백업 격리** | 슬레이브에서 백업 → 마스터 영향 X |
| **분석** | 슬레이브를 분석 전용으로 (긴 쿼리도 OK) |
| **지리적 분산** | 다른 리전에 슬레이브 (DR) |

---

## 2. MySQL Replication

### 종류

| | 동기 모드 | 특징 |
|---|---|---|
| **Asynchronous** (기본) | 마스터가 커밋 → 슬레이브에 비동기 전달 | 빠름, 손실 가능 |
| **Semi-Synchronous** | 마스터가 커밋 전 슬레이브 1대 확인 | 손실 ↓, 약간 느림 |
| **Group Replication** | 다수 노드 합의 (Paxos 기반) | 자동 페일오버, 모든 노드 동기 |
| **InnoDB Cluster** | Group Replication + MySQL Router | 운영자 친화 |

### Binary Log + Position 기반 (전통)

```
Master                       Slave
  ↓                            ↓
binary log file: mysql-bin.000001
position: 4523

Slave:
  CHANGE MASTER TO MASTER_HOST='master', MASTER_LOG_FILE='mysql-bin.000001', MASTER_LOG_POS=4523;
  START SLAVE;
```

### GTID 기반 (5.6+, 권장)

```
GTID (Global Transaction Identifier): 'uuid:1-100'
페일오버 시 슬레이브 간 이전 가능 — position 추적 불필요
```

### 셋업 예제 (Docker)

```yaml
# docker-compose.yml — 마스터 + 슬레이브
services:
  mysql-master:
    image: mysql:8.4
    ports: ["3306:3306"]
    environment:
      MYSQL_ROOT_PASSWORD: passw0rd
      MYSQL_DATABASE: labdb
    command:
      - --server-id=1
      - --log-bin=mysql-bin
      - --binlog-format=ROW
      - --gtid-mode=ON
      - --enforce-gtid-consistency=ON
      - --log-replica-updates=ON

  mysql-slave:
    image: mysql:8.4
    ports: ["3307:3306"]
    environment:
      MYSQL_ROOT_PASSWORD: passw0rd
    command:
      - --server-id=2
      - --log-bin=mysql-bin
      - --binlog-format=ROW
      - --gtid-mode=ON
      - --enforce-gtid-consistency=ON
      - --log-replica-updates=ON
      - --read-only=ON
    depends_on:
      - mysql-master
```

### 복제 사용자 생성 + 연결

```sql
-- 마스터에서
CREATE USER 'replicator'@'%' IDENTIFIED BY 'replpass';
GRANT REPLICATION SLAVE ON *.* TO 'replicator'@'%';
FLUSH PRIVILEGES;

-- 마스터 백업
mysqldump -uroot -p --single-transaction --master-data=2 --all-databases > master_dump.sql

-- 슬레이브에 복원
mysql -uroot -p < master_dump.sql

-- 슬레이브에서 복제 시작
CHANGE REPLICATION SOURCE TO
    SOURCE_HOST = 'mysql-master',
    SOURCE_USER = 'replicator',
    SOURCE_PASSWORD = 'replpass',
    SOURCE_AUTO_POSITION = 1;     -- GTID

START REPLICA;

-- 상태 확인
SHOW REPLICA STATUS\G
-- Replica_IO_Running: Yes
-- Replica_SQL_Running: Yes
-- Seconds_Behind_Source: 0
```

### 8.0+ 용어 변경

| 옛 | 새 |
|---|---|
| MASTER | SOURCE |
| SLAVE | REPLICA |
| `SHOW SLAVE STATUS` | `SHOW REPLICA STATUS` |
| `CHANGE MASTER TO` | `CHANGE REPLICATION SOURCE TO` |

---

## 3. DB2 HADR

### 동기 모드 4가지

| 모드 | 동작 | RPO |
|---|---|---|
| **SYNC** | Primary 커밋 = Standby 디스크 쓰기 완료 후 | 0 |
| **NEARSYNC** | Primary 커밋 = Standby 수신 + 메모리 기록 | ~0 |
| **ASYNC** | Primary 커밋 = Standby로 송신 직후 (확인 X) | 가변 |
| **SUPERASYNC** | Primary 커밋 = 즉시 (TCP 송신 보장 안 함) | 큼 |

### 셋업 (요약)

Primary (db2 inst):
```sql
UPDATE DB CFG FOR labdb USING
    LOGARCHMETH1 DISK:/db2logs
    HADR_LOCAL_HOST primary_host
    HADR_LOCAL_SVC 51000
    HADR_REMOTE_HOST standby_host
    HADR_REMOTE_SVC 51000
    HADR_REMOTE_INST db2inst1
    HADR_SYNCMODE NEARSYNC
    HADR_TIMEOUT 120;

-- 백업 → standby로 복원
BACKUP DATABASE labdb TO /tmp;
-- (standby에 복원)

-- Primary로 시작
START HADR ON DATABASE labdb AS PRIMARY;
```

Standby (db2 inst):
```sql
RESTORE DATABASE labdb FROM /tmp REPLACE HISTORY FILE;

UPDATE DB CFG FOR labdb USING
    HADR_LOCAL_HOST standby_host
    HADR_LOCAL_SVC 51000
    HADR_REMOTE_HOST primary_host
    HADR_REMOTE_SVC 51000
    HADR_REMOTE_INST db2inst1
    HADR_SYNCMODE NEARSYNC;

START HADR ON DATABASE labdb AS STANDBY;
```

### 상태 확인

```sql
SELECT HADR_ROLE, HADR_STATE, HADR_SYNCMODE,
       PRIMARY_LOG_FILE, STANDBY_LOG_FILE,
       LOG_HADR_DELAY
  FROM SYSIBMADM.MON_HADR;
```

### TSA + ACR (자동 페일오버)

DB2 HADR + IBM Tivoli System Automation (TSA) + Automatic Client Reroute (ACR)
- TSA: 헬스체크 후 자동 takeover
- ACR: 클라이언트가 standby로 자동 재연결

---

## 4. 읽기 분산 (Read/Write Splitting)

### Spring Boot — 동적 DataSource

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.master")
    DataSource masterDataSource() { return DataSourceBuilder.create().build(); }

    @Bean
    @ConfigurationProperties("spring.datasource.slave")
    DataSource slaveDataSource() { return DataSourceBuilder.create().build(); }

    @Bean
    DataSource routingDataSource(@Qualifier("masterDataSource") DataSource master,
                                  @Qualifier("slaveDataSource") DataSource slave) {
        Map<Object, Object> targets = new HashMap<>();
        targets.put("master", master);
        targets.put("slave", slave);

        AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                    ? "slave" : "master";
            }
        };
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(master);
        return routing;
    }

    @Bean
    DataSource lazyDataSource(@Qualifier("routingDataSource") DataSource routing) {
        return new LazyConnectionDataSourceProxy(routing);
    }
}
```

서비스 코드:

```java
@Transactional(readOnly = true)
public List<OrderDto> list() {
    // routing이 slave 선택
}

@Transactional
public void create(OrderDto dto) {
    // routing이 master 선택
}
```

> 💡 `LazyConnectionDataSourceProxy`가 핵심: 트랜잭션이 시작되면서 실제 connection 잡는 시점을 늦춤 → readOnly 판단 후 적절한 DS 선택.

### 복제 지연 함정

```
Master에 INSERT → 즉시 Slave에서 SELECT → 못 찾음 (lag 100ms)
```

#### 해결

1. **Read-after-write는 master로** — 같은 사용자 세션 표시
2. **GTID wait**: `WAIT_FOR_EXECUTED_GTID_SET(@@global.gtid_executed, 5)` — 특정 GTID 적용까지 대기
3. **Sticky session** + **timeout 짧게**

---

## 5. 복제 지연 모니터링

### MySQL

```sql
SHOW REPLICA STATUS\G
-- Seconds_Behind_Source: 0
-- 0이 정상, 양수면 따라가는 중, NULL이면 끊김

-- 더 정확한 측정 (8.0+)
SELECT * FROM performance_schema.replication_applier_status_by_worker;
```

### DB2

```sql
SELECT HADR_ROLE, HADR_STATE, LOG_HADR_DELAY
  FROM SYSIBMADM.MON_HADR;
-- LOG_HADR_DELAY가 지연 (초)
```

### 알림

```yaml
# Prometheus + alertmanager 등으로
# replica_lag > 10s → 경고
```

---

## 6. 페일오버

### MySQL 수동

```sql
-- 슬레이브에서 (마스터 죽었을 때)
STOP REPLICA;
RESET REPLICA ALL;
SET GLOBAL read_only = OFF;
SET GLOBAL super_read_only = OFF;

-- 옛 마스터를 새 슬레이브로
CHANGE REPLICATION SOURCE TO SOURCE_HOST='new-master', ...;
START REPLICA;
```

### DB2 takeover

```sql
-- standby에서
TAKEOVER HADR ON DB labdb;

-- by force (primary 살아있어도)
TAKEOVER HADR ON DB labdb BY FORCE;
```

### 자동 페일오버 도구

| | 도구 |
|---|---|
| MySQL | Orchestrator, MHA, MySQL Router + InnoDB Cluster, ProxySQL |
| DB2 | TSA, Pacemaker |
| 클라우드 | AWS RDS Multi-AZ, Aurora, Azure SQL HA |

---

## 7. ❌ / ✅

### "복제 = 백업 대체"

```
❌ 복제 있으니 백업 안 필요
   → DROP TABLE이 슬레이브에 즉시 복제됨 → 둘 다 데이터 사라짐

✅ 복제 + 별도 백업 + 지연 복제 (slave에 lag 1시간 일부러 부여)
```

### "비동기 복제 = RPO 0"

```
❌ 비동기는 마스터 죽으면 마지막 트랜잭션 손실 가능
✅ 금융 등 RPO 0 필요 시 동기 모드 사용 (성능 trade-off)
```

### "마스터 죽으면 자동 페일오버 되겠지"

```
❌ 자동 페일오버 안 셋업
✅ Orchestrator/MHA 또는 클라우드 매니지드
```

### "Read-after-write 일관성 무시"

```
❌ 사용자가 INSERT 후 즉시 SELECT — slave에서 못 찾음
✅ 같은 사용자 세션은 짧은 시간 master로 라우팅
```

---

## 8. 실습

### Step 1: MySQL 마스터-슬레이브 docker-compose

위 §2의 yaml을 `practice_db/docker-compose-replication.yml`로 저장 후

```bash
docker compose -f docker-compose-replication.yml up -d

# 마스터에서
docker exec -it mysql-master mysql -uroot -ppassw0rd
> CREATE USER 'replicator'@'%' IDENTIFIED BY 'replpass';
> GRANT REPLICATION SLAVE ON *.* TO 'replicator'@'%';

# 슬레이브에서
docker exec -it mysql-slave mysql -uroot -ppassw0rd
> CHANGE REPLICATION SOURCE TO
>     SOURCE_HOST='mysql-master', SOURCE_USER='replicator',
>     SOURCE_PASSWORD='replpass', SOURCE_AUTO_POSITION=1;
> START REPLICA;
> SHOW REPLICA STATUS\G
```

### Step 2: 복제 동작 확인

```sql
-- 마스터
INSERT INTO labdb.customers (name, email) VALUES ('Test', 'test@example.com');

-- 슬레이브 (즉시)
SELECT * FROM labdb.customers WHERE email = 'test@example.com';
-- 매우 빠른 lag 후 매치
```

### Step 3: 페일오버 시뮬레이션

```bash
# 마스터 죽이기
docker stop mysql-master

# 슬레이브 승격
docker exec -it mysql-slave mysql -uroot -ppassw0rd -e "STOP REPLICA; RESET REPLICA ALL; SET GLOBAL read_only=OFF;"

# 슬레이브로 INSERT 가능 확인
docker exec -it mysql-slave mysql -uroot -ppassw0rd -e "INSERT INTO labdb.customers ..."
```

### Step 4: Spring 읽기 분산 적용

위 §4 코드로 readOnly 트랜잭션이 슬레이브로 가는지 SQL 로그로 확인.

---

## 더 읽어볼 자료

- 📘 『High Performance MySQL』 Ch. 12 (Replication)
- 🔗 MySQL Replication: <https://dev.mysql.com/doc/refman/8.4/en/replication.html>
- 🔗 DB2 HADR: <https://www.ibm.com/docs/en/db2/11.5?topic=ha-high-availability-disaster-recovery-hadr>
- 🔗 Orchestrator: <https://github.com/openark/orchestrator>

---

## 자가 점검

- [ ] 비동기/반동기/동기 복제의 RPO·성능 trade-off
- [ ] MySQL 8.0의 SOURCE/REPLICA 용어 변경
- [ ] GTID 기반 복제의 장점
- [ ] DB2 HADR 4가지 SYNCMODE
- [ ] 읽기 분산 시 복제 지연 함정 (read-after-write)
- [ ] 복제는 백업이 아니다 (DROP은 즉시 복제됨)

다음: [`03_partitioning.md`](03_partitioning.md)
