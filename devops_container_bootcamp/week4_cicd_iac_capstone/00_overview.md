# Week 4 — CI/CD · IaC · 캡스톤

## 주차 목표

코드 push → 자동 빌드·테스트·이미지·배포의 **연속 파이프라인**. GitHub Actions로 실전 workflow. Terraform으로 인프라 코드. GitOps(ArgoCD) 개념. 마지막 캡스톤에서 모든 것을 합쳐 Spring Boot 앱을 GitHub→Docker Hub→k8s까지.

---

## 일정

| Day | 주제 | 핵심 |
|---|---|---|
| Day 1 | [GitHub Actions](01_github_actions.md) | workflow·job·step, runner, secret |
| Day 2 | [파이프라인 설계](02_pipeline_design.md) | build/test/lint/sec/deploy stages |
| Day 3 | [IaC · Terraform](03_iac_terraform.md) | declarative infra, state, modules |
| Day 4 | [GitOps · ArgoCD](04_gitops_argocd.md) | "git을 진실원천으로" |
| Day 5 | [캡스톤](05_capstone.md) | end-to-end 파이프라인 |

### Lab

| Lab | 내용 |
|---|---|
| [lab7_github_actions_pipeline.md](labs/lab7_github_actions_pipeline.md) | Spring Boot의 CI/CD 파이프라인 |

---

## 학습 결과

- [ ] GitHub Actions workflow 작성
- [ ] 빌드·테스트·lint·scan·deploy 5단계 파이프라인
- [ ] secret·환경별 deploy gating
- [ ] Terraform 기초 (resource·variable·output·state)
- [ ] GitOps의 개념과 ArgoCD 흐름
- [ ] Spring Boot 앱을 GitHub push 한 번에 k8s에 배포

---

## Week 4를 마치면 답할 수 있어야

1. CI와 CD의 차이?
2. GitHub Actions에서 secret을 어떻게 다루나?
3. Terraform plan과 apply의 차이?
4. terraform state가 어디 저장되고 왜 backend로 옮기나?
5. ArgoCD가 "push" 모델이 아닌 이유?
6. Production 배포에 manual approval을 어떻게 넣나?
7. Blue-Green vs Canary vs Rolling 차이?
