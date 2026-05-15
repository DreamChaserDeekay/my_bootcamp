# Day 4 — 사고 대응·디지털 포렌식·로그 분석

> 사고는 **나는 게 아니라 시기의 문제**다. 발생 시 대응 절차가 미리 있어야 한다.

## 1. NIST SP 800-61 — 사고 대응 라이프사이클

```
[준비 Preparation]
       ↓
[탐지·분석 Detection & Analysis]
       ↓
[억제·근절·복구 Containment, Eradication, Recovery]
       ↓
[교훈 Post-Incident Activity]
       ↓ (다시 준비로)
```

### 1.1 준비 (Preparation)
- IR Playbook 작성·연습
- 도구·접근권한 확보 (Forensic 장비, 로그 시스템 접근)
- 비상 연락망
- 외부 파트너 (법무, IR 회사, PR)
- 시뮬레이션·Tabletop Exercise

### 1.2 탐지·분석
- SIEM 알림
- 사용자 신고
- 외부 제보 (보안 연구자, ISP)
- 1차 분류: 진짜 사고인가? 영향 범위?

### 1.3 억제·근절·복구
- **단기 억제**: 일단 확산 차단 (네트워크 격리, 계정 무효화)
- **장기 억제**: 임시 패치, 모니터링 강화
- **근절**: 악성 파일·계정·룰 제거, 패치
- **복구**: 정상 운영 복귀, 데이터 복원, 모니터링 강화

### 1.4 교훈
- Postmortem (Blameless)
- IOC 정리
- 개선 항목 → 백로그
- 재발 방지 조치

---

## 2. 사고 분류·심각도 등급

| 등급 | 정의 | 대응 |
|------|------|------|
| **Sev 1 (Critical)** | 운영 중단, 대량 데이터 유출, 결제 영향 | 즉시 전원 호출, 외부 발표 검토 |
| **Sev 2 (High)** | 일부 서비스, 일부 사용자 영향 | 1시간 내 대응 |
| **Sev 3 (Medium)** | 잠재적 침해, 검증 필요 | 업무 시간 내 |
| **Sev 4 (Low)** | 정보성, 단발성 | 백로그 |

기업마다 정의 다름. **합의된 기준**이 핵심.

---

## 3. 사고 유형별 플레이북 (예)

### 3.1 데이터 유출 의심
**증상**: 외부에서 데이터 발견(GitHub, 다크웹, 언론), 비정상 대량 다운로드 알림.

**플레이북**:
1. 정보 수집 (어떤 데이터, 양, 시기)
2. 유출 경로 추정 (계정 탈취? 내부자? 외부 침입?)
3. 영향 범위 (PII 포함? 결제 정보? 영업 비밀?)
4. 의심 계정·시스템 격리
5. 로그 보존 (변조 방지)
6. **법적 신고**: 한국은 개인정보 침해 시 72시간 내 KISA·개인정보위 신고 의무
7. 영향 사용자 통지 (법적 요구)
8. 포렌식 분석
9. 외부 IR 회사 협업 검토
10. PR 대응

### 3.2 랜섬웨어
**증상**: 다수 파일 암호화, 협박 메시지.

**플레이북**:
1. **즉시 네트워크 격리** (확산 차단이 1순위)
2. 감염 범위 파악
3. **백업 무결성 확인** (백업이 같은 망에 있으면 같이 암호화 됨)
4. 키 협상은 신중 (지불해도 복구 보장 X, 법적 이슈)
5. 재구축 (clean room에서)
6. 시스템 원본은 보존 (포렌식)

### 3.3 무차별 대입(Brute Force) / Credential Stuffing
**증상**: 로그인 실패 폭증, 비정상 IP 다수.

**플레이북**:
1. WAF/Rate Limit 즉시 강화
2. 피해 가능 계정 식별 (성공한 로그인 중 비정상 IP)
3. 대상 사용자 강제 비밀번호 재설정
4. MFA 미설정 사용자 안내
5. 알려진 유출 PW DB 점검

### 3.4 SQL Injection / RCE 발견
**증상**: WAF 알림, 코드 리뷰, 외부 제보.

**플레이북**:
1. 취약 엔드포인트 즉시 비활성 또는 WAF 룰
2. 영향 평가: 어디까지 침투 가능?
3. 로그 분석: 이미 악용된 흔적?
4. 패치 → 배포
5. 데이터 무결성 검증
6. 침해 흔적 발견 시 → 데이터 유출 플레이북으로

### 3.5 의존성 0-day (Log4Shell 류)
**증상**: 공개된 CVE, 본인 환경 영향 가능.

**플레이북**:
1. SBOM·의존성에서 영향 컴포넌트 식별
2. WAF 임시 룰 (페이로드 패턴 차단)
3. 환경 변수·JVM 옵션 미티게이션
4. 패치 가능한 버전으로 업그레이드
5. 침해 흔적 점검 (로그·네트워크)

---

## 4. 디지털 포렌식 기본

### 4.1 원칙
1. **무결성 보존**: 원본 변경 X, 항상 복사본으로 작업
2. **chain of custody**: 누가 언제 무엇을 했는지 기록
3. **재현 가능성**: 같은 방법으로 같은 결과
4. **법적 증거능력**: 절차·도구가 인정되는 표준 따름

### 4.2 휘발성 순서 (Order of Volatility)
사라지는 순서로 수집:
1. CPU 레지스터·캐시
2. RAM
3. 네트워크 연결 상태
4. 실행 중 프로세스
5. 디스크
6. 원격 로그
7. 오프라인 미디어

### 4.3 도구
- **Volatility** — 메모리 덤프 분석
- **Autopsy / Sleuth Kit** — 디스크 이미지
- **Wireshark** — 패킷 분석
- **plaso/log2timeline** — 타임라인 생성
- **YARA** — 시그니처 매칭
- **dd, FTK Imager** — 디스크 이미지화

### 4.4 클라우드 포렌식
- AWS EBS Snapshot, RAM 캡처
- CloudTrail·VPC Flow Logs 보존
- 매니지드 서비스는 일부 접근 불가 → 클라우드 SP 협력

---

## 5. 로그 분석 — 실전 패턴

### 5.1 의심 IP 패턴
```bash
# 가장 많이 요청한 IP
awk '{print $1}' access.log | sort | uniq -c | sort -rn | head

# 5xx만
awk '$9 >= 500' access.log | awk '{print $1, $7, $9}'

# 특정 시간대
awk '$4 > "[15/May/2026:14:00"' access.log

# SQL 패턴
grep -iE "union.*select|or.+=.+|--\b|sleep\(" access.log
```

### 5.2 인증 로그
```bash
# 실패한 로그인 (Linux)
grep "Failed password" /var/log/auth.log | awk '{print $11}' | sort | uniq -c | sort -rn

# 성공한 외부 로그인
last | head
```

### 5.3 SIEM 쿼리 예 (Splunk-style)
```
index=app sourcetype=spring "AccessDeniedException"
| stats count by user, src_ip
| where count > 10
```

```
index=cloud sourcetype=cloudtrail eventName=AssumeRole
| stats count by userIdentity.userName, recipientAccountId
```

---

## 6. Threat Hunting — 능동 탐지

이미 침해됐다고 가정하고 찾기. MITRE ATT&CK 기반.

### 가설 예
- 공격자가 PowerShell로 lateral movement 시도했을 것 → PS 실행 이력 조사
- 공격자가 외부 C2와 비콘 통신 → DNS 통계로 비정상 도메인 빈도
- 공격자가 새 admin 계정 생성 → IAM 변경 로그 검토
- 공격자가 데이터 압축·exfil → 큰 outbound 트래픽

### 도구
- **Sigma** — SIEM 룰 표준
- **MITRE ATT&CK Navigator**
- **Atomic Red Team** — 행위 시뮬레이션
- **CALDERA** — 자동화된 어드버서리 에뮬레이션

---

## 7. Honeypot — 미끼

가짜 자원으로 공격자 유인·탐지.
- **honeypotted endpoint**: `/admin-backup`, `/wp-admin`에 접근하면 즉시 알림 + IP 차단
- **canary token** (Thinkst Canary): 가짜 AWS 키, S3 버킷 — 누군가 사용하면 알림
- 깊은 honeypot은 운영 부담 큼, 가벼운 canary부터.

---

## 8. 위협 인텔리전스

- **MISP** (오픈소스 TI 공유)
- **AlienVault OTX**
- **Mandiant Threat Intelligence**
- 산업별 ISAC (Information Sharing and Analysis Center)
- 한국 KISA, FSI, 한국인터넷진흥원

---

## 9. 외부 통신·법무

### 9.1 한국 법적 의무
- **개인정보 침해**: 정보주체 통지 + 개인정보위 신고 (72시간 내)
- **정보통신망 침해**: KISA 신고 (한국정보보호산업협회 기준)
- **금융 사고**: 금융감독원
- **상장사**: 공시 (중요 사고는 한국거래소)

### 9.2 외부 발표
- 사실 기반, 추측 X
- 사용자에게 정확한 안내 (재설정·모니터링)
- 거짓·은폐는 더 큰 사고

---

## 10. Postmortem (사후 분석)

### 10.1 Blameless 원칙
사람이 아닌 **시스템·프로세스**가 사고를 가능하게 했다고 본다. 특정 사람 비난으로 끝나면 다음 사고도 같은 패턴.

### 10.2 템플릿
```markdown
# Postmortem: <사고명>
## 요약 (Executive Summary)
- 무엇이, 언제, 영향
## 타임라인
- T0: 침해 시점 (추정)
- T+X: 첫 알림
- T+Y: 격리 완료
- ...
## 영향
- 사용자, 시스템, 금전, 신뢰
## 근본 원인 (5 Whys)
## 대응 잘한 점 (What went well)
## 대응 부족한 점 (What didn't)
## 개선 항목 (Action Items)
- [ ] AI-1: ... (담당, 기한)
- [ ] AI-2: ...
## IOC
```

### 10.3 Action Items 추적
JIRA·Linear·GitHub Issues에 등록, 정기 점검.

---

## 11. 실습

### 실습 4.1 — 본인 시스템 IR Playbook 작성
3가지 시나리오 (예: 데이터 유출 의심, SSH brute force, S3 버킷 public 노출) 플레이북.

### 실습 4.2 — 로그 분석 미니 게임
nginx 액세스 로그 샘플(인터넷에 공개된 것)에 다음 grep:
- 가장 많은 4xx 발생 IP
- POST /login 으로 실패한 IP
- SQL 패턴 포함 요청

### 실습 4.3 — Volatility 입문
공개된 메모리 덤프 샘플(예: github.com/volatilityfoundation 예제)로 프로세스 목록·네트워크 연결·악성 코드 흔적 추출.

### 실습 4.4 — Tabletop Exercise
가상 시나리오로 팀과 1시간 토론:
"새벽 2시, AWS CloudTrail에서 모르는 IAM 사용자가 100개 EC2 종료. 어떻게 대응?"
- 단계별 행동·소통 시뮬레이션

### 실습 4.5 — Canary Token 설치
https://canarytokens.org 에서 가짜 AWS key 또는 PDF 토큰 만들어 본인 환경에 배치. 누가 열면 알림.
