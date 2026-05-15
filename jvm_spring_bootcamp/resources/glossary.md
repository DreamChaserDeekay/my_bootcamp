# 용어집 (Glossary)

## A

- **AOP (Aspect-Oriented Programming)** — 횡단 관심사 모듈화
- **AOT (Ahead-Of-Time)** — GraalVM Native Image
- **ApplicationContext** — Spring IoC 컨테이너
- **AQS (AbstractQueuedSynchronizer)** — `j.u.c` 잠금의 기반
- **AsyncTaskExecutor** — Spring 비동기 실행 추상
- **AutoConfiguration** — Spring Boot 자동 Bean 등록
- **Atomic*** — `java.util.concurrent.atomic` — CAS 기반

## B

- **Bean** — Spring 컨테이너 관리 객체
- **BeanDefinition** — Bean의 메타데이터·설계도
- **BeanFactory** — IoC 컨테이너 인터페이스 (ApplicationContext의 부모)
- **BeanPostProcessor (BPP)** — Bean 인스턴스 후처리 훅
- **BeanFactoryPostProcessor (BFPP)** — BeanDefinition 후처리 훅
- **Bytecode** — `.class`의 JVM 명령어

## C

- **C1 / C2** — JIT 컴파일러 (Client / Server)
- **CAS (Compare-And-Swap)** — 락 없는 원자 업데이트
- **CGLIB** — 동적 자식 클래스 생성 (Spring AOP)
- **ClassLoader** — `.class` 파일 적재
- **Code Cache** — JIT 컴파일 결과 캐시
- **CommonPool** — `ForkJoinPool.commonPool()`, CompletableFuture 기본
- **CompletableFuture** — JDK 8+ 비동기 합성
- **Concurrent Mark** — G1·CMS의 동시 마킹 (STW 아님)

## D

- **Direct Memory** — `ByteBuffer.allocateDirect()`. Heap 외
- **DispatcherServlet** — Spring MVC front controller

## E

- **Eden** — Young Generation의 첫 영역
- **Escape Analysis** — JIT 최적화. 객체 탈출 분석
- **Event Loop** — 리액티브 모델의 스레드 (Netty)

## F

- **Field Injection** — `@Autowired private` (비권장)
- **Final Field** — JMM의 안전한 발행 보장
- **Flame Graph** — 프로파일링 시각화 (async-profiler)
- **Full GC** — 모든 영역 GC, STW 김
- **ForkJoinPool** — work-stealing 풀

## G

- **G1 GC** — Garbage First. JDK 9+ 기본
- **GC Root** — 참조 그래프의 출발점 (Stack/static 등)
- **GC Threshold** — Young → Old 승격 임계치
- **Graceful Shutdown** — 우아한 종료

## H

- **HappensBefore** — JMM의 순서 관계
- **Heap** — 객체가 사는 메모리. GC 대상
- **HikariCP** — Spring Boot 기본 connection pool
- **HotSpot** — Oracle/OpenJDK 표준 VM
- **Humongous Object** — G1 region 50%+ 객체

## I

- **IoC (Inversion of Control)** — 제어의 역전
- **Inlining** — JIT가 메서드 호출을 본문으로 펼침
- **Interpreter** — 바이트코드 한 줄씩 실행
- **InitializingBean** — Spring 초기화 콜백
- **InjectionPoint** — `@Autowired` 대상

## J

- **JIT (Just-In-Time)** — 자주 실행되는 코드를 기계어로
- **JFR (Java Flight Recorder)** — JDK 내장 프로파일러
- **JMM (Java Memory Model)** — 멀티스레드 메모리 가시성 규칙
- **JNI (Java Native Interface)** — 네이티브 코드 연결
- **JPA** — Java Persistence API (Hibernate 등 구현)
- **jstat / jcmd / jmap / jstack** — JDK 진단 도구

## K

- **Keyset Pagination** — Seek 방식 페이징 (DB)

## L

- **LaunchedClassLoader** — Spring Boot fat jar의 ClassLoader
- **LongAdder** — 분산 카운터 (AtomicLong보다 충돌 강함)

## M

- **MAT (Memory Analyzer Tool)** — Eclipse, 힙덤프 분석
- **Metaspace** — 클래스 메타데이터 (JDK 8+, Native 메모리)
- **Micrometer** — 메트릭 추상 (Spring Boot 통합)
- **Mission Control (JMC)** — JFR 분석 GUI
- **MVC (Model-View-Controller)** — Spring Web 패턴

## N

- **Native Memory Tracking (NMT)** — Heap 외 메모리 추적
- **Netty** — 비동기 NIO 프레임워크 (WebFlux 기반)

## O

- **Old Generation** — 장수 객체 영역
- **OOM (OutOfMemoryError)** — 8종

## P

- **Parallel GC** — throughput 우선 GC
- **PermGen** — 옛 클래스 영역. JDK 8에서 제거 (→ Metaspace)
- **Pinning** — Virtual Thread가 carrier에 묶임
- **Pointcut** — AOP의 매칭 표현식
- **PostConstruct** — `@PostConstruct`, 초기화 콜백
- **Propagation** — `@Transactional`의 전파 정책 (REQUIRED 등)
- **Prototype Scope** — 매 요청 새 Bean
- **Proxy** — 동적으로 만든 wrapper

## Q

- **Quartz** — 클러스터 가능 스케줄러

## R

- **REQUIRED / REQUIRES_NEW** — Propagation 모드
- **Reactive** — Mono/Flux, 논블로킹
- **Reachability** — 참조 그래프 도달 가능성
- **Reentrant** — 재진입 가능한 잠금
- **Reference** — Strong/Soft/Weak/Phantom

## S

- **Singleton Scope** — 기본 Bean scope
- **SLF4J** — 로깅 추상
- **Spring Boot Starter** — autoconfigure + 의존성 묶음
- **Spring Initializr** — start.spring.io
- **STW (Stop-The-World)** — GC가 앱 스레드 정지
- **Survivor (S0, S1)** — Young Generation의 보조 영역

## T

- **Tomcat** — Spring Boot 기본 내장 서버
- **Tiered Compilation** — C1 → C2 점진 최적화
- **TransactionManager** — JDBC/JPA/JTA 트랜잭션 관리
- **TransactionSynchronizationManager** — 스레드 ThreadLocal에 Connection 등 보관
- **ThreadPoolExecutor** — JDK 스레드 풀

## U

- **Unsafe** — JDK 내부 API. JDK 23부터 deprecate

## V

- **Virtual Thread** — JDK 21+ Loom, 경량 스레드
- **volatile** — 가시성·재정렬 금지

## W

- **WebApplicationType** — NONE / SERVLET / REACTIVE
- **WebFlux** — Reactor 기반 리액티브 웹
- **Worker Thread** — Tomcat 등의 요청 처리 스레드

## X

- **Xms / Xmx / Xss** — JVM 메모리 옵션

## Y

- **Young Generation** — 새 객체 영역

## Z

- **ZGC** — low-latency GC (JDK 15+ production)
- **Zero-Copy** — DMA로 커널-사용자 메모리 복사 회피
