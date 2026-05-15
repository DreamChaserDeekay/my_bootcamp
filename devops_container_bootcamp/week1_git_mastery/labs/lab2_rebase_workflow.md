# Lab 2 — Rebase·Cherry-pick·Reflog 실전

## 목표

- Interactive rebase로 history 정리 (squash/reword/edit/drop)
- 충돌 해결
- cherry-pick으로 hotfix 적용
- reflog로 사고 복구

---

## 1단계 — repo 준비

```powershell
mkdir rebase-lab
cd rebase-lab
git init

# 시작 commit
"v1" | Out-File README.md -NoNewline
git add . && git commit -m "feat: initial commit"

# 여러 WIP commit
"v1 + a" | Out-File README.md -NoNewline
git add . && git commit -m "WIP: a"

"v1 + a + b" | Out-File README.md -NoNewline
git add . && git commit -m "WIP: b"

"v1 + a + b + c" | Out-File README.md -NoNewline
git add . && git commit -m "fix typo"

"v1 + a + b + c (final)" | Out-File README.md -NoNewline
git add . && git commit -m "WIP: c"

git log --oneline
# 5개 commit
```

---

## 2단계 — Squash with rebase -i

PR을 깔끔히 만들기 위해 WIP 4개를 한 commit으로:

```powershell
git rebase -i HEAD~4
```

에디터 열림:
```
pick abc1 WIP: a
pick def2 WIP: b
pick ghi3 fix typo
pick jkl4 WIP: c
```

다음으로 변경:
```
pick abc1 WIP: a
fixup def2 WIP: b
fixup ghi3 fix typo
fixup jkl4 WIP: c
```

저장. 그다음 첫 commit의 메시지를 정리 (reword):

```powershell
git rebase -i HEAD~1
# pick → reword
# 메시지 에디터에서: "feat: add feature X"
```

```powershell
git log --oneline
# 2개 commit:
#   abc1234 feat: add feature X
#   xxx0000 feat: initial commit
```

---

## 3단계 — 충돌 해결 시나리오

```powershell
# 새 브랜치
git checkout -b feature

# feature에서 작업
"v1 (feature edit)" | Out-File README.md -NoNewline
git add . && git commit -m "feat: feature edit"

# main에서 같은 줄 다른 변경
git checkout main
"v1 (main edit)" | Out-File README.md -NoNewline
git add . && git commit -m "feat: main edit"

# feature를 main 위로 rebase → 충돌
git checkout feature
git rebase main
# CONFLICT
```

```powershell
# 충돌 마커 확인
cat README.md
# <<<<<<< HEAD
# v1 (main edit)
# =======
# v1 (feature edit)
# >>>>>>> ...

# 해결 (마커 제거하고 원하는 내용으로)
"v1 (merged: feature + main)" | Out-File README.md -NoNewline

# 해결 표시
git add README.md
git rebase --continue
```

또는 포기:
```powershell
git rebase --abort
```

---

## 4단계 — Cherry-pick

```powershell
# main에 hotfix
git checkout main
"v2 with security patch" | Out-File README.md -NoNewline
git add . && git commit -m "fix: security CVE-2026-xxxxx"

$hotfixSha = git rev-parse HEAD

# release 브랜치에도 적용해야 함
git checkout -b release-1.x HEAD~2     # 옛 시점
git cherry-pick $hotfixSha

git log --oneline
# release-1.x에 hotfix가 새 SHA로 추가됨
```

---

## 5단계 — reset --hard → reflog 복구

```powershell
git checkout main
$beforeSha = git rev-parse HEAD

# 실수
git reset --hard HEAD~3

git log --oneline
# 3개 commit 사라진 듯

# 복구
git reflog
# 마지막 부분에 reset 직전 SHA

git reset --hard $beforeSha
git log --oneline
# 복원!
```

---

## 6단계 — `git reset` 3가지 모드 비교

```powershell
# 시작 상태 확인
echo "stage me" > new.txt
git add new.txt
echo "modified" > README.md
git status

# --soft: HEAD만 이동, index·worktree 유지
git reset --soft HEAD~1
git status
# 변경사항이 staged 상태로 남아있음

# --mixed (기본): HEAD + index, worktree 유지
git reset HEAD~1     # 또는 --mixed
git status
# 변경사항이 unstaged 상태

# --hard: 모두 버림 (위험!)
git reset --hard HEAD~1
# 모든 변경사항 사라짐
```

| 모드 | HEAD | index | working tree |
|---|---|---|---|
| `--soft` | 이동 | 유지 | 유지 |
| `--mixed` (기본) | 이동 | 리셋 | 유지 |
| `--hard` | 이동 | 리셋 | 리셋 |

---

## 7단계 — push --force-with-lease

```powershell
# GitHub에 repo 만들고 (또는 두 번째 로컬 repo로 시뮬)
# git remote add origin <URL>
# git push -u origin main

# rebase로 history 재작성
git rebase -i HEAD~3
# 변경 후 push

git push --force-with-lease
# 안전 — 누가 그 사이 push했으면 거부

# 실제 시나리오 시뮬레이션
# (다른 셸/사용자가) 같은 브랜치에 push
# 내가 --force-with-lease 시도 → "stale info" 거부
# --force로 했으면 동료 commit 날아감
```

---

## 8단계 — bisect 미니 실습

```powershell
mkdir bisect-mini
cd bisect-mini
git init

# 10개 commit, 5번째에 버그
for ($i = 1; $i -le 10; $i++) {
  if ($i -eq 5) {
    "BAD" | Out-File state.txt -NoNewline
  } else {
    "OK" | Out-File state.txt -NoNewline -Append
  }
  git add .; git commit -m "commit $i"
}

# 현재는 BAD, 처음은 OK
git bisect start
git bisect bad HEAD
git bisect good (git rev-parse HEAD~9)

# Git이 중간으로 이동. cat state.txt로 OK/BAD 확인
# git bisect good/bad 반복

git bisect reset    # 종료
```

---

## 산출물 체크리스트

- [ ] Interactive rebase로 squash·fixup·reword
- [ ] rebase 도중 충돌 해결
- [ ] cherry-pick으로 hotfix 적용
- [ ] reflog로 reset --hard 복구
- [ ] reset 3모드 동작 차이 이해
- [ ] push --force-with-lease 안전성
- [ ] bisect로 버그 commit 찾기

---

## 트러블슈팅

### "rebase --continue 했는데 안 됨"

충돌 마커가 남아있나 확인. 모두 제거 후 `git add`, 다시 `git rebase --continue`.

### "reset 후 reflog에 없음"

90일/30일 경과? `git gc` 명시적으로 실행했나? 보통은 살아있다.

### "한 줄도 충돌"

`git rerere`로 충돌 해결 패턴 학습 활성화:
```powershell
git config rerere.enabled true
```
같은 충돌이 반복될 때 자동 해결.
