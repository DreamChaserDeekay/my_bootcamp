# Day 2 — JVM 메모리 영역

## 한 줄 요약

JVM은 메모리를 5개 영역으로 나눠 쓴다 — **Heap**(객체), **JVM Stack**(스레드별 호출 프레임), **Method Area / Metaspace**(클래스 메타데이터), **PC Register**(현재 실행 위치), **Native Method Stack**(JNI용).

## 학습 목표

- [ ] 5개 메모리 영역의 역할·생명주기·공유 여부를 안다
- [ ] Heap의 Young/Old/Survivor 구조를 설명한다
- [ ] Stack과 Heap의 차이를 객체·참조로 설명한다
- [ ] Metaspace가 PermGen을 어떻게 대체했는지 안다
- [ ] String Pool·intern() 메커니즘을 이해한다
- [ ] OOM 8종 — 각각 어느 영역에서 발생하는지 안다

---

## 메모리 영역 전체 그림

```
                  ┌─────────────────────────────────────────────────┐
                  │              JVM Process                        │
                  │                                                 │
   스레드별 분리   │   ┌──────────┐  ┌──────────┐  ┌──────────┐    │
   (Thread-Local) │   │ Thread-1 │  │ Thread-2 │  │ Thread-N │    │
                  │   │          │  │          │  │          │    │
                  │   │ ┌──────┐ │  │ ┌──────┐ │  │ ┌──────┐ │    │
                  │   │ │Stack │ │  │ │Stack │ │  │ │Stack │ │    │
                  │   │ ├──────┤ │  │ ├──────┤ │  │ ├──────┤ │    │
                  │   │ │ PC   │ │  │ │ PC   │ │  │ │ PC   │ │    │
                  │   │ ├──────┤ │  │ ├──────┤ │  │ ├──────┤ │    │
                  │   │ │Native│ │  │ │Native│ │  │ │Native│ │    │
                  │   │ └──────┘ │  │ └──────┘ │  │ └──────┘ │    │
                  │   └──────────┘  └──────────┘  └──────────┘    │
                  │                                                 │
   모든 스레드     │   ┌──────────────────────────────────────────┐ │
   공유            │   │              Heap                        │ │
                  │   │  ┌──────────────────┐  ┌──────────────┐  │ │
                  │   │  │ Young Generation │  │     Old      │  │ │
                  │   │  │ ┌────┐ ┌──┬───┐  │  │  Generation  │  │ │
                  │   │  │ │Eden│ │S0│S1 │  │  │              │  │ │
                  │   │  │ └────┘ └──┴───┘  │  │              │  │ │
                  │   │  └──────────────────┘  └──────────────┘  │ │
                  │   └──────────────────────────────────────────┘ │
                  │                                                 │
                  │   ┌──────────────────────────────────────────┐ │
                  │   │     Metaspace (Method Area)              │ │
                  │   │     - Class 메타데이터, Method, 상수풀    │ │
                  │   └──────────────────────────────────────────┘ │
                  │                                                 │
                  │   ┌──────────────────────────────────────────┐ │
                  │   │     Code Cache (JIT 컴파일된 코드)        │ │
                  │   └──────────────────────────────────────────┘ │
                  │                                                 │
                  │   ┌──────────────────────────────────────────┐ │
                  │   │     Direct Buffer / Native Memory        │ │
                  │   │     (NIO, ByteBuffer.allocateDirect)     │ │
                  │   └──────────────────────────────────────────┘ │
                  └─────────────────────────────────────────────────┘
```

| 영역 | 공유 | 관리 주체 | 자랄 수 있나 |
|---|---|---|---|
| **Heap** | O | GC | -Xmx까지 |
| **Stack** | X (스레드별) | 메서드 종료 시 자동 | -Xss 한도 |
| **PC Register** | X | 자동 | 변하지 않음 |
| **Metaspace** | O | GC가 정리 | Native 메모리 (기본 unlimited) |
| **Native Method Stack** | X (스레드별) | OS | 운영체제 한도 |
| **Code Cache** | O | JIT가 관리 | -XX:ReservedCodeCacheSize |
| **Direct Buffer** | O | 명시적 free + GC | -XX:MaxDirectMemorySize |

---

## 1) Heap — 객체의 집

모든 객체(`new`로 만든 것)와 배열이 사는 곳. **모든 스레드가 공유**. GC의 주 무대.

### Generational Heap (세대 가설)

> 가설: 대부분의 객체는 **금방 죽는다**. 오래 살아남는 객체는 **계속 오래 산다**.

```
                  Young Generation                    Old Generation
   ┌──────────────────────────────────┐   ┌──────────────────────┐
   │   Eden     │   S0    │    S1     │   │                      │
   │            │         │           │   │                      │
   │  새 객체   │ Survivor│ Survivor  │   │   장수 객체           │
   │            │   From  │   To      │   │                      │
   └──────────────────────────────────┘   └──────────────────────┘
        │             │       │                       │
        │             ▼       │                       ▼
        │     Minor GC: Eden + From → To             Major GC:
        │     생존자만 To로 이사                       Old 전체 정리
        │     age 카운터 증가
        │
        │     Tenuring threshold (기본 15) 넘으면 → Old로 승격
```

### 비율 (JDK 8 기본, 이후도 비슷)

```
-Xms / -Xmx                          # 최소/최대 Heap 크기
-XX:NewRatio=2                       # Old : Young = 2 : 1 (기본)
-XX:SurvivorRatio=8                  # Eden : Survivor = 8 : 1 (기본)
-XX:MaxTenuringThreshold=15          # age 넘으면 Old로 승격
```

> **G1 이후엔 region 단위**로 동작 — 명시적 Eden/S0/S1이 없지만 논리적으로는 동일.

### 객체 할당 흐름

```java
Order o = new Order();    // 1. Eden에 할당 시도
                          // 2. Eden 가득 차면 Minor GC
                          //    → 살아남으면 Survivor로
                          // 3. Survivor에서 age 임계치 도달
                          //    → Old로 승격
```

---

## 2) JVM Stack — 메서드 호출의 흔적

**스레드별로 하나**씩. 메서드를 호출할 때마다 **Frame** 하나가 push, 종료 시 pop.

```
public int sum(int a, int b) {        Frame
    int result = a + b;              ┌─────────────────┐
    return result;                   │ Local Variables │  a, b, result
}                                    ├─────────────────┤
                                     │ Operand Stack   │  iadd 등 연산
                                     ├─────────────────┤
                                     │ Constant Pool Ref│
                                     └─────────────────┘
```

**한 스레드의 스택 전체 크기**: `-Xss` (기본 1MB 정도, OS별로 다름).

```bash
java -Xss512k MyApp                  # 스레드당 스택 512KB
```

### StackOverflowError

```java
void recurse() {
    recurse();                       // 무한 재귀
}
// java.lang.StackOverflowError      // 스택 frame이 한도 초과
```

### Stack vs Heap

```java
public void example() {
    int x = 10;                      // Stack — primitive
    String s = new String("hi");     // Stack에 참조, Heap에 객체
    int[] arr = {1, 2, 3};           // Stack에 참조, Heap에 배열
}                                    // 메서드 끝 → x, s, arr 참조 사라짐
                                     // Heap의 String/array는 GC 대상
```

---

## 3) PC Register — "지금 어디?"

스레드별로 **현재 실행 중인 바이트코드 명령의 주소**. 마이크로 단위라 거의 신경 쓸 일 없다.

---

## 4) Method Area / Metaspace — 클래스의 명세서

클래스의 **메타데이터** — 필드 이름, 메서드 시그니처, 상수 풀, static 변수 등.

### PermGen → Metaspace (JDK 8 변경)

| | PermGen (JDK 7 이전) | Metaspace (JDK 8+) |
|---|---|---|
| 위치 | Heap 안 | **Native 메모리** |
| 기본 크기 | 제한 (-XX:MaxPermSize) | 무제한 (옵션으로 제한 가능) |
| OOM | `OutOfMemoryError: PermGen space` | `OutOfMemoryError: Metaspace` |
| 정리 | 잘 안 됨 | Class Loader가 죽으면 정리 |

```bash
# Metaspace 한도 (안 걸면 시스템 메모리 끝까지)
-XX:MaxMetaspaceSize=512m
```

### 왜 Metaspace로 바뀌었나

PermGen은 Heap 안에 있어서 **재배포(redeploy)** 시 클래스가 누적되면 Heap이 줄어드는 문제가 있었다. Native로 빼면서 분리.

### Metaspace OOM의 흔한 원인

- **클래스 누수** — 동적 클래스 생성(CGLIB, ASM) 반복, Class Loader 누수
- **재배포** — WAR redeploy 시 옛 ClassLoader가 GC되지 않음
- **너무 많은 의존성** — 한 앱이 수만 개 클래스 적재

---

## 5) Native Method Stack — JNI용

JNI(Java Native Interface)로 호출되는 **C/C++ 네이티브 메서드**의 스택. 스레드별.

---

## Code Cache — JIT의 작품 저장소

JIT가 컴파일한 기계어 코드를 보관. 가득 차면 **JIT 비활성화** → 갑자기 느려질 수 있다.

```bash
# 기본 240MB (Tiered Compilation 켜진 경우)
-XX:ReservedCodeCacheSize=256m
-XX:+PrintCodeCache              # 사용량 출력
```

**증상**: 어느 순간 throughput이 갑자기 떨어지고 다시 안 회복. **CodeCache가 가득 찼을 가능성**.

---

## Direct Buffer / Native Memory

`ByteBuffer.allocateDirect(N)`로 만든 메모리는 **Heap 바깥의 Native**에 있다. Netty, NIO에서 자주 사용.

```java
ByteBuffer buf = ByteBuffer.allocateDirect(1024 * 1024);  // 1MB
// Heap이 아닌 OS native에 할당
// → -Xmx에 영향 X, 컨테이너 메모리 limit에는 영향 O
```

### OOM 종류

```bash
# 모두 다른 영역
java.lang.OutOfMemoryError: Java heap space            # Heap
java.lang.OutOfMemoryError: GC overhead limit exceeded  # GC가 98% CPU만 먹는데 결과 없음
java.lang.OutOfMemoryError: Metaspace                   # Metaspace
java.lang.OutOfMemoryError: Direct buffer memory        # Direct Buffer
java.lang.OutOfMemoryError: unable to create new native thread  # OS 스레드 한도
java.lang.OutOfMemoryError: Requested array size exceeds VM limit  # int.MAX 근접
java.lang.OutOfMemoryError: Compressed class space      # Metaspace의 일부
java.lang.OutOfMemoryError: Out of swap space?          # OS swap
```

> **메시지로 영역을 알 수 있다**. 무작정 `-Xmx` 늘리지 말고 진단 먼저.

---

## String Pool — 잘 모르면 메모리 새는 곳

String 리터럴은 **String Pool**에 캐시된다. 같은 문자열을 여러 번 써도 객체 하나.

```java
String a = "hello";              // String Pool에 "hello" 등록, a가 참조
String b = "hello";              // Pool에서 같은 객체 재사용
String c = new String("hello");  // 새 객체 (Heap에 별도)
String d = c.intern();           // Pool에 등록 또는 기존 참조

System.out.println(a == b);      // true (같은 객체)
System.out.println(a == c);      // false (다른 객체)
System.out.println(a == d);      // true (intern으로 Pool과 연결)
```

### String Pool 위치 (JDK 7+)

JDK 6까지 PermGen에 있었음 → JDK 7부터 **Heap**에 있음. 그래서 `intern()` 누수가 PermGen 가득 차는 일이 사라짐.

### 함정

```java
// ❌ 사용자 입력을 intern() — 수백만 개 String이 Pool에 영원히 남음
for (String userInput : inputs) {
    String key = userInput.intern();   // String Pool이 거대해진다
    cache.put(key, ...);
}

// ✅ intern은 컴파일타임 상수처럼 쓸 때만
// 캐시는 별도 자료구조로 (ConcurrentHashMap 등)
```

---

## ❌ 위험 패턴 vs ✅ 안전 패턴

### 1) -Xmx만 늘리기

```bash
# ❌ OOM 나면 일단 메모리 늘림 (근본 원인 미해결)
-Xmx16g                              # 누수가 있으면 결국 또 터짐

# ✅ 진단 먼저
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/heap-dump.hprof
# OOM 시 자동으로 힙덤프 → MAT로 분석
```

### 2) Heap만 보고 컨테이너 메모리 설정

```yaml
# ❌ 컨테이너 메모리 = Heap만 고려
resources:
  limits:
    memory: 4Gi
# 실제 사용: Heap 4G + Metaspace 0.5G + Direct 1G + Stack 0.5G + Native 0.5G ≈ 6.5G
# → OOMKilled (컨테이너가 죽음)

# ✅ Heap 외 메모리도 계산
# -Xmx3g + Metaspace + Direct + Stack 합 < 4Gi
```

### 3) 큰 객체를 자주 만들기

```java
// ❌ 메서드 호출마다 거대 배열 만들기
public void process() {
    byte[] buffer = new byte[10 * 1024 * 1024];   // 매번 10MB Heap
    // ...
}

// ✅ 재사용 (스레드 안전성 주의)
private final ThreadLocal<byte[]> buffer = ThreadLocal.withInitial(() -> new byte[10 * 1024 * 1024]);
```

---

## 운영 사고 사례

### 사례 1 — Direct Memory 누수 (Netty)

**증상**: 컨테이너 OOM-killed. JVM 로그엔 OOM 없음. Heap은 3G만 씀(`-Xmx4g`).

**원인**: Netty `ByteBuf`를 `release()` 안 했음. **Direct Memory가 누적** → 컨테이너 limit 초과.

**진단**:
```bash
# Direct Memory 사용량 (JDK 21)
jcmd <pid> VM.native_memory summary | grep "Direct"
```

**조치**: `ByteBuf.release()` 명시 호출. `-Dio.netty.leakDetection.level=PARANOID`로 누수 탐지.

### 사례 2 — Metaspace 폭주 (Groovy)

**증상**: 며칠마다 `OOM: Metaspace`. 재배포 후 안정.

**원인**: 동적으로 Groovy 스크립트를 컴파일·실행 → 새 클래스를 매번 생성 → Metaspace에 적재. ClassLoader가 GC되지 않음.

**조치**: 같은 GroovyShell 재사용 또는 스크립트 캐싱. Metaspace OOM 자동 덤프 설정.

---

## 실습 (Hands-on)

### 1단계 — 메모리 영역별 사용량 보기

```bash
# 동작 중인 JVM 메모리
jps -l                               # PID 확인
jcmd <pid> VM.native_memory summary  # 영역별 (NMT 사전 활성화 필요)

# NMT 켜고 실행
java -XX:NativeMemoryTracking=summary MyApp &
jcmd $! VM.native_memory summary
```

### 2단계 — Heap 모양 보기

```bash
jmap -heap <pid>
# Heap Configuration: G1, max 4GB
# Region count, Young/Old 비율 출력
```

### 3단계 — OOM 직접 만들기

```java
// HeapOOM.java
import java.util.ArrayList;
import java.util.List;

public class HeapOOM {
    public static void main(String[] args) {
        List<byte[]> list = new ArrayList<>();
        for (int i = 0; ; i++) {
            list.add(new byte[1024 * 1024]);    // 1MB씩
            System.out.println(i + "MB allocated");
        }
    }
}
```

```bash
javac HeapOOM.java
java -Xmx100m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=. HeapOOM
# → java.lang.OutOfMemoryError: Java heap space
# → heap-dump.hprof 생성
```

Mission Control이나 MAT로 dump 분석 — `byte[]`가 List에 잡혀있는 게 보임.

### 4단계 — Metaspace OOM 만들기

```java
// MetaspaceOOM.java — Javassist로 동적 클래스 생성
// (의존성: javassist)
import javassist.ClassPool;
for (int i = 0; i < 1_000_000; i++) {
    ClassPool pool = ClassPool.getDefault();
    pool.makeClass("Gen$" + i).toClass();
}
```

```bash
java -XX:MaxMetaspaceSize=64m MetaspaceOOM
# → java.lang.OutOfMemoryError: Metaspace
```

---

## 더 읽어볼 자료

- 📘 『Java Performance: The Definitive Guide』 2nd ed. (Scott Oaks) — 4장 메모리, 5장 GC
- 📘 『Understanding the JVM』 (Bill Venners, 무료 PDF로 일부)
- 🔗 [JEP 122: Remove the Permanent Generation](https://openjdk.org/jeps/122)
- 🔗 [Native Memory Tracking](https://docs.oracle.com/en/java/javase/21/troubleshoot/diagnostic-tools.html#nmt)
- 🔗 Aleksey Shipilev — [What heap dumps are lying to you about](https://shipilev.net/jvm/anatomy-quarks/4-tlab-allocation/)
- 🎓 InfoQ 발표 — Monica Beckwith, "JVM Memory Model"
