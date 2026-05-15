# Day 1 — 메모리·바이너리 취약점·리버싱 기초

> Java/Spring 개발자는 메모리 안전 언어 위에 있어 비교적 안전하지만, **CVE 분석·악성코드 조사·네이티브 라이브러리 사용 시** 기초 이해가 필요.

## 1. 메모리 레이아웃 (Linux x86_64)

```
높은 주소
+---------------------+
|   Kernel space      |
+---------------------+
|   Stack (downward)  | ← 함수 호출, 지역 변수, RIP
|         |           |
|         v           |
+---------------------+
|                     |
|       Heap          | ← malloc, new
|         ^           |
|         |           |
+---------------------+
|  BSS (uninit data)  |
+---------------------+
|  Data (init globals)|
+---------------------+
|  Text (code)        |
+---------------------+
낮은 주소
```

함수 호출 시 스택에 **return address(RIP)** + **saved frame pointer(RBP)** + **지역 변수** 적층.

---

## 2. Buffer Overflow — 고전 예제

```c
#include <stdio.h>
#include <string.h>

void greet(char *name) {
    char buffer[16];
    strcpy(buffer, name);   // 길이 검증 X
    printf("Hello, %s\n", buffer);
}

int main(int argc, char *argv[]) {
    greet(argv[1]);
    return 0;
}
```

`argv[1]`이 16바이트 넘으면 → buffer 너머 saved RBP·return address 덮어쓰기 → 임의 주소로 점프 가능.

### 방어 메커니즘 (현대 OS 기본)
| 보호 | 효과 |
|------|------|
| **Stack Canary** | 스택에 무작위 값 두고 return 직전 검증 |
| **NX / DEP** | 스택·힙은 실행 불가 |
| **ASLR** | 메모리 주소 무작위화 |
| **PIE** | 코드 영역도 ASLR |
| **RELRO** | GOT 보호 |
| **FORTIFY_SOURCE** | strcpy 등을 안전 버전으로 |

**현대 시스템은 이 보호들이 다 켜져 있다.** 그래서 단순 BOF로 즉시 RCE는 어렵고, 정교한 기법(ROP, JOP, Heap shaping)이 필요.

---

## 3. ROP (Return-Oriented Programming) 개념만

NX/DEP로 인해 임의 shellcode 실행 불가 → 기존 코드에 있는 **gadget**(`pop rdi; ret` 등)을 체이닝하여 시스템 콜 호출.

```
[ payload ]
[ gadget1 (pop rdi; ret) ]
[ "/bin/sh"의 주소     ]
[ gadget2 (call system) ]
```

도구: **pwntools** (Python), **ROPgadget**.

> 부트캠프 범위 밖이지만, "CVE 분석 시 RCE까지 어떻게 가나"를 이해하면 영향도 평가에 도움.

---

## 4. Use-After-Free, Double Free, Heap Overflow

C/C++ 메모리 버그의 다른 흔한 종류. 브라우저·커널·자바 JNI 등에서 발생.

**Java/Kotlin은 GC가 있어 대부분 안전.** 하지만 **JNI**로 네이티브 호출 시 경계 검사 필요. 일부 Java 라이브러리(`sun.misc.Unsafe`)도 메모리 안전 위반 가능.

---

## 5. 정수 오버플로우 — Java에도 있다

```java
int total = quantity * price;   // quantity * price > Integer.MAX_VALUE 면 음수
if (total >= 0) {               // 검사 통과
    pay(total);                 // 음수 결제?
}
```

방어:
- `Math.multiplyExact(a, b)` — 오버플로우 시 ArithmeticException
- `Math.addExact`, `subtractExact`
- 금전은 `BigDecimal`

---

## 6. 리버싱 — 기초 도구

### 6.1 도구
- **Ghidra** (NSA, 무료) — 디컴파일러 강력
- **IDA Pro** (상용, 표준)
- **radare2 / Cutter** (오픈소스)
- **x64dbg** (Windows 디버거)
- **gdb + pwndbg / GEF** (Linux)

### 6.2 정적 분석 vs 동적 분석
- **정적**: 실행 안 하고 코드 구조 파악 (Ghidra 디컴파일)
- **동적**: 실행하며 행위 관찰 (디버거, sandbox)

### 6.3 자바 리버싱
**JAR/Class는 바이트코드** → 거의 원본 복원 가능.
- **CFR**, **Procyon**, **Fernflower** — 디컴파일러
- **JD-GUI** — GUI
- **Bytecode Viewer** — 종합

```bash
# JAR 안의 클래스 디컴파일
java -jar cfr.jar myapp.jar --outputdir decompiled/
```

→ **본인 회사 앱의 JAR**도 누군가 가져가면 거의 소스 수준으로 복원됨. **ProGuard, R8** 같은 난독화·축소 도구 사용, 또한 **비밀을 JAR에 넣지 말 것** (Kerckhoffs 원칙).

### 6.4 안드로이드 APK 리버싱 (참고)
- `apktool d app.apk` → smali
- `jadx` → 디컴파일된 Java
- **앱 안의 API 키·서명 키 모두 노출** 가능

---

## 7. 악성코드 분석 입문

### 7.1 분류
- **Trojan** / Backdoor
- **RAT** (Remote Access Trojan)
- **Ransomware**
- **Cryptominer**
- **Worm**
- **Rootkit**
- **Wiper**
- **Loader / Dropper**
- **Stealer** (자격증명 탈취)

### 7.2 안전한 분석 환경
- **격리 VM** (네트워크 차단)
- **REMnux** (Linux용 악성코드 분석 배포판)
- **Flare-VM** (Windows용, Mandiant)
- 스냅샷으로 복원

### 7.3 도구
- **strings** — 평문 문자열 추출
- **PEview, PEStudio** — Windows PE 분석
- **YARA** — 시그니처 매칭
- **VirusTotal** — 다중 AV 검증 + 행위 (해시만 제출, 샘플 업로드는 신중)
- **Any.Run, Joe Sandbox, Hatching Triage** — 자동 sandbox
- **Hybrid Analysis**

### 7.4 IOC (Indicator of Compromise)
- 해시 (MD5/SHA256)
- IP·도메인
- URL
- 파일 경로·이름
- 레지스트리 키
- TLS JA3/JA4 핑거프린트
- YARA 규칙

**MITRE ATT&CK** 프레임워크로 분류 (TTPs).

---

## 8. CVE 분석 워크플로우 (실용)

새 CVE가 발표되면:
1. **NVD/CVE.mitre.org** 에서 CVSS·기술 설명 확인
2. 영향받는 버전·환경 확인 (본인 서비스에 해당하는가)
3. **PoC** 공개 여부 (PoC 공개되면 활용 빨라짐)
4. **벤더 어드바이저리** 패치·미티게이션
5. SBOM·의존성 트리에서 영향 컴포넌트 식별
6. WAF/IDS 룰 임시 적용
7. 패치 → 검증 → 배포

### 도구
- **GitHub Advisory Database**
- **OSV.dev** (Google, 통합)
- **Sonatype OSS Index**
- **vulncheck.com**, **CISA KEV** (실제 악용 사례)

---

## 9. 본 부트캠프 맥락

Java/Spring 개발자가 바이너리 보안을 직접 코딩할 일은 적다. 하지만:
- **JNI 사용 코드 리뷰**
- **신규 CVE의 PoC 분석**
- **자체 JAR 난독화·역공학 방어**
- **악성 첨부파일 조사** (이메일·업로드)

기본 이해가 필요. 더 깊이 가려면 별도 트랙(Exploit Development, Malware Analysis).

---

## 10. 실습

### 실습 1.1 — JAR 디컴파일 체험
1. `vulnerable_app`을 `./gradlew bootJar` 빌드
2. `cfr.jar` 또는 `jadx` 로 디컴파일
3. 본인 소스와 비교 — 어느 정도 복원되나
4. ProGuard/R8 적용 후 다시 시도

### 실습 1.2 — strings로 비밀 찾기
의도적으로 비밀 하드코딩한 jar에 `strings`:
```bash
strings vuln-app.jar | grep -iE "password|secret|key|token"
```

### 실습 1.3 — Ghidra 설치 + Hello World 분석
간단한 C 프로그램 컴파일 → Ghidra 로딩 → main 함수 디컴파일 보기.

### 실습 1.4 — OSV 의존성 분석
```bash
# osv-scanner
osv-scanner --lockfile=gradle.lockfile
```

### 실습 1.5 — CISA KEV 점검
https://www.cisa.gov/known-exploited-vulnerabilities-catalog
본인 환경에 해당하는 KEV가 있는지 검사.

---

## 정리
- 바이너리 취약점은 깊은 분야, 입문 수준이면 충분
- **Java 앱은 JAR이 거의 소스라는 사실** 인지 — 비밀 분리, 난독화
- 새 CVE는 워크플로우 따라 빠르게 평가·패치
