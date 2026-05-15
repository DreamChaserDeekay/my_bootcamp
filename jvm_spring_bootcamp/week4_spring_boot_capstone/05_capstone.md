# Day 5 — 캡스톤: 운영 사고 5종 진단 + 보고서

## 목표

JVM·Spring 부트캠프를 마무리하며 **실제 운영 사고를 재현·진단·복구하고 보고서를 작성**한다. 시뮬레이션 코드는 `practice_app/`에 있다 (별도 lab으로 진행).

---

## 캡스톤 작업

다음 5가지 시나리오를 모두 진행하고 각각 보고서 작성.

| # | 사고 | 핵심 진단 도구 |
|---|---|---|
| 1 | Heap OOM (메모리 누수) | jmap, MAT |
| 2 | Full GC 폭주 | gceasy, jstat |
| 3 | Tomcat 스레드 고갈 | jstack, /actuator |
| 4 | @Transactional 무력화 (self-invocation) | 로그, AOP 디버그 |
| 5 | Direct Memory 누수 (Netty) | NMT, jcmd |

---

## 시나리오 1 — Heap OOM

### 재현

`practice_app/`의 `LeakyController` 호출 → 1만 번 hit → 약 5분 후 OOM.

```bash
# 부하
for i in 1..10000; do curl http://localhost:8080/leak; done

# 동시에 모니터
jstat -gcutil <pid> 2000
```

### 진단 단계

```bash
# 1. /actuator/metrics 확인
curl /actuator/metrics/jvm.memory.used | jq

# 2. GC 추이
jstat -gcutil <pid> 1000 30

# 3. 힙덤프 (OOM 자동 또는 명시적)
jcmd <pid> GC.heap_dump /tmp/heap.hprof
# 또는 운영용
curl /actuator/heapdump -o heap.hprof

# 4. MAT/Mission Control로 분석
# Leak Suspects Report → 어느 클래스가 메모리 점유?
```

### 보고서 템플릿

```markdown
## 사고 보고서 — Heap OOM

### 일시
2026-MM-DD HH:MM ~ HH:MM

### 증상
- /actuator/health: status=DOWN
- Latency p99: 평소 50ms → 30,000ms
- 로그: java.lang.OutOfMemoryError: Java heap space
- pod restart 3회

### 진단 절차
1. /actuator/metrics/jvm.memory.used 추세 확인 → 단조증가 패턴
2. jstat -gcutil로 GC 빈도·Old 사용률 확인 → Old 99% 지속
3. /actuator/heapdump로 .hprof 추출
4. MAT Leak Suspects → com.example.LeakyController.HISTORY 의심
5. 코드 확인 → static List에 요청 데이터를 무한 누적

### 원인
LeakyController.java:15에 `static final List<RequestData> HISTORY = new ArrayList<>()`,
모든 요청 데이터를 추가하나 제거 로직 없음.

### 조치
1. 즉시: pod 메모리 limit 2배로 임시 증가, 30분 후 재시작
2. 단기 (당일 hotfix): HISTORY를 LinkedHashMap에 1000개 제한
3. 장기 (1주일): 메트릭은 Micrometer로, 데이터는 외부 store로

### 재발 방지
- @Component에 static collection 사용 금지 룰 추가
- ArchUnit 테스트로 자동 검출
- 운영서 -XX:+HeapDumpOnOutOfMemoryError 의무화

### 학습
static 변수의 누적은 GC가 못 잡음. 캐시는 명시적 size·TTL.
```

---

## 시나리오 2 — Full GC 폭주

### 재현

`practice_app/`의 `FullGcController` — 큰 객체(humongous)를 반복 생성.

```bash
# Heap을 작게 시작
java -Xms256m -Xmx256m -XX:+UseG1GC \
  -Xlog:gc*:file=gc.log:time,uptime,level,tags \
  -jar app.jar

# 부하
for i in 1..1000; do curl http://localhost:8080/big & done
```

### 진단

```bash
# 1. GC 빈도 — Full GC가 등장하나?
jstat -gc <pid> 1000 30
# YGC=빈도, FGC=Full GC 빈도, FGCT=Full GC 시간

# 2. GC 로그 → gceasy.io
# → Throughput < 95%, Full GC 자주 발생

# 3. Humongous Allocation 확인
java -Xlog:gc+humongous=trace ...
```

### 보고서

```markdown
## Full GC 폭주 보고서

### 증상
- 분당 Full GC 5~10회
- 각 Full GC STW 1~3초
- Throughput 70%
- API timeout 폭증

### 원인
FullGcController.java:20에서 30MB byte[]를 매 요청마다 할당.
G1 region 크기 16MB → Humongous Allocation으로 Old에 직접 → 청소 누적.

### 조치
1. 객체 chunk 단위 분할 (4MB씩 8개)
2. G1HeapRegionSize=32m로 변경 (humongous 임계치 우회)
3. 응답 후 ByteBuf.release() 명시
```

---

## 시나리오 3 — Tomcat 스레드 고갈

### 재현

```java
@RestController
public class SlowController {
    @GetMapping("/slow")
    public String slow() throws Exception {
        Thread.sleep(10000);  // 외부 API 흉내
        return "ok";
    }
}
```

```bash
# 동시 부하
hey -n 1000 -c 300 -t 60 http://localhost:8080/slow
```

300이 200(max-threads) 초과 → 대기 → timeout.

### 진단

```bash
# /actuator/metrics
curl /actuator/metrics/tomcat.threads.busy
# → 200 (max 도달)

curl /actuator/metrics/tomcat.threads.config.max
# → 200

# 스레드덤프
jcmd <pid> Thread.print | grep -c "http-nio"
# → 200 (모두 잡힘)

jcmd <pid> Thread.print | grep -A 5 "http-nio-8080-exec-1"
# → Thread.sleep 또는 외부 API 대기
```

### 보고서

```markdown
### 원인
외부 API 호출이 10초 걸림. 평균 100 req/s × 10s = 1000 inflight 필요.
Tomcat max=200으로 한정 → 800개가 큐 또는 거부.

### 조치
1. 외부 API timeout 1초로 단축
2. Semaphore로 외부 호출 동시 50개 제한 (backpressure)
3. spring.threads.virtual.enabled=true로 VT 활성화
4. CircuitBreaker (Resilience4j)
```

---

## 시나리오 4 — @Transactional 무력화

### 재현

`practice_app/`의 `OrderService.placeWithLog()`:

```java
@Service
public class OrderService {
    public void place(Order o) {
        repo.save(o);
        logWithTransaction("placed: " + o.id);    // ❌ self-call
    }
    
    @Transactional(propagation = REQUIRES_NEW)
    public void logWithTransaction(String msg) {
        auditRepo.save(new Audit(msg));
        throw new RuntimeException("force test rollback");
    }
}
```

기대: audit이 별도 트랜잭션 → 외부 예외 + 별도 commit.
실제: self-call로 같은 (없는) 트랜잭션 → 모두 auto-commit.

### 진단

```bash
# JPA SQL 로그
logging.level.org.hibernate.SQL: DEBUG
logging.level.org.springframework.transaction.interceptor: TRACE

# 콘솔에서 트랜잭션 시작·커밋·롤백 메시지 추적
# → "Don't need to create transaction" 또는 트랜잭션 없음
```

`TransactionSynchronizationManager.isActualTransactionActive()` 출력 추가.

### 보고서

```markdown
### 원인
OrderService.place()가 같은 클래스의 logWithTransaction()을 직접 호출.
AOP 프록시 우회 → @Transactional 무시.

### 조치
1. AuditService 별도 클래스로 분리 → 프록시 경유
2. 또는 ApplicationContext.getBean()으로 자기 프록시 가져옴
```

---

## 시나리오 5 — Direct Memory 누수 (Netty)

### 재현

```java
@RestController
public class NettyController {
    @GetMapping("/netty/leak")
    public Mono<String> leak() {
        ByteBuf buf = Unpooled.directBuffer(1024 * 1024);  // 1MB direct
        // release() 안 함!
        return Mono.just("ok");
    }
}
```

WebFlux 환경에서 1만 번 호출 → 10GB direct memory.

### 진단

JVM Heap은 정상. 그러나 컨테이너 메모리 limit 초과 → OOMKilled.

```bash
# NMT (Native Memory Tracking) 활성화 후
java -XX:NativeMemoryTracking=detail ...

jcmd <pid> VM.native_memory summary
# - Direct Memory: 큰 값
```

Netty leak 탐지:

```yaml
io.netty.leakDetection.level: PARANOID
```

콘솔에 누수 stack trace 출력.

### 보고서

```markdown
### 원인
Netty ByteBuf 명시적 release() 누락.
Direct Memory 무한 증가 → 컨테이너 limit 초과 → SIGKILL.

### 조치
1. try-finally로 release() 보장
2. ReferenceCountUtil.safeRelease() 헬퍼 사용
3. Netty leak detection을 dev/staging에 PARANOID로 켬
```

---

## 캡스톤 최종 보고서 템플릿

```markdown
# JVM·Spring 부트캠프 캡스톤 보고서

## 학습한 사고 5건
1. Heap OOM
2. Full GC 폭주
3. Tomcat 스레드 고갈
4. @Transactional 무력화
5. Direct Memory 누수

## 각 사고별 (위 템플릿 5회)

## 개인 학습 회고

### 가장 새로 배운 것 3가지

### 운영 중 자주 마주칠 것 같은 것

### 다음에 더 공부할 것
- [ ] AspectJ load-time weaving
- [ ] Project Loom 심화 (StructuredTaskScope)
- [ ] ZGC 운영 경험
- ...

## 부록: 즐겨찾기 명령어 리스트

```bash
# 진단 시작 3종 세트
jcmd <pid> Thread.print > thread-$(date +%s).txt
jcmd <pid> GC.heap_info
jcmd <pid> VM.native_memory summary

# 운영 endpoint
curl /actuator/health/liveness
curl /actuator/metrics/jvm.gc.pause | jq
curl /actuator/heapdump -o heap-$(date +%s).hprof

# 로거 동적 변경
curl -X POST /actuator/loggers/com.example.X -d '{"configuredLevel":"DEBUG"}'
```
```

---

## 캡스톤 채점 가이드 (자기 평가)

| 항목 | 비중 | 기준 |
|---|---|---|
| **재현** | 20% | 5개 사고 모두 재현했나 |
| **진단 도구 활용** | 25% | jcmd·MAT·jstack·gceasy 등 활용 |
| **원인 분석** | 25% | 단순 증상이 아닌 근본 원인 |
| **조치** | 15% | 단기·장기·재발 방지 구분 |
| **보고서 품질** | 15% | 시간순서·재현가능·전수인계 가능 |

90점+ — 부트캠프 졸업
70점+ — 추가 학습 후 재도전
70점 미만 — Week 1-4 부족 부분 재학습

---

## 캡스톤 이후

이 부트캠프를 마쳤다면 다음을 권합니다:

1. **실제 운영 사고 5건 더** — 회사/사이드 프로젝트에서
2. **JMH 마이크로벤치마크** 작성 — 본인 코드의 hot path
3. **사내 Starter** 만들기 — 팀 표준 정착
4. **DB 부트캠프** (이미 했다면 다음) → **DevOps/SRE 부트캠프**

> 운영 사고를 처음 만났을 때 jcmd/jstack/jmap을 손이 먼저 가게 되면 성공.
