# Lab 3 — Spring Boot 앱 도커라이즈

## 목표

- Spring Boot 앱을 step-by-step Dockerize
- single-stage → multi-stage → distroless 진화
- docker-compose로 DB와 함께 띄움

---

## 1단계 — 미니 Spring Boot 앱

`practice_app/`의 앱 사용. 또는 새로:

```powershell
cd ~/
mkdir simple-app
cd simple-app
```

`build.gradle`:
```gradle
plugins {
    id 'org.springframework.boot' version '3.3.4'
    id 'io.spring.dependency-management' version '1.1.6'
    id 'java'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'
java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories { mavenCentral() }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}
```

`src/main/java/com/example/App.java`:
```java
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
    
    @GetMapping("/")
    public String hello() {
        return "Hello from " + System.getenv().getOrDefault("HOSTNAME", "host");
    }
}
```

`src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: simple-app
management:
  endpoints:
    web:
      exposure:
        include: health,info
server:
  port: 8080
  shutdown: graceful
```

빌드 검증:
```powershell
./gradlew bootJar
java -jar build/libs/*.jar
# curl http://localhost:8080
```

---

## 2단계 — Single-stage Dockerfile (출발선)

`Dockerfile.v1`:
```dockerfile
FROM eclipse-temurin:21
WORKDIR /app
COPY . .
RUN ./gradlew bootJar
ENTRYPOINT ["java","-jar","build/libs/simple-app-0.0.1-SNAPSHOT.jar"]
```

```powershell
docker build -f Dockerfile.v1 -t simple:v1 .
docker image ls simple:v1
# SIZE: ~1.2GB
```

너무 큼. 다음으로 가자.

---

## 3단계 — Multi-stage + JRE

`Dockerfile.v2`:
```dockerfile
# Stage 1: 빌드
FROM eclipse-temurin:21 AS builder
WORKDIR /build
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon || true
COPY src ./src
RUN ./gradlew bootJar --no-daemon

# Stage 2: 런타임
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

```powershell
docker build -f Dockerfile.v2 -t simple:v2 .
docker image ls simple:v2
# SIZE: ~290MB
```

74% 감소.

---

## 4단계 — Distroless

`Dockerfile.v3`:
```dockerfile
# Stage 1: 빌드 (동일)
FROM eclipse-temurin:21 AS builder
WORKDIR /build
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon || true
COPY src ./src
RUN ./gradlew bootJar --no-daemon

# Stage 2: distroless
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=builder /build/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

```powershell
docker build -f Dockerfile.v3 -t simple:v3 .
docker image ls simple:v3
# SIZE: ~200MB
```

83% 감소. shell·apt 없음 → 보안 더 좋음.

---

## 5단계 — Layered Jar

Spring Boot 2.3+에서 layered jar 풀어 캐시 활용.

```dockerfile
FROM eclipse-temurin:21 AS builder
WORKDIR /build
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN ./gradlew bootJar --no-daemon
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination /build/extracted

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
# 변경 빈도 순으로 layer
COPY --from=builder /build/extracted/dependencies/         ./
COPY --from=builder /build/extracted/spring-boot-loader/   ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/          ./
EXPOSE 8080
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
```

소스 변경 후 rebuild → application layer만 push (몇 MB) → 운영 배포 빠름.

---

## 6단계 — BuildKit cache mount

빌드 캐시(Maven `.m2`, Gradle `~/.gradle`) 유지:

```dockerfile
# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21 AS builder
WORKDIR /build
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination /build/extracted

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=builder /build/extracted/dependencies/         ./
COPY --from=builder /build/extracted/spring-boot-loader/   ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/          ./
EXPOSE 8080
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
```

두 번째 빌드부터 Gradle 의존성 재다운로드 X.

---

## 7단계 — docker-compose로 DB와 함께

`docker-compose.yml`:
```yaml
services:
  app:
    build: .
    ports: ["8080:8080"]
    environment:
      JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=70
    depends_on:
      db:
        condition: service_healthy
    networks: [backend]

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: appdb
      POSTGRES_USER: app
      POSTGRES_PASSWORD: secret
    volumes:
      - db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app"]
      interval: 5s
      timeout: 3s
      retries: 5
    networks: [backend]

  adminer:
    image: adminer
    ports: ["8081:8080"]
    profiles: [debug]
    networks: [backend]

networks:
  backend:

volumes:
  db-data:
```

```powershell
docker compose up -d
docker compose ps
docker compose logs -f app

curl http://localhost:8080
curl http://localhost:8080/actuator/health

# debug 도구 포함
docker compose --profile debug up -d
# http://localhost:8081 adminer
```

---

## 8단계 — Spring Boot Buildpack 비교

Dockerfile 없이:

```powershell
./gradlew bootBuildImage --imageName=simple:buildpack

docker image ls simple
# REPO          TAG         SIZE
# simple        v3          200MB
# simple        buildpack    280MB
```

Buildpack은 약간 큼 (CA cert·디버그 도구 포함), OCI label 자동.

---

## 9단계 — Trivy로 보안 스캔

```powershell
trivy image simple:v3
# Total: 5 (CRITICAL: 0, HIGH: 2)

trivy image --severity HIGH,CRITICAL simple:v3
```

비교:
```powershell
trivy image --severity HIGH,CRITICAL eclipse-temurin:21
trivy image --severity HIGH,CRITICAL eclipse-temurin:21-jre
trivy image --severity HIGH,CRITICAL gcr.io/distroless/java21-debian12
```

distroless가 보통 가장 적음.

---

## 10단계 — 이미지 분석 (dive)

```bash
dive simple:v3
# 좌측: layer 목록
# 우측: 각 layer의 파일 변경
# 'tab'으로 전환
```

application layer만 작아야 정상 (~few MB).

---

## 산출물 체크리스트

- [ ] Spring Boot 앱 빌드·실행
- [ ] Dockerfile v1 (single-stage)
- [ ] Dockerfile v2 (multi-stage + JRE)
- [ ] Dockerfile v3 (distroless)
- [ ] Layered jar 활용
- [ ] BuildKit cache mount
- [ ] docker-compose로 DB와 함께
- [ ] Trivy 스캔
- [ ] 크기 비교 표 작성 (~1.2GB → 200MB)

---

## 다음 단계

[Lab 4 — Image Optimization](lab4_image_optimization.md)
