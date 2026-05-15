# practice_db — 데이터베이스 부트캠프 실습 환경

DB2와 MySQL을 동시에 띄우는 Docker 환경 + 동일 스키마 + Spring Boot 미니 앱.

## 구성

```
practice_db/
├── README.md
├── docker-compose.yml                  ← DB2 + MySQL 동시 기동
├── docker-compose-replication.yml      ← Week 4 Lab 7용 (마스터-슬레이브)
├── sql/
│   ├── db2/
│   │   ├── schema.sql
│   │   └── data.sql
│   └── mysql/
│       ├── schema.sql
│       └── data.sql
└── spring-app/                          ← JPA + MyBatis + JDBC 동시
    ├── build.gradle
    └── src/main/java/com/example/dblab/
```

## 빠른 시작

```bash
cd practice_db
docker compose up -d

# 상태 (Healthy 둘 다)
docker compose ps

# DB2 접속
docker exec -it db2-lab su - db2inst1 -c "db2 connect to labdb"

# MySQL 접속
docker exec -it mysql-lab mysql -uroot -ppassw0rd labdb
```

## 스키마 적용

```bash
# MySQL은 docker-entrypoint-initdb.d 자동 실행 — 이미 적용됨

# DB2는 수동
docker exec -it db2-lab su - db2inst1 -c \
    "db2 connect to labdb && db2 -tvf /sql/schema.sql && db2 -tvf /sql/data.sql"
```

## 종료

```bash
docker compose down              # 컨테이너만
docker compose down -v           # 볼륨까지 (데이터 날아감)
```
