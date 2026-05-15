# Day 4 — GitOps · ArgoCD

## 한 줄 요약

**GitOps**는 "**git이 인프라의 진실원천**". 클러스터에 직접 kubectl·helm 안 함. git을 보는 agent(ArgoCD)가 자동으로 sync. 감사·롤백·재현이 git history로 모두.

## 학습 목표

- [ ] CIOps vs GitOps 차이
- [ ] ArgoCD 아키텍처 (Application·Project·Repository)
- [ ] Pull 모델의 안전성
- [ ] App-of-Apps 패턴
- [ ] Drift detection·self-heal
- [ ] Sync wave·Hooks

---

## CIOps vs GitOps

### CIOps (전통)

```
   PR merge ──▶ GitHub Actions
                    │
                    │ kubectl apply / helm upgrade
                    ▼
                  Cluster
```

문제:
- CI가 클러스터 자격 보유 (보안)
- 실제 상태 ↔ 코드 비교 X
- 수동 변경 detection X

### GitOps (Pull)

```
   PR merge ──▶ git (manifests/values)
                    ▲
                    │ pull (60초마다)
                    │
   Cluster의 ArgoCD ──▶ apply
```

장점:
- 클러스터가 git을 보러감 (CI에 자격 X)
- **drift 자동 감지** (코드와 다르면 알림 또는 자동 복구)
- 모든 변경이 git에 — **완전한 audit**
- 환경별 git 디렉토리 또는 브랜치 → 명확

---

## ArgoCD 구조

```
   ┌──────────────────────────────────────┐
   │  Cluster                              │
   │                                       │
   │  ArgoCD Server   ◀─────▶ ArgoCD UI    │
   │       │                               │
   │  ┌────┴──────────┐                    │
   │  │ Application 1 │── watches git repo │
   │  │ Application 2 │                    │
   │  │ Application N │                    │
   │  └───────────────┘                    │
   │       │                               │
   │       │ 변경 발견 시 kubectl apply     │
   │       ▼                               │
   │  Deployment, Service, ConfigMap, ...  │
   └──────────────────────────────────────┘
                ▲
                │ pull
                │
   ┌──────────────────────────────────────┐
   │  Git repo                             │
   │  ├── apps/                            │
   │  │   ├── my-app/                      │
   │  │   │   ├── deployment.yaml          │
   │  │   │   └── service.yaml             │
   │  │   └── another-app/                 │
   │  └── helm/                            │
   │      └── my-app/                      │
   │          └── values-prod.yaml         │
   └──────────────────────────────────────┘
```

---

## 설치

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# 또는 Helm
helm repo add argo https://argoproj.github.io/argo-helm
helm install argocd argo/argo-cd --namespace argocd --create-namespace

# 확인
kubectl get pods -n argocd
```

### UI 접근

```bash
kubectl port-forward svc/argocd-server -n argocd 8080:443

# admin 비밀번호
kubectl -n argocd get secret argocd-initial-admin-secret \
    -o jsonpath="{.data.password}" | base64 -d
```

브라우저 → https://localhost:8080. admin / 위의 비밀번호.

### CLI

```bash
brew install argocd
# 또는 https://argo-cd.readthedocs.io/en/stable/cli_installation/

argocd login localhost:8080
```

---

## Application — ArgoCD의 단위

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: my-app
  namespace: argocd
spec:
  project: default
  
  source:
    repoURL: https://github.com/my-org/manifests
    targetRevision: main
    path: apps/my-app/dev
  
  destination:
    server: https://kubernetes.default.svc
    namespace: dev
  
  syncPolicy:
    automated:
      prune: true              # 삭제된 리소스 정리
      selfHeal: true           # drift 자동 복구
    syncOptions:
      - CreateNamespace=true
```

```bash
kubectl apply -f my-app.yaml
# ArgoCD가 git 보고 자동 sync 시작

argocd app get my-app
argocd app sync my-app
argocd app history my-app
argocd app rollback my-app <revision>
```

### 또는 Helm chart로

```yaml
spec:
  source:
    repoURL: https://github.com/my-org/manifests
    targetRevision: main
    path: helm/my-app
    helm:
      values: |
        replicaCount: 3
        image:
          tag: v1.2.3
      valueFiles:
        - values-prod.yaml
```

### Kustomize

```yaml
spec:
  source:
    repoURL: https://github.com/my-org/manifests
    path: kustomize/overlays/prod
```

---

## Sync 정책

### Auto vs Manual

| | `automated:` | 없음 (Manual) |
|---|---|---|
| 변경 감지 | 자동 sync | 알림만 |
| 적용 | 즉시 | UI/CLI로 수동 |
| 운영 | 적합 (production도) | 위험한 변경 검토 |

### Self-heal

```yaml
syncPolicy:
  automated:
    selfHeal: true
```

누가 `kubectl edit deploy`로 수동 변경 → ArgoCD가 다시 git의 상태로 되돌림.

> **장점**: drift 방지. **단점**: 디버깅 중 수동 변경이 사라짐 → 일시적으로 sync 꺼두기.

### Prune

```yaml
syncPolicy:
  automated:
    prune: true
```

git에서 삭제된 manifest → 클러스터에서도 삭제.

기본은 false (안전). 명시적으로 켜야.

---

## Sync Wave

순서가 필요한 리소스:

```yaml
# database 먼저
apiVersion: v1
kind: ConfigMap
metadata:
  name: db-config
  annotations:
    argocd.argoproj.io/sync-wave: "1"
---
# 그 후 app
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
  annotations:
    argocd.argoproj.io/sync-wave: "2"
```

낮은 wave 먼저. ConfigMap·CRD·StatefulSet 같이 의존성 있는 자원에.

---

## Hooks

특별한 시점에 실행되는 Job·Pod:

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: db-migration
  annotations:
    argocd.argoproj.io/hook: PreSync
    argocd.argoproj.io/hook-delete-policy: HookSucceeded
spec:
  template:
    spec:
      containers:
        - name: migrate
          image: my-app:v1.2.3
          command: ["./migrate.sh"]
      restartPolicy: Never
```

### Hook 종류

| Hook | 언제 |
|---|---|
| `PreSync` | 본 sync 전 (DB 마이그레이션) |
| `Sync` | 본 sync 중 |
| `PostSync` | 본 sync 후 (smoke test) |
| `SyncFail` | 실패 시 (롤백·알림) |
| `PostDelete` | App 삭제 후 |

### Delete policy

| Policy | 의미 |
|---|---|
| `HookSucceeded` | 성공 시 삭제 |
| `HookFailed` | 실패 시 삭제 |
| `BeforeHookCreation` | 다음 hook 만들기 전 옛 것 삭제 |

---

## App-of-Apps 패턴

여러 app을 하나의 Application으로 관리:

```yaml
# parent Application
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: root
spec:
  source:
    repoURL: https://github.com/my-org/argocd-apps
    path: apps                          # 이 디렉토리 아래의 Application들
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

`apps/` 디렉토리 안에 child Application yaml들 → 하나의 root만 부트스트랩하면 모두.

### ApplicationSet (더 강력)

```yaml
apiVersion: argoproj.io/v1alpha1
kind: ApplicationSet
metadata:
  name: clusters-apps
spec:
  generators:
    - list:
        elements:
          - cluster: dev
            url: https://dev-k8s
          - cluster: prod
            url: https://prod-k8s
  template:
    metadata:
      name: '{{cluster}}-my-app'
    spec:
      source:
        repoURL: ...
        path: 'environments/{{cluster}}'
      destination:
        server: '{{url}}'
        namespace: my-app
```

한 ApplicationSet → 여러 클러스터·환경에 자동 생성.

---

## 운영 사례

### 사례 1 — Image tag 자동 업데이트

CI에서 image 빌드·push 후 manifest의 tag만 업데이트:

```yaml
# manifests repo
spec:
  image:
    tag: v1.2.4    # CI가 이 줄만 수정 → commit → ArgoCD가 자동 sync
```

또는 **Argo CD Image Updater**가 registry를 polling해 자동 PR.

### 사례 2 — Drift 알림

ArgoCD UI의 "OutOfSync" 알림. 누군가 수동 변경했음:
- self-heal 켜졌으면 자동 복구
- 안 켜졌으면 알림 → 조사 → 원복 또는 코드 수정

### 사례 3 — 환경별 manifest 분리

```
manifests-repo/
├── base/
│   ├── deployment.yaml
│   └── service.yaml
├── overlays/
│   ├── dev/
│   │   └── kustomization.yaml      # replicas: 1, image: dev tag
│   ├── staging/
│   │   └── kustomization.yaml
│   └── prod/
│       └── kustomization.yaml      # replicas: 5, image: prod tag
```

Kustomize 또는 Helm values로 환경 분리.

---

## CIOps와 GitOps 혼합

대부분 운영서는 **혼합**:

```
PR merge
   │
   ├── CI: build, test, push image (GitHub Actions)
   │   └── manifests repo의 tag만 PR (또는 push)
   │
   └── ArgoCD: manifests 변경 감지 → sync
```

CI는 **build·image**, ArgoCD는 **deploy**. 책임 분리 + 보안 (CI에 클러스터 자격 X).

---

## 실습 (Hands-on)

### 1단계 — ArgoCD 설치 (Docker Desktop)

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl wait -n argocd --for=condition=Ready --timeout=300s pods --all

# UI
kubectl port-forward svc/argocd-server -n argocd 8080:443

# 비밀번호
kubectl -n argocd get secret argocd-initial-admin-secret \
    -o jsonpath="{.data.password}" | base64 -d
```

### 2단계 — 샘플 Application

GitHub에 public repo 만들기 `my-argocd-test`:
```
my-argocd-test/
└── guestbook.yaml
```

`guestbook.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: guestbook }
spec:
  replicas: 2
  selector: { matchLabels: { app: guestbook } }
  template:
    metadata: { labels: { app: guestbook } }
    spec:
      containers:
        - name: guestbook
          image: nginx:1.27
---
apiVersion: v1
kind: Service
metadata: { name: guestbook }
spec:
  selector: { app: guestbook }
  ports: [{ port: 80 }]
```

push.

ArgoCD에 Application 생성:
```bash
argocd login localhost:8080 --insecure
argocd app create guestbook \
    --repo https://github.com/<you>/my-argocd-test \
    --path . \
    --dest-server https://kubernetes.default.svc \
    --dest-namespace default \
    --sync-policy automated --self-heal --auto-prune

argocd app get guestbook
```

UI에서 sync 상태 확인.

### 3단계 — drift 시뮬레이션

```bash
kubectl scale deployment/guestbook --replicas=5
# 잠시 후
kubectl get deploy guestbook
# replicas: 2 (ArgoCD가 self-heal로 되돌림)
```

### 4단계 — git 변경 → 자동 sync

```bash
# replicas: 2 → 3으로 git에 commit·push
# 60초 이내 ArgoCD가 감지·sync
kubectl get deploy guestbook -w
```

### 5단계 — 롤백

```bash
argocd app history guestbook
argocd app rollback guestbook 1
```

또는 git revert.

---

## 더 읽어볼 자료

- 🔗 [ArgoCD 공식](https://argo-cd.readthedocs.io/)
- 🔗 [Flux](https://fluxcd.io/) — ArgoCD 대안
- 🔗 [OpenGitOps](https://opengitops.dev/) — GitOps 원칙
- 🔗 [ArgoCD Image Updater](https://argocd-image-updater.readthedocs.io/)
- 📘 『GitOps and Kubernetes』 (Beda 등, Manning)
- 🎓 ArgoCon talks (YouTube)
