# Day 3 — Multi-stage Build · Distroless

## 한 줄 요약

빌드 도구(JDK·Maven)는 무겁고 런타임에 불필요 → **multi-stage build**로 분리. 최소 OS 이미지(**alpine → slim → distroless → scratch**)로 100MB 이하 가능. 보안 표면적도 감소.

## 학습 목표

- [ ] Multi-stage build 작성
- [ ] alpine·slim·distroless·scratch 차이
- [ ] Spring Boot 이미지 100MB 이하로
- [ ] Java용 베스트 베이스 이미지 선택
- [ ] BuildKit cache mount
- [ ] Spring Boot Buildpack vs Dockerfile

---

## Multi-stage Build

빌드용 stage와 런타임 stage 분리.

```dockerfile
# ── Stage 1: 빌드 ────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw dependency:resolve

COPY src/ src/
RUN ./mvnw clean package -DskipTests
# /build/target/app.jar 생성됨

# ── Stage 2: 런타임 ──────────────────
FROM eclipse-temurin:21-jre

COPY --from=builder /build/target/app.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

빌드:
```bash
docker build -t my-app:multi .
```

결과 이미지에는 **JRE + app.jar만**. JDK·Maven·소스코드 X.

| | Single-stage | Multi-stage |
|---|---|---|
| 이미지 크기 | ~1.2GB | ~280MB |
| 빌드 시간 | 비슷 | 비슷 |
| 보안 | JDK·Maven 취약점 노출 | 최소 |

---

## 베이스 이미지 비교 (Java 21)

| 베이스 | 크기 | 특징 |
|---|---|---|
| `eclipse-temurin:21` | ~430MB | JDK (개발용) |
| `eclipse-temurin:21-jre` | ~270MB | JRE만 |
| `eclipse-temurin:21-jre-alpine` | ~190MB | musl libc, 작음 |
| `eclipse-temurin:21-jre-noble` (Ubuntu 24.04) | ~280MB | 표준 glibc |
| `gcr.io/distroless/java21-debian12` | ~190MB | **distroless** |
| `gcr.io/distroless/java21-debian12:nonroot` | ~190MB | nonroot 사용자 |

> 운영 권장: **distroless** 또는 alpine-jre.

### Alpine 주의

Alpine은 musl libc 사용 (glibc 아님). 일부 native 라이브러리 호환 문제.
- Java의 JIT 성능: glibc보다 약간 떨어짐
- Spring Boot 대부분 잘 동작

### Distroless의 정체

Google이 만든 **OS 없는 컨테이너 이미지**. shell·apt·debug 도구 모두 없음. Java/Python/Node 런타임만.

```dockerfile
FROM gcr.io/distroless/java21-debian12

COPY app.jar /app.jar
CMD ["/app.jar"]                        # ENTRYPOINT는 자동으로 java 실행
```

### Distroless의 장점

- **보안 표면적 최소** — shell, package manager 없음 → 공격자가 들어와도 할 게 없음
- **이미지 크기 작음** — 190MB
- **CVE 적음** — apt·bash 등 흔한 취약점 없음

### Distroless의 도전

- 디버깅 어려움 (shell 없음) → `:debug` 변종 (busybox shell)

```bash
docker run -it --entrypoint /busybox/sh \
    gcr.io/distroless/java21-debian12:debug
```

---

## 운영 Spring Boot Dockerfile (완전판)

```dockerfile
# syntax=docker/dockerfile:1.7

# ── Stage 1: 빌드 ────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# 의존성 캐시 활용
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:go-offline -B

# 소스 복사 후 빌드
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -DskipTests -B

# layered jar 풀기
RUN java -Djarmode=layertools -jar target/*.jar extract --destination /build/extracted

# ── Stage 2: 런타임 ──────────────────
FROM gcr.io/distroless/java21-debian12:nonroot

# JVM 옵션
ENV JAVA_TOOL_OPTIONS="\
    -XX:MaxRAMPercentage=75 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -Xlog:gc*:stdout"

# layered jar — 변경 빈도 순으로 layer
COPY --from=builder /build/extracted/dependencies/        ./
COPY --from=builder /build/extracted/spring-boot-loader/  ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/         ./

USER nonroot
EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

### Spring Boot Layered Jar의 효과

```
jar 내부:
├── application/         ← 자주 바뀜 (코드)
├── snapshot-dependencies/  ← 자주 바뀜 (snapshot)
├── dependencies/        ← 가끔 바뀜 (release deps)
└── spring-boot-loader/  ← 거의 안 바뀜
```

위 4개를 별도 layer로 풀어 COPY → 코드 변경 시에도 의존성 layer 재사용 → push/pull 빠름.

```bash
# Spring Boot 자체 도구로 확인
java -Djarmode=layertools -jar app.jar list
# dependencies
# spring-boot-loader
# snapshot-dependencies
# application
```

---

## Spring Boot Buildpack (대안)

Dockerfile 없이:

```bash
./mvnw spring-boot:build-image
# 또는
./gradlew bootBuildImage
```

자동으로:
- 적절한 베이스 이미지 (Ubuntu + JRE)
- layered jar 활용
- nonroot 사용자
- OCI labels
- BOM·SBOM 생성

이미지 결과: ~280MB.

```yaml
# Gradle 설정 예
tasks.named('bootBuildImage') {
    imageName = "ghcr.io/example/my-app:${version}"
    builder = 'paketobuildpacks/builder-jammy-base'
    environment = ['BP_JVM_VERSION': '21']
}
```

> Dockerfile 학습 후엔 **Buildpack도 고려**. 표준화 좋음.

---

## 이미지 크기 추적

```bash
docker image ls
# REPOSITORY   TAG     SIZE
# my-app       v1      290MB        ← 운영용
# my-app       v1-fat   1.2GB        ← single-stage

# 자세히
docker history my-app:v1
```

### 크기 분석 도구

```bash
# dive — layer 분석
brew install dive
dive my-app:v1
# 인터랙티브 TUI로 layer별 변경 파일 확인
```

```bash
docker run --rm -it \
    -v /var/run/docker.sock:/var/run/docker.sock \
    wagoodman/dive my-app:v1
```

---

## scratch — 최소의 최소

```dockerfile
FROM scratch
COPY my-static-binary /
ENTRYPOINT ["/my-static-binary"]
```

- 0 byte 베이스
- 정적 컴파일된 binary (Go·Rust)만 가능
- Java는 GraalVM Native Image 사용

```dockerfile
# Go 예
FROM golang:1.23 AS builder
COPY . /src
WORKDIR /src
RUN CGO_ENABLED=0 GOOS=linux go build -o app

FROM scratch
COPY --from=builder /src/app /app
ENTRYPOINT ["/app"]
# 최종: ~10MB
```

> Java는 일반 JVM이면 distroless가 최선.

---

## GraalVM Native Image (선택)

Spring Boot 3.x는 native 지원:

```bash
./mvnw native:compile -Pnative
# 또는
./gradlew nativeCompile
```

```dockerfile
FROM ghcr.io/graalvm/native-image-community:21 AS builder
WORKDIR /build
COPY . .
RUN ./mvnw native:compile -Pnative

FROM gcr.io/distroless/cc-debian12
COPY --from=builder /build/target/app /app
ENTRYPOINT ["/app"]
# 최종: ~80MB, 시작 < 100ms
```

장점:
- 시작 빠름 (Lambda·CLI)
- 메모리 적게 (~30MB)
- 이미지 작음

단점:
- 빌드 시간 매우 김 (5분+)
- 리플렉션 hint 필요
- 일부 라이브러리 호환 X
- 디버깅 어려움

> 일반 운영 backend엔 **여전히 JVM 권장**. Lambda는 native 고려.

---

## 운영 사례

### 사례 1 — 1GB → 280MB

single-stage + JDK base:
```dockerfile
FROM eclipse-temurin:21
COPY . /app
WORKDIR /app
RUN mvn package
ENTRYPOINT ["java","-jar","target/app.jar"]
# 1.2 GB
```

multi-stage + JRE:
```dockerfile
# 위의 multi-stage 예
# 280 MB
```

distroless로:
```dockerfile
FROM gcr.io/distroless/java21-debian12:nonroot
# 190 MB
```

배포 시간·디스크·네트워크 모두 절약.

### 사례 2 — JIT가 컨테이너 limit 무시

옛 JDK는 cgroup 인식 X → host 메모리를 보고 Heap 결정 → OOM.

JDK 10+ `-XX:+UseContainerSupport` (기본 켜짐) + JDK 21:
```bash
-XX:MaxRAMPercentage=75
```

컨테이너 limit의 75%를 Heap. 자세한 건 JVM 부트캠프 참조.

---

## 실습 (Hands-on)

### 1단계 — 단순 single-stage

```dockerfile
# Dockerfile.fat
FROM eclipse-temurin:21
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src ./src
RUN ./mvnw package -DskipTests
ENTRYPOINT ["java","-jar","target/app.jar"]
```

```bash
docker build -f Dockerfile.fat -t fat:v1 .
docker image ls fat:v1
# ~1.2GB
```

### 2단계 — Multi-stage

```dockerfile
# Dockerfile.slim
FROM eclipse-temurin:21 AS builder
WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=builder /build/target/*.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

```bash
docker build -f Dockerfile.slim -t slim:v1 .
# ~280MB
```

### 3단계 — Distroless

```dockerfile
# Dockerfile.distroless
FROM eclipse-temurin:21 AS builder
# (위와 동일)

FROM gcr.io/distroless/java21-debian12:nonroot
COPY --from=builder /build/target/*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
```

```bash
docker build -f Dockerfile.distroless -t distro:v1 .
# ~190MB
```

### 4단계 — Layered Jar

Spring Boot 2.3+ 기본:

```dockerfile
FROM eclipse-temurin:21 AS builder
WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw package -DskipTests
RUN java -Djarmode=layertools -jar target/*.jar extract --destination /build/extracted

FROM eclipse-temurin:21-jre
COPY --from=builder /build/extracted/dependencies/         ./
COPY --from=builder /build/extracted/spring-boot-loader/   ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/          ./
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
```

소스 1줄 수정 후 `docker build` → **application layer만 rebuild**. 빌드·push 빠름.

### 5단계 — Spring Boot Buildpack

```bash
./gradlew bootBuildImage --imageName=buildpack-app:v1
docker image ls buildpack-app
# ~280MB, OCI label·SBOM 자동
```

### 6단계 — dive로 분석

```bash
brew install dive
dive distro:v1
# 각 layer가 어떤 파일을 추가/변경했는지
```

---

## 더 읽어볼 자료

- 🔗 [Docker — Multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- 🔗 [Distroless](https://github.com/GoogleContainerTools/distroless)
- 🔗 [Spring Boot — Layered Jars](https://docs.spring.io/spring-boot/docs/current/reference/html/container-images.html)
- 🔗 [Paketo Buildpacks](https://paketo.io/)
- 🔗 [dive — image analyzer](https://github.com/wagoodman/dive)
- 🎓 SpringOne — "Container Best Practices"
