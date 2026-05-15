# Day 4 — Burp Suite & OWASP ZAP 마스터하기

> 웹 보안 테스터의 첫째 도구는 **인터셉팅 프록시**다. 브라우저와 서버 사이에 앉아 모든 요청을 보고, 멈추고, 변조한다.

## 1. 인터셉팅 프록시란

```
[브라우저] ←→ [Burp/ZAP] ←→ [서버]
              ↑
        모든 요청/응답을
        보고 변조 가능
```

이걸 못 다루면 웹 보안 학습은 불가능하다. **DevTools만으로는 부족**한 이유:
- DevTools는 브라우저가 보낸 후의 모습만 보여줌. 변조해서 다시 보내려면 번거로움
- 자동화된 공격 페이로드 적용 불가
- 패시브·액티브 스캔 자동화 불가

---

## 2. Burp Suite Community Edition 설치

1. https://portswigger.net/burp/communitydownload — Windows 설치
2. 처음 실행 → **Temporary project** → **Use Burp defaults**
3. **Proxy** 탭 → **Open browser** (내장 Chromium, 가장 편함)

### Burp 브라우저 vs 외부 브라우저
- **내장 브라우저(권장)**: 인증서 자동 신뢰, 트래픽 자동 캡처. 학습용 최적
- **외부 브라우저**: 시스템 프록시·CA 인증서 설정 필요

### 외부 브라우저 설정 (FoxyProxy 사용)
1. Firefox에 **FoxyProxy** 확장 설치
2. 프로필 추가: `127.0.0.1:8080` (Burp)
3. CA 인증서 설치:
   - 프록시 켠 상태에서 `http://burp` 접속
   - "CA Certificate" 다운로드
   - Firefox → 설정 → 인증서 → "Trust this CA to identify websites" 체크

---

## 3. Burp 핵심 탭

### Proxy
- **Intercept**: ON으로 두면 매 요청을 중단. Forward로 보냄
- **HTTP history**: 지나간 요청 모두 (가장 자주 보는 곳)
- **WebSockets history**

### Target
- **Site map**: 방문한 모든 URL을 트리로 정리
- **Scope**: 분석 대상 도메인 지정. 다른 트래픽은 무시
  ```
  Target → Scope → Include in scope → 도메인 추가
  ```

### Repeater (가장 자주 쓰는 기능)
요청을 잡아 자유롭게 변조 후 재전송.
- HTTP history에서 요청 우클릭 → **Send to Repeater** (`Ctrl+R`)
- 본문·헤더·메서드 모두 수정 가능
- Send 누르면 응답 표시

### Intruder
같은 요청에 여러 페이로드를 자동 대입. (Brute-force, 파라미터 fuzz)
- **Sniper**: 한 위치에 페이로드 순차 대입
- **Battering ram**: 같은 페이로드를 여러 위치에
- **Pitchfork**: 여러 위치에 각기 다른 페이로드 (병렬)
- **Cluster bomb**: 모든 조합

> Community Edition은 Intruder가 매우 느림. 학습용은 충분.

### Decoder
URL/Base64/HTML/Hex/Gzip 등 인코딩 변환.

### Comparer
두 응답의 차이를 시각화. (오류 응답 vs 정상 응답 비교 시 유용)

### Logger / Dashboard
모든 트래픽 종합 로그.

### Extender / BApp Store
Active Scan++, Logger++, Autorize 등 확장.

---

## 4. Burp 실습 시나리오

### 시나리오 4.1 — 첫 인터셉트
1. Burp 내장 브라우저로 `http://testphp.vulnweb.com` (의도적 취약 데모 사이트, 공개) 접속
2. Proxy → Intercept ON
3. 검색 폼에 아무 단어 입력 → 검색
4. 요청 가로채진 화면에서 **searchFor=test** 보임
5. **searchFor=hacked** 으로 수정 → Forward
6. 응답 페이지 변화 확인

### 시나리오 4.2 — Repeater로 SQL Injection 시도
1. HTTP history에서 검색 요청 → 우클릭 → Send to Repeater
2. Repeater에서 `searchFor=' OR '1'='1` (`'`은 URL encode 필요할 수 있음) 시도
3. 응답 길이가 정상과 다르면 SQLi 가능성

### 시나리오 4.3 — 본인 로컬 앱 인터셉트
1. 로컬 Spring Boot 띄움
2. 브라우저에서 로그인
3. JSESSIONID 쿠키 변조 후 다른 요청 시도
4. 권한 검사가 매 요청마다 일어나는지 확인

---

## 5. Burp 주요 단축키

| 단축키 | 기능 |
|--------|------|
| `Ctrl+R` | Send to Repeater |
| `Ctrl+I` | Send to Intruder |
| `Ctrl+Shift+I` | Send to Intruder (with options) |
| `Ctrl+U` | URL encode |
| `Ctrl+Shift+U` | URL decode |
| `Ctrl+B` | Base64 encode |
| `Ctrl+T` | Toggle intercept |
| `Ctrl+Space` | Auto-complete in Repeater |

---

## 6. OWASP ZAP (Burp의 오픈소스 대안)

### 설치
- https://www.zaproxy.org/download/
- Windows 설치파일 또는 Docker (`owasp/zap2docker-stable`)

### Burp vs ZAP 비교

| | Burp Community | OWASP ZAP |
|---|---|---|
| 라이선스 | 상용(Free Tier) | 오픈소스 |
| Active Scan | 제한적 | ✅ 강력 |
| Intruder | 제한적(느림) | Fuzzer 무제한 |
| API/CLI | 제한적 | ✅ 완전 자동화 가능 |
| 학습 곡선 | 직관적 | 약간 낮음 |
| 커뮤니티 | 매우 큼 | 큼 |

> 💡 **추천**: Burp Community로 학습, ZAP은 자동화·CI에 통합. 둘 다 익혀두면 좋다.

### ZAP Quick Start
1. 실행 → "Automated Scan"
2. URL 입력 → Attack
3. **Active Scan은 절대 본인 자산 아닌 곳에 돌리지 말 것**

### ZAP CI 통합 (DevSecOps 미리보기)
```bash
docker run -t owasp/zap2docker-stable zap-baseline.py \
  -t https://myapp.local -r zap_report.html
```

---

## 7. mitmproxy (CLI 사랑하는 분)

```bash
pip install mitmproxy
mitmproxy           # TUI
mitmweb             # 웹 UI
mitmdump            # 헤드리스
```
스크립트로 자동 변조 가능 — **API 보안 테스트 자동화에 유용**.

---

## 8. 모바일 트래픽 가로채기 (참고)

본인 회사가 모바일 앱이 있다면 동일하게 적용 가능:
1. 폰을 PC와 같은 Wi-Fi
2. 폰 Wi-Fi 설정에서 프록시 = PC IP:8080
3. Burp CA 인증서 폰에 설치
4. **Certificate Pinning**이 있으면 추가 우회 필요(`Frida`, `objection`)

> 안드로이드 7+는 시스템 CA만 신뢰. 사용자 CA는 디버그 빌드에서만 또는 `Network Security Config`로 풀어야 함.

---

## 9. 트래픽 분석 — Wireshark (참고)

HTTPS는 키 없이는 못 보지만 **DNS·TCP 흐름·SNI는 보임**. Week 3에서 깊이 다룸.
- 설치 시 **WinPcap/Npcap** 함께 (이미 포함됨)

---

## 10. 윤리 — 다시 강조

| ✅ 해도 됨 | ❌ 절대 안 됨 |
|---|---|
| `vulnerable_app/` (본 부트캠프) | 회사 운영 서비스에 무단 스캔·공격 |
| testphp.vulnweb.com (공개 데모) | 회사 내 다른 팀 서비스에 무단 |
| Juice Shop, DVWA, WebGoat | 친구·지인 서비스 |
| HackTheBox, TryHackMe, PortSwigger Academy | 정부·금융·의료 사이트 |
| 본인 사이드 프로젝트 | 단순 호기심으로 외부 사이트 |

**스캔만으로도 정보통신망법 위반 가능성**. 회사 자산도 사전 합의·범위 명확화 필수.

---

## 11. 오늘의 실습

### 실습 4.1 — Burp 셋업 + 핵심 화면 익히기
- [ ] Burp Community 설치
- [ ] 내장 브라우저로 `https://example.com` 접속
- [ ] Intercept ON/OFF 토글 익숙해지기
- [ ] HTTP history에서 요청 → Repeater로 보내 변조

### 실습 4.2 — PortSwigger Web Security Academy (강추)
**무료**의 공식 학습 플랫폼. Burp 만든 회사가 직접 제공.
- https://portswigger.net/web-security
- 회원가입 후 "All Materials" → 첫 SQL Injection Lab 클리어
- 부트캠프 동안 매주 5개씩 클리어 권장 (총 200+ 랩 있음)

### 실습 4.3 — ZAP Baseline Scan
- [ ] 로컬에 `juice-shop` 컨테이너 실행
  ```bash
  docker run -d -p 3000:3000 bkimminich/juice-shop
  ```
- [ ] ZAP에서 `http://localhost:3000` Automated Scan
- [ ] 리포트 HTML로 저장하고 발견된 취약점 종류 정리

### 실습 4.4 — Repeater 정렬
HTTP history → 본인 로컬 앱 요청 5개를 Repeater로 보내 다음을 테스트:
1. 메서드 변경 (GET ↔ POST)
2. 헤더 추가 (`X-Forwarded-For: 1.2.3.4`)
3. 쿠키 제거
4. JSON 본문 변조
5. Content-Type 변조

---

## 더 읽어볼 자료
- 📘 *The Web Application Hacker's Handbook* — Dafydd Stuttard (Burp 만든 사람)
- 🎓 PortSwigger Web Security Academy (무료, 부트캠프 내내 활용)
- 🎓 HackTheBox Academy — Web Apps 모듈
- 🔗 Burp Suite Documentation
