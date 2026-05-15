# Git Cheatsheet

## 일상

```bash
# 상태·로그
git status
git log --oneline --graph --all
git log -p file.txt           # 파일 변경 history
git log -S "search term"      # 코드 변경 검색
git log --author="Name"
git blame file.txt
git diff                       # unstaged
git diff --cached              # staged
git diff main..feature

# add·commit
git add .
git add -p                     # 부분만 (interactive)
git commit -m "msg"
git commit -am "msg"           # add + commit (tracked만)
git commit --amend             # 마지막 commit 수정

# 브랜치
git branch                     # 목록
git branch -a                  # remote 포함
git switch main                # checkout 대신 (modern)
git switch -c feature          # 생성 + 전환
git branch -d feature          # 삭제 (merged만)
git branch -D feature          # 강제 삭제

# remote
git remote -v
git remote add origin <url>
git remote set-url origin <new-url>
git fetch
git fetch --prune              # 사라진 remote 브랜치 정리
git pull                       # = fetch + merge
git pull --rebase              # = fetch + rebase
git push
git push -u origin feature     # upstream 설정
git push --force-with-lease    # 안전한 force
```

## 되돌리기

```bash
# 파일 변경 취소
git restore file.txt           # 마지막 commit 상태로

# staged 풀기
git restore --staged file.txt  # 옛 git reset HEAD file.txt

# commit 취소
git reset --soft HEAD~1        # commit만 풀기 (변경 staged)
git reset HEAD~1               # commit + index 풀기 (변경 unstaged)
git reset --hard HEAD~1        # 모두 버림 ⚠️

# 새 commit으로 되돌리기 (safer)
git revert HEAD                # HEAD를 되돌리는 새 commit
git revert HEAD~3..HEAD        # 범위
```

## rebase·cherry-pick

```bash
# interactive rebase
git rebase -i HEAD~5
#   pick / reword / edit / squash / fixup / drop

# rebase
git rebase main                # 현재 브랜치를 main 위로

# 충돌 후
git add <resolved files>
git rebase --continue
git rebase --abort              # 포기

# cherry-pick
git cherry-pick <sha>
git cherry-pick A^..B           # 범위
git cherry-pick --continue
```

## reflog (안전망)

```bash
git reflog                      # HEAD의 모든 이동
git reflog show main            # main 브랜치만
git reset --hard HEAD@{1}       # N번 전 위치로
```

## stash

```bash
git stash                       # 변경사항 임시 저장
git stash push -m "msg"
git stash list
git stash pop                   # 가장 최근 + 제거
git stash apply stash@{2}       # 특정 stash 유지
git stash drop stash@{0}
git stash clear
```

## tag

```bash
git tag                         # 목록
git tag v1.0                    # lightweight
git tag -a v1.0 -m "release"    # annotated
git push origin v1.0
git push --tags
git tag -d v1.0                 # 로컬 삭제
git push origin --delete v1.0   # remote 삭제
```

## 검색

```bash
git log --grep="bug"            # commit 메시지
git log -S "function_name"      # 코드에서
git log -G "regex"
git log --all --source --remotes --pretty=oneline -- file.txt
```

## bisect

```bash
git bisect start
git bisect bad                  # 현재 HEAD가 bad
git bisect good v1.0            # v1.0은 good
# Git이 중간으로 이동, 테스트 후
git bisect good   또는   git bisect bad
# 자동
git bisect run ./test.sh
git bisect reset                # 종료
```

## blame·history

```bash
git blame file.txt
git blame -L 10,20 file.txt
git log --follow file.txt       # rename 따라가기
```

## 정리

```bash
git gc                          # garbage collection
git fsck                        # 무결성 검사
git count-objects -v            # 객체 통계
```

## 큰 파일·민감 정보 제거

```bash
# pip install git-filter-repo
git filter-repo --path secrets.env --invert-paths
git filter-repo --strip-blobs-bigger-than 50M
```

## 자주 쓰는 alias

```bash
git config --global alias.co checkout
git config --global alias.br branch
git config --global alias.ci commit
git config --global alias.st status
git config --global alias.lg "log --oneline --graph --all --decorate"
git config --global alias.amend "commit --amend --no-edit"
git config --global alias.pushf "push --force-with-lease"
git config --global pull.rebase true
git config --global core.editor "code --wait"
```

## 운영 표준 설정

```bash
git config --global user.name "Your Name"
git config --global user.email "your@email.com"

# Windows 줄바꿈
git config --global core.autocrlf true        # Windows
git config --global core.autocrlf input       # Mac/Linux

# 색상
git config --global color.ui auto

# 큰 파일 경고
git config --global core.bigFileThreshold 100m

# rerere — 같은 충돌 자동 해결
git config --global rerere.enabled true
```

## 흔한 에러·해결

```bash
# "fatal: refusing to merge unrelated histories"
git pull --allow-unrelated-histories

# "Updates were rejected" (rebase 후)
git push --force-with-lease

# "your local changes would be overwritten"
git stash; git pull; git stash pop

# merge conflict marker 남음
grep -r "<<<<<<<" .             # 찾기

# 잘못된 브랜치에 commit
git log --oneline               # SHA 확인
git checkout main
git cherry-pick <SHA>
git checkout feature
git reset --hard HEAD~1
```
