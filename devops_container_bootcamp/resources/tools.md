# 도구 (Tools)

## 1. Git

| 도구 | 용도 |
|---|---|
| **git** | 기본 |
| **gh** (GitHub CLI) | GitHub 작업 (PR·issue·workflow) |
| **glab** | GitLab CLI |
| **lazygit** | TUI Git 클라이언트 |
| **GitKraken** | GUI |
| **Sourcetree** | GUI (Atlassian) |
| **GitLens** | VS Code 확장 |
| **commitlint** | conventional commit 강제 |
| **husky** | git hooks 관리 |
| **pre-commit** | hook framework (Python) |
| **git-filter-repo** | history 재작성 |
| **BFG Repo-Cleaner** | 큰 파일·민감 정보 제거 |
| **Sealed Secrets** | git에 안전한 secret 저장 |

## 2. Docker·컨테이너

| 도구 | 용도 |
|---|---|
| **Docker Desktop** | Mac/Windows 표준 |
| **Rancher Desktop** | 무료 대안 |
| **Colima** | Mac 가벼운 대안 |
| **Podman** | rootless 컨테이너 (Red Hat) |
| **buildah** | 이미지 빌드 (rootless) |
| **nerdctl** | containerd CLI |
| **dive** | 이미지 layer 분석 |
| **lazydocker** | TUI Docker 클라이언트 |
| **ctop** | container top |
| **trivy** | 취약점 스캔 |
| **hadolint** | Dockerfile lint |
| **syft** | SBOM 생성 |
| **grype** | 취약점 스캔 |
| **cosign** | 이미지 서명 |
| **docker-slim** | 이미지 크기 자동 최적화 |

## 3. Kubernetes

| 도구 | 용도 |
|---|---|
| **kubectl** | 기본 CLI |
| **minikube** | 로컬 k8s |
| **kind** | Docker in Docker k8s |
| **k3s** | 가벼운 k8s |
| **k3d** | k3s in Docker |
| **kubectx / kubens** | context·namespace 전환 |
| **k9s** | TUI 클라이언트 (강력) |
| **Lens** | GUI (상용 무료) |
| **Octant** | GUI (VMware) |
| **stern** | 다중 Pod log tail |
| **kubefwd** | 여러 service 동시 port-forward |
| **krew** | kubectl plugin manager |
| **kustomize** | overlay·patch 기반 manifest |
| **helm** | 패키지 매니저 |
| **helmfile** | helm release 선언적 관리 |
| **Argo CD** | GitOps |
| **Flux** | GitOps 대안 |
| **Argo Rollouts** | Blue-Green / Canary |
| **Flagger** | Canary 자동화 |
| **cert-manager** | 인증서 자동 |
| **ingress-nginx** | Ingress Controller |
| **Traefik** | 모던 Ingress·LB |
| **MetalLB** | bare-metal LoadBalancer |
| **Velero** | 백업·복구 |
| **kubeseal** | Sealed Secrets CLI |

## 4. CI/CD

| 도구 | 특징 |
|---|---|
| **GitHub Actions** | git와 통합, 무료 티어 |
| **GitLab CI** | GitLab과 통합 |
| **Jenkins** | 한국 금융권 표준 |
| **CircleCI** | SaaS |
| **Buildkite** | 자체 runner + SaaS UI |
| **Drone** | 컨테이너 기반 |
| **Tekton** | k8s native CI/CD |
| **Argo Workflows** | k8s에서 복잡한 workflow |
| **Spinnaker** | 멀티 클라우드 배포 (Netflix) |
| **ArgoCD** | GitOps |

## 5. IaC

| 도구 | 용도 |
|---|---|
| **Terraform** | 사실상 표준 (multi-cloud) |
| **OpenTofu** | Terraform fork (오픈) |
| **Pulumi** | 코드(Python/TS)로 IaC |
| **AWS CDK** | AWS 전용 |
| **CloudFormation** | AWS 네이티브 |
| **Ansible** | configuration management (서버 설정) |
| **Chef / Puppet** | 옛 configuration |
| **Crossplane** | k8s로 인프라 관리 |
| **terragrunt** | Terraform wrapper |
| **tflint** | lint |
| **tfsec / Checkov** | 보안 스캔 |
| **Atlantis** | PR 기반 Terraform workflow |

## 6. Registry

| | 특징 |
|---|---|
| **Docker Hub** | 표준, 무료 (rate limit) |
| **GHCR (GitHub Container Registry)** | GitHub 통합, 무료 |
| **AWS ECR** | private (AWS) |
| **GCP Artifact Registry** | private (GCP) |
| **Azure Container Registry** | private (Azure) |
| **Harbor** | self-hosted enterprise |
| **Nexus / Artifactory** | 한국 금융권 흔함 |
| **Quay** | Red Hat |

## 7. Secret 관리

| 도구 | 용도 |
|---|---|
| **HashiCorp Vault** | 표준 |
| **AWS Secrets Manager** | AWS |
| **AWS Parameter Store** | AWS (저렴) |
| **GCP Secret Manager** | GCP |
| **Azure Key Vault** | Azure |
| **Bitnami Sealed Secrets** | git에 암호화한 secret commit |
| **External Secrets Operator** | k8s ↔ Vault/AWS 통합 |
| **SOPS** | git용 암호화 YAML |

## 8. 모니터링 (다음 부트캠프)

| 도구 | 용도 |
|---|---|
| **Prometheus** | 메트릭 |
| **Grafana** | 대시보드 |
| **Loki** | 로그 (Grafana) |
| **Tempo** | trace (Grafana) |
| **Jaeger** | 분산 trace |
| **Datadog / New Relic** | SaaS APM |
| **Pinpoint** | 한국 |
| **OpenTelemetry** | 표준 |

## 9. 개발 환경 (로컬)

| 도구 | 용도 |
|---|---|
| **WSL2** | Windows에서 Linux |
| **Tilt** | k8s 로컬 개발 (rapid iteration) |
| **Skaffold** | 같음 |
| **Telepresence** | local app ↔ k8s cluster |
| **mirrord** | container 안에서 local code 실행 |
| **devspace** | k8s 개발 |
| **VS Code Dev Containers** | 컨테이너에서 개발 |
| **GitHub Codespaces** | 클라우드 IDE |

## 10. 보안·정책

| 도구 | 용도 |
|---|---|
| **OPA Gatekeeper** | k8s policy as code |
| **Kyverno** | k8s policy (YAML 친화) |
| **Falco** | runtime 보안 |
| **kube-bench** | CIS k8s benchmark |
| **kube-hunter** | k8s 침투 테스트 |
| **Polaris** | k8s best practice |
| **Trivy** | 종합 보안 스캐너 |
| **Snyk** | dependency·이미지·IaC |
| **Aqua** | 컨테이너 보안 |

## 11. 클라우드 (참고)

| | 매니지드 k8s |
|---|---|
| **AWS** | EKS |
| **GCP** | GKE |
| **Azure** | AKS |
| **NHN Cloud** | NKS (한국) |
| **Naver Cloud** | Kubernetes Service |
| **KT Cloud** | K-PaaS |

## 12. 한국에서 자주 보는 조합

| 영역 | 도구 |
|---|---|
| Git host | GitLab (사내) + GitHub (오픈소스) |
| CI | Jenkins |
| Registry | Harbor / Nexus |
| k8s | 자체 또는 NKS |
| 배포 | Spinnaker (옛) / ArgoCD (현재) |
| 모니터링 | Pinpoint + Grafana + ELK |
| IaC | Terraform |
| Secret | Vault |

---

## 학습 추천 순서

1. **git + gh** — 일상
2. **Docker** + **trivy** + **hadolint**
3. **kubectl** + **k9s** + **stern**
4. **helm**
5. **kustomize**
6. **GitHub Actions**
7. **ArgoCD**
8. **Terraform** (cloud 환경이면)
9. **Vault** 또는 **Sealed Secrets**
