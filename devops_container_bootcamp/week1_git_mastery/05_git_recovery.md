# Day 5 — Git 복구·고급

## 한 줄 요약

Git에서 **거의 모든 것**은 복구 가능. reflog가 안전망. bisect로 버그 도입 commit 찾기. submodule·subtree로 큰 저장소 관리. hooks로 자동화.

## 학습 목표

- [ ] 흔한 사고 5가지 복구
- [ ] `git bisect`로 버그 도입 commit 찾기
- [ ] submodule vs subtree 차이와 선택
- [ ] Git hooks (client·server side)
- [ ] worktree로 동시 여러 브랜치
- [ ] big repo 정리 (BFG·filter-repo)

---

## 사고 복구 5종

### 1) `git reset --hard`로 잃음

```bash
# 실수
git reset --hard HEAD~3      # 3 commit 사라진 듯

# 복구
git reflog                    # 마지막 HEAD 위치 찾기
# 8ab686e HEAD@{1}: commit: third  ← 이거
git reset --hard 8ab686e
```

> reflog는 90일 (도달 가능) / 30일 (unreachable) 유지. 그 안엔 거의 다 살릴 수 있음.

### 2) 잘못 amend / push -f한 commit

```bash
# 실수
git commit --amend -m "wrong"
git push --force

# 복구
git reflog                    # 옛 commit SHA 찾기
git reset --hard <옛 SHA>
git push --force-with-lease   # 다시 덮어씀
```

### 3) 잘못된 브랜치로 commit

```bash
# main에 해야 할 걸 feature에 했음
git log --oneline
# abc123 (HEAD -> feature) 잘못된 commit

# main에 적용
git checkout main
git cherry-pick abc123

# feature에서 제거
git checkout feature
git reset --hard HEAD~1
```

### 4) 추적 안 되는 파일 실수로 삭제

```bash
rm -rf important_dir

# 추적되는 파일만 복구
git checkout -- important_dir/

# 추적 안 되는(=git에 없는) 파일은 복구 X
# → 휴지통(Recycle Bin) 확인 또는 OS 백업
```

### 5) 잘못된 merge·rebase

```bash
git merge feature
# 의도와 다른 결과
git reset --hard ORIG_HEAD    # merge 직전으로 복원

# 또는
git reflog
git reset --hard <merge 전 SHA>
```

> `ORIG_HEAD`는 merge/rebase 직전 자동 저장. `MERGE_HEAD`는 merging 중일 때만.

---

## `git bisect` — 버그 도입 commit 찾기

```
   commit A ✅ good
   commit B
   commit C
   commit D
   ...
   commit Z ❌ bad (지금)
```

수십~수백 개 commit 중 어느 commit이 버그를 도입했나? **이진 탐색**.

```bash
git bisect start
git bisect bad                # 지금 HEAD가 bad
git bisect good A             # commit A는 good

# Git이 자동으로 중간 commit으로 이동
# 빌드·테스트
./run-test.sh
# 결과에 따라:
git bisect good   # 또는
git bisect bad

# 반복 — log2(N) 횟수만
# Git이 "abc123 is the first bad commit" 출력
```

### 자동화

```bash
git bisect start HEAD A
git bisect run ./check.sh
# check.sh가 exit 0 = good, exit non-zero = bad
# Git이 알아서 끝까지 찾음
```

100개 commit이면 7번이면 끝. 거대 repo에선 시간 큰 절약.

---

## Submodule vs Subtree

### Submodule

```bash
git submodule add https://github.com/foo/lib.git external/lib
git submodule update --init --recursive
```

- 외부 repo를 **참조**로 보관 (SHA 핀)
- 메인 repo는 가벼움
- clone 시 별도 명령 필요 (`--recurse-submodules`)
- 업데이트는 두 단계 (외부 → 메인)
- **복잡** — 협업 시 자주 헷갈림

### Subtree

```bash
git subtree add --prefix=external/lib https://github.com/foo/lib.git main --squash
```

- 외부 repo의 **내용**을 메인 repo에 흡수
- 메인 repo만 알면 됨 (clone 단순)
- 업데이트는 `git subtree pull`로
- 단점: 메인 repo 크기 증가

### 선택

| | Submodule | Subtree |
|---|---|---|
| 메인 repo 크기 | 가벼움 | 무거움 |
| Clone 복잡도 | 복잡 | 단순 |
| 외부 변경 자주 | 별로 | 좋음 |
| 외부에 commit back | 자연 | 어려움 |

> 둘 다 별로. 가능하면 **별도 패키지(npm, Maven)로 의존**.

---

## Git Hooks

`.git/hooks/`에 스크립트. 자동 실행.

### Client-side hooks

| Hook | 언제 |
|---|---|
| `pre-commit` | commit 전 (테스트·lint) |
| `prepare-commit-msg` | commit 메시지 에디터 열기 전 |
| `commit-msg` | commit 메시지 검증 |
| `post-commit` | commit 후 |
| `pre-push` | push 전 |
| `pre-rebase` | rebase 전 |

### 예 — pre-commit

`.git/hooks/pre-commit` (실행 권한 필요):

```bash
#!/bin/sh
# 빌드 + 단위 테스트 실행
./gradlew check
# 실패하면 exit non-zero → commit 거부
```

### 표준화 — pre-commit framework

`.pre-commit-config.yaml`:

```yaml
repos:
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v4.6.0
    hooks:
      - id: trailing-whitespace
      - id: end-of-file-fixer
      - id: check-yaml
      - id: check-merge-conflict
      - id: detect-private-key
  - repo: https://github.com/gitleaks/gitleaks
    rev: v8.18.4
    hooks:
      - id: gitleaks
```

```bash
pip install pre-commit
pre-commit install
# .git/hooks/pre-commit이 자동 생성됨
```

이제 commit마다 자동 실행. **개인 컴퓨터에만** 적용되므로 강제 X — CI에서도 같은 검증 필요.

### Server-side hooks

- `pre-receive` / `update` / `post-receive`
- GitHub·GitLab은 자체 UI로 (branch protection 등)

---

## Worktree — 여러 브랜치 동시에

```bash
# 새 worktree 추가
git worktree add ../my-repo-feature feature

# 디렉토리가 별도로 생성됨
ls ../my-repo-feature
# → 동일 repo, 다른 브랜치

# 작업 후 제거
git worktree remove ../my-repo-feature
```

**언제 유용?**
- 큰 빌드 중에 다른 브랜치 작업
- production hotfix를 메인 작업 중단 없이
- 두 브랜치 비교 (diff 도구로)

```bash
git worktree list
# /home/me/repo            abc1234 [main]
# /home/me/repo-feature    def5678 [feature]
```

---

## 큰 파일·민감 정보 제거

### 시나리오: API 키를 실수로 commit

```python
# secrets.py (commit 됨)
API_KEY = "sk-abc123..."
```

```bash
# 1. 즉시 API 키 폐기·재발급 (gitignore로는 부족!)

# 2. history에서 제거
# 옵션 A: git filter-repo (권장)
pip install git-filter-repo
git filter-repo --path secrets.py --invert-paths

# 옵션 B: BFG Repo-Cleaner (옛)
bfg --delete-files secrets.py

# 3. 강제 push
git push --force-with-lease

# 4. 협업자에게 clone 다시 알림
```

> **이미 push된 secret은 노출된 것**. 키 재발급이 우선. history 정리는 보조.

### 큰 binary 제거

```bash
# 50MB 이상 파일 모두 삭제
git filter-repo --strip-blobs-bigger-than 50M

# 또는
git filter-repo --path build/ --invert-paths
```

---

## 운영 사례

### 사례 1 — main에 잘못된 push (force)

동료가 `git push --force`로 내 commit 5개를 덮어씀.

```bash
git reflog                    # 내 마지막 commit 찾기
git push --force-with-lease origin <SHA>:main
# 또는 GitHub의 protected branch 설정으로 처음부터 막기
```

조치: branch protection rule + `--force-with-lease` 표준화.

### 사례 2 — clone이 너무 느림 (큰 repo)

```bash
# Shallow clone — 최근 N commit만
git clone --depth 50 https://github.com/big/repo.git

# Sparse checkout — 일부 디렉토리만
git clone --filter=blob:none --sparse https://github.com/big/repo.git
cd repo
git sparse-checkout set frontend/
```

### 사례 3 — 망분리에서 Git 동기화

폐쇄망 ↔ 인터넷망. git bundle:

```bash
# 인터넷망에서
git bundle create repo.bundle --all
# repo.bundle을 USB로 옮김

# 폐쇄망에서
git clone repo.bundle my-repo
# 또는 기존 repo에 추가
cd my-repo
git pull repo.bundle main
```

---

## 실습 (Hands-on)

### 1단계 — bisect로 버그 찾기

```bash
# 일부러 버그 도입
git init bisect-demo && cd bisect-demo
echo "ok" > test.txt && git add . && git commit -m "1: ok"
echo "ok" > test.txt && git add . && git commit --allow-empty -m "2"
echo "ok" > test.txt && git add . && git commit --allow-empty -m "3"
echo "BUG" > test.txt && git add . && git commit -m "4: introduce bug"
echo "BUG" > test.txt && git add . && git commit --allow-empty -m "5"
echo "BUG" > test.txt && git add . && git commit --allow-empty -m "6"

# bisect
git bisect start
git bisect bad
git bisect good HEAD~5

# 매 step에서 cat test.txt 확인
# BUG → git bisect bad
# ok  → git bisect good

# 결과: "4: introduce bug"가 first bad commit
```

### 2단계 — pre-commit framework 설치

```bash
pip install pre-commit
echo "repos:
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v4.6.0
    hooks:
      - id: trailing-whitespace
      - id: check-yaml" > .pre-commit-config.yaml

pre-commit install

# 일부러 깨진 파일 commit 시도
echo "trailing space   " > foo.txt
git add foo.txt
git commit -m "test"
# → pre-commit이 잡음. 자동 수정 후 다시 commit
```

### 3단계 — worktree로 두 브랜치 동시 작업

```bash
git worktree add ../proj-feature -b feature
cd ../proj-feature
# 별도 디렉토리에서 feature 브랜치 작업
```

### 4단계 — git filter-repo로 파일 제거

```bash
git init dirty
cd dirty
echo "API_KEY=sk-secret" > secrets.env
git add . && git commit -m "oops"
echo "ok" > app.py
git add . && git commit -m "app"

# 모든 history에서 secrets.env 제거
pip install git-filter-repo
git filter-repo --path secrets.env --invert-paths --force

git log --all -- secrets.env
# (empty — 사라짐)
```

---

## 더 읽어볼 자료

- 📘 『Pro Git』 — 10장 (내부) + 7장 (Tools)
- 🔗 [git-filter-repo](https://github.com/newren/git-filter-repo)
- 🔗 [pre-commit](https://pre-commit.com/)
- 🔗 [BFG Repo-Cleaner](https://rtyley.github.io/bfg-repo-cleaner/)
- 🎓 [Oh Shit, Git!?!](https://ohshitgit.com/) — 흔한 사고 복구 모음
- 🔗 [Git Internals - PDF](https://github.com/pluralsight/git-internals-pdf)
