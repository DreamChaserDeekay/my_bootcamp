# Week 4 — 체크리스트

## GitHub Actions

- [ ] Workflow / Job / Step 계층
- [ ] 트리거 종류 (push / pr / schedule / dispatch)
- [ ] needs로 job 순서·matrix
- [ ] secret·environment·OIDC
- [ ] cache·artifact
- [ ] reusable workflow
- [ ] concurrency로 중복 cancel

## 파이프라인 설계

- [ ] 표준 단계 (validate → image → deploy)
- [ ] PR vs main 차별화
- [ ] 환경별 deploy gating
- [ ] image promotion vs per-env rebuild
- [ ] Rolling vs Blue-Green vs Canary
- [ ] 보안 단계 (SAST·dependency·image scan)
- [ ] Rollback 전략

## Terraform

- [ ] provider·resource·variable·output·data
- [ ] plan vs apply
- [ ] state 개념과 backend (S3·GCS)
- [ ] state lock (DynamoDB)
- [ ] module로 재사용
- [ ] terraform import
- [ ] github provider (repo·branch protection 코드로)

## GitOps · ArgoCD

- [ ] CIOps vs GitOps
- [ ] ArgoCD Application 객체
- [ ] auto sync·self-heal·prune
- [ ] Sync wave·Hook
- [ ] App-of-Apps·ApplicationSet
- [ ] CIOps와 GitOps 혼합 (CI는 image, ArgoCD는 deploy)

## 캡스톤

- [ ] app-repo와 manifests-repo 분리
- [ ] CI: test → image → manifest update
- [ ] ArgoCD: manifests 감지 → 자동 sync
- [ ] Dev 자동 / Prod 수동 승인
- [ ] Drift detection·self-heal 확인
- [ ] 캡스톤 보고서 작성

## 실습 결과

- [ ] Lab 7 — CI/CD 파이프라인 완성
- [ ] Capstone — end-to-end push → deploy

## 자기 점검

1. CI와 CD 차이?
   <details><summary>답</summary>CI(Continuous Integration): 코드 변경 통합·검증 자동화 (build·test). CD: ① Continuous Delivery — 항상 deployable 상태 유지. ② Continuous Deployment — 자동으로 prod까지 배포. 보통 CD는 두 의미 혼용.</details>

2. Terraform plan과 apply의 차이?
   <details><summary>답</summary>plan: 코드와 state·실제 인프라 비교, 어떤 변경이 일어날지 미리 보기. apply: plan의 결과를 실제로 실행. 운영선 plan을 PR에 첨부·검토 후 apply가 표준.</details>

3. Terraform state를 remote backend로 옮기는 이유?
   <details><summary>답</summary>① 분실 위험 (local 파일). ② 팀 협업 (동시 사용). ③ state lock (동시 apply 방지). ④ 시크릿 보호 (encrypt). S3+DynamoDB가 흔한 조합.</details>

4. ArgoCD가 "push 모델"이 아닌 이유?
   <details><summary>답</summary>Pull 모델 — 클러스터의 ArgoCD agent가 git을 polling. CI에 클러스터 자격을 줄 필요 X (보안). Drift detection 가능. 다중 클러스터 자연스러움.</details>

5. Image promotion vs per-env rebuild?
   <details><summary>답</summary>같은 이미지(같은 binary)를 모든 환경에 promote. "dev에선 됐는데" 문제가 환경/config로 좁혀짐. per-env rebuild는 미세한 차이로 디버깅 지옥.</details>

6. Blue-Green vs Canary?
   <details><summary>답</summary>Blue-Green: 2개 전체 환경 → 라우터 스위치로 즉시 전환. 즉시 롤백 가능. 자원 2배. Canary: 새 버전에 일부 트래픽(5%, 10%)부터 점진. 메트릭 보며 확대. 자원 효율·실 사용자 검증.</details>

7. ArgoCD의 self-heal이 위험한 경우?
   <details><summary>답</summary>운영 디버깅 중 임시 변경(replica 증가, label 추가 등)이 사라짐. 일시적으로 sync 끄거나 ApplicationSet의 sync wave/hook으로 제어. self-heal은 보통 운영서에 켜는 게 안전 (drift 방지).</details>

8. GitOps에서 secret을 git에 안전하게 저장하려면?
   <details><summary>답</summary>Sealed Secrets (Bitnami): 공개키로 암호화한 SealedSecret 객체를 git에 commit. 클러스터의 controller가 복호화해 일반 Secret 생성. 또는 External Secrets Operator로 Vault·AWS Secrets Manager에서 가져옴. SOPS도 옵션.</details>

---

## 통과했다면

[Capstone](05_capstone.md)으로!
