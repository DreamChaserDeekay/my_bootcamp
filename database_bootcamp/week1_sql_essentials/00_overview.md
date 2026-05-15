# Week 1 — SQL 심화 · DB2 vs MySQL 방언

## 주차 목표

- IBM DB2와 MySQL의 **큰 그림 차이** (벤더·라이선스·아키텍처·기본 동작)를 안다
- ANSI SQL 표준 vs 벤더 방언의 경계를 안다
- JOIN, 서브쿼리, CTE(`WITH`)를 자유롭게 조합한다
- 윈도우 함수(`ROW_NUMBER`, `RANK`, `LEAD/LAG`, `PARTITION BY`)를 활용한다
- 페이징의 표준(`OFFSET FETCH`) vs MySQL `LIMIT` vs DB2 `FETCH FIRST`를 안다
- 자주 쓰는 내장 함수의 양쪽 매핑을 안다 (`NOW()`, `CURRENT_TIMESTAMP`, 문자열·날짜 함수)

## 일정표

| Day | 주제 | 핵심 산출물 |
|---|---|---|
| 1 | DB2 vs MySQL 큰 그림 + Docker 셋업 | 양쪽에 동일 스키마 적용 |
| 2 | JOIN · 서브쿼리 · CTE | 복잡 리포트 쿼리 한 줄 |
| 3 | 윈도우 함수 | 누적 합계 · 순위 · 최근 N |
| 4 | 페이징 · 집계 · ROLLUP/CUBE | 페이지네이션 + 부서별 합계 |
| 5 | SQL 함정 · 안티패턴 | 흔한 실수 10선과 안전 패턴 |

## Java/Spring 개발자를 위한 매핑

| 익숙한 개념 | DB SQL 대응 |
|---|---|
| Java Stream `filter` | `WHERE` |
| Stream `map` | `SELECT 컬럼/식` |
| Stream `groupingBy(...counting())` | `GROUP BY ... COUNT(*)` |
| Stream `sorted` | `ORDER BY` |
| Stream `limit(N)` | `LIMIT N` / `FETCH FIRST N ROWS ONLY` |
| Optional의 null 처리 | `COALESCE`, `IFNULL`, `NULLIF` |
| try-with-resources | (DB에서는) 트랜잭션 종료 |
| `List<Order>` 의 모든 주문에 user 붙이기 | `JOIN` (단, N+1을 만들지 않게) |

## 사전 점검

- [ ] Docker로 DB2와 MySQL 컨테이너가 둘 다 동작
- [ ] DBeaver 또는 CLI로 양쪽에 접속 가능
- [ ] `SELECT 1`을 양쪽에서 실행

## 첫 발걸음

[`01_db2_vs_mysql.md`](01_db2_vs_mysql.md)부터 시작.
