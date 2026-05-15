# Day 5 — 컨테이너·Kubernetes·Linux 하드닝

## 1. Docker 보안 기본

### 1.1 컨테이너는 VM이 아니다
- 호스트 커널 공유
- 보안 모델: namespace + cgroup + capabilities + seccomp + AppArmor/SELinux
- 컨테이너 탈출(escape) 시 호스트 전체 위협

### 1.2 이미지 보안

#### 베이스 이미지
- `latest` 태그 ❌ — 빌드마다 다른 결과
- 작은 이미지 (`alpine`, `distroless`) — 공격 표면 감소
- Verified Publisher (Docker Hub)
- 가능하면 `scratch` 또는 `distroless` 위에 직접 빌드

```dockerfile
# 좋음 - distroless
FROM eclipse-temurin:17-jdk AS build
COPY . /src
WORKDIR /src
RUN ./gradlew bootJar

FROM gcr.io/distroless/java17-debian12
COPY --from=build /src/build/libs/*.jar /app.jar
USER 65532:65532
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

#### 이미지 스캔
- **Trivy** (Aqua, 무료, 강력)
- **Grype** (Anchore)
- **Snyk Container**
- **Docker Scout**
- 클라우드: ECR scanning, GCR Container Analysis

```bash
trivy image myapp:1.0.0
trivy image --severity HIGH,CRITICAL myapp:1.0.0
```

#### 서명·검증
- **Cosign / Sigstore** — 이미지 서명, 무료
- **Notary v2**

### 1.3 런타임 보안

#### Dockerfile 모범 사례
```dockerfile
FROM eclipse-temurin:17-jdk-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --chown=app:app app.jar .
USER app                          # ← root 금지
EXPOSE 8080
HEALTHCHECK CMD wget -q -O - http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-jar","app.jar"]
```

#### `docker run` 옵션
```bash
docker run -d \
  --read-only \                    # 파일시스템 읽기 전용
  --tmpfs /tmp \                   # 쓰기 가능 영역만 tmpfs
  --cap-drop=ALL \                 # 모든 capability 제거
  --cap-add=NET_BIND_SERVICE \     # 필요한 것만
  --security-opt no-new-privileges \  # 권한 상승 차단
  --pids-limit 200 \               # 프로세스 수 제한
  --memory 512m --cpus 1 \         # 자원 제한
  --network app-net \              # 격리 네트워크
  -u 1000:1000 \                   # 비-root
  myapp:1.0.0
```

#### 절대 안 됨
- `--privileged` (도커 안에서 호스트 자원 무제한)
- `-v /:/host` 호스트 루트 마운트
- 호스트 네트워크 (`--net=host`) 컨테이너를 공개 서비스로 (격리 무력화)
- `docker.sock` 컨테이너 안에 마운트 (root 같은 권한)

### 1.4 비밀 처리
- **환경변수에 비밀 X** (`docker inspect`에 노출)
- BuildKit secret mount
  ```dockerfile
  RUN --mount=type=secret,id=npmrc cp /run/secrets/npmrc ~/.npmrc && npm install
  ```
- Docker Secret / Kubernetes Secret / Vault

---

## 2. Kubernetes 보안

### 2.1 위협 면 (Threat Surface)
- **API Server** — kubectl·controller·webhook이 모두 거치는 중앙
- **etcd** — 모든 상태·시크릿 보관 (암호화 안 되면 평문)
- **kubelet** — 노드 에이전트
- **컨테이너 런타임** — containerd, CRI-O
- **워크로드** — 앱 컨테이너
- **노드** — VM/머신
- **이미지 레지스트리**

### 2.2 RBAC 최소 권한
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: app
  name: app-reader
rules:
- apiGroups: [""]
  resources: ["pods", "services"]
  verbs: ["get", "list"]
```
- ClusterAdmin 분배 안 함
- 기본 ServiceAccount에 권한 부여 X
- 워크로드별 별도 SA + 최소 RBAC

### 2.3 Pod Security Standards
3 단계: Privileged / Baseline / **Restricted**.

Restricted 권장:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: app
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/enforce-version: latest
```

### 2.4 Pod 보안 설정
```yaml
spec:
  automountServiceAccountToken: false       # 기본 SA 토큰 마운트 거부
  securityContext:
    runAsNonRoot: true
    runAsUser: 10000
    fsGroup: 10000
    seccompProfile:
      type: RuntimeDefault
  containers:
  - name: app
    image: myapp:1.0.0
    securityContext:
      allowPrivilegeEscalation: false
      readOnlyRootFilesystem: true
      capabilities:
        drop: ["ALL"]
    resources:
      limits:
        cpu: 500m
        memory: 512Mi
      requests:
        cpu: 100m
        memory: 256Mi
```

### 2.5 NetworkPolicy
기본은 모든 Pod가 모든 Pod와 통신 가능. 명시적 격리 필요.
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: deny-all
  namespace: app
spec:
  podSelector: {}
  policyTypes: ["Ingress", "Egress"]
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-db-from-app
spec:
  podSelector:
    matchLabels: { app: postgres }
  ingress:
  - from:
    - podSelector:
        matchLabels: { app: backend }
    ports:
    - port: 5432
```

### 2.6 Secret 관리
K8s Secret은 기본 **base64만**. 평문이나 마찬가지.
- **etcd encryption at rest** ON
- **External Secrets Operator** + Vault / AWS Secrets Manager
- **Sealed Secrets** (Bitnami) — git에 암호화 보관
- **SOPS** + age/kms

### 2.7 도구
- **kube-bench** — CIS Benchmark 평가
- **kubescape**, **kube-hunter** — 취약점 탐지
- **Falco** — 런타임 행위 탐지 (eBPF)
- **Trivy operator** — 클러스터 스캔
- **OPA Gatekeeper / Kyverno** — 정책 강제
- **Polaris** — 베스트 프랙티스 점검

### 2.8 Admission Controllers
이미지 정책, Pod Security, NetworkPolicy 강제 등을 **클러스터 진입 시점**에 검사.

---

## 3. Linux 호스트 하드닝

### 3.1 사용자·SSH
- root 로그인 금지
- 키 인증, 비밀번호 X
- sudoers 최소
- `auditd` 활성

### 3.2 패키지·패치
- `unattended-upgrades` (Debian) 또는 `dnf-automatic` (RHEL)
- 정기 점검·CVE 모니터링

### 3.3 방화벽
- `ufw` 또는 `firewalld`
- 기본 deny inbound, allow outbound

### 3.4 커널 강화
- `sysctl` (TCP SYN cookies, `net.ipv4.conf.all.rp_filter=1`)
- AppArmor / SELinux Enforcing
- `kernel.dmesg_restrict=1`

### 3.5 도구
- **Lynis** — Linux 보안 audit
  ```bash
  sudo lynis audit system
  ```
- **OpenSCAP** — 표준 기반 평가
- **CIS Benchmarks**

---

## 4. 실제 사례

### Docker Hub Tesla (2018)
Tesla의 K8s 콘솔이 인증 없이 노출 → 안에서 발견된 AWS 자격증명 → 클라우드에서 크립토 마이닝.

### Capital One (재언급)
WAF 컨테이너의 잘못된 IAM이 핵심.

### Confluence Cloud (다수 CVE)
컨테이너 escape, Privilege Escalation 사례 다수.

### Argo CD CVE-2022-24348
디렉토리 트래버설 — 다른 앱의 자원 접근.

---

## 5. 실습

### 실습 5.1 — vulnerable_app 이미지 스캔
```bash
# vulnerable_app을 docker build
cd security_bootcamp/vulnerable_app
docker build -t vuln-app:dev .

# Trivy 스캔
trivy image vuln-app:dev
```
결과의 HIGH/CRITICAL 항목 확인.

### 실습 5.2 — 비-root, distroless로 재빌드
같은 vulnerable_app을 distroless + non-root로 재빌드해서 스캔 차이 비교.

### 실습 5.3 — Docker 권한 회수 실행
```bash
docker run --rm --read-only --cap-drop=ALL --security-opt no-new-privileges \
  -u 1000:1000 vuln-app:dev
```
앱이 정상 동작하는지 확인. 안 되면 어떤 권한이 필요한지 분석.

### 실습 5.4 — K8s 로컬 (minikube/kind) — Restricted PSS
```bash
kind create cluster
kubectl create ns app
kubectl label ns app pod-security.kubernetes.io/enforce=restricted
# vulnerable_app 배포 → 거부 → 이유 분석
```

### 실습 5.5 — kube-bench
```bash
docker run --rm --pid=host -v /etc:/etc:ro -v /var:/var:ro \
  aquasec/kube-bench:latest --version 1.27
```
점수와 미해결 항목 정리.

---

## Week 3 정리
- 네트워크·인프라 보안은 **설정의 합** — 정기 audit이 핵심
- 컨테이너는 **최소 권한·비-root·읽기 전용** 기본
- K8s는 PSS Restricted + NetworkPolicy + RBAC + Secret management 4가지가 베이스라인
- 클라우드는 IAM이 곧 보안 경계
