# JVM · Spring 내부 부트캠프

> "Spring을 쓴다"에서 "Spring을 안다"로. JVM과 Spring의 **블랙박스를 열어** 운영 사고를 추적하고 면접에서 답할 수 있는 수준으로.

---

## 학습 목표

이 부트캠프를 마치면:

- JVM 메모리 영역과 GC 알고리즘(G1/ZGC/Shenandoah)을 설명하고 GC 로그를 읽을 수 있다
- 힙덤프·스레드덤프를 떠서 OOM·데드락·CPU 폭주의 원인을 파악할 수 있다
- 클래스로더 위임 모델·JIT 컴파일·Java Memory Model을 이해한다
- Spring `ApplicationContext` refresh 12단계를 그릴 수 있다
- `@Transactional` self-invocation·CGLIB final 제약 등 AOP 프록시의 함정을 안다
- Spring Boot AutoConfiguration이 어떻게 Bean을 골라 등록하는지 추적할 수 있다
- 내장 Tomcat·Reactor Netty 차이와 선택 기준을 설명한다
- 실제 운영 사고를 재현해보고 진단·해결할 수 있다

---

## 사전 준비

| 구성 요소 | 버전 |
|---|---|
| **JDK** | 21 LTS (Temurin 또는 Oracle) |
| **Spring Boot** | 3.3.x |
| **Build** | Gradle 8.x |
| **IDE** | IntelliJ IDEA (Community도 가능) |
| **OS** | Windows 10/11 (PowerShell) — Linux/WSL 가능 |
| **APM** | Pinpoint or Scouter (선택, Week 4) |
| **분석 도구** | JFR + Mission Control, VisualVM, async-profiler |

### 빠른 검증

```powershell
# JDK 확인
java -version
javac -version

# JDK 도구 확인
jps
jstack -h
jmap -h
jcmd -h

# Mission Control (또는 GraalVM Mission Control) 설치 권장
# https://www.oracle.com/java/technologies/jdk-mission-control.html
```

---

## 디렉토리 구조

```
jvm_spring_bootcamp/
├── README.md                          ← 지금 이 문서
├── week1_jvm_memory_gc/               ← JVM 구조·메모리·GC·튜닝
│   ├── 00_overview.md
│   ├── 01_jvm_architecture.md
│   ├── 02_memory_areas.md
│   ├── 03_gc_algorithms.md
│   ├── 04_gc_tuning.md
│   ├── 05_heap_thread_dump.md
│   ├── labs/
│   └── checklist.md
├── week2_classloader_jit_concurrency/ ← ClassLoader·JIT·JMM·동시성
│   ├── 00_overview.md
│   ├── 01_classloader.md
│   ├── 02_bytecode_jit.md
│   ├── 03_jmm_happens_before.md
│   ├── 04_concurrent_internals.md
│   ├── 05_executor_virtual_thread.md
│   ├── labs/
│   └── checklist.md
├── week3_spring_core_internals/       ← IoC·Bean 생명주기·AOP·트랜잭션
│   ├── 00_overview.md
│   ├── 01_ioc_container.md
│   ├── 02_bean_lifecycle.md
│   ├── 03_aop_proxy.md
│   ├── 04_transactional_internal.md
│   ├── 05_event_async.md
│   ├── labs/
│   └── checklist.md
├── week4_spring_boot_capstone/        ← 부팅·자동설정·내장서버·캡스톤
│   ├── 00_overview.md
│   ├── 01_boot_startup.md
│   ├── 02_auto_configuration.md
│   ├── 03_starter_actuator.md
│   ├── 04_embedded_servers.md
│   ├── 05_capstone.md
│   ├── labs/
│   └── checklist.md
├── practice_app/                      ← 트러블슈팅 재현 미니앱
│   ├── README.md
│   ├── build.gradle
│   └── src/...
└── resources/
    ├── jvm_options.md                 ← JVM 옵션 치트시트
    ├── gc_log_reading.md              ← GC 로그 읽는 법
    ├── tools.md
    ├── books_and_courses.md
    ├── glossary.md
    ├── quick_reference.md             ← 한 페이지 카드
    └── troubleshooting_playbook.md    ← 증상별 진단
```

---

## 주차 흐름

### Week 1 — JVM 메모리·GC·튜닝
JVM 구조 (Class Loader → Runtime Data Area → Execution Engine). 메모리 영역(Heap/Stack/Metaspace/PC/Native). GC 알고리즘 비교(Serial/Parallel/G1/ZGC/Shenandoah). GC 로그 읽기. 힙·스레드 덤프 분석.

### Week 2 — ClassLoader·JIT·JMM·동시성
ClassLoader 위임 모델, 동적 로딩. JIT (C1/C2/Graal), 인라이닝, escape analysis. Java Memory Model — volatile, happens-before. AQS·ConcurrentHashMap·ForkJoinPool 내부. Virtual Thread (Loom).

### Week 3 — Spring Core 내부
`BeanFactory` vs `ApplicationContext`, `BeanDefinition`. Bean 생명주기 14단계. `BeanPostProcessor`·`BeanFactoryPostProcessor`. AOP 프록시 (JDK Dynamic vs CGLIB), self-invocation 함정. `@Transactional` 내부 (`TransactionInterceptor` → `PlatformTransactionManager`). Propagation·Isolation 동작.

### Week 4 — Spring Boot 자동설정·부팅·캡스톤
`SpringApplication.run()` 단계별 (Environment → ApplicationContext → refresh → CommandLineRunner). `@EnableAutoConfiguration` 메커니즘 (`spring.factories` → `AutoConfiguration.imports`). `@Conditional` 패턴. Custom Starter. Tomcat vs Reactor Netty. Actuator·Micrometer. 캡스톤: 운영 사고 5종 진단·보고서.

---

## 추천 학습 페이스

| 일정 | Day 단위 |
|---|---|
| **5일 / 주 (평일 1h + 주말 2h)** | 평일에 1 Day씩, 주말에 lab 진행 |
| **주말 집중 (주말 6h)** | 토요일 Day 1-3, 일요일 Day 4-5 + lab |
| **2달 분산 (느린 페이스)** | 1주에 2-3 Day씩, 깊이 우선 |

각 Day는 **본문 30~50분 + 실습 30~60분** 정도 예상.

---

## 시작하기

1. [practice_app/README.md](practice_app/README.md)로 실습 앱 빌드 검증
2. [week1/00_overview.md](week1_jvm_memory_gc/00_overview.md)부터 순서대로 진행
3. 각 주차 끝에서 `checklist.md`로 자가 점검
4. 중간에 막히면 [resources/troubleshooting_playbook.md](resources/troubleshooting_playbook.md) 참조
5. Week 4 캡스톤은 **운영 사고 시나리오 재현·진단·보고서 작성**

---

## 이 부트캠프의 차별점

- **"왜 그렇게 동작하는가"** 우선. API 사용법은 공식 문서로 충분
- **Java/Spring 코드와 함께 보는 내부 흐름** — Spring 소스 단계 추적
- **운영 사고 사례 중심** — 메모리 누수, GC 폭주, 데드락, AOP 함정 등 실제 사건
- **JVM ↔ Spring 연결** — `@Transactional`이 왜 같은 클래스 호출에서 안 먹는가, Bean 초기화에서 왜 OOM이 나는가
- **금융권/Java 운영 환경** 가정 — Pinpoint/Scouter 활용 포함

---

## 다음 단계

이 부트캠프 이후:

- **DevOps·컨테이너** — JVM 컨테이너 친화 옵션, K8s 메모리 limit
- **관측·SRE** — Micrometer + Prometheus + Grafana 실전
- **메시지큐·캐시·검색** — Redis/Kafka/ES (Spring 통합 관점)

---

> *"You don't really understand something unless you can explain it to your computer."* — Donald Knuth (변형)
