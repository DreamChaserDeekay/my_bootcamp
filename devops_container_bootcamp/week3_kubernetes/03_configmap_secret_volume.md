# Day 3 — ConfigMap · Secret · Volume

## 한 줄 요약

설정은 **ConfigMap**, 시크릿은 **Secret**, 데이터는 **Volume**. 12-Factor App의 "Config 외부화" 원칙. ConfigMap·Secret을 env로 주입하거나 volume으로 마운트.

## 학습 목표

- [ ] ConfigMap 작성·주입 (env / volume)
- [ ] Secret 종류 (Opaque, TLS, docker registry)
- [ ] Secret이 평문인 문제와 해결책 (sealed secrets, external secrets)
- [ ] emptyDir·hostPath·PersistentVolume 차이
- [ ] PVC bind 과정
- [ ] StatefulSet 기본 개념

---

## ConfigMap

설정값을 Pod 밖으로.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  SPRING_PROFILES_ACTIVE: prod
  LOG_LEVEL: INFO
  application.yml: |
    server:
      port: 8080
    spring:
      datasource:
        url: jdbc:postgresql://db:5432/labdb
```

### env로 주입

```yaml
spec:
  containers:
    - name: app
      image: my-app:1.0
      envFrom:
        - configMapRef:
            name: app-config
      # 또는 개별로
      env:
        - name: SPRING_PROFILES_ACTIVE
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: SPRING_PROFILES_ACTIVE
```

### Volume으로 마운트 (파일 형태)

```yaml
spec:
  containers:
    - name: app
      volumeMounts:
        - name: config
          mountPath: /etc/app
  volumes:
    - name: config
      configMap:
        name: app-config
        items:
          - key: application.yml
            path: application.yml
```

→ `/etc/app/application.yml`로 파일이 보임.

### 명령으로 생성

```bash
# literal
kubectl create configmap app-config --from-literal=DB_USER=app

# 파일에서
kubectl create configmap app-config --from-file=application.yml

# 여러 파일
kubectl create configmap app-config --from-file=conf/
```

### ConfigMap 변경 시 Pod 반영?

| 주입 방식 | 자동 반영? |
|---|---|
| env | ❌ — 새 Pod 시작 필요 |
| Volume mount (파일) | ✅ — 약 1분 후 (kubelet 주기) |

env로 주입한 경우 Pod 재시작 필요:
```bash
kubectl rollout restart deployment/web
```

---

## Secret

ConfigMap과 형식 같으나 **민감 정보**용. base64 인코딩 (암호화 X, 그냥 인코딩!).

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
type: Opaque
data:
  DB_USER: YXBw                       # echo -n "app" | base64
  DB_PASSWORD: c2VjcmV0               # base64
```

또는 stringData (k8s가 자동 인코딩):

```yaml
stringData:
  DB_USER: app
  DB_PASSWORD: secret
```

### 종류

| Type | 용도 |
|---|---|
| `Opaque` | 일반 (기본) |
| `kubernetes.io/tls` | TLS 인증서·키 |
| `kubernetes.io/dockerconfigjson` | private registry 인증 |
| `kubernetes.io/service-account-token` | SA 토큰 |
| `kubernetes.io/basic-auth` | username·password |

### 사용

```yaml
spec:
  containers:
    - name: app
      env:
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: DB_PASSWORD
```

### Secret이 진짜 secret인가?

```bash
kubectl get secret db-secret -o yaml
# data:
#   DB_PASSWORD: c2VjcmV0       ← base64, 누구나 디코드
```

base64는 **인코딩이지 암호화 아님**. **etcd에 평문 저장** (etcd 암호화 옵션 켜야 진짜 암호화).

### 실전 보안

| 방법 | 설명 |
|---|---|
| **etcd 암호화 at rest** | k8s 설정 (운영자 영역) |
| **RBAC** | Secret 접근 권한 제한 |
| **Sealed Secrets** (Bitnami) | 공개 키로 암호화한 SealedSecret을 git에 commit 가능 |
| **External Secrets Operator** | Vault·AWS Secrets Manager에서 secret 가져와 k8s Secret 생성 |
| **SOPS** (Mozilla) | git에 암호화된 YAML 저장 |
| **Vault** | HashiCorp Vault |

**git에 Secret YAML 직접 commit 절대 금지**.

---

## Volume

Pod 안의 컨테이너끼리 데이터 공유, 또는 영속화.

### emptyDir — Pod 생애주기

```yaml
spec:
  containers:
    - name: writer
      image: alpine
      command: ['sh', '-c', 'echo hello > /data/msg; sleep 3600']
      volumeMounts:
        - { name: shared, mountPath: /data }
    - name: reader
      image: alpine
      command: ['sh', '-c', 'cat /data/msg; sleep 3600']
      volumeMounts:
        - { name: shared, mountPath: /data }
  volumes:
    - name: shared
      emptyDir: {}
```

- Pod 시작 시 빈 디렉토리
- 컨테이너끼리 공유
- Pod 죽으면 사라짐

옵션:
```yaml
emptyDir:
  medium: Memory                       # tmpfs (휘발 더 빠름)
  sizeLimit: 100Mi
```

### hostPath — Node의 디렉토리

```yaml
volumes:
  - name: host-data
    hostPath:
      path: /data
      type: DirectoryOrCreate
```

**위험** — Pod이 host에 접근. 운영서엔 피하기 (보안). 개발용.

### PersistentVolume (PV) + PersistentVolumeClaim (PVC)

```
   PV (admin이 만든 storage)
       ▲
       │ binding
       │
   PVC (사용자가 요청)
       ▲
       │ mounted by
       │
   Pod
```

#### PVC

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: db-data
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
  storageClassName: standard            # 기본 클래스
```

k8s가 자동으로 PV 생성·bind (dynamic provisioning, StorageClass 있을 때).

#### Pod에서 사용

```yaml
spec:
  containers:
    - name: db
      image: postgres:16
      volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
  volumes:
    - name: data
      persistentVolumeClaim:
        claimName: db-data
```

PVC bound 후 Pod 재시작해도 데이터 유지.

### accessModes

| Mode | 의미 |
|---|---|
| `ReadWriteOnce` (RWO) | 한 node에서만 read/write |
| `ReadOnlyMany` (ROX) | 여러 node에서 read |
| `ReadWriteMany` (RWX) | 여러 node에서 read/write (NFS 등) |
| `ReadWriteOncePod` (RWOP) | 한 Pod만 |

대부분 RWO. RWX는 special storage 필요.

---

## StatefulSet (간단)

Deployment는 **stateless**. DB·메시지큐처럼 **stateful**한 워크로드는 StatefulSet:

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: db
spec:
  serviceName: db                       # Headless Service 필요
  replicas: 3
  selector:
    matchLabels:
      app: db
  template:
    metadata:
      labels:
        app: db
    spec:
      containers:
        - name: postgres
          image: postgres:16
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: [ReadWriteOnce]
        resources:
          requests:
            storage: 10Gi
```

특징:
- Pod 이름이 **순차 + 안정**: db-0, db-1, db-2 (안 바뀜)
- 각 Pod이 **자기만의 PVC** (db-data-db-0, db-data-db-1...)
- 시작 순서 보장 (0 → 1 → 2)
- 종료 역순 (2 → 1 → 0)
- DNS: `db-0.db.default.svc.cluster.local`

> DB·Kafka·ZK 등 stateful 워크로드. 그러나 **운영 DB는 매니지드 서비스(RDS·Cloud SQL)** 권장. StatefulSet은 학습/Dev에.

---

## DaemonSet (참고)

모든 (또는 일부) Node에 **각 1개씩** Pod. 로그 수집·모니터링 에이전트용.

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluentbit
spec:
  selector:
    matchLabels:
      app: fluentbit
  template:
    metadata:
      labels:
        app: fluentbit
    spec:
      containers:
        - name: fluentbit
          image: fluent/fluent-bit:3.0
          volumeMounts:
            - name: varlog
              mountPath: /var/log
              readOnly: true
      volumes:
        - name: varlog
          hostPath:
            path: /var/log
```

---

## 운영 사례

### 사례 1 — Secret 평문이 git에

```yaml
# ❌ git에 commit
apiVersion: v1
kind: Secret
data:
  DB_PASSWORD: c2VjcmV0
```

조치:
1. 즉시 password 변경
2. git history에서 제거 (filter-repo)
3. Sealed Secrets·SOPS·External Secrets로 마이그레이션

### 사례 2 — ConfigMap 바꿨는데 반영 안 됨

env로 주입한 경우 Pod 재시작 필요:
```bash
kubectl rollout restart deployment/web
```

Volume mount면 ~1분 후 자동, 그러나 앱이 파일을 다시 읽어야 함 (Spring Cloud Config나 자체 watcher).

### 사례 3 — PVC가 Bound 안 됨

```bash
kubectl describe pvc my-pvc
# Events:
#   Warning  ProvisioningFailed   storageclass.storage.k8s.io "fast" not found
```

원인: 없는 StorageClass 요청. 기본 사용:
```yaml
spec:
  storageClassName: ""                  # 또는 생략 (default 사용)
```

---

## 실습 (Hands-on)

### 1단계 — ConfigMap

```yaml
# app-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  GREETING: "Hello from ConfigMap"
  LOG_LEVEL: "DEBUG"
```

```yaml
# app.yaml
apiVersion: v1
kind: Pod
metadata:
  name: configdemo
spec:
  containers:
    - name: app
      image: alpine
      command: ['sh', '-c', 'echo $GREETING; echo "Level: $LOG_LEVEL"; sleep 3600']
      envFrom:
        - configMapRef:
            name: app-config
```

```bash
kubectl apply -f app-config.yaml
kubectl apply -f app.yaml
kubectl logs configdemo
# Hello from ConfigMap
# Level: DEBUG
```

### 2단계 — Secret

```bash
kubectl create secret generic db-secret \
    --from-literal=USER=app \
    --from-literal=PASS=secret123

# 확인 (base64 노출)
kubectl get secret db-secret -o yaml
```

Pod에서:
```yaml
spec:
  containers:
    - name: app
      env:
        - name: DB_USER
          valueFrom:
            secretKeyRef: { name: db-secret, key: USER }
        - name: DB_PASS
          valueFrom:
            secretKeyRef: { name: db-secret, key: PASS }
```

### 3단계 — ConfigMap을 Volume으로

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: nginx-conf
data:
  default.conf: |
    server {
      listen 80;
      location / {
        return 200 "Hello from k8s\n";
      }
    }
---
apiVersion: v1
kind: Pod
metadata:
  name: nginx-cm
spec:
  containers:
    - name: nginx
      image: nginx:1.27
      volumeMounts:
        - name: config
          mountPath: /etc/nginx/conf.d
  volumes:
    - name: config
      configMap:
        name: nginx-conf
```

```bash
kubectl port-forward pod/nginx-cm 8080:80
curl http://localhost:8080
# Hello from k8s
```

ConfigMap 수정:
```bash
kubectl edit configmap nginx-conf
# return 200 → return 201로 변경
```

~1분 후 nginx 리로드 또는:
```bash
kubectl exec nginx-cm -- nginx -s reload
```

### 4단계 — emptyDir 공유

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: share
spec:
  containers:
    - name: writer
      image: alpine
      command: ['sh', '-c', 'while true; do echo "$(date)" >> /data/log.txt; sleep 5; done']
      volumeMounts:
        - { name: shared, mountPath: /data }
    - name: reader
      image: alpine
      command: ['sh', '-c', 'tail -f /data/log.txt']
      volumeMounts:
        - { name: shared, mountPath: /data }
  volumes:
    - name: shared
      emptyDir: {}
```

```bash
kubectl logs share -c reader -f
# 5초마다 새 줄
```

### 5단계 — PVC

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: data
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 1Gi
---
apiVersion: v1
kind: Pod
metadata:
  name: pvc-demo
spec:
  containers:
    - name: app
      image: alpine
      command: ['sh', '-c', 'echo persistent > /data/file; sleep 3600']
      volumeMounts:
        - { name: vol, mountPath: /data }
  volumes:
    - name: vol
      persistentVolumeClaim:
        claimName: data
```

```bash
kubectl get pvc
kubectl get pv
kubectl exec pvc-demo -- cat /data/file
# persistent

# Pod 죽임
kubectl delete pod pvc-demo
kubectl apply -f pvc-demo.yaml
kubectl exec pvc-demo -- cat /data/file
# persistent (유지됨)
```

---

## 더 읽어볼 자료

- 🔗 [ConfigMaps](https://kubernetes.io/docs/concepts/configuration/configmap/)
- 🔗 [Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
- 🔗 [Volumes](https://kubernetes.io/docs/concepts/storage/volumes/)
- 🔗 [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets)
- 🔗 [External Secrets Operator](https://external-secrets.io/)
- 🔗 [SOPS](https://github.com/getsops/sops)
- 📘 『Kubernetes Patterns』 (Ibryam, Huss)
