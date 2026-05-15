# Lab 7 — MySQL Replication 셋업 + 페일오버

## 목표

Docker로 마스터-슬레이브 셋업, 데이터 복제 확인, 페일오버 시뮬레이션.

## 1. docker-compose

`practice_db/docker-compose-replication.yml`:

```yaml
services:
  mysql-master:
    image: mysql:8.4
    container_name: mysql-master
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
    volumes:
      - master_data:/var/lib/mysql
      - ./sql/mysql:/docker-entrypoint-initdb.d:ro

  mysql-slave:
    image: mysql:8.4
    container_name: mysql-slave
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
    volumes:
      - slave_data:/var/lib/mysql

volumes:
  master_data:
  slave_data:
```

```bash
docker compose -f docker-compose-replication.yml up -d
```

## 2. 마스터에 복제 사용자

```bash
docker exec -it mysql-master mysql -uroot -ppassw0rd <<'EOF'
CREATE USER 'replicator'@'%' IDENTIFIED WITH mysql_native_password BY 'replpass';
GRANT REPLICATION SLAVE ON *.* TO 'replicator'@'%';
FLUSH PRIVILEGES;
EOF
```

## 3. 슬레이브에 마스터 정보 + START REPLICA

```bash
docker exec -it mysql-slave mysql -uroot -ppassw0rd <<'EOF'
CHANGE REPLICATION SOURCE TO
    SOURCE_HOST = 'mysql-master',
    SOURCE_USER = 'replicator',
    SOURCE_PASSWORD = 'replpass',
    SOURCE_AUTO_POSITION = 1,
    GET_SOURCE_PUBLIC_KEY = 1;

START REPLICA;
EOF
```

## 4. 상태 확인

```bash
docker exec -it mysql-slave mysql -uroot -ppassw0rd -e "SHOW REPLICA STATUS\G" | head -50
```

확인할 것:

- `Replica_IO_Running: Yes`
- `Replica_SQL_Running: Yes`
- `Seconds_Behind_Source: 0`
- `Source_UUID: ...` (마스터의 UUID)

## 5. 복제 동작 검증

```bash
# 마스터에서 INSERT
docker exec -it mysql-master mysql -uroot -ppassw0rd labdb -e "
INSERT INTO customers (name, email) VALUES ('Replica Test', 'replica@example.com');
SELECT COUNT(*) FROM customers;"

# 슬레이브에서 즉시 확인
docker exec -it mysql-slave mysql -uroot -ppassw0rd labdb -e "
SELECT * FROM customers WHERE email = 'replica@example.com';
SELECT COUNT(*) FROM customers;"
```

→ 슬레이브에서도 같은 행 보이면 성공.

## 6. 복제 지연 측정

```bash
# 마스터에 부하 (1000 INSERT)
docker exec -it mysql-master mysql -uroot -ppassw0rd labdb -e "
INSERT INTO orders (customer_id, total_amount, status, created_at)
SELECT 1, 100, 'PAID', NOW() FROM (
    SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
) t1, (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) t2,
   (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) t3,
   (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) t4,
   (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) t5,
   (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) t6,
   (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) t7;
"

# 슬레이브 지연 확인
docker exec -it mysql-slave mysql -uroot -ppassw0rd -e "SHOW REPLICA STATUS\G" | grep Seconds_Behind
```

## 7. 페일오버 시뮬레이션

### 마스터 죽이기

```bash
docker stop mysql-master
```

### 슬레이브 승격

```bash
docker exec -it mysql-slave mysql -uroot -ppassw0rd <<'EOF'
STOP REPLICA;
RESET REPLICA ALL;
SET GLOBAL read_only = OFF;
SET GLOBAL super_read_only = OFF;
EOF
```

### 슬레이브에 쓰기 가능 확인

```bash
docker exec -it mysql-slave mysql -uroot -ppassw0rd labdb -e "
INSERT INTO customers (name, email) VALUES ('Promoted', 'promoted@example.com');
SELECT * FROM customers WHERE email = 'promoted@example.com';"
```

### 옛 마스터 부활 후 새 슬레이브로

```bash
docker start mysql-master

# 마스터를 슬레이브로 (옛 마스터)
docker exec -it mysql-master mysql -uroot -ppassw0rd <<'EOF'
RESET MASTER;
CHANGE REPLICATION SOURCE TO
    SOURCE_HOST = 'mysql-slave',         -- 옛 슬레이브가 이제 새 마스터
    SOURCE_USER = 'replicator',
    SOURCE_PASSWORD = 'replpass',
    SOURCE_AUTO_POSITION = 1;
START REPLICA;
SHOW REPLICA STATUS\G
EOF
```

> ⚠ 실제 운영에서는 데이터 정합성 검증·재동기화가 필수. 위는 학습용 단순화.

## 8. Spring 읽기 분산 시연

`practice_db/spring-app/`에 두 DataSource 설정 후:

```yaml
spring:
  datasource:
    master:
      url: jdbc:mysql://localhost:3306/labdb
      username: root
      password: passw0rd
    slave:
      url: jdbc:mysql://localhost:3307/labdb
      username: root
      password: passw0rd
```

```java
@Transactional(readOnly = true)
public List<CustomerDto> findAll() { ... }   // → slave

@Transactional
public void save(CustomerDto dto) { ... }     // → master
```

P6Spy 로그로 어느 DB에 어느 쿼리가 가는지 확인.

## 9. 회고

- 복제 지연이 0인 경우와 큰 경우는?
- Read-after-write에서 슬레이브에서 못 찾는 시나리오 재현
- 실제 운영에서 자동 페일오버 도구의 필요성

다음: [`../checklist.md`](../checklist.md)
