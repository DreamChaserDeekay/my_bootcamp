# Lab 1 — Docker로 DB2 · MySQL 동시 실습 환경

## 목표

같은 PC에서 DB2와 MySQL을 동시에 띄우고, 같은 스키마·데이터를 양쪽에 적용한 뒤 SELECT를 확인.

## 소요

20~30분 (DB2 첫 컨테이너 초기화에 시간 걸림)

---

## 1. docker-compose

`practice_db/docker-compose.yml`:

```yaml
services:
  db2:
    image: icr.io/db2_community/db2:11.5.9.0
    container_name: db2-lab
    privileged: true                    # DB2가 요구
    ports:
      - "50000:50000"
    environment:
      LICENSE: accept
      DB2INST1_PASSWORD: passw0rd
      DBNAME: labdb
      AUTOCONFIG: "false"
    volumes:
      - db2_data:/database
      - ./sql/db2:/sql:ro
    healthcheck:
      test: ["CMD-SHELL", "su - db2inst1 -c 'db2 connect to labdb' | grep -q 'Database Connection Information'"]
      interval: 10s
      timeout: 5s
      retries: 30

  mysql:
    image: mysql:8.4
    container_name: mysql-lab
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: passw0rd
      MYSQL_DATABASE: labdb
      MYSQL_USER: appuser
      MYSQL_PASSWORD: passw0rd
      TZ: UTC
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_0900_ai_ci
      - --default-time-zone=+00:00
    volumes:
      - mysql_data:/var/lib/mysql
      - ./sql/mysql:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-ppassw0rd"]
      interval: 5s
      timeout: 3s
      retries: 30

volumes:
  db2_data:
  mysql_data:
```

## 2. 실행

```bash
cd practice_db
docker compose up -d

# DB2는 첫 시작에 1~3분
docker logs -f db2-lab | grep -i "Setup has completed"

# MySQL은 빠름
docker logs -f mysql-lab | tail -5
```

상태 확인:

```bash
docker compose ps
# Healthy 둘 다
```

## 3. 양쪽 접속

### DB2

```bash
docker exec -it db2-lab su - db2inst1 -c "db2 connect to labdb"
# Database Connection Information
# Database server        = DB2/LINUXX8664 11.5.9.0
# SQL authorization ID   = DB2INST1
# Local database alias   = LABDB

docker exec -it db2-lab su - db2inst1
# 셸로 진입 후
db2 connect to labdb
db2 "SELECT CURRENT TIMESTAMP FROM SYSIBM.SYSDUMMY1"
```

> 💡 DB2 CLI는 각 명령을 `db2 "..."`로 감싸 실행. 또는 `db2` 진입 후 프롬프트에서.

### MySQL

```bash
docker exec -it mysql-lab mysql -uroot -ppassw0rd labdb
# mysql> SELECT NOW();
# mysql> exit
```

### DBeaver GUI (권장)

| 항목 | DB2 | MySQL |
|---|---|---|
| Driver | IBM DB2 | MySQL |
| Host | localhost | localhost |
| Port | 50000 | 3306 |
| Database | labdb | labdb |
| User | db2inst1 | root |
| Password | passw0rd | passw0rd |

DBeaver는 두 연결을 같은 창에서 띄울 수 있어 비교 학습에 좋음.

## 4. 스키마 적용

`practice_db/sql/db2/schema.sql`과 `practice_db/sql/mysql/schema.sql` 사용. 본 부트캠프 전체에서 같은 스키마를 사용.

```bash
# DB2 — 스키마
docker exec -i db2-lab su - db2inst1 << 'EOF'
db2 connect to labdb
db2 -tvf /sql/schema.sql
db2 -tvf /sql/data.sql
EOF

# MySQL — 자동 초기화 (docker-entrypoint-initdb.d 사용 시)
# 또는 수동
docker exec -i mysql-lab mysql -uroot -ppassw0rd labdb < practice_db/sql/mysql/schema.sql
docker exec -i mysql-lab mysql -uroot -ppassw0rd labdb < practice_db/sql/mysql/data.sql
```

## 5. 확인

```sql
-- 양쪽
SELECT COUNT(*) FROM customers;
SELECT COUNT(*) FROM orders;
SELECT * FROM customers FETCH FIRST 3 ROWS ONLY;     -- DB2 표준 OK
-- MySQL: 위 동일하게 됨 (8.0.19+), 안 되면 LIMIT 3
```

## 6. 트러블슈팅

### DB2 컨테이너가 안 켜짐

- Docker에 4GB 이상 메모리 할당 (Settings → Resources)
- `--privileged=true` 필수
- 첫 시작에 1~3분 대기

### "SQL30081N A communication error has been detected"

→ 컨테이너가 아직 초기화 중. `docker logs db2-lab | tail`로 "Setup has completed." 확인.

### MySQL `Public Key Retrieval is not allowed`

JDBC URL에 `?allowPublicKeyRetrieval=true&useSSL=false` 추가 (개발 환경만).

### 포트 충돌

```bash
# 호스트 50000, 3306 이미 사용 중인지
lsof -i :50000
lsof -i :3306

# docker-compose.yml에서 호스트 포트 변경
ports:
  - "50001:50000"
```

### 컨테이너 정리

```bash
docker compose down              # 컨테이너만
docker compose down -v           # 볼륨도 삭제 (데이터 날아감)
```

## 7. 종료·재시작

```bash
docker compose stop              # 정지 (데이터 유지)
docker compose start             # 다시 시작
docker compose restart db2
```

## 다음

[`lab2_sql_challenge.md`](lab2_sql_challenge.md)
