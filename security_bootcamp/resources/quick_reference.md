# 빠른 참조 (Quick Reference)

> 부트캠프 학습 중·이후 코드 작성 시 매번 펴볼 한 페이지.

## 1. 코드 리뷰 5가지 질문
1. 신뢰 경계가 어디? (사용자 입력은 모두 경계 바깥)
2. 인증·인가는 누가 언제 검사?
3. 이 데이터가 변조되면?
4. 100배 큰 입력 들어오면?
5. 실패 시 fail-secure인가?

## 2. STRIDE 빠른 점검
- **S**poofing — 인증 우회 가능?
- **T**ampering — 변조 가능?
- **R**epudiation — 부인 가능? 감사 로그 충분?
- **I**nformation Disclosure — 누출 경로?
- **D**oS — 자원 한도?
- **E**levation of Privilege — 권한 상승 가능?

## 3. Spring Security 안전 기본 (체크용)
```java
http
  .csrf(Customizer.withDefaults())                    // ✅ CSRF ON
  .sessionManagement(s -> s
    .sessionFixation().migrateSession()                // ✅ Fixation 방어
    .maximumSessions(1))
  .headers(h -> h
    .contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'"))
    .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true))
    .frameOptions(f -> f.deny())
    .contentTypeOptions(Customizer.withDefaults()))
  .authorizeHttpRequests(a -> a
    .requestMatchers("/login","/css/**","/js/**").permitAll()
    .anyRequest().authenticated())
  .requiresChannel(c -> c.anyRequest().requiresSecure());  // 운영

@Bean PasswordEncoder pe() {
  return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```

## 4. Thymeleaf 안전 패턴
| 의도 | 사용할 것 | 피할 것 |
|------|---------|--------|
| 텍스트 출력 | `th:text="${x}"` | `th:utext` |
| 속성값 | `th:value`, `th:href`, `th:src` | `th:attr` with 사용자 입력 |
| URL 만들기 | `@{/path(p=${x})}` | concat |
| JS 변수 | `<script th:inline="javascript"> var x = [[${val}]]; </script>` | raw concat |

## 5. SQL 안전 패턴
```java
// JdbcTemplate
jdbc.queryForList("SELECT ... WHERE id = ?", id);

// JPA
@Query("SELECT u FROM User u WHERE u.username = :name")
User f(@Param("name") String n);

// MyBatis
<select>SELECT ... WHERE name = #{name}</select>   <!-- ✅ #{} -->
```

## 6. 위험 패턴 grep (코드베이스 자가 점검)
```bash
# SQL concat
grep -rnE "(SELECT|INSERT|UPDATE|DELETE).*\".*\"\s*\+\s*[a-zA-Z]" src/

# OS command 실행
grep -rn "Runtime\.\.exec\|new ProcessBuilder" src/

# 역직렬화
grep -rn "ObjectInputStream\|readObject\|XMLDecoder" src/

# HTTP 검증 끄기
grep -rn "trustAllCerts\|HostnameVerifier\|verify.*true" src/

# Thymeleaf 위험
grep -rn "th:utext\|\[(.*)\]" src/main/resources/templates/

# 비밀 의심
grep -rnE "(api[_-]?key|secret|password|token)\s*=" src/

# Spring 동적 뷰 이름
grep -rn 'return "[^"]*+.*"' src/main/java/

# SpEL 평가
grep -rn "parseExpression\|@Value.*#{" src/main/java/

# Mass assignment 의심
grep -rn "@ModelAttribute\|@RequestBody" src/main/java/
```

## 7. 비밀번호 정책 (NIST 2017+)
- 길이 ≥ 12 권장 (8 최소)
- 모든 ASCII + 유니코드
- 강제 복잡도 X — 길이가 더 중요
- 주기적 변경 강제 X
- 유출 PW 차단 (haveibeenpwned)
- BCrypt/Argon2id로 저장

## 8. 보안 헤더 6종 세트
```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
Content-Security-Policy: default-src 'self'; ...
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: geolocation=(), camera=()
```

## 9. 쿠키 안전 속성
```
Set-Cookie: SESSION=...; HttpOnly; Secure; SameSite=Lax; Path=/
```

## 10. 파일 업로드 7단계
1. 크기 제한
2. Tika로 매직 바이트 검증
3. 확장자 화이트리스트
4. 새 파일명 (UUID)
5. WebRoot 바깥 저장
6. 경로 정규화 + base 검증
7. 다운로드는 octet-stream + nosniff

## 11. SSRF 방어 체크
- [ ] URL 스킴 화이트리스트 (https만)
- [ ] 호스트 화이트리스트
- [ ] DNS 응답이 사설 IP면 거부
- [ ] 리다이렉트 따라가지 않음
- [ ] 응답 크기·시간 제한
- [ ] AWS IMDSv2 강제

## 12. 인증·세션 체크
- [ ] BCrypt 사용
- [ ] 사용자 열거 방지 (동일 응답·시간)
- [ ] Rate Limit (로그인·PW 재설정)
- [ ] 세션 ID 재발급 (로그인 시)
- [ ] HttpOnly + Secure + SameSite
- [ ] 로그아웃 시 세션 무효
- [ ] PW 변경 시 모든 세션 무효
- [ ] PW 재설정 토큰: 무작위·해시저장·만료·1회용

## 13. 접근 통제 체크
- [ ] `@PreAuthorize` 활성화
- [ ] 도메인 객체 소유자 검사 (조회 쿼리에 user 포함)
- [ ] Mass assignment 방지 (DTO 분리 또는 `@InitBinder`)
- [ ] 관리자 액션 별도 분리·MFA
- [ ] 권한 거부 시 404 (존재 은닉 옵션)

## 14. 로깅·에러 체크
- [ ] `server.error.include-stacktrace=never`
- [ ] 통합 ExceptionHandler + traceId
- [ ] 비밀번호·카드·PII는 로그 X
- [ ] 보안 이벤트(로그인 실패·권한 거부·관리자 행위) 로그 ON
- [ ] 알림 룰 (5xx 폭증·권한 거부 폭주)

## 15. 사고 대응 1차 행동 (메모)
1. **격리** — 확산 차단이 1순위
2. **보존** — 로그·디스크 원본 보존
3. **분류** — Sev 등급, 영향 추정
4. **소통** — 관련자 호출, 외부 발표 신중
5. **법적 신고** — PII 침해 시 72h 내
6. **복구 → Postmortem**

## 16. AWS 보안 최소 체크
- [ ] IAM Access Analyzer ON
- [ ] CloudTrail 모든 리전
- [ ] GuardDuty + Security Hub
- [ ] S3 Block Public Access 계정 레벨
- [ ] IMDSv2 강제
- [ ] root 계정 MFA + 미사용
- [ ] CloudTrail 로그 자체 별도 계정 보관

## 17. 컨테이너 안전 옵션
```bash
docker run --read-only --cap-drop=ALL \
  --security-opt no-new-privileges \
  -u 10000:10000 \
  --pids-limit 200 --memory 512m \
  --network app-net \
  myapp:tag
```

## 18. CI 보안 잡 4종
```yaml
- gitleaks                # Secret 스캔
- semgrep / spotbugs+fsb  # SAST
- dependency-check        # SCA
- trivy                   # Container
```

## 19. 자주 보는 명령 모음
```bash
# 인증서 만료
echo | openssl s_client -connect x:443 2>/dev/null | openssl x509 -noout -enddate

# 헤더 점검
curl -I https://example.com

# DNS 보안
dig +short TXT example.com
dig +short TXT _dmarc.example.com
dig +short CAA example.com

# 자가 Nmap
sudo nmap -sV -sC -p- -T4 -oA scan target

# Lynis 시스템 점검
sudo lynis audit system

# 이미지 스캔
trivy image myapp:tag

# 의존성
./gradlew dependencyCheckAnalyze
```

## 20. 매 PR 보안 질문 10개
1. 새 입력에 검증이 있는가?
2. 새 쿼리에 바인딩 사용했는가?
3. 새 출력은 어떤 컨텍스트인가? (HTML/JS/URL)
4. 새 인증·인가 영향?
5. 새 외부 호출의 URL은 검증되는가?
6. 새 파일 처리에 경로·타입 검증?
7. 새 의존성에 CVE는?
8. 새 비밀이 코드·로그·git에 없는가?
9. 에러는 안전하게 처리되는가?
10. 새 권한이 너무 넓지 않은가?
