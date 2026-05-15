# syntax=docker/dockerfile:1.7

# Stage 1: 빌드
FROM eclipse-temurin:21 AS builder
WORKDIR /build
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon

RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination /build/extracted

# Stage 2: distroless 런타임
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

# layered jar — 변경 빈도 순으로 layer
COPY --from=builder /build/extracted/dependencies/         ./
COPY --from=builder /build/extracted/spring-boot-loader/   ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/          ./

EXPOSE 8080

# JVM 옵션
ENV JAVA_TOOL_OPTIONS="\
    -XX:MaxRAMPercentage=75 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/heapdump.hprof \
    -Xlog:gc*:stdout"

LABEL org.opencontainers.image.source="https://github.com/example/devops-lab"
LABEL org.opencontainers.image.licenses="MIT"
LABEL org.opencontainers.image.description="DevOps bootcamp practice app"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
