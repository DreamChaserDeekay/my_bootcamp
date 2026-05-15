# Day 1 — ClassLoader

## 한 줄 요약

ClassLoader는 `.class` 파일을 찾아서 메모리에 적재하는 컴포넌트. **부모에게 먼저 묻는 위임 모델**로 보안과 격리를 동시에 달성한다. Spring·Tomcat·Hibernate가 동적으로 클래스를 만들고 적재하는 일의 기반.

## 학습 목표

- [ ] 3단계 위임 모델을 설명한다 (Bootstrap / Platform / Application)
- [ ] 클래스 로딩 5단계를 안다 (Loading → Linking[verify/prepare/resolve] → Initializing)
- [ ] `Class.forName()` vs `ClassLoader.loadClass()`의 차이를 안다
- [ ] 사용자 정의 ClassLoader를 만든다
- [ ] Tomcat·Spring Boot fat jar의 ClassLoader 구조를 이해한다
- [ ] WAR redeploy 누수의 원인을 안다

---

## ClassLoader 계층 (JDK 9+)

JDK 9 모듈 시스템 이후 명칭이 바뀌었다:

```
                  ┌──────────────────────────────┐
                  │   Bootstrap ClassLoader      │   네이티브 (C++)
                  │   $JAVA_HOME/lib/modules     │   java.base 등
                  └──────────────┬───────────────┘
                                 │ 부모
                  ┌──────────────▼───────────────┐
                  │   Platform ClassLoader        │   JDK Java 클래스 일부
                  │   (옛 Extension Loader)        │   java.sql, java.xml 등
                  └──────────────┬───────────────┘
                                 │ 부모
                  ┌──────────────▼───────────────┐
                  │   Application ClassLoader     │   -cp / -classpath
                  │   (System ClassLoader)        │   사용자 코드
                  └──────────────┬───────────────┘
                                 │ 부모 (선택)
                  ┌──────────────▼───────────────┐
                  │   Custom ClassLoader          │   플러그인·OSGi·Tomcat 웹앱
                  │   (사용자 정의)                 │
                  └──────────────────────────────┘
```

| 로더 | 어디서 가져옴 |
|---|---|
| **Bootstrap** | `$JAVA_HOME/lib/modules` (Java 핵심 모듈) |
| **Platform** | JDK 확장 모듈 |
| **Application** | `-cp`, classpath 환경변수, `-jar` |
| **Custom** | 직접 구현 (URLClassLoader 등) |

---

## 위임 모델 — Parent Delegation

자식이 클래스 로딩 요청을 받으면 **부모에게 먼저 위임**.

```java
// java.lang.ClassLoader.loadClass()의 핵심 (간략화)
protected Class<?> loadClass(String name, boolean resolve) {
    synchronized (getClassLoadingLock(name)) {
        // 1. 이미 로드됐는지 확인
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            try {
                // 2. 부모에게 먼저 요청
                if (parent != null) {
                    c = parent.loadClass(name, false);
                } else {
                    c = findBootstrapClassOrNull(name);
                }
            } catch (ClassNotFoundException e) {
                // 부모가 못 찾음
            }

            // 3. 부모도 못 찾으면 직접 찾기
            if (c == null) {
                c = findClass(name);
            }
        }
        return c;
    }
}
```

### 왜 위임?

1. **보안** — 사용자가 `java.lang.String`을 정의해도 Bootstrap이 먼저 정답을 줌. 표준 라이브러리 위조 방지.
2. **유일성** — 같은 클래스가 두 번 로드되지 않음 (한 ClassLoader에서). `==` 비교 가능.
3. **타입 격리** — 다른 ClassLoader에 같은 이름 클래스를 동시에 가질 수 있음 (Web container).

### `==` 함정

```java
// Custom ClassLoader 두 개에서 같은 클래스 로딩
ClassLoader cl1 = new URLClassLoader(...);
ClassLoader cl2 = new URLClassLoader(...);

Class<?> a = cl1.loadClass("com.example.Foo");
Class<?> b = cl2.loadClass("com.example.Foo");

System.out.println(a == b);          // false ← 다른 ClassLoader의 다른 클래스
System.out.println(a.getName().equals(b.getName())); // true

Object o = a.newInstance();
Foo f = (Foo) o;                     // ClassCastException!
// 같은 패키지·클래스명이라도 다른 ClassLoader에서 온 것은 다른 타입
```

> Spring DevTools·Tomcat redeploy 디버깅에서 자주 만나는 함정.

---

## 클래스 로딩의 5단계

```
1. Loading      .class 파일 읽어서 메모리에
                ↓
2. Linking
   a. Verify    바이트코드 유효성 검사
   b. Prepare   static 필드 기본값 할당 (0, null 등)
   c. Resolve   심볼릭 참조 → 직접 참조
                ↓
3. Initializing  static 블록 실행, static 변수 실제 초기화
                ↓
   사용 가능
```

### Prepare vs Initialize

```java
public class Foo {
    static int x = 5;                // Prepare 후: x = 0
                                     // Initialize 후: x = 5
    static {
        System.out.println("init");  // Initialize 시점에 실행
    }
}
```

### 트리거

다음 중 하나가 일어나야 **Initialize**:
- `new Foo()` (인스턴스 생성)
- `Foo.x = 10` (static 필드 할당/조회, **단** 상수는 제외)
- `Foo.doSomething()` (static 메서드 호출)
- `Class.forName("Foo")` (기본은 init 함)
- 자식 클래스가 초기화될 때 부모

**Initialize 안 하는 경우**:
- `Foo.class` (Class 객체만 얻기)
- `ClassLoader.loadClass("Foo")` (로드만)
- `Foo[].class` (배열 클래스 참조)
- `Foo.SOME_CONSTANT` (compile-time constant)

```java
Class<?> c = Class.forName("Foo");           // static {} 실행됨
Class<?> c2 = Foo.class;                     // 실행 안 됨
Class<?> c3 = cl.loadClass("Foo");           // 실행 안 됨 (load만)
```

> Spring의 `@PostConstruct`는 Initialize 다음. `class Foo {}`만 있으면 안 일어남.

---

## Spring Boot fat jar의 ClassLoader

`java -jar app.jar`로 실행되는 Spring Boot fat jar는 평범한 ClassPath가 아니다.

```
app.jar (실제 구조)
├── BOOT-INF/
│   ├── classes/com/example/MyApp.class        ← 사용자 코드
│   └── lib/
│       ├── spring-core-6.x.jar
│       └── spring-context-6.x.jar
│       └── ...
├── META-INF/
│   └── MANIFEST.MF (Main-Class: org.springframework.boot.loader.launch.JarLauncher)
└── org/springframework/boot/loader/...        ← 부트 로더
```

`JarLauncher`가 **`LaunchedClassLoader`**라는 커스텀 로더로 `BOOT-INF/lib/` 안의 jar들을 적재.

```bash
# Spring Boot 앱 안에서
this.getClass().getClassLoader();
# → org.springframework.boot.loader.launch.LaunchedClassLoader

# 그 부모는?
.getParent();
# → jdk.internal.loader.ClassLoaders$AppClassLoader
```

이런 구조 때문에:
- 사용자 코드가 lib의 jar 클래스에 접근 가능
- 그러나 동적으로 lib의 클래스를 `getResource()`할 때 경로가 `jar:file:.../app.jar!/BOOT-INF/lib/foo.jar!/...` 같은 nested URL
- 일부 라이브러리(예: 옛 ASM 버전)가 이 형식을 못 읽어서 깨짐 → `spring-boot-maven-plugin`의 `unpack` 옵션

---

## Tomcat WebappClassLoader

Tomcat에서 여러 webapp을 같은 JVM에 띄울 때 격리가 필요. **위임 모델을 거꾸로** — 자식이 먼저 찾는다.

```
Tomcat 표준:
  System CL ──▶ Common CL ──▶ Catalina CL
                                 └─▶ WebappCL #1   ← /webapp1 ROOT.war
                                 └─▶ WebappCL #2   ← /webapp2 admin.war

WebappCL은 위임 모델 반전:
  1. 자기 WEB-INF/lib에서 먼저 찾기
  2. 못 찾으면 부모에게
  → 같은 라이브러리도 webapp마다 다른 버전 가능
```

### WAR redeploy 누수 — 흔한 OOM

`/webapp1.war`를 redeploy하면:
1. 옛 `WebappCL #1`이 GC되어야 함
2. 옛 클래스 + 옛 클래스의 모든 인스턴스가 사라져야 함

그런데 **다음이 있으면 옛 ClassLoader가 GC되지 않음**:
- 옛 webapp이 등록한 JDBC Driver (DriverManager가 참조)
- ThreadLocal에 옛 클래스 인스턴스
- 옛 클래스의 static 필드가 외부에서 참조됨 (Logger 등)

→ **Metaspace 누수**, 결국 OOM.

```java
// ❌ 옛 webapp의 static
public class MyService {
    private static final Logger LOG = Logger.getLogger(MyService.class);
    private static final ThreadLocal<UserContext> CTX = new ThreadLocal<>();
}

// 외부 ThreadPool의 스레드가 살아있고 그 ThreadLocal에 UserContext 들어있으면
// → UserContext가 MyService를 참조
// → MyService.class가 살아있음
// → MyService를 로드한 ClassLoader가 살아있음
// → 그 ClassLoader가 로드한 모든 클래스가 살아있음 (Metaspace에)
```

> Tomcat이 redeploy 시 누수 감지·정리해주지만 100% 막진 못함. 운영서는 **redeploy 대신 새 인스턴스 띄우고 LB 전환** 권장.

---

## 사용자 정의 ClassLoader

플러그인 시스템·OSGi·동적 클래스 로딩을 만들 때.

```java
import java.io.*;
import java.nio.file.*;

public class MyClassLoader extends ClassLoader {
    private final Path baseDir;

    public MyClassLoader(Path baseDir, ClassLoader parent) {
        super(parent);
        this.baseDir = baseDir;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Path file = baseDir.resolve(name.replace('.', '/') + ".class");
        if (!Files.exists(file)) {
            throw new ClassNotFoundException(name);
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}

// 사용
MyClassLoader cl = new MyClassLoader(Paths.get("/plugins"), getClass().getClassLoader());
Class<?> plugin = cl.loadClass("com.plugin.Main");
Object instance = plugin.getConstructor().newInstance();
```

### ServiceLoader (표준 SPI)

대부분의 플러그인 시나리오는 직접 ClassLoader 만들 필요 없이 ServiceLoader로 충분.

```java
// META-INF/services/com.example.Plugin 파일에 구현체 클래스 이름들 나열

ServiceLoader<Plugin> plugins = ServiceLoader.load(Plugin.class);
for (Plugin p : plugins) {
    p.run();
}
```

JDK·Spring·JDBC Driver 등 다수가 이 메커니즘 사용.

---

## 동적 바이트코드 — Spring AOP의 기반

Spring AOP는 두 가지 동적 프록시 메커니즘 사용:

### 1) JDK Dynamic Proxy (인터페이스 기반)

```java
// 런타임에 인터페이스의 구현체 생성
import java.lang.reflect.*;

interface Service { void doIt(); }

class RealService implements Service {
    public void doIt() { System.out.println("real"); }
}

Service real = new RealService();
Service proxy = (Service) Proxy.newProxyInstance(
    Service.class.getClassLoader(),
    new Class<?>[]{Service.class},
    (p, method, args) -> {
        System.out.println("before " + method.getName());
        Object result = method.invoke(real, args);
        System.out.println("after " + method.getName());
        return result;
    }
);

proxy.doIt();
// before doIt
// real
// after doIt
```

### 2) CGLIB (클래스 상속 기반)

인터페이스가 없을 때, **타깃 클래스의 자식 클래스를 동적 생성**.

```java
// CGLIB
import org.springframework.cglib.proxy.*;

class RealService { public void doIt() { System.out.println("real"); } }

Enhancer e = new Enhancer();
e.setSuperclass(RealService.class);
e.setCallback((MethodInterceptor) (obj, method, args, methodProxy) -> {
    System.out.println("before");
    Object result = methodProxy.invokeSuper(obj, args);
    System.out.println("after");
    return result;
});
RealService proxy = (RealService) e.create();
proxy.doIt();
```

**제약**:
- `final` 클래스는 상속 못 함 → 프록시 불가
- `final` 메서드는 override 못 함 → AOP 안 먹음
- `private` 메서드는 어차피 override 불가

> Week 3 Day 3에서 Spring AOP가 어떻게 이걸 활용하는지 깊이.

---

## ❌ 위험 vs ✅ 안전

### 1) static에 ClassLoader 잡아두기

```java
// ❌ 다른 ClassLoader가 로드한 객체를 static에 보관
public class Cache {
    public static final Map<String, Object> ENTRIES = new HashMap<>();
}
// → 외부 webapp의 객체 들어가면 그 webapp redeploy 시 누수
```

```java
// ✅ WeakReference로 약 참조
public static final Map<String, WeakReference<Object>> ENTRIES = new HashMap<>();
```

### 2) Thread.currentThread().setContextClassLoader() 후 안 복구

```java
// ❌
ClassLoader original = Thread.currentThread().getContextClassLoader();
Thread.currentThread().setContextClassLoader(pluginLoader);
runPlugin();
// → 예외 발생 시 ContextClassLoader가 plugin인 상태로 ThreadPool에 반환됨
// → 다음 작업이 엉뚱한 ClassLoader 사용

// ✅
try {
    Thread.currentThread().setContextClassLoader(pluginLoader);
    runPlugin();
} finally {
    Thread.currentThread().setContextClassLoader(original);
}
```

---

## 실습 (Hands-on)

[Lab 3 — 사용자 정의 ClassLoader](labs/lab3_classloader_demo.md)에서 진행.

빠른 실험:

```java
// 어떤 클래스 누가 로드?
String.class.getClassLoader();           // null (Bootstrap)
javax.sql.DataSource.class.getClassLoader();
// → jdk.internal.loader.ClassLoaders$PlatformClassLoader

MyApp.class.getClassLoader();
// → jdk.internal.loader.ClassLoaders$AppClassLoader
// (또는 Spring Boot면 LaunchedClassLoader)

// 부모 사슬
ClassLoader cl = MyApp.class.getClassLoader();
while (cl != null) {
    System.out.println(cl);
    cl = cl.getParent();
}
```

---

## 더 읽어볼 자료

- 📘 『Java Concurrency in Practice』 (Goetz) — 7장 일부에 ClassLoader
- 📘 『Inside the Java Virtual Machine』 (Venners) — Loading & Linking
- 🔗 [JLS §12 — Execution](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html)
- 🔗 [Spring Boot 실행 가능 jar 구조](https://docs.spring.io/spring-boot/docs/current/reference/html/executable-jar.html)
- 🔗 [Tomcat ClassLoader 문서](https://tomcat.apache.org/tomcat-10.1-doc/class-loader-howto.html)
- 🎓 InfoQ — "Demystifying Java Class Loaders"
