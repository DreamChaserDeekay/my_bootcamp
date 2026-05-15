# Day 1 — 컨테이너 기초

## 한 줄 요약

컨테이너는 VM이 아니라 **호스트 커널을 공유하는 격리된 프로세스**. Linux의 **namespace**(보이는 것 격리)와 **cgroup**(자원 사용 제한)이 핵심. 이미지는 **계층(layer)**으로 구성된 파일시스템 스냅샷.

## 학습 목표

- [ ] 컨테이너 vs VM 차이를 설명
- [ ] 7가지 namespace 종류
- [ ] cgroup이 무엇을 제한하는가
- [ ] 이미지의 layer 구조와 union FS
- [ ] `docker run`이 일어나는 과정
- [ ] Windows에서 Docker가 동작하는 방식 (WSL2/Hyper-V)

---

## 컨테이너 vs VM

```
   VM                              컨테이너
   ┌──────────┐ ┌──────────┐      ┌────────┐ ┌────────┐ ┌────────┐
   │ App A    │ │ App B    │      │ App A  │ │ App B  │ │ App C  │
   │ Bin/Libs │ │ Bin/Libs │      │Bin/Libs│ │Bin/Libs│ │Bin/Libs│
   │ Guest OS │ │ Guest OS │      └────────┘ └────────┘ └────────┘
   ├──────────┴────────────┤      ┌────────────────────────────┐
   │      Hypervisor       │      │      Container Runtime      │
   ├───────────────────────┤      ├────────────────────────────┤
   │      Host OS          │      │      Host OS (kernel)       │
   ├───────────────────────┤      ├────────────────────────────┤
   │      Hardware         │      │      Hardware              │
   └───────────────────────┘      └────────────────────────────┘
```

| 항목 | VM | 컨테이너 |
|---|---|---|
| OS | Guest OS 별도 | Host OS 공유 |
| 크기 | GB | MB |
| 시작 | 분 | 초 (밀리초) |
| 격리 | 강함 | 보통 |
| 오버헤드 | 큼 | 거의 없음 |

> **컨테이너는 가벼운 격리. VM은 강력한 격리**. 보안 민감하면 VM, 빠른 배포·자원 효율은 컨테이너.

---

## 컨테이너의 정체 — namespace + cgroup

### Namespace — "보이는 것의 격리"

Linux의 namespace는 **프로세스마다 다른 view**를 줌:

| Namespace | 무엇을 격리? |
|---|---|
| **PID** | 프로세스 ID — 컨테이너 안에선 PID 1부터 |
| **NET** | 네트워크 — 별도 인터페이스·IP |
| **MOUNT** | 파일시스템 마운트 |
| **UTS** | hostname·domain |
| **IPC** | inter-process communication |
| **USER** | UID·GID 매핑 |
| **CGROUP** | cgroup 계층 |
| **TIME** (5.6+) | 시간 |

직접 확인:

```bash
# 호스트
ps aux | head -5
# 모든 프로세스 보임

# 컨테이너 안
docker run -it ubuntu bash
ps aux
# PID 1 = bash, 그 외 거의 없음 (PID namespace 격리)
```

### cgroup — "자원 사용 제한"

CPU·메모리·I/O·네트워크 사용량 제한.

```bash
docker run -it --memory 256m --cpus 0.5 ubuntu bash
# 메모리 256MB, CPU 0.5 코어
```

container 내부:
```bash
# v2 (cgroup v2)
cat /sys/fs/cgroup/memory.max     # 256M
cat /sys/fs/cgroup/cpu.max         # 50000 100000 (50%)
```

OOM 가능 — 메모리 초과 시 컨테이너 안의 프로세스만 죽음 (호스트는 OK).

---

## 이미지 — 계층(layer)

```
my-app:latest 이미지
       │
       │ 위에서 아래로 쌓임
       ▼
   ┌─────────────────────────────────────┐
   │ Layer 5: CMD ["java", "-jar", ...]  │  ← 메타데이터만
   ├─────────────────────────────────────┤
   │ Layer 4: COPY app.jar /             │  ← 50MB
   ├─────────────────────────────────────┤
   │ Layer 3: RUN apt install fontconfig │  ← 10MB
   ├─────────────────────────────────────┤
   │ Layer 2: ENV ...                    │  ← 메타데이터만
   ├─────────────────────────────────────┤
   │ Layer 1: FROM eclipse-temurin:21    │  ← 200MB
   └─────────────────────────────────────┘
```

각 Dockerfile 명령 = layer 1개. **Union FS**(overlay2)로 합쳐 보임.

```bash
docker history my-app:latest
# 각 layer의 크기·명령 표시
```

### 캐시

같은 명령 + 같은 input = 같은 layer SHA → 재사용. **빌드 가속**의 핵심.

```dockerfile
# 1. 의존성 (잘 안 바뀜) - 위에 두면 캐시 잘 됨
COPY pom.xml .
RUN mvn dependency:resolve

# 2. 소스 (자주 바뀜) - 아래
COPY src/ src/
RUN mvn package
```

소스만 바꿔도 1번 layer는 캐시. **순서 중요**.

### 이미지 공유 — registry

```bash
docker pull eclipse-temurin:21-jre
# 1. Docker Hub에서 manifest 받기
# 2. 각 layer 받기 (이미 있으면 skip)
# 3. 합쳐서 이미지 만들기

docker image ls
# REPOSITORY        TAG    SIZE
# eclipse-temurin   21-jre  290MB
```

### 다이제스트 (digest)

```bash
docker pull eclipse-temurin:21-jre
docker images --digests
# eclipse-temurin   21-jre   sha256:abc123...   290MB
```

`tag`는 mutable (덮어쓸 수 있음). **운영은 digest로 pin** 권장:

```dockerfile
FROM eclipse-temurin:21-jre@sha256:abc123...
```

---

## `docker run` 흐름

```
$ docker run -d -p 8080:8080 my-app:latest
   │
   ▼
1. Docker daemon이 요청 받음
   │
   ▼
2. 이미지 my-app:latest 확인
   - 로컬에 없으면 docker pull
   │
   ▼
3. 컨테이너 만들기
   - rootfs (이미지 layer 합쳐서)
   - 새 namespace 생성 (PID/NET/MOUNT/...)
   - cgroup 등록
   │
   ▼
4. 네트워크 설정
   - bridge network 연결
   - 포트 8080 매핑 (host:container)
   │
   ▼
5. 컨테이너 안에서 CMD 실행
   - PID 1로 java 프로세스
```

```bash
# 동작 중 컨테이너 안 들여다보기
docker ps
docker exec -it <id> bash
# 안에서 ps, ls, env 등
```

---

## Windows의 Docker — WSL2

```
   Windows                           Linux 컨테이너
   ┌─────────────────────────────┐   ┌──────────────┐
   │ Docker Desktop (UI)         │──▶│ ubuntu       │
   │                             │   │ /redis/...   │
   │ ┌─────────────────────────┐ │   └──────────────┘
   │ │  WSL2 (Linux VM)        │ │           ▲
   │ │  - Docker Engine        │ │           │
   │ │  - containerd           │ │           │
   │ └─────────────────────────┘ │   ┌──────────────┐
   │                             │   │  Docker      │
   │ Hyper-V                     │──▶│  containers  │
   └─────────────────────────────┘   └──────────────┘
```

- Docker Desktop이 WSL2 안에서 Linux Docker Engine 실행
- 사용자는 PowerShell·CMD에서 `docker` 명령
- 사실은 WSL2 → Linux Docker

> **WSL2 활성화 권장**. Hyper-V 백엔드는 느리고 IO 약함.

---

## 흔한 명령어

```bash
# 이미지
docker pull <image>
docker images
docker image inspect <image>
docker rmi <image>
docker history <image>

# 컨테이너
docker run [OPTIONS] <image>
docker ps                    # 실행 중
docker ps -a                 # 종료된 것도
docker stop <id>
docker rm <id>
docker logs -f <id>          # 로그 따라가기
docker exec -it <id> bash    # 안으로 들어가기

# 정리
docker system prune          # 안 쓰는 컨테이너·네트워크
docker system prune -a       # 안 쓰는 이미지까지
docker system df             # 디스크 사용량
```

### `docker run` 자주 쓰는 옵션

```bash
docker run \
  -d \                       # detached (백그라운드)
  --name myapp \             # 이름
  -p 8080:8080 \             # 포트 매핑 host:container
  -v $(pwd)/data:/data \     # 볼륨 마운트
  -e DB_URL=jdbc:... \       # 환경변수
  --memory 1g \              # 메모리 제한
  --cpus 1.0 \               # CPU 제한
  --restart unless-stopped \ # 재시작 정책
  --network mynet \          # 네트워크
  myimage:tag
```

---

## 운영 사례

### 사례 1 — "내 컴퓨터에선 됐어요"

같은 이미지 = 같은 binary. **컨테이너 채택의 가장 큰 이유**. 환경 차이는 ENV·volume mount로만.

### 사례 2 — `docker ps`에 없음 (이미 종료)

```bash
docker ps -a                 # 종료된 것도
docker logs <id>             # 왜 죽었나
```

흔한 원인:
- ENTRYPOINT가 즉시 종료 (foreground 프로세스 아님)
- 환경변수 누락
- 권한 문제

### 사례 3 — Disk full

```bash
docker system df
# Build cache가 100GB
docker builder prune
# 또는
docker system prune -a --volumes
```

---

## 실습 (Hands-on)

### 1단계 — Hello container

```powershell
docker run hello-world
# 잘 받아지고 메시지 나와야 함
```

### 2단계 — Ubuntu 컨테이너 들여다보기

```powershell
docker run -it ubuntu bash
# 안에서:
ps aux                       # PID 1이 bash
ls /                          # 전체 filesystem
cat /etc/os-release           # ubuntu 정보
hostname                      # 컨테이너 hostname
exit
```

### 3단계 — namespace 격리 확인

```powershell
# 호스트 (PowerShell)
docker run -d --name web nginx
docker ps

# 컨테이너 안
docker exec -it web sh
# 안에서: ps aux → nginx만 보임 (다른 프로세스 X)
exit

# 정리
docker stop web
docker rm web
```

### 4단계 — cgroup 제한 확인

```powershell
docker run -it --memory 100m ubuntu bash
# 안에서
free -h
# 시스템 메모리는 호스트값이지만
cat /sys/fs/cgroup/memory.max
# 100M 제한 확인

# 메모리 폭주 시도
yes | head -c 200M > /tmp/big.txt   # OK
# 200MB도 가능 (디스크에 쓰므로)
# 메모리에 쌓는 작업이라면 OOM
```

### 5단계 — layer 캐시 확인

```powershell
mkdir cache-demo
cd cache-demo

# Dockerfile
@"
FROM alpine:3.20
RUN apk add --no-cache curl
RUN apk add --no-cache jq
RUN echo "static content" > /info.txt
"@ | Out-File Dockerfile

docker build -t cache-test:v1 .
# 모두 처음 빌드

docker build -t cache-test:v2 .
# 모두 캐시 — 매우 빠름

# Dockerfile의 두 번째 줄 수정 (RUN apk add curl → wget)
# 이후 빌드 시 그 줄부터 다시
```

### 6단계 — 이미지 히스토리

```powershell
docker history nginx
# 각 layer 명령·크기

docker image inspect nginx | jq '.[0].RootFS.Layers'
# layer SHA 목록
```

---

## 더 읽어볼 자료

- 📘 『Docker Deep Dive』 (Nigel Poulton) — 입문~중급
- 📘 『Docker in Action』 2nd (Jeff Nickoloff)
- 🔗 [Docker Docs — Overview](https://docs.docker.com/get-started/overview/)
- 🔗 [What even is a container](https://jvns.ca/blog/2016/10/10/what-even-is-a-container/) — Julia Evans
- 🔗 [Linux Namespaces 시리즈](https://lwn.net/Articles/531114/) — LWN
- 🎓 [Liz Rice — A Container from Scratch](https://www.youtube.com/watch?v=8fi7uSYlOdc) — 컨테이너를 Go로 직접 구현
