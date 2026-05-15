# DevOps · 컨테이너 트러블슈팅 플레이북

증상별 진단 절차. 운영서에서 바로 사용하도록.

---

## 원칙

1. **재시작 전 증거 확보** (logs·describe·events)
2. **측정만, 추측 X**
3. **변경 한 번에 하나**
4. **재현 환경에서 먼저**
5. **사후 보고서 작성**

---

## 시나리오 1: Git "잘못 reset --hard"

### 증상
중요한 변경이 사라짐.

### 진단·복구
```bash
git reflog                                    # 모든 HEAD 이동
# 마지막 정상 SHA 찾기
git reset --hard <SHA>
# 또는 브랜치 생성
git branch rescue <SHA>
```

### 예방
- `--force-with-lease` 사용 (`--force` X)
- 큰 작업 전 `git stash` 또는 임시 브랜치

---

## 시나리오 2: 잘못된 force-push로 동료 commit 삭제

### 증상
remote에 있던 commit이 사라짐.

### 복구
```bash
# 동료의 local에서
git reflog show main
git push origin <last good SHA>:main --force-with-lease

# 또는 GitHub의 "Restore branch" (UI)
```

### 예방
- branch protection rules
- `--force-with-lease` 표준화

---

## 시나리오 3: PR이 conflict 폭주

### 증상
PR이 며칠 후 main에 거대한 conflict.

### 진단·해결
```bash
git checkout feature
git fetch origin
git rebase origin/main                        # 충돌 해결하며

# 또는 merge
git merge origin/main

git push --force-with-lease
```

### 예방
- 짧은 PR (< 400줄)
- 작업 시작 시 main rebase
- main에 자주 동기화

---

## 시나리오 4: Docker 빌드가 5분

### 진단
```bash
docker build --progress=plain .
# 각 step 시간 보기
```

### 흔한 원인
- `.dockerignore` 없음 → 큰 context 전송
- COPY 순서 잘못 → 캐시 무효화
- 의존성 다운로드 매번
- BuildKit 미사용

### 조치
```dockerfile
# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21 AS builder
WORKDIR /build

# 의존성 파일 먼저
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Cache mount
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:go-offline -B

# 소스 (자주 바뀜)
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests -B
```

```bash
# BuildKit 활성 (Docker 23+ 기본)
DOCKER_BUILDKIT=1 docker build .
```

---

## 시나리오 5: Image pull 실패

### 증상
```
Failed to pull image "ghcr.io/org/app:v1": denied
```

### 진단
```bash
# 이미지 존재 확인
docker pull ghcr.io/org/app:v1

# k8s
kubectl describe pod <pod> | grep -A 10 Events
```

### 흔한 원인
| 원인 | 조치 |
|---|---|
| Private image + secret 없음 | imagePullSecret 추가 |
| 잘못된 image 이름 | typo 확인 |
| tag 없음 | tag 존재 확인 |
| Rate limit (Docker Hub) | 다른 registry 사용 |

### k8s imagePullSecret

```bash
kubectl create secret docker-registry ghcr \
    --docker-server=ghcr.io \
    --docker-username=<user> \
    --docker-password=<PAT>

# Pod에서
spec:
  imagePullSecrets:
    - name: ghcr
```

---

## 시나리오 6: Pod CrashLoopBackOff

### 진단
```bash
kubectl logs <pod>                            # 현재
kubectl logs --previous <pod>                 # 죽기 전 (중요)
kubectl describe pod <pod>
```

### 흔한 원인

| 원인 | 시그널 | 조치 |
|---|---|---|
| 빠른 종료 | 로그에 stack trace | 코드 수정 |
| 환경변수 누락 | "Required env not set" | ConfigMap·Secret 확인 |
| 의존성 (DB) 못 함 | "Connection refused" | initContainer 또는 retry |
| OOM | describe "OOMKilled" | memory limit↑ 또는 누수 점검 |
| probe 너무 빠름 | "Liveness probe failed" | startupProbe 추가 |

### 예시 — JPA + DB 연결 실패

```yaml
initContainers:
  - name: wait-for-db
    image: busybox
    command: ['sh', '-c', 'until nc -z db 5432; do sleep 2; done']
```

또는 Spring Boot에서 retry:
```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 30000
      initialization-fail-timeout: 60000
```

---

## 시나리오 7: OOMKilled

### 진단
```bash
kubectl describe pod <pod>
# Last State:    Terminated
#   Reason:      OOMKilled
#   Exit Code:   137
```

### 흔한 원인
- JVM heap이 컨테이너 limit 초과
- 누수
- 갑작스런 부하

### 조치
```yaml
resources:
  limits:
    memory: 1Gi

env:
  - name: JAVA_TOOL_OPTIONS
    value: "-XX:MaxRAMPercentage=70 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heap.hprof"
```

heapdump 위해 emptyDir·PVC 마운트 권장.

---

## 시나리오 8: Service가 Pod 못 찾음

### 진단
```bash
kubectl get svc <svc>
kubectl get endpoints <svc>
# Endpoints가 비어있으면 selector 매칭 실패
```

```bash
kubectl get svc <svc> -o yaml | grep selector -A 2
# selector:
#   app: web

kubectl get pods --show-labels
# app=web 라벨 있나?
```

### 조치
selector·label 일치시킴.

---

## 시나리오 9: Ingress가 404

### 진단
```bash
kubectl get ingress <ing>
kubectl describe ingress <ing>
# 백엔드 Service 매핑 확인

# Ingress controller 로그
kubectl logs -n ingress-nginx -l app.kubernetes.io/component=controller
```

### 흔한 원인
- ingressClassName 빠짐
- path·host 매칭 안 됨
- Backend Service 없음
- ingress-nginx 미설치

```yaml
spec:
  ingressClassName: nginx          # 이거 중요
  rules:
    - http:
        paths:
          - path: /api
            pathType: Prefix       # Prefix? Exact?
            backend:
              service:
                name: api-svc       # 존재?
                port: { number: 80 }
```

---

## 시나리오 10: HPA가 동작 안 함

### 진단
```bash
kubectl get hpa
# TARGETS = <unknown>/70%
```

### 원인
- metrics-server 미설치
- Deployment에 resources.requests 없음

```bash
kubectl top pods                              # 동작?
kubectl get deploy <name> -o yaml | grep -A 3 resources
```

### 조치
```bash
# metrics-server 설치
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Docker Desktop이면 추가 patch 필요 (--kubelet-insecure-tls)
```

---

## 시나리오 11: ArgoCD OutOfSync

### 증상
git과 클러스터 상태 다름.

### 진단
```bash
argocd app diff my-app
# 또는 UI의 Diff
```

### 흔한 원인
- 누가 수동 변경 (drift)
- ConfigMap의 generated value
- annotation 자동 추가 (Helm 등)

### 조치
- 의도된 변경 → 코드 업데이트
- 의도 X → `argocd app sync`로 되돌림
- self-heal 켜서 자동 처리

### 일시적으로 sync 끄기
```yaml
syncPolicy: {}    # 또는 자동 X
```

---

## 시나리오 12: GitHub Actions 빌드 시간 30분

### 진단
- 단일 job, 순차 실행?
- 캐시 사용?
- matrix 너무 큰가?

### 조치
```yaml
- uses: actions/cache@v4
  with:
    path: ~/.gradle/caches
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}

# concurrency로 중복 cancel
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

병렬 job + 캐시로 보통 5분 미만.

---

## 시나리오 13: Terraform "state lock"

### 증상
```
Error: Error acquiring the state lock
```

### 진단
누가 작업 중인지 확인 (DynamoDB·Cloud UI).

### 조치
```bash
# 진짜 다른 사람이 작업 중이면 기다림

# stale lock이면 (확실할 때만)
terraform force-unlock <lock-id>
```

### 예방
- 짧은 apply
- `-target`로 부분만 적용
- 동시 작업 협의

---

## 시나리오 14: Terraform drift

### 증상
```bash
terraform plan
# # aws_instance.web has been changed externally
```

수동으로 콘솔에서 변경됨.

### 조치
- 의도 → 코드 업데이트
- 사고 → apply로 원복

### 예방
- IAM으로 콘솔 변경 차단
- 정기적 `terraform plan` (cron 또는 Atlantis)

---

## 시나리오 15: Secret이 git에

### 응급 처치 (즉시)

```bash
# 1. 즉시 secret 폐기·재발급 (AWS key·API token 등)
# 2. git history에서 제거
pip install git-filter-repo
git filter-repo --path secrets.env --invert-paths --force

# 3. force push (모든 협업자에게 알림)
git push --force-with-lease --all
git push --force-with-lease --tags

# 4. 협업자들 fresh clone
```

### 예방
- pre-commit hook (gitleaks)
- `.gitignore`에 `.env*`
- GitHub의 Push Protection
- BFG·git-filter-repo 자동 검사

---

## 자기 노트

본인 환경에서 마주친 사건을 같은 형식으로 추가:

```markdown
### 2026-MM-DD — 사고 제목

- **증상**:
- **진단 단계**:
- **원인**:
- **조치**:
- **재발 방지**:
- **학습**:
```
