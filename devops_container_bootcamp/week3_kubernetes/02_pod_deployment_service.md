# Day 2 — Pod · Deployment · Service

## 한 줄 요약

**Pod**은 컨테이너 하나(또는 묶음)의 실행 단위. **Deployment**가 Pod을 N개 유지·rolling update. **Service**가 Pod IP들을 묶어 안정된 endpoint 제공. 이 셋이 k8s의 90%.

## 학습 목표

- [ ] Pod 구조 (containers + shared network + volume)
- [ ] ReplicaSet과 Deployment 관계
- [ ] Rolling update 메커니즘
- [ ] Service 4가지 타입
- [ ] Label·Selector 매칭
- [ ] Pod에 디버깅 도구 추가 (init container, sidecar)

---

## Pod

### Pod이란

```
   Pod
   ┌────────────────────────────────────┐
   │  Container A   Container B          │
   │  ┌────────┐    ┌────────┐           │
   │  │ nginx  │    │ log    │           │
   │  │        │    │ sender │           │
   │  └────────┘    └────────┘           │
   │       ▲             ▲               │
   │       │             │               │
   │       └─── shared network ──┘       │
   │       └─── shared volume ──┘        │
   └────────────────────────────────────┘
```

- 같은 Pod의 컨테이너는 **localhost로 통신**, **같은 volume 공유**
- 같이 시작·죽음 (한 unit)
- 단일 IP

### Pod 1개 = 컨테이너 1개? (보통)

대부분 1 컨테이너. 예외:
- **Sidecar** — 로그 수집·proxy (Istio Envoy)
- **Adapter** — 출력 형식 변환
- **Ambassador** — 외부 통신 proxy

```yaml
# 단순 Pod
apiVersion: v1
kind: Pod
metadata:
  name: web
spec:
  containers:
    - name: app
      image: my-app:1.0
      ports:
        - containerPort: 8080
```

### Pod이 죽으면?

**다시 안 만들어짐**. Pod 자체는 ephemeral. Deployment 같은 컨트롤러가 있어야 재생성.

---

## ReplicaSet · Deployment

### ReplicaSet — "N개 유지"

```yaml
apiVersion: apps/v1
kind: ReplicaSet
metadata:
  name: web-rs
spec:
  replicas: 3
  selector:
    matchLabels:
      app: web
  template:
    metadata:
      labels:
        app: web
    spec:
      containers:
        - name: app
          image: my-app:1.0
```

ReplicaSet Controller가:
- selector로 매칭되는 Pod 수 확인
- < replicas면 새 Pod 생성
- > replicas면 삭제

> 우리가 직접 ReplicaSet 만드는 일은 거의 없음. Deployment를 통해.

### Deployment — ReplicaSet 위 추상

Deployment는 **버전이 바뀐 ReplicaSet들의 history**를 관리:

```
   Deployment "web"
       │
       ├─▶ ReplicaSet v1 (replicas 0)  ← 옛 버전
       │       └─▶ (no pods)
       │
       └─▶ ReplicaSet v2 (replicas 3)  ← 현재
               ├─▶ Pod web-v2-abc
               ├─▶ Pod web-v2-def
               └─▶ Pod web-v2-ghi
```

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web
spec:
  replicas: 3
  selector:
    matchLabels:
      app: web
  template:
    metadata:
      labels:
        app: web
    spec:
      containers:
        - name: app
          image: my-app:1.0
          ports:
            - containerPort: 8080
          resources:
            requests:
              cpu: "100m"
              memory: "256Mi"
            limits:
              cpu: "500m"
              memory: "512Mi"
```

```bash
kubectl apply -f deploy.yaml
kubectl get deploy
kubectl get rs
kubectl get pods --show-labels
```

### Rolling Update

```bash
# 이미지 업데이트
kubectl set image deployment/web app=my-app:2.0
# 또는 YAML 수정 후 apply
```

진행:
```
새 ReplicaSet v2 만들기 (replicas: 0)
v2 +1 (1 Pod 시작)
v2 ready 확인
v1 -1 (1 Pod 종료)
... 반복 (maxSurge·maxUnavailable에 따라)
v1 0, v2 3
```

```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1                       # 최대 +N개 (기본 25%)
      maxUnavailable: 0                 # 최대 -N개 (기본 25%)
```

`maxUnavailable: 0`이면 **무중단** 보장 (느림). `maxSurge: 1`이면 한 번에 1개씩.

### 롤백

```bash
kubectl rollout history deployment/web
kubectl rollout undo deployment/web
# 또는 특정 revision으로
kubectl rollout undo deployment/web --to-revision=2
```

→ history는 어디? Deployment annotation + 옛 ReplicaSet들.

### Recreate strategy

```yaml
spec:
  strategy:
    type: Recreate
```

옛 Pod 모두 죽이고 → 새 Pod 시작. **다운타임 있음**. DB 마이그레이션 등에 사용.

---

## Service

Pod IP는 ephemeral — Pod 죽으면 IP 바뀜. 안정된 endpoint 필요 → **Service**.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: web-svc
spec:
  selector:
    app: web                            # 이 label 가진 Pod 모두
  ports:
    - port: 80                          # Service 포트
      targetPort: 8080                  # Pod 포트
  type: ClusterIP                       # 기본
```

```bash
kubectl get svc
# NAME      TYPE        CLUSTER-IP     PORT(S)
# web-svc   ClusterIP   10.96.4.123    80/TCP
```

Pod에서:
```bash
curl http://web-svc                     # 같은 namespace
curl http://web-svc.default.svc.cluster.local  # FQDN
```

### Service 4가지 타입

#### 1) ClusterIP (기본)

- 클러스터 내부 IP만
- Pod끼리 통신용
- 외부 접근 X

#### 2) NodePort

- 모든 Node의 특정 port (30000~32767)에 노출
- `<NodeIP>:<NodePort>`로 접근
- 개발·테스트에 OK, 운영엔 별로

```yaml
spec:
  type: NodePort
  ports:
    - port: 80
      targetPort: 8080
      nodePort: 30080
```

#### 3) LoadBalancer

- 클라우드 환경에서 자동으로 LB 생성 (AWS ELB, GCP LB)
- 외부 IP 제공
- Docker Desktop도 일부 지원 (localhost)

```yaml
spec:
  type: LoadBalancer
  ports:
    - port: 80
      targetPort: 8080
```

#### 4) Headless

```yaml
spec:
  clusterIP: None
  selector:
    app: db
```

clusterIP 없음 → DNS로 모든 Pod IP 직접 반환. StatefulSet과 함께 사용.

---

## Label · Selector

```yaml
metadata:
  labels:
    app: web
    version: v2
    env: prod
    tier: frontend
```

selector로 매칭:
```yaml
selector:
  matchLabels:
    app: web
    env: prod
```

명령어로 selector:
```bash
kubectl get pods -l app=web
kubectl get pods -l 'env in (prod,staging)'
kubectl get pods -l 'version!=v1'
```

### Annotation vs Label

| | Label | Annotation |
|---|---|---|
| 목적 | selector로 매칭 | 메타데이터·툴 정보 |
| 길이 | 짧음 | 김 가능 |
| 검색 | 가능 | 보통 X |

```yaml
metadata:
  labels:
    app: web                          # selector용
  annotations:
    description: "Main web application"   # 정보만
    prometheus.io/scrape: "true"          # tool용
```

---

## init Container

Pod의 main container 시작 **전에** 실행되는 컨테이너:

```yaml
spec:
  initContainers:
    - name: wait-for-db
      image: busybox:1.36
      command: ['sh', '-c', 'until nc -z db 5432; do sleep 1; done']
  containers:
    - name: app
      image: my-app:1.0
```

DB 준비 대기·시드 데이터 로드·secret fetch에 사용.

> Spring Boot 앱이 startup probe로 의존성 대기를 처리할 수도 있음. 어느 쪽이든 OK.

---

## 운영 사례

### 사례 1 — image:latest의 함정

```yaml
image: my-app:latest
```

```bash
kubectl apply -f deploy.yaml      # 첫 배포 OK
# 새 이미지를 :latest로 push
kubectl apply -f deploy.yaml      # 같은 YAML → 아무것도 안 일어남!
                                  # k8s가 "변화 없음" 판단
```

해결:
1. **고정 태그** (`my-app:v1.2.3`)
2. **digest** (`my-app@sha256:abc...`)
3. 강제 재배포: `kubectl rollout restart deployment/web`
4. `imagePullPolicy: Always` (옵션)

### 사례 2 — Pod이 CrashLoopBackOff

```bash
kubectl get pods
# NAME           STATUS              RESTARTS
# web-xyz        CrashLoopBackOff    5

kubectl logs web-xyz                  # 현재 컨테이너
kubectl logs --previous web-xyz       # 죽은 컨테이너 (중요!)
kubectl describe pod web-xyz          # Events 섹션
```

흔한 원인:
- ENTRYPOINT 즉시 종료
- DB 연결 실패
- Healthcheck 실패
- 자원 부족 (OOMKilled)

### 사례 3 — Service가 Pod 못 찾음

```bash
kubectl get svc web-svc -o yaml | grep selector -A 2
# selector:
#   app: web

kubectl get pods --show-labels
# 라벨이 app=web 인가?
```

label·selector 불일치가 가장 흔함.

```bash
kubectl get endpoints web-svc
# NAME      ENDPOINTS
# web-svc   <none>                ← 비어있으면 selector 매칭 실패
```

---

## 실습 (Hands-on)

### 1단계 — Deployment 만들기

```yaml
# web-deploy.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web
spec:
  replicas: 3
  selector:
    matchLabels:
      app: web
  template:
    metadata:
      labels:
        app: web
    spec:
      containers:
        - name: nginx
          image: nginx:1.27
          ports:
            - containerPort: 80
```

```powershell
kubectl apply -f web-deploy.yaml
kubectl get deploy,rs,pods
```

### 2단계 — Service 추가

```yaml
# web-svc.yaml
apiVersion: v1
kind: Service
metadata:
  name: web-svc
spec:
  selector:
    app: web
  ports:
    - port: 80
      targetPort: 80
```

```powershell
kubectl apply -f web-svc.yaml
kubectl get svc

# 내부에서 접근 (다른 Pod로)
kubectl run debug --rm -it --image=alpine -- sh
$ apk add curl
$ curl web-svc
# nginx 응답
```

### 3단계 — port-forward로 외부에서

```powershell
kubectl port-forward svc/web-svc 8080:80
# http://localhost:8080
```

### 4단계 — Scale·Rolling update

```powershell
kubectl scale deployment/web --replicas=5
kubectl get pods -w               # 변경 관찰

kubectl set image deployment/web nginx=nginx:1.28
kubectl rollout status deployment/web

kubectl rollout history deployment/web
kubectl rollout undo deployment/web
```

### 5단계 — Pod 죽이기 → 자동 복구

```powershell
kubectl get pods
kubectl delete pod web-xxxxx
kubectl get pods                  # 새 Pod이 즉시 만들어짐
```

ReplicaSet Controller의 reconciliation.

### 6단계 — 정리

```powershell
kubectl delete -f web-svc.yaml
kubectl delete -f web-deploy.yaml
```

---

## 더 읽어볼 자료

- 📘 『Kubernetes Up and Running』 — 5-6장
- 🔗 [Pod 공식](https://kubernetes.io/docs/concepts/workloads/pods/)
- 🔗 [Deployment 공식](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)
- 🔗 [Service 공식](https://kubernetes.io/docs/concepts/services-networking/service/)
- 🔗 [Kubernetes Patterns](https://github.com/k8spatterns/examples) (책 + 코드)
