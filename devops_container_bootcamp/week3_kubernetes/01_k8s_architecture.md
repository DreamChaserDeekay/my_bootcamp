# Day 1 — Kubernetes 아키텍처

## 한 줄 요약

k8s는 **Control Plane**(brain)이 원하는 상태(desired state)를 결정하고, **Node**(worker)가 실제 컨테이너를 실행한다. 모든 상태는 **etcd**에 저장되고 **kube-apiserver**가 단일 진입점. **Declarative + Reconciliation Loop**가 핵심.

## 학습 목표

- [ ] Control Plane 4개 컴포넌트의 역할
- [ ] Node 2개 컴포넌트 (kubelet, kube-proxy)
- [ ] Pod·Service·Deployment 등 객체의 위치 (etcd)
- [ ] Reconciliation Loop (desired ↔ current state)
- [ ] kubectl 명령의 흐름
- [ ] manifest YAML 구조

---

## 큰 그림

```
                      ┌────────────────────────────────────────────┐
                      │           Control Plane (master)           │
                      │                                            │
                      │   ┌─────────────────┐                      │
   kubectl ◀────────▶ │   │  kube-apiserver  │ ← 단일 진입점        │
                      │   └────────┬────────┘                      │
                      │            │                               │
                      │   ┌────────┴────────┐  ┌──────────────────┐│
                      │   │      etcd       │  │  scheduler        ││
                      │   │  (key-value DB) │  │  controller-mgr   ││
                      │   └─────────────────┘  └──────────────────┘│
                      └────────────────┬───────────────────────────┘
                                       │
                                       │ apiserver를 통해
                                       │
            ┌──────────────────────────┼──────────────────────────┐
            │                          │                          │
            ▼                          ▼                          ▼
   ┌──────────────────┐       ┌──────────────────┐       ┌──────────────────┐
   │   Node 1         │       │   Node 2         │       │   Node 3         │
   │  ┌────────────┐  │       │  ┌────────────┐  │       │  ┌────────────┐  │
   │  │  kubelet   │  │       │  │  kubelet   │  │       │  │  kubelet   │  │
   │  │ kube-proxy │  │       │  │ kube-proxy │  │       │  │ kube-proxy │  │
   │  │ container  │  │       │  │ container  │  │       │  │ container  │  │
   │  │ runtime    │  │       │  │ runtime    │  │       │  │ runtime    │  │
   │  └────────────┘  │       │  └────────────┘  │       │  └────────────┘  │
   │                  │       │                  │       │                  │
   │  Pod  Pod  Pod   │       │  Pod  Pod        │       │  Pod  Pod  Pod   │
   └──────────────────┘       └──────────────────┘       └──────────────────┘
```

---

## Control Plane 4개 컴포넌트

### 1) kube-apiserver

**모든 통신의 단일 진입점**. REST API.

- kubectl이 보내는 명령 받음
- 다른 컴포넌트(scheduler·kubelet)와 통신
- etcd에 상태 읽기/쓰기
- 인증·인가·admission

```bash
# 사실 kubectl은 그냥 REST API 호출
kubectl get pods -v=9             # 자세히
# GET https://k8s.example/api/v1/namespaces/default/pods
```

### 2) etcd

**모든 클러스터 상태**가 저장되는 분산 key-value DB.

- 모든 객체(Pod, Service, ConfigMap, Secret)가 키-값으로
- Raft consensus로 일관성
- **k8s의 단일 진실원천(SSoT)**

```bash
# (관리자만)
ETCDCTL_API=3 etcdctl get / --prefix --keys-only
# /registry/pods/default/myapp-7d5b8c-xyz
# /registry/services/default/myapp
# ...
```

> etcd 손상 = 클러스터 사망. **백업 필수**.

### 3) scheduler

**새 Pod을 어느 Node에 배치할지 결정**.

알고리즘:
1. Filtering — 가능한 Node만 (자원 충분, taint 매치)
2. Scoring — 가장 좋은 Node (자원 균형, affinity)

```yaml
# Pod이 noderequirement를 가짐
spec:
  resources:
    requests:
      cpu: "500m"
      memory: "512Mi"
  nodeSelector:
    disktype: ssd
```

scheduler가 위 요구사항 + 모든 node 자원 보고 결정.

### 4) controller-manager

**여러 컨트롤러를 한 프로세스로**. 각 컨트롤러는 **Reconciliation Loop** 실행:

```
while true:
    desired = etcd에서 원하는 상태
    current = 실제 상태
    if desired != current:
        조치
    sleep(short)
```

내장 컨트롤러:
- **Deployment Controller** — desired replicas == actual?
- **ReplicaSet Controller**
- **Node Controller** — Node가 죽었나?
- **Endpoint Controller** — Service ↔ Pod 매핑
- **Namespace Controller**

### 그 외

- **cloud-controller-manager** — 클라우드 통합 (AWS/GCP)

---

## Node 컴포넌트

### kubelet

각 Node에서 동작. **apiserver의 명령을 받아 컨테이너 실행/종료**.

- Pod spec 받음
- container runtime(containerd, CRI-O)에 컨테이너 시작 명령
- 상태를 apiserver에 보고
- liveness/readiness probe 실행

### kube-proxy

각 Node의 **네트워크 구현**.

- Service IP → Pod IP 라우팅
- iptables 또는 IPVS rules
- L4 load balancing

### Container Runtime

실제로 컨테이너를 실행하는 엔진:
- **containerd** (가장 흔함, Docker 일부 추출)
- **CRI-O**
- ~~Docker~~ (k8s 1.24부터 제거, dockershim 사라짐)

> Docker로 빌드한 이미지는 그대로 사용 가능 (containerd가 OCI 이미지 표준 준수).

---

## Pod 생성 흐름 — `kubectl apply -f` 후

```
1. kubectl이 manifest YAML을 apiserver에 POST
   │
   ▼
2. apiserver — 인증·검증
   │
   ▼
3. apiserver — etcd에 Pod 객체 저장
                (status: Pending, nodeName: 없음)
   │
   ▼
4. scheduler가 etcd watch — Pending pod 발견
   │
   ▼
5. scheduler — 적절한 Node 결정
                (filtering + scoring)
   │
   ▼
6. scheduler — etcd 업데이트
                (nodeName: node-2)
   │
   ▼
7. 해당 node의 kubelet이 etcd/apiserver watch
                — 자기 노드에 할당된 Pod 발견
   │
   ▼
8. kubelet — 이미지 pull (이미 없으면)
            → containerd로 컨테이너 시작
   │
   ▼
9. kubelet — apiserver에 상태 보고
                (status: Running)
   │
   ▼
10. kube-proxy가 Service 변경 감지
                (Pod의 IP를 Service endpoint에 등록)
```

**모든 통신은 apiserver를 거침**. 컴포넌트끼리 직접 안 함.

---

## Declarative vs Imperative

### Imperative (옛 방식)

```bash
kubectl run nginx --image=nginx
kubectl scale deployment nginx --replicas=3
kubectl set image deployment/nginx nginx=nginx:1.27
```

순간순간 명령. **상태 유지 어려움**.

### Declarative (k8s 권장)

YAML로 **원하는 상태**를 표현, apply:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx
spec:
  replicas: 3
  ...
```

```bash
kubectl apply -f nginx.yaml
```

같은 명령을 반복해도 안전 (idempotent). git에 commit → 재현 가능. **GitOps의 기반**.

---

## manifest YAML 구조

모든 k8s 객체:

```yaml
apiVersion: <api>/<version>          # apps/v1, v1, networking.k8s.io/v1
kind: <Type>                          # Pod, Deployment, Service, ...
metadata:
  name: <name>
  namespace: <ns>                     # default
  labels:
    app: my-app
  annotations:
    ...
spec:
  ...                                 # 객체별로 다름
status:                               # k8s가 채움
  ...
```

### 예 — 가장 단순한 Pod

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: hello
spec:
  containers:
    - name: nginx
      image: nginx:1.27
      ports:
        - containerPort: 80
```

```bash
kubectl apply -f hello.yaml
kubectl get pods
# NAME    READY   STATUS    RESTARTS   AGE
# hello   1/1     Running   0          10s
```

---

## kubectl — 핵심 명령

```bash
# 조회
kubectl get pods                       # default ns
kubectl get pods -A                    # 모든 namespace
kubectl get pods -n kube-system        # 특정 ns
kubectl get pods -o wide               # node·IP 추가
kubectl get pods -o yaml > pods.yaml
kubectl get pods --watch               # 실시간

# 자세히
kubectl describe pod hello
kubectl logs hello
kubectl logs -f hello                  # follow
kubectl logs --previous hello          # 죽기 전 컨테이너 로그

# 들어가기
kubectl exec -it hello -- bash
kubectl exec hello -- ls /

# 포트포워드 (로컬에서 접근)
kubectl port-forward pod/hello 8080:80

# 객체 생성·수정·삭제
kubectl apply -f manifest.yaml
kubectl delete -f manifest.yaml
kubectl delete pod hello

# 편집 (직접 etcd 수정 — 위험)
kubectl edit deployment myapp

# context (여러 클러스터 전환)
kubectl config get-contexts
kubectl config use-context my-prod-cluster
```

### 자주 보는 약어

```
po   = pods
deploy = deployments
svc  = services
ns   = namespaces
cm   = configmaps
sec  = secrets
ing  = ingresses
hpa  = horizontalpodautoscalers
sa   = serviceaccounts
```

```bash
kubectl get po,svc,deploy
```

---

## Namespace — 격리 단위

```bash
kubectl get namespaces
# default
# kube-system          ← k8s 시스템 컴포넌트
# kube-public
# kube-node-lease
```

새 namespace:
```bash
kubectl create namespace dev
# 또는 YAML
```

같은 이름 객체를 여러 namespace에 가질 수 있음.

```yaml
metadata:
  name: my-app
  namespace: dev
```

**team·environment별 격리** 권장.

---

## 운영 사례

### 사례 1 — Pod이 Pending에 머무름

```bash
kubectl describe pod stuck-pod
# Events:
#   Warning  FailedScheduling   no nodes available to schedule pods
```

원인:
- 자원 부족 (CPU/메모리 request가 모든 node 합보다 큼)
- nodeSelector·taint 매치 안 됨
- PVC bound 안 됨

### 사례 2 — Deployment 업데이트 안 됨

```bash
kubectl apply -f deploy.yaml
# 새 이미지 반영 안 됨
```

YAML의 `image:` 태그가 `:latest`고 동일 → k8s가 변화 감지 안 함. **digest 또는 명시적 버전 사용**.

### 사례 3 — etcd 손상으로 클러스터 다운

**조치**: etcd 백업 복원 (운영 사이트가 미리 백업 cronjob 운영해야 함).

> Docker Desktop의 단일 node 클러스터엔 해당 X.

---

## 실습 (Hands-on)

### 1단계 — 클러스터 정보

```powershell
kubectl version
kubectl cluster-info
kubectl get nodes -o wide
kubectl get all -A
```

### 2단계 — 첫 Pod

```yaml
# hello.yaml
apiVersion: v1
kind: Pod
metadata:
  name: hello
  labels:
    app: hello
spec:
  containers:
    - name: nginx
      image: nginx:1.27
      ports:
        - containerPort: 80
```

```powershell
kubectl apply -f hello.yaml
kubectl get pods
kubectl describe pod hello
kubectl logs hello
```

### 3단계 — port-forward로 접근

```powershell
kubectl port-forward pod/hello 8080:80
# 다른 터미널: curl http://localhost:8080
```

### 4단계 — exec로 들어가기

```powershell
kubectl exec -it hello -- bash
# 안에서
cat /etc/hostname                       # Pod 이름
env                                      # env vars
exit
```

### 5단계 — 직접 etcd 들여다보기 (참고)

Docker Desktop은 etcdctl 직접 접근 어려움. 그러나:

```powershell
kubectl get pod hello -o yaml
# k8s가 etcd에서 가져온 YAML 전체
# spec, status, metadata.uid, resourceVersion 등
```

### 6단계 — 정리

```powershell
kubectl delete -f hello.yaml
# 또는
kubectl delete pod hello
```

---

## 더 읽어볼 자료

- 📘 『Kubernetes Up and Running』 3rd (Hightower, Burns, Beda)
- 📘 『Kubernetes in Action』 2nd (Marko Lukša)
- 🔗 [Kubernetes Docs — Concepts](https://kubernetes.io/docs/concepts/)
- 🎓 [CNCF — Kubernetes Fundamentals](https://www.cncf.io/training/) (LFS158)
- 🔗 [kelseyhightower/kubernetes-the-hard-way](https://github.com/kelseyhightower/kubernetes-the-hard-way) — 처음부터 직접 만들어보기
- 🎓 [김태민의 쿠버네티스](https://www.inflearn.com/course/쿠버네티스-입문) (인프런 한국어)
