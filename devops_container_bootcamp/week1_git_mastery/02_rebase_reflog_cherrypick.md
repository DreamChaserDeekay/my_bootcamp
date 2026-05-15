# Day 2 — rebase · reflog · cherry-pick

## 한 줄 요약

`merge`는 history를 **보존**하고, `rebase`는 **재작성**한다. `cherry-pick`은 한 commit만 다른 브랜치로 옮긴다. 잘못해도 `reflog`가 있다 — 거의 모든 것은 복구 가능.

## 학습 목표

- [ ] merge와 rebase의 정확한 차이와 결과 그래프
- [ ] interactive rebase로 history 재작성
- [ ] cherry-pick으로 hotfix를 여러 브랜치에 적용
- [ ] reflog로 잃어버린 commit 복구
- [ ] `--force` vs `--force-with-lease` 차이
- [ ] rebase 도중 충돌 해결

---

## merge vs rebase — 그림으로

### 시작 상태

```
A ─ B ─ C ─ D ─ E      main
         \
          F ─ G        feature
```

### `git checkout main && git merge feature`

```
A ─ B ─ C ─ D ─ E ─ M       main
         \         /
          F ─ G ─ ─        feature
```

- merge commit **M** 생성 (parent 2개)
- history 보존
- non-fast-forward

### `git checkout feature && git rebase main`

```
A ─ B ─ C ─ D ─ E              main
                 \
                  F'─ G'        feature  (재작성됨!)
```

- F, G 대신 **새 commit F', G'** 생성
- history 선형
- F, G는 unreachable (reflog에만 남음, 30일 후 GC)

### 어느 것?

| 상황 | 선택 |
|---|---|
| 짧은 feature 브랜치, PR 깔끔하게 | **rebase** |
| 협업 중인 브랜치 | **merge** (강요된 rebase는 동료 죽임) |
| Public 브랜치 (main 등) | 절대 rebase 금지 |
| GitHub PR merge 방식 | "Squash and merge"가 양쪽 장점 |

---

## Interactive Rebase

```bash
git rebase -i HEAD~5
```

에디터가 열림:

```
pick a1b2c3 first commit
pick d4e5f6 second commit
pick 78910a third commit
pick bcdef0 fourth commit
pick 123456 fifth commit

# Commands:
# p, pick = use commit
# r, reword = use commit, but edit the commit message
# e, edit = use commit, but stop for amending
# s, squash = use commit, but meld into previous commit
# f, fixup = like "squash", but discard this commit's log message
# d, drop = remove commit
```

### 자주 쓰는 시나리오

#### A. commit 메시지 수정

```
pick a1b2c3 first commit
reword d4e5f6 second commit    ← reword로 변경
pick 78910a third commit
```

저장하면 두 번째 commit 메시지 에디터가 다시 열림.

#### B. WIP commit들 통합 (squash)

```
pick a1b2c3 implement login
fixup 2222 WIP                 ← fixup으로 변경 (이전 commit에 합침)
fixup 3333 fix typo
fixup 4444 oops
pick d4e5f6 next feature
```

→ 4개 commit이 1개로. 메시지는 첫 번째 것만.

#### C. 순서 바꾸기

행을 그냥 swap. Git이 자동 cherry-pick.

```
pick d4e5f6 second commit   ← 앞으로
pick a1b2c3 first commit
```

> 의존성 있으면 충돌. 그땐 일반 rebase처럼 해결.

#### D. commit 분리 (edit)

```
pick a1b2c3 first commit
edit d4e5f6 huge commit       ← 여기서 stop
pick 78910a third
```

저장하면 `d4e5f6` 적용 후 멈춤. 그 시점에서:

```bash
git reset HEAD^               # 변경사항만 unstaged
git add part1.py
git commit -m "part 1"
git add part2.py
git commit -m "part 2"
git rebase --continue
```

---

## Rebase 도중 충돌

```bash
git rebase main
# Auto-merging foo.py
# CONFLICT (content): Merge conflict in foo.py
```

해결 흐름:

```bash
# 1. 충돌 파일 편집 (<<<<< ===== >>>>> 마커 해결)
code foo.py

# 2. 해결 표시
git add foo.py

# 3. 계속
git rebase --continue

# 또는 포기
git rebase --abort        # 시작 전 상태로
```

### 흔한 함정 — `git commit` 안 함

rebase 중에는 `git rebase --continue`이지 `git commit`이 아님. 잘못 commit하면 끼어들기 발생.

---

## cherry-pick

```
main:    A ─ B ─ C
              \
hotfix:        H        ← hotfix commit
```

```bash
git checkout main
git cherry-pick <H의 SHA>
```

```
main:    A ─ B ─ C ─ H'      ← H의 변경을 main에 적용
              \
hotfix:        H
```

### 운영 시나리오

`release-1.x` 브랜치 운영 중 main의 보안 패치를 가져옴:

```bash
git checkout release-1.x
git cherry-pick <commit-from-main>

# 충돌 시
git mergetool
git cherry-pick --continue
```

### 범위 cherry-pick

```bash
git cherry-pick A^..C       # A부터 C까지 (A 포함)
```

---

## reflog — 사고의 안전망

```bash
git reflog
# 9c0b1a2 HEAD@{0}: rebase finished: returning to refs/heads/feature
# 7d3a2f1 HEAD@{1}: rebase: third
# 8ab686e HEAD@{2}: rebase (start): checkout main
# 5c0d3e4 HEAD@{3}: commit: third
# 1f2g3h4 HEAD@{4}: commit: second
```

`HEAD@{N}`은 "N번 전 HEAD 위치". 모든 HEAD 이동이 여기에.

### 잃어버린 commit 살리기

```bash
# 1. 실수로 reset
git reset --hard HEAD~3      # 3개 commit 사라진 듯

# 2. reflog 확인
git reflog
# 1f2g3h4 HEAD@{0}: reset: moving to HEAD~3
# 5c0d3e4 HEAD@{1}: commit: third          ← 이걸 살리고 싶음

# 3. 해당 SHA로 이동 또는 브랜치 생성
git branch rescue 5c0d3e4
# 또는
git reset --hard 5c0d3e4
```

### branch reflog

```bash
git reflog show main        # main 브랜치만의 변경 기록
```

### reflog 만료

기본:
- **도달 가능한** entry: 90일
- **unreachable** entry: 30일

`git gc`가 만료된 것 삭제.

---

## `--force` vs `--force-with-lease`

```bash
# ❌ 위험
git push --force

# ✅ 안전
git push --force-with-lease
```

### 시나리오

당신:
```bash
git rebase -i HEAD~3        # history 재작성
git push --force            # remote 덮어씀
```

만약 그 사이 동료가 새 commit을 remote에 push했다면? `--force`는 **그 commit을 삭제!** 동료의 작업 손실.

`--force-with-lease`는 "내가 마지막으로 fetch한 시점 SHA와 remote의 현재 SHA가 같을 때만" force. 동료가 push했으면 다름 → 거부.

> 무조건 `--force-with-lease`를 alias로 설정 권장.

```bash
git config --global alias.pushf "push --force-with-lease"
```

---

## 안전한 history 재작성 규칙

1. **public 브랜치(main, develop) rebase 금지**
2. 협업 브랜치 rebase 전 동료에게 알림
3. `push --force-with-lease` 사용
4. PR/MR을 squash·rebase하기 전 GitHub UI에서
5. `git reflog`가 안전망 — 두려워 말기

---

## 운영 사례

### 사례 1 — `git pull` 후 rebase 시 충돌 폭주

```bash
git pull                     # = fetch + merge (보통)
git pull --rebase            # = fetch + rebase (history 깔끔)
```

`--rebase`는 local commit이 많으면 충돌마다 stop. 첫 커밋만 충돌 해결하면 나머지 자동인 경우가 흔하지만 안 그럴 수도. 그럼 일반 pull(merge)이 편할 수 있음.

```bash
# pull 기본을 rebase로
git config --global pull.rebase true
```

### 사례 2 — 잘못된 브랜치에 commit

```bash
# main에 commit해야 할 걸 feature에 commit
git log --oneline
# abc123 (HEAD -> feature) 잘못 들어간 commit
# def456 (main) ...
```

해결:

```bash
git checkout main
git cherry-pick abc123       # main에 적용

git checkout feature
git reset --hard HEAD~1      # feature에선 제거 (reflog에 남음)
```

### 사례 3 — rebase 후 push 거부

```bash
git rebase main
git push                     # ! [rejected]
# Updates were rejected because the tip of your current branch is behind
```

옳음 — 안전 장치. 의도적이면:

```bash
git push --force-with-lease
```

---

## 실습 (Hands-on)

### 1단계 — merge vs rebase 직접 비교

```powershell
git init demo
cd demo
echo "1" > f.txt; git add .; git commit -m "1"
echo "2" >> f.txt; git add .; git commit -m "2"

git checkout -b feature
echo "f1" >> f.txt; git add .; git commit -m "f1"
echo "f2" >> f.txt; git add .; git commit -m "f2"

git checkout main
echo "3" >> f.txt; git add .; git commit -m "3"

# 그래프
git log --all --graph --oneline

# 방법 A: merge
git merge feature
git log --all --graph --oneline
# *--*  merge commit

# 방법 B: rebase (위 merge 후 reset)
git reset --hard HEAD~       # merge 취소
git checkout feature
git rebase main
git log --all --graph --oneline
# 선형!
```

### 2단계 — interactive rebase로 commit 정리

```powershell
# WIP commit 4개 만들기
echo "a" > a.txt; git add .; git commit -m "feat: a"
echo "b" > b.txt; git add .; git commit -m "WIP"
echo "c" > c.txt; git add .; git commit -m "WIP"
echo "d" > d.txt; git add .; git commit -m "WIP"

# rebase로 통합
git rebase -i HEAD~4
# 에디터에서 WIP 3개를 fixup으로 변경
```

### 3단계 — reset 후 reflog 복구

```powershell
echo "important" > i.txt
git add .; git commit -m "important work"

git reset --hard HEAD~1     # 실수!
ls i.txt                     # 사라짐

git reflog                   # 마지막 commit SHA 찾기
git reset --hard <SHA>       # 복구
ls i.txt                     # 부활
```

### 4단계 — cherry-pick

```powershell
git checkout -b release
echo "release work" > r.txt; git add .; git commit -m "release feature"

git checkout main
git log --oneline             # 어디까지 있는지 확인

# release의 commit 가져오기
git cherry-pick <SHA-of-release-commit>
git log --oneline             # 새 SHA로 main에 적용
```

---

## 더 읽어볼 자료

- 📘 『Pro Git』 — 7장 Git Tools
- 🔗 [Git rebase 공식](https://git-scm.com/docs/git-rebase)
- 🔗 [Atlassian — Merging vs Rebasing](https://www.atlassian.com/git/tutorials/merging-vs-rebasing)
- 🎓 [Learn Git Branching — Rebase 부분](https://learngitbranching.js.org/)
- 🔗 GitHub Blog — [Hello git reflog](https://github.blog/2018-10-23-best-practices-for-protecting-data/)
