# Quick Reference — 한 페이지 카드

## 진단 시작 3종

```bash
# git
git status
git log --oneline -10
git reflog

# Docker
docker ps -a
docker logs <id>
docker stats

# k8s
kubectl get pods -A
kubectl describe pod <pod>
kubectl logs -f <pod>
```

---

## 자주 쓰는 패턴

### Spring Boot Docker (운영급)

```dockerfile
# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21 AS builder
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination /build/extracted

FROM gcr.io/distroless/java21-debian12:nonroot
COPY --from=builder /build/extracted/dependencies/         ./
COPY --from=builder /build/extracted/spring-boot-loader/   ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/          ./
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
```

### k8s Spring Boot Deployment (운영급)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: web }
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate: { maxSurge: 1, maxUnavailable: 0 }
  selector: { matchLabels: { app: web } }
  template:
    metadata: { labels: { app: web } }
    spec:
      securityContext: { runAsNonRoot: true }
      containers:
        - name: app
          image: ghcr.io/org/app:v1.0.0
          ports: [{ containerPort: 8080 }]
          envFrom:
            - configMapRef: { name: app-config }
            - secretRef:    { name: app-secret }
          resources:
            requests: { cpu: 200m, memory: 384Mi }
            limits:   { cpu: 1000m, memory: 512Mi }
          securityContext:
            allowPrivilegeEscalation: false
            capabilities: { drop: [ALL] }
            readOnlyRootFilesystem: true
          startupProbe:
            httpGet: { path: /actuator/health/liveness, port: 8080 }
            failureThreshold: 30
            periodSeconds: 5
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: 8080 }
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: 8080 }
```

### GitHub Actions 최소 풀파이프라인

```yaml
name: CI
on: { push: { branches: [main] }, pull_request: }
permissions: { contents: read, packages: write, security-events: write }
env: { IMAGE: ghcr.io/${{ github.repository }} }
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: actions/cache@v4
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
      - run: ./gradlew test
  build:
    needs: test
    if: github.event_name != 'pull_request'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - id: tag
        run: echo "sha=$(git rev-parse --short HEAD)" >> $GITHUB_OUTPUT
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with: { registry: ghcr.io, username: ${{ github.actor }}, password: ${{ secrets.GITHUB_TOKEN }} }
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            ${{ env.IMAGE }}:${{ steps.tag.outputs.sha }}
            ${{ env.IMAGE }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max
      - uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ env.IMAGE }}:${{ steps.tag.outputs.sha }}
          severity: HIGH,CRITICAL
```

---

## 응급 처치

### git 사고

```bash
git reflog                                  # 모든 HEAD 이동
git reset --hard HEAD@{1}                   # N번 전으로

# 잘못된 브랜치 commit
git cherry-pick <SHA>                       # 옳은 브랜치에
git reset --hard HEAD~1                     # 잘못된 브랜치에서 제거
```

### Docker 디스크 가득

```bash
docker system df                            # 확인
docker system prune -a --volumes            # 정리
docker builder prune                        # 빌드 캐시
```

### k8s Pod 못 시작

```bash
# 1. 상태
kubectl get pod <pod> -o wide

# 2. Events (가장 중요)
kubectl describe pod <pod> | grep -A 30 Events

# 3. 로그
kubectl logs <pod>
kubectl logs --previous <pod>               # 죽기 전

# 4. 흔한 상태별
ImagePullBackOff:   이미지·태그·secret 확인
CrashLoopBackOff:   logs --previous
OOMKilled:          memory limit 점검
Pending:            노드 자원·nodeSelector
```

### k8s rollback

```bash
kubectl rollout history deploy/web
kubectl rollout undo deploy/web
# 또는 Helm
helm rollback my-release 1
# 또는 ArgoCD UI History → Rollback
```

### 운영 Pod 디버깅

```bash
# 로그 follow
kubectl logs -f -l app=web --max-log-requests=10

# 들어가서
kubectl exec -it <pod> -- sh

# distroless면
kubectl debug -it <pod> --image=busybox --target=app

# port-forward
kubectl port-forward svc/web 8080:80
```

---

## 자주 까먹는 것

### `.gitignore` 표준 (Java)

```
target/
build/
*.iml
.idea/
.gradle/
out/
.env*
*.log
```

### `.dockerignore` 표준

```
.git/
.gitignore
.idea/
.vscode/
target/
build/
.gradle/
.env*
*.log
README.md
.github/
k8s/
```

### Spring Boot health endpoint

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true                       # /health/liveness, /health/readiness
  health:
    livenessstate:    { enabled: true }
    readinessstate:   { enabled: true }
```

### Docker run으로 디버그

```bash
docker run --rm -it --entrypoint sh <image>
# distroless는 sh 없음
docker run --rm -it --entrypoint /bin/busybox <image>
```

---

## 운영 옵션 세트

### JVM in container

```bash
JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/tmp/heap.hprof \
  -Xlog:gc*:stdout"
```

### k8s graceful shutdown

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

```yaml
# k8s manifest
lifecycle:
  preStop:
    exec:
      command: ["sleep", "10"]
terminationGracePeriodSeconds: 60
```

---

## 운영 단축 명령

```bash
# 빠른 k8s 자원 확인
alias k=kubectl
alias kg='kubectl get'
alias kgp='kubectl get pods'
alias kgpa='kubectl get pods -A'
alias kd='kubectl describe'
alias kl='kubectl logs -f'

# 빠른 git
alias g=git
alias gs='git status'
alias gl='git log --oneline --graph'
alias gd='git diff'
```

---

## 면접 단골 질문 10개

1. CI와 CD 차이?
2. Docker 컨테이너 vs VM?
3. Dockerfile multi-stage build의 목적?
4. k8s의 Pod와 Deployment의 차이?
5. Service 타입 4가지와 사용처?
6. liveness vs readiness probe?
7. Rolling vs Blue-Green vs Canary 배포?
8. GitOps와 ArgoCD?
9. Terraform state를 remote backend로 옮기는 이유?
10. Spring Boot 앱을 k8s에 배포 시 권장 옵션?
