# Day 2 — 파이프라인 설계

## 한 줄 요약

좋은 CI/CD 파이프라인은 **빠른 피드백 + 보안 게이트 + 명확한 단계**. 빌드 → 테스트 → lint → 보안 스캔 → 이미지 push → 환경별 배포. 운영 배포엔 manual approval 또는 GitOps.

## 학습 목표

- [ ] 표준 파이프라인 단계 (build/test/lint/scan/image/deploy)
- [ ] 환경별 deploy gating (dev 자동, prod 승인)
- [ ] 배포 전략 비교 (Rolling / Blue-Green / Canary)
- [ ] Image promotion (dev tag → prod tag)
- [ ] Rollback 전략
- [ ] PR 검증 vs 배포 분리

---

## 표준 단계

```
   PR 또는 push
       │
       ▼
   ┌─────────────────────────────────────────────────────────────┐
   │  Phase 1: Validate (PR과 main 둘 다)                          │
   │  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐           │
   │  │ Lint │  │Build │  │ Unit │  │Integr│  │Scan  │           │
   │  │      │  │      │  │ Test │  │ Test │  │(SAST)│           │
   │  └──────┘  └──────┘  └──────┘  └──────┘  └──────┘           │
   └─────────────────────────────────────────────────────────────┘
                                │ (main만 진행)
                                ▼
   ┌─────────────────────────────────────────────────────────────┐
   │  Phase 2: Package                                           │
   │  ┌────────┐  ┌────────┐  ┌────────┐                         │
   │  │ Build  │  │ Trivy  │  │ Push to│                         │
   │  │ Image  │  │ Scan   │  │ GHCR   │                         │
   │  └────────┘  └────────┘  └────────┘                         │
   └─────────────────────────────────────────────────────────────┘
                                │
                                ▼
   ┌─────────────────────────────────────────────────────────────┐
   │  Phase 3: Deploy                                            │
   │  ┌──────┐    ┌─────┐ approval ┌─────┐ approval ┌────────┐   │
   │  │ dev  │──▶│staging│────────▶│ prod│         │rollback│   │
   │  │auto  │    │auto   │         │     │         │수동/자동│   │
   │  └──────┘    └─────┘            └─────┘        └────────┘   │
   └─────────────────────────────────────────────────────────────┘
```

---

## Phase 1 — Validate (PR·main 공통)

### Lint

```yaml
- name: Spotless check
  run: ./gradlew spotlessCheck

- name: Hadolint
  uses: hadolint/hadolint-action@v3
  with:
    dockerfile: Dockerfile

- name: yamllint
  run: |
    pip install yamllint
    yamllint .
```

### Build

```yaml
- name: Build JAR
  run: ./gradlew bootJar -x test
```

### Unit Test

```yaml
- name: Unit tests
  run: ./gradlew test
- name: Upload test report
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: test-reports
    path: build/reports/tests/
```

### Integration Test (Testcontainers)

```yaml
- name: Integration tests
  run: ./gradlew integrationTest
  # Testcontainers가 자동으로 Docker로 DB·Redis 띄움
```

### SAST (Static Application Security Testing)

```yaml
- name: SonarQube
  uses: SonarSource/sonarqube-scan-action@v3
  env:
    SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
    SONAR_HOST_URL: https://sonar.example.com

# 또는 무료 CodeQL
- name: CodeQL
  uses: github/codeql-action/analyze@v3
```

### Dependency scan

```yaml
- name: OWASP Dependency Check
  run: ./gradlew dependencyCheckAnalyze

- name: Grype scan
  uses: anchore/scan-action@v4
  with:
    path: "."
    fail-build: true
    severity-cutoff: high
```

---

## Phase 2 — Package (main만)

```yaml
package:
  needs: validate
  if: github.ref == 'refs/heads/main'
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        distribution: temurin
        java-version: 21
    
    - name: Build JAR
      run: ./gradlew bootJar
    
    - name: Set image tag
      id: tag
      run: echo "tag=$(git rev-parse --short HEAD)" >> $GITHUB_OUTPUT
    
    - name: Setup BuildKit
      uses: docker/setup-buildx-action@v3
    
    - name: Login to GHCR
      uses: docker/login-action@v3
      with:
        registry: ghcr.io
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}     # 자동 제공
    
    - name: Build·Push
      uses: docker/build-push-action@v6
      with:
        context: .
        push: true
        tags: |
          ghcr.io/${{ github.repository }}:${{ steps.tag.outputs.tag }}
          ghcr.io/${{ github.repository }}:latest
        cache-from: type=gha
        cache-to: type=gha,mode=max
    
    - name: Trivy scan
      uses: aquasecurity/trivy-action@master
      with:
        image-ref: ghcr.io/${{ github.repository }}:${{ steps.tag.outputs.tag }}
        severity: 'HIGH,CRITICAL'
        exit-code: '1'                            # 발견 시 실패
        format: 'sarif'
        output: 'trivy.sarif'
    
    - name: Upload SARIF
      if: always()
      uses: github/codeql-action/upload-sarif@v3
      with:
        sarif_file: trivy.sarif
```

---

## Phase 3 — Deploy

### 환경별 자동 vs 승인

```yaml
deploy-dev:
  needs: package
  if: github.ref == 'refs/heads/main'
  runs-on: ubuntu-latest
  environment: development                       # 승인 없이 자동
  steps:
    - uses: actions/checkout@v4
    - name: Deploy to dev k8s
      run: |
        helm upgrade --install my-app ./helm/my-app \
          -f helm/values-dev.yaml \
          --set image.tag=${{ needs.package.outputs.tag }} \
          --namespace dev

deploy-staging:
  needs: deploy-dev
  if: github.ref == 'refs/heads/main'
  runs-on: ubuntu-latest
  environment: staging
  steps: [...]

deploy-prod:
  needs: deploy-staging
  if: startsWith(github.ref, 'refs/tags/v')      # 태그만
  runs-on: ubuntu-latest
  environment: production                         # GitHub 환경 → 승인 필요
  steps: [...]
```

GitHub repo Settings → Environments → production:
- ☑ Required reviewers (3명 중 1명 승인)
- ☑ Wait timer: 0
- Deployment branches: main 또는 tags `v*`

---

## 배포 전략

### Rolling Update (기본)

k8s Deployment의 기본. 점진적 교체.

```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
```

장: 다운타임 X, 자원 절약. 단: 옛/새 버전 공존 (트래픽 둘 다 받음).

### Blue-Green

```
   라우터
     │
     ├── Blue (현재 운영)
     └── Green (새 버전, 사전 검증)
   
   검증 후 라우터 → Green
   문제 있으면 즉시 → Blue 롤백
```

k8s에선:
- Service의 selector를 `version: blue`로
- Green Deployment 만들고 검증
- Service selector를 `version: green`으로 변경 (즉시 전환)

장: 즉시 롤백, 옛/새 공존 X. 단: 자원 2배.

### Canary

```
   라우터
     │  (예: 95% 트래픽)
     ├── v1 (현재)
     │  (5% 트래픽)
     └── v2 (새 버전)
   
   메트릭 OK면 → 10% → 50% → 100%
```

k8s에선:
- Argo Rollouts 또는 Flagger 사용
- Istio·Linkerd로 percent 기반 라우팅

장: 점진적 검증, 실제 사용자로 카나리. 단: 복잡.

---

## Image promotion vs Rebuild

### Anti-pattern — 환경마다 rebuild

```
dev: 빌드(main) → image v1.2.3-dev → 배포
staging: 빌드(main) → image v1.2.3-staging → 배포
prod: 빌드(main) → image v1.2.3-prod → 배포
```

각 빌드가 미세하게 다를 수 있음. "dev에선 됐는데" 가능.

### 권장 — image promotion

```
빌드(main) → image v1.2.3 → dev에 배포
                            ↓ 통과
                          staging에 배포
                            ↓ 통과
                          prod에 배포 (수동 승인)
```

같은 이미지(같은 binary)를 모든 환경에. **환경 차이는 ConfigMap·Secret만**.

```yaml
deploy-dev:
  steps:
    - run: |
        helm upgrade --install ... --set image.tag=$SHA

deploy-prod:
  needs: deploy-staging
  steps:
    - run: |
        helm upgrade --install ... --set image.tag=$SHA   # 동일 SHA
```

---

## Rollback

### Helm

```bash
helm history my-app
helm rollback my-app <revision>
```

### k8s Deployment

```bash
kubectl rollout history deployment/my-app
kubectl rollout undo deployment/my-app
```

### GitOps

git revert → ArgoCD 자동 sync.

### 응급 (DB 마이그레이션 등)

DB 스키마 forward-compatible 설계 — rollback 시 옛 코드도 새 스키마 OK.

---

## PR 검증 vs 배포 분리

```yaml
# .github/workflows/pr.yml
on:
  pull_request:
jobs:
  test: [...]
  lint: [...]

# .github/workflows/deploy.yml
on:
  push:
    branches: [main]
jobs:
  build: [...]
  deploy-dev: [...]
```

**PR엔 배포 X** — push 권한 부여 안 함. 보안 우선.

---

## 풀 예제 — 단일 파일

```yaml
name: CI/CD

on:
  push:
    branches: [main]
    tags: ['v*']
  pull_request:
    branches: [main]

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

permissions:
  contents: read
  packages: write       # GHCR push
  id-token: write       # OIDC (옵션)
  security-events: write # SARIF

env:
  IMAGE: ghcr.io/${{ github.repository }}

jobs:

  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: actions/cache@v4
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
      - run: ./gradlew spotlessCheck
      - run: ./gradlew build
      - if: always()
        uses: actions/upload-artifact@v4
        with:
          name: reports
          path: build/reports/

  image:
    needs: validate
    if: github.event_name != 'pull_request'
    runs-on: ubuntu-latest
    outputs:
      tag: ${{ steps.tag.outputs.tag }}
    steps:
      - uses: actions/checkout@v4
      - id: tag
        run: echo "tag=$(git rev-parse --short HEAD)" >> $GITHUB_OUTPUT
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
            ${{ env.IMAGE }}:${{ steps.tag.outputs.tag }}
            ${{ env.IMAGE }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max
      - uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ env.IMAGE }}:${{ steps.tag.outputs.tag }}
          severity: HIGH,CRITICAL
          exit-code: '1'

  deploy-dev:
    needs: image
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    environment: development
    steps:
      - uses: actions/checkout@v4
      - run: |
          echo "Deploying ${{ env.IMAGE }}:${{ needs.image.outputs.tag }} to dev"
          # helm upgrade --install ... --set image.tag=${{ needs.image.outputs.tag }}

  deploy-prod:
    needs: deploy-dev
    if: startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    environment: production           # GitHub UI에서 승인
    steps:
      - run: echo "Deploy to prod approved"
```

---

## 실습 (Hands-on)

다음 lab(`lab7`)에서 풀 파이프라인 작성.

---

## 더 읽어볼 자료

- 📘 『Continuous Delivery』 (Jez Humble) — 표준 텍스트
- 📘 『The DevOps Handbook』 (Kim, Humble, Debois, Willis)
- 🔗 [docker/build-push-action](https://github.com/docker/build-push-action)
- 🔗 [GitHub Environments](https://docs.github.com/en/actions/deployment/targeting-different-environments)
- 🔗 [Argo Rollouts](https://argoproj.github.io/argo-rollouts/) — Canary·Blue-Green
- 🔗 [Flagger](https://flagger.app/)
