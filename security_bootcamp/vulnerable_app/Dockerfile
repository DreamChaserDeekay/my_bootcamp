# Multi-stage: build + runtime
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

# Runtime: distroless, non-root
FROM gcr.io/distroless/java17-debian12:nonroot
COPY --from=build /src/build/libs/*.jar /app.jar
EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["java","-jar","/app.jar"]
