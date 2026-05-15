# Week 1 자가 점검 체크리스트

> 이 체크리스트의 항목을 모두 ✅ 할 수 있어야 Week 2로 넘어갑니다.

## 운영체제 기초 (Day 1)

- [ ] OS가 응용에게 제공하는 4대 추상화(프로세스, 가상메모리, 파일시스템, 소켓)를 한 문장씩 설명할 수 있다
- [ ] 사용자 모드 / 커널 모드 / 컨텍스트 스위칭의 의미를 안다
- [ ] 프로세스와 스레드의 차이를 메모리 관점에서 설명할 수 있다
- [ ] `fork`, `exec`, `wait`, `exit` 의 역할을 안다
- [ ] `ps aux`, `Get-Process`로 PID·PPID·메모리를 조회한다
- [ ] `strace -e openat ls`를 실행해 시스템 콜이 보였다
- [ ] 좀비/고아 프로세스의 발생 원인을 안다
- [ ] `/proc/<pid>/` 디렉터리에서 3가지 이상의 정보를 추출했다

## 파일시스템·권한 (Day 2)

- [ ] FHS 주요 디렉터리(`/etc`, `/var`, `/usr`, `/proc`, `/sys`, `/dev`)의 용도를 안다
- [ ] `ls -l` 출력의 모든 열을 해석할 수 있다
- [ ] `chmod 644`, `chmod u+x`, `chmod -R 755 ...` 자유롭게 쓴다
- [ ] suid · sgid · sticky bit의 의미와 위험을 설명할 수 있다
- [ ] `umask`의 동작을 직접 실험해 확인했다
- [ ] 하드링크 vs 심볼릭링크 차이를 inode 관점에서 설명한다
- [ ] PowerShell `Get-Acl`로 NTFS ACL을 조회했다

## Bash 핵심 (Day 3)

- [ ] stdin/stdout/stderr와 `>`, `>>`, `2>`, `2>&1`, `<`, `<<EOF`를 자유롭게 쓴다
- [ ] 파이프(`|`)와 명령 치환(`$(...)`)의 차이를 설명한다
- [ ] 30개 핵심 명령어 중 25개 이상의 용도를 즉시 떠올린다
- [ ] glob 패턴(`*`, `?`, `[...]`)과 quoting(`""` vs `''`)의 차이를 안다
- [ ] `$PATH`, 환경변수, `.bashrc`/`.bash_profile`의 차이를 안다
- [ ] `&`, `jobs`, `fg`, `bg`, `nohup`, `tmux` 활용한다
- [ ] **`set -euo pipefail`** 이 왜 필요한지 안다
- [ ] nginx 액세스 로그에서 톱 10 IP를 한 줄로 추출했다

## PowerShell 객체 파이프라인 (Day 4)

- [ ] 텍스트 vs 객체 파이프라인의 차이를 동료에게 설명할 수 있다
- [ ] cmdlet의 `Verb-Noun` 명명 규약을 안다
- [ ] **`Get-Member`, `Get-Help`, `Get-Command`** 세 가지 탐험 도구를 자유롭게 쓴다
- [ ] `Where-Object`, `Select-Object`, `Sort-Object`, `Group-Object`, `Measure-Object`, `ForEach-Object`를 조합할 수 있다
- [ ] PowerShell 5.1 vs 7.x의 차이 3가지 이상 안다
- [ ] `Out-File`의 기본 인코딩 함정을 안다

## PowerShell 실전 (Day 5)

- [ ] PSDrive로 파일·레지스트리·환경변수·인증서를 같은 cmdlet으로 다룬다
- [ ] 실행 정책 4종(`Restricted`, `AllSigned`, `RemoteSigned`, `Bypass`)의 차이를 안다
- [ ] **실행 정책이 "보안 기능이 아님"** 을 안다
- [ ] `$PROFILE`을 만들고 별칭·함수·환경변수를 등록했다
- [ ] PSReadLine으로 히스토리 검색·자동완성을 설정했다
- [ ] `Invoke-RestMethod`로 GitHub API를 호출해봤다
- [ ] `Set-StrictMode -Version Latest` + `$ErrorActionPreference = 'Stop'` 의미를 안다

## 실습 (Labs)

- [ ] WSL2 + Ubuntu 22.04가 정상 동작한다
- [ ] PowerShell 7이 설치돼 있고 `$PSVersionTable`로 확인했다
- [ ] Lab 2의 6문제를 Bash와 PowerShell 양쪽으로 풀었다

---

## 다음 주차 미리보기

[Week 2 — Linux 운영 + PowerShell 자동화 심화](../week2_shell_mastery/00_overview.md)

- 프로세스/시그널/systemd
- 정규식·sed·awk·jq
- Bash 스크립팅 (함수, 트랩, 에러 처리)
- PowerShell 스크립팅·모듈·고급 함수
- PowerShell Remoting (WinRM, Invoke-Command, PSSession)
