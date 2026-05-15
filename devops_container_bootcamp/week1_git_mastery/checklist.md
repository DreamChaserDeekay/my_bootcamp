# Week 1 — 체크리스트

## Git 내부

- [ ] `.git/`의 주요 항목 7가지 설명
- [ ] 4가지 객체 타입 (blob/tree/commit/tag) 차이
- [ ] `git add`가 어떤 객체를 만드는지 추적
- [ ] HEAD·branch·tag의 관계 그리기
- [ ] packfile·`git gc` 동작
- [ ] Plumbing 명령으로 commit 수동 생성

## rebase·reflog·cherry-pick

- [ ] merge와 rebase의 그래프 차이
- [ ] interactive rebase 6가지 명령 (pick/reword/edit/squash/fixup/drop)
- [ ] rebase 충돌 해결 흐름
- [ ] cherry-pick으로 commit 옮기기
- [ ] reflog로 잃은 commit 복구
- [ ] `--force` vs `--force-with-lease` 차이

## 브랜칭 전략

- [ ] Trunk-Based / GitHub Flow / Git Flow 비교
- [ ] Feature Flag의 역할
- [ ] 환경별 브랜치가 안티패턴인 이유
- [ ] release·hotfix 처리 (각 모델별)

## 코드 리뷰

- [ ] Conventional Commits 형식
- [ ] PR 템플릿 (What/Why/How/Test)
- [ ] 좋은 리뷰 코멘트 prefix (blocking/nit/suggestion/...)
- [ ] CODEOWNERS·Branch Protection
- [ ] pre-commit framework
- [ ] commitlint·husky

## 복구·고급

- [ ] 사고 5가지 복구 절차
- [ ] git bisect로 버그 commit 찾기
- [ ] submodule vs subtree 선택
- [ ] git hooks (pre-commit·commit-msg·pre-push)
- [ ] worktree
- [ ] git filter-repo로 민감 정보 제거

## 실습 결과

- [ ] Lab 1 — `.git/objects/` 직접 보기
- [ ] Lab 1 — Plumbing 명령으로 commit 만들기
- [ ] Lab 1 — reflog로 reset 복구
- [ ] Lab 2 — Interactive rebase squash
- [ ] Lab 2 — rebase 충돌 해결
- [ ] Lab 2 — cherry-pick으로 hotfix 적용
- [ ] Lab 2 — bisect로 버그 commit 찾기

## 자기 점검 질문

1. `git add foo.txt` 직후 `.git/objects/`에 무엇이 생기나?
   <details><summary>답</summary>foo.txt 내용으로 blob 객체 1개 (이미 있으면 skip). 같은 내용 다른 이름 파일은 같은 blob을 가리킴.</details>

2. `git commit`은 어떤 순서로 객체를 만드는가?
   <details><summary>답</summary>1) index의 staged 파일들로 tree 객체(들) 생성. 2) tree + parent + 메타로 commit 객체 생성. 3) refs/heads/<현재브랜치>를 새 commit SHA로 업데이트. 4) reflog 추가.</details>

3. `git reset --hard HEAD~1` 후 그 commit을 어떻게 살리는가?
   <details><summary>답</summary>git reflog로 직전 SHA 확인 → git reset --hard <SHA>. 90일 안엔 거의 항상 복구.</details>

4. `git push --force`가 위험한 이유와 `--force-with-lease`의 안전 메커니즘?
   <details><summary>답</summary>--force는 remote 현재 상태 무시하고 덮어씀 → 그 사이 동료가 push했으면 손실. --force-with-lease는 내가 마지막 fetch한 시점의 remote SHA와 현재 remote SHA가 같을 때만 force.</details>

5. trunk-based가 git flow보다 나은 경우는?
   <details><summary>답</summary>SaaS/웹 서비스로 CD 자주 하는 환경. feature flag로 미완 기능 숨길 수 있는 인프라. 자동화된 테스트 충분. 반대로 패키지 소프트웨어·릴리스 주기 긴 환경엔 git flow가 자연.</details>

6. 환경별 브랜치(dev/staging/prod)가 안티패턴인 이유?
   <details><summary>답</summary>"dev에선 됐는데 prod에선 안 됨"의 원인이 코드인지 환경인지 구분 불가. 같은 commit을 모든 환경에 promote하고 환경 차이는 환경변수로 → 디버깅 명확.</details>

7. `@Transactional` 같은 commit message convention의 가치는?
   <details><summary>답</summary>git log --grep으로 검색 효율. PR title 자동 생성. CHANGELOG 자동 생성. type별 의도 명확화. commitlint로 강제 가능.</details>

---

## 통과했다면

Week 2 [Docker 심화](../week2_docker/00_overview.md)로!
