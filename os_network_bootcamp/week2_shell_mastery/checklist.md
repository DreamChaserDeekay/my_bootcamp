# Week 2 자가 점검 체크리스트

## 프로세스·시그널·systemd (Day 1)

- [ ] SIGTERM과 SIGKILL의 차이를 운영 관점에서 설명한다
- [ ] `Restart=on-failure` + `TimeoutStopSec`을 함께 쓰는 이유를 안다
- [ ] systemd 유닛 파일을 직접 작성해 Spring Boot 앱을 서비스로 등록했다
- [ ] `journalctl -u <svc> --since "1 hour ago" -p err` 의미가 명확하다
- [ ] systemd timer로 cron 작업 하나를 옮겼다
- [ ] Windows `Register-ScheduledTask`로 일회성 작업을 만들었다

## 정규식·sed·awk·jq (Day 2)

- [ ] BRE/ERE/PCRE 차이를 안다
- [ ] `grep -P`의 lookahead/lookbehind를 사용했다
- [ ] `sed -i.bak`으로 in-place 수정을 안전하게 한다
- [ ] `awk -F, '$3>100 {sum+=$4} END {print sum}'` 같은 한 줄을 즉시 쓴다
- [ ] `jq 'map(select(.x > 10))'` 류를 자유롭게 쓴다
- [ ] nginx access.log에서 톱 IP·상태코드 분포·시간대별 추세를 한 줄씩으로 추출했다
- [ ] PowerShell의 `-match`, `-replace`, `ConvertFrom-Json`를 같은 목적으로 사용했다

## Bash 스크립팅 (Day 3)

- [ ] `set -euo pipefail; IFS=$'\n\t'` 안전 헤더를 반사적으로 쓴다
- [ ] `trap cleanup EXIT` 패턴을 사용했다
- [ ] `getopts`로 옵션 파싱 스크립트를 작성했다
- [ ] 함수 안 `local` 선언을 빠짐없이 한다
- [ ] 멱등성 있는 스크립트를 작성했다 (`mkdir -p`, `ln -sfn`, 존재 검사 등)
- [ ] **ShellCheck**를 돌려 0 warning을 달성했다
- [ ] 본인 배포 스크립트에 dry-run · 로깅 · 헬스체크 재시도가 들어 있다

## PowerShell 스크립팅 (Day 4)

- [ ] `[CmdletBinding()]`과 `[Parameter()]` 활용한 고급 함수를 작성했다
- [ ] 파이프라인 입력을 받는 함수를 만들었다 (`process` 블록)
- [ ] `[ValidateSet]`, `[ValidateRange]`, `[ValidatePattern]` 검증 속성을 사용했다
- [ ] `try/catch` + `-ErrorAction Stop`으로 비종결 에러를 잡았다
- [ ] `SupportsShouldProcess` + `ShouldProcess()`로 `-WhatIf`를 지원한다
- [ ] 함수를 `.psm1` 모듈로 묶어 `Import-Module`로 사용한다
- [ ] Pester 테스트 1개 이상 작성하고 통과
- [ ] PSScriptAnalyzer 클린

## PowerShell Remoting (Day 5)

- [ ] `Enable-PSRemoting`으로 활성화하고 로컬 루프백 접속에 성공했다
- [ ] `Invoke-Command -ThrottleLimit`으로 병렬 처리했다
- [ ] HTTP vs HTTPS, Negotiate vs CredSSP의 차이와 위험을 안다
- [ ] PS 7 SSH-based Remoting을 한 번 셋업해봤다
- [ ] `$using:` 으로 로컬 변수를 원격에 전달한다
- [ ] JEA의 목적(least privilege)을 설명한다

## 종합 실습

- [ ] Lab 3 (로그 분석) 9개 질문 모두 풀었다
- [ ] Lab 4 (PowerShell Audit) 1~3단계 완성, 본인 환경에서 실행 확인

---

다음: [Week 3 — TCP/IP · 네트워크 트러블슈팅](../week3_tcpip_network/00_overview.md)
