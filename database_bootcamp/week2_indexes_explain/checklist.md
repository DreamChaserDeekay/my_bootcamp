# Week 2 자가 점검 체크리스트

## 스토리지·구조 (Day 1)

- [ ] 페이지·익스텐트·테이블스페이스의 의미
- [ ] InnoDB clustered index vs DB2 heap+PK index 구조 차이
- [ ] 무작위 PK가 InnoDB에서 페이지 분할을 일으키는 이유
- [ ] 큰 PK가 모든 세컨더리 인덱스를 비대하게 만드는 이유
- [ ] 버퍼풀 크기 조정의 효과

## B-tree 인덱스 (Day 2)

- [ ] B+tree 구조와 검색 비용 (1억 행 = 3~4 IO)
- [ ] selectivity와 cardinality 차이
- [ ] 인덱스 무력화 5가지 패턴 (함수, 부정, 좌측 와일드카드, 형변환, OR)
- [ ] 인덱스 비용 (저장공간·쓰기 추가 IO·유지)
- [ ] 사용 안 되는 인덱스 찾는 쿼리

## 복합·Covering·함수 인덱스 (Day 3)

- [ ] leftmost prefix 규칙
- [ ] 컬럼 순서 우선순위 (equality → range → order by)
- [ ] Covering Index의 효과 (Index-Only Scan)
- [ ] DB2 INCLUDE vs MySQL 컬럼 추가 차이
- [ ] 함수 기반 인덱스 사용 (DB2 / MySQL 8+)
- [ ] FK 컬럼에 인덱스 없을 때의 위험

## EXPLAIN 해석 (Day 4)

- [ ] MySQL type 좋은 순서를 안다 (system > const > eq_ref > ref > range > index > ALL)
- [ ] Extra의 Using index (Covering) / Using filesort / Using temporary 의미
- [ ] EXPLAIN ANALYZE로 실제 vs 추정 비교
- [ ] DB2 db2expln의 IXSCAN, TBSCAN, FETCH, NLJOIN 노드 해석
- [ ] JOIN 알고리즘 3가지 (Nested Loop, Hash, Merge) 차이

## 옵티마이저·통계 (Day 5)

- [ ] 옵티마이저가 통계 기반 비용 추정으로 계획 선택함을 안다
- [ ] ANALYZE TABLE / RUNSTATS 사용
- [ ] 히스토그램이 필요한 경우 (비균등 분포)
- [ ] 힌트는 마지막 수단임을 인지하고 사용 시 주석 작성
- [ ] MySQL `USE INDEX`, `STRAIGHT_JOIN`, `/*+ ... */` 힌트
- [ ] 통계 갱신만으로 풀리는 문제를 힌트로 봉합하지 않는다

## 실습

- [ ] Lab 3 (인덱스 튜닝): 5 쿼리에 최적 인덱스 설계·검증
- [ ] Lab 4 (EXPLAIN 워크스루): 10가지 쿼리 패턴 분석

---

다음: [Week 3 — 트랜잭션 · 잠금 · Spring 연동](../week3_transactions_concurrency/00_overview.md)
