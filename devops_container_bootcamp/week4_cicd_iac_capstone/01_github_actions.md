# Day 1 — GitHub Actions

## 한 줄 요약

GitHub Actions는 **YAML로 정의한 workflow**를 GitHub에서 자동 실행. push·PR·schedule·dispatch 등 이벤트에 trigger. Marketplace의 액션을 조합. 무료 티어로도 충분.

## 학습 목표

- [ ] Workflow · Job · Step 계층
- [ ] trigger 종류 (`on:`)
- [ ] secret·env·environment
- [ ] matrix·strategy
- [ ] reusable workflow
- [ ] 비용·시간 관리

---

## 구조

```
Repository
├── .github/workflows/
│   ├── ci.yml                       ← workflow (파일 하나 = workflow 하나)
│   └── deploy.yml
```

```yaml
# .github/workflows/ci.yml
name: CI                              # workflow 이름

on:                                   # 트리거
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:                                 # 1개 이상의 job
  test:                               # job 이름
    runs-on: ubuntu-latest            # runner
    steps:                            # job은 step들로 구성
      - name: Checkout
        uses: actions/checkout@v4
      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - name: Build
        run: ./gradlew build
```

---

## Trigger — `on:`

### push / pull_request

```yaml
on:
  push:
    branches: [main, develop]
    paths:
      - 'src/**'
      - 'pom.xml'
  pull_request:
    types: [opened, synchronize, reopened]
    branches: [main]
```

### schedule

```yaml
on:
  schedule:
    - cron: '0 2 * * *'               # 매일 02:00 UTC
```

### workflow_dispatch (수동)

```yaml
on:
  workflow_dispatch:
    inputs:
      version:
        description: 'Version to deploy'
        required: true
        default: 'latest'
```

GitHub UI에서 "Run workflow" 버튼.

### 다중

```yaml
on:
  push:
    tags: ['v*']
  workflow_dispatch:
```

---

## Job

### runs-on

```yaml
jobs:
  test:
    runs-on: ubuntu-latest            # GitHub 호스트 runner
  
  windows-test:
    runs-on: windows-latest
  
  mac-test:
    runs-on: macos-latest
  
  self-hosted:
    runs-on: [self-hosted, linux, x64]  # 자체 runner
```

### needs (의존성)

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps: [...]
  
  test:
    needs: build                       # build 후
    runs-on: ubuntu-latest
    steps: [...]
  
  deploy:
    needs: [build, test]               # 둘 다 후
    runs-on: ubuntu-latest
    steps: [...]
```

### matrix

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [17, 21]
        os: [ubuntu-latest, windows-latest]
    steps:
      - uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java }}
      - run: ./gradlew build
```

→ 2 × 2 = 4 job 동시 실행.

### environment

```yaml
jobs:
  deploy-prod:
    environment: production            # 정의된 환경 사용
    runs-on: ubuntu-latest
    steps: [...]
```

GitHub repo Settings → Environments에서:
- Required reviewers (수동 승인)
- Wait timer
- Deployment branches (특정 브랜치만)
- Environment secrets

→ **운영 배포 승인 워크플로**의 표준.

---

## Step

### uses (action 사용)

```yaml
- name: Checkout
  uses: actions/checkout@v4            # GitHub 공식
  with:
    fetch-depth: 0                     # 전체 history

- name: Cache
  uses: actions/cache@v4
  with:
    path: ~/.gradle/caches
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
```

### run (셸 명령)

```yaml
- name: Build
  run: ./gradlew build

- name: Multi-line
  run: |
    echo "step 1"
    echo "step 2"

- name: 명시적 shell
  run: ./script.sh
  shell: bash                          # 또는 pwsh, python
```

### if (조건)

```yaml
- name: Deploy
  if: github.ref == 'refs/heads/main'
  run: ./deploy.sh

- name: PR comment
  if: github.event_name == 'pull_request'
  run: echo "PR"
```

### outputs / inputs

```yaml
- name: Compute
  id: compute
  run: echo "value=42" >> $GITHUB_OUTPUT

- name: Use
  run: echo "Got ${{ steps.compute.outputs.value }}"
```

---

## Secret

### Repository secret

GitHub repo Settings → Secrets and variables → Actions:
- New repository secret
- 이름: `DOCKER_PASSWORD`, 값: `...`

```yaml
- name: Docker login
  uses: docker/login-action@v3
  with:
    username: ${{ vars.DOCKER_USERNAME }}
    password: ${{ secrets.DOCKER_PASSWORD }}
```

### Environment secret

특정 environment에만:

```yaml
jobs:
  deploy-prod:
    environment: production            # production 환경의 secret 사용
    steps:
      - run: echo ${{ secrets.PROD_API_KEY }}
```

### secret이 로그에 노출 X

GitHub가 자동으로 secret 값을 `***`로 마스킹. 그러나 base64 등으로 우회는 가능 → **신뢰할 수 있는 코드만**.

### OIDC (권장)

비밀번호 없이 AWS·GCP에 인증:

```yaml
permissions:
  id-token: write
  contents: read

steps:
  - uses: aws-actions/configure-aws-credentials@v4
    with:
      role-to-assume: arn:aws:iam::123456789:role/github-actions
      aws-region: ap-northeast-2
```

GitHub의 OIDC token → AWS IAM이 검증 → 임시 자격 발급. **장기 secret 안 둠**.

---

## 캐시·Artifact

### cache

```yaml
- name: Cache Gradle
  uses: actions/cache@v4
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
    restore-keys: |
      ${{ runner.os }}-gradle-
```

### upload-artifact

```yaml
- name: Upload JAR
  uses: actions/upload-artifact@v4
  with:
    name: jar
    path: build/libs/*.jar
    retention-days: 7

# 다른 job에서
- name: Download JAR
  uses: actions/download-artifact@v4
  with:
    name: jar
    path: artifacts/
```

job 사이 파일 전달.

---

## Reusable Workflow

`.github/workflows/reusable-build.yml`:

```yaml
on:
  workflow_call:
    inputs:
      java-version:
        type: string
        default: '21'
    secrets:
      DOCKER_PASSWORD:
        required: true

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: ${{ inputs.java-version }}
          distribution: temurin
      - run: ./gradlew build
```

다른 workflow에서:

```yaml
jobs:
  call-build:
    uses: ./.github/workflows/reusable-build.yml
    with:
      java-version: '21'
    secrets:
      DOCKER_PASSWORD: ${{ secrets.DOCKER_PASSWORD }}
```

---

## 비용·시간

### 무료 티어 (public repo)

- **무제한** 무료
- public repo는 GitHub Actions 마음껏

### 무료 티어 (private repo)

- 월 **2,000분** (Linux)
- Windows는 2배 (×2 카운팅), macOS는 10배

### 초과

GitHub Free: 추가 분 구매. Team plan: 3,000분, Enterprise: 50,000분.

### 절약

- Cache 활용 (빌드 시간 50%+ 단축)
- 필요한 트리거만 (path filter)
- matrix 조합 줄이기
- `concurrency`로 중복 cancel

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

같은 브랜치 push 여러 번이면 옛 workflow 자동 취소.

---

## 자주 쓰는 Action 모음

| Action | 용도 |
|---|---|
| `actions/checkout@v4` | git clone |
| `actions/setup-java@v4` | JDK 설치 |
| `actions/setup-node@v4` | Node.js |
| `actions/setup-python@v5` | Python |
| `actions/cache@v4` | 의존성 캐시 |
| `actions/upload-artifact@v4` | 산출물 업로드 |
| `actions/download-artifact@v4` | 다운로드 |
| `docker/login-action@v3` | Docker registry login |
| `docker/build-push-action@v6` | 빌드·push |
| `docker/setup-buildx-action@v3` | BuildKit |
| `aquasecurity/trivy-action` | 보안 스캔 |
| `azure/k8s-deploy@v5` | k8s 배포 |
| `aws-actions/configure-aws-credentials@v4` | AWS OIDC |

---

## 디버깅

```yaml
- name: Debug
  run: |
    echo "GitHub ref: ${{ github.ref }}"
    echo "Event: ${{ github.event_name }}"
    echo "Actor: ${{ github.actor }}"
    env | sort
```

### Secret 디버그 (절대 echo 금지)

```yaml
- run: echo ${{ secrets.X }}              # ❌ 마스킹되지만 위험
- run: echo "$X" > /dev/null              # OK — secret이 실제 들어왔나만 확인
  env:
    X: ${{ secrets.X }}
```

### Re-run failed jobs

UI에서 "Re-run failed jobs" 클릭. 캐시 활용으로 빠름.

---

## 운영 사례

### 사례 1 — Cache key 너무 좁아 매번 miss

```yaml
key: ${{ runner.os }}-gradle-${{ github.sha }}    # ❌ sha마다 다름
# → 매번 캐시 miss
```

```yaml
key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}    # ✅
# 빌드 스크립트 변경 시에만 새 캐시
```

### 사례 2 — secret이 PR fork에 노출

PR fork에서 `pull_request` 트리거 → secret 접근 X (보안). `pull_request_target`은 access 가능하지만 위험 (악의적 PR이 secret 도용 가능).

> 신뢰할 수 있는 contributor만 trigger되는 패턴 설계 필요.

### 사례 3 — workflow 실행 시간 30분

- 단일 job, 모든 작업 순차 → 분리
- Gradle daemon 미사용 → `org.gradle.daemon=true`
- 캐시 미설정
- 큰 Docker 이미지 매번 pull

---

## 실습 (Hands-on)

### 1단계 — 첫 workflow

```yaml
# .github/workflows/hello.yml
name: Hello

on: [push, pull_request]

jobs:
  greet:
    runs-on: ubuntu-latest
    steps:
      - name: Hello
        run: echo "Hello from GitHub Actions, ${{ github.actor }}!"
      - name: Date
        run: date
```

```bash
git add . && git commit -m "ci: add hello workflow"
git push
# GitHub의 Actions 탭에서 실행 결과
```

### 2단계 — Java 빌드

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
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
      - run: ./gradlew test
      - name: Upload test report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-report
          path: build/reports/tests/
```

### 3단계 — Matrix

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [17, 21]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ matrix.java }}
      - run: ./gradlew test
```

### 4단계 — Secret

GitHub Settings → Secrets → New: `TEST_TOKEN=hello`.

```yaml
jobs:
  print:
    runs-on: ubuntu-latest
    steps:
      - run: echo "Token length=${#TT}"
        env:
          TT: ${{ secrets.TEST_TOKEN }}
```

로그에 길이만, 값은 마스킹.

### 5단계 — Manual trigger

```yaml
on:
  workflow_dispatch:
    inputs:
      env:
        type: choice
        options: [dev, staging, prod]

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: ${{ inputs.env }}
    steps:
      - run: echo "Deploying to ${{ inputs.env }}"
```

UI에서 "Run workflow" → env 선택.

---

## 더 읽어볼 자료

- 🔗 [GitHub Actions Docs](https://docs.github.com/en/actions)
- 🔗 [Actions Marketplace](https://github.com/marketplace?type=actions)
- 🔗 [Awesome Actions](https://github.com/sdras/awesome-actions)
- 🔗 [Reusable Workflows](https://docs.github.com/en/actions/using-workflows/reusing-workflows)
- 📘 『GitHub Actions Cookbook』 (Michael Kaufmann, O'Reilly)
