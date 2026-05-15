# Day 4 — 코드 리뷰 문화

## 한 줄 요약

코드 리뷰는 단순 결함 검출이 아닌 **지식 공유·코드베이스 일관성·신뢰 형성**의 도구. 좋은 commit 메시지·작은 PR·명확한 리뷰 코멘트가 전체 팀 속도를 결정한다.

## 학습 목표

- [ ] 좋은 commit 메시지 작성 (Conventional Commits)
- [ ] PR 설명·체크리스트 작성
- [ ] 리뷰어로서 좋은 코멘트
- [ ] 리뷰이로서 받아들이는 자세
- [ ] CODEOWNERS·branch protection rules
- [ ] AI 리뷰 도구 활용

---

## 좋은 commit 메시지

### Conventional Commits

```
<type>(<scope>): <subject>

<body>

<footer>
```

| Type | 의미 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서 |
| `style` | 포맷 (코드 의미 변화 X) |
| `refactor` | 기능 변화 없는 코드 개선 |
| `perf` | 성능 |
| `test` | 테스트 |
| `chore` | 빌드·도구 |
| `ci` | CI/CD 설정 |
| `revert` | 되돌리기 |

### 예시

```
feat(auth): JWT refresh token 발급 API 추가

기존 access token만 발급했으나 만료 시 재로그인이 필요했음.
refresh token을 추가하여 14일간 자동 갱신 가능.

- POST /auth/refresh 엔드포인트
- RefreshToken 엔티티·repository
- 회전(rotation) 전략으로 보안 강화

Closes #432
```

### 50/72 규칙

- subject: **50자 이내**, 명령형 ("Add" not "Added")
- body: 한 줄당 **72자 이내**, why를 중심으로

### Bad vs Good

```
❌ "fix"
❌ "fix bug"
❌ "Fixed the issue where users couldn't log in to the system"

✅ "fix(auth): 빈 이메일에 대한 NPE 방지"
✅ "feat(payment): VAN 결제 응답 timeout 5초로 단축"
```

### 왜 중요한가

- `git log --grep`으로 검색
- `git blame`으로 누가 왜 그렇게 했는지 추적
- PR 생성 시 자동으로 description 채워짐 (squash 시)
- 자동 변경로그(CHANGELOG.md) 생성 가능

---

## 작은 PR

### 통계 — 리뷰 효율

| PR 크기 | 리뷰 발견율 | 시간 |
|---|---|---|
| < 200줄 | 50% | 15분 |
| 200~400줄 | 30% | 30분 |
| 400~1000줄 | 15% | 1시간+ |
| > 1000줄 | 5%? | "LGTM 도장" |

큰 PR은 사실상 리뷰 안 됨.

### 큰 변경을 작게 나누기

1. **별도 commit 시리즈**: refactor → feature → tests
2. **별도 PR 시리즈** (stack PR): #1 인터페이스, #2 구현, #3 통합
3. **feature flag**: 미완성도 main에 (toggle OFF)

---

## PR 작성

### PR 템플릿 예

`.github/pull_request_template.md`:

```markdown
## What
무엇을 변경했나? 1~3문장.

## Why
왜? 이슈 번호·배경.

## How
구현 방식·고민한 트레이드오프.

## Test plan
- [ ] 단위 테스트 추가
- [ ] 수동 시나리오 A, B, C
- [ ] 영향받는 기능 회귀 확인

## Screenshots (if UI)

## Related
Closes #123
```

### 좋은 PR의 시그널

- 제목 < 70자
- 본문에 **What/Why/How**
- 스크린샷·재현 단계 (해당 시)
- 자가 리뷰 표시 (`Self-reviewed`)
- 작음 (< 400줄)
- 테스트 같이

---

## 리뷰어 가이드

### 우선순위

```
1. 정확성 (버그·보안)
2. 설계 (이 변경이 옳은 추상화인가)
3. 가독성·유지보수성
4. 일관성 (codebase 스타일)
5. 스타일 (포맷·이름 — 자동화로)
```

### 코멘트 강도 (suggested labels)

| Prefix | 의미 |
|---|---|
| **blocking:** | 머지 전 반드시 해결 |
| **nit:** | 사소함, 무시해도 됨 |
| **question:** | 이해가 안 됨 |
| **suggestion:** | 다른 방법 제안 |
| **praise:** | 좋은 부분 칭찬 (의외로 중요) |

```
nit: 변수명 `cnt`보다 `count`가 더 읽힘
suggestion: 이 부분은 stream으로 더 짧을 듯 (필수는 아님)
blocking: 이 SQL은 사용자 입력을 concat하고 있음 → injection
praise: 이 helper로 중복 제거한 게 깔끔합니다
```

### 안 좋은 리뷰

```
❌ "왜 이렇게 했어요?"          → 공격적, 정보 없음
✅ "여기서 X 패턴을 고른 이유가 궁금합니다. Y 패턴이 ... 같이 더 적합해 보여서요"

❌ "다시 짜야 함"                → 무엇을, 어떻게?
✅ "이 함수가 너무 많은 일을 함. A, B, C로 분리 제안. ..."

❌ "내가 했으면 ..."              → 자기 자랑
✅ 그냥 suggestion으로
```

### 자동화 가능한 건 자동화

- 포맷·스타일: Prettier·Spotless·Checkstyle
- 임포트 순서: IDE auto-fix
- 일반적 안티패턴: SonarQube·SpotBugs

→ **사람은 설계·로직만**.

---

## 리뷰이 가이드

### PR 보내기 전 self-review

```
□ 빌드·테스트 통과
□ 자기 PR을 처음 보는 사람으로서 본 적 있나
□ 디버그 코드/주석 제거
□ TODO 정리 또는 명시
□ 보안 민감 데이터 (.env, key) 안 들어갔나
□ 큰 binary 안 들어갔나
□ 의도 명확한 commit 메시지
```

### 리뷰 받는 자세

- "내 코드"가 아닌 "**우리 코드베이스**"
- 동의 안 되는 부분은 **이유와 함께** 반박
- nit이라도 무시 X (보통 더 큰 패턴의 시그널)
- 큰 변경 요구는 **별도 PR**로 협상

---

## CODEOWNERS

`.github/CODEOWNERS`:

```
# 글로벌 fallback
*       @team-backend

# 디렉토리별
/auth/  @alice @bob
/api/   @team-api

# 파일 종류
*.tf    @team-platform
```

특정 경로 변경 시 자동으로 owner를 reviewer로 지정. GitHub branch protection과 함께 강제 가능.

---

## Branch Protection Rules (GitHub)

`Settings → Branches → Add rule` for `main`:

```
□ Require a pull request before merging
  □ Require approvals: 1 (또는 2)
  □ Dismiss stale pull request approvals when new commits are pushed
  □ Require review from Code Owners
□ Require status checks to pass before merging
  □ Require branches to be up to date
  □ Status checks: CI / build, CI / test, CI / lint
□ Require conversation resolution before merging
□ Require linear history (rebase·squash만 허용)
□ Do not allow bypassing the above settings
□ Restrict who can push to matching branches
```

---

## 자동화 도구

### Linter / Formatter (Java)

```gradle
plugins {
    id 'com.diffplug.spotless' version '6.25.0'
    id 'checkstyle'
    id 'pmd'
}

spotless {
    java {
        googleJavaFormat('1.22.0')
        removeUnusedImports()
    }
}
```

### Pre-commit hook

```yaml
# .pre-commit-config.yaml
repos:
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v4.6.0
    hooks:
      - id: trailing-whitespace
      - id: end-of-file-fixer
      - id: check-merge-conflict
      - id: check-yaml
      - id: detect-private-key       # secrets 방지
```

```bash
pre-commit install      # 한 번만
# 이후 commit마다 자동 실행
```

### Conventional Commits 강제

```bash
npm install -g @commitlint/cli @commitlint/config-conventional
echo "module.exports = {extends: ['@commitlint/config-conventional']}" > commitlint.config.js
```

husky로 commit hook 등록 → 형식 안 맞으면 commit 거부.

### CI에서 검사

GitHub Actions:

```yaml
name: PR Lint
on: pull_request
jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: wagoid/commitlint-github-action@v6
      - uses: amannn/action-semantic-pull-request@v5
```

---

## AI 리뷰 도구

| 도구 | 특징 |
|---|---|
| **GitHub Copilot Pull Request review** | GitHub 내장, 무료 (Copilot 구독자) |
| **Claude Code** | 에디터 + PR review |
| **Cursor** | IDE 내장 |
| **CodeRabbit** | PR 자동 리뷰 봇 |
| **Codacy** | 정적 분석 + AI |
| **GitGuardian** | 시크릿 누출 탐지 |

### AI 리뷰 활용 원칙

- AI는 **첫 패스**, 사람이 **최종 결정**
- AI가 놓치는 도메인 지식·아키텍처 결정
- AI 코멘트도 잘못된 경우 많음 — 신뢰하되 검증

---

## 운영 사례

### 사례 1 — "LGTM"만 받는 PR

PR이 거대 → 리뷰어 부담 → 대충 보고 통과 → 버그 자주.

해결:
- PR 크기 제한 (CI에서 자동 경고)
- pair programming으로 사전 협의
- stack PR로 분할

### 사례 2 — 리뷰가 며칠 걸림

리뷰가 한 사람에게 몰림 → 대기 → 의욕 저하.

해결:
- CODEOWNERS로 분산
- SLA: 24시간 내 첫 응답
- 짧은 daily stand-up에서 stuck PR 공유

### 사례 3 — Conventional commits 안 지킴

규칙은 있지만 강제 X → 안 지키는 사람들 → 메시지 자동화 깨짐.

해결: commitlint + husky + CI 강제.

---

## 실습 (Hands-on)

### 1단계 — PR 템플릿 만들기

`.github/pull_request_template.md`을 위 예시로 생성, push, PR 만들어 자동 적용 확인.

### 2단계 — Conventional Commits 강제

```bash
# commitlint 설치
npm init -y
npm install --save-dev @commitlint/{config-conventional,cli} husky

# config
cat > commitlint.config.js <<EOF
module.exports = {extends: ['@commitlint/config-conventional']};
EOF

# hook 등록
npx husky init
echo "npx commitlint --edit \$1" > .husky/commit-msg
```

```bash
git commit -m "stuff"          # 거부
git commit -m "feat: stuff"    # 통과
```

### 3단계 — Branch Protection 설정

GitHub repo Settings → Branches → main에 위 규칙 적용 → PR 없이 push 시도 → 거부됨.

### 4단계 — CODEOWNERS

`.github/CODEOWNERS` 생성, 본인 GitHub 계정 적기. PR 생성 시 자동으로 reviewer 지정 확인.

---

## 더 읽어볼 자료

- 📘 『The Pragmatic Programmer』 — 코드 리뷰 부분
- 📘 『Code Complete』 2nd (Steve McConnell)
- 🔗 [Google Engineering Practices](https://google.github.io/eng-practices/review/)
- 🔗 [Conventional Commits](https://www.conventionalcommits.org/)
- 🔗 [GitHub - About Pull Request Reviews](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/reviewing-changes-in-pull-requests/about-pull-request-reviews)
- 🎓 SmartBear — "Best Kept Secrets of Peer Code Review"
- 📘 『Software Engineering at Google』 — 9장 Code Review
