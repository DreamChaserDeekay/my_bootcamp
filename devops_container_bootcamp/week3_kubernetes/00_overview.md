# Week 3 — Kubernetes 기초·운영

## 주차 목표

Kubernetes의 **컨트롤 플레인 + 워커 노드** 구조를 안다. Pod·Deployment·Service·Ingress·ConfigMap·Secret을 YAML로 작성. Helm으로 환경별 차이 관리. probe·HPA·리소스 limit. 운영 중 디버깅 (`kubectl describe`, `kubectl logs`, `kubectl exec`).

---

## 일정

| Day | 주제 | 핵심 |
|---|---|---|
| Day 1 | [k8s 아키텍처](01_k8s_architecture.md) | Control Plane + Node, etcd, scheduler |
| Day 2 | [Pod·Deployment·Service](02_pod_deployment_service.md) | 핵심 객체 3종 |
| Day 3 | [ConfigMap·Secret·Volume](03_configmap_secret_volume.md) | 설정·시크릿·스토리지 |
| Day 4 | [Ingress·Helm](04_ingress_helm.md) | 외부 노출, 패키지 매니저 |
| Day 5 | [Probe·HPA·디버깅](05_probes_hpa_debug.md) | 운영 필수 |

### Lab

| Lab | 내용 |
|---|---|
| [lab5_first_deploy.md](labs/lab5_first_deploy.md) | Spring Boot 앱을 k8s에 배포 |
| [lab6_helm_chart.md](labs/lab6_helm_chart.md) | Custom Helm chart 작성 |

---

## 학습 결과

- [ ] Control plane 4개 컴포넌트 안다 (apiserver / etcd / scheduler / controller-manager)
- [ ] Pod·ReplicaSet·Deployment 관계
- [ ] Service 4가지 타입 (ClusterIP / NodePort / LoadBalancer / Headless)
- [ ] ConfigMap·Secret 사용
- [ ] Ingress로 외부 노출
- [ ] Helm Chart 작성·배포
- [ ] liveness·readiness·startup probe
- [ ] HPA로 auto-scaling
- [ ] `kubectl logs / describe / exec / port-forward`

---

## Week 3을 마치면 답할 수 있어야

1. `kubectl apply -f deploy.yaml`이 일어나는 순간 무엇이 시작되나?
2. Pod이 죽으면 누가 다시 만드나?
3. Service 없이 Pod끼리 통신 가능한가? (가능하지만 왜 안 좋은가)
4. ConfigMap의 값이 변경되면 Pod이 자동으로 반영하나?
5. Ingress와 Service의 차이?
6. liveness probe 실패하면 무엇이 일어나나? readiness는?
7. HPA가 어떤 메트릭으로 스케일링하나?

---

## 사전 설정

### Docker Desktop의 k8s

```powershell
# Docker Desktop → Settings → Kubernetes → Enable Kubernetes
# (체크 후 Apply & Restart)

kubectl version
kubectl get nodes
# NAME             STATUS   ROLES           AGE
# docker-desktop   Ready    control-plane   1m
```

### kubectl 자동완성 (옵션)

```powershell
# PowerShell
kubectl completion powershell | Out-String | Invoke-Expression
Add-Content $PROFILE 'kubectl completion powershell | Out-String | Invoke-Expression'
```

### 첫 명령

```powershell
kubectl cluster-info
# Kubernetes control plane is running at https://kubernetes.docker.internal:6443

kubectl get all -A
# 시스템 namespace의 컴포넌트들
```
