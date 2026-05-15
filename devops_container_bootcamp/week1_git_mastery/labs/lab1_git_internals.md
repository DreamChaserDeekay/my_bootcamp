# Lab 1 — Git 내부 들여다보기

## 목표

- `.git/objects/`에 직접 접근
- Plumbing 명령으로 commit을 손으로 만들기
- packfile 동작 확인
- reflog 활용

---

## 1단계 — 깨끗한 repo

```powershell
mkdir git-internals-lab
cd git-internals-lab
git init
ls .git/
```

확인:
```
HEAD       config     description  hooks/   info/   objects/   refs/
```

```powershell
cat .git/HEAD          # ref: refs/heads/main
ls .git/objects        # info/  pack/   (둘 다 비어있음)
```

---

## 2단계 — `git add`의 정체

```powershell
"Hello, Git" | Out-File hello.txt -NoNewline
git add hello.txt

# 어떤 객체가 만들어졌나?
Get-ChildItem .git/objects -Recurse | Where-Object { -not $_.PSIsContainer }
# 예: .git\objects\8a\b686ea...
```

객체 분석:

```powershell
$objs = Get-ChildItem .git/objects -Recurse | Where-Object { -not $_.PSIsContainer }
$obj = $objs[0]
$sha = $obj.Directory.Name + $obj.Name
git cat-file -t $sha     # blob
git cat-file -p $sha     # Hello, Git
git cat-file -s $sha     # 크기 (bytes)
```

### 직접 hash 계산

```powershell
git hash-object hello.txt
# 동일한 SHA
```

### 같은 내용 = 같은 SHA

```powershell
"Hello, Git" | Out-File hello2.txt -NoNewline
git hash-object hello2.txt
# 같은 SHA → blob은 한 번만 저장 (deduplication)
```

---

## 3단계 — commit 후 객체 늘어남

```powershell
git commit -m "first"

# 이제 객체 3개
Get-ChildItem .git/objects -Recurse | Where-Object { -not $_.PSIsContainer } | Measure-Object

# 1) blob (hello.txt 내용)
# 2) tree (디렉토리)
# 3) commit (메타 + tree 참조)

git cat-file -p HEAD
```

예상 출력:
```
tree <tree-SHA>
author DK <...> ...
committer DK <...> ...

first
```

```powershell
git cat-file -p HEAD^{tree}
# 100644 blob <blob-SHA>     hello.txt
```

---

## 4단계 — Plumbing으로 commit 직접 만들기

```powershell
# 1. 새 내용으로 blob 만들기
$blob = "manual content" | git hash-object -w --stdin
$blob

# 2. index에 등록
git update-index --add --cacheinfo 100644 $blob manual.txt

# 3. tree 만들기
$tree = git write-tree
$tree

# 4. commit 만들기
$commit = "manual commit" | git commit-tree $tree -p HEAD
$commit

# 5. 브랜치 업데이트
git update-ref refs/heads/main $commit

git log --oneline
git cat-file -p HEAD
```

→ "manual commit"이 보임. 일반 `git commit`과 동일한 결과.

---

## 5단계 — packfile 확인

```powershell
# 객체 통계
git count-objects -v
# count: N
# size: K
# in-pack: 0     ← 아직 pack 없음

# 강제로 pack
git gc

git count-objects -v
# in-pack: N      ← 모두 packed

ls .git/objects/pack/
# pack-abc.idx
# pack-abc.pack
```

`.git/objects/12/` 같은 loose 디렉토리는 비워짐 (또는 사라짐).

```powershell
# pack 내용 보기
$packIdx = (Get-ChildItem .git/objects/pack/*.idx).Name
git verify-pack -v .git/objects/pack/$packIdx
# 각 객체의 SHA·타입·크기 출력
```

---

## 6단계 — refs 들여다보기

```powershell
# 브랜치 = 한 줄 파일
cat .git/refs/heads/main
# <commit-SHA>

# 새 브랜치
git branch dev
cat .git/refs/heads/dev
# 같은 SHA

# tag
git tag v1.0
cat .git/refs/tags/v1.0
# 같은 SHA (lightweight tag)

# annotated tag
git tag -a v2.0 -m "release"
cat .git/refs/tags/v2.0
# 다른 SHA (tag 객체)

git cat-file -p (git rev-parse v2.0)
# tag 객체 내용 — object, type, tag, tagger, message
```

---

## 7단계 — reflog 실험

```powershell
# 더 많은 commit
echo "1" >> hello.txt; git add .; git commit -m "second"
echo "2" >> hello.txt; git add .; git commit -m "third"
echo "3" >> hello.txt; git add .; git commit -m "fourth"

git log --oneline
# 4개 commit

# 마지막 2개 잃어버리기 (실수 시뮬레이션)
git reset --hard HEAD~2
git log --oneline
# 2개만 남음

# reflog로 복구
git reflog
# 두 번째 줄에 reset 직전 commit SHA 있음
git reset --hard HEAD@{1}
git log --oneline
# 4개 모두 부활
```

---

## 8단계 — orphan 객체 만들기

```powershell
# detached HEAD에서 commit
$commitSha = git rev-parse HEAD
git checkout HEAD~1
echo "orphan" > orphan.txt
git add .
git commit -m "orphan commit"
$orphanSha = git rev-parse HEAD

git checkout main
git log --all --oneline
# orphan은 어디에도 없는 듯

# 그러나 reflog에 남음
git reflog
git cat-file -p $orphanSha
# 살아있음

# fsck로 orphan 찾기
git fsck --unreachable --no-reflogs
```

기본 reflog로 보호되지만, 30일 후 GC. 영구 보존하려면 브랜치 생성:

```powershell
git branch rescue $orphanSha
git log --all --oneline
# orphan 표시됨
```

---

## 9단계 — git fsck 사용

```powershell
# 모든 객체 검증
git fsck --full

# 도달 불가능한 commit
git fsck --unreachable --no-reflogs
# (없거나, 위 orphan)

# dangling object (참조 없음)
git fsck --dangling
```

---

## 산출물 체크리스트

- [ ] `.git/`의 주요 항목 7가지 알기
- [ ] blob·tree·commit 객체 직접 확인
- [ ] git hash-object로 SHA 계산
- [ ] plumbing 명령으로 commit 만들기
- [ ] packfile 생성·verify
- [ ] reflog로 reset된 commit 복구
- [ ] fsck로 객체 검증

---

## 다음 단계

[Lab 2 — rebase workflow](lab2_rebase_workflow.md)
