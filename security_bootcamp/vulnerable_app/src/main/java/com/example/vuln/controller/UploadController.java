package com.example.vuln.controller;

import org.apache.tika.Tika;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

/**
 * 파일 업로드 학습용.
 *
 * /vuln/upload — 파일명 그대로 저장, 확장자·내용 검증 없음 (Path Traversal + 실행 가능 파일 업로드)
 * /safe/upload — Tika로 실제 MIME 검증, UUID 새 이름, 화이트리스트, 경로 정규화
 */
@Controller
public class UploadController {

    private static final Path VULN_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "vuln_uploads");
    private static final Path SAFE_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "safe_uploads");

    private static final Set<String> ALLOWED_MIMES = Set.of(
        "image/jpeg", "image/png", "image/gif", "application/pdf"
    );
    private static final Set<String> ALLOWED_EXTS = Set.of("jpg", "jpeg", "png", "gif", "pdf");

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5MB

    @GetMapping("/vuln/upload")
    public String vulnForm() { return "vuln/upload"; }

    @GetMapping("/safe/upload")
    public String safeForm() { return "safe/upload"; }

    /**
     * ❌ 위험: 사용자 파일명 그대로 사용 → 디렉토리 트래버설·임의 확장자.
     */
    @PostMapping("/vuln/upload")
    public String vulnUpload(@RequestParam("file") MultipartFile file, Model model) throws IOException {
        Files.createDirectories(VULN_DIR);
        Path target = VULN_DIR.resolve(file.getOriginalFilename()); // ../../ 등 가능
        file.transferTo(target);
        model.addAttribute("savedAs", target.toString());
        return "vuln/upload";
    }

    /**
     * ✅ 안전:
     *  1) 크기 제한
     *  2) Tika 매직 바이트로 진짜 MIME 검증
     *  3) 확장자 화이트리스트
     *  4) 파일명을 UUID로 재발급
     *  5) 경로 정규화 + base 확인 (Path Traversal 차단)
     */
    @PostMapping("/safe/upload")
    public String safeUpload(@RequestParam("file") MultipartFile file, Model model) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_SIZE_BYTES) {
            model.addAttribute("error", "파일 크기 또는 빈 파일 오류");
            return "safe/upload";
        }
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String ext = StringUtils.getFilenameExtension(original);
        if (ext == null || !ALLOWED_EXTS.contains(ext.toLowerCase())) {
            model.addAttribute("error", "허용되지 않는 확장자");
            return "safe/upload";
        }
        Tika tika = new Tika();
        String detectedMime = tika.detect(file.getInputStream());
        if (!ALLOWED_MIMES.contains(detectedMime)) {
            model.addAttribute("error", "허용되지 않는 파일 형식: " + detectedMime);
            return "safe/upload";
        }

        Files.createDirectories(SAFE_DIR);
        String newName = UUID.randomUUID() + "." + ext.toLowerCase();
        Path base = SAFE_DIR.toAbsolutePath().normalize();
        Path target = base.resolve(newName).normalize();
        if (!target.startsWith(base)) {
            model.addAttribute("error", "잘못된 경로");
            return "safe/upload";
        }
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        model.addAttribute("savedAs", target.toString());
        return "safe/upload";
    }
}
