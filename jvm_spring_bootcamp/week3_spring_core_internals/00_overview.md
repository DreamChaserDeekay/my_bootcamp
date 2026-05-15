# Week 3 — Spring Core 내부

## 주차 목표

`@Service`·`@Autowired`·`@Transactional`이 어떻게 동작하는지 **소스 수준에서 안다**. `ApplicationContext` refresh의 12단계, Bean 생명주기, AOP 프록시(JDK Dynamic vs CGLIB), `@Transactional` 내부 흐름.

---

## 일정

| Day | 주제 | 핵심 |
|---|---|---|
| Day 1 | [IoC 컨테이너](01_ioc_container.md) | BeanFactory vs ApplicationContext, BeanDefinition, refresh 12단계 |
| Day 2 | [Bean 생명주기](02_bean_lifecycle.md) | 14단계 흐름, BeanPostProcessor, `@PostConstruct` |
| Day 3 | [AOP 프록시](03_aop_proxy.md) | JDK Dynamic vs CGLIB, self-invocation 함정 |
| Day 4 | [@Transactional 내부](04_transactional_internal.md) | TransactionInterceptor, Propagation·Isolation 동작 |
| Day 5 | [Event·Async](05_event_async.md) | ApplicationEvent, @Async, TaskExecutor 통합 |

### Lab

| Lab | 내용 |
|---|---|
| [lab5_aop_trace.md](labs/lab5_aop_trace.md) | AOP 프록시 추적 + CGLIB vs JDK Dynamic 비교 |
| [lab6_transactional_pitfall.md](labs/lab6_transactional_pitfall.md) | self-invocation 함정 + Propagation 실험 |

---

## 학습 결과

- [ ] `SpringApplication.run()` → `refresh()`의 12단계 흐름을 안다
- [ ] BeanDefinition·BeanFactory·ApplicationContext 관계를 그린다
- [ ] BeanPostProcessor가 어디서 호출되는지 안다
- [ ] AOP 프록시가 JDK Dynamic / CGLIB 어느 쪽으로 생성되는지 결정 규칙
- [ ] `@Transactional`이 same-class 호출에서 안 먹는 이유 (코드 수준)
- [ ] Propagation 7 모드와 실전 활용
- [ ] `@Async`가 ThreadPool에 어떻게 위임하는지 안다

---

## Week 3을 마치면 답할 수 있어야

1. Bean 의존성 주입은 정확히 언제 발생?
2. `@Configuration`과 `@Component`의 내부 차이는?
3. `@Transactional(readOnly=true)`가 정말로 read-only를 보장? (Hibernate vs JDBC)
4. Spring AOP가 인터페이스 없는 클래스에 어떻게 동작?
5. `@Async` 메서드가 self-invocation으로 호출되면?
6. `@Transactional`이 `private` 메서드에 안 먹는 이유?
7. ApplicationContext가 닫힐 때 일어나는 일?
