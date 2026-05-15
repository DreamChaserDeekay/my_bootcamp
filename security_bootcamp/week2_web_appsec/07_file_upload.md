# Day 4 (3/3) — 파일 업로드 보안

> 파일 업로드는 웹 앱에서 가장 위험한 기능 중 하나. 한 곳에서 7~8가지 취약점이 동시에 발생 가능.

## 1. 파일 업로드 공격 매트릭스

| 공격 | 예 |
|------|---|
| **Unrestricted File Upload (RCE)** | `.jsp`, `.war`, `.php` 업로드 후 실행 |
| **Path Traversal** | `../../etc/passwd`로 임의 위치 저장 |
| **Polyglot** | 이미지로 보이지만 실행 가능한 파일 |
| **XXE via 이미지/문서** | SVG, DOCX 내 XML |
| **Stored XSS** | 파일명·HTML/SVG 본문 |
| **DoS** | 거대 파일, Zip Bomb (수 MB → 수 TB 해제) |
| **MIME Confusion** | 이미지인 척하지만 브라우저는 HTML로 해석 |
| **Antivirus 우회** | 시그니처 회피 |
| **공유 스토리지 오염** | 같은 S3에 운영·로그 섞임 |

---

## 2. 안전한 파일 업로드 — 체크리스트

### 2.1 파일 종류 제한
- **확장자 화이트리스트** (블랙리스트 금지)
- 단일 점만 허용 (`a.tar.gz` 같은 케이스는 별도 검토)
- 사용자가 보내는 확장자·MIME 신뢰 X

### 2.2 실제 내용 검증
```java
import org.apache.tika.Tika;

private static final Set<String> ALLOWED_MIMES = Set.of(
    "image/jpeg", "image/png", "image/gif", "application/pdf"
);

public void validate(MultipartFile file) throws IOException {
    Tika tika = new Tika();
    String detectedMime = tika.detect(file.getInputStream());  // 매직 바이트
    if (!ALLOWED_MIMES.contains(detectedMime)) {
        throw new IllegalArgumentException("Unsupported file type");
    }
}
```
**Apache Tika**는 매직 바이트로 진짜 타입 감지. 확장자만 보지 말 것.

### 2.3 크기 제한
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 20MB
```
+ 디스크 사용량 quota 별도.

### 2.4 파일명 새로 만들기
**원본 파일명을 절대 그대로 저장 경로에 사용하지 말 것.**
```java
// ❌
File dest = new File("/uploads/" + file.getOriginalFilename());

// ✅
String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
if (!ALLOWED_EXT.contains(ext.toLowerCase())) throw ...;
String safe = UUID.randomUUID() + "." + ext;
Path target = Paths.get("/uploads/").resolve(safe).normalize();

// Path Traversal 추가 방어
if (!target.startsWith(Paths.get("/uploads/"))) {
    throw new SecurityException("Invalid path");
}
file.transferTo(target);
```

### 2.5 저장 위치
- **WebRoot 바깥**에 저장 (`/var/uploads/`)
- 컨트롤러를 통해 권한 검사 후 스트림
- 실행 권한 없는 디렉토리 (`chmod 644`)
- 가능하면 **외부 스토리지(S3 등)** + presigned URL

### 2.6 다운로드 시 헤더
```java
@GetMapping("/files/{id}")
public ResponseEntity<Resource> download(@PathVariable Long id, Authentication auth) {
    FileEntity f = fileService.getForUser(id, auth.getName());  // 권한 검사
    Resource r = new FileSystemResource(f.getStoragePath());

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)  // 항상 octet-stream으로 강제
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + encodeFilename(f.getOriginalName()) + "\"")
        .header("X-Content-Type-Options", "nosniff")  // 매우 중요
        .header("Content-Security-Policy", "default-src 'none'")
        .body(r);
}
```

**X-Content-Type-Options: nosniff** 없으면 브라우저가 내용을 보고 HTML로 해석할 수 있음 → 업로드한 이미지가 XSS가 됨.

### 2.7 별도 도메인에서 서빙
이미지/파일을 메인 도메인(`app.com`)이 아닌 **별도 도메인**(`usercontent.app.com` 또는 `cdn.app.com`)에서 서빙. XSS가 일어나도 메인 세션 쿠키 격리.

---

## 3. 이미지·문서 파싱의 위험

### 3.1 ImageMagick CVE-2016-3714 (ImageTragick)
이미지 처리 라이브러리에 magic 형식 입력으로 RCE. → 이미지 처리는 별도 컨테이너·sandbox.

### 3.2 SVG
**SVG는 XML이고 JavaScript 실행 가능.** `<svg onload="alert(1)">` 처럼. 업로드 받지 말거나, sanitize.

### 3.3 ZIP Slip (CVE-2018-1002200)
ZIP/TAR에 `../../../etc/passwd` 같은 경로 포함된 엔트리 → 압축 해제 시 임의 위치 작성.
```java
// ❌
ZipEntry entry = zip.getNextEntry();
File out = new File(destDir, entry.getName());  // ../../ 들어가면 탈출

// ✅
Path destPath = destDir.resolve(entry.getName()).normalize();
if (!destPath.startsWith(destDir)) throw new SecurityException("Zip Slip");
```

### 3.4 Zip Bomb
42KB → 4.5 PB의 zip 폭탄. 압축 해제 전 **압축 비율·총 크기 검증**.

---

## 4. 운영 — 안티바이러스·격리

- 업로드된 파일은 **ClamAV** 등 AV 스캔 후 사용
- 처리 잡(이미지 리사이즈 등)은 **별도 컨테이너**에서 격리 실행 (network 차단)
- S3에 올라가면 Lambda + GuardDuty Malware Protection

---

## 5. Path Traversal — 업로드 외 다른 곳에서도

### 5.1 다운로드 엔드포인트
```java
// ❌ 위험
@GetMapping("/download")
public Resource download(@RequestParam String name) {
    return new FileSystemResource("/uploads/" + name);  // ../ 가능
}
```

```java
// ✅ 안전
@GetMapping("/download")
public Resource download(@RequestParam String name) {
    Path base = Paths.get("/uploads").toAbsolutePath().normalize();
    Path target = base.resolve(name).normalize();
    if (!target.startsWith(base)) throw new IllegalArgumentException();
    if (!Files.isRegularFile(target)) throw new ResourceNotFoundException();
    return new PathResource(target);
}
```

### 5.2 정적 리소스 매핑 함정
Spring `ResourceHandler`에 외부 경로를 매핑하면 더 주의.

---

## 6. 실습

### 실습 7.1 — 업로드 우회
`vulnerable_app/`의 `/vuln/upload`에:
- `shell.jsp.png` (이중 확장자)
- `image.png` 인데 내용은 PHP
- `../../../../tmp/evil.txt` 파일명
- 100MB zip bomb
- SVG with `<script>`
- `.htaccess` 또는 `web.xml` 같은 위험 파일명

`/safe/upload`로 패치 후 동일 시도 → 모두 차단

### 실습 7.2 — Path Traversal
`/vuln/download?file=../../../../etc/hosts` 로 시스템 파일 읽기.

### 실습 7.3 — Zip Slip
악성 zip을 만들어 압축 해제 엔드포인트에 전송:
```python
import zipfile
with zipfile.ZipFile('evil.zip', 'w') as z:
    z.writestr('../../../../tmp/pwned.txt', 'gotcha')
```

### 실습 7.4 — 회사 코드 점검
- `MultipartFile`을 받는 모든 컨트롤러 찾기
- 위 §2 체크리스트 항목 비교
- `File`, `Path`로 사용자 입력을 받는 곳 모두 검토

---

## 정리 — 업로드 한 줄 원칙
> **원본 파일명·확장자·MIME 은 모두 신뢰 안 한다. Tika로 진짜 타입 검증, UUID 새 이름, WebRoot 바깥 저장, 다운로드는 항상 octet-stream + nosniff.**
