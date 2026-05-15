# Week 2 — 체크리스트

## 컨테이너 기초

- [ ] 컨테이너 vs VM 차이 (그림으로)
- [ ] 7가지 namespace 종류
- [ ] cgroup이 제한하는 자원
- [ ] 이미지 layer · Union FS
- [ ] `docker run`의 OS 수준 흐름
- [ ] WSL2에서 Docker 동작 방식

## Dockerfile

- [ ] 명령 15개
- [ ] ENTRYPOINT vs CMD (exec form vs shell form)
- [ ] 캐시 친화적 RUN·COPY 순서
- [ ] `.dockerignore`
- [ ] 비루트 사용자
- [ ] PID 1·tini
- [ ] LABEL OCI annotations

## Multi-stage·Distroless

- [ ] Multi-stage build 작성
- [ ] alpine·slim·distroless·scratch 차이
- [ ] Spring Boot Layered Jar
- [ ] Spring Boot Buildpack
- [ ] BuildKit cache mount
- [ ] BuildKit secret mount
- [ ] Multi-platform build (amd64/arm64)
- [ ] Digest pin

## Docker Compose

- [ ] compose 파일 구조
- [ ] depends_on with healthcheck
- [ ] 네트워크·볼륨
- [ ] env_file / `.env`
- [ ] profile (선택적 서비스)
- [ ] override 파일

## 보안

- [ ] Trivy 이미지 스캔
- [ ] Hadolint Dockerfile lint
- [ ] SBOM (Syft)
- [ ] Docker Scout
- [ ] Cosign 이미지 서명

## 실습 결과

- [ ] Lab 3 — 5단계 Dockerfile 진화
- [ ] Lab 3 — docker-compose로 DB와 함께
- [ ] Lab 4 — 5가지 방식 크기·시작·CVE 비교
- [ ] 자신의 운영 권장안 결정

## 자기 점검

1. `docker run`이 일어날 때 OS 수준에서?
   <details><summary>답</summary>① 이미지 layer를 union FS로 합쳐 rootfs 마운트, ② 새 namespace 생성 (PID/NET/MOUNT/UTS/IPC/USER), ③ cgroup 등록(메모리·CPU 제한), ④ 네트워크 인터페이스·포트 매핑, ⑤ ENTRYPOINT/CMD 프로세스를 PID 1로 실행.</details>

2. Dockerfile에서 `RUN apt install && rm -rf /var/lib/apt/lists/*`를 한 줄로 쓰는 이유?
   <details><summary>답</summary>각 RUN이 별도 layer를 만듦. 두 줄로 분리하면 첫 layer에 apt cache가 영구히 남음 → 이미지 크기 증가. 한 줄로 합치면 cache 정리 후 layer 만들어짐.</details>

3. JVM 컨테이너에 줘야 할 옵션?
   <details><summary>답</summary>-XX:MaxRAMPercentage=75 (Heap을 컨테이너 limit의 75%로), -XX:+HeapDumpOnOutOfMemoryError + HeapDumpPath, -Xlog:gc* GC 로그, -XX:+UseG1GC. JDK 10+은 -XX:+UseContainerSupport 기본 켜짐.</details>

4. distroless의 장점·도전?
   <details><summary>답</summary>장점: 최소 보안 표면적(shell·apt 없음), 이미지 작음(190MB), CVE 적음. 도전: 디버깅 어려움(shell 없음) — :debug 변종 또는 ephemeral container 사용.</details>

5. `docker-compose.yml`의 depends_on만으론 부족한 이유?
   <details><summary>답</summary>depends_on은 시작 순서만 보장, 준비 완료는 X. db가 시작됐어도 listen까지 시간 걸림. healthcheck + condition: service_healthy로 진짜 준비 후 의존 서비스 시작.</details>

6. Trivy로 CRITICAL CVE 발견 시 대응?
   <details><summary>답</summary>① 베이스 이미지 update 확인 → 새 버전 있으면 PR. ② 라이브러리면 dependency 버전 올림. ③ ignore-unfixed로 수정 가능한 것만. ④ CI에 trivy --exit-code 1로 강제.</details>

7. Spring Boot Layered Jar의 효과?
   <details><summary>답</summary>jar 내부를 변경 빈도별로 분리(dependencies/spring-boot-loader/snapshot-deps/application). Dockerfile에서 각 layer를 별도 COPY → 코드만 바뀌면 application layer만 rebuild·push. CI/CD 시간 크게 단축.</details>

---

## 통과했다면

Week 3 [Kubernetes](../week3_kubernetes/00_overview.md)로!
