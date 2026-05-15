# Day 1 — Git 내부 구조

## 한 줄 요약

Git은 **컨텐츠 주소화 저장소(Content-Addressable Storage)**다. 모든 파일은 SHA-1 해시를 키로 `.git/objects/`에 저장되고, 4가지 객체 타입(**blob/tree/commit/tag**)만 있다. 브랜치는 그냥 commit을 가리키는 41 byte 텍스트 파일.

## 학습 목표

- [ ] `.git/` 디렉토리의 주요 항목을 안다
- [ ] 4가지 객체 타입을 설명한다 (blob/tree/commit/tag)
- [ ] `git add`·`git commit`이 어떤 객체를 만드는지 추적
- [ ] HEAD·branch·tag·refs/의 관계
- [ ] packfile과 garbage collection
- [ ] Plumbing 명령으로 commit을 수동 생성

---

## `.git/` 디렉토리 해부

```
my-repo/
├── README.md              ← working tree
└── .git/                  ← Git의 본체
    ├── HEAD               ← "지금 어느 브랜치에 있는가"
    ├── config             ← 로컬 설정
    ├── description
    ├── index              ← Staging area (binary)
    ├── hooks/             ← 자동 실행 스크립트 (pre-commit 등)
    ├── info/
    │   └── exclude        ← .gitignore의 로컬 버전
    ├── logs/              ← reflog 데이터
    │   ├── HEAD
    │   └── refs/
    │       └── heads/main
    ├── objects/           ← 모든 객체 저장 ⭐
    │   ├── 12/
    │   │   └── 34abcd...  (실제 SHA: 1234abcd...)
    │   ├── pack/          ← packfile
    │   └── info/
    └── refs/
        ├── heads/         ← 로컬 브랜치
        │   └── main       (commit SHA 한 줄)
        ├── tags/
        └── remotes/
            └── origin/
                └── main
```

각각의 역할을 직접 보기:

```bash
git init test-repo
cd test-repo
ls .git/

# HEAD가 무엇을 가리키는가?
cat .git/HEAD
# ref: refs/heads/main
```

→ HEAD는 "지금 어떤 브랜치를 가리키는가"의 정보. 그 브랜치는 또 어떤 commit을 가리키는가:

```bash
# 아직 commit 없음
ls .git/refs/heads/
# (비어있음 — main 브랜치 파일은 첫 commit 후 만들어짐)
```

---

## 4가지 객체 타입

```
┌──────────────────────────────────────────┐
│ Object 종류 (.git/objects/ 안)            │
│                                          │
│ 1. blob   — 파일 내용                     │
│ 2. tree   — 디렉토리 (blob/tree 목록)     │
│ 3. commit — tree 한 개 + 부모 + 메타데이터 │
│ 4. tag    — annotated tag (드물게 사용)   │
└──────────────────────────────────────────┘
```

### 1) blob — 파일 내용

```bash
echo "Hello, Git" > hello.txt
git add hello.txt

# 어떤 blob이 만들어졌나?
ls .git/objects/
# 첫 2글자가 디렉토리, 나머지가 파일명
# 예: 8a/b686eafeb1f44702738c8b0f24f2567c36da6d

# 객체 내용 보기
git cat-file -p 8ab686eafeb1f44702738c8b0f24f2567c36da6d
# Hello, Git

# 객체 타입 확인
git cat-file -t 8ab686
# blob

# 직접 hash 계산
git hash-object hello.txt
# 8ab686eafeb1f44702738c8b0f24f2567c36da6d
```

**중요**:
- blob에는 **파일 이름이 없다** — 내용만
- 같은 내용 파일이 두 개 있어도 blob은 하나 (deduplication)
- SHA-1 = `sha1("blob " + size + "\0" + content)`

### 2) tree — 디렉토리

```bash
git commit -m "first"

# 가장 최근 commit의 tree
git cat-file -p HEAD^{tree}
# 100644 blob 8ab686ea... hello.txt
```

tree는 항목 목록:
```
<mode> <type> <sha>     <name>
100644 blob   8ab686... hello.txt    ← 일반 파일
100755 blob   ...       script.sh    ← 실행 파일
040000 tree   ...       subdir       ← 하위 디렉토리
```

서브디렉토리가 있으면 그 tree는 또 다른 tree를 참조 → **재귀 구조**.

### 3) commit — 스냅샷 + 메타

```bash
git cat-file -p HEAD
```

```
tree 7d3a2f...                  ← 이 commit의 디렉토리 스냅샷
parent 9c0b1a...                ← 부모 commit (첫 commit은 없음)
author DK <dk@example.com> 1737000000 +0900
committer DK <dk@example.com> 1737000000 +0900

first
```

핵심:
- commit은 **tree + parent + 메타** = 매우 단순한 객체
- "변경사항"이 아니라 **그 시점의 전체 스냅샷**
- merge commit은 parent가 둘 이상

```
   commit A ──▶ tree X ──▶ blob (hello.txt)
       │
       │ parent
       ▼
   commit B ──▶ tree Y ──▶ ...
```

### 4) tag (annotated) — 거의 안 만져도 됨

`git tag v1.0`은 **lightweight tag** (refs/tags/ 한 줄 파일). `git tag -a v1.0 -m "..."` 만이 tag 객체 생성.

---

## refs — 그냥 포인터

```bash
cat .git/refs/heads/main
# 9c0b1a234abc...
```

**브랜치 = commit SHA가 적힌 한 줄 파일**. 그래서 브랜치 생성/삭제가 O(1).

```bash
# 새 브랜치
git branch feature
cat .git/refs/heads/feature       # main과 같은 SHA

# checkout
git checkout feature
cat .git/HEAD
# ref: refs/heads/feature
```

### HEAD의 두 가지 형태

```bash
# 1) Symbolic ref (보통)
cat .git/HEAD
# ref: refs/heads/main

# 2) Detached HEAD (특정 commit 직접 가리킴)
git checkout 9c0b1a23
cat .git/HEAD
# 9c0b1a234abc...
```

**Detached HEAD**: 브랜치가 아닌 commit을 직접 가리킨 상태. 여기서 commit하면 어느 브랜치도 모름 → 나중에 사라질 수 있음.

---

## `git add` → `git commit` 순간의 변화

### `git add foo.txt`

1. foo.txt 내용으로 blob 객체 생성 (이미 있으면 skip)
2. `.git/index`에 (foo.txt → blob SHA) 추가

### `git commit -m "msg"`

1. `.git/index`로부터 tree 객체 생성 (디렉토리 단위로 재귀)
2. commit 객체 생성 (tree + parent + 메타)
3. `refs/heads/<현재브랜치>`를 새 commit SHA로 업데이트
4. reflog 추가

### 그림

```
working tree         index (staging)         objects/
  foo.txt    ───▶    foo.txt → blob   ───▶   blob (abc123...)
                                              tree (def456...)
                                              commit (ghi789...)
                                                ▲
                                                │
                                        refs/heads/main
                                            (= ghi789...)
```

---

## packfile — 효율적 저장

수천 개 commit 후엔 `.git/objects/`가 거대해짐. Git이 **자동으로** packfile로 압축:

```bash
# packfile 강제 생성
git gc

ls .git/objects/pack/
# pack-abc.idx
# pack-abc.pack       ← 압축된 모든 객체
```

- **delta compression** — 비슷한 객체끼리 차이만 저장
- 1만 commit이 100MB가 아닌 5MB

```bash
# 객체 통계
git count-objects -v
# count: 4                 ← 풀어진 객체 (loose)
# size: 16
# in-pack: 1234            ← pack 안의 객체
# size-pack: 5000
```

---

## reflog — 모든 ref 변경 기록

```bash
git reflog
# 9c0b1a2 HEAD@{0}: commit: third
# 7d3a2f1 HEAD@{1}: commit: second
# 8ab686e HEAD@{2}: commit: first
```

**모든 HEAD 이동**이 기록됨. `reset --hard`로 잃었다 생각해도 reflog로 살릴 수 있다 → 안전망. (Day 2·5에서 깊이)

기본 유지 기간: **90일** (도달 가능한 reflog) / **30일** (unreachable).

---

## Plumbing 명령 — 손으로 commit 만들기

```bash
# 1. blob 만들기 (파일 추가 없이)
echo "manual commit content" | git hash-object -w --stdin
# abc123...                  ← blob SHA 반환

# 2. tree 만들기 (한 파일 = blob 등록)
git update-index --add --cacheinfo 100644 abc123... mafile.txt
git write-tree
# def456...                  ← tree SHA

# 3. commit 만들기
echo "manual commit" | git commit-tree def456... -p HEAD
# ghi789...                  ← commit SHA

# 4. 브랜치 업데이트
git update-ref refs/heads/main ghi789...

# 또는 한 줄
git log --oneline           # 확인
```

이 흐름이 `git add`·`git commit`의 실제 내부 동작.

---

## 운영 사례 / 함정

### 사례 1 — Detached HEAD 작업 후 잃어버림

```bash
git checkout abc123      # detached HEAD
# 코드 수정
git add . && git commit -m "fix"
# 새 commit이 만들어짐. 그러나 어느 브랜치도 가리키지 않음

git checkout main        # 돌아옴
# fix commit은? → reflog에만 남음. 90일 후 GC로 삭제
```

조치: detached HEAD에서 commit 후 즉시 `git switch -c new-branch`로 브랜치 생성.

### 사례 2 — `.git/` 손상

`.git/index` 손상이 흔함. fsck로 검사:

```bash
git fsck --full
# error: object file is empty
# missing tree abc123...

# 옵션 1: index만 재생성
rm .git/index
git reset

# 옵션 2: 매우 망가졌으면 clone 다시
```

### 사례 3 — 큰 파일 실수로 commit

```bash
# 100MB 파일 commit 후 push 실패 (GitHub 100MB 제한)
git rm --cached big.bin
git commit -m "remove big"
git push                 # ❌ history에 아직 있음 → 거부

# 해결: history 재작성 (위험)
git filter-repo --strip-blobs-bigger-than 50M
# 또는 BFG Repo-Cleaner
```

근본 대책: **Git LFS** (대용량 파일을 별도 저장).

---

## 실습 (Hands-on)

### 1단계 — 첫 객체 만들기

```powershell
mkdir git-internals
cd git-internals
git init

echo "Hello" > a.txt
git add a.txt

# 어떤 객체가 만들어졌나?
Get-ChildItem .git/objects -Recurse | Where-Object { -not $_.PSIsContainer }
```

### 2단계 — 객체 내용 보기

```powershell
$sha = (Get-ChildItem .git/objects -Recurse | Where-Object { -not $_.PSIsContainer })[0]
# 디렉토리명 + 파일명 합치기 (12/abc... → 12abc...)
$shaFull = $sha.Directory.Name + $sha.Name
git cat-file -t $shaFull
git cat-file -p $shaFull
```

### 3단계 — commit 후 객체 늘어남 확인

```powershell
git commit -m "first"

Get-ChildItem .git/objects -Recurse | Where-Object { -not $_.PSIsContainer } | Measure-Object
# 3개 (blob + tree + commit)
```

### 4단계 — Plumbing으로 commit 만들기

```powershell
echo "manual" | git hash-object -w --stdin
# abc123...
# 
git update-index --add --cacheinfo 100644 <SHA> manual.txt
git write-tree
# def456...
# 
echo "manual" | git commit-tree <tree-SHA> -p HEAD
# ghi789...
git update-ref refs/heads/main <commit-SHA>
git log --oneline
```

### 5단계 — `.git/refs/` 직접 보기

```powershell
cat .git/HEAD
cat .git/refs/heads/main
git branch new
cat .git/refs/heads/new      # main과 같은 SHA
```

---

## 더 읽어볼 자료

- 📘 『Pro Git』 (Scott Chacon, 무료) — <https://git-scm.com/book/en/v2>
  - **10장 Git Internals**이 이 챕터의 정답지
- 📘 『Git for Teams』 (Emma Jane Westby)
- 🔗 [Git Reference](https://git-scm.com/docs)
- 🔗 [Git Magic](http://www-cs-students.stanford.edu/~blynn/gitmagic/) (무료)
- 🎓 [Learn Git Branching](https://learngitbranching.js.org/) — 인터랙티브 학습
- 🎓 Ben Lynn — "Git Magic"
- 🔗 [Think Like a Git](http://think-like-a-git.net/)
