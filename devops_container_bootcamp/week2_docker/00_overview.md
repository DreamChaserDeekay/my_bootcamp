# Week 2 — Docker 심화·이미지 최적화

## 주차 목표

Docker가 어떻게 동작하는지(Linux **namespace + cgroup**)를 안다. Dockerfile을 잘 쓰고, multi-stage·distroless로 이미지를 100MB 이하로. 이미지 보안 스캔과 BuildKit 활용. Spring Boot 앱을 운영급 이미지로 패키징.

---

## 일정

| Day | 주제 | 핵심 |
|---|---|---|
| Day 1 | [컨테이너 기초](01_container_basics.md) | namespace·cgroup, 이미지 vs 컨테이너, layer |
| Day 2 | [Dockerfile 베스트](02_dockerfile_best.md) | 캐시 활용, .dockerignore, ENTRYPOINT |
| Day 3 | [Multi-stage·Distroless](03_multistage_distroless.md) | 빌드/런타임 분리, slim → distroless → scratch |
| Day 4 | [Docker Compose](04_docker_compose.md) | 로컬 개발 환경, 서비스 의존성, 볼륨 |
| Day 5 | [보안 스캔·BuildKit](05_image_security_scan.md) | Trivy, Hadolint, BuildKit, SBOM |

### Lab

| Lab | 내용 |
|---|---|
| [lab3_spring_boot_docker.md](labs/lab3_spring_boot_docker.md) | Spring Boot 앱 도커라이즈 |
| [lab4_image_optimization.md](labs/lab4_image_optimization.md) | 1GB → 100MB 이하로 |

---

## 학습 결과

- [ ] 컨테이너 = namespace + cgroup임을 안다
- [ ] 이미지 layer 캐시 메커니즘
- [ ] Dockerfile 명령어 15+개
- [ ] multi-stage build로 빌드 vs 런타임 분리
- [ ] distroless·scratch 차이
- [ ] docker-compose로 다중 서비스 띄움
- [ ] Trivy로 보안 스캔
- [ ] BuildKit·캐시 마운트

---

## Week 2를 마치면 답할 수 있어야

1. `docker run`이 일어날 때 OS 수준에서 무엇이 일어나는가?
2. Dockerfile의 `RUN apt install && rm -rf /var/lib/apt/lists/*`는 왜 한 줄?
3. multi-stage build의 build stage와 final stage 구분 기준?
4. distroless 이미지에 shell이 없는 이유?
5. `COPY` 위치를 잘못 두면 캐시가 깨지는 시나리오?
6. JVM 컨테이너에 줘야 할 옵션은?
7. Trivy·Hadolint·Docker Scout 차이?
