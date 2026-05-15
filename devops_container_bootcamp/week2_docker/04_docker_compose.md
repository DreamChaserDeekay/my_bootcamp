# Day 4 — Docker Compose

## 한 줄 요약

`docker-compose.yml` 하나로 **여러 서비스를 한 번에** 띄움. 로컬 개발에 필수 — DB·Redis·Kafka·앱을 한 명령으로. 운영엔 k8s지만 dev/staging엔 여전히 유용.

## 학습 목표

- [ ] compose 파일 구조
- [ ] 서비스 의존성 (depends_on, healthcheck)
- [ ] 네트워크·볼륨
- [ ] env file과 secrets
- [ ] profile로 환경별 차이
- [ ] override 파일

---

## compose 기본

### 한 파일에 모두

`docker-compose.yml`:

```yaml
services:
  app:
    image: my-app:latest
    build: .                              # Dockerfile로 빌드
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_URL: jdbc:postgresql://db:5432/labdb
      DB_USER: app
      DB_PASSWORD: app_pwd
    depends_on:
      db:
        condition: service_healthy
    networks:
      - backend

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: labdb
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app_pwd
    volumes:
      - db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app"]
      interval: 5s
      timeout: 3s
      retries: 5
    networks:
      - backend

  redis:
    image: redis:7
    ports:
      - "6379:6379"
    networks:
      - backend

networks:
  backend:

volumes:
  db-data:
```

### 명령

```bash
# 모두 시작
docker compose up -d                     # 백그라운드

# 로그
docker compose logs -f                   # 모두
docker compose logs -f app               # 특정 서비스

# 상태
docker compose ps

# 한 서비스만
docker compose restart app
docker compose stop redis

# 정리
docker compose down                      # 컨테이너·네트워크 제거
docker compose down -v                   # 볼륨까지
```

> 옛 `docker-compose` (하이픈) v1은 deprecate. **`docker compose`** (공백) v2 권장. Docker Desktop 자동 포함.

---

## 서비스 의존성

### depends_on (간단)

```yaml
services:
  app:
    depends_on:
      - db
      - redis
```

→ db, redis가 **시작**되면 app 시작. 그러나 **준비 완료**는 보장 X.

### depends_on with condition (권장)

```yaml
services:
  app:
    depends_on:
      db:
        condition: service_healthy        # db의 healthcheck 통과 후
      redis:
        condition: service_started        # redis가 시작 후
```

healthcheck로 진짜 준비 상태 확인.

### healthcheck

```yaml
db:
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U app"]
    interval: 5s
    timeout: 3s
    retries: 5
    start_period: 10s                     # 시작 후 N초간 실패 무시
```

### 앱 자체에 retry

container 시작 순서가 보장돼도 **앱이 DB 연결 재시도**해야 견고. Spring Boot의 경우:

```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 30000
      initialization-fail-timeout: 60000
```

---

## 네트워크

### 자동 네트워크

compose가 자동으로 네트워크 생성:

```yaml
services:
  app: ...
  db: ...
# 자동: {project}_default 네트워크 생성, 두 서비스 같이
```

서비스 이름이 hostname:

```bash
# app 컨테이너 안에서
ping db                                   # 가능
# 또는 jdbc:postgresql://db:5432/...
```

### 명시적 네트워크

```yaml
networks:
  frontend:
  backend:

services:
  app:
    networks: [backend, frontend]
  db:
    networks: [backend]                   # frontend엔 없음
  web:
    networks: [frontend]                  # backend 못 봄
```

서비스 격리.

---

## 볼륨

### 명명 볼륨 (named volume)

```yaml
volumes:
  db-data:

services:
  db:
    volumes:
      - db-data:/var/lib/postgresql/data
```

Docker가 관리. `docker volume ls`로 보임. compose down -v 전엔 유지.

### bind mount

```yaml
services:
  app:
    volumes:
      - ./logs:/var/log/app               # host 경로 : container 경로
      - ./config:/etc/myapp:ro            # read-only
```

호스트의 특정 디렉토리를 컨테이너에. 로컬 개발에서 코드 mount 시 자주 사용.

### tmpfs

```yaml
services:
  app:
    tmpfs:
      - /tmp                              # 메모리 기반 (휘발)
```

---

## 환경변수

### 직접

```yaml
services:
  app:
    environment:
      DB_URL: jdbc:postgresql://db:5432/labdb
      DB_USER: app
```

### `.env` 파일

```bash
# .env
DB_PASSWORD=secret
APP_VERSION=1.2.3
```

```yaml
services:
  app:
    environment:
      DB_PASSWORD: ${DB_PASSWORD}        # .env에서 가져옴
    image: my-app:${APP_VERSION}
```

> `.env`를 `.gitignore`에. `.env.example`만 commit.

### env_file

```yaml
services:
  app:
    env_file:
      - common.env
      - app-specific.env
```

---

## profile — 선택적 서비스

```yaml
services:
  app: { ... }
  db: { ... }
  
  # debug 도구 — 보통 안 띄움
  adminer:
    image: adminer
    ports: ["8081:8080"]
    profiles: ["debug"]
    
  # k6 부하 테스트 — 명시적으로만
  loadtest:
    image: grafana/k6
    profiles: ["test"]
```

```bash
# 기본은 profile 없는 것만
docker compose up

# debug 프로파일도
docker compose --profile debug up

# 또는 환경변수
COMPOSE_PROFILES=debug,test docker compose up
```

---

## override 파일 — 환경별 차이

`docker-compose.yml`:
```yaml
services:
  app:
    image: my-app:latest
    environment:
      LOG_LEVEL: INFO
```

`docker-compose.override.yml` (개발용, 자동 적용):
```yaml
services:
  app:
    build: .                              # 로컬 빌드
    environment:
      LOG_LEVEL: DEBUG
    volumes:
      - ./src:/app/src                    # 코드 hot reload
```

`docker-compose.prod.yml` (운영):
```yaml
services:
  app:
    image: ghcr.io/example/my-app:1.2.3
    deploy:
      replicas: 3
      restart_policy:
        condition: on-failure
```

```bash
# dev (override 자동 적용)
docker compose up

# prod (명시적)
docker compose -f docker-compose.yml -f docker-compose.prod.yml up
```

---

## 자주 쓰는 패턴

### Spring Boot + PostgreSQL + Redis

```yaml
services:
  app:
    build: .
    ports: ["8080:8080"]
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/labdb
      SPRING_DATASOURCE_USERNAME: app
      SPRING_DATASOURCE_PASSWORD: app_pwd
      SPRING_DATA_REDIS_HOST: redis
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_started

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: labdb
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app_pwd
    volumes:
      - db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app"]
      interval: 5s

  redis:
    image: redis:7

  adminer:
    image: adminer
    ports: ["8081:8080"]
    profiles: ["debug"]

volumes:
  db-data:
```

### Kafka 클러스터 (1 broker, dev용)

```yaml
services:
  kafka:
    image: confluentinc/cp-kafka:7.7.0
    ports: ["9092:9092"]
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_LISTENERS: 'PLAINTEXT://:9092,CONTROLLER://:9093'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka:9092'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka:9093'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'

  kafdrop:
    image: obsidiandynamics/kafdrop:latest
    ports: ["9000:9000"]
    environment:
      KAFKA_BROKERCONNECT: "kafka:9092"
    depends_on: [kafka]
    profiles: ["debug"]
```

---

## 운영 사례

### 사례 1 — "DB 시작 전에 앱이 죽음"

`depends_on`만으로는 부족 — 시작 순서만 보장, 준비 X.

```yaml
db:
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U app"]
    interval: 5s
    retries: 10

app:
  depends_on:
    db:
      condition: service_healthy
```

### 사례 2 — 볼륨 데이터 사라짐

```bash
docker compose down -v
```

`-v`로 볼륨까지 삭제. 운영서에선 절대 피하기. dev에선 reset 용도로.

### 사례 3 — 다중 인스턴스 (load test)

```yaml
services:
  app:
    deploy:
      replicas: 3
```

`docker compose up` 시 같은 서비스 3개. 그러나 로컬 dev엔 거의 안 씀 — k8s 영역.

---

## 실습 (Hands-on)

### 1단계 — Hello compose

```yaml
# docker-compose.yml
services:
  web:
    image: nginx:alpine
    ports: ["80:80"]
    volumes:
      - ./html:/usr/share/nginx/html:ro

  redis:
    image: redis:7
```

```bash
mkdir html
echo "<h1>Hello compose</h1>" > html/index.html

docker compose up -d
curl http://localhost
# <h1>Hello compose</h1>

docker compose ps
docker compose down
```

### 2단계 — Spring Boot + Postgres

위 표준 패턴으로 compose. 다음 lab(lab3)에서 자세히.

### 3단계 — Profile

```yaml
services:
  app: { ... }
  db: { ... }
  adminer:
    image: adminer
    profiles: [debug]
```

```bash
docker compose up                         # app, db만
docker compose --profile debug up         # adminer까지
```

### 4단계 — Override 실험

`docker-compose.yml`:
```yaml
services:
  echo:
    image: hashicorp/http-echo
    command: ["-text", "production"]
```

`docker-compose.override.yml`:
```yaml
services:
  echo:
    command: ["-text", "development"]
```

```bash
docker compose up
# development 출력 (override 자동 적용)

docker compose -f docker-compose.yml up
# production
```

---

## 더 읽어볼 자료

- 🔗 [Compose 공식](https://docs.docker.com/compose/)
- 🔗 [Compose file reference](https://docs.docker.com/compose/compose-file/)
- 🔗 [Awesome Compose](https://github.com/docker/awesome-compose) — 예제 모음
- 🎓 Bret Fisher — "Docker Mastery" 코스
