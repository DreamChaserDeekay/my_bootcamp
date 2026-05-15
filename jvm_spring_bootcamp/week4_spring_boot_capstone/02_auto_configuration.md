# Day 2 — Auto-Configuration

## 한 줄 요약

`@SpringBootApplication`은 내부에 `@EnableAutoConfiguration`을 포함. 이는 classpath의 모든 jar에서 `META-INF/spring/...AutoConfiguration.imports`를 읽어 200+개 AutoConfiguration 클래스를 가져온다. 각 클래스는 `@Conditional` 어노테이션으로 "이 조건이 맞을 때만 활성"되는 Bean 묶음.

## 학습 목표

- [ ] `@EnableAutoConfiguration` 메커니즘을 안다
- [ ] `AutoConfiguration.imports` 파일 형식
- [ ] `@Conditional` 7가지 패턴
- [ ] `@AutoConfiguration`의 `before` / `after` 순서 제어
- [ ] AutoConfiguration 디버깅 (`--debug` 또는 conditions endpoint)
- [ ] Custom Starter 만드는 방법 (Day 3에서 깊이)

---

## @SpringBootApplication 해부

```java
@SpringBootApplication
public class App { ... }

// 위는 아래와 동일
@SpringBootConfiguration            // = @Configuration
@EnableAutoConfiguration            // AutoConfiguration 트리거
@ComponentScan                      // 현재 패키지부터 스캔
public class App { ... }
```

---

## @EnableAutoConfiguration 흐름

```
@EnableAutoConfiguration
   ↓
@Import(AutoConfigurationImportSelector.class)
   ↓ deferImportSelector.selectImports() 호출 시점
   ↓
1. classpath의 모든 jar에서 다음 파일을 찾음:
   META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
   
2. 각 파일은 AutoConfiguration 클래스 이름의 목록:
   org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
   org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
   ...
   
3. 각 클래스의 @Conditional 평가 → 조건 만족하면 ApplicationContext에 임포트
```

### AutoConfiguration.imports 파일 형식

옛 Boot 2.x: `META-INF/spring.factories`의 한 항목.

```
# META-INF/spring.factories (2.x)
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  com.example.MyAutoConfig,\
  com.example.OtherAutoConfig
```

Boot 2.7+ / 3.x: 별도 파일.

```
# META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.example.MyAutoConfig
com.example.OtherAutoConfig
```

> 한 줄에 한 클래스. 주석은 `#`.

---

## AutoConfiguration 클래스 예

`DataSourceAutoConfiguration`(실제 Boot 내부):

```java
@AutoConfiguration(before = SqlInitializationAutoConfiguration.class)
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })
@ConditionalOnMissingBean(type = "io.r2dbc.spi.ConnectionFactory")
@EnableConfigurationProperties(DataSourceProperties.class)
@Import(DataSourcePoolMetadataProvidersConfiguration.class)
public class DataSourceAutoConfiguration {
    
    @Configuration(proxyBeanMethods = false)
    @Conditional(EmbeddedDatabaseCondition.class)
    @ConditionalOnMissingBean({ DataSource.class, XADataSource.class })
    @Import(EmbeddedDataSourceConfiguration.class)
    protected static class EmbeddedDatabaseConfiguration { }
    
    @Configuration(proxyBeanMethods = false)
    @Conditional(PooledDataSourceCondition.class)
    @ConditionalOnMissingBean({ DataSource.class, XADataSource.class })
    @Import({ DataSourceConfiguration.Hikari.class, ... })
    protected static class PooledDataSourceConfiguration { ... }
}
```

읽어내기:
- `@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })` — 이 클래스들이 classpath에 있을 때만
- `@ConditionalOnMissingBean(type = "io.r2dbc.spi.ConnectionFactory")` — R2DBC가 등록 안 됐을 때만
- `@EnableConfigurationProperties(DataSourceProperties.class)` — `@ConfigurationProperties` 활성화

---

## @Conditional 패턴 7가지

### 1. @ConditionalOnClass

classpath에 특정 클래스 있을 때.

```java
@ConditionalOnClass(name = "com.mysql.cj.jdbc.Driver")
public class MySqlConfig { ... }
```

### 2. @ConditionalOnMissingClass

특정 클래스 **없을** 때.

```java
@ConditionalOnMissingClass("javax.servlet.Filter")
```

### 3. @ConditionalOnBean / @ConditionalOnMissingBean

다른 Bean의 존재·부재.

```java
@Bean
@ConditionalOnMissingBean
public DataSource dataSource(DataSourceProperties props) {
    // 사용자가 DataSource Bean을 정의 안 했을 때만 등록
}
```

이게 **AutoConfiguration의 핵심**. 기본값을 제공하되 사용자 정의가 있으면 양보.

### 4. @ConditionalOnProperty

application.yml의 값.

```java
@ConditionalOnProperty(
    name = "feature.cache.enabled",
    havingValue = "true",
    matchIfMissing = false        // 키가 없으면 안 활성
)
```

```yaml
feature:
  cache:
    enabled: true
```

### 5. @ConditionalOnWebApplication

```java
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
```

### 6. @ConditionalOnExpression

SpEL 표현식.

```java
@ConditionalOnExpression("${app.foo} && '${app.env}' == 'prod'")
```

### 7. @ConditionalOnJava

```java
@ConditionalOnJava(JavaVersion.SEVENTEEN)        // JDK 17+
```

### 커스텀 — Condition 직접 구현

```java
public class MyCondition implements Condition {
    @Override
    public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata md) {
        return ctx.getEnvironment().containsProperty("my.feature");
    }
}

@Conditional(MyCondition.class)
@Configuration
public class MyConfig { ... }
```

---

## AutoConfiguration 순서

```java
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
public class JpaAutoConfiguration { ... }
```

`after` / `before` / `afterName` / `beforeName`로 명시. Spring Boot가 토폴로지 정렬.

> 일반적으로 우리가 만들 땐 신경 안 써도 됨 — Spring Boot가 알아서.

---

## 디버깅 — Conditions Report

### 방법 1 — `--debug`

```bash
./gradlew bootRun --args='--debug'
```

출력에 `CONDITIONS EVALUATION REPORT` 섹션:

```
========================
CONDITIONS EVALUATION REPORT
========================

Positive matches:
-----------------
   AopAutoConfiguration matched:
      - @ConditionalOnProperty (spring.aop.auto=true) matched
      - @ConditionalOnClass found 'org.springframework.context.annotation.EnableAspectJAutoProxy'
   
   DataSourceAutoConfiguration#PooledDataSourceConfiguration matched:
      - DataSource found supported pooled data source

Negative matches:
-----------------
   RabbitAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'com.rabbitmq.client.Channel'

Exclusions:
-----------

Unconditional classes:
----------------------
   org.springframework.boot.autoconfigure.cache.GenericCacheConfiguration
```

→ 어떤 AutoConfig가 왜 활성/비활성인지 한눈에.

### 방법 2 — Actuator conditions

```yaml
management:
  endpoints:
    web:
      exposure:
        include: conditions
```

```bash
curl http://localhost:8080/actuator/conditions | jq
# positiveMatches / negativeMatches / unconditionalClasses
```

운영서에서도 (보안 조심) 활성 가능.

---

## AutoConfiguration 제외하기

```java
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class App { ... }
```

또는 yml:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

> DataSource는 있지만 우리가 직접 만들 때 — 보통은 `@ConditionalOnMissingBean`가 알아서 양보.

---

## @EnableConfigurationProperties

```java
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String name;
    private int port;
    // setter, getter
}
```

```yaml
app:
  name: My App
  port: 8080
```

```java
@Service
public class MyService {
    @Autowired AppProperties props;
    public void use() { System.out.println(props.getName()); }
}
```

### AutoConfiguration에서

```java
@AutoConfiguration
@EnableConfigurationProperties(MyProperties.class)
public class MyAutoConfig {
    @Bean
    public MyService myService(MyProperties props) { ... }
}
```

`@Component` 안 붙어도 `@EnableConfigurationProperties`로 등록.

### Record (JDK 16+)

```java
@ConfigurationProperties(prefix = "app")
public record AppProperties(String name, int port) {}
```

Boot 3.x에서 record가 깔끔.

---

## 자주 보는 AutoConfiguration 목록

| AutoConfig | 활성 조건 |
|---|---|
| `WebMvcAutoConfiguration` | spring-webmvc on classpath |
| `WebFluxAutoConfiguration` | spring-webflux on classpath, WebMvc 없을 때 |
| `DataSourceAutoConfiguration` | DataSource on classpath |
| `JpaAutoConfiguration` | EntityManager on classpath, DataSource Bean |
| `JdbcTemplateAutoConfiguration` | JdbcTemplate on classpath |
| `RedisAutoConfiguration` | Lettuce/Jedis |
| `KafkaAutoConfiguration` | spring-kafka |
| `SecurityAutoConfiguration` | spring-security |
| `ActuatorAutoConfiguration` | spring-boot-actuator |
| `TomcatAutoConfiguration` | (의존성 안에) 항상 |
| `MicrometerAutoConfiguration` | Micrometer on classpath |

> Spring Boot의 강력함은 이 200+개의 AutoConfiguration이 **합리적 기본값**으로 동작하기 때문.

---

## 운영 사례

### 사례 1 — Bean이 등록 안 됨

**증상**: `MyService`를 `@Autowired`하는데 NoSuchBeanDefinitionException.

**진단**:
```bash
curl /actuator/conditions | jq '.contexts.application.negativeMatches' | grep MyService
```

또는 `--debug`로 시작.

**원인**: `@ConditionalOnProperty(name="my.feature")` 있는데 yml에 안 적음.

**조치**: yml에 추가 또는 Condition 제거.

### 사례 2 — 두 개의 DataSource

```java
// AutoConfig가 만든 기본 DataSource
// + 사용자가 만든 @Bean DataSource
// → 2개! NoUniqueBeanDefinitionException
```

해결:
- `@Primary` 한쪽에
- `@Qualifier`로 명시
- 또는 AutoConfig 제외

### 사례 3 — JPA + JDBC 둘 다 활성화

JPA의 EntityManager 트랜잭션과 직접 JdbcTemplate 트랜잭션이 따로 놀음. **해결**: 같은 PlatformTransactionManager 공유 (Boot가 자동 처리). 명시 설정 시 주의.

---

## 실습 (Hands-on)

### 1단계 — Conditions Report 보기

```bash
./gradlew bootRun --args='--debug' 2>&1 | grep -A 5 "DataSourceAutoConfiguration"
```

### 2단계 — Actuator endpoint

```yaml
management:
  endpoints:
    web:
      exposure:
        include: conditions,beans,env,configprops
```

```bash
curl http://localhost:8080/actuator/conditions | jq
curl http://localhost:8080/actuator/beans | jq
curl http://localhost:8080/actuator/configprops | jq
```

### 3단계 — `@ConditionalOnMissingBean` 실험

```java
@Configuration
public class MyAppConfig {
    
    @Bean
    @ConditionalOnMissingBean
    public Greeter defaultGreeter() {
        return new Greeter("default");
    }
}

@Configuration
public class UserConfig {
    
    @Bean
    public Greeter myGreeter() {
        return new Greeter("custom");
    }
}
```

→ Greeter 1개만 등록 (custom). 사용자 정의가 기본을 이긴다.

UserConfig 주석 처리 → "default"가 등록.

### 4단계 — Custom @ConfigurationProperties

```yaml
my:
  greeting:
    prefix: "Hello"
    suffix: "!"
```

```java
@ConfigurationProperties(prefix = "my.greeting")
public record GreetingProps(String prefix, String suffix) {}

@Configuration
@EnableConfigurationProperties(GreetingProps.class)
public class GreetingConfig {
    @Bean
    public Greeter greeter(GreetingProps p) {
        return new Greeter(p.prefix() + " " + "world" + p.suffix());
    }
}
```

```bash
curl /actuator/configprops | jq '."my.greeting"'
```

---

## 더 읽어볼 자료

- 📘 『Spring Boot: Up and Running』 (Mark Heckler, O'Reilly)
- 🔗 [Spring Boot Reference — Auto-Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration)
- 🔗 [@AutoConfiguration vs @Configuration](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/AutoConfiguration.html)
- 🔗 [Spring Boot AutoConfiguration 소스](https://github.com/spring-projects/spring-boot/tree/main/spring-boot-project/spring-boot-autoconfigure)
- 🎓 SpringOne — "Behind the Scenes of Auto-Configuration"
