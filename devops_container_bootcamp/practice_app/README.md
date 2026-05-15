# practice_app — End-to-End 파이프라인 참조 코드

캡스톤에서 사용할 **완성된 Spring Boot 앱 + 도커라이즈 + k8s manifests + GitHub Actions**.

부트캠프의 모든 주제를 한 곳에 모은 참조.

---

## 구조

```
practice_app/
├── README.md                          ← 지금 이 문서
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── Dockerfile                         ← multi-stage + distroless
├── docker-compose.yml                  ← 로컬 DB와 함께
├── .dockerignore
├── .github/
│   └── workflows/
│       └── ci.yml                      ← CI/CD 파이프라인
├── src/main/
│   ├── java/com/example/devopslab/
│   │   ├── App.java
│   │   └── HelloController.java
│   └── resources/
│       └── application.yml
├── k8s/                                ← 단순 manifest 버전
│   ├── 01-namespace.yaml
│   ├── 02-configmap.yaml
│   ├── 03-secret.yaml
│   ├── 04-deployment.yaml
│   ├── 05-service.yaml
│   ├── 06-ingress.yaml
│   └── 07-hpa.yaml
└── terraform/                          ← GitHub repo 관리 예제
    └── main.tf
```

---

## 빠른 시작

### 1. 로컬에서 (gradle)

```powershell
./gradlew bootRun
# http://localhost:8080
```

### 2. Docker로

```powershell
docker build -t devops-lab:dev .
docker run -p 8080:8080 devops-lab:dev
```

### 3. docker-compose로 (DB 함께)

```powershell
docker compose up -d
```

### 4. k8s에 배포

```powershell
docker build -t devops-lab:k8s .
kubectl apply -f k8s/
kubectl port-forward svc/web-svc -n devops-lab 8080:80
```

### 5. GitHub Actions

repo로 push하면 자동:
- test
- image 빌드·GHCR push
- Trivy 스캔

---

## 학습 흐름 매핑

| 주차 | 이 앱에서 보는 것 |
|---|---|
| Week 1 (Git) | `.github/workflows/ci.yml`이 어떤 trigger·branch·event |
| Week 2 (Docker) | `Dockerfile`, `.dockerignore`, `docker-compose.yml` |
| Week 3 (k8s) | `k8s/` 디렉토리 전체 |
| Week 4 (CI/CD) | `.github/workflows/ci.yml` 풀 파이프라인 |
| Week 4 (IaC) | `terraform/main.tf` |

---

## 파일 한 줄 소개

| 파일 | 학습 포인트 |
|---|---|
| `Dockerfile` | multi-stage + BuildKit + distroless + layered jar |
| `.dockerignore` | 빌드 컨텍스트 최소화 |
| `docker-compose.yml` | 로컬 dev 환경 (앱 + DB) |
| `k8s/04-deployment.yaml` | probes + resources + ConfigMap·Secret 주입 |
| `k8s/06-ingress.yaml` | path 라우팅 |
| `k8s/07-hpa.yaml` | autoscaling |
| `.github/workflows/ci.yml` | test/build/scan/push/deploy 5단계 |
| `terraform/main.tf` | GitHub repo 관리 |

---

## 트러블슈팅

### Docker Desktop k8s가 안 보임

Settings → Kubernetes → Enable Kubernetes 체크.

### Image pull 안 됨

로컬 이미지 사용 시 `imagePullPolicy: IfNotPresent` 또는 `Never`.

```powershell
docker build -t devops-lab:k8s .       # 로컬 빌드
kubectl get pods -n devops-lab          # 이미지 받기 안 함, 로컬 사용
```

### Ingress가 작동 안 함

ingress-nginx 설치 여부 확인:
```powershell
kubectl get pods -n ingress-nginx
```

없으면 Week 3 Day 4 참조.
