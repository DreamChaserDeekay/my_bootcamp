# Day 5 — 캡스톤: 종합 진단 보고서

## 한 줄 요약

4주간 익힌 SQL·인덱스·트랜잭션·운영을 **하나의 실제 시나리오**에 적용. **느린 쿼리를 찾고, 원인 분석하고, 인덱스/쿼리 재작성하고, 트랜잭션 점검하고, 재측정**한 결과를 보고서로 작성.

---

## 시나리오

전자상거래 운영 DB. 사용자 클레임:

> "주문 화면이 점점 느려져요. 어떤 날은 30초 이상."

운영팀이 다음을 알려줬다:

- 데이터: 고객 100만, 주문 5000만, 주문항목 1억 5천만
- 피크 시간: 평일 10~14시
- 가끔 데드락 알람
- DB 서버 CPU: 60~85%
- DB 서버 메모리: 80% (스왑 없음)
- 디스크 IO: 60~90% (피크 시간)

---

## 단계 1: 측정 (Baseline)

### 1.1 슬로우 쿼리 추출

```sql
-- MySQL
SELECT
    DIGEST_TEXT,
    COUNT_STAR,
    ROUND(SUM_TIMER_WAIT/1e12, 2) AS total_sec,
    ROUND(AVG_TIMER_WAIT/1e9, 2) AS avg_ms,
    ROUND(MAX_TIMER_WAIT/1e9, 2) AS max_ms
  FROM performance_schema.events_statements_summary_by_digest
 ORDER BY SUM_TIMER_WAIT DESC LIMIT 10;
```

```sql
-- DB2
SELECT
    NUM_EXEC_WITH_METRICS,
    TOTAL_EXEC_TIME / 1000000 AS TOTAL_SEC,
    AVG_EXEC_TIME / 1000 AS AVG_MS,
    STMT_TEXT
  FROM TABLE(MON_GET_PKG_CACHE_STMT(NULL, NULL, NULL, -2))
 ORDER BY TOTAL_EXEC_TIME DESC FETCH FIRST 10 ROWS ONLY;
```

→ 톱 5개 골라 기록.

### 1.2 시스템 메트릭

```bash
# CPU, 메모리, IO
docker stats mysql-lab
docker exec mysql-lab top -bn 1 | head
docker exec mysql-lab iostat -x 1 5

# DB 측
mysql -e "SHOW GLOBAL STATUS LIKE 'Innodb_buffer_pool_read%'"
mysql -e "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%'"
```

### 1.3 풀 상태

```
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.pending
```

---

## 단계 2: 분석

각 슬로우 쿼리에 대해:

### 2.1 EXPLAIN

```sql
EXPLAIN ANALYZE
SELECT o.id, o.created_at, c.name, c.email, oi.product_id, p.name
  FROM orders o
  JOIN customers c ON c.id = o.customer_id
  JOIN order_items oi ON oi.order_id = o.id
  JOIN products p ON p.id = oi.product_id
 WHERE o.status = 'PAID'
   AND o.created_at >= '2026-05-01'
   AND c.country = 'KR'
 ORDER BY o.created_at DESC
 FETCH FIRST 50 ROWS ONLY;
```

### 2.2 진단 체크리스트

각 쿼리에 대해:

- [ ] type=ALL (풀스캔)?
- [ ] 사용된 인덱스가 적절한가?
- [ ] rows 추정 vs 실제 차이?
- [ ] Using temporary / Using filesort?
- [ ] JOIN 알고리즘 (NLJ / Hash)?
- [ ] Covering 가능?
- [ ] 함수가 컬럼에 씌워졌나?
- [ ] N+1 가능성?

### 2.3 트랜잭션 점검

```sql
-- 긴 트랜잭션
SELECT trx_id, trx_started,
       TIMESTAMPDIFF(SECOND, trx_started, NOW()) AS duration_sec
  FROM information_schema.innodb_trx
 ORDER BY trx_started LIMIT 10;

-- 데드락 이력
SHOW ENGINE INNODB STATUS\G    -- LATEST DETECTED DEADLOCK
SHOW GLOBAL STATUS LIKE 'Innodb_deadlocks';
```

---

## 단계 3: 조치

각 쿼리에 대해 조치 적용 후 **반드시 재측정**.

### 3.1 인덱스 추가/변경

```sql
-- 예: orders에 Covering 인덱스
CREATE INDEX idx_orders_paid_recent
    ON orders(status, created_at, customer_id, total_amount);

-- 통계 갱신
ANALYZE TABLE orders;
```

### 3.2 쿼리 재작성

```sql
-- 함수 → 범위
WHERE YEAR(created_at) = 2026   →   WHERE created_at >= '2026-01-01' AND created_at < '2027-01-01'

-- 서브쿼리 → JOIN
WHERE id IN (SELECT ...)   →   JOIN ... ON ...

-- DISTINCT 남용 → EXISTS
```

### 3.3 트랜잭션 점검

- 자기 호출 함정 제거
- `rollbackFor = Exception.class` 추가
- `readOnly = true` 적용
- 외부 호출을 트랜잭션 밖으로
- 낙관적 잠금 검토

### 3.4 풀 크기 조정

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30      # 기존 10
```

부하 테스트로 검증.

### 3.5 파티셔닝 (장기)

오래된 데이터 분리:

```sql
-- orders를 RANGE 파티션으로 마이그레이션 (운영 중 어려움 — 별도 계획 필요)
```

---

## 단계 4: 재측정

조치 전후를 같은 표로 비교:

| 쿼리 | Before avg_ms | After avg_ms | 개선율 |
|---|---|---|---|
| Q1 (주문 목록) | 2500 | 35 | 71x |
| Q2 (월간 매출) | 8000 | 180 | 44x |
| Q3 (고객 검색) | 1200 | 12 | 100x |
| Q4 (상품 인기) | 600 | 90 | 6.7x |
| Q5 (재고 차감) | 450 | 45 | 10x |

### 시스템 메트릭

| 지표 | Before | After |
|---|---|---|
| CPU 평균 | 70% | 35% |
| 메모리 | 80% | 75% (큰 변동 없음) |
| Disk IO 피크 | 90% | 40% |
| HikariCP active 평균 | 18/20 | 8/30 |
| 데드락/일 | 12 | 1 |
| HTTP p99 | 5초 | 300ms |

---

## 단계 5: 보고서

`REPORT.md` 작성. 형식:

```markdown
# 운영 DB 성능 진단·튜닝 보고서

## 1. 개요
- 일시: 2026-05-15
- 대상 시스템: 전자상거래 운영 DB (MySQL 8.4 / DB2 11.5)
- 작업자: 본인 이름
- 기간: 5 영업일

## 2. 증상
- 사용자 클레임: "주문 화면 30초+"
- 측정된 p99: 5초
- 데드락: 12/일

## 3. 진단

### 3.1 슬로우 쿼리 톱 5
[표]

### 3.2 EXPLAIN 분석
[Q1] — 풀스캔, 인덱스 없음
[Q2] — 함수 사용으로 인덱스 무효
...

### 3.3 트랜잭션 분석
- 자기 호출 1건
- 트랜잭션 안 외부 호출 1건
- rollbackFor 누락 5건

## 4. 조치

### 4.1 인덱스
- `idx_orders_paid_recent` 추가 (Covering)
- `idx_customers_country` 추가
- 미사용 인덱스 3개 삭제

### 4.2 쿼리
- Q1: 함수 → 범위, Covering 활용
- Q2: 서브쿼리 → JOIN
- ...

### 4.3 트랜잭션
- 자기 호출 → 빈 분리
- @Transactional(rollbackFor = Exception.class) 일괄 적용
- readOnly 트랜잭션 도입

### 4.4 시스템
- HikariCP max 10 → 30
- innodb_buffer_pool_size 2GB → 6GB

## 5. 결과 (재측정)

[Before/After 비교 표]

p99 5초 → 300ms (16x 개선)
데드락 12/일 → 1/일

## 6. 잔여 과제 / 향후 계획

- [ ] orders 테이블 RANGE 파티셔닝 (6월)
- [ ] read replica 도입 검토 (Q3)
- [ ] 분산 트레이싱 적용 (분석)
- [ ] 정기 슬로우 쿼리 점검 자동화

## 7. 부록
- 슬로우 로그 원본: /backups/slow_20260510-15.log
- EXPLAIN 결과: explain_results.txt
- 부하 테스트 결과: jmeter_report.html
```

---

## 채점 가이드

| 항목 | 점수 |
|---|---|
| 슬로우 쿼리 명확히 식별 (5개) | /15 |
| 각 쿼리 EXPLAIN + 원인 분석 | /20 |
| 인덱스 / 쿼리 재작성 적용 | /15 |
| 트랜잭션 점검 (자기 호출·rollbackFor·readOnly) | /15 |
| Before/After 정량 비교 | /15 |
| 보고서 가독성 | /10 |
| 잔여 과제 / 향후 계획 | /10 |
| **합계** | /100 |

70+ : 시니어 운영자 수준. 85+ : DBA 수준.

---

## 다음 단계

### 자격증

- IBM Certified Database Administrator — DB2 12 for z/OS or LUW
- Oracle Certified Professional MySQL DBA
- AWS Certified Database — Specialty

### 심화 도서

- 📘 『Database Internals』 (Petrov) — 깊이 있는 내부 구조
- 📘 『Designing Data-Intensive Applications』 (Kleppmann) — 분산 시스템
- 📘 『Database Reliability Engineering』 (Campbell, Majors) — SRE 관점

### 본인 환경 적용

- 회사 운영 DB의 매주 슬로우 쿼리 톱 10 자동 메일
- 분기 1회 백업 → 복구 훈련
- 신규 기능 코드 리뷰에 "EXPLAIN 첨부" 의무화
- 사내 위키에 본 보고서 형식 표준화

---

## 마무리

4주, 20 Day, 약 60시간 학습. 이 과정 후 본인은:

- SQL 함정 10가지를 즉시 식별
- DB2와 MySQL 양쪽에서 같은 문제를 풀 수 있음
- EXPLAIN을 보고 어디가 문제인지 즉답
- 인덱스를 의도해서 설계
- 격리수준·잠금·MVCC를 운영 관점에서 설명
- 데드락을 진단·회피
- Spring @Transactional의 함정을 모두 안다
- 백업·복제·HA 셋업 가능
- 매일 운영 점검 SQL 가지고 있음

… 가 단단해졌으리라 믿는다.

수고했습니다.

← 메인: [`../README.md`](../README.md)
