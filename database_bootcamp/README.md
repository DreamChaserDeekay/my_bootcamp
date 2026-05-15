# 데이터베이스 부트캠프 — IBM DB2 · MySQL 비교 학습 (Database Bootcamp)

> **대상**: Java/Spring 개발자 중 운영 DB(특히 **IBM DB2**)를 다루지만 **체계적인 SQL·인덱스·트랜잭션·운영 지식**이 부족한 분
> **기간**: 4주 × 5일 = 20 Day (집중 코스) + 캡스톤
> **전제**: SELECT/JOIN/GROUP BY 작성 가능, Spring Boot 사용 경험, Docker 또는 WSL2 가능

---

## 왜 DB2 + MySQL 동시 학습인가

| 이유 | 효과 |
|---|---|
| 회사 환경(금융권)은 **DB2**가 표준 | 실무에서 매일 만지는 시스템 |
| 외부 시장·오픈소스는 **MySQL/MariaDB**가 가장 흔함 | 이직·사이드 프로젝트·기술 학습 |
| 두 DB의 **공통점 = ANSI SQL 표준**, **차이점 = 옵티마이저·잠금·운영 도구** | 한 번 학습으로 두 DB 사용 가능 |
| 비교 학습이 **DB의 본질**을 더 잘 드러냄 | "MySQL은 왜 이렇고 DB2는 왜 저런지"의 *왜*를 익힘 |

> 💡 본 부트캠프는 둘을 **나란히** 다룬다. 같은 문제를 양쪽 DB로 풀고, 차이를 매 챕터에서 표로 정리한다.

---

## 졸업 시점 능력

| 영역 | 능력 |
|---|---|
| **SQL** | JOIN/서브쿼리/CTE/윈도우 함수를 자유롭게. 둘 다의 방언과 표준 차이를 안다 |
| **실행계획** | DB2 `EXPLAIN` + `db2expln` / MySQL `EXPLAIN`·`EXPLAIN ANALYZE`를 해석하고 인덱스 누락·full scan을 즉시 발견 |
| **인덱스 설계** | B-tree, 복합 인덱스 컬럼 순서, Covering Index를 의도해서 설계 |
| **트랜잭션** | 4가지 격리수준의 현상(dirty/non-repeatable/phantom)을 둘 다에서 재현 |
| **잠금** | DB2 페이지·행 잠금 / MySQL InnoDB row·gap·next-key lock 차이를 안다 |
| **데드락** | 발생시키고, 진단하고(`db2 get snapshot`, `SHOW ENGINE INNODB STATUS`), 회피 패턴 적용 |
| **Spring 연동** | JPA·MyBatis·JDBC Template 각각에서 트랜잭션·페이징·N+1·배치 처리 |
| **운영** | 백업·복구·복제(MySQL replication, DB2 HADR) 셋업·장애조치 |

---

## 학습 흐름

```
Week 1: SQL 심화 + DB2 vs MySQL 차이 (방언·내장함수·페이징)
   ↓
Week 2: 실행계획 · 인덱스 튜닝 (B-tree, EXPLAIN, 복합 인덱스, 통계)
   ↓
Week 3: 트랜잭션 · 잠금 · 격리수준 + Spring @Transactional
   ↓
Week 4: 운영 · 백업 · 복제 · HA · 모니터링 + 캡스톤(슬로우 쿼리 진단)
```

각 주차는 **5 Day × 2~4시간** 분량.

---

## 디렉토리 구조

```
database_bootcamp/
├── README.md                       ← 본 파일
├── week1_sql_essentials/           ← SQL 심화 + DB2/MySQL 방언
│   ├── 00_overview.md
│   ├── 01_db2_vs_mysql.md          ← 시작점: 두 DB 큰 그림
│   ├── 02_join_subquery_cte.md
│   ├── 03_window_functions.md
│   ├── 04_pagination_aggregation.md
│   ├── 05_sql_pitfalls.md
│   ├── labs/
│   │   ├── lab1_environment_setup.md  ← Docker로 DB2·MySQL 동시 셋업
│   │   └── lab2_sql_challenge.md
│   └── checklist.md
├── week2_indexes_explain/          ← 실행계획 · 인덱스 · 옵티마이저
│   ├── 00_overview.md
│   ├── 01_storage_engines.md
│   ├── 02_btree_index.md
│   ├── 03_composite_covering.md
│   ├── 04_explain_plan.md
│   ├── 05_optimizer_hints.md
│   ├── labs/
│   │   ├── lab3_index_tuning.md
│   │   └── lab4_explain_walkthrough.md
│   └── checklist.md
├── week3_transactions_concurrency/ ← 트랜잭션 · 잠금 · 격리수준 · Spring
│   ├── 00_overview.md
│   ├── 01_acid_isolation.md
│   ├── 02_locking_models.md
│   ├── 03_deadlock_diagnose.md
│   ├── 04_mvcc_snapshot.md
│   ├── 05_spring_transactional.md
│   ├── labs/
│   │   ├── lab5_isolation_demo.md
│   │   └── lab6_deadlock_reproduce.md
│   └── checklist.md
├── week4_ops_ha_capstone/          ← 운영 · 백업 · 복제 · HA · 캡스톤
│   ├── 00_overview.md
│   ├── 01_backup_recovery.md
│   ├── 02_replication.md
│   ├── 03_partitioning.md
│   ├── 04_monitoring_slow_logs.md
│   ├── 05_capstone.md
│   ├── labs/
│   │   └── lab7_replication_setup.md
│   └── checklist.md
├── practice_db/                    ← Docker + 샘플 스키마 + Spring 미니 앱
│   ├── README.md
│   ├── docker-compose.yml          ← DB2 + MySQL 동시 기동
│   ├── sql/
│   │   ├── db2/schema.sql
│   │   ├── db2/data.sql
│   │   ├── mysql/schema.sql
│   │   └── mysql/data.sql
│   └── spring-app/                 ← JPA + MyBatis + JDBC 동시 사용 예제
└── resources/
    ├── tools.md
    ├── books_and_courses.md
    ├── glossary.md
    ├── quick_reference.md
    ├── db2_vs_mysql_matrix.md      ← 한 페이지 매트릭스 (방언, 운영명령, 잠금)
    ├── sql_cheatsheet.md
    └── troubleshooting_playbook.md
```

---

## 사전 준비

### 1) 공통

- JDK 17+, Docker Desktop (또는 Docker on WSL2), VS Code/IntelliJ
- DBeaver Community(권장) — 두 DB를 한 GUI로 동시에 보기 좋음
  - <https://dbeaver.io/>

### 2) DB2 (Docker)

```bash
docker run -itd --name db2-lab --privileged=true \
    -p 50000:50000 \
    -e LICENSE=accept \
    -e DB2INST1_PASSWORD=passw0rd \
    -e DBNAME=labdb \
    icr.io/db2_community/db2:11.5.9.0
```

> ⚠ DB2 컨테이너는 초기화에 1~3분 소요. `docker logs -f db2-lab`로 `Setup has completed.` 메시지 확인 후 접속.

### 3) MySQL (Docker)

```bash
docker run -d --name mysql-lab \
    -p 3306:3306 \
    -e MYSQL_ROOT_PASSWORD=passw0rd \
    -e MYSQL_DATABASE=labdb \
    mysql:8.4
```

### 4) docker-compose 한 번에

[`practice_db/docker-compose.yml`](practice_db/docker-compose.yml) 사용:

```bash
cd practice_db
docker compose up -d
```

### 5) CLI 도구

```bash
# DB2 CLI (컨테이너 안)
docker exec -it db2-lab su - db2inst1 -c "db2 connect to labdb"

# MySQL CLI
docker exec -it mysql-lab mysql -uroot -ppassw0rd labdb
```

---

## 학습 가이드

- **하루 흐름**: 개념 문서(30~60분) → 직접 실행(60~120분) → 체크리스트(10분)
- **매 주제는 양쪽 DB에 모두 적용**. "DB2에서는…", "MySQL에서는…" 박스로 비교
- 매 챕터 끝 `❌ 위험 / ✅ 안전` 패턴 + 실제 운영 사례

### 코드 예제 컨벤션

```sql
-- ❌ 위험: 인덱스 안 타고 풀스캔
SELECT * FROM orders WHERE YEAR(created_at) = 2026;

-- ✅ 안전: 인덱스 활용
SELECT * FROM orders
 WHERE created_at >= '2026-01-01'
   AND created_at <  '2027-01-01';
```

---

## ⚠ 윤리·운영 가드레일

- 본 부트캠프 실습은 **로컬 Docker** 또는 **명시적 허가받은 개발 DB**에서만 수행
- 운영 DB에 함부로 `EXPLAIN ANALYZE`(쓰기 동반)·`ALTER`·`KILL`을 시도하지 말 것
- 백업·복제 셋업 실습은 격리된 환경에서만
- 실습용 데이터는 PII를 포함하지 않도록 (스키마는 e-commerce 가상 데이터)

---

## 다음 단계 / 심화 경로

- 📘 『SQL Antipatterns』 (Bill Karwin) — 흔한 안티패턴
- 📘 『High Performance MySQL』 (Schwartz et al.) — MySQL 운영의 정수
- 📘 『DB2 Pure Performance for Linux/UNIX』 (Sanyal et al.) — DB2 튜닝
- 📘 『Database Internals』 (Alex Petrov) — 스토리지 엔진 내부
- 🔗 Use The Index, Luke: <https://use-the-index-luke.com/> — 인덱스 학습 최고
- 🎓 CMU 15-445: Database Systems — <https://15445.courses.cs.cmu.edu/>
- **자격증**: IBM Certified Database Administrator (DB2), Oracle MySQL DBA

---

## 시작하기

[`week1_sql_essentials/00_overview.md`](week1_sql_essentials/00_overview.md) → 1주차 시작.
