# Lab 3 — 사용자 정의 ClassLoader

## 목표

- ClassLoader 위임 모델을 코드로 확인
- 같은 클래스를 두 로더에 적재했을 때 `==` 비교 결과
- ClassCastException의 원인 체감
- Spring AOP가 만드는 동적 클래스의 ClassLoader 추적

---

## 1단계 — 현재 ClassLoader 탐색

```java
// LoaderInspect.java
public class LoaderInspect {
    public static void main(String[] args) {
        // 클래스별 ClassLoader
        print("String", String.class);
        print("DataSource", javax.sql.DataSource.class);
        print("LoaderInspect", LoaderInspect.class);
        print("ArrayList", java.util.ArrayList.class);
        
        // 부모 사슬
        ClassLoader cl = LoaderInspect.class.getClassLoader();
        System.out.println("\n부모 사슬:");
        while (cl != null) {
            System.out.println("  " + cl);
            cl = cl.getParent();
        }
        System.out.println("  null (Bootstrap)");
    }
    
    static void print(String name, Class<?> c) {
        ClassLoader cl = c.getClassLoader();
        System.out.printf("%-15s → %s%n", name, cl == null ? "Bootstrap" : cl);
    }
}
```

```bash
javac LoaderInspect.java
java LoaderInspect
```

예상 출력:
```
String          → Bootstrap
DataSource      → jdk.internal.loader.ClassLoaders$PlatformClassLoader@...
LoaderInspect   → jdk.internal.loader.ClassLoaders$AppClassLoader@...
ArrayList       → Bootstrap

부모 사슬:
  jdk.internal.loader.ClassLoaders$AppClassLoader@...
  jdk.internal.loader.ClassLoaders$PlatformClassLoader@...
  null (Bootstrap)
```

---

## 2단계 — 사용자 정의 ClassLoader

`plugins/com/plugin/Greeter.class` 라는 외부 클래스를 동적으로 로딩.

```java
// Greeter.java (plugins 디렉토리에 별도로 컴파일)
package com.plugin;
public class Greeter {
    public String greet() {
        return "Hello from plugin loaded at " + System.currentTimeMillis();
    }
}
```

```bash
mkdir plugins
mkdir plugins\com
mkdir plugins\com\plugin
javac -d plugins Greeter.java
# plugins\com\plugin\Greeter.class 생성됨

# 그 다음 plugins를 -cp에서 제외하고
del Greeter.class                    # 메인 cp에서 보이지 않게
```

### Custom Loader

```java
// PluginLoader.java
import java.io.*;
import java.nio.file.*;

public class PluginLoader extends ClassLoader {
    private final Path baseDir;
    
    public PluginLoader(Path baseDir, ClassLoader parent) {
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
```

### 사용

```java
// PluginMain.java
import java.lang.reflect.*;
import java.nio.file.*;

public class PluginMain {
    public static void main(String[] args) throws Exception {
        PluginLoader loader = new PluginLoader(
            Paths.get("plugins"),
            PluginMain.class.getClassLoader()
        );
        
        Class<?> greeterClass = loader.loadClass("com.plugin.Greeter");
        System.out.println("Loader: " + greeterClass.getClassLoader());
        
        Object greeter = greeterClass.getConstructor().newInstance();
        Method greet = greeterClass.getMethod("greet");
        String result = (String) greet.invoke(greeter);
        System.out.println(result);
    }
}
```

```bash
javac PluginLoader.java PluginMain.java
java PluginMain
```

예상 출력:
```
Loader: PluginLoader@...
Hello from plugin loaded at 1737000000000
```

> 사용자 정의 로더가 동작! 메인 클래스패스에 `Greeter`가 없는데도 plugins에서 적재.

---

## 3단계 — 동일 클래스, 두 로더 → 다른 타입

```java
// TwoLoaders.java
import java.nio.file.*;

public class TwoLoaders {
    public static void main(String[] args) throws Exception {
        PluginLoader l1 = new PluginLoader(
            Paths.get("plugins"), TwoLoaders.class.getClassLoader());
        PluginLoader l2 = new PluginLoader(
            Paths.get("plugins"), TwoLoaders.class.getClassLoader());
        
        Class<?> c1 = l1.loadClass("com.plugin.Greeter");
        Class<?> c2 = l2.loadClass("com.plugin.Greeter");
        
        System.out.println("c1 == c2 ? " + (c1 == c2));      // false
        System.out.println("c1.getName().equals(c2.getName()) ? " 
            + c1.getName().equals(c2.getName()));            // true
        
        Object inst1 = c1.getConstructor().newInstance();
        
        // Cross-cast 시도
        try {
            // c2 타입으로 cast (실제 객체는 c1 타입)
            Object cast = c2.cast(inst1);
            System.out.println("cast 성공");
        } catch (ClassCastException e) {
            System.out.println("ClassCastException: " + e.getMessage());
        }
    }
}
```

```bash
javac TwoLoaders.java
java TwoLoaders
```

예상 출력:
```
c1 == c2 ? false
c1.getName().equals(c2.getName()) ? true
ClassCastException: ...
```

이게 **Spring DevTools / Tomcat redeploy 시 발생하는 ClassCastException의 정체**.

---

## 4단계 — Spring AOP 프록시의 ClassLoader

Spring Boot 프로젝트에서:

```java
@SpringBootApplication
public class App implements CommandLineRunner {
    private final MyService service;
    public App(MyService s) { this.service = s; }
    
    @Override
    public void run(String... args) {
        System.out.println("service.class = " + service.getClass());
        System.out.println("loader = " + service.getClass().getClassLoader());
        
        // 인터페이스 기반이면 Proxy.class
        // 클래스 기반(CGLIB)이면 ...$$EnhancerBySpringCGLIB$$...
    }
    
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}

@Service
class MyService {
    @Transactional
    public void doIt() { ... }
}
```

```
service.class = class com.example.MyService$$SpringCGLIB$$0
loader = jdk.internal.loader.ClassLoaders$AppClassLoader@...
```

→ Spring이 **CGLIB**로 동적 서브클래스를 만들어 `@Transactional`을 위한 프록시를 적용.

---

## 5단계 — Plugin Hot-Reload (실험)

매 호출마다 새 ClassLoader → 매번 디스크에서 클래스 다시 읽음.

```java
public class HotReload {
    public static void main(String[] args) throws Exception {
        while (true) {
            PluginLoader cl = new PluginLoader(
                Paths.get("plugins"), HotReload.class.getClassLoader());
            
            Class<?> greeter = cl.loadClass("com.plugin.Greeter");
            Object instance = greeter.getConstructor().newInstance();
            String result = (String) greeter.getMethod("greet").invoke(instance);
            System.out.println(result);
            
            cl = null;
            instance = null;
            // 이론적으로 GC가 옛 ClassLoader와 클래스 정리
            
            Thread.sleep(2000);
        }
    }
}
```

다른 터미널에서 `Greeter.java` 수정 → `javac -d plugins` → 5초 내 새 메시지 출력.

### 누수 위험

이 방식을 운영서에 적용하면 Metaspace 누수 가능:
- 옛 ClassLoader가 GC 되어야 함
- 그러나 어디서 옛 클래스 참조가 살아있으면 안 죽음
- → Metaspace에 옛 클래스 메타데이터가 쌓임

```bash
java -XX:MaxMetaspaceSize=64m -XX:+UseG1GC -Xlog:gc+metaspace=trace HotReload
# 한참 돌리면 Metaspace 추이 확인
```

---

## 6단계 — Spring Boot fat jar의 LaunchedClassLoader

```java
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
        System.out.println(App.class.getClassLoader());
        // jar로 실행 시: org.springframework.boot.loader.launch.LaunchedClassLoader
        // IDE에서: jdk.internal.loader.ClassLoaders$AppClassLoader
    }
}
```

```bash
./gradlew bootJar
java -jar build/libs/app.jar
```

→ `LaunchedClassLoader`가 출력. 그 부모는 AppClassLoader.

---

## 산출물

이 lab으로 다음을 검증:

- [ ] JDK 핵심 클래스는 Bootstrap, 사용자 코드는 AppClassLoader가 적재
- [ ] 사용자 정의 ClassLoader로 외부 .class 동적 로딩 가능
- [ ] 같은 클래스라도 두 ClassLoader에서 적재되면 다른 타입 → ClassCastException
- [ ] Spring AOP는 CGLIB로 동적 서브클래스 생성
- [ ] Spring Boot fat jar는 LaunchedClassLoader 사용

---

## 더 깊이

- [Tomcat — ClassLoader Howto](https://tomcat.apache.org/tomcat-10.1-doc/class-loader-howto.html) — webapp 격리 메커니즘
- [Spring Boot — Executable JAR Format](https://docs.spring.io/spring-boot/docs/current/reference/html/executable-jar.html)
- ASM 또는 ByteBuddy 라이브러리로 직접 바이트코드 생성해보기
