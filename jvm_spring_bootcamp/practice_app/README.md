# practice_app — 트러블슈팅 재현 미니앱

JVM·Spring 부트캠프의 캡스톤·labs를 위한 **재현 가능한 사고 시뮬레이터**.

## 시나리오 모음

| 엔드포인트 | 시나리오 | 어디서 |
|---|---|---|
| `GET /leak` | static List에 데이터 무한 누적 (Heap OOM) | Capstone #1 |
| `GET /humongous` | 큰 byte[] 할당 (G1 Humongous, Full GC 폭주) | Capstone #2 |
| `GET /slow` | Thread.sleep(10초) — Tomcat 풀 고갈 시뮬 | Capstone #3 |
| `GET /tx/self` | @Transactional self-invocation 무력화 | Capstone #4 |
| `GET /direct/leak` | Direct ByteBuffer release 누락 | Capstone #5 |
| `GET /deadlock` | 즉시 2-스레드 데드락 발생 | Lab 4 |
| `GET /race` | 동시 카운터 race (volatile vs Atomic) | Lab 4 |
| `GET /tx/req-new` | REQUIRES_NEW로 외부 롤백 + 별도 commit | Lab 6 |
| `GET /tx/checked` | Checked Exception 자동 롤백 안 함 | Lab 6 |

## 빠른 시작

```bash
# 빌드
./gradlew bootRun

# 또는 OOM 옵션 + GC 로그
java -Xms256m -Xmx256m \
     -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=. \
     -Xlog:gc*:file=gc.log:time,uptime,level,tags \
     -jar build/libs/practice-app-0.0.1-SNAPSHOT.jar
```

## 빠른 진단

```bash
# JVM 진단
jcmd $(jps -l | grep practice-app | cut -d' ' -f1) Thread.print > thread.txt
jcmd $(jps -l | grep practice-app | cut -d' ' -f1) GC.heap_info
jcmd $(jps -l | grep practice-app | cut -d' ' -f1) GC.heap_dump /tmp/heap.hprof

# 메트릭 / 액추에이터
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/tomcat.threads.busy
curl http://localhost:8080/actuator/heapdump -o heap.hprof
curl http://localhost:8080/actuator/threaddump
```

## 의도된 위험 코드

이 앱의 코드는 **의도적으로 안티패턴**을 포함. 실제 운영에 쓰지 마세요.
파일별로 `// ❌` 주석을 달아 표시.

## 구조

```
practice_app/
├── build.gradle
├── src/main/
│   ├── java/com/example/jvmlab/
│   │   ├── JvmLabApp.java
│   │   ├── leak/LeakController.java
│   │   ├── gc/GcController.java
│   │   ├── thread/SlowController.java
│   │   ├── thread/DeadlockController.java
│   │   ├── thread/RaceController.java
│   │   ├── tx/SelfController.java
│   │   ├── tx/CheckedController.java
│   │   ├── tx/ReqNewController.java
│   │   └── direct/DirectLeakController.java
│   └── resources/
│       └── application.yml
```
