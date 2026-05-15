# Week 3 자가 점검 체크리스트

## ACID · 격리수준 (Day 1)

- [ ] ACID 4가지 의미
- [ ] 4가지 격리수준 × 4가지 이상 현상 매트릭스
- [ ] DB2 기본 = CS (Read Committed), MySQL 기본 = REPEATABLE READ
- [ ] DB2 약자 (UR/CS/RS/RR)와 표준 매핑
- [ ] InnoDB의 RR이 phantom도 막는 이유 (next-key lock)
- [ ] Lost Update가 격리수준만으로는 안 풀림

## 잠금 모델 (Day 2)

- [ ] S/X 잠금 호환성
- [ ] DB2 lock escalation 메커니즘과 회피
- [ ] InnoDB row / gap / next-key lock 차이
- [ ] `SELECT ... FOR UPDATE NOWAIT / SKIP LOCKED` 활용
- [ ] DB2 `LOCKTIMEOUT = -1`이 위험
- [ ] WHERE 인덱스 없을 때 잠금 범위 폭주

## 데드락 (Day 3)

- [ ] 데드락 정의 (순환 대기)
- [ ] 자동 감지·victim 롤백 동작
- [ ] MySQL `SHOW ENGINE INNODB STATUS` 해석
- [ ] DB2 LOCK_EVENT 활용
- [ ] 회피 5패턴 (순서·짧은 트랜잭션·인덱스·재시도·낙관적)
- [ ] 데드락 재시도는 정상 응답

## MVCC (Day 4)

- [ ] 잠금 기반 vs MVCC 차이
- [ ] InnoDB undo log + read view
- [ ] DB2 currently committed
- [ ] 긴 트랜잭션의 위험 (undo log 누적)
- [ ] History list length 모니터링

## Spring `@Transactional` (Day 5)

- [ ] AOP 프록시 동작 (자기 호출 함정)
- [ ] `rollbackFor` 기본값이 RuntimeException만
- [ ] propagation 7가지, 자주 쓰는 3가지 의미
- [ ] `readOnly = true` 효과
- [ ] JPA `@Version` 낙관적 잠금 + 재시도
- [ ] JPA + MyBatis 1차 캐시 함정과 명시적 flush
- [ ] 트랜잭션 안 외부 호출 금지

## 실습

- [ ] Lab 5: 격리수준 × 이상 현상 매트릭스 채우기 (양쪽 DB)
- [ ] Lab 6: 데드락 재현 + 진단 + 회피 3패턴 적용

---

다음: [Week 4 — 운영 · 백업 · 복제 · HA · 캡스톤](../week4_ops_ha_capstone/00_overview.md)
