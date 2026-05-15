# Lab 7 — AutoConfiguration 디버그·추적

## 목표

- Spring Boot가 어떤 AutoConfiguration을 적용/거부하는지 추적
- `@Conditional` 매치 결과를 코드 수준에서 확인
- Custom Starter를 만들어 자기 AutoConfiguration 작성

---

## 1단계 — Conditions Report

```bash
./gradlew bootRun --args='--debug' 2>&1 > debug.log

# 출력에서
grep -A 5 "POSITIVE\|NEGATIVE" debug.log | head -100
```

또는 Actuator:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: conditions
```

```bash
curl http://localhost:8080/actuator/conditions | jq '.contexts.application.positiveMatches | keys' 
# 활성화된 AutoConfig 목록

curl http://localhost:8080/actuator/conditions | jq '.contexts.application.negativeMatches.RabbitAutoConfiguration'
# 왜 RabbitAutoConfig가 비활성?
```

---

## 2단계 — @ConditionalOnMissingBean 동작 확인

### 기본 (Boot 제공)

```java
// Spring Boot가 자동으로 만든 DataSource
@RestController
class Check {
    @Autowired DataSource ds;
    
    @GetMapping("/ds")
    public String ds() { return ds.getClass().getName(); }
}
// → HikariDataSource (기본)
```

### 사용자 정의

```java
@Configuration
class MyDsConfig {
    @Bean
    public DataSource myDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setMaximumPoolSize(50);
        // ...
        return ds;
    }
}
```

→ Boot의 기본 DataSource가 양보 (`@ConditionalOnMissingBean`).

`/ds` 다시 호출 → 같은 `HikariDataSource`지만 풀 크기 50.

---

## 3단계 — Custom Starter 만들기

### 프로젝트 구조

```
my-greeter-parent/
├── settings.gradle
├── my-greeter-autoconfigure/
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/example/greeter/
│       │   ├── Greeter.java
│       │   ├── GreeterProperties.java
│       │   └── GreeterAutoConfiguration.java
│       └── resources/META-INF/spring/
│           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── my-greeter-starter/
    └── build.gradle
```

### settings.gradle

```gradle
rootProject.name = 'my-greeter-parent'
include 'my-greeter-autoconfigure', 'my-greeter-starter'
```

### my-greeter-autoconfigure/build.gradle

```gradle
plugins {
    id 'java-library'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.example'
version = '1.0.0'
java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories { mavenCentral() }

dependencyManagement {
    imports {
        mavenBom 'org.springframework.boot:spring-boot-dependencies:3.3.4'
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-autoconfigure'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'
}
```

### Greeter.java

```java
package com.example.greeter;

public class Greeter {
    private final String prefix;
    private final String suffix;
    
    public Greeter(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }
    
    public String greet(String name) {
        return prefix + " " + name + suffix;
    }
}
```

### GreeterProperties.java

```java
package com.example.greeter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "my.greeter")
public record GreeterProperties(String prefix, String suffix) {
    public GreeterProperties {
        if (prefix == null) prefix = "Hello,";
        if (suffix == null) suffix = "!";
    }
}
```

### GreeterAutoConfiguration.java

```java
package com.example.greeter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(name = "my.greeter.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(GreeterProperties.class)
public class GreeterAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public Greeter greeter(GreeterProperties props) {
        return new Greeter(props.prefix(), props.suffix());
    }
}
```

### imports 파일

`my-greeter-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.example.greeter.GreeterAutoConfiguration
```

### my-greeter-starter/build.gradle

```gradle
plugins { id 'java-library' }

group = 'com.example'
version = '1.0.0'

dependencies {
    api project(':my-greeter-autoconfigure')
}
```

### 빌드·publish (local)

```bash
./gradlew publishToMavenLocal
```

---

## 4단계 — 사용자 앱에서 사용

### 새 Boot 프로젝트의 build.gradle

```gradle
plugins {
    id 'org.springframework.boot' version '3.3.4'
    id 'io.spring.dependency-management' version '1.1.6'
    id 'java'
}

repositories {
    mavenLocal()                                 // ← 로컬 publish 본
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'com.example:my-greeter-starter:1.0.0'
}
```

### 사용

```java
@RestController
class Test {
    @Autowired Greeter greeter;
    
    @GetMapping("/hi/{name}")
    public String hi(@PathVariable String name) {
        return greeter.greet(name);
    }
}
```

### application.yml로 커스터마이즈

```yaml
my:
  greeter:
    prefix: "안녕"
    suffix: " 세상아!"
```

```bash
curl http://localhost:8080/hi/dev
# → "안녕 dev 세상아!"
```

### 사용자 정의 우선

사용자가 `@Bean Greeter`를 직접 제공하면 AutoConfig 양보 (`@ConditionalOnMissingBean`).

```java
@Configuration
class UserConfig {
    @Bean
    Greeter greeter() {
        return new Greeter("YO", "");
    }
}
```

```bash
curl http://localhost:8080/hi/dev
# → "YO dev"   (yml 무시, 사용자 Bean 사용)
```

---

## 5단계 — Conditions로 확인

```bash
curl http://localhost:8080/actuator/conditions | jq '.contexts.application.positiveMatches.GreeterAutoConfiguration'
```

또는 negative:

```bash
# my.greeter.enabled=false로 설정 후
curl http://localhost:8080/actuator/conditions | jq '.contexts.application.negativeMatches.GreeterAutoConfiguration'
```

조건 매치/미스 결과를 직접 확인.

---

## 6단계 — Configuration Processor 효과

`spring-boot-configuration-processor` 의존성을 추가하고 빌드하면 `META-INF/spring-configuration-metadata.json` 자동 생성:

```json
{
  "properties": [
    {
      "name": "my.greeter.prefix",
      "type": "java.lang.String",
      "description": "..."
    },
    ...
  ]
}
```

IDE(IntelliJ)에서 application.yml 자동완성·hover 도큐먼트 표시.

---

## 7단계 — `info` 추가

```java
@Bean
@ConditionalOnMissingBean
public InfoContributor greeterInfo(GreeterProperties props) {
    return builder -> builder.withDetail("greeter", Map.of(
        "prefix", props.prefix(),
        "suffix", props.suffix()
    ));
}
```

```bash
curl /actuator/info
# {
#   "greeter": { "prefix": "안녕", "suffix": "..." }
# }
```

---

## 산출물

이 lab으로 확인:

- [ ] /actuator/conditions로 AutoConfig 매치 확인
- [ ] @ConditionalOnMissingBean으로 사용자 정의 양보
- [ ] Custom Starter 두 모듈 (autoconfigure + starter)
- [ ] @ConfigurationProperties + record
- [ ] spring-configuration-metadata.json 자동 생성

---

## 다음 단계

[Week 4 Checklist](../checklist.md) → [Capstone](../05_capstone.md)
