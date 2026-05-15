# Week 1 — Git 마스터리

## 주차 목표

Git이 단지 `commit/push`가 아니라 **컨텐츠 주소화 저장소(content-addressable storage)**임을 안다. 어떤 명령이 어떤 객체를 어떻게 바꾸는지 그릴 수 있고, 사고가 났을 때 `reflog`로 복구할 수 있다. 팀 단위 코드 리뷰·브랜칭 전략을 주도한다.

---

## 일정

| Day | 주제 | 핵심 |
|---|---|---|
| Day 1 | [Git 내부 구조](01_git_internals.md) | blob/tree/commit/tag, refs, .git/ |
| Day 2 | [rebase·reflog·cherry-pick](02_rebase_reflog_cherrypick.md) | 안전한 history 조작 |
| Day 3 | [브랜칭 전략](03_branching_strategies.md) | trunk-based vs git-flow vs github-flow |
| Day 4 | [코드 리뷰 문화](04_code_review_culture.md) | PR 작성·리뷰, conventional commits |
| Day 5 | [Git 복구·고급](05_git_recovery.md) | reflog로 부활, bisect, submodule, hooks |

### Lab

| Lab | 내용 |
|---|---|
| [lab1_git_internals.md](labs/lab1_git_internals.md) | `.git/` 들여다보기, plumbing 명령으로 commit 만들기 |
| [lab2_rebase_workflow.md](labs/lab2_rebase_workflow.md) | 다양한 시나리오에서 rebase·reflog로 복구 |

---

## 학습 결과

- [ ] `.git/objects/`의 4가지 객체 타입을 안다
- [ ] `git add`·`git commit`이 정확히 어떤 객체를 만드는지
- [ ] HEAD·branch·tag의 차이
- [ ] `git rebase`와 `git merge`의 차이와 선택 기준
- [ ] `git reflog`로 잃어버린 commit 복구
- [ ] trunk-based / git flow / github flow 비교
- [ ] PR에 좋은 commit 메시지·설명 작성
- [ ] `git bisect`로 버그 도입 시점 찾기

---

## Week 1을 마치면 답할 수 있어야

1. `git add foo.txt`가 일어나는 순간 `.git/`에 무엇이 만들어지는가?
2. `git commit --amend` 후 push가 거부되는 이유는?
3. `git reset --hard HEAD~1`로 잃은 commit을 어떻게 살리는가?
4. `git rebase -i` 중에 충돌나면 어떻게 해결하는가?
5. trunk-based가 git flow보다 나은 경우는?
6. PR을 거부할 명확한 기준 3가지는?
7. `git push --force-with-lease`가 `--force`보다 안전한 이유는?
