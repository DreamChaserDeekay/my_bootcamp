# Week 2 — 실행계획 · 인덱스 · 옵티마이저

## 주차 목표

- 스토리지 엔진(InnoDB, DB2) 내부 구조와 **clustered/secondary 인덱스** 차이를 안다
- B-tree 인덱스가 어떻게 동작하는지 설명한다
- **복합 인덱스 컬럼 순서**, **Covering Index**, **함수 기반 인덱스**를 의도해서 설계한다
- DB2 `EXPLAIN` + `db2expln` / MySQL `EXPLAIN` + `EXPLAIN ANALYZE`를 자유롭게 해석한다
- 옵티마이저 동작 (통계·비용 모델)을 이해한다
- 옵티마이저 힌트와 그 위험을 안다

## 일정표

| Day | 주제 | 핵심 산출물 |
|---|---|---|
| 1 | 스토리지 엔진 · clustered index | InnoDB vs DB2 페이지·테이블스페이스 구조 그림 |
| 2 | B-tree 인덱스 동작 원리 | 직접 인덱스 만들고 풀스캔 vs 인덱스 스캔 비교 |
| 3 | 복합 인덱스 · Covering · 함수 인덱스 | 컬럼 순서 변경에 따른 EXPLAIN 차이 |
| 4 | EXPLAIN 해석 (양쪽) | 같은 쿼리의 DB2 vs MySQL 계획 비교 |
| 5 | 옵티마이저 · 통계 · 힌트 | RUNSTATS / ANALYZE TABLE 효과 검증 |

## Java/Spring 매핑

| 익숙한 개념 | DB 인덱스 매핑 |
|---|---|
| Java `HashMap` | 해시 인덱스 (DB에서는 흔치 않음, 메모리 캐시) |
| Java `TreeMap` | B-tree 인덱스 |
| Stream 정렬 후 처리 | ORDER BY 컬럼에 인덱스 있으면 정렬 생략 |
| List에서 indexOf | 인덱스 없으면 O(N), 있으면 O(log N) |
| Spring Data `findByEmail` | email 컬럼에 인덱스 필요 |
| Spring Data `findByXAndY` | 복합 인덱스 (X, Y) — 순서 중요 |

## 사전 점검

- [ ] Week 1 checklist ✅
- [ ] practice_db에 적당량 데이터 (만 단위)
- [ ] DBeaver 또는 CLI로 EXPLAIN 실행 가능

다음: [`01_storage_engines.md`](01_storage_engines.md)
