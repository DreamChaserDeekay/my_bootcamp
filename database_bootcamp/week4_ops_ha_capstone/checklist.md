# Week 4 자가 점검 체크리스트

## 백업·복구 (Day 1)

- [ ] RPO·RTO 의미와 시스템별 권장값
- [ ] 논리 vs 물리 백업 trade-off
- [ ] mysqldump `--single-transaction` 의 의미
- [ ] DB2 archive log 모드와 PITR
- [ ] 백업은 검증되어야 백업
- [ ] 3-2-1 백업 규칙

## 복제·HA (Day 2)

- [ ] 비동기/반동기/동기 복제의 RPO·성능 trade-off
- [ ] MySQL 8.0 SOURCE/REPLICA 용어
- [ ] GTID 기반 복제의 장점
- [ ] DB2 HADR 4가지 SYNCMODE
- [ ] Spring `LazyConnectionDataSourceProxy`로 readOnly 라우팅
- [ ] 복제는 백업 대체 아님 (DROP 즉시 복제됨)

## 파티셔닝 (Day 3)

- [ ] 파티셔닝의 4가지 효과
- [ ] 파티션 키가 WHERE에 없으면 효과 없음
- [ ] DROP PARTITION vs DELETE의 차이
- [ ] MySQL의 partition + unique 충돌
- [ ] 파티셔닝과 샤딩의 차이

## 슬로우 쿼리·모니터링 (Day 4)

- [ ] MySQL slow log 활성화 + pt-query-digest 분석
- [ ] sys schema 핵심 view 5개 이상
- [ ] DB2 MON_GET_PKG_CACHE_STMT
- [ ] db2pd, db2top 사용
- [ ] HikariCP 메트릭 (active, pending, leak-detection)
- [ ] Spring Actuator + Prometheus 연동

## 캡스톤 (Day 5)

- [ ] 슬로우 쿼리 5개 식별
- [ ] 각 쿼리 EXPLAIN + 원인 분석
- [ ] 인덱스/쿼리 재작성 적용
- [ ] 트랜잭션 점검 (자기 호출, rollbackFor, readOnly)
- [ ] Before/After 비교
- [ ] 보고서 작성

## 전체 부트캠프 졸업 점검

- [ ] DB2와 MySQL의 큰 그림 차이를 즉답
- [ ] ANSI SQL과 방언 구별
- [ ] EXPLAIN 보고 즉시 풀스캔/Covering/JOIN 알고리즘 파악
- [ ] 인덱스 무력화 5패턴 즉답
- [ ] 격리수준 × 이상 현상 매트릭스 외움
- [ ] 데드락 발생 시 진단 명령 즉시 사용
- [ ] Spring `@Transactional` 함정 모두 인지
- [ ] 백업 → 복구 시연 1회 이상
- [ ] 복제 셋업 1회 이상
- [ ] 슬로우 쿼리 일일 점검 SQL 가짐
- [ ] 캡스톤 보고서 작성

---

축하합니다. 부트캠프 졸업입니다.

← 메인: [`../README.md`](../README.md)
