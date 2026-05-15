# Day 2 — 바이트코드·JIT 컴파일

## 한 줄 요약

자바 소스는 `javac`로 **바이트코드**(`.class`)가 되고, JVM이 그것을 **인터프리터**로 실행하다가 자주 쓰는 코드는 **JIT**가 기계어로 컴파일해서 캐시한다. **escape analysis · inlining · null check elimination** 같은 최적화가 코드를 다시 쓴다.

## 학습 목표

- [ ] 바이트코드 명령어 분류 (load/store, 산술, branch, invoke)
- [ ] `javap -c -v`로 바이트코드를 읽는다
- [ ] Tiered Compilation의 5단계를 안다
- [ ] C1과 C2의 차이를 설명한다
- [ ] Inlining·Escape Analysis·Inline Caching 등 주요 최적화를 안다
- [ ] `-XX:+PrintCompilation`, `-XX:+PrintInlining`을 본다
- [ ] JIT가 만든 어셈블리(`hsdis`)를 본다

---

## 바이트코드 한눈에

`.class` 파일은 **JVM이 읽는 중간 표현**. 스택 머신 명령으로 구성.

### 명령어 카테고리

| 카테고리 | 예시 | 의미 |
|---|---|---|
| Load | `iload_0`, `aload_1`, `dload` | 로컬 변수 → 스택 |
| Store | `istore_2`, `astore_3` | 스택 → 로컬 변수 |
| 산술 | `iadd`, `isub`, `imul`, `irem` | int 연산 |
| Stack | `dup`, `pop`, `swap` | 스택 조작 |
| Branch | `ifeq`, `ifne`, `goto`, `if_icmplt` | 분기 |
| Invoke | `invokestatic`, `invokevirtual`, `invokespecial`, `invokeinterface`, `invokedynamic` | 메서드 호출 |
| Object | `new`, `getfield`, `putfield`, `getstatic`, `putstatic` | 객체 |
| Array | `iaload`, `iastore`, `arraylength`, `newarray` | 배열 |
| Cast | `checkcast`, `instanceof`, `i2l`, `i2d` | 타입 변환 |
| Return | `ireturn`, `areturn`, `return` | 반환 |
| Exception | `athrow` | 던지기 |

### 예시 1 — 단순 산술

```java
public int sum(int a, int b) {
    return a + b;
}
```

```bash
javap -c Hello
```

```
public int sum(int, int);
  Code:
     0: iload_1     // a를 스택에 push
     1: iload_2     // b를 스택에 push
     2: iadd        // 두 값 더해서 결과를 스택에
     3: ireturn     // int 반환
```

### 예시 2 — invokevirtual 5종

```java
class Foo {
    void instanceMethod() {}
    static void staticMethod() {}
    private void privateMethod() {}
}
interface Bar { void interfaceMethod(); }

void caller() {
    Foo f = new Foo();
    f.instanceMethod();             // invokevirtual
    Foo.staticMethod();             // invokestatic
    f.privateMethod();              // invokespecial (private/super/<init>)
    Bar b = ...;
    b.interfaceMethod();            // invokeinterface
    
    // Lambda·String concat 등
    Runnable r = () -> {};          // invokedynamic
}
```

| Invoke | 언제 |
|---|---|
| `invokestatic` | static 메서드 |
| `invokespecial` | 생성자, super.method(), private |
| `invokevirtual` | 일반 인스턴스 메서드 (다형성 O) |
| `invokeinterface` | 인터페이스 메서드 호출 |
| `invokedynamic` | Lambda, String concat (JDK 9+), pattern matching |

> `invokedynamic`은 매우 흥미로움 — call site를 처음 실행 때 결정. Lambda·Pattern Matching의 기반.

---

## javap 실전

```bash
# 기본
javap Hello

# 바이트코드까지
javap -c Hello

# 상수 풀까지
javap -v Hello

# private 포함
javap -p -v Hello

# 클래스 파일 정보 (버전)
javap -v Hello | head -5
# major version: 65 (= Java 21)
# minor version: 0
```

### 자바 버전 ↔ class file 메이저 버전

| Java | Major |
|---|---|
| 8 | 52 |
| 11 | 55 |
| 17 | 61 |
| 21 | 65 |

> `UnsupportedClassVersionError`: 메이저 버전이 안 맞을 때. JDK 다운그레이드 또는 `--release` 옵션.

---

## String concat의 진화

```java
String s = "hello " + name + " (" + count + ")";
```

### Java 8 — StringBuilder

```
new StringBuilder()
  .append("hello ")
  .append(name)
  .append(" (")
  .append(count)
  .append(")")
  .toString()
```

### Java 9+ — invokedynamic

```
invokedynamic makeConcat ...
```

JDK 9부터 `invokedynamic`으로 동작 — VM이 최적의 구현을 런타임에 선택. 빠르고 코드 크기 작음.

---

## JIT 컴파일러 — Tiered Compilation

JDK 7+ 기본. 5개 레벨로 점진적 최적화.

```
   바이트코드
       │
       │ 처음
       ▼
┌─────────────────┐
│ Level 0         │ Interpreter
│ - 가장 느림      │ 카운터 추적
└────────┬────────┘
         │ 카운터 임계치
         ▼
┌─────────────────┐
│ Level 1, 2, 3   │ C1 (Client Compiler)
│ - 빠른 컴파일    │ 단순 최적화
│ - 적당한 성능    │
└────────┬────────┘
         │ 더 많이 실행
         ▼
┌─────────────────┐
│ Level 4         │ C2 (Server Compiler)
│ - 느린 컴파일    │ 강력한 최적화
│ - 최고 성능      │ (escape, inlining, etc)
└─────────────────┘
```

### 레벨 의미

| Level | 무엇 |
|---|---|
| 0 | Interpreter |
| 1 | C1, 프로파일링 없음 |
| 2 | C1, 약간의 프로파일링 |
| 3 | C1, 전체 프로파일링 |
| 4 | C2, C1의 프로파일링 데이터 활용 |

**일반 흐름**: 0 → 3 → 4 (Level 3에서 충분한 프로파일링 후 C2로).

### 옵션

```bash
# 컴파일 활동 출력
-XX:+PrintCompilation
# 출력 예시:
#  142    1       3       java.lang.String::charAt (25 bytes)
# ^^^^   ^^^    ^^^      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
#  ms     id    level   메서드 (바이트 길이)

# C1만 사용 (Tiered 끄기)
-XX:-TieredCompilation

# 처음부터 모두 컴파일 (Interpreter 거의 안 씀)
-Xcomp

# 그 반대 — Interpreter만
-Xint
```

---

## 주요 최적화 5가지

### 1) Method Inlining (인라이닝)

작은 메서드의 호출을 본문으로 펼침. 호출 오버헤드 제거 + 추가 최적화 기회.

```java
// Before:
int square(int x) { return x * x; }
int sumOfSquares(int[] arr) {
    int sum = 0;
    for (int x : arr) sum += square(x);
    return sum;
}

// JIT inline 후 (개념):
int sumOfSquares(int[] arr) {
    int sum = 0;
    for (int x : arr) sum += x * x;
    return sum;
}
```

### Inline 결정 기준

- 메서드 크기 (기본 `-XX:MaxInlineSize=35` 바이트)
- 자주 호출되는 메서드는 더 크게 인라인 (`-XX:FreqInlineSize=325`)
- `final`이거나 같은 receiver type만 호출되면 가능
- 다형성 — 한 receiver type만 보이면 가능 (monomorphic call site)

```bash
-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining
# 출력:
# @ 5   com.example.Foo::bar (10 bytes)   inline (hot)
# @ 12  com.example.Foo::baz (50 bytes)   too big
```

### 2) Escape Analysis

객체가 메서드 밖으로 "탈출"하지 않으면 **Heap 대신 Stack에 할당**(스칼라 치환).

```java
// 이 객체는 메서드 안에서만 살고 죽음
void process() {
    Point p = new Point(1, 2);
    use(p.x, p.y);
}                                    // p는 메서드 종료 시 죽음

// Escape Analysis → Heap 할당 안 함
// (실제로는 Point 객체 만들지 않고 x, y만 레지스터에)
```

```bash
-XX:+DoEscapeAnalysis                # 기본 켜짐
-XX:+EliminateAllocations
```

### 3) Inline Caching

다형성 호출의 receiver type을 캐싱. 같은 type이 반복되면 빠른 path.

```java
List<Foo> list = new ArrayList<>();   // 항상 ArrayList
for (Foo f : list) {                  // f.bar() → ArrayList.iterator().next()로 캐시
    f.bar();
}
```

### 4) Null Check Elimination

JVM이 "이 변수는 null일 수 없다"고 알면 null check를 제거.

```java
String s = "hello";
int len = s.length();                 // null check 컴파일됨
                                      // → JIT가 "s는 리터럴이라 null 아님" 판단 → 제거
```

### 5) Loop Unrolling

루프 횟수가 작거나 고정이면 펼침.

```java
for (int i = 0; i < 4; i++) sum += arr[i];

// JIT unroll:
sum += arr[0];
sum += arr[1];
sum += arr[2];
sum += arr[3];
```

---

## JIT 어셈블리 보기 — hsdis

JDK 21부터 `hsdis-amd64.dll`(또는 `.so`)을 다운로드하면 JIT이 만든 기계어를 볼 수 있다.

```bash
# https://chriswhocodes.com/ 에서 hsdis 다운로드
# JDK_HOME/lib/server/ 에 hsdis-amd64.dll 두기

java -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly -XX:CompileCommand=print,*Foo.bar Main
# Foo.bar 메서드의 컴파일 결과 어셈블리 출력
```

성능 미세 튜닝 외에는 거의 안 봐도 됨. 그러나 **JIT가 진짜로 이런 일을 한다는 증거**.

---

## On-Stack Replacement (OSR)

긴 루프 중간에 메서드를 컴파일해서 **루프 중간에 컴파일된 코드로 전환**. 그래서 `main()`의 큰 루프도 가속됨.

```java
public static void main(String[] args) {
    for (int i = 0; i < 100_000_000; i++) {
        compute(i);
    }
    // main이 끝나기 전에 main 자체가 컴파일됨 (OSR)
}
```

### `-Xcomp`나 JIT 비활성화로 측정 비교

```bash
# 1. 정상
java Bench

# 2. Interpreter만 (JIT X) — 매우 느림
java -Xint Bench

# 3. 처음부터 컴파일 (시작은 느리지만 이후 빠름)
java -Xcomp Bench
```

차이를 보면 JIT의 효과 체감.

---

## 마이크로벤치마크 함정 — JMH 써야 한다

```java
// ❌ 흔한 잘못된 측정
long start = System.currentTimeMillis();
for (int i = 0; i < 100_000_000; i++) {
    Math.sqrt(i);                     // 결과 안 씀
}
long elapsed = System.currentTimeMillis() - start;
// → JIT가 "결과 안 쓰니까 호출도 지움" → 측정 무의미
```

```java
// ✅ JMH (Java Microbenchmark Harness)
@Benchmark
public double sqrtTest(Blackhole bh) {
    return Math.sqrt(value);
}
// Blackhole로 결과를 consume → JIT가 못 지움
// JMH가 warm-up·반복·통계 처리
```

운영급 측정은 무조건 JMH. 단순 `System.currentTimeMillis()` 비교는 거의 항상 함정.

---

## 운영 사례

### 사례 1 — Code Cache 가득 차서 throughput 급락

**증상**: 처음 1시간은 정상. 그 후 갑자기 latency 10배.

**진단**:
```bash
jcmd <pid> Compiler.codecache
# CodeCache: size=245760Kb used=240567Kb max_used=245760Kb free=4097Kb
```

CodeCache 거의 가득 → JIT가 **컴파일 중단** → 새 메서드는 Interpreter 사용 → 느려짐.

**조치**: `-XX:ReservedCodeCacheSize=512m`로 증가.

### 사례 2 — 다형성 함정 (Megamorphic call site)

**증상**: 똑같은 코드가 환경에 따라 10배 차이.

**원인**: 어떤 구현체가 한 receiver type만 받는 환경(monomorphic)에서는 빠르고, 여러 type이 섞이면(megamorphic) 인라인 캐시가 무효화되어 가상 호출 비용 발생.

**진단**: `-XX:+PrintInlining`으로 "too many types" 메시지.

**조치**: 자주 호출되는 인터페이스 메서드에서 receiver 다양성 줄임. 또는 `final` 클래스 사용.

---

## 실습 (Hands-on)

### 1단계 — 바이트코드 읽기

```java
// Calc.java
public class Calc {
    int sum(int[] a) {
        int s = 0;
        for (int x : a) s += x;
        return s;
    }
}
```

```bash
javac Calc.java
javap -c -p -v Calc | head -50
```

루프가 어떻게 바이트코드로 표현됐는지 확인.

### 2단계 — JIT 활동 보기

```java
// HotMethod.java
public class HotMethod {
    public static void main(String[] args) {
        for (int i = 0; i < 1_000_000; i++) {
            compute(i);
        }
    }
    static int compute(int n) {
        return n * n + n;
    }
}
```

```bash
javac HotMethod.java
java -XX:+PrintCompilation HotMethod 2>&1 | findstr HotMethod
# 출력:
# 142    1       3       HotMethod::compute (5 bytes)
# 145    2       4       HotMethod::compute (5 bytes)   ← Level 4 (C2) 컴파일
```

### 3단계 — Inlining 보기

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining HotMethod 2>&1 | findstr compute
```

### 4단계 — 같은 코드, JIT 끄고 켜기

```bash
# JIT 정상
java HotMethod                       # 빠름

# Interpreter만
java -Xint HotMethod                 # 10배 이상 느림
```

### 5단계 — JMH 맛보기

```gradle
// build.gradle
plugins { id 'me.champeau.jmh' version '0.7.2' }
```

```java
// MyBench.java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class MyBench {
    @Benchmark
    public int sum() {
        int s = 0;
        for (int i = 1; i <= 100; i++) s += i;
        return s;
    }
}
```

```bash
./gradlew jmh
```

---

## 더 읽어볼 자료

- 📘 『Optimizing Java』 — 5장 (JIT 내부)
- 📘 『Java Performance: The Definitive Guide』 — 4장 (JIT 튜닝)
- 🔗 [Aleksey Shipilev — JVM Anatomy Quark](https://shipilev.net/jvm/anatomy-quarks/)
- 🔗 [JMH 공식](https://github.com/openjdk/jmh)
- 🔗 [hsdis 빌드/설치](https://chriswhocodes.com/)
- 🎓 Cliff Click — "A JVM Does That?" (전설적 강연)
- 🎓 Aleksey Shipilev — "Java Performance Engineering" 시리즈
