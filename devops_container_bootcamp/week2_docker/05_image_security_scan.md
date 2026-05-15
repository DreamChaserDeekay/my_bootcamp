# Day 5 — 이미지 보안·스캔·BuildKit

## 한 줄 요약

운영급 이미지는 **취약점 스캔(Trivy)**, **Dockerfile lint(Hadolint)**, **SBOM** 자동 생성이 표준. BuildKit으로 빌드 캐시·시크릿·multi-platform.

## 학습 목표

- [ ] Trivy로 이미지·파일시스템 스캔
- [ ] Hadolint로 Dockerfile 검증
- [ ] SBOM (Software Bill of Materials)
- [ ] BuildKit cache mount·secret mount
- [ ] Multi-platform build (amd64·arm64)
- [ ] 컨테이너 ID 추적 (digest pin)

---

## Trivy — 이미지 취약점 스캔

```bash
# 설치
# Windows: choco install trivy
# Mac:     brew install trivy
# Linux:   apt install trivy 또는 binary

trivy --version
```

### 이미지 스캔

```bash
trivy image my-app:latest
```

출력 예:
```
my-app:latest (debian 12.5)
================================
Total: 12 (UNKNOWN: 0, LOW: 5, MEDIUM: 4, HIGH: 2, CRITICAL: 1)

┌──────────────┬────────────────┬──────────┬─────────────────────┐
│ Library      │ Vulnerability  │ Severity │ Installed Version   │
├──────────────┼────────────────┼──────────┼─────────────────────┤
│ libcrypto1.1 │ CVE-2026-xxxxx │ CRITICAL │ 1.1.1n-0+deb11u3    │
│ ...                                                            │
└──────────────┴────────────────┴──────────┴─────────────────────┘
```

### 옵션

```bash
# CRITICAL만
trivy image --severity CRITICAL my-app:latest

# 수정 가능한 것만
trivy image --ignore-unfixed my-app:latest

# JSON 출력 (CI 친화)
trivy image --format json -o report.json my-app:latest

# SARIF (GitHub Code Scanning)
trivy image --format sarif -o trivy.sarif my-app:latest

# 빠른 모드 (캐시)
trivy image --skip-update my-app:latest
```

### 파일시스템 스캔

```bash
trivy fs .
# 현재 디렉토리의 dependencies (package-lock.json, pom.xml 등) 스캔
```

### Misconfig·Secret 스캔

```bash
trivy config .                            # Dockerfile, k8s YAML 등
trivy fs --scanners secret .              # 시크릿 노출 탐지
```

### GitHub Actions 통합

```yaml
- name: Trivy scan
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: my-app:${{ github.sha }}
    severity: 'CRITICAL,HIGH'
    exit-code: '1'                        # 발견 시 빌드 실패
    format: 'sarif'
    output: 'trivy.sarif'

- name: Upload to GitHub Security
  uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: trivy.sarif
```

---

## Hadolint — Dockerfile lint

```bash
# Windows
docker run --rm -i hadolint/hadolint < Dockerfile

# 또는 설치
brew install hadolint
hadolint Dockerfile
```

출력:
```
Dockerfile:3 DL3008 warning: Pin versions in apt get install. apt-get install <package>=<version>
Dockerfile:5 DL3009 warning: Delete the apt-get lists after installing something
Dockerfile:7 SC2086 info: Double quote to prevent globbing and word splitting.
```

### 주요 규칙

| 규칙 | 의미 |
|---|---|
| DL3008 | apt 패키지 버전 명시 |
| DL3009 | apt-get list 정리 |
| DL3015 | --no-install-recommends |
| DL3020 | ADD 대신 COPY |
| DL3025 | JSON 형식 ENTRYPOINT |
| DL3059 | RUN을 한 줄에 합치기 |
| DL4006 | SHELL을 명시적으로 |

### 무시

```dockerfile
# hadolint ignore=DL3008
RUN apt install curl
```

---

## SBOM (Software Bill of Materials)

이미지에 든 모든 패키지 목록. 보안·법무·취약점 추적.

### Syft로 생성

```bash
# 설치
curl -sSfL https://raw.githubusercontent.com/anchore/syft/main/install.sh | sh -s -- -b /usr/local/bin

# 이미지 SBOM
syft my-app:latest -o spdx-json > sbom.json
# 또는 cyclonedx, table

syft my-app:latest
# NAME              VERSION         TYPE
# adduser           3.118+deb12u8   deb
# apt               2.6.1           deb
# ...
# spring-boot       3.3.4           java-archive
# slf4j-api         2.0.11          java-archive
```

### Docker Scout (Docker 공식)

```bash
docker scout cves my-app:latest
docker scout compare my-app:v1 --to my-app:v2
```

GitHub Container Registry push 시 자동 분석.

---

## BuildKit

Docker의 새 빌드 엔진 (2018~). Docker 23+ 기본 활성화.

### 활성화 확인

```bash
docker buildx version
# OK면 BuildKit 활성

# 환경변수로
export DOCKER_BUILDKIT=1
```

### Cache mount

```dockerfile
# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21 AS builder
WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests
```

`/root/.m2`가 빌드 사이 유지됨 → Maven 의존성 재다운로드 X.

### Secret mount

```dockerfile
# syntax=docker/dockerfile:1.7

FROM ubuntu
RUN --mount=type=secret,id=github_token \
    git clone https://$(cat /run/secrets/github_token)@github.com/org/private-repo.git
```

```bash
docker build --secret id=github_token,src=token.txt .
```

이미지엔 시크릿 포함 안 됨. history에도 안 남음.

### SSH mount

```dockerfile
RUN --mount=type=ssh git clone git@github.com:org/private.git
```

```bash
docker build --ssh default=$SSH_AUTH_SOCK .
```

---

## Multi-platform build

ARM (Apple Silicon, AWS Graviton) + AMD64 동시:

```bash
docker buildx create --name multi --use

docker buildx build \
    --platform linux/amd64,linux/arm64 \
    -t ghcr.io/example/my-app:v1 \
    --push .
```

### 한 이미지, 두 아키텍처

```bash
docker manifest inspect ghcr.io/example/my-app:v1
# manifests:
#   - platform: linux/amd64, digest: sha256:...
#   - platform: linux/arm64, digest: sha256:...
```

pull 시 자동으로 호스트 아키텍처 선택.

### Java도 multi-platform 됨

```dockerfile
FROM eclipse-temurin:21-jre
COPY app.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

`eclipse-temurin`이 multi-arch 이미지 → buildx로 자동.

---

## Digest pinning

`tag`는 mutable. **운영은 digest로 고정**:

```dockerfile
# ❌ 권장 X
FROM eclipse-temurin:21-jre

# ✅ digest pin
FROM eclipse-temurin:21-jre@sha256:abc123def456...
```

digest 얻기:
```bash
docker pull eclipse-temurin:21-jre
docker image inspect eclipse-temurin:21-jre --format='{{.RepoDigests}}'
```

Renovate/Dependabot이 자동 PR로 업데이트.

---

## Container 서명 (Sigstore/cosign)

이미지 서명·검증으로 supply chain 보안.

```bash
# 키 페어 생성
cosign generate-key-pair

# 이미지 서명
cosign sign --key cosign.key ghcr.io/example/my-app:v1

# 검증
cosign verify --key cosign.pub ghcr.io/example/my-app:v1
```

k8s에서 admission controller로 서명 강제 가능.

---

## 운영 사례

### 사례 1 — CRITICAL CVE 발견

운영 이미지에 OpenSSL CRITICAL CVE 보고됨.

조치 (순서):
1. `trivy image --severity CRITICAL` 확인
2. 베이스 이미지 새 버전 확인 (`eclipse-temurin:21-jre` 최신)
3. PR 만들어 베이스 업데이트 + 빌드 + 재배포
4. 본인 코드 변경 X → 회귀 위험 낮음

### 사례 2 — secret이 layer에 박힘

```dockerfile
ARG API_KEY
RUN echo $API_KEY > /etc/foo               # ❌
```

`docker history`로 빌드 history 노출 + 이미지 안에 파일.

조치: BuildKit secret mount + runtime 주입.

### 사례 3 — Apple Silicon에서 안 됨

```bash
docker pull my-old-app:v1
# image platform (linux/amd64) does not match host (linux/arm64)
```

조치: multi-platform 빌드. 또는 `--platform linux/amd64`로 강제 (느림, x86 emulation).

---

## 실습 (Hands-on)

### 1단계 — Trivy 설치·스캔

```bash
# 스캔
trivy image eclipse-temurin:21-jre
trivy image alpine:3.20

# 비교
trivy image --severity HIGH,CRITICAL eclipse-temurin:21-jre
```

### 2단계 — Hadolint

```bash
# 일부러 깨진 Dockerfile
cat > Dockerfile.bad <<EOF
FROM ubuntu
RUN apt-get update
RUN apt-get install curl
ADD https://example.com/file.tar.gz /tmp/
EOF

docker run --rm -i hadolint/hadolint < Dockerfile.bad
# 다수의 경고
```

수정 후 다시.

### 3단계 — SBOM

```bash
syft my-app:latest

# JSON 저장
syft my-app:latest -o spdx-json > sbom.json
cat sbom.json | jq '.packages | length'
# 패키지 수
```

### 4단계 — BuildKit cache mount

위 multi-stage Dockerfile에 `--mount=type=cache,target=/root/.m2` 추가. 두 번 빌드 후 시간 비교.

### 5단계 — Multi-platform

```bash
docker buildx create --name mybuilder --use
docker buildx build --platform linux/amd64,linux/arm64 -t my-multi:v1 .
# Docker Hub push 또는 로컬 (--load)
```

---

## 더 읽어볼 자료

- 🔗 [Trivy](https://trivy.dev/)
- 🔗 [Hadolint](https://github.com/hadolint/hadolint)
- 🔗 [Syft](https://github.com/anchore/syft)
- 🔗 [BuildKit](https://docs.docker.com/build/buildkit/)
- 🔗 [Cosign / Sigstore](https://www.sigstore.dev/)
- 🔗 [SLSA framework](https://slsa.dev/) — supply chain security
- 🎓 SANS — Container Security 코스
