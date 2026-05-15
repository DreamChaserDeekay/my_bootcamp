# Lab 4 — 이미지 최적화 챌린지

## 목표

- 같은 Spring Boot 앱을 5가지 방식으로 패키징
- 크기·시작 시간·취약점 수 측정
- 각 방식의 장단 평가

---

## 환경

Lab 3의 Spring Boot 앱 사용.

---

## 5가지 방식

### A. Single-stage JDK base

```dockerfile
FROM eclipse-temurin:21
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon
ENTRYPOINT ["java","-jar","build/libs/simple-app-0.0.1-SNAPSHOT.jar"]
```

### B. Multi-stage + JRE

```dockerfile
FROM eclipse-temurin:21 AS builder
WORKDIR /build
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
COPY --from=builder /build/build/libs/*.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

### C. Multi-stage + JRE Alpine

```dockerfile
FROM eclipse-temurin:21-alpine AS builder
WORKDIR /build
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /build/build/libs/*.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

### D. Distroless (layered jar)

위 Day 3·Lab 3에 있음.

### E. Spring Boot Buildpack

```bash
./gradlew bootBuildImage --imageName=app:buildpack
```

또는 Paketo builder 명시:
```gradle
tasks.named('bootBuildImage') {
    builder = 'paketobuildpacks/builder-jammy-base:latest'
    imageName = 'app:buildpack'
}
```

---

## 빌드·측정

### 빌드

```powershell
docker build -f Dockerfile.A -t app:a .
docker build -f Dockerfile.B -t app:b .
docker build -f Dockerfile.C -t app:c .
docker build -f Dockerfile.D -t app:d .
./gradlew bootBuildImage --imageName=app:e
```

### 크기

```powershell
docker image ls app
```

기대:
```
REPOSITORY   TAG         SIZE
app          a           1.2GB
app          b           290MB
app          c           220MB
app          d           200MB
app          e           280MB
```

### 시작 시간

```powershell
Measure-Command {
    docker run -d --name test app:a
    while ((docker inspect test --format='{{.State.Health.Status}}' 2>$null) -ne 'healthy') {
        Start-Sleep -Milliseconds 200
    }
}
```

또는 간단히:

```powershell
# 컨테이너 시작 후 endpoint 응답까지 측정
$start = Get-Date
docker run -d --name test -p 8080:8080 app:a
do {
    Start-Sleep -Seconds 1
    $resp = try { Invoke-WebRequest http://localhost:8080/actuator/health -UseBasicParsing } catch { $null }
} while ($null -eq $resp)
$elapsed = (Get-Date) - $start
"$elapsed.TotalSeconds초"
docker stop test; docker rm test
```

### 보안

```powershell
trivy image --severity HIGH,CRITICAL app:a
trivy image --severity HIGH,CRITICAL app:b
trivy image --severity HIGH,CRITICAL app:c
trivy image --severity HIGH,CRITICAL app:d
trivy image --severity HIGH,CRITICAL app:e
```

각각 발견된 CVE 수 기록.

---

## 결과표 (예)

| | 크기 | 시작 | CVE (HIGH+) |
|---|---|---|---|
| A: single-stage JDK | 1.2GB | 5초 | 50+ |
| B: multi JRE | 290MB | 4초 | 10 |
| C: multi JRE alpine | 220MB | 5초 | 3 |
| D: distroless | 200MB | 4초 | 1 |
| E: Buildpack | 280MB | 4초 | 5 |

→ **D (distroless) 또는 E (Buildpack)** 권장.

---

## 추가 실험

### 1) 빌드 시간 비교 (캐시 활용)

```powershell
# 첫 빌드
Measure-Command { docker build -f Dockerfile.B -t app:b --no-cache . }
# 두 번째 (캐시)
Measure-Command { docker build -f Dockerfile.B -t app:b . }
# 소스만 변경 후 세 번째
echo "// trivial" >> src/main/java/com/example/App.java
Measure-Command { docker build -f Dockerfile.B -t app:b . }
```

캐시 친화적 Dockerfile일수록 세 번째 빌드가 빠름.

### 2) Layered jar 효과 측정

소스 한 줄만 바꾸면:
- 일반 jar: 전체 layer rebuild (~50MB push)
- layered jar: application layer만 (~5MB push)

`docker history`로 layer 크기 확인.

### 3) JVM 옵션 cold start

```powershell
# C1 only (빠른 컴파일)
docker run --rm -e JAVA_TOOL_OPTIONS="-XX:TieredStopAtLevel=1" app:d java -jar /app.jar
# vs 기본
docker run --rm app:d
```

시작은 C1만이 빠를 수 있음 — 그러나 throughput 떨어짐.

### 4) GraalVM Native (상급)

`build.gradle`:
```gradle
plugins {
    id 'org.graalvm.buildtools.native' version '0.10.3'
}
```

```bash
./gradlew nativeCompile
```

(시간 5~10분)

```dockerfile
FROM ghcr.io/graalvm/native-image-community:21 AS builder
WORKDIR /build
COPY . .
RUN ./gradlew nativeCompile --no-daemon

FROM gcr.io/distroless/cc-debian12
COPY --from=builder /build/build/native/nativeCompile/app /app
ENTRYPOINT ["/app"]
```

크기: ~80MB. 시작: ~100ms. 그러나 빌드 시간 + 호환성 이슈.

---

## 산출물

- [ ] 5가지 Dockerfile/방식 모두 빌드
- [ ] 크기 비교표
- [ ] 시작 시간 측정
- [ ] CVE 수 측정
- [ ] 자신의 결론 (어느 방식이 운영에 적합한지)

---

## 결론 가이드

- **개발/CI**: B 또는 C (디버깅 쉬움)
- **운영**: D (distroless) 또는 E (Buildpack)
- **Lambda·CLI**: GraalVM Native
- **레거시 호환**: B

> 정답은 컨텍스트에 따라. **측정 → 결정**.

---

## 다음 단계

[Week 2 Checklist](../checklist.md)
