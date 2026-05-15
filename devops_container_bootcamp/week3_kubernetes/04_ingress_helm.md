# Day 4 — Ingress · Helm

## 한 줄 요약

**Ingress**는 클러스터 외부에서 들어오는 HTTP/HTTPS를 내부 Service로 라우팅. **Helm**은 k8s YAML의 패키지 매니저 — 환경별 values만 바꿔 같은 chart로 dev/prod 배포.

## 학습 목표

- [ ] Ingress vs Service 차이
- [ ] Ingress Controller 설치 (nginx-ingress)
- [ ] Path·host 기반 라우팅
- [ ] TLS termination
- [ ] Helm Chart 구조
- [ ] values.yaml로 환경 분리
- [ ] Helm release 관리

---

## Ingress

### Service의 한계

- LoadBalancer 타입은 Service마다 하나씩 → AWS LB 비용
- 도메인·경로 기반 라우팅 X
- TLS 끝점 부재

### Ingress가 해결

```
   클라이언트 (HTTPS)
       │
       ▼
   Ingress (도메인·경로 기반 라우팅 + TLS)
       │
       ├── /api/*           ─▶ api-svc (ClusterIP)
       ├── /admin/*         ─▶ admin-svc (ClusterIP)
       └── api.example.com  ─▶ another-svc
       
   하나의 LoadBalancer로 여러 Service
```

### Ingress 객체

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  ingressClassName: nginx
  rules:
    - host: api.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: api-svc
                port:
                  number: 80
    - host: admin.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: admin-svc
                port:
                  number: 80
  tls:
    - hosts:
        - api.example.com
      secretName: api-tls
```

### Ingress Controller 필요

Ingress 객체만으론 동작 X. **Ingress Controller**가 그 객체를 보고 실제로 라우팅:

| Controller | 특징 |
|---|---|
| **ingress-nginx** | 가장 흔함 (k8s 공식) |
| **Traefik** | 모던 UI, 자동 ACME |
| **HAProxy** | 고성능 |
| **AWS LB Controller** | EKS, ALB·NLB |
| **Cilium** | eBPF 기반 |

### Docker Desktop에 ingress-nginx 설치

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.11.2/deploy/static/provider/cloud/deploy.yaml

# 또는 Helm으로
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm install ingress-nginx ingress-nginx/ingress-nginx \
    --namespace ingress-nginx --create-namespace

# 확인
kubectl get pods -n ingress-nginx
kubectl get svc -n ingress-nginx
# LoadBalancer 타입의 ingress-nginx-controller
# Docker Desktop에선 EXTERNAL-IP = localhost
```

### Path 기반 라우팅

```yaml
spec:
  rules:
    - http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: api-svc
                port: { number: 80 }
          - path: /
            pathType: Prefix
            backend:
              service:
                name: web-svc
                port: { number: 80 }
```

`/api/*` → api-svc, 나머지 → web-svc.

### TLS termination

```yaml
spec:
  tls:
    - hosts:
        - api.example.com
      secretName: api-tls           # type: kubernetes.io/tls Secret
```

```bash
kubectl create secret tls api-tls \
    --cert=tls.crt --key=tls.key
```

### cert-manager (자동 인증서)

[cert-manager](https://cert-manager.io/)로 Let's Encrypt 인증서 자동:

```yaml
metadata:
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
    - hosts: [api.example.com]
      secretName: api-tls
```

cert-manager가 자동으로 ACME 챌린지 + Secret 생성·갱신.

---

## Helm

### 왜 Helm?

수십 개 YAML을 매번 환경별로 복사·수정하는 건 끔찍. Helm은:
- **Template** + **Values** = 환경별로 다른 manifest
- Release 관리 (history, rollback)
- Repository (artifact hub)

### Helm Chart 구조

```
my-chart/
├── Chart.yaml                    ← 메타데이터
├── values.yaml                   ← 기본 값
├── values-dev.yaml               ← dev 환경
├── values-prod.yaml              ← prod 환경
├── templates/
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   ├── configmap.yaml
│   ├── _helpers.tpl
│   └── NOTES.txt
└── charts/                       ← 의존 chart (선택)
```

### Chart.yaml

```yaml
apiVersion: v2
name: my-app
description: My Spring Boot App
type: application
version: 0.1.0                     # Chart 버전
appVersion: "1.0.0"                # 앱 버전
```

### values.yaml

```yaml
replicaCount: 2

image:
  repository: ghcr.io/example/my-app
  tag: "1.0.0"
  pullPolicy: IfNotPresent

service:
  type: ClusterIP
  port: 80
  targetPort: 8080

ingress:
  enabled: true
  className: nginx
  host: my-app.local
  tls: false

resources:
  requests:
    cpu: 100m
    memory: 256Mi
  limits:
    cpu: 500m
    memory: 512Mi
```

### templates/deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "my-app.fullname" . }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      app: {{ include "my-app.name" . }}
  template:
    metadata:
      labels:
        app: {{ include "my-app.name" . }}
    spec:
      containers:
        - name: app
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - containerPort: {{ .Values.service.targetPort }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
```

### templates/_helpers.tpl

```yaml
{{- define "my-app.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- define "my-app.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name -}}
{{- end -}}
```

### 명령어

```bash
# 새 chart 생성 (예시 포함)
helm create my-app
# my-app/ 디렉토리 만들어짐

# rendered YAML 확인 (배포 X)
helm template my-app ./my-app
# 또는
helm template my-app ./my-app --values values-dev.yaml

# 배포
helm install my-app ./my-app
helm install my-app ./my-app -f values-dev.yaml

# 업데이트
helm upgrade my-app ./my-app --set image.tag=1.1.0

# install 또는 upgrade (idempotent)
helm upgrade --install my-app ./my-app

# 목록
helm list
helm list -A

# 상태
helm status my-app
helm get values my-app
helm get manifest my-app

# 롤백
helm history my-app
helm rollback my-app 1                # revision 1로

# 삭제
helm uninstall my-app
```

### 환경별 values

```bash
helm install my-app ./my-app -f values-dev.yaml
# 또는
helm install my-app ./my-app -f values-prod.yaml --namespace prod
```

```bash
# CLI override
helm install my-app ./my-app \
    --set replicaCount=5 \
    --set image.tag=1.2.0
```

### 공개 chart 사용

```bash
# repo 추가
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# 검색
helm search repo postgresql

# install
helm install my-db bitnami/postgresql \
    --set auth.postgresPassword=secret

# values 확인
helm show values bitnami/postgresql
```

---

## ingress-nginx 설치·테스트

```bash
# 설치 (Docker Desktop)
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm install ingress-nginx ingress-nginx/ingress-nginx \
    --namespace ingress-nginx --create-namespace

# 확인
kubectl get svc -n ingress-nginx
# ingress-nginx-controller   LoadBalancer   ...   localhost   80:..., 443:...
```

### 테스트 앱

```yaml
# test-app.yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: web }
spec:
  replicas: 2
  selector: { matchLabels: { app: web } }
  template:
    metadata: { labels: { app: web } }
    spec:
      containers:
        - name: nginx
          image: nginx:1.27
---
apiVersion: v1
kind: Service
metadata: { name: web-svc }
spec:
  selector: { app: web }
  ports: [{ port: 80, targetPort: 80 }]
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: web-ing
spec:
  ingressClassName: nginx
  rules:
    - http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: web-svc
                port: { number: 80 }
```

```bash
kubectl apply -f test-app.yaml

# host 헤더 없이도 됨 (Ingress의 hosts 미지정)
curl http://localhost
# nginx 응답
```

---

## 운영 사례

### 사례 1 — Helm upgrade 실패 후 상태 불일치

```bash
helm upgrade my-app ./my-app
# Error: UPGRADE FAILED: ...
```

조치:
```bash
helm history my-app
helm rollback my-app <마지막 정상 revision>
```

### 사례 2 — 같은 chart, 다른 환경에 deploy

```bash
# dev
helm install my-app ./my-app -f values-dev.yaml --namespace dev --create-namespace

# staging
helm install my-app ./my-app -f values-staging.yaml --namespace staging --create-namespace

# prod
helm install my-app ./my-app -f values-prod.yaml --namespace prod --create-namespace
```

각각 다른 image tag·replica·domain·resources. **같은 chart로 환경 일관성**.

### 사례 3 — values 검증

```bash
helm lint ./my-app

# rendered YAML이 valid k8s YAML?
helm template my-app ./my-app | kubectl apply --dry-run=client -f -
```

CI에 두기.

---

## 실습 (Hands-on)

### 1단계 — ingress-nginx 설치

위 helm install 명령.

### 2단계 — 테스트 Ingress

위 test-app.yaml.

### 3단계 — Path 기반 라우팅

```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: api }
spec:
  replicas: 1
  selector: { matchLabels: { app: api } }
  template:
    metadata: { labels: { app: api } }
    spec:
      containers:
        - name: api
          image: hashicorp/http-echo
          args: ["-text=API"]
          ports: [{ containerPort: 5678 }]
---
apiVersion: v1
kind: Service
metadata: { name: api-svc }
spec:
  selector: { app: api }
  ports: [{ port: 80, targetPort: 5678 }]
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata: { name: path-ing }
spec:
  ingressClassName: nginx
  rules:
    - http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service: { name: api-svc, port: { number: 80 } }
          - path: /
            pathType: Prefix
            backend:
              service: { name: web-svc, port: { number: 80 } }
```

```bash
curl http://localhost/        # nginx
curl http://localhost/api/    # API
```

### 4단계 — 첫 Helm chart

```bash
helm create demo
cd demo
ls
# Chart.yaml, values.yaml, templates/
```

```bash
helm template demo .
# 기본 chart의 rendered YAML
```

수정:
```yaml
# values.yaml
replicaCount: 2
image:
  repository: nginx
  tag: "1.27"
```

```bash
helm install demo .
helm list
kubectl get all -l app.kubernetes.io/instance=demo
```

### 5단계 — 환경별 values

```yaml
# values-dev.yaml
replicaCount: 1
service:
  type: NodePort
```

```yaml
# values-prod.yaml
replicaCount: 5
service:
  type: ClusterIP
ingress:
  enabled: true
```

```bash
helm upgrade --install demo . -f values-dev.yaml
helm upgrade --install demo . -f values-prod.yaml
```

### 6단계 — 정리

```bash
helm uninstall demo
helm uninstall ingress-nginx -n ingress-nginx
```

---

## 더 읽어볼 자료

- 🔗 [Ingress 공식](https://kubernetes.io/docs/concepts/services-networking/ingress/)
- 🔗 [ingress-nginx](https://kubernetes.github.io/ingress-nginx/)
- 🔗 [Helm 공식](https://helm.sh/docs/)
- 🔗 [Helm template best practices](https://helm.sh/docs/chart_best_practices/)
- 🔗 [Artifact Hub](https://artifacthub.io/) — chart 검색
- 📘 『Learning Helm』 (Matt Butcher 등, O'Reilly)
