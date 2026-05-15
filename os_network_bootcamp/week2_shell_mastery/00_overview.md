# Week 2 — 셸 마스터리: Linux 운영 + PowerShell 자동화

## 주차 목표

- Linux 프로세스 관리·시그널·systemd로 서비스를 다룬다
- 정규식·`grep`·`sed`·`awk`·`jq`를 활용해 로그/JSON을 자유롭게 변형한다
- Bash 스크립팅: 함수, 트랩, 에러 처리, 인자 파싱
- PowerShell 스크립팅 심화: 고급 함수, 모듈, 에러 처리, 디버깅
- PowerShell Remoting (WinRM, SSH 전송, Invoke-Command, PSSession)

## 일정표

| Day | 주제 | 핵심 산출물 |
|---|---|---|
| 1 | 프로세스·시그널·systemd | 직접 만든 서비스가 자동 시작·재시작·로그 남김 |
| 2 | 정규식·sed·awk·jq | Nginx 로그를 통계 리포트로 변환하는 한 줄 |
| 3 | Bash 스크립팅 | 안전한 배포 스크립트 (인자 파싱·트랩·롤백) |
| 4 | PowerShell 스크립팅 | 고급 함수 + 모듈 + Pester 단위 테스트 |
| 5 | PowerShell Remoting | 여러 서버에 병렬 명령, JEA(Just Enough Admin) 이해 |

## 사전 점검

- [ ] Week 1 checklist 모두 ✅
- [ ] Bash 안전 헤더 `#!/usr/bin/env bash; set -euo pipefail`를 이해함
- [ ] PowerShell 7과 PSReadLine 동작

## Java/Spring 개발자를 위한 매핑

| 익숙한 개념 (Java) | 이번 주에 배울 셸 개념 |
|---|---|
| `try { ... } catch { ... } finally { ... }` | Bash의 `trap ERR/EXIT`, PowerShell의 `try/catch/finally` |
| Logback의 `MDC`·로그 레벨 | systemd journal, `logger`, PS의 `Write-Verbose`/`Write-Debug` |
| Spring Boot `application.yml`의 프로파일 | 환경변수 + Bash 함수 + PS profile |
| Maven/Gradle의 `dependencies` | Bash에서는 직접 호출, PS는 `Import-Module` |
| JUnit | Pester (PowerShell 단위 테스트) |
| `@Scheduled` | cron / systemd timer / Scheduled Task |

다음: [`01_processes_signals_systemd.md`](01_processes_signals_systemd.md)
