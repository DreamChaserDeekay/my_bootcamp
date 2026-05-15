# Week 3 — 체크리스트

## 아키텍처

- [ ] Control Plane 4개 컴포넌트 (apiserver/etcd/scheduler/controller-mgr)
- [ ] Node 2개 컴포넌트 (kubelet/kube-proxy)
- [ ] Pod 생성 흐름 (kubectl → apiserver → etcd → scheduler → kubelet)
- [ ] Reconciliation Loop
- [ ] Declarative vs Imperative
- [ ] Namespace

## Pod·Deployment·Service

- [ ] Pod의 정체 (containers + shared net/volume)
- [ ] ReplicaSet vs Deployment
- [ ] Rolling update + rollback
- [ ] Service 4가지 타입 (ClusterIP / NodePort / LoadBalancer / Headless)
- [ ] Label·Selector
- [ ] init container

## ConfigMap·Secret·Volume

- [ ] ConfigMap 주입 (env vs volume)
- [ ] Secret 종류·base64 함정
- [ ] Sealed Secrets / External Secrets
- [ ] emptyDir vs hostPath vs PVC
- [ ] StatefulSet 기본
- [ ] DaemonSet 용도

## Ingress·Helm

- [ ] Ingress Controller 필요성
- [ ] Path / host 기반 라우팅
- [ ] TLS termination, cert-manager
- [ ] Helm Chart 구조
- [ ] values.yaml로 환경 분리
- [ ] helm install / upgrade / rollback / history
- [ ] dependency chart

## Probe·HPA·디버깅

- [ ] liveness vs readiness vs startup
- [ ] resources requests vs limits
- [ ] QoS class
- [ ] HPA 동작·튜닝
- [ ] Pod 디버깅 5단계 (logs/describe/exec/port-forward/debug)
- [ ] ephemeral container
- [ ] OOMKilled 진단

## 실습 결과

- [ ] Lab 5 — Spring Boot 앱을 k8s에 배포
- [ ] Lab 5 — Ingress로 외부 노출
- [ ] Lab 5 — Rolling update + rollback
- [ ] Lab 5 — 디버깅 3시나리오
- [ ] Lab 6 — Custom Helm chart
- [ ] Lab 6 — 환경별 values

## 자기 점검

1. `kubectl apply -f deploy.yaml`이 일어나는 순서?
   <details><summary>답</summary>kubectl→apiserver(검증·인증)→etcd 저장→Deployment Controller가 ReplicaSet 생성→ReplicaSet Controller가 Pod 생성→Scheduler가 Pod에 Node 할당→해당 Node의 kubelet이 컨테이너 시작→상태 보고→endpoints 업데이트</details>

2. Pod이 죽으면 누가 다시 만드나?
   <details><summary>답</summary>Pod 자체는 ephemeral — 부활 X. ReplicaSet (또는 그 위 Deployment/StatefulSet/DaemonSet) Controller가 desired replicas 유지를 위해 새 Pod 생성. 그래서 단독 Pod은 거의 안 쓰고 Deployment로 감싸기.</details>

3. Service ClusterIP의 IP가 어떻게 동작하나?
   <details><summary>답</summary>가상 IP. kube-proxy가 모든 Node에 iptables/IPVS rule 추가. ClusterIP로 들어온 트래픽을 selector 매칭 Pod IP들로 round-robin 라우팅.</details>

4. ConfigMap 변경 시 자동 반영?
   <details><summary>답</summary>env로 주입한 값: 새 Pod 시작 필요. Volume mount 파일: 약 1분 후 자동 업데이트. 단, 앱이 파일을 다시 읽어야 적용. 강제 재시작: kubectl rollout restart.</details>

5. liveness probe와 readiness probe 차이?
   <details><summary>답</summary>liveness 실패 → 컨테이너 재시작. readiness 실패 → Service endpoints에서 제외(트래픽 차단), 컨테이너는 안 죽임. 배포 중·일시적 부하·외부 의존성 문제엔 readiness, 영원히 hang했을 때 liveness.</details>

6. JVM 컨테이너에 메모리 limit 1Gi 줬을 때 -Xmx는?
   <details><summary>답</summary>JDK 10+ -XX:+UseContainerSupport 기본. -XX:MaxRAMPercentage=70 등으로 limit의 비율 지정. 직접 -Xmx 700m도 가능. Heap 외 Metaspace·Direct·Stack도 limit 안에 들어가야 함.</details>

7. HPA가 동작하려면 필요한 것?
   <details><summary>답</summary>① metrics-server 설치. ② Deployment의 Pod에 resources.requests 설정 (% 계산 기준). ③ HPA 객체. ④ 적정 min/maxReplicas, target utilization.</details>

8. distroless Pod 디버깅 방법?
   <details><summary>답</summary>kubectl debug --image=busybox --target=<container>로 ephemeral container 추가. 같은 namespace 공유 → 다른 컨테이너 파일·프로세스 보임. 또는 distroless의 :debug 변종 사용.</details>

---

## 통과했다면

Week 4 [CI/CD · IaC · 캡스톤](../week4_cicd_iac_capstone/00_overview.md)으로!
