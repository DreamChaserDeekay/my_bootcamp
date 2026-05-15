# Week 1 자가 점검 체크리스트

## DB2 vs MySQL 큰 그림 (Day 1)

- [ ] 두 DB의 출시·라이선스·주 사용처 차이를 안다
- [ ] MySQL의 스토리지 엔진 종류와 InnoDB를 기본으로 쓰는 이유
- [ ] AUTO_INCREMENT vs IDENTITY 구문 차이를 안다
- [ ] `utf8` vs `utf8mb4` 차이를 안다
- [ ] `||` 연산자가 DB2(연결)와 MySQL(OR) 에서 다름을 안다
- [ ] 양쪽 컨테이너에 동일 스키마 적용에 성공

## JOIN · 서브쿼리 · CTE (Day 2)

- [ ] INNER/LEFT/SEMI/ANTI JOIN을 의도해서 골라 쓴다
- [ ] `NOT IN`의 NULL 함정을 알고 `NOT EXISTS`로 대체한다
- [ ] LEFT JOIN의 우측 WHERE 조건이 INNER로 만드는 함정을 안다
- [ ] CTE로 복잡 쿼리를 단계 분해한다
- [ ] 재귀 CTE로 계층 구조를 탐색했다 (`RECURSIVE`)

## 윈도우 함수 (Day 3)

- [ ] GROUP BY와 윈도우 함수의 결과 행 수 차이를 안다
- [ ] `ROW_NUMBER`, `RANK`, `DENSE_RANK` 차이를 동점 예제로 안다
- [ ] `LAG/LEAD`로 이전·다음 행 비교
- [ ] 누적 합계 / 이동 평균을 `ROWS BETWEEN`으로 작성
- [ ] `LAST_VALUE` 기본 프레임 함정을 안다
- [ ] 윈도우 함수는 WHERE에 못 쓰는 이유 (평가 순서)

## 페이징·집계 (Day 4)

- [ ] DB2의 `OFFSET FETCH`와 MySQL `LIMIT` 차이
- [ ] OFFSET 깊은 페이지의 성능 한계
- [ ] 키 기반(seek) 페이지네이션 작성 가능
- [ ] WHERE/GROUP BY/HAVING의 평가 순서
- [ ] ROLLUP, CUBE, GROUPING SETS 차이 (MySQL은 CUBE 미지원)
- [ ] 조건부 집계(`COUNT(CASE WHEN ... THEN 1 END)`, MySQL `SUM(condition)`)

## SQL 함정 (Day 5)

- [ ] NULL 비교 (`=` 아닌 `IS NULL`)
- [ ] 함수가 컬럼에 씌워지면 인덱스 무효
- [ ] 암시적 형변환의 위험
- [ ] DECIMAL을 돈에 사용
- [ ] WHERE 없는 UPDATE/DELETE 절대 금지
- [ ] ORDER BY 없는 LIMIT의 비결정성
- [ ] UNION ALL이 기본, UNION은 중복 제거 의도일 때만

## 실습

- [ ] Docker로 DB2 + MySQL 동시 기동
- [ ] Lab 2 10문제 양쪽 DB로 풀이 + 차이 정리

---

다음: [Week 2 — 실행계획 · 인덱스 · 옵티마이저](../week2_indexes_explain/00_overview.md)
