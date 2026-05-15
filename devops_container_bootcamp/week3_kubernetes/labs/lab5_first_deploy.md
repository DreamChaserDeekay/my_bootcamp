# Lab 5 — Spring Boot 앱을 k8s에 배포

## 목표

- Spring Boot 이미지를 빌드·로드
- Deployment + Service + ConfigMap + Secret 작성
- probe·resources 설정
- Ingress로 외부 노출
- 디버깅·로그·port-forward

---

## 1단계 — 이미지 준비

Lab 3에서 만든 `simple:v3` 이미지 사용 (또는 `practice_app/`).

```powershell
docker image ls simple
# simple   v3   ...
```

Docker Desktop의 k8s는 로컬 Docker 이미지를 **그대로 사용**. push 안 해도 됨.

```powershell
kubectl get nodes -o yaml | grep -A 5 images
# 또는
docker exec -it $(docker ps | grep k8s_ | head -1 | awk '{print $1}') crictl images
```

> kind나 다른 환경은 `kind load docker-image simple:v3` 명령 필요.

---

## 2단계 — Deployment

`k8s/01-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web
  labels:
    app: web
spec:
  replicas: 2
  selector:
    matchLabels:
      app: web
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: web
        version: v1
    spec:
      containers:
        - name: app
          image: simple:v3
          imagePullPolicy: IfNotPresent     # 로컬에 있으면 안 pull
          ports:
            - containerPort: 8080
              name: http
          env:
            - name: JAVA_TOOL_OPTIONS
              value: "-XX:MaxRAMPercentage=70 -XX:+UseG1GC"
          resources:
            requests:
              cpu: 200m
              memory: 384Mi
            limits:
              cpu: 1000m
              memory: 512Mi
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            failureThreshold: 30
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            periodSeconds: 5
```

```powershell
kubectl apply -f k8s/01-deployment.yaml

kubectl get pods -w
# 2 replicas 시작 → Running, READY 1/1
```

---

## 3단계 — Service

`k8s/02-service.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: web-svc
spec:
  selector:
    app: web
  ports:
    - port: 80
      targetPort: 8080
      name: http
```

```powershell
kubectl apply -f k8s/02-service.yaml

kubectl get svc
kubectl get endpoints web-svc
# 2개의 Pod IP가 endpoint로
```

테스트:
```powershell
kubectl port-forward svc/web-svc 8080:80
# 다른 터미널
curl http://localhost:8080
```

---

## 4단계 — ConfigMap·Secret

`k8s/03-config.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  SPRING_PROFILES_ACTIVE: prod
  LOGGING_LEVEL_ROOT: INFO
  GREETING: "Hello from k8s"
---
apiVersion: v1
kind: Secret
metadata:
  name: app-secret
type: Opaque
stringData:
  API_KEY: "demo-secret-key-1234"
```

Deployment에 주입:

```yaml
spec:
  template:
    spec:
      containers:
        - name: app
          # ... (위와 동일)
          envFrom:
            - configMapRef:
                name: app-config
          env:
            - name: API_KEY
              valueFrom:
                secretKeyRef:
                  name: app-secret
                  key: API_KEY
```

```powershell
kubectl apply -f k8s/03-config.yaml
kubectl rollout restart deployment/web    # env 반영 위해 재시작
```

확인:
```powershell
kubectl exec -it $(kubectl get pod -l app=web -o name | head -1) -- env | findstr GREETING
# GREETING=Hello from k8s
```

---

## 5단계 — Ingress

```powershell
# ingress-nginx 설치 (Day 4)
helm install ingress-nginx ingress-nginx/ingress-nginx \
    --namespace ingress-nginx --create-namespace

kubectl get svc -n ingress-nginx
# EXTERNAL-IP = localhost (Docker Desktop)
```

`k8s/04-ingress.yaml`:

```yaml
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
                port:
                  number: 80
```

```powershell
kubectl apply -f k8s/04-ingress.yaml
curl http://localhost                     # ingress → svc → pod
```

---

## 6단계 — Rolling Update

```powershell
# 새 이미지 만들기 (코드 변경)
# src에 "v2" 표시
docker build -t simple:v4 .

# Deployment 업데이트
kubectl set image deployment/web app=simple:v4
# 또는 YAML 수정 후 apply

kubectl rollout status deployment/web
kubectl get pods -w
# 새 ReplicaSet 시작·옛 것 종료 (rolling)

kubectl rollout history deployment/web
```

롤백:
```powershell
kubectl rollout undo deployment/web
```

---

## 7단계 — 디버깅 시나리오

### A. probe 실패 시뮬

`livenessProbe.path`를 `/no-such-path`로 변경:

```yaml
livenessProbe:
  httpGet: { path: /no-such-path, port: 8080 }
```

```powershell
kubectl apply -f k8s/01-deployment.yaml
kubectl get pods -w
# RESTARTS 증가 → CrashLoopBackOff

kubectl describe pod web-xxxxx | findstr -A 5 Events
# Liveness probe failed
```

원복.

### B. 자원 부족

`resources.requests.memory: 100Gi`로 변경:

```powershell
kubectl apply -f k8s/01-deployment.yaml
kubectl get pods
# Pending

kubectl describe pod web-xxxxx
# FailedScheduling — Insufficient memory
```

원복.

### C. 이미지 없음

`image: nosuch:v1`:

```powershell
kubectl apply -f k8s/01-deployment.yaml
kubectl get pods
# ErrImagePull → ImagePullBackOff

kubectl describe pod web-xxxxx
# Failed to pull image
```

---

## 8단계 — HPA 추가 (옵션)

metrics-server 활성화 (Day 5).

```yaml
# k8s/05-hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: web-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: web
  minReplicas: 2
  maxReplicas: 5
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 50
```

```powershell
kubectl apply -f k8s/05-hpa.yaml
kubectl get hpa

# 부하 생성
kubectl run -it --rm load --image=busybox --restart=Never -- \
    sh -c "while true; do wget -q -O- http://web-svc; done"

# 다른 터미널
watch kubectl get hpa,pods
# CPU 사용량·replica 증가
```

---

## 9단계 — 정리

```powershell
kubectl delete -f k8s/
kubectl delete namespace ingress-nginx
```

---

## 산출물 체크리스트

- [ ] Spring Boot 이미지를 k8s에 배포
- [ ] Deployment + Service + ConfigMap + Secret
- [ ] probe 3종 설정
- [ ] resources requests·limits
- [ ] Ingress로 외부 노출
- [ ] Rolling update + rollback
- [ ] 디버깅 3시나리오 (probe 실패 / 자원 부족 / 이미지 없음)
- [ ] HPA로 auto-scaling 관찰 (옵션)

---

## 다음 단계

[Lab 6 — Custom Helm Chart](lab6_helm_chart.md)
