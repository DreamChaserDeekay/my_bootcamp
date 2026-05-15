# Day 5 — 캡스톤: End-to-End 파이프라인

## 목표

이 부트캠프의 모든 것을 합쳐 Spring Boot 앱을 **소스 push → 자동 빌드 → 이미지 push → k8s 배포**까지.

---

## 시나리오

```
   Developer ──▶ GitHub repo
                    │
                    │ push to main
                    ▼
   GitHub Actions:
     ┌─────────────────────────────────────────────────┐
     │ 1. Test (./gradlew test)                        │
     │ 2. Build JAR                                    │
     │ 3. Build & push Docker image to GHCR           │
     │ 4. Trivy 보안 스캔                              │
     │ 5. Manifest repo의 image tag 업데이트            │
     │    (또는 직접 helm upgrade)                      │
     └─────────────────────────────────────────────────┘
                    │
                    ▼
   ArgoCD (또는 Helm)
     ┌─────────────────────────────────────────────────┐
     │ k8s 클러스터에 sync                               │
     │  - Deployment 업데이트                           │
     │  - Service / Ingress / ConfigMap / Secret        │
     │  - HPA, Probes                                   │
     └─────────────────────────────────────────────────┘
```

---

## Phase 1 — 사전 준비

### A. 두 개의 GitHub repo

1. **app-repo** (`my-app`) — 소스 코드 + Dockerfile + CI workflow
2. **manifests-repo** (`my-app-manifests`) — k8s YAML + Helm chart (ArgoCD가 봄)

또는 둘을 한 repo의 디렉토리로:
```
my-app/
├── src/
├── Dockerfile
├── .github/workflows/
└── deploy/
    └── helm/
```

이 캡스톤은 분리 가정 (실제 운영 패턴).

### B. GHCR 인증

GitHub Settings → Developer settings → Personal access tokens:
- `read:packages`, `write:packages` 권한
- 또는 GitHub Actions의 `GITHUB_TOKEN` (자동, 무료)

### C. k8s 클러스터

Docker Desktop의 k8s + ArgoCD 설치 (Day 4).

---

## Phase 2 — app-repo

### 디렉토리

```
my-app/
├── build.gradle
├── gradle/wrapper/
├── gradlew
├── Dockerfile
├── src/
│   └── main/
│       └── java/com/example/App.java
└── .github/
    └── workflows/
        └── ci.yml
```

### Dockerfile (Week 2)

```dockerfile
# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21 AS builder
WORKDIR /build
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination /build/extracted

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=builder /build/extracted/dependencies/         ./
COPY --from=builder /build/extracted/spring-boot-loader/   ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/          ./
EXPOSE 8080
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
```

### CI workflow

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read
  packages: write
  security-events: write

env:
  IMAGE: ghcr.io/${{ github.repository }}

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

jobs:

  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: actions/cache@v4
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
      - run: ./gradlew test
      - if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: build/reports/tests/
      
      - name: Hadolint
        uses: hadolint/hadolint-action@v3
        with:
          dockerfile: Dockerfile
          no-fail: true

  build-and-push:
    needs: test
    if: github.event_name != 'pull_request'
    runs-on: ubuntu-latest
    outputs:
      sha: ${{ steps.tag.outputs.sha }}
    steps:
      - uses: actions/checkout@v4
      
      - id: tag
        run: echo "sha=$(git rev-parse --short HEAD)" >> $GITHUB_OUTPUT
      
      - uses: docker/setup-buildx-action@v3
      
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            ${{ env.IMAGE }}:${{ steps.tag.outputs.sha }}
            ${{ env.IMAGE }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max
      
      - name: Trivy scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ env.IMAGE }}:${{ steps.tag.outputs.sha }}
          severity: HIGH,CRITICAL
          exit-code: '0'                  # 캡스톤은 fail 안 함
          format: sarif
          output: trivy.sarif
      
      - if: always()
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: trivy.sarif

  update-manifests:
    needs: build-and-push
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - name: Checkout manifests repo
        uses: actions/checkout@v4
        with:
          repository: ${{ github.repository_owner }}/my-app-manifests
          token: ${{ secrets.MANIFESTS_PAT }}      # 별도 PAT
      
      - name: Update image tag
        run: |
          sed -i "s|tag:.*|tag: \"${{ needs.build-and-push.outputs.sha }}\"|" \
              helm/values-dev.yaml
      
      - name: Commit·push
        run: |
          git config user.name "github-actions"
          git config user.email "actions@github.com"
          git add helm/values-dev.yaml
          git commit -m "ci: deploy ${{ needs.build-and-push.outputs.sha }} to dev"
          git push
```

---

## Phase 3 — manifests-repo

### 디렉토리

```
my-app-manifests/
├── helm/
│   ├── Chart.yaml
│   ├── values.yaml             # 공통
│   ├── values-dev.yaml         # 환경별
│   ├── values-prod.yaml
│   └── templates/
│       ├── deployment.yaml
│       ├── service.yaml
│       ├── ingress.yaml
│       ├── configmap.yaml
│       ├── secret.yaml
│       ├── hpa.yaml
│       └── _helpers.tpl
└── argocd/
    ├── app-dev.yaml
    └── app-prod.yaml
```

Helm chart는 Week 3 Lab 6의 결과 활용.

### values-dev.yaml

```yaml
replicaCount: 2
image:
  repository: ghcr.io/<your-org>/my-app
  tag: "abc1234"                  # CI가 자동 업데이트
ingress:
  enabled: true
  host: my-app.dev.local
config:
  SPRING_PROFILES_ACTIVE: dev
  LOG_LEVEL: DEBUG
resources:
  requests: { cpu: 100m, memory: 256Mi }
  limits:   { cpu: 500m, memory: 384Mi }
autoscaling:
  enabled: false
```

### values-prod.yaml

```yaml
replicaCount: 3
image:
  repository: ghcr.io/<your-org>/my-app
  tag: "v1.0.0"                   # tag로 promote
ingress:
  enabled: true
  host: my-app.example.com
  tls:
    enabled: true
config:
  SPRING_PROFILES_ACTIVE: prod
  LOG_LEVEL: INFO
resources:
  requests: { cpu: 500m, memory: 512Mi }
  limits:   { cpu: 2000m, memory: 1Gi }
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
```

### ArgoCD Application

`argocd/app-dev.yaml`:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: my-app-dev
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/<your-org>/my-app-manifests
    targetRevision: main
    path: helm
    helm:
      valueFiles:
        - values.yaml
        - values-dev.yaml
  destination:
    server: https://kubernetes.default.svc
    namespace: dev
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

`argocd/app-prod.yaml`:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: my-app-prod
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/<your-org>/my-app-manifests
    targetRevision: main
    path: helm
    helm:
      valueFiles:
        - values.yaml
        - values-prod.yaml
  destination:
    server: https://kubernetes.default.svc
    namespace: prod
  syncPolicy:
    # prod는 수동 sync (자동 X)
    syncOptions:
      - CreateNamespace=true
```

```bash
kubectl apply -f argocd/
```

---

## Phase 4 — 흐름 실행

### 1. app-repo에 작은 변경

```java
// Greeting 수정
@GetMapping("/")
public String hello() {
    return "Hello v2 from " + System.getenv().getOrDefault("HOSTNAME", "host");
}
```

```bash
git add . && git commit -m "feat: update greeting"
git push origin main
```

### 2. CI 관찰

GitHub Actions 탭:
- test → 통과
- build-and-push → image ghcr.io/.../my-app:abc1234 push
- Trivy → 결과 GitHub Security 탭에
- update-manifests → manifests-repo에 commit (values-dev.yaml의 tag)

### 3. ArgoCD 관찰

ArgoCD UI:
- my-app-dev가 "OutOfSync"
- 자동 sync → "Synced" + "Healthy"
- k8s에 새 Pod 시작 (rolling update)

```bash
kubectl get pods -n dev -w
kubectl logs -n dev -l app.kubernetes.io/name=my-app
curl http://my-app.dev.local
# Hello v2 from my-app-xxxx
```

### 4. Prod로 promote

manifests-repo의 `values-prod.yaml`을 tag로:
```yaml
image:
  tag: "abc1234"      # dev에서 검증 끝난 SHA
```

또는 git tag로:
```bash
cd app-repo
git tag v1.0.1
git push origin v1.0.1
# CI가 이 tag로 image 생성 (옵션)
```

manifests에 prod tag 업데이트 → ArgoCD UI에서 prod app 수동 sync.

---

## Phase 5 — 운영 시뮬레이션

### A. Drift detection

```bash
# 누군가 클러스터 수정
kubectl scale deployment/my-app -n dev --replicas=10

# ArgoCD가 self-heal로 되돌림 (수십 초 안에)
kubectl get deploy/my-app -n dev
```

### B. Rollback

GitHub에서 잘못된 commit 발견 시:
```bash
cd manifests-repo
git revert HEAD
git push
# ArgoCD가 옛 image tag로 sync
```

또는 ArgoCD UI에서 History → Rollback.

### C. Prod 배포 승인

ArgoCD UI:
- `my-app-prod`는 자동 sync 비활성
- "Sync" 버튼 누르기 전에 검토
- 또는 GitHub Actions의 environment에 required reviewers 설정

### D. 메트릭·로그

```bash
kubectl top pods -n dev
kubectl logs -f -n dev -l app.kubernetes.io/name=my-app
```

운영 환경엔 Prometheus·Grafana·Loki 추가 (다음 부트캠프 영역).

---

## 캡스톤 보고서 템플릿

```markdown
# DevOps · 컨테이너 부트캠프 캡스톤 보고서

## 구축한 파이프라인

[다이어그램 또는 설명]

## 사용한 도구

- Git: GitHub
- CI: GitHub Actions
- Registry: GHCR
- IaC: Terraform (또는 미사용)
- k8s: Docker Desktop
- GitOps: ArgoCD
- 보안: Trivy, Hadolint

## 핵심 결정 사항

### 1. CIOps vs GitOps
[선택 이유]

### 2. 환경 분리 (manifest 디렉토리 vs 브랜치)
[선택과 이유]

### 3. Image tag 전략
[SHA / semver / 둘 다]

### 4. Prod 배포 승인
[ArgoCD 수동 sync / GitHub Environment / 다른 방법]

## 측정 결과

| 지표 | 측정값 |
|---|---|
| CI 평균 시간 | ?분 |
| 이미지 크기 | ?MB |
| Trivy CRITICAL CVE | ?개 |
| Pod 시작 시간 | ?초 |
| 새 commit → prod 배포 가능까지 | ?분 |

## 다음 단계

이 인프라에서 다음을 추가해보고 싶다:
- [ ] Prometheus + Grafana 메트릭
- [ ] Sealed Secrets 또는 External Secrets
- [ ] Service Mesh (Istio)
- [ ] Canary 배포 (Argo Rollouts)
- [ ] Multi-cluster (ApplicationSet)
- [ ] AWS·GCP에 실제 배포 (Terraform)

## 학습 회고

### 가장 어려웠던 것

### 가장 새로 배운 것 3가지

### 즐겨찾기 명령어 5개

```bash
kubectl get pods -A
kubectl logs -f -n <ns> <pod>
helm upgrade --install ...
argocd app sync ...
terraform plan
```
```

---

## 캡스톤 채점 가이드 (자기 평가)

| 항목 | 비중 | 기준 |
|---|---|---|
| **CI 동작** | 20% | push 시 test/build/image/scan 모두 자동 |
| **이미지 품질** | 15% | 100MB 이하, distroless·multi-stage |
| **k8s manifest** | 20% | Helm chart로 환경별 분리 |
| **GitOps** | 15% | ArgoCD가 자동 sync, self-heal |
| **보안** | 10% | Trivy 스캔 통과, Secret 보호 |
| **재현성** | 10% | 다른 사람이 클론 후 같은 결과 |
| **문서** | 10% | README·보고서 |

---

## 캡스톤 이후

이 부트캠프를 마쳤다면 다음 권장:

1. **AWS/GCP에 실제 배포** — EKS·GKE·Cloud Run
2. **관측·SRE 부트캠프** — Prometheus·Grafana·Loki·SLO
3. **메시지큐·캐시·검색** — Kafka·Redis·Elasticsearch
4. **시스템 설계** — MSA·DDD·Event Sourcing

> push 한 번에 운영 배포가 일어나면 — 성공.
