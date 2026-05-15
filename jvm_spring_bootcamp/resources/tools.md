# 도구 (Tools)

## 1. JDK 표준 (가장 중요)

| 도구 | 용도 |
|---|---|
| `jps` | JVM 프로세스 목록 |
| `jstat` | GC·class load·compiler 통계 (실시간) |
| `jmap` | 힙덤프, 힙 통계 |
| `jstack` | 스레드덤프 |
| `jcmd` | **통합 명령 — 가장 강력** |
| `jhsdb` | 코어 덤프·스트레스 분석 (Hotspot SA) |
| `jconsole` | GUI 모니터링 (간단) |
| `jfr` | Flight Recorder CLI |

### jcmd가 거의 모든 일을 한다

```bash
jcmd <pid> VM.version
jcmd <pid> VM.flags
jcmd <pid> VM.system_properties
jcmd <pid> GC.heap_info
jcmd <pid> GC.heap_dump /path/heap.hprof
jcmd <pid> Thread.print
jcmd <pid> VM.native_memory summary
jcmd <pid> JFR.start duration=60s filename=app.jfr
jcmd <pid> Compiler.codecache
jcmd <pid> help
```

---

## 2. 분석 도구 (GUI)

| 도구 | 라이선스 | 특징 |
|---|---|---|
| **Eclipse MAT** | 무료 | 힙덤프 표준. Leak Suspects |
| **JDK Mission Control** | 무료 | JFR 분석. 가장 풍부 |
| **VisualVM** | 무료 | 가볍지만 큰 덤프엔 부족 |
| **YourKit** | 상용 | 프로파일링 최강 |
| **JProfiler** | 상용 | YourKit 경쟁 |
| **IntelliJ Profiler** | Ultimate | IDE 통합 |

---

## 3. 프로파일러 (CLI)

| 도구 | 특징 |
|---|---|
| **async-profiler** | 사실상 표준. CPU/alloc/lock flame graph |
| **perf** | Linux 시스템 프로파일러 |
| **honest-profiler** | safepoint bias 없음 (옛) |

### async-profiler 빠른 사용

```bash
# 30초 CPU 프로파일
./profiler.sh -d 30 -f cpu.html <pid>

# 메모리 할당
./profiler.sh -e alloc -d 30 -f alloc.html <pid>

# Lock contention
./profiler.sh -e lock -d 30 -f lock.html <pid>
```

---

## 4. JFR (Java Flight Recorder)

JDK 11+ 표준 내장. 운영급 (오버헤드 < 1%).

```bash
# 즉시 시작
jcmd <pid> JFR.start duration=60s filename=app.jfr

# 항상 녹화 (ring buffer)
-XX:StartFlightRecording=disk=true,maxage=1h,maxsize=500m

# 사고 시 즉시 dump
jcmd <pid> JFR.dump name=startup filename=incident.jfr
```

분석: **Mission Control**.

---

## 5. 부하 테스트

| 도구 | 특징 |
|---|---|
| **wrk** | C 기반, 매우 빠름 |
| **hey** | Go 기반, 간단 |
| **JMeter** | GUI, 종합 |
| **Gatling** | Scala, 시나리오 |
| **k6** | JS, 모던 |

---

## 6. Spring/Java APM

| 도구 | 라이선스 | 특징 |
|---|---|---|
| **Pinpoint** | 무료 (네이버) | 한국 금융권 표준 |
| **Scouter** | 무료 (LG CNS) | 한국 |
| **Datadog APM** | 상용 SaaS | 종합 |
| **New Relic** | 상용 SaaS | 종합 |
| **Dynatrace** | 상용 SaaS | 자동화 강점 |
| **Elastic APM** | OSS·상용 | ELK 통합 |
| **SkyWalking** | 무료 (Apache) | OpenTelemetry |
| **Sentry** | 무료/상용 | 에러 추적 |

---

## 7. Pinpoint 설치 (간단)

```bash
# 에이전트 다운로드
wget https://repo.maven.apache.org/.../pinpoint-agent-2.5.x.tar.gz
tar -xzf pinpoint-agent-2.5.x.tar.gz

# 앱 실행
java -javaagent:/path/to/pinpoint-bootstrap.jar \
     -Dpinpoint.agentId=app-1 \
     -Dpinpoint.applicationName=MyApp \
     -jar app.jar
```

Pinpoint 서버는 별도로 띄움 (HBase·Cassandra 백엔드).

---

## 8. Metric · Trace

| 도구 | 용도 |
|---|---|
| **Micrometer** | Java 메트릭 추상 (Spring Boot 기본) |
| **Prometheus** | 메트릭 스토어 |
| **Grafana** | 대시보드 |
| **OpenTelemetry** | 트레이스 표준 |
| **Jaeger** | 분산 트레이싱 백엔드 |
| **Zipkin** | 분산 트레이싱 백엔드 |
| **Loki** | 로그 집계 |
| **Tempo** | 트레이스 스토어 |

---

## 9. 빌드·의존성

| 도구 | 용도 |
|---|---|
| **Maven** | 표준 |
| **Gradle** | 유연·빠름 |
| **Spring Boot Gradle Plugin** | bootJar 등 |
| **JIB** | 도커 이미지 생성 (Dockerfile 없이) |
| **Buildpacks** | 표준 컨테이너 빌드 |

---

## 10. 벤치마크

| 도구 | 용도 |
|---|---|
| **JMH** | 마이크로벤치마크 표준 |
| **Caliper** | 옛 (Google) |
| **wrk2** | constant throughput |

JMH가 사실상 유일한 정확한 옵션. `System.currentTimeMillis()` 비교는 함정.

---

## 11. Bytecode 도구

| 도구 | 용도 |
|---|---|
| **javap** | 표준 디스어셈블러 |
| **ASM** | 바이트코드 생성·변환 (Spring 등이 사용) |
| **ByteBuddy** | 친화적인 wrapper |
| **Javassist** | 옛, 간단 |
| **jclasslib** | IntelliJ 플러그인, 클래스 파일 시각화 |

---

## 12. Spring 디버그

| 도구 | 용도 |
|---|---|
| **Spring Actuator** | `/actuator/conditions`, `/actuator/beans` 등 |
| **`--debug`** | Conditions Report |
| **Spring DevTools** | 자동 reload, log color |
| **Spring Boot Devtools Remote** | 원격 디버그 |
| **HikariCP leak detection** | leak-detection-threshold |
| **P6Spy** | JDBC 로깅 |

---

## 13. Native Memory Tracking

```bash
# 활성화 (시작 시)
-XX:NativeMemoryTracking=summary    # 또는 detail

# 확인
jcmd <pid> VM.native_memory summary

# Heap 외 메모리: Metaspace, Direct, Class, Thread, Code, GC, Internal
```

운영 환경에서 OOMKilled 진단의 첫 단계.

---

## 14. 로깅·진단 라이브러리

| 도구 | 용도 |
|---|---|
| **SLF4J + Logback** | Spring 기본 |
| **Logstash Encoder** | JSON 로그 (ELK 친화) |
| **Sleuth (deprecate)** → **Micrometer Tracing** | Spring 분산 트레이싱 |
| **MDC** | 요청별 컨텍스트 |

---

## 15. IDE

| | 추천 |
|---|---|
| **IntelliJ IDEA Ultimate** | 최고. 프로파일러·DB·HTTP 클라이언트 통합 |
| **IntelliJ Community** | 무료. 기본은 다 됨 |
| **VS Code + Java Extensions** | 가볍게 |
| **Eclipse** | 옛부터 친숙 |

---

## 16. CI/CD

| 도구 | 용도 |
|---|---|
| **GitHub Actions** | OSS, SaaS |
| **GitLab CI** | OSS·SaaS |
| **Jenkins** | 한국 금융권 표준 |
| **TeamCity** | JetBrains |

---

## 17. 컨테이너·k8s 관련

| 도구 | 용도 |
|---|---|
| **Docker** | 컨테이너 표준 |
| **Buildpacks** | Spring Boot `./gradlew bootBuildImage` |
| **Skaffold** | 로컬 k8s 개발 |
| **Helm** | k8s 패키지 |
| **K9s** | k8s TUI 클라이언트 |

---

## 18. 한국에서 자주 쓰는 조합 (참고)

| 영역 | 도구 |
|---|---|
| APM | Pinpoint, Scouter |
| 로그 | ELK (Elasticsearch + Logstash + Kibana) |
| 메트릭 | Prometheus + Grafana |
| 트레이스 | Jaeger 또는 Zipkin |
| 빌드 | Maven 또는 Gradle |
| 배포 | Jenkins → ArgoCD (점진적) |
| 컨테이너 | Docker, k8s |
