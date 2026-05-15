# 용어집 (Glossary)

## A

- **Annotation** — k8s 메타데이터 (selector X)
- **API Server** — k8s control plane의 단일 entry point
- **ArgoCD** — GitOps continuous delivery
- **ASLR** — Address Space Layout Randomization
- **Artifact** — 빌드 산출물 (jar, image)

## B

- **Backend (Terraform)** — state 저장 위치 (S3, GCS 등)
- **Bind mount** — host 디렉토리를 컨테이너에 마운트
- **Blob** (git) — 파일 내용 객체
- **Blue-Green deployment** — 두 환경 스위치
- **BuildKit** — Docker의 빌드 엔진
- **Buildpack** — 자동 컨테이너 빌드 (Paketo, Heroku)

## C

- **Canary deployment** — 일부 트래픽부터 점진
- **cert-manager** — k8s 인증서 자동
- **CGroup** — Linux 자원 제한
- **CI / CD** — Continuous Integration / Delivery (Deployment)
- **ClusterIP** — 클러스터 내부 Service
- **CNCF** — Cloud Native Computing Foundation
- **CodeQL** — GitHub 정적 분석
- **Commit** — git의 스냅샷
- **ConfigMap** — k8s 설정 객체
- **container** — namespace + cgroup으로 격리된 프로세스
- **containerd** — 표준 container runtime
- **Control Plane** — k8s의 brain (apiserver/etcd/scheduler/...)
- **Cosign** — 이미지 서명
- **Cron** — 주기적 실행

## D

- **DaemonSet** — 모든 (또는 일부) Node에 1 Pod
- **Declarative** — "원하는 상태"로 표현 (vs Imperative)
- **Deployment** — k8s rolling update 추상
- **Detached HEAD** — 브랜치 아닌 commit 직접 가리킴
- **Digest** — 이미지의 SHA256 (immutable)
- **Distroless** — 최소 OS 컨테이너 이미지
- **DNS (k8s)** — Service 이름 → IP
- **Docker** — 컨테이너 플랫폼
- **Drift** — 코드와 실제 상태 차이

## E

- **EKS** — AWS Elastic Kubernetes Service
- **emptyDir** — Pod 수명 volume
- **Endpoint** — Service의 backend Pod IP들
- **Ephemeral container** — 디버그용 임시 컨테이너
- **etcd** — k8s의 분산 key-value DB
- **External Secrets** — Vault·AWS Secrets에서 가져온 k8s Secret

## F

- **Feature Flag** — 코드 분기 토글
- **Flux** — GitOps (ArgoCD 대안)
- **fork** (git) — repo 복사
- **fork bomb** — 무한 프로세스 생성

## G

- **GHCR** — GitHub Container Registry
- **Git Flow** — branching 모델 (deprecate 진행)
- **GitHub Actions** — GitHub의 CI/CD
- **GitOps** — git을 진실원천으로 하는 인프라 운영
- **GKE** — Google Kubernetes Engine
- **GraalVM** — JVM 대안 (AOT 컴파일)

## H

- **Hadolint** — Dockerfile lint
- **HEAD** — git의 현재 위치
- **Helm** — k8s 패키지 매니저
- **Helm Chart** — 패키지 단위
- **HPA** — Horizontal Pod Autoscaler
- **hostPath** — host 디렉토리 mount (보안 위험)
- **Hub (Docker)** — Docker Hub registry

## I

- **IaC** — Infrastructure as Code
- **Image** — 컨테이너의 read-only 템플릿
- **Image promotion** — dev tag → prod tag (rebuild X)
- **Imperative** — "이렇게 해라" 명령형 (vs Declarative)
- **Ingress** — HTTP/HTTPS routing
- **init container** — main container 시작 전 실행

## J

- **JIT** — Just-In-Time compiler (JVM)
- **JMH** — Java Microbenchmark Harness

## K

- **k3s / k3d** — 가벼운 k8s
- **kind** — Docker in Docker k8s
- **kubelet** — Node의 agent
- **kube-proxy** — Node의 네트워크 구현
- **kubectl** — k8s CLI
- **Kubernetes** — 컨테이너 오케스트레이션
- **Kustomize** — overlay·patch 기반 manifest

## L

- **Label** — k8s selector용 메타데이터
- **Layered jar** — Spring Boot 변경 빈도별 layer
- **LoadBalancer** — k8s Service 타입 (외부)

## M

- **Manifest** — k8s YAML
- **MAT** — Memory Analyzer Tool
- **metrics-server** — k8s 자원 메트릭
- **Module** (Terraform) — 재사용 단위
- **Multi-stage build** — Dockerfile 빌드/런타임 분리

## N

- **Namespace** (k8s) — 격리 단위
- **Namespace** (Linux) — 컨테이너 격리
- **NodePort** — Service 타입 (Node 포트)

## O

- **OCI** — Open Container Initiative (표준)
- **OOMKilled** — 메모리 초과로 컨테이너 종료
- **OPA** — Open Policy Agent
- **Orphan** (git) — 도달 불가능한 commit

## P

- **PR / MR** — Pull Request / Merge Request
- **PAT** — Personal Access Token
- **Pod** — k8s의 실행 단위
- **port-forward** — 로컬에서 클러스터 자원 접근
- **PV / PVC** — Persistent Volume / Claim
- **Probe** — liveness / readiness / startup

## Q

- **QoS class** — Guaranteed / Burstable / BestEffort

## R

- **Rebase** — git history 재작성
- **Reconciliation Loop** — desired ↔ current 자동 조정
- **Reflog** — git의 모든 HEAD 이동 기록
- **Registry** — 이미지 저장소
- **ReplicaSet** — Pod N개 유지
- **Resource (Terraform)** — 인프라 자원
- **Rolling update** — 점진적 교체
- **Runner** — GitHub Actions 실행 환경

## S

- **SARIF** — Static Analysis Results Interchange Format
- **SBOM** — Software Bill of Materials
- **Sealed Secrets** — git에 안전한 secret 저장
- **Secret** (k8s) — 민감 정보 객체 (base64만)
- **Selector** — label로 자원 매칭
- **Service** (k8s) — 안정된 endpoint
- **Service Mesh** — Istio·Linkerd
- **Sidecar** — main container 옆 보조 컨테이너
- **SLA / SLI / SLO** — service level (agreement/indicator/objective)
- **SOPS** — git용 암호화
- **stash** (git) — 임시 변경 저장
- **State** (Terraform) — 인프라 현재 상태
- **StatefulSet** — 순서·이름 안정한 Pod 집합

## T

- **Tag** — git의 lightweight/annotated reference
- **Terraform** — IaC 도구
- **TLS** — Transport Layer Security
- **Tomcat** — Spring Boot 기본 내장 서버
- **tini** — 작은 init for 컨테이너
- **Tree** (git) — 디렉토리 객체
- **Trivy** — 보안 스캐너
- **Trunk-Based Development** — 짧은 feature·main 중심

## U

- **Union FS** — overlay 등 layer 합치는 FS

## V

- **Vault** — HashiCorp Secret 관리
- **Volume** (Docker/k8s) — 영속·공유 스토리지

## W

- **WAL** — Write-Ahead Log
- **Webhook** — HTTP 콜백
- **WebSocket** — full-duplex 통신
- **Workflow** (GitHub Actions) — YAML 정의

## X

- **XSS** — Cross-Site Scripting

## Y

- **YAML** — k8s·CI/CD 표준 설정 형식

## Z

- **Zero-downtime** — 무중단 배포
- **ZGC** — Z Garbage Collector (JDK)
