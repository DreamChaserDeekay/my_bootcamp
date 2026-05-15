# Week 3 — 체크리스트

## IoC 컨테이너

- [ ] BeanFactory vs ApplicationContext 관계
- [ ] BeanDefinition이 무엇이고 어디서 만들어지는지
- [ ] `ApplicationContext.refresh()` 12단계
- [ ] BeanFactoryPostProcessor vs BeanPostProcessor 차이
- [ ] `@Configuration`의 CGLIB 강화 (proxyBeanMethods)
- [ ] `@ComponentScan`이 ASM으로 패키지 스캔
- [ ] `@Conditional` 평가 시점

## Bean 생명주기

- [ ] 14단계 생명주기 흐름
- [ ] @PostConstruct vs InitializingBean vs @Bean(initMethod)
- [ ] AOP 프록시가 postProcessAfterInitialization에서 만들어짐
- [ ] 생성자 주입을 권장하는 이유
- [ ] graceful shutdown 설정 (server.shutdown=graceful)

## AOP 프록시

- [ ] JDK Dynamic Proxy vs CGLIB 차이
- [ ] Spring Boot 기본은 CGLIB
- [ ] CGLIB의 final / private 제약
- [ ] Self-invocation 함정 (코드 흐름으로 설명)
- [ ] 해결 3가지 (자기 참조 / @Lazy / 클래스 분리)
- [ ] Around Advice 작성과 Pointcut 표현식

## @Transactional 내부

- [ ] TransactionInterceptor가 어떻게 트랜잭션을 관리하는지
- [ ] PlatformTransactionManager 3종 (DataSource / Jpa / Jta)
- [ ] Propagation 7가지 동작
- [ ] REQUIRED vs REQUIRES_NEW 차이
- [ ] rollbackFor 기본 규칙과 함정 (checked exception)
- [ ] readOnly의 효과 (JPA 환경)
- [ ] afterCommit 콜백 활용

## Event · Async · Scheduler

- [ ] ApplicationEvent 발행·수신 흐름 (기본 동기)
- [ ] @TransactionalEventListener phase 4가지
- [ ] @Async 동작 (TaskExecutor에 위임)
- [ ] @Async self-invocation 함정
- [ ] @Scheduled의 fixedRate vs fixedDelay vs cron
- [ ] Spring Boot 3.2+ Virtual Thread 통합

## 실습 결과

- [ ] Lab 5 — CGLIB 프록시 클래스 확인
- [ ] Lab 5 — Self-invocation으로 AOP 무시 재현
- [ ] Lab 5 — 3가지 해결법 적용
- [ ] Lab 6 — REQUIRES_NEW로 외부 롤백 + 별도 commit
- [ ] Lab 6 — Checked exception 자동 롤백 안 함 확인
- [ ] Lab 6 — afterCommit 콜백으로 외부 시스템 통합

## 자기 점검 질문

1. `ApplicationContext.refresh()`에서 AOP 프록시가 만들어지는 정확한 단계?
   <details><summary>답</summary>finishBeanFactoryInitialization → preInstantiateSingletons → getBean → createBean → initializeBean → applyBeanPostProcessorsAfterInitialization (AnnotationAwareAspectJAutoProxyCreator가 여기서 동작)</details>

2. `@Transactional` 메서드가 자기 클래스 안에서 다른 `@Transactional` 메서드를 호출하면?
   <details><summary>답</summary>self-invocation으로 AOP 프록시 우회. 트랜잭션은 외부 호출의 것만 적용. propagation 무시.</details>

3. `@Configuration` 클래스의 @Bean 메서드를 같은 클래스에서 호출하면?
   <details><summary>답</summary>CGLIB이 가로채서 BeanFactory에서 가져옴 → 항상 같은 singleton. (proxyBeanMethods=true 기본). false면 매번 new.</details>

4. `@PostConstruct` 던지면?
   <details><summary>답</summary>BeansException → ApplicationContext startup 실패 → SpringApplication.run() 예외. 컨테이너 종료.</details>

5. CGLIB이 `final` 클래스에 못 만드는 이유는?
   <details><summary>답</summary>CGLIB은 대상의 자식 클래스를 동적 생성. final이면 상속 불가. Kotlin은 spring 플러그인이 자동 open 처리.</details>

6. `@Transactional`이 `private` 메서드에 안 먹는 이유?
   <details><summary>답</summary>CGLIB이 만든 자식 클래스가 private을 override 못 함. JDK Dynamic Proxy는 인터페이스 메서드(=public)만 처리. private은 어차피 인터페이스에 없음.</details>

7. `IOException` 던지는 `@Transactional` 메서드, 자동 롤백되나?
   <details><summary>답</summary>아니. 기본은 RuntimeException과 Error만 자동 롤백. `@Transactional(rollbackFor = Exception.class)` 필요.</details>

8. `@Async` 메서드의 반환 타입을 `String`으로 하면?
   <details><summary>답</summary>비동기 실행은 되지만 반환값을 받을 수 없음. void만 가능. 값 받고 싶으면 `CompletableFuture<T>` 또는 `Future<T>`.</details>

---

## 통과했다면

Week 4 [Spring Boot 자동설정·부팅·캡스톤](../week4_spring_boot_capstone/00_overview.md)로!
