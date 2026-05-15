# Day 5 — Probe · HPA · 디버깅

## 한 줄 요약

**Probe**(liveness/readiness/startup)로 Pod의 건강을 체크하고 자동 복구·트래픽 분리. **HPA**로 CPU·메모리·custom 메트릭에 따라 replica 자동 조절. 디버깅 도구: logs / describe / exec / port-forward / ephemeral container.

## 학습 목표

- [ ] 3가지 probe와 각각의 역할
- [ ] resource requests vs limits
- [ ] HPA 동작·튜닝
- [ ] Pod 디버깅 5단계
- [ ] kubectl debug (ephemeral container)
- [ ] events 활용

---

## Probe 3종

### liveness — "살아있나?"

실패 시 → 컨테이너 **재시작**.

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30        # Pod 시작 후 N초 대기
  periodSeconds: 10              # N초마다
  timeoutSeconds: 3              # N초 안에 응답 없으면 실패
  failureThreshold: 3            # 연속 N번 실패 시 액션
```

> Spring Boot 3.x는 `/actuator/health/liveness` 기본 제공 (probes enabled 시).

### readiness — "트래픽 받을 준비됐나?"

실패 시 → Service의 endpoints에서 **제외** (트래픽 차단). 컨테이너는 안 죽임.

```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 5
  failureThreshold: 3
```

`/actuator/health/readiness`는 의존성(DB·Redis) 검사도 포함 권장.

### startup — "초기화 끝났나?"

오래 걸리는 시작(JVM·Spring Boot)에. **startup이 성공할 때까지 liveness/readiness 무시**:

```yaml
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 30           # 30 × 5초 = 150초 동안 시작 기회
```

대안: liveness에 `initialDelaySeconds: 60` 등 큰 값 — 그러나 startup probe가 더 명시적.

### 종류

```yaml
# HTTP
livenessProbe:
  httpGet:
    path: /health
    port: 8080
    httpHeaders:
      - name: Custom
        value: Header

# TCP
livenessProbe:
  tcpSocket:
    port: 8080

# Exec
livenessProbe:
  exec:
    command: ["pg_isready", "-U", "app"]
```

### Spring Boot 3.x probe

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true              # /health/liveness, /health/readiness 노출
      show-details: never           # 운영 보안
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

```yaml
# k8s manifest
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8080 }
startupProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  failureThreshold: 30
  periodSeconds: 5
```

---

## Resources — requests vs limits

```yaml
resources:
  requests:
    cpu: 100m                       # 0.1 코어
    memory: 256Mi
  limits:
    cpu: 500m                       # 0.5 코어
    memory: 512Mi
```

| | 의미 |
|---|---|
| **requests** | 스케줄링 기준. 이 자원이 보장됨 |
| **limits** | 이 이상 못 씀. CPU는 throttle, 메모리는 OOMKill |

### CPU

```yaml
cpu: 100m     # 100 milli-cores = 0.1 코어
cpu: 1        # 1 코어
cpu: 1500m    # 1.5 코어
```

CPU limit 초과 시 **throttle** (느려짐, 안 죽음).

### 메모리

```yaml
memory: 256Mi
memory: 1Gi
memory: 1024Mi
```

메모리 limit 초과 시 **OOMKilled** (컨테이너 죽음).

### QoS Class

requests·limits 조합으로 결정:

| QoS | 조건 | 우선순위 (eviction) |
|---|---|---|
| **Guaranteed** | requests == limits (모든 자원) | 마지막에 evict |
| **Burstable** | requests < limits (일부) | 중간 |
| **BestEffort** | requests·limits 없음 | 가장 먼저 evict |

**운영은 Burstable 또는 Guaranteed**. BestEffort는 위험.

### JVM 컨테이너 권장

```yaml
# k8s
resources:
  requests:
    memory: 1Gi
    cpu: 500m
  limits:
    memory: 1Gi             # Guaranteed (메모리는 같게)
    cpu: 1500m              # CPU burst 허용

env:
  - name: JAVA_TOOL_OPTIONS
    value: "-XX:MaxRAMPercentage=70 -XX:+UseG1GC"
# Heap이 컨테이너의 70% (700MB), 나머지 300MB는 Metaspace·stack·native
```

---

## HPA (Horizontal Pod Autoscaler)

CPU·메모리·custom 메트릭에 따라 replica 자동 조절.

### 전제 — metrics-server

```bash
# Docker Desktop엔 보통 없음
kubectl top nodes
# error: Metrics API not available

# 설치
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Docker Desktop은 self-signed 인증서 이슈 → patch 필요
kubectl patch deployment metrics-server -n kube-system --type='json' \
    -p='[{"op": "add", "path": "/spec/template/spec/containers/0/args/-", "value": "--kubelet-insecure-tls"}]'

kubectl top pods
# OK면 성공
```

### HPA 정의

```yaml
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
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70      # CPU 70% 넘으면 scale up
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

### Scale 알고리즘

```
desiredReplicas = currentReplicas * (currentMetric / targetMetric)
```

예: 3 replica, CPU 사용 평균 90%, target 70% → 3 × (90/70) ≈ 4 replica로.

### 안정성 옵션

```yaml
behavior:
  scaleUp:
    stabilizationWindowSeconds: 0     # 즉시
    policies:
      - type: Percent
        value: 100                     # 한 번에 2배까지
        periodSeconds: 60
  scaleDown:
    stabilizationWindowSeconds: 300    # 5분간 안정 후 down
    policies:
      - type: Percent
        value: 50
        periodSeconds: 60
```

> 빠르게 up, 천천히 down — flapping 방지.

### 한계

- 메트릭 기반 (응답 시간 직접 X — custom metric 필요)
- 의존성 (DB)을 함께 scale 안 함
- 갑작스런 spike엔 늦음 → over-provision 또는 사전 scaling

### KEDA (참고)

Kafka offset·SQS queue depth 같은 **외부 메트릭**으로 scale. CNCF.

---

## 디버깅 — 5단계

### 1. logs

```bash
kubectl logs <pod>
kubectl logs <pod> -c <container>      # multi-container
kubectl logs -f <pod>                  # follow
kubectl logs --previous <pod>          # 죽은 컨테이너 로그
kubectl logs --tail=100 <pod>
kubectl logs -l app=web                # label로 (여러 Pod 합쳐)
kubectl logs --since=1h <pod>
```

### 2. describe

```bash
kubectl describe pod <pod>
```

특히 **Events** 섹션:
```
Events:
  Type     Reason       Age   From               Message
  Normal   Scheduled    1m    default-scheduler  Successfully assigned ...
  Normal   Pulling      1m    kubelet            Pulling image "..."
  Warning  Failed       30s   kubelet            Error: ImagePullBackOff
```

### 3. exec

```bash
kubectl exec -it <pod> -- bash
kubectl exec -it <pod> -c <container> -- sh

# 안에서
env
ls /app
cat /etc/config/...
curl localhost:8080/actuator/health
nslookup db                            # DNS 확인
```

distroless 이미지엔 shell 없음 → `kubectl debug`.

### 4. port-forward

로컬에서 Pod·Service에 직접 접근:

```bash
kubectl port-forward pod/myapp 8080:8080
kubectl port-forward svc/myapp 8080:80

# 안 쓰는 포트 자동
kubectl port-forward svc/myapp :80
# Forwarding from 127.0.0.1:54321 -> 80
```

### 5. ephemeral container (kubectl debug)

distroless·minimal 이미지에서 디버그용 컨테이너 추가:

```bash
kubectl debug -it <pod> --image=busybox --target=<container-name>
# Pod 안에 새 busybox 컨테이너 attach
# 같은 namespace 공유 → 파일·프로세스·네트워크 보임
```

```bash
# alpine + 도구
kubectl debug -it <pod> --image=nicolaka/netshoot --target=app
# netshoot: 네트워크 진단 도구 모음 (dig, tcpdump, etc)
```

---

## 흔한 Pod 상태

| Status | 의미 | 조치 |
|---|---|---|
| Pending | 스케줄링 대기 | describe로 events 확인 |
| ContainerCreating | 이미지 pull 중 | 큰 이미지·private registry 인증 확인 |
| Running | 정상 | - |
| CrashLoopBackOff | 반복 종료 | logs --previous |
| ImagePullBackOff | 이미지 pull 실패 | secret·registry·이미지명 확인 |
| ErrImagePull | 이미지 못 받음 | 위와 동일 |
| OOMKilled | 메모리 초과 | limits 늘리거나 누수 점검 |
| Completed | Job 정상 종료 | - |
| Error | 비정상 종료 | logs --previous |

---

## kubectl 디버그 명령 모음

```bash
# 클러스터 자원 사용
kubectl top nodes
kubectl top pods
kubectl top pods -A --sort-by=memory

# 동작 중인 Pod의 Service endpoint
kubectl get endpoints <svc>

# Service 매핑이 잘못됐나
kubectl get svc <svc> -o yaml | grep selector -A 2
kubectl get pods --show-labels

# 모든 자원 한 번에
kubectl get all -n <ns>

# 이벤트만 시간순
kubectl get events --sort-by=.lastTimestamp -A

# YAML로 (변경 후 apply 전 확인)
kubectl get deploy <name> -o yaml > current.yaml

# DNS 디버그
kubectl run -it --rm debug --image=busybox --restart=Never -- nslookup myservice
```

---

## 운영 사례

### 사례 1 — readiness 미설정 → 배포 중 5xx

새 Pod 시작 → 아직 Spring init 안 끝남 → Service가 트래픽 보냄 → 5xx.

조치: readiness probe 설정. 또는 startup probe로 초기화 시간 보장.

### 사례 2 — HPA가 동작 안 함

```bash
kubectl get hpa
# NAME      TARGETS         MINPODS   MAXPODS   REPLICAS
# web-hpa   <unknown>/70%   2         10        2
```

`<unknown>` → metrics-server 동작 X 또는 Pod에 resource requests 없음.

조치: metrics-server 확인 + Deployment에 `resources.requests` 설정.

### 사례 3 — OOMKilled 반복

```bash
kubectl describe pod web-xyz
# Last State:    Terminated
#   Reason:      OOMKilled
#   Exit Code:   137
```

조치:
- 일단 메모리 limit 늘리기
- Heap dump 받아서 누수 분석 (JVM 부트캠프 참조)
- `-XX:+HeapDumpOnOutOfMemoryError` + emptyDir/PVC

---

## 실습 (Hands-on)

### 1단계 — probe 있는 Deployment

```yaml
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
          ports: [{ containerPort: 80 }]
          livenessProbe:
            httpGet: { path: /, port: 80 }
            initialDelaySeconds: 10
            periodSeconds: 10
          readinessProbe:
            httpGet: { path: /, port: 80 }
            initialDelaySeconds: 5
            periodSeconds: 5
          resources:
            requests: { cpu: 100m, memory: 64Mi }
            limits:   { cpu: 200m, memory: 128Mi }
```

```bash
kubectl apply -f web.yaml
kubectl get pods -w
# READY 1/1 = readiness 통과
```

### 2단계 — probe 실패 시뮬레이션

```bash
# Pod 안에서 nginx 죽임
kubectl exec -it web-xyz -- sh
$ nginx -s stop

# 다른 터미널
kubectl get pods -w
# liveness 실패 → 컨테이너 재시작 (RESTARTS +1)
```

### 3단계 — HPA

metrics-server 확인 후:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata: { name: web-hpa }
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

```bash
kubectl apply -f hpa.yaml
kubectl get hpa
```

부하 생성:
```bash
kubectl run -it --rm load --image=busybox --restart=Never -- \
    sh -c "while true; do wget -q -O- http://web-svc; done"
```

다른 터미널:
```bash
watch kubectl get hpa,pods
# CPU 사용량 증가 → replica 증가
```

### 4단계 — 디버깅

CrashLoopBackOff 시뮬레이션:

```yaml
apiVersion: v1
kind: Pod
metadata: { name: crash }
spec:
  containers:
    - name: bad
      image: alpine
      command: ["sh", "-c", "exit 1"]   # 즉시 종료
```

```bash
kubectl apply -f crash.yaml
kubectl get pods
# crash   0/1   CrashLoopBackOff

kubectl describe pod crash
# Events: container exited with code 1

kubectl logs crash
kubectl logs --previous crash
```

### 5단계 — ephemeral container

distroless Pod에:

```yaml
apiVersion: v1
kind: Pod
metadata: { name: distroless-pod }
spec:
  containers:
    - name: app
      image: gcr.io/distroless/java21-debian12
      command: ["java", "-version"]
```

```bash
kubectl apply -f distroless-pod.yaml
# Pod 시작·종료 빠름. 살아있는 동안:

kubectl debug -it distroless-pod --image=busybox --target=app
# busybox shell이 같은 PID·network namespace에 attach
# ls /proc/1/root      # 다른 컨테이너의 파일시스템
```

---

## 더 읽어볼 자료

- 🔗 [Probes 공식](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#container-probes)
- 🔗 [HPA 공식](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)
- 🔗 [Resource Management](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)
- 🔗 [Debug Pods](https://kubernetes.io/docs/tasks/debug/debug-application/)
- 🔗 [Ephemeral Containers (kubectl debug)](https://kubernetes.io/docs/concepts/workloads/pods/ephemeral-containers/)
- 🔗 [netshoot](https://github.com/nicolaka/netshoot) — 디버그 컨테이너 이미지
