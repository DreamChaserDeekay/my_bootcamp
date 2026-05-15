# vulnerable_app — 의도적으로 취약한 Spring Boot 실습 앱

> ⚠ **이 앱은 의도적으로 취약하게 작성됐다.** 절대 공용 네트워크에 노출하지 말 것. 학습용 로컬·격리 환경 전용.

## 구조
- `/vuln/*` — 취약한 엔드포인트 (공격 실습 대상)
- `/safe/*` — 같은 기능의 안전 버전 (패치 비교용)

## 다루는 취약점
- A03 SQL Injection (`LoginController`, `SearchController`)
- A03 OS Command Injection (`PingController`)
- A03 Stored XSS (`BoardController`)
- A01 IDOR (`OrderController`)
- A10 SSRF (`FetchController`)
- 파일 업로드 (`UploadController`)
- A02 평문 비밀번호 저장
- A07 사용자 열거·Brute-force 무차단

## 실행

### Gradle (로컬)
```bash
./gradlew bootRun
```
→ http://localhost:8080

### Docker
```bash
docker build -t vuln-app:dev .
docker run --rm -p 8080:8080 --network lab-net vuln-app:dev
```

## 테스트 계정
- `admin` / `admin123`
- `alice` / `password1`
- `bob` / `qwerty`

## 학습 흐름
각 컨트롤러마다 `/vuln/*` 으로 공격 → 성공 확인 → `/safe/*` 로 같은 공격 시도 → 차단 확인. Week 1~2 실습 가이드와 함께 사용.

## 주의
- 실제 DB 연결 없이 **H2 인메모리** 사용 (실행 즉시 초기화)
- 운영 환경에 배포 금지 (이름·구조도 운영과 분리되어야 함)

---

이 디렉토리에는 핵심 컨트롤러·서비스 클래스만 포함. 학습 시 직접 Spring Boot 프로젝트로 가져와서 실행하길 권장. 학습 효과를 위해서다.
