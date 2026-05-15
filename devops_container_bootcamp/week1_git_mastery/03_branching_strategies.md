# Day 3 — 브랜칭 전략

## 한 줄 요약

브랜칭 전략은 **팀 규모·릴리스 빈도·환경**에 따라 다르다. **trunk-based**가 modern CD에 최적, **git-flow**는 릴리스 주기 긴 옛 모델, **github flow**가 그 사이. 정답은 없지만 잘못된 선택은 명확.

## 학습 목표

- [ ] 3가지 주요 전략을 비교한다 (trunk-based / github flow / git flow)
- [ ] 각 전략에 맞는 팀·제품 형태
- [ ] feature toggle의 역할
- [ ] release 브랜치·hotfix 처리
- [ ] 환경별 브랜치(env-branch) 안티패턴 이해
- [ ] 금융권에서 흔한 브랜칭 패턴

---

## 3대 전략 한눈에

### 1) Trunk-Based Development (TBD)

```
   main ────●──●──●──●──●──●──●──●──▶  (deployable always)
            ↑      ↑      ↑      ↑
        short-lived feature branches (< 1 day)
```

- **main 한 줄**이 항상 deployable
- feature 브랜치는 **하루 이내**
- merge는 작고 자주 (PR당 < 400줄 권장)
- feature toggle로 미완 기능 숨김
- Google·Meta·Netflix 등 대형 테크 표준

### 2) GitHub Flow

```
   main ────●──●──●──●──●──●──▶
              \   \   \
               feature/A
                   feature/B
                       feature/C
```

- main 외 모든 브랜치는 feature
- 각 feature 브랜치는 PR로 main에 merge
- merge = deploy (CD 전제)
- 오픈소스·SaaS에 적합

### 3) Git Flow (옛, 무거움)

```
   main ────●──────────●──────────▶  (production tag)
            ↑          ↑
           release   release
            ↑          ↑
   develop ─●──●──●──●─●──●──●──▶
            ↑   ↑   ↑
         feature/A  hotfix
            feature/B
```

- main = production tagged
- develop = next release
- feature/* / release/* / hotfix/*
- 릴리스 주기 긴 패키지 소프트웨어에 적합
- **모던 웹 서비스엔 무거움** (대부분 안 씀)

---

## 비교표

| 항목 | Trunk-Based | GitHub Flow | Git Flow |
|---|---|---|---|
| 메인 브랜치 수 | 1 | 1 | 2 (main, develop) |
| Feature 브랜치 수명 | 하루 | 며칠 | 주~달 |
| 릴리스 빈도 | 일 다회 | 일 1회+ | 주~달 |
| feature toggle | 필수 | 권장 | 선택 |
| 학습 곡선 | 낮음 | 매우 낮음 | 높음 |
| 적합 환경 | SaaS, 대형 | SaaS, OSS | 패키지 SW |
| 한국 금융권 | 거의 없음 | 일부 | **다수** |

---

## Trunk-Based 깊이

### 핵심 원칙

1. **main이 항상 deployable**
2. feature 브랜치 < 1일
3. PR < 400줄
4. 미완성 기능은 **feature flag**로 숨김
5. CI가 모든 PR을 거치며 자동 테스트

### Feature Flag 예

```java
@Service
public class NewPaymentService implements PaymentService {
    @Autowired FeatureFlag flag;
    @Autowired LegacyPaymentService legacy;
    @Autowired NewPaymentImpl newImpl;
    
    public Result pay(Order o) {
        if (flag.isOn("payment.v2", o.user)) {
            return newImpl.pay(o);
        }
        return legacy.pay(o);
    }
}
```

- 새 기능은 일단 비활성 상태로 merge
- 백오피스에서 점진 활성화 (특정 유저 → 1% → 10% → 100%)
- 문제 발견 즉시 OFF로 롤백 (배포 X)

플랫폼: LaunchDarkly, GrowthBook, Unleash, ConfigCat.

### TBD의 장점

- 통합 비용 최소 (작은 PR 자주)
- 트렁크 한 줄 → 의존성 충돌 적음
- 모든 환경에 같은 binary → 진짜 "Build Once"
- 릴리스 결정 = deploy 버튼 (브랜치 작업 X)

### TBD의 도전

- 모든 PR이 main에 들어가야 하니 **테스트 자동화 필수**
- feature flag 관리 (오래된 flag 청소)
- 미완성 UI는? → 라우트 자체 비활성

---

## Git Flow 비판

처음 제안된 글에 작성자 본인이 추가:

> "If your team is doing continuous delivery, I would suggest to adopt a much simpler workflow (like GitHub Flow)"
> — Vincent Driessen, A successful Git branching model

요약: **Git Flow는 패키지 소프트웨어용. SaaS엔 부적합**.

### Git Flow의 한국 금융권

여전히 흔함. 이유:
- 릴리스 = 망분리 환경 배포 = 주·월 단위 / 형상 관리 표준
- 승인·테스트 단계가 많음 → release 브랜치 자연스러움
- 운영 안정성 최우선

→ **있는 그대로 두기**보다 **이해하고 합리적 조정** 권장.

---

## 환경별 브랜치 — 안티패턴

```
   feature ─▶ develop ─▶ release ─▶ master
                ↑           ↑           ↑
              dev env    staging      prod
```

이 패턴이 흔한데 **여러 문제**:
- "dev에서 됐는데 prod에선 안 됨" → 무엇이 다른가? 코드? 환경? 알 수 없음
- 브랜치 merge 충돌이 환경마다 발생
- staging의 한 fix가 production으로 가는 길이 복잡

### 대안 — 한 commit, 여러 환경

```
   main ───●──●──●──●──●──●──▶
            ↓     ↓     ↓     ↓
          dev   stg   prod
```

- 같은 commit(=같은 이미지)을 환경마다 promote
- 환경 차이는 **환경변수**로만
- "dev에서 됐는데" 문제가 코드가 아닌 환경/config에 있음을 즉시 알 수 있음

이게 **12-Factor App**의 III. Config 원칙.

---

## Release · Hotfix 처리

### Git Flow 스타일

```bash
# release 브랜치 시작
git checkout -b release/1.2.0 develop

# 버그픽스만, 새 feature X
git commit -m "fix: rounding"

# 완료 시
git checkout main
git merge release/1.2.0
git tag v1.2.0

git checkout develop
git merge release/1.2.0
git branch -d release/1.2.0
```

### Hotfix (production 긴급)

```bash
git checkout -b hotfix/security-patch main

# 수정
git commit -m "fix: SQL injection"

# main · develop 둘 다에
git checkout main
git merge hotfix/security-patch
git tag v1.2.1

git checkout develop
git merge hotfix/security-patch
```

### Trunk-Based 스타일

main에 즉시 fix → CI → main 새 배포. release 브랜치 없음.

---

## PR 크기·합치는 방식

### PR 크기 가이드

| | 권장 |
|---|---|
| 줄 수 | < 400 줄 (review 가능) |
| 파일 수 | < 20 |
| 변경 commit 수 | 1~5 (squash 권장) |
| 리뷰 시간 | 30분 이내 |

> 큰 PR은 작은 PR 여러 개로. 의존성 있으면 stack PR 패턴.

### GitHub의 3가지 merge

| 방식 | 결과 |
|---|---|
| **Create a merge commit** | merge commit 만들음 (history 보존) |
| **Squash and merge** | feature commit들을 하나로 합쳐 main에 |
| **Rebase and merge** | feature commit들을 main 위에 선형으로 |

**Squash and merge가 대부분 좋다** — main history가 PR 단위로 깔끔.

---

## 운영 사례

### 사례 1 — Git Flow에서 hotfix 복잡

production 사고 → hotfix → main에 적용. 그런데 develop에는 5개 release를 합쳐서 못 따라잡음. develop은 production과 다른 상태.

해결: **trunk-based로 옮기거나** release 브랜치를 짧게.

### 사례 2 — 환경별 브랜치 → "dev에서 됐는데 prod에선 안 됨"

config 파일이 환경마다 브랜치에 박혀있음. promote할 때 빠뜨림. 같은 일이 반복.

해결:
- 환경별 브랜치 폐기
- 같은 commit, 환경변수만 다름
- Helm values·k8s ConfigMap 활용

### 사례 3 — Feature Flag 청소 안 됨

3년 전 추가된 flag 50개가 남아있음. 코드에 if 폭주.

해결: **flag 만들 때 cleanup 날짜 기록**. 라이브러리에 expiration 기능 (LaunchDarkly의 `temporary` 등).

---

## 실습 (Hands-on)

### 1단계 — Trunk-Based 시뮬레이션

```bash
# 깨끗한 repo
git init tbd-demo && cd tbd-demo
echo "v1" > app.txt && git add . && git commit -m "initial"

# 작은 feature 브랜치 (10분 분량)
git checkout -b add-feature
echo "v2 with feature A" > app.txt && git add . && git commit -m "feat: A"
git checkout main
git merge --no-ff add-feature -m "merge: feat A"
git branch -d add-feature

git log --oneline --graph
```

### 2단계 — GitHub Flow 시뮬레이션

GitHub에서 새 repo. clone → branch → push → PR → merge → 다시.

```bash
git checkout -b feature/login
# 작업
git push -u origin feature/login
# GitHub에서 PR → review → squash merge
git checkout main
git pull
git branch -d feature/login
```

### 3단계 — Feature Flag 예제

```java
public class FlagBasedService {
    private final boolean newAlgo = System.getenv("FEATURE_NEW_ALGO") != null;
    
    public Result process(Input x) {
        if (newAlgo) {
            return newImpl(x);
        }
        return legacyImpl(x);
    }
}
```

같은 코드, 환경변수만 바꿔서 ON/OFF 비교.

### 4단계 — 환경별 vs 환경변수 비교

```yaml
# bad: 환경별 application.yml을 브랜치마다 다르게
# good: application.yml은 하나, ${ENV_VAR}로

server:
  port: ${PORT:8080}
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

---

## 더 읽어볼 자료

- 🔗 [Trunk-Based Development](https://trunkbaseddevelopment.com/)
- 🔗 [GitHub Flow](https://docs.github.com/en/get-started/quickstart/github-flow)
- 🔗 [A successful Git branching model](https://nvie.com/posts/a-successful-git-branching-model/) — 원조 Git Flow + 후기
- 📘 『Accelerate』 (Nicole Forsgren, Jez Humble, Gene Kim)
  - DORA 연구 — 짧은 통합주기·trunk-based가 성과 좋음을 데이터로
- 📘 『Continuous Delivery』 (Jez Humble)
- 🔗 [Feature Flags Best Practices](https://launchdarkly.com/blog/best-practices-feature-flags/)
- 🎓 InfoQ — "Trunk-Based Development at Google"
