# Week 4 — 체크리스트

## Boot 시작 흐름

- [ ] `SpringApplication.run()`의 12단계
- [ ] 7개 라이프사이클 이벤트 + ContextClosedEvent
- [ ] WebApplicationType 자동 감지 규칙
- [ ] application.yml 13가지 우선순위 (override 순서)
- [ ] Profile 활용
- [ ] startup tracking (`/actuator/startup`)
- [ ] CommandLineRunner vs ApplicationRunner

## Auto-Configuration

- [ ] `AutoConfiguration.imports` 파일 형식
- [ ] `@Conditional` 7가지 패턴
- [ ] @ConditionalOnMissingBean의 양보 메커니즘
- [ ] @AutoConfiguration after/before
- [ ] `--debug` 또는 /actuator/conditions로 매치 확인
- [ ] AutoConfig 제외 (`exclude`)

## Starter · Actuator · Micrometer

- [ ] Starter 표준 구조 (autoconfigure + starter)
- [ ] Custom Starter 작성
- [ ] Actuator 주요 endpoint 10개
- [ ] /actuator/loggers로 동적 로그 레벨
- [ ] /actuator/heapdump
- [ ] Custom Health Indicator
- [ ] Micrometer Counter/Timer/Gauge
- [ ] Prometheus 노출

## 내장 서버

- [ ] Tomcat 내장 메커니즘
- [ ] 4가지 서버 차이 (Tomcat/Jetty/Undertow/Reactor Netty)
- [ ] 서블릿 vs 리액티브 모델
- [ ] Tomcat 튜닝 옵션 (threads, connection)
- [ ] Connection / Thread / Queue 관계
- [ ] graceful shutdown + k8s preStop

## 캡스톤

- [ ] 5가지 사고 시나리오 모두 진행
- [ ] 각 시나리오 보고서 작성
- [ ] 자기만의 즐겨찾기 명령어 정리

## 실습 결과

- [ ] Lab 7 — Custom Starter 만들고 사용

## 자기 점검 질문

1. application.yml에 같은 키가 있을 때 어느 것이 이기나?
   <details><summary>답</summary>External(env, command-line) > External(file) > classpath > 빌드 시 properties. 정확히 13단계. 자세한 건 Day 1.</details>

2. AutoConfigurationImportSelector가 import한 클래스의 순서를 어떻게 보장하나?
   <details><summary>답</summary>@AutoConfiguration(after=..., before=...)로 토폴로지 정렬. 구체적 순서는 Boot가 결정.</details>

3. /actuator/heapdump 호출 시 일어나는 일?
   <details><summary>답</summary>jmap -dump:live와 같은 효과. Heap 크기만큼 .hprof 응답. 운영서 보안 조심.</details>

4. Custom Starter에서 `spring.factories`와 `AutoConfiguration.imports` 차이?
   <details><summary>답</summary>spring.factories는 Boot 2.x까지 다용도(EnableAutoConfiguration 외에도). 2.7+에 AutoConfiguration.imports 도입 (전용·우선). 3.x에선 imports 권장, factories는 deprecate.</details>

5. Tomcat max-threads=200인데 동시 1000 요청 받으면?
   <details><summary>답</summary>200 처리, max-connections(8192)까지 큐. 큐도 차면 accept-count(100) OS 큐. 그것도 넘으면 거부. 큐 안의 요청은 timeout 대기.</details>

6. Reactor Netty 모드에서 JPA를 쓰면?
   <details><summary>답</summary>JPA가 blocking → event loop 막힘 → 다른 요청 영향. R2DBC로 옮기거나 boundedElastic scheduler에 subscribeOn. 또는 Tomcat + VT로 전환.</details>

7. AutoConfiguration이 활성/비활성을 디버그하는 가장 빠른 방법?
   <details><summary>답</summary>/actuator/conditions endpoint (또는 --debug 모드). positiveMatches/negativeMatches로 조건별 결과.</details>

---

## 통과했다면

[Capstone](05_capstone.md)으로!
