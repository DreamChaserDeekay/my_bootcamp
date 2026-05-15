# 4주 시큐어 코딩 & 방어 보안 부트캠프

> **대상**: Java/Spring + Thymeleaf SSR 환경에서 일하는 개발자
> **목표**: 공격자의 시선으로 자신의 코드를 점검할 수 있는 방어 능력 확보
> **분량**: 하루 2~3시간 × 4주 (총 약 60~80시간)
> **윤리 원칙**: 모든 공격 실습은 **본인이 소유했거나 명시적 허가를 받은 시스템**에서만 수행한다. 타인의 시스템을 무단으로 공격하는 것은 정보통신망법 위반(징역 5년 이하 또는 5천만원 이하 벌금)이다.

---

## 왜 개발자가 보안을 배워야 하는가

> "The only secure system is one that is powered off, cast in a block of concrete and sealed in a lead-lined room with armed guards." — Gene Spafford

현실의 시스템은 절대적으로 안전할 수 없다. 하지만 **공격 비용이 방어 비용보다 크게 만드는 것**은 가능하다. 보안 학습의 본질은 다음과 같다.

1. **공격자의 사고방식(Adversarial mindset)** 을 익혀 코드를 새로운 시선으로 본다.
2. **위협 모델링(Threat Modeling)** 으로 무엇을, 누구로부터, 어떻게 지킬지 명확히 한다.
3. **방어 메커니즘**을 계층(Defense in Depth)으로 쌓는다.
4. **사고 발생 시 탐지·대응·복구** 절차를 미리 갖춰둔다.

---

## 학습 흐름 한눈에 보기

| 주차 | 주제 | 핵심 산출물 |
|------|------|-----------|
| **Week 1** | 보안 기초·HTTP/웹 동작·정찰·도구 셋업 | 로컬 실습 환경, Burp Suite/OWASP ZAP 익숙해지기, 1차 위협 모델 |
| **Week 2** | 웹 애플리케이션 보안 (OWASP Top 10 + Spring/Thymeleaf 특화) | 취약 앱 공격·패치, 안전한 Spring Security 설정 |
| **Week 3** | 네트워크·인프라·클라우드·컨테이너 보안 | TLS 점검, 컨테이너 하드닝, Secret 관리 표준 |
| **Week 4** | 시스템·바이너리·DevSecOps·사고 대응 | SAST/DAST 파이프라인, 인시던트 플레이북, 캡스톤 |

---

## 디렉토리 구조

```
security_bootcamp/
├── README.md                       ← 지금 이 문서
├── week1_foundations/              ← 보안 기초
├── week2_web_appsec/               ← 웹 애플리케이션 보안
├── week3_network_infra/            ← 네트워크/인프라/클라우드
├── week4_advanced_response/        ← 시스템/바이너리/대응
├── vulnerable_app/                 ← 일부러 취약하게 만든 Spring Boot 앱 (실습용)
└── resources/                      ← 도구, 책, 용어집, CTF 플랫폼
```

각 주차 폴더에는 다음 파일들이 들어있다.

- `00_overview.md` — 주차 학습 목표·일정
- `01_*.md` ~ `0N_*.md` — 핵심 개념·공격·방어·실제 사례
- `labs/` — 실습 가이드·코드
- `checklist.md` — 주차 종료 시 자가 점검

---

## 시작 전 준비물

### 필수
- **OS**: Windows 10/11 (현재 사용 환경) — WSL2 또는 VirtualBox로 Linux VM도 함께 권장
- **Java**: JDK 17 이상
- **빌드**: Gradle 또는 Maven
- **IDE**: IntelliJ IDEA Community / VS Code
- **Git**

### 보안 도구 (Week 1에서 설치)
- **Burp Suite Community Edition** — 웹 프록시·요청 변조
- **OWASP ZAP** — Burp의 오픈소스 대안
- **Wireshark** — 패킷 분석
- **Nmap** — 포트/서비스 스캔
- **Docker Desktop** — 취약 앱·실습 컨테이너 격리
- **Postman or HTTPie** — API 요청 도구

### 격리 환경 (매우 중요)
> ⚠ **실습용 취약 앱은 절대로 공용 네트워크에 노출하지 말 것.**
> 항상 `localhost` 또는 격리된 Docker 네트워크에서만 실행한다.

```bash
# 추천: 모든 실습은 Docker 컨테이너 + bridge 네트워크 안에서
docker network create lab-net
```

---

## 학습 방법론

### 1) 능동 학습 비율
- 읽기 30% / **직접 공격 시도 40%** / 안전한 코드로 수정 20% / 사례 분석 10%
- 이론만 보지 말고 항상 **취약한 코드 → 공격 성공 → 패치 → 재시도 실패 확인** 사이클을 돌린다.

### 2) "왜 안전한가"를 설명할 수 있어야 한다
프레임워크 기본값이 "안전하다고 알려져 있다"가 아니라, **무엇이 어떤 공격을 어떻게 차단하는지** 한 문장으로 말할 수 있어야 한다.

> 예) "Thymeleaf의 `th:text`는 HTML 특수문자(`<`, `>`, `"`, `'`, `&`)를 엔티티로 자동 인코딩하기 때문에 사용자 입력이 DOM 구조를 깨뜨릴 수 없어 Reflected XSS를 차단한다. 하지만 `th:utext`는 인코딩을 우회하므로 위험하다."

### 3) 위협 모델링 습관화
새 기능을 만들 때마다 4가지 질문을 한다 — **Threat Modeling 4 Questions (Adam Shostack)**:
1. 우리는 무엇을 만들고 있는가? (Data Flow Diagram)
2. 무엇이 잘못될 수 있는가? (STRIDE)
3. 그것에 대해 무엇을 할 것인가? (Mitigation)
4. 우리가 잘 했는가? (Validation)

### 4) 체크리스트 기반 회고
매 주차 끝 `checklist.md`로 자가 점검 후, 미진한 항목은 다음 주에 보강.

---

## 윤리·법률 가드레일

| 행위 | 한국 법령 | 비고 |
|------|----------|------|
| 타인 서버에 무단 접근 시도 | 정보통신망법 §48 | 5년 이하 징역 또는 5천만원 이하 벌금 |
| 타인 서버에 무단 침입 | 정보통신망법 §71 | 5년 이하 |
| 악성코드 전달·유포 | 정보통신망법 §70-2 | 7년 이하 |
| 무허가 취약점 공개 | 정통망법·영업비밀보호법 | 책임공개(Responsible Disclosure) 절차 준수 |
| 본인 시스템·허가 받은 시스템 테스트 | 합법 | 서면 동의서 보관 권장 |
| 공식 CTF·교육 플랫폼 (HackTheBox, TryHackMe, DVWA, WebGoat) | 합법 | 부트캠프 실습은 이곳에서 |

**이 부트캠프의 모든 공격 실습은 본인이 띄운 취약 앱(`vulnerable_app/`) 또는 공식 학습 플랫폼에서만 수행한다.**

---

## 다음 단계

[`week1_foundations/00_overview.md`](week1_foundations/00_overview.md) 부터 시작.
