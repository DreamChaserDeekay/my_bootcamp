# Week 4 — Spring Boot 자동설정·부팅·캡스톤

## 주차 목표

`SpringApplication.run()`이 시작에서 끝까지 무엇을 하는지, `@EnableAutoConfiguration`이 어떻게 200+개 Bean을 골라 등록하는지, Tomcat·Reactor Netty가 어떻게 임베디드되는지를 안다. 마지막 캡스톤에서 운영 사고 5종을 진단·보고한다.

---

## 일정

| Day | 주제 | 핵심 |
|---|---|---|
| Day 1 | [Boot 시작 흐름](01_boot_startup.md) | `SpringApplication.run()` 12단계, 이벤트 7종 |
| Day 2 | [Auto-Configuration](02_auto_configuration.md) | `@EnableAutoConfiguration` 메커니즘, Conditional 패턴 |
| Day 3 | [Starter · Actuator](03_starter_actuator.md) | Custom Starter 만들기, Actuator/Micrometer |
| Day 4 | [내장 서버](04_embedded_servers.md) | Tomcat vs Jetty vs Undertow vs Reactor Netty |
| Day 5 | [캡스톤](05_capstone.md) | 운영 사고 5종 진단 + 보고서 |

### Lab

| Lab | 내용 |
|---|---|
| [lab7_auto_config_trace.md](labs/lab7_auto_config_trace.md) | AutoConfiguration 디버그·conditions 분석 |

---

## 학습 결과

- [ ] `SpringApplication.run()`의 12단계를 안다
- [ ] AutoConfiguration이 `AutoConfiguration.imports`에서 시작
- [ ] `@Conditional` 패턴 7가지 (Class, Property, Bean, ...)
- [ ] Starter의 구조 (autoconfigure + starter 모듈)
- [ ] Actuator의 endpoint, Micrometer 통합
- [ ] Tomcat·Reactor Netty의 임베디드 메커니즘
- [ ] 운영 사고 5종을 정리한 보고서 작성

---

## Week 4를 마치면 답할 수 있어야

1. `SpringApplication.run()` 직후 가장 먼저 실행되는 사용자 코드?
2. AutoConfiguration이 어떤 순서로 적용되나?
3. `@ConditionalOnMissingBean`이 어떻게 사용자 Bean을 존중하나?
4. Tomcat이 부팅 중 언제 시작되나?
5. Reactor Netty 모드는 언제 어떻게 활성화?
6. Actuator `/health`의 동작과 커스터마이즈 방법?
7. 운영서에서 본 `@ConditionalOnProperty` 미스 — 어떻게 디버그?
