# Day 2 — Dockerfile 베스트 프랙티스

## 한 줄 요약

좋은 Dockerfile은 **작고·빠르고·안전**. 캐시 활용을 위한 순서, 한 줄로 합치기, root 사용 금지, 명확한 ENTRYPOINT/CMD.

## 학습 목표

- [ ] Dockerfile 명령 15개를 안다
- [ ] 캐시가 깨지는 시점과 그것을 활용하는 순서
- [ ] `.dockerignore`로 빌드 컨텍스트 축소
- [ ] ENTRYPOINT vs CMD vs RUN
- [ ] 비루트 사용자
- [ ] 시그널·PID 1 문제 (init·tini)

---

## Dockerfile 명령 15개

| 명령 | 무엇 |
|---|---|
| `FROM` | 베이스 이미지 |
| `RUN` | 빌드 시 명령 실행 (새 layer) |
| `COPY` | host → 이미지 복사 |
| `ADD` | COPY + URL/tar 자동 풀기 (보통 COPY 권장) |
| `WORKDIR` | 작업 디렉토리 |
| `ENV` | 환경변수 |
| `ARG` | 빌드 시 변수 |
| `EXPOSE` | 사용 포트 (문서·메타데이터) |
| `VOLUME` | 마운트 지점 |
| `USER` | 실행 사용자 |
| `LABEL` | 메타데이터 |
| `ENTRYPOINT` | 컨테이너 시작 명령 (고정) |
| `CMD` | ENTRYPOINT의 기본 인자 |
| `HEALTHCHECK` | 헬스체크 명령 |
| `STOPSIGNAL` | 종료 시그널 (기본 SIGTERM) |
| `ONBUILD` | (드물게) 자식 이미지에서 trigger |
| `SHELL` | RUN의 기본 셸 변경 |

---

## ENTRYPOINT vs CMD

| | ENTRYPOINT | CMD |
|---|---|---|
| 역할 | 컨테이너 시작 명령 | 기본 인자 (또는 명령) |
| `docker run image arg`에 의해 | 변하지 않음 | 덮어쓰기 가능 |
| 권장 형식 | exec form (`["java","-jar","app"]`) | exec form |

### 패턴

```dockerfile
# 가장 흔한 패턴
ENTRYPOINT ["java", "-jar", "/app.jar"]
CMD ["--spring.profiles.active=prod"]   # 기본 인자

# docker run image                       → java -jar /app.jar --spring.profiles.active=prod
# docker run image --debug               → java -jar /app.jar --debug
# docker run image bash                  → java -jar /app.jar bash (이상함)
```

### shell form 함정

```dockerfile
# ❌ shell form
ENTRYPOINT java -jar /app.jar
# 실제로는: /bin/sh -c "java -jar /app.jar"
# → 시그널 전파 X. SIGTERM 받아도 java 안 죽음
```

```dockerfile
# ✅ exec form (JSON 배열)
ENTRYPOINT ["java", "-jar", "/app.jar"]
# → java가 PID 1, 시그널 직접 받음
```

---

## RUN 최적화

### 1) 한 줄로 합치기

```dockerfile
# ❌ layer 3개 + 캐시
RUN apt-get update
RUN apt-get install -y curl
RUN apt-get install -y jq
# → 3개 layer

# ✅ 한 layer
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        jq && \
    rm -rf /var/lib/apt/lists/*
```

`rm -rf /var/lib/apt/lists/*`로 cache 제거 — 이미지 크기 감소.

### 2) 캐시 친화적 순서

```dockerfile
# 잘 안 바뀌는 것 먼저 (캐시 활용)
FROM eclipse-temurin:21-jdk

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:resolve              # 의존성만 받아옴 — 자주 안 바뀜

COPY src/ src/                          # 소스 — 자주 바뀜
RUN mvn package
```

소스만 바뀌면 `mvn dependency:resolve` 캐시 활용 — **빌드 시간 5배 단축** 가능.

### 3) BuildKit cache mount

BuildKit 활성화 시:

```dockerfile
# syntax=docker/dockerfile:1.7

RUN --mount=type=cache,target=/root/.m2 \
    mvn package
```

`/root/.m2`를 빌드 캐시 디렉토리로. 이미지엔 포함 안 됨, 다음 빌드 재사용.

```bash
# BuildKit 활성화 (기본 켜짐)
docker build .
# 또는
DOCKER_BUILDKIT=1 docker build .
```

---

## `.dockerignore` — 빌드 컨텍스트

`docker build .`는 현재 디렉토리 전체를 daemon에 전송. 큰 폴더(`.git/`, `node_modules/`, `build/`)가 있으면 느림.

`.dockerignore`:
```
.git/
.gitignore
.idea/
.vscode/
target/
build/
node_modules/
*.md
!README.md
.env*
*.log
**/__pycache__
**/.pytest_cache
```

`.gitignore`처럼 동작.

---

## 안전한 사용자

기본은 **root**로 실행. 컨테이너 탈출 시 위험.

```dockerfile
FROM eclipse-temurin:21-jre

# 사용자·그룹 생성
RUN groupadd -r app && useradd -r -g app -s /bin/false app

COPY --chown=app:app target/app.jar /app.jar

USER app                                # 이후 명령은 app 사용자로
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
docker run my-app:latest
docker exec -it <container> sh
$ whoami
app                                     # root 아님
```

> **베스트 이미지(eclipse-temurin 등)는 이미 nonroot 사용자 제공**. 활용 권장.

---

## HEALTHCHECK

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1
```

`docker ps`에 healthy/unhealthy 표시. k8s는 자체 probe 사용 (Dockerfile HEALTHCHECK 무시).

---

## PID 1 문제

컨테이너의 PID 1은 특별:
- 시그널이 자동 전파 안 됨 (커널이 보호)
- 자식 프로세스 reap 안 함 → zombie 누적

### 해결책

1. **JVM이 PID 1** — 보통 OK (JVM은 시그널 잘 처리)
2. **tini** — 작은 init 프로세스

```dockerfile
# tini 설치
RUN apt-get update && apt-get install -y tini && rm -rf /var/lib/apt/lists/*
ENTRYPOINT ["tini", "--", "java", "-jar", "/app.jar"]
```

```dockerfile
# Docker run --init 옵션
# docker run --init my-app
# → docker가 init을 자동 주입
```

---

## ARG vs ENV

```dockerfile
# ARG — 빌드 시에만, runtime엔 없음
ARG VERSION=1.0
RUN echo "Building $VERSION"

# ENV — 빌드 시 + runtime
ENV APP_VERSION=$VERSION
# 컨테이너 안에서 echo $APP_VERSION → 1.0
```

```bash
docker build --build-arg VERSION=2.0 .
```

### 시크릿은 ARG/ENV에 넣지 마라

```dockerfile
# ❌ ENV에 password — 이미지에 영구 저장
ENV DB_PASSWORD=secret

# ❌ build --build-arg PASSWORD=... — history에 남음
ARG PASSWORD
RUN echo $PASSWORD > /etc/foo
```

```dockerfile
# ✅ BuildKit secret mount
# syntax=docker/dockerfile:1.7

RUN --mount=type=secret,id=password \
    cat /run/secrets/password | login.sh
# 이미지에 포함 안 됨
```

```bash
docker build --secret id=password,src=./pass.txt .
```

> 운영 환경에선 **runtime에 시크릿 주입** (k8s Secret, vault).

---

## LABEL — 표준화된 메타데이터

OCI annotations:

```dockerfile
LABEL org.opencontainers.image.source="https://github.com/example/myapp"
LABEL org.opencontainers.image.revision="abc123def456"
LABEL org.opencontainers.image.created="2026-05-15T00:00:00Z"
LABEL org.opencontainers.image.version="1.2.3"
LABEL org.opencontainers.image.title="My App"
LABEL org.opencontainers.image.description="..."
LABEL org.opencontainers.image.licenses="MIT"
LABEL org.opencontainers.image.authors="DK <dk@example.com>"
```

GitHub Container Registry가 자동으로 사용 (PR/release 연결).

---

## 운영 사례

### 사례 1 — 빌드가 매번 5분

```dockerfile
# 모든 layer가 매번 새로
FROM eclipse-temurin:21
COPY . /app                          # ❌ .git까지 다 복사
WORKDIR /app
RUN mvn package                       # ❌ 의존성도 매번 다시
```

조치: `.dockerignore` + 의존성 먼저 + COPY 순서.

### 사례 2 — 컨테이너가 SIGTERM 무시

```dockerfile
ENTRYPOINT java -jar /app.jar         # ❌ shell form
```

`docker stop`이 10초 후 SIGKILL → 데이터 손상.

조치: exec form 또는 tini.

### 사례 3 — 이미지에 .env 파일 포함

```dockerfile
COPY . /app
# .env가 그대로 들어가서 시크릿 노출
```

조치: `.dockerignore`에 `.env*`, secret mount 사용.

---

## 실습 (Hands-on)

### 1단계 — 첫 Dockerfile

```dockerfile
# Dockerfile.simple
FROM alpine:3.20
RUN apk add --no-cache curl
ENTRYPOINT ["curl"]
CMD ["--version"]
```

```bash
docker build -f Dockerfile.simple -t curl-tool .
docker run curl-tool                    # --version 출력
docker run curl-tool https://example.com  # CMD 덮어씀
```

### 2단계 — 캐시 효과 측정

```dockerfile
# Dockerfile.cache
FROM ubuntu:22.04
RUN apt-get update && apt-get install -y curl jq
COPY pom.xml /tmp/
RUN echo "dependency install (slow)" && sleep 5
COPY src/ /tmp/src/
RUN echo "compile (fast)"
```

```bash
docker build -f Dockerfile.cache -t cache-demo .
# 첫 빌드: 모두 실행

# src 변경 후 다시
echo "// changed" > src/app.txt
docker build -f Dockerfile.cache -t cache-demo .
# dependency install 캐시, compile만 실행
```

### 3단계 — .dockerignore 효과

```bash
mkdir test
cd test
git init                                # .git 생성 (큼)
echo "FROM alpine" > Dockerfile

# .dockerignore 없이
docker build -t test:v1 . --progress=plain
# Context: 큰 크기

# .dockerignore 추가
echo ".git/" > .dockerignore
docker build -t test:v2 . --progress=plain
# Context: 작은 크기
```

### 4단계 — 비루트 사용자

```dockerfile
FROM ubuntu:22.04
RUN groupadd -r app && useradd -r -g app app
USER app
ENTRYPOINT ["whoami"]
```

```bash
docker build -t nonroot .
docker run nonroot
# app
```

### 5단계 — Hadolint로 검사

```bash
docker run --rm -i hadolint/hadolint < Dockerfile
# DL3008 warning: Pin versions in apt-get install
# DL3009 warning: Delete the apt-get lists after installing
# ...
```

권장 사항을 따라 Dockerfile 개선.

---

## 더 읽어볼 자료

- 🔗 [Dockerfile reference](https://docs.docker.com/engine/reference/builder/)
- 🔗 [Best practices for writing Dockerfiles](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/)
- 🔗 [Hadolint](https://github.com/hadolint/hadolint) — Dockerfile linter
- 🔗 [OCI Image Annotations](https://github.com/opencontainers/image-spec/blob/main/annotations.md)
- 🔗 [BuildKit](https://docs.docker.com/build/buildkit/)
- 📘 『Docker Deep Dive』
