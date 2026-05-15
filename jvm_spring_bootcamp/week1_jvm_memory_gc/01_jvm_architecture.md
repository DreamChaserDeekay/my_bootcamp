# Day 1 — JVM 아키텍처

## 한 줄 요약

JVM은 `.class` 파일을 읽어서(**ClassLoader**) 메모리에 적재하고(**Runtime Data Area**), 한 줄씩 실행하거나 기계어로 컴파일해서(**Execution Engine**) 돌리는 가상 머신이다.

## 학습 목표

- [ ] JVM의 3대 구성요소를 그릴 수 있다 (ClassLoader / Runtime Data Area / Execution Engine)
- [ ] `.java → .class → 클래스 로딩 → 실행`의 흐름을 추적할 수 있다
- [ ] Interpreter vs JIT의 역할 분담을 설명한다
- [ ] JVM 구현체(HotSpot, OpenJ9, GraalVM)의 차이를 안다
- [ ] `java HelloWorld` 한 줄에서 일어나는 일을 단계별로 안다

---

## JVM 큰 그림

```
        ┌─────────────────────────────────────────────────────┐
        │                      JVM                            │
        │                                                     │
        │  ┌────────────────┐    ┌─────────────────────────┐  │
        │  │  ClassLoader   │    │   Runtime Data Area     │  │
        │  │  Subsystem     │───▶│                         │  │
        │  │                │    │  Method Area (Metaspace)│  │
        │  │  Bootstrap     │    │  Heap                   │  │
        │  │  Platform      │    │  JVM Stack (per thread) │  │
        │  │  Application   │    │  PC Register            │  │
        │  │  (Custom)      │    │  Native Method Stack    │  │
        │  └────────────────┘    └────────────┬────────────┘  │
        │                                     │               │
        │                       ┌─────────────▼────────────┐  │
        │                       │   Execution Engine       │  │
        │                       │                          │  │
        │                       │   Interpreter            │  │
        │                       │   JIT Compiler (C1/C2)   │  │
        │                       │   Garbage Collector      │  │
        │                       └──────────────────────────┘  │
        │                                                     │
        │     ┌─────────────────────────────────────────┐     │
        │     │   Native Method Interface (JNI)         │     │
        │     └─────────────────────────────────────────┘     │
        └─────────────────────────────────────────────────────┘
```

### 핵심 책임 분리

| 구성 | 책임 |
|---|---|
| **ClassLoader** | `.class` 파일을 찾아서 메모리로 적재. 위임 모델로 보안·격리 |
| **Runtime Data Area** | 적재된 클래스·생성된 객체·실행 중인 스레드 데이터를 보관 |
| **Execution Engine** | 바이트코드를 실행. Interpreter(시작은 빠르게) + JIT(자주 쓰면 기계어로) |
| **JNI** | C/C++로 작성된 네이티브 코드와 연결 |
| **GC** | Heap에서 더 이상 참조 안 되는 객체 정리 |

---

## `.java`에서 실행까지 — 단계별

```
HelloWorld.java
    │
    │  javac (컴파일러)
    ▼
HelloWorld.class  ← 바이트코드(JVM이 읽는 중간 표현)
    │
    │  $ java HelloWorld
    ▼
┌─────────────────────────────────────┐
│ 1. java launcher가 JVM 부팅         │
│ 2. JVM이 ClassLoader로 클래스 로딩   │
│    - 로딩  (find & load)             │
│    - 링킹  (verify, prepare, resolve)│
│    - 초기화 (static 블록 실행)        │
│ 3. main 메서드의 바이트코드 실행      │
│    - Interpreter로 시작              │
│    - 자주 실행되면 JIT 컴파일         │
│ 4. GC가 백그라운드로 메모리 정리      │
└─────────────────────────────────────┘
```

### 바이트코드 예시

```java
public class Hello {
    public static void main(String[] args) {
        int a = 10;
        int b = 20;
        System.out.println(a + b);
    }
}
```

```bash
javac Hello.java
javap -c Hello
```

```
public static void main(java.lang.String[]);
  Code:
     0: bipush        10            // 스택에 10 push
     2: istore_1                    // 로컬변수 1번에 저장 (a)
     3: bipush        20            // 스택에 20 push
     5: istore_2                    // 로컬변수 2번에 저장 (b)
     6: getstatic     #2            // System.out
     9: iload_1                     // a를 스택에 push
    10: iload_2                     // b를 스택에 push
    11: iadd                        // 두 값 더하기
    12: invokevirtual #3            // println 호출
    15: return
```

**JVM은 스택 머신이다** — 레지스터가 아닌 오퍼랜드 스택을 기준으로 동작.

---

## ClassLoader 위임 모델 (예고)

```
                       ┌──────────────────────┐
   부모에게 먼저 물음   │   Bootstrap Loader   │  $JAVA_HOME/lib/modules
       ▲               │   (C++로 구현)        │  java.*, sun.*
       │               └──────────┬───────────┘
       │                          ▼
       │               ┌──────────────────────┐
       │               │   Platform Loader    │  JDK 모듈 (javax.*, jdk.*)
       │               └──────────┬───────────┘
       │                          ▼
       │               ┌──────────────────────┐
       │               │   Application Loader │  -cp / -classpath
       │               └──────────┬───────────┘
       │                          ▼
       └─── 못 찾으면 │  Custom Loader (선택) │  플러그인, OSGi, Tomcat 등
                       └──────────────────────┘
```

**위임 모델의 핵심**: 자식이 클래스를 찾을 때 항상 부모에게 먼저 묻는다. 그래서 사용자가 `java.lang.String`을 정의해도 Bootstrap이 이긴다 (보안).

> 자세한 내용은 Week 2 Day 1에서. 여기서는 "위임 모델이 있다"는 사실만.

---

## Interpreter + JIT 하이브리드

### Interpreter (인터프리터)

바이트코드를 **한 줄씩** 기계어로 변환해서 실행. 시작은 빠르지만 반복 실행은 느림.

### JIT (Just-In-Time) Compiler

자주 실행되는 코드(**hot spot**)를 통째로 기계어로 컴파일해서 캐시. 다음 실행부터는 컴파일된 코드를 사용.

```
                      처음 실행
   바이트코드 ─────────────────────────▶ Interpreter (느림)
                              │
                              │  반복 횟수 임계치 (-XX:CompileThreshold ≈ 10000)
                              ▼
                       JIT 컴파일러
                       ├─ C1 (빠른 컴파일, 적당한 최적화)
                       └─ C2 (느린 컴파일, 강력한 최적화)
                              │
                              ▼
                       Compiled Code Cache
                              │
                       다음 실행은 기계어 (빠름)
```

### Tiered Compilation (계층적 컴파일, 기본 켜짐)

JDK 7+ 기본. **C1 → C2** 단계로 점진적 최적화.

```bash
# JIT 활동 보기
java -XX:+PrintCompilation HelloWorld

# 출력 예
#    143    1       3       java.lang.String::charAt (25 bytes)
#    144    2       3       java.lang.AbstractStringBuilder::append (50 bytes)
#    ^^      ^^^    ^^^     ^^^^^
#    ms       id   tier    method
```

> JIT 자세한 내부는 Week 2 Day 2.

---

## JVM 구현체

| 구현 | 특징 | 사용처 |
|---|---|---|
| **HotSpot** | Oracle/OpenJDK 표준. C1/C2 JIT, G1 기본 | 대부분 |
| **OpenJ9** | IBM(이클립스 재단). 메모리 효율 강점 | DB2와 같은 IBM 환경, 저사양 |
| **GraalVM** | Graal JIT + AOT (Native Image) 지원 | 빠른 시작 필요 (Lambda, CLI) |
| **Zulu, Corretto** | OpenJDK 빌드 | 상용 지원 받고 싶을 때 |

```bash
# 어떤 VM인지 확인
java -XX:+PrintFlagsFinal -version | findstr UseG1GC
java -version
```

**HotSpot이 압도적**. 면접·운영 표준은 HotSpot 기준.

---

## ❌ 위험 패턴 vs ✅ 안전 패턴

### 1) 운영서에 디버그용 인터프리터 강제

```bash
# ❌ JIT 끄기 (왜 하면 안 되는가)
java -Xint MyApp                    # Interpreter Only → 10~100배 느림

# ✅ 운영 기본 (Tiered Compilation 자동)
java MyApp                          # 알아서 hot spot 컴파일
```

### 2) 모르고 클래스 로더 갈아끼우기

```java
// ❌ Application Loader를 무작정 갈아치움
Thread.currentThread().setContextClassLoader(new MyClassLoader());
// → Spring/Hibernate가 의존하는 컨텍스트 손실 → ClassNotFoundException 폭풍

// ✅ 격리가 필요한 영역만 별도 로더로 묶기
ClassLoader pluginLoader = new URLClassLoader(pluginUrls, parentLoader);
Class<?> pluginClass = pluginLoader.loadClass("com.plugin.Main");
```

### 3) `System.gc()`로 GC 강제

```java
// ❌ 명시적 GC 호출
System.gc();                        // Full GC를 직접 부름. STW 길어짐

// ✅ GC는 JVM이 결정. 끄거나 통제만:
// -XX:+DisableExplicitGC          (System.gc() 무시)
```

---

## Java/Spring 개발자가 흔히 갖는 오해

| 오해 | 진실 |
|---|---|
| "JVM은 Java만 돌리는 VM이다" | 바이트코드를 돌리는 VM. Kotlin·Scala·Groovy 모두 |
| "Java는 컴파일 언어다 / 인터프리터다" | 둘 다. 바이트코드 컴파일 + JVM 인터프리터 + JIT |
| "JVM이 느리다" | JIT 후엔 종종 C++ 수준. 시작이 느릴 뿐 (그래서 GraalVM AOT) |
| "Heap만 메모리다" | Stack/Metaspace/Direct Buffer/Code Cache 등 다수 |
| "GC가 모든 메모리를 관리한다" | Heap만 관리. Native·Direct Buffer는 따로 |

---

## 실습 (Hands-on)

### 1단계 — 첫 JVM 정보 확인

```bash
java -version
# openjdk version "21.0.x"

# 모든 JVM 옵션 (긴 목록)
java -XX:+PrintFlagsFinal -version | findstr /i "heap gc compiler"

# 현재 JVM의 GC가 무엇?
java -XX:+PrintFlagsFinal -version | findstr "Use.*GC "
# UseG1GC = true  ← JDK 21 기본은 G1
```

### 2단계 — 바이트코드 직접 보기

```java
// Hello.java
public class Hello {
    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "World";
        System.out.println("Hello, " + name + "!");
    }
}
```

```bash
javac Hello.java
javap -c -p Hello                   # 바이트코드 출력
javap -v Hello                      # 상수 풀까지 전부
```

**관찰**: `+`가 `StringBuilder.append`로 컴파일됨.

### 3단계 — JIT 컴파일 보기

```bash
# JIT 활동 출력
java -XX:+PrintCompilation Hello

# Tiered Compilation 레벨도 보기
java -XX:+PrintCompilation -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining Hello 2>&1 | findstr Hello
```

### 4단계 — 같은 코드, 다른 JVM 옵션

```bash
# Interpreter 강제 — 얼마나 느린지 체감
java -Xint -XX:+PrintCompilation Hello

# JIT 강제 (반대)
java -Xcomp Hello                   # 처음부터 모두 컴파일 (시작 오래 걸림)

# 기본 (Tiered) — 알아서
java Hello
```

---

## 더 읽어볼 자료

- 📘 『JVM Performance Engineering』 (Monica Beckwith, Addison-Wesley)
- 📘 『The Definitive Guide to Java Performance』 (Scott Oaks, O'Reilly) — JVM의 바이블
- 📘 『Inside the Java Virtual Machine』 (Bill Venners) — 옛 책이지만 구조는 여전
- 🔗 [JVM Specification (Java SE 21)](https://docs.oracle.com/javase/specs/jvms/se21/html/)
- 🔗 [HotSpot Glossary](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/runtime/vmStructs.cpp)
- 🎓 Coursera — "Java Programming: Principles of Software Design" (보조)
- 🔗 [Aleksey Shipilev's blog](https://shipilev.net/) — JVM 내부 권위자
