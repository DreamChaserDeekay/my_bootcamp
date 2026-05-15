# kubectl Cheatsheet

## Context·Cluster

```bash
kubectl config get-contexts
kubectl config use-context <name>
kubectl config current-context
kubectl config view
kubectl cluster-info
kubectl version
```

## 조회 (get)

```bash
kubectl get pods
kubectl get pods -A                        # 모든 namespace
kubectl get pods -n <ns>
kubectl get pods -o wide                   # node·IP
kubectl get pods -o yaml > pods.yaml
kubectl get pods -o json | jq
kubectl get pods --watch                   # 실시간
kubectl get pods --show-labels
kubectl get pods -l app=web                # label selector
kubectl get pods -l 'env in (prod,staging)'
kubectl get pods --field-selector=status.phase=Running

# 자주 쓰는 약어
kubectl get po                              # pods
kubectl get deploy                          # deployments
kubectl get rs                              # replicasets
kubectl get svc                             # services
kubectl get ing                             # ingresses
kubectl get ns                              # namespaces
kubectl get cm                              # configmaps
kubectl get sec                             # secrets
kubectl get hpa
kubectl get pv,pvc                          # 여러 종류 동시
kubectl get all                             # 흔한 자원 모두
```

## 자세히 (describe)

```bash
kubectl describe pod <pod>
kubectl describe deploy <name>
kubectl describe node <node>
```

### Events 확인 (디버깅 1순위)

```bash
kubectl get events
kubectl get events --sort-by=.lastTimestamp
kubectl get events -A --sort-by=.lastTimestamp
kubectl describe pod <pod> | grep -A 20 Events
```

## 로그

```bash
kubectl logs <pod>
kubectl logs -f <pod>                      # follow
kubectl logs --tail=100 <pod>
kubectl logs --since=1h <pod>
kubectl logs --previous <pod>              # 죽기 전
kubectl logs -c <container> <pod>          # multi-container
kubectl logs -l app=web --max-log-requests=10  # label로
```

## 들어가기·디버깅

```bash
kubectl exec -it <pod> -- bash
kubectl exec -it <pod> -- sh
kubectl exec <pod> -- env
kubectl exec <pod> -- ls /app

# port-forward
kubectl port-forward pod/<pod> 8080:8080
kubectl port-forward svc/<svc> 8080:80
kubectl port-forward deploy/<deploy> 8080:8080
kubectl port-forward svc/<svc> :8080       # 로컬 포트 자동

# proxy (apiserver 접근)
kubectl proxy
# http://localhost:8001/api/v1/namespaces/default/pods

# ephemeral container (distroless 디버깅)
kubectl debug -it <pod> --image=busybox --target=<container>
kubectl debug -it <pod> --image=nicolaka/netshoot --target=app
```

## 생성·변경

```bash
# 단일 자원
kubectl run pod-name --image=nginx
kubectl create deployment web --image=nginx
kubectl create svc clusterip web --tcp=80:8080
kubectl create configmap my-config --from-literal=K=V --from-file=app.conf
kubectl create secret generic my-secret --from-literal=PASS=xxx
kubectl create namespace dev

# YAML로 (권장)
kubectl apply -f manifest.yaml
kubectl apply -f manifest.yaml --dry-run=client -o yaml
kubectl apply -f ./k8s/                    # 디렉토리 전체

# 편집 (직접 etcd 수정)
kubectl edit deploy web

# 패치
kubectl patch deploy web -p '{"spec":{"replicas":5}}'
kubectl patch deploy web --type=json -p='[{"op":"replace","path":"/spec/replicas","value":5}]'

# 직접 변경
kubectl scale deploy/web --replicas=5
kubectl set image deploy/web app=myapp:v2
kubectl set env deploy/web FOO=bar
kubectl set resources deploy/web --limits=memory=1Gi --requests=cpu=200m
```

## 삭제

```bash
kubectl delete pod <pod>
kubectl delete -f manifest.yaml
kubectl delete pod -l app=web              # label로
kubectl delete pod --field-selector=status.phase=Succeeded
kubectl delete namespace dev               # 안의 모든 자원 함께
kubectl delete pod <pod> --grace-period=0 --force    # 강제 (마지막 수단)
```

## Rollout

```bash
kubectl rollout status deploy/web
kubectl rollout history deploy/web
kubectl rollout undo deploy/web                              # 마지막 revision으로
kubectl rollout undo deploy/web --to-revision=2
kubectl rollout pause deploy/web                              # 일시정지
kubectl rollout resume deploy/web
kubectl rollout restart deploy/web                            # 강제 재시작
```

## 자원 사용

```bash
kubectl top nodes
kubectl top pods
kubectl top pods -A --sort-by=memory
kubectl top pods --sort-by=cpu --containers
```

## Service·Endpoint

```bash
kubectl get svc
kubectl get endpoints <svc>                # Pod IP가 매핑됐나
kubectl get endpointslices                 # 새 API (v1.21+)
```

## Helm

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
helm search repo postgresql

helm install my-release ./chart
helm install my-release ./chart -f values-dev.yaml --namespace dev --create-namespace
helm install my-release ./chart --set image.tag=v2

helm list -A
helm status my-release
helm get values my-release
helm get manifest my-release

helm upgrade my-release ./chart
helm upgrade --install my-release ./chart        # idempotent

helm history my-release
helm rollback my-release 1

helm uninstall my-release

helm lint ./chart
helm template my-release ./chart
helm package ./chart                              # tgz 만들기
```

## 자주 쓰는 alias·trick

```bash
# kubectl 별칭
alias k=kubectl
alias kg='kubectl get'
alias kd='kubectl describe'
alias ka='kubectl apply -f'
alias kc='kubectl config use-context'

# kubens / kubectx (krew plugin)
kubectx                                    # 컨텍스트 목록
kubectx prod                                # 전환
kubens                                      # ns 목록
kubens dev                                  # ns 전환

# 현재 ns 변경
kubectl config set-context --current --namespace=dev

# stern (좋은 logs 도구)
stern web                                   # 모든 web Pod 로그 합쳐서
stern -l app=web --tail=100

# watch
watch kubectl get pods
watch -d kubectl get pods                   # diff highlight
```

## 디버깅 체크리스트

```bash
# 1. Pod 상태
kubectl get pods -n <ns>

# 2. Events
kubectl describe pod <pod> -n <ns> | grep -A 30 Events

# 3. 로그
kubectl logs -n <ns> <pod>
kubectl logs -n <ns> <pod> --previous

# 4. Image pull 문제?
kubectl describe pod <pod> -n <ns> | grep -i pull

# 5. probe 실패?
kubectl describe pod <pod> -n <ns> | grep -i probe

# 6. Service endpoint 매칭?
kubectl get endpoints -n <ns>
kubectl get pods --show-labels -n <ns>

# 7. 네트워크 (다른 Pod에서 test)
kubectl run --rm -it net-test --image=nicolaka/netshoot -- bash
$ nslookup my-svc.<ns>
$ curl http://my-svc.<ns>

# 8. 자원
kubectl top pod <pod> -n <ns>
kubectl describe node | grep -A 5 Allocated

# 9. RBAC 권한
kubectl auth can-i create pods --as system:serviceaccount:default:my-sa
```

## 자주 보는 Pod 상태별 대응

| Status | 1차 조치 |
|---|---|
| Pending | describe → Events |
| ContainerCreating | describe → image pull / volume mount 확인 |
| ImagePullBackOff | image name·tag·secret 확인 |
| CrashLoopBackOff | logs --previous |
| OOMKilled | describe → Last State, 메모리 limit 점검 |
| Error | logs (--previous) |
| Init:0/1 | initContainer logs |

## YAML 빠른 생성 (template으로)

```bash
# kubectl로 YAML 골격 만들기
kubectl create deployment web --image=nginx --dry-run=client -o yaml > deploy.yaml
kubectl expose deployment web --port=80 --dry-run=client -o yaml > svc.yaml
kubectl create configmap app --from-literal=KEY=value --dry-run=client -o yaml > cm.yaml

# 기존 자원에서
kubectl get deploy web -o yaml > deploy.yaml
# 깨끗하게 (status·관리 필드 제거)
kubectl get deploy web -o yaml | yq 'del(.status, .metadata.uid, .metadata.resourceVersion, .metadata.generation, .metadata.creationTimestamp)' > deploy.yaml
```

## Krew (kubectl plugin manager)

```bash
# https://krew.sigs.k8s.io/
kubectl krew install ctx ns                  # kubectx, kubens
kubectl krew install tree                    # 자원 트리
kubectl krew install neat                    # YAML 정리
kubectl krew install rbac-view               # RBAC 시각화
```
