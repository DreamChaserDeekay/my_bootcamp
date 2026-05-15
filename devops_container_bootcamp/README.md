# DevOps · 컨테이너 부트캠프

> Git → Docker → Kubernetes → CI/CD까지. 백엔드 개발자가 **인프라까지 책임지는 시대**의 표준 스택을 한 사이클로.

---

## 학습 목표

이 부트캠프를 마치면:

- Git 내부(객체·refs·packfile)를 이해하고 복잡한 상황에서 reflog로 복구할 수 있다
- 안전한 브랜칭 전략을 선택하고 코드 리뷰 문화를 주도할 수 있다
- Docker 이미지를 **multi-stage + distroless**로 100MB 이하로 만든다
- Kubernetes의 Pod·Service·Ingress·ConfigMap을 YAML로 작성한다
- Helm Chart를 만들어 환경별로 다르게 배포한다
- GitHub Actions로 build → test → image push → deploy 파이프라인 작성
- Terraform으로 인프라를 코드로 관리
- GitOps(ArgoCD) 개념을 이해한다
- Spring Boot 앱 하나를 **소스→운영 배포**까지 end-to-end로 진행

---

## 사전 준비

| 구성 | 버전·도구 |
|---|---|
| **OS** | Windows 10/11 (PowerShell) + WSL2 권장 |
| **Git** | 2.40+ |
| **Docker Desktop** | 최신 (Kubernetes 활성화) |
| **kubectl** | Docker Desktop 자동 설치 |
| **helm** | 3.x |
| **GitHub 계정** | Actions 무료 티어 사용 |
| **IDE** | VS Code (YAML/Docker 확장) 또는 IntelliJ |
| **JDK** | 21 LTS (Spring Boot 실습용) |

### 빠른 검증

```powershell
git --version           # 2.40+
docker --version        # 24+
docker compose version
kubectl version --client
helm version            # 3.x

# Docker Desktop의 k8s 활성화
kubectl get nodes
# 1개의 docker-desktop 노드가 보여야 함
```

---

## 디렉토리 구조

```
devops_container_bootcamp/
├── README.md                          ← 지금 이 문서
├── week1_git_mastery/                 ← Git 심화·브랜칭·코드리뷰
├── week2_docker/                      ← Docker 심화·이미지 최적화
├── week3_kubernetes/                  ← k8s 기초·운영
├── week4_cicd_iac_capstone/           ← CI/CD·IaC·캡스톤
├── practice_app/                       ← Spring Boot end-to-end 파이프라인
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── k8s/
│   ├── .github/workflows/
│   └── terraform/
└── resources/
    ├── git_cheatsheet.md
    ├── docker_cheatsheet.md
    ├── kubectl_cheatsheet.md
    ├── tools.md
    ├── books_and_courses.md
    ├── glossary.md
    ├── quick_reference.md
    └── troubleshooting_playbook.md
```

---

## 주차 흐름

### Week 1 — Git 마스터리
Git이 단지 commit/push가 아니라 **객체·refs·packfile**로 구성된 컨텐츠 주소화 저장소임을 안다. rebase·reflog·cherry-pick의 위험과 활용. trunk-based vs git flow. 코드 리뷰 문화와 conventional commits.

### Week 2 — Docker 심화
Linux namespace·cgroup이 컨테이너의 정체. Dockerfile 베스트 프랙티스. multi-stage build로 빌드 vs 런타임 분리. distroless·BuildKit으로 이미지 100MB 이하. 보안 스캔 (Trivy).

### Week 3 — Kubernetes 기초
Control Plane (kube-apiserver, etcd, scheduler, controller-manager) + Node (kubelet, kube-proxy). Pod → Deployment → Service → Ingress. ConfigMap/Secret. liveness/readiness probe. HPA. Helm.

### Week 4 — CI/CD · IaC · 캡스톤
GitHub Actions workflow 작성. Jenkins·GitLab CI 비교. Terraform으로 GitHub repo·AWS resource 관리. GitOps(ArgoCD). 캡스톤: practice_app을 GitHub → Docker Hub → k8s까지 자동 배포.

---

## 추천 학습 페이스

| 일정 | Day 단위 |
|---|---|
| **5일/주 (평일 1h + 주말 2h)** | 평일 Day 1개, 주말 lab |
| **주말 집중 (주말 6h)** | 토 Day 1-3, 일 Day 4-5 + lab |
| **2달 분산** | 1주 2-3 Day, 깊이 우선 |

각 Day는 **본문 30~60분 + 실습 30~90분**.

---

## 시작하기

1. [practice_app/README.md](practice_app/README.md)로 실습 앱 빌드 검증
2. [week1/00_overview.md](week1_git_mastery/00_overview.md)부터 순서대로
3. 각 주차 끝 `checklist.md`로 자가 점검
4. 막히면 [troubleshooting_playbook.md](resources/troubleshooting_playbook.md)
5. **Week 4 캡스톤**에서 모든 것을 합친다

---

## 이 부트캠프의 차별점

- **Java/Spring 개발자 관점** — 실습 앱이 Spring Boot
- **윈도우 + WSL2 / Docker Desktop** 환경 가정
- **로컬에서 다 됨** — 클라우드 비용 X
- **실수와 복구** 중심 — `git reset --hard` 후 복구하기 등
- **GitHub Actions 무료 티어**로 진짜 파이프라인
- **금융권 운영 관점** — 망분리·승인 흐름·롤백 전략도 다룸

---

## 다음 단계

이후 권장:
- **관측·SRE 부트캠프** — Prometheus + Grafana + Loki 실전
- **메시지큐·캐시·검색** — Kafka·Redis·Elasticsearch
- **시스템 설계·아키텍처** — MSA·DDD·CQRS

---

> *"Slow is smooth. Smooth is fast."* — SRE 격언
