# Week 4 — 운영 · 백업 · 복제 · HA · 캡스톤

## 주차 목표

- 백업·복구 전략 수립 (논리 vs 물리, full vs incremental, PITR)
- DB2 HADR / MySQL replication 셋업 차이를 안다
- 파티셔닝으로 큰 테이블 관리
- 슬로우 쿼리 로그·모니터링으로 운영 지속 개선
- **캡스톤**: 실 운영 시나리오에서 진단 → 튜닝 → 보고서

## 일정표

| Day | 주제 | 핵심 산출물 |
|---|---|---|
| 1 | 백업·복구 (논리/물리/PITR) | 양쪽 DB 백업 후 복구 시연 |
| 2 | 복제 (DB2 HADR · MySQL Replication) | 마스터-슬레이브 셋업 |
| 3 | 파티셔닝 | RANGE 파티션 운영 시뮬레이션 |
| 4 | 슬로우 쿼리 · 모니터링 · 성능 인사이트 | 자동 진단 SQL 묶음 |
| 5 | **캡스톤** | 종합 진단 보고서 작성 |

## Java/Spring 매핑

| 익숙한 개념 | 운영 매핑 |
|---|---|
| `application-prod.yml` 분리 | 운영 DB 설정 (커넥션 풀, 타임아웃) |
| Spring `@Profile` | 마스터/슬레이브 분리 데이터소스 |
| Spring Cloud Config | DB 설정 외부화 |
| Actuator `/actuator/metrics` | 슬로우 쿼리 · 풀 상태 모니터링 |
| `@Async` | 백업·정리 작업 |

## 사전 점검

- [ ] Week 1~3 모두 ✅
- [ ] 캡스톤에 사용할 본인 코드/쿼리 1~2개 준비
- [ ] Docker로 추가 인스턴스 띄울 여유

다음: [`01_backup_recovery.md`](01_backup_recovery.md)
