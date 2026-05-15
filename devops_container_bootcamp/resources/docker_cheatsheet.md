# Docker Cheatsheet

## 이미지

```bash
docker images
docker pull <image>:<tag>
docker rmi <image>
docker image prune              # dangling
docker image prune -a           # 안 쓰는 이미지 모두
docker history <image>          # layer 분석
docker image inspect <image>
docker image inspect <image> --format='{{.RepoDigests}}'
```

## 컨테이너

```bash
docker run -d --name web -p 8080:80 nginx
docker run -it ubuntu bash
docker run --rm alpine echo hi  # 종료 후 삭제

docker ps                       # 실행 중
docker ps -a                    # 종료된 것도
docker stop <id>
docker start <id>
docker restart <id>
docker rm <id>
docker rm -f <id>               # 강제

docker logs <id>
docker logs -f --tail 100 <id>
docker exec -it <id> bash
docker exec <id> env
docker top <id>                 # 프로세스
docker stats                    # 자원 사용량 실시간
docker port <id>                # 포트 매핑
docker inspect <id>

docker container prune          # 종료된 모두
```

## `docker run` 옵션

```bash
-d                              # detached
-it                             # interactive + TTY
--rm                            # 종료 시 자동 삭제
--name <n>                      # 이름
-p 8080:80                      # host:container 포트
-P                              # 모든 EXPOSE 포트 자동 매핑
-v $(pwd):/data                 # bind mount
-v vol-name:/data               # named volume
-e KEY=value                    # 환경변수
--env-file .env
-w /app                         # working dir
--network mynet
--memory 1g
--cpus 1.0
--restart unless-stopped        # always / on-failure / unless-stopped
--init                          # tini 자동 주입
--read-only                     # rootfs 읽기 전용
--user 1000                     # UID
```

## 빌드

```bash
docker build -t myapp:1.0 .
docker build -f Dockerfile.prod -t myapp:prod .
docker build --no-cache -t myapp:fresh .
docker build --target builder -t myapp:builder .       # multi-stage 특정 단계

# BuildKit
docker buildx build --platform linux/amd64,linux/arm64 -t myapp:multi .
docker buildx build --secret id=token,src=./token.txt .
docker buildx build --cache-from type=gha --cache-to type=gha,mode=max .
```

## 네트워크

```bash
docker network ls
docker network create mynet
docker network create --driver bridge --subnet 172.20.0.0/16 mynet
docker network inspect mynet
docker network connect mynet <container>
docker network disconnect mynet <container>
docker network rm mynet
```

## 볼륨

```bash
docker volume ls
docker volume create mydata
docker volume inspect mydata
docker volume rm mydata
docker volume prune

# bind vs volume
-v $(pwd):/app                  # bind (host 디렉토리)
-v mydata:/data                 # named volume (Docker가 관리)
-v /tmp                         # anonymous volume
```

## 시스템

```bash
docker system df                # 디스크 사용량
docker system prune             # 정리 (사용 안 하는 것)
docker system prune -a          # 이미지까지
docker system prune --volumes   # 볼륨까지
docker system events            # 실시간 이벤트

docker info                     # daemon 정보
docker version
```

## docker-compose

```bash
docker compose up
docker compose up -d            # detached
docker compose up --build       # 강제 rebuild
docker compose up app           # 특정 서비스만

docker compose down             # stop + remove
docker compose down -v          # 볼륨까지

docker compose ps
docker compose logs -f
docker compose logs -f app
docker compose restart app
docker compose exec app sh
docker compose run --rm app sh

docker compose --profile debug up
docker compose -f docker-compose.yml -f docker-compose.prod.yml up
```

## Registry (Docker Hub, GHCR)

```bash
# 로그인
docker login                                    # Docker Hub
docker login ghcr.io                            # GitHub
docker login registry.example.com

# tag·push
docker tag myapp:1.0 ghcr.io/user/myapp:1.0
docker push ghcr.io/user/myapp:1.0

# pull
docker pull ghcr.io/user/myapp:1.0

# search
docker search nginx
```

## Dockerfile 자주 쓰는 패턴

```dockerfile
# 기본
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

# multi-stage
FROM eclipse-temurin:21 AS builder
COPY . /build
RUN cd /build && ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=builder /build/target/*.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]

# layered jar
FROM eclipse-temurin:21 AS builder
WORKDIR /build
COPY . .
RUN ./gradlew bootJar
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination /build/extracted

FROM gcr.io/distroless/java21-debian12:nonroot
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]

# 비루트
RUN groupadd -r app && useradd -r -g app app
USER app

# HEALTHCHECK
HEALTHCHECK --interval=30s --timeout=3s \
    CMD curl -f http://localhost:8080/actuator/health || exit 1
```

## 자주 쓰는 BuildKit

```dockerfile
# syntax=docker/dockerfile:1.7

# Cache mount
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar

# Secret mount
RUN --mount=type=secret,id=token \
    git clone https://$(cat /run/secrets/token)@github.com/private/repo.git
```

```bash
docker build --secret id=token,src=./token.txt .
```

## 보안

```bash
# Trivy
trivy image myapp:1.0
trivy image --severity HIGH,CRITICAL myapp:1.0
trivy fs .                                      # filesystem

# Hadolint
docker run --rm -i hadolint/hadolint < Dockerfile

# Docker Scout
docker scout cves myapp:1.0
docker scout compare myapp:v1 --to myapp:v2

# Syft (SBOM)
syft myapp:1.0 -o spdx-json > sbom.json
```

## 진단

```bash
# 컨테이너 안 자원
docker stats <id>

# 디스크 사용량
docker system df -v

# 로그 위치
docker inspect <id> --format='{{.LogPath}}'

# 환경 확인
docker exec <id> env | sort
docker exec <id> ps aux
docker exec <id> cat /proc/cpuinfo
docker exec <id> df -h
```

## 자주 쓰는 짧은 명령

```bash
# 종료된 컨테이너 모두 삭제
docker rm $(docker ps -aq)

# 모든 이미지 삭제
docker rmi $(docker images -q)

# 가장 큰 이미지 top 5
docker images --format "{{.Size}}\t{{.Repository}}:{{.Tag}}" | sort -h | tail -5

# 한 줄 (네트워크 + 볼륨 + 이미지 정리)
docker system prune -a --volumes
```
