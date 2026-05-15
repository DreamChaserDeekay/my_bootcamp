# Lab 7 — GitHub Actions CI/CD 파이프라인

## 목표

- Spring Boot 앱의 CI/CD 풀 파이프라인을 GitHub Actions로
- 빌드·테스트·이미지·스캔·배포 단계
- 환경별 deploy gating

---

## 1단계 — GitHub repo·앱 준비

```powershell
# 새 repo
mkdir devops-lab
cd devops-lab
git init
git remote add origin https://github.com/<you>/devops-lab.git

# Spring Boot 앱 (위 캡스톤의 simple-app 활용)
# build.gradle, src/, Dockerfile
```

기본 구조:
```
devops-lab/
├── build.gradle
├── settings.gradle
├── gradlew
├── gradle/wrapper/
├── src/
├── Dockerfile
└── .github/workflows/
```

---

## 2단계 — 첫 CI workflow

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read
  packages: write
  security-events: write

env:
  IMAGE: ghcr.io/${{ github.repository }}

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
          restore-keys: ${{ runner.os }}-gradle-
      - name: Run tests
        run: ./gradlew test
      - name: Upload reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: build/reports/tests/
```

```bash
git add . && git commit -m "ci: add basic CI"
git push -u origin main
```

GitHub Actions 탭에서 실행 확인.

---

## 3단계 — Build·image·push 단계 추가

```yaml
  image:
    needs: test
    if: github.event_name != 'pull_request'
    runs-on: ubuntu-latest
    outputs:
      sha: ${{ steps.tag.outputs.sha }}
    steps:
      - uses: actions/checkout@v4
      
      - id: tag
        run: echo "sha=$(git rev-parse --short HEAD)" >> $GITHUB_OUTPUT
      
      - name: Setup BuildKit
        uses: docker/setup-buildx-action@v3
      
      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build·push
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            ${{ env.IMAGE }}:${{ steps.tag.outputs.sha }}
            ${{ env.IMAGE }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max
          labels: |
            org.opencontainers.image.source=https://github.com/${{ github.repository }}
            org.opencontainers.image.revision=${{ github.sha }}
            org.opencontainers.image.created=${{ github.event.head_commit.timestamp }}
```

push → GHCR에 image 올라감. GitHub repo의 "Packages" 탭에서 확인.

---

## 4단계 — Trivy 스캔 추가

```yaml
      - name: Trivy scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ env.IMAGE }}:${{ steps.tag.outputs.sha }}
          severity: HIGH,CRITICAL
          exit-code: '0'                # 학습용으로 fail 안 함
          format: sarif
          output: trivy.sarif
      
      - name: Upload SARIF
        if: always()
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: trivy.sarif
```

GitHub repo의 "Security" → "Code scanning"에서 결과.

---

## 5단계 — Hadolint·lint 추가

`test` job에 step 추가:

```yaml
      - name: Hadolint
        uses: hadolint/hadolint-action@v3
        with:
          dockerfile: Dockerfile
          no-fail: true                  # 경고만, 실패 X
      
      - name: yamllint
        if: hashFiles('**/*.yaml') != ''
        run: |
          pip install yamllint
          yamllint . || true
```

---

## 6단계 — Environment + 수동 승인 (운영 시뮬)

GitHub Settings → Environments → "production" 생성:
- Required reviewers: 본인
- Wait timer: 0
- Deployment branches: main

workflow에 deploy job 추가:

```yaml
  deploy-dev:
    needs: image
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    environment: development
    steps:
      - name: Notify
        run: |
          echo "Would deploy ${{ env.IMAGE }}:${{ needs.image.outputs.sha }} to dev"
          # 실제론 kubectl/helm/argocd CLI
  
  deploy-prod:
    needs: deploy-dev
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    environment: production            # 승인 필요
    steps:
      - name: Notify
        run: |
          echo "Deploying ${{ env.IMAGE }}:${{ needs.image.outputs.sha }} to prod"
```

push → Actions 탭에서 deploy-prod가 "Waiting for review" → 본인이 승인.

---

## 7단계 — Matrix·캐시 최적화

```yaml
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [17, 21]                # 두 버전 동시 테스트
      fail-fast: false                # 하나 실패해도 나머지 진행
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ matrix.java }}
      - name: Cache
        uses: actions/cache@v4
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ matrix.java }}-${{ hashFiles('**/*.gradle*') }}
      - run: ./gradlew test
```

---

## 8단계 — PR 빌드 차별화

PR에선 image push 안 함 (위 `if: github.event_name != 'pull_request'`).

PR엔 자동 코멘트로 결과:

```yaml
  pr-comment:
    needs: test
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/github-script@v7
        with:
          script: |
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: '✅ CI passed!'
            });
```

---

## 9단계 — 부가 — Codecov

`build.gradle`에 jacoco 플러그인 추가:

```gradle
plugins {
    id 'jacoco'
}

test {
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    reports {
        xml.required = true
    }
}
```

workflow:

```yaml
      - run: ./gradlew test jacocoTestReport
      
      - name: Codecov
        uses: codecov/codecov-action@v4
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
          files: ./build/reports/jacoco/test/jacocoTestReport.xml
```

(Codecov.io 계정 필요, 무료)

---

## 10단계 — Release tag로 별도 처리

```yaml
on:
  push:
    branches: [main]
    tags: ['v*']
  pull_request:

jobs:
  release:
    if: startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Get version
        id: ver
        run: echo "version=${GITHUB_REF#refs/tags/v}" >> $GITHUB_OUTPUT
      - name: Image with version tag
        # ... (위와 비슷하지만 tag: v${{ steps.ver.outputs.version }})
      - name: Create GitHub release
        uses: softprops/action-gh-release@v2
        with:
          generate_release_notes: true
```

```bash
git tag v1.0.0
git push origin v1.0.0
# → release job 실행
```

---

## 산출물 체크리스트

- [ ] CI workflow가 PR·push에 트리거
- [ ] test → build → image → scan 흐름
- [ ] GHCR에 이미지 push
- [ ] Trivy 결과 GitHub Security 탭에
- [ ] PR에선 image 안 만들음
- [ ] Environment + 수동 승인
- [ ] cache로 빌드 시간 단축
- [ ] Matrix로 여러 Java 버전 테스트
- [ ] Release tag 자동화

---

## 트러블슈팅

### "permission denied: GHCR"

`permissions:` 블록에 `packages: write` 있는지. Settings → Actions → General → Workflow permissions: "Read and write".

### "test: ./gradlew not found"

```bash
chmod +x gradlew
git update-index --chmod=+x gradlew
git commit -m "chmod gradlew"
```

### "cache miss every time"

`hashFiles` 패턴 확인. `**/build.gradle*`가 정확한가? gradle wrapper의 properties도 포함하면 더 정확.

---

## 다음 단계

[Week 4 Checklist](../checklist.md) → [Capstone](../05_capstone.md)
