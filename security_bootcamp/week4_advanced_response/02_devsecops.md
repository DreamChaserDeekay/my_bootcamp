# Day 2 — DevSecOps · SAST · DAST · SCA · CI/CD 통합

> 보안을 **개발 워크플로우 안**에 녹여야 지속된다. 사람 검토만으로는 한계가 명확.

## 1. Shift Left 개념

```
요구사항 → 설계 → 코딩 → 빌드 → 테스트 → 배포 → 운영
   ↑       ↑       ↑       ↑      ↑       ↑       ↑
 위협    설계    SAST     SCA   DAST    IaC      WAF
 모델   리뷰   IDE린트  의존성  ZAP    Scan    런타임
```

**왼쪽으로 갈수록 수정 비용 ↓**. 운영 단계에서 발견하면 수정 비용이 100배.

---

## 2. SAST (Static Application Security Testing)

소스 코드를 분석. 빌드 안 해도 가능.

### 도구 (Java)
| 도구 | 특징 |
|------|------|
| **SonarQube + SonarLint** | 코드 품질 + 보안 룰 (1000+) |
| **Semgrep** | 패턴 기반, 빠름, 무료 |
| **CodeQL** (GitHub Advanced) | 강력, 정밀 |
| **Snyk Code** | DeepCode 기반 |
| **Fortify, Checkmarx, Veracode** | 엔터프라이즈 |
| **SpotBugs + FindSecBugs** | 무료, Java 전용 |
| **PMD with Security ruleset** | 무료 |

### 운영 팁
- 처음엔 false positive 많음 — **우선순위 룰만 활성**
- **신규 코드(diff)에만 적용** — 기존은 부채로 인정하고 점진
- **빌드 차단 vs 경고**: 처음에는 경고만, 익숙해지면 임계 이상 차단

### Semgrep 예
```bash
semgrep --config=auto src/
# 또는 특정 룰셋
semgrep --config=p/owasp-top-ten src/
semgrep --config=p/java src/
```
커스텀 룰도 작성 가능 (YAML).

### SpotBugs + FindSecBugs (Gradle)
```groovy
plugins {
    id 'com.github.spotbugs' version '6.0.7'
}

spotbugs {
    effort = 'max'
    reportLevel = 'low'
    excludeFilter = file('spotbugs-exclude.xml')
}

dependencies {
    spotbugsPlugins 'com.h3xstream.findsecbugs:findsecbugs-plugin:1.13.0'
}
```

---

## 3. DAST (Dynamic Application Security Testing)

실행 중인 앱에 실제 요청을 보내 분석.

### 도구
- **OWASP ZAP** — 무료, CI 통합 잘 됨
- **Burp Suite Pro** — 강력
- **Nuclei** — 템플릿 기반, 빠름
- **Acunetix, Netsparker** — 상용

### ZAP CI 통합
```yaml
# GitHub Actions
- name: ZAP Baseline Scan
  uses: zaproxy/action-baseline@v0.10.0
  with:
    target: https://staging.myapp.com
    rules_file_name: '.zap/rules.tsv'
    cmd_options: '-a'
```

### Authenticated Scan
DAST의 한계: 로그인 뒤 페이지는 인증 설정 필요. ZAP의 **Authentication Context** 또는 사전 로그인 스크립트.

### IAST (Interactive)
앱 내부에 에이전트를 심어 동적+정적 결합. Contrast Security 등. 보통 상용.

---

## 4. SCA (Software Composition Analysis)

의존성 CVE 스캔. **A06**의 핵심 대응.

### 도구
- **OWASP Dependency-Check** (무료)
- **Snyk Open Source**
- **GitHub Dependabot** (무료)
- **Renovate** (Bot)
- **Trivy** (의존성 + 컨테이너)
- **JFrog Xray**

### Gradle
```groovy
plugins {
    id 'org.owasp.dependencycheck' version '9.0.7'
}

dependencyCheck {
    failBuildOnCVSS = 7.0   // CVSS 7+ 에서 빌드 실패
    formats = ['HTML', 'JSON', 'SARIF']
    nvd {
        apiKey = System.getenv('NVD_API_KEY')   // 더 빠른 갱신
    }
}
```

### Dependabot
`.github/dependabot.yml`:
```yaml
version: 2
updates:
  - package-ecosystem: gradle
    directory: /
    schedule:
      interval: weekly
    open-pull-requests-limit: 10
    groups:
      spring:
        patterns: ["org.springframework*"]
```

### 라이선스 점검
의존성의 라이선스도 자동 체크. GPL 코드를 상용 SaaS에 못 쓰는 등.

---

## 5. IaC (Infrastructure as Code) Scan

Terraform, CloudFormation, Kubernetes YAML 등 인프라 정의 파일도 보안 검토.

### 도구
- **Checkov** (Bridgecrew/Prisma, 무료, 강력)
- **tfsec** (Aqua)
- **Terrascan**
- **KICS** (Checkmarx)
- **Kubescape, Kubesec** (K8s)
- **conftest** + OPA Rego

### Checkov 예
```bash
checkov -d . --framework terraform
```

발견 예: S3 public, 보안 그룹 0.0.0.0/0, 암호화 없는 RDS, 콘솔 로그인 MFA 없음 등.

---

## 6. Secret 스캐닝

### 도구
- **gitleaks** (무료, 매우 강력)
- **trufflehog** (히스토리 + 엔트로피)
- **detect-secrets** (Yelp)
- **GitHub Secret Scanning** (무료, 푸시 차단 옵션)

### Pre-commit Hook
```yaml
# .pre-commit-config.yaml
repos:
- repo: https://github.com/gitleaks/gitleaks
  rev: v8.18.1
  hooks:
  - id: gitleaks
```

### 누출 시 진짜 대응
1. **즉시 키 회전** (git 히스토리 청소보다 우선)
2. 누구·언제·어디서 사용했는지 추적
3. 영향 평가
4. 히스토리 청소 (`git filter-repo`) — 다만 협업하는 모두에게 영향
5. **누출 방지** 시스템 점검 (pre-commit, push 차단)

---

## 7. 컨테이너 이미지 + IaC 스캔

CI에서:
```yaml
# GitHub Actions 예
- name: Build image
  run: docker build -t myapp:${{ github.sha }} .

- name: Trivy scan
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: myapp:${{ github.sha }}
    severity: HIGH,CRITICAL
    exit-code: '1'
    format: 'sarif'
    output: 'trivy.sarif'

- name: Upload to GitHub Security
  uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: trivy.sarif
```

---

## 8. CI/CD 파이프라인 — 통합 예 (GitHub Actions)

```yaml
name: Security Pipeline

on: [push, pull_request]

jobs:
  security:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      security-events: write
    steps:
    - uses: actions/checkout@v4
      with:
        fetch-depth: 0     # gitleaks 전체 히스토리

    - name: Gitleaks
      uses: gitleaks/gitleaks-action@v2

    - uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Semgrep
      uses: returntocorp/semgrep-action@v1
      with:
        config: p/java p/owasp-top-ten p/security-audit

    - name: SpotBugs + FindSecBugs
      run: ./gradlew spotbugsMain

    - name: Dependency-Check
      run: ./gradlew dependencyCheckAnalyze
      env:
        NVD_API_KEY: ${{ secrets.NVD_API_KEY }}

    - name: Build image
      run: docker build -t myapp:test .

    - name: Trivy
      uses: aquasecurity/trivy-action@master
      with:
        image-ref: myapp:test
        severity: HIGH,CRITICAL
        exit-code: '1'

    - name: Checkov (IaC)
      uses: bridgecrewio/checkov-action@master
      with:
        directory: ./infra/

  dast:
    needs: security
    runs-on: ubuntu-latest
    steps:
    - name: Start app
      run: docker compose up -d
    - name: ZAP Baseline
      uses: zaproxy/action-baseline@v0.10.0
      with:
        target: http://localhost:8080
```

### 빌드 정책
| 결과 | 동작 |
|------|------|
| Secret 발견 | 무조건 차단 |
| Critical CVE 의존성 | 차단 |
| Critical SAST | 차단 |
| High | 경고 + 리뷰 필요 |
| Medium/Low | 정보성 |

---

## 9. Pre-commit / IDE 통합

### Pre-commit
```yaml
repos:
- repo: https://github.com/gitleaks/gitleaks
  rev: v8.18.1
  hooks: [{id: gitleaks}]
- repo: https://github.com/returntocorp/semgrep
  rev: v1.45.0
  hooks: [{id: semgrep, args: ['--config=p/java', '--error']}]
```

### IDE
- **SonarLint** (IntelliJ/VSCode)
- **Snyk plugin**
- **Semgrep plugin**

문제 발견을 **타이핑 중**으로 앞당김.

---

## 10. SBOM·서명·공급망

### SBOM
모든 의존성 목록. CVE 대응 시 영향 평가에 필수.
```groovy
plugins {
    id 'org.cyclonedx.bom' version '1.8.2'
}
// ./gradlew cyclonedxBom → build/reports/bom.json
```

### Cosign 이미지 서명
```bash
cosign generate-key-pair
cosign sign --key cosign.key myregistry/myapp:1.0.0
cosign verify --key cosign.pub myregistry/myapp:1.0.0
```

### SLSA Framework
공급망 무결성 레벨 (1~4). 점진적 도입.

---

## 11. Pen Test / Bug Bounty

### 외부 Pen Test
- 분기·반기 정기
- 새 큰 기능 출시 전
- 컴플라이언스 요건 (PCI-DSS 등)
- 사후 보고서 → 백로그·KPI

### Bug Bounty
- 직접 운영(Responsible Disclosure 페이지) 또는 플랫폼(HackerOne, Bugcrowd)
- 범위(Scope), 보상, 응답 SLA 명시
- 안전한 보고 채널(security@)

---

## 12. 실습

### 실습 2.1 — vulnerable_app 에 SAST 적용
- Semgrep `--config=p/java`
- SpotBugs + FindSecBugs
- 발견 항목을 OWASP Top 10 매핑

### 실습 2.2 — 본인 사이드 프로젝트 CI에 보안 잡 추가
GitHub Actions에 위 §8 워크플로우 일부 적용. PR 1개로 정리.

### 실습 2.3 — Pre-commit + gitleaks
설치 후 일부러 가짜 키 커밋 시도 → 차단 확인.

### 실습 2.4 — SBOM 생성·관리
SBOM 생성 → OWASP Dependency-Track 또는 GitHub에 업로드.

### 실습 2.5 — Pen Test 보고서 샘플 읽기
HackerOne의 공개 리포트(Hacktivity) 10개 읽고 카테고리 분류.

---

## 정리 — DevSecOps 우선순위
1. **Secret Scanning** (즉시 효과)
2. **SCA / Dependency-Check** (Log4Shell 같은 사고 방지)
3. **IaC Scan** (클라우드 misconfig)
4. **SAST** (점진적, false positive 관리)
5. **DAST** (배포 후 실행 가능 환경)
6. **이미지 스캔**

처음에는 한두 개부터, 팀이 익숙해지면 추가.
