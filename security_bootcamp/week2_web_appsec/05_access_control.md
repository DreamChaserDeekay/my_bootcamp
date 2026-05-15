# Day 4 (1/3) — 접근 통제 (Access Control)

> OWASP Top 10 2021의 1위. 모든 사고의 약 30%가 여기서 발생. **자동 도구로 찾기 어려운** 비즈니스 로직 결함이라 코드 리뷰가 핵심.

## 1. 접근 통제 분류

| 종류 | 설명 | 예 |
|------|------|---|
| **Vertical (수직)** | 역할 간 권한 상승 | 일반 유저 → 관리자 |
| **Horizontal (수평)** | 같은 역할 내 타인 데이터 접근 | 유저A → 유저B의 주문 조회 |
| **Context-dependent** | 상태·시점에 따른 권한 | 결제 완료 전엔 환불 불가 |

---

## 2. IDOR (Insecure Direct Object Reference) — 가장 흔한 결함

```
GET /orders/123    → 본인 주문
GET /orders/124    → 남의 주문도 보임 ← IDOR
```

### 발견 방법
- 모든 ID 파라미터를 ±1 변경해 본다
- UUID라도 검증 누락이면 동일 문제 (어디서 새는지가 다를 뿐)
- Burp Repeater로 자동화

### 코드 패턴

```java
// ❌ 위험 (제일 흔함)
@GetMapping("/orders/{id}")
public OrderDto get(@PathVariable Long id) {
    return orderRepo.findById(id).orElseThrow();
}

// ✅ 안전 — 쿼리에 사용자 포함
@GetMapping("/orders/{id}")
public OrderDto get(@PathVariable Long id, Authentication auth) {
    return orderRepo.findByIdAndOwner(id, auth.getName())
        .orElseThrow(() -> new ResourceNotFoundException());
}

// ✅ 또는 서비스에서 검사 (PreAuthorize)
@PreAuthorize("@orderSec.canView(#id, authentication.name)")
@GetMapping("/orders/{id}")
public OrderDto get(@PathVariable Long id) { ... }
```

### 한 단계 더 — UUID 사용?
**UUID는 IDOR 방어가 아니다.** 추측을 어렵게 할 뿐 권한 검사를 대신 못 함.
- UUID도 노출되면 (URL 공유, 로그, Referer) 끝
- 그래도 **순차 ID보다는 UUID 권장** (자동 enumeration 방어, 데이터 양 추정 방지)

---

## 3. 수직 권한 상승 (Privilege Escalation)

### 흔한 결함
- 관리자 화면을 "메뉴 숨기기"로만 보호 (직접 URL은 호출 가능)
- 권한 검사를 컨트롤러 일부에만
- 일반 API와 관리자 API가 같은 엔드포인트, 파라미터로만 구분

### 예
```java
// ❌ 위험: type 파라미터로 동작 결정, 권한 검사 약함
@PostMapping("/users/update")
public void update(@RequestBody UserUpdate dto) {
    User user = userRepo.findById(dto.getId()).orElseThrow();
    user.setRole(dto.getRole());  // 일반 유저가 dto.role = ADMIN 보내면?
    userRepo.save(user);
}

// ✅ 안전
@PostMapping("/users/update")
public void update(@RequestBody UserUpdateDto dto, Authentication auth) {
    User user = userRepo.findByUsername(auth.getName()).orElseThrow();
    // 일반 필드만 업데이트
    user.setEmail(dto.getEmail());
    user.setNickname(dto.getNickname());
    // role은 별도 admin-only endpoint
    userRepo.save(user);
}

@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/admin/users/{id}/role")
public void updateRole(@PathVariable Long id, @RequestParam Role role) {
    User u = userRepo.findById(id).orElseThrow();
    u.setRole(role);
    userRepo.save(u);
}
```

---

## 4. Forced Browsing (강제 브라우징)

URL이 비공개일 거라 가정하고 보호 안 함.

```
/admin/dashboard      ← 인증된 사용자가 URL 알면 그냥 접근
/api/internal/sync    ← 내부용이라며 인증 없음
/swagger-ui.html      ← 운영에 공개?
/h2-console           ← Spring 개발용 콘솔 공개?
```

**원칙**: 모든 엔드포인트는 기본 인증 요구. `permitAll`은 명시적으로만.

```java
http.authorizeHttpRequests(a -> a
    .requestMatchers("/login", "/css/**", "/js/**", "/img/**").permitAll()
    .anyRequest().authenticated()  // 명시적 기본값
);
```

---

## 5. RBAC vs ABAC

### RBAC (Role-Based)
사용자에게 역할(ADMIN, USER, MANAGER) 부여, 역할에 권한.
- 단순, 관리 쉬움
- 세밀한 권한 표현 한계

### ABAC (Attribute-Based)
사용자·자원·환경의 속성으로 정책.
- "부서가 같고, 근무시간 내, 본인 팀 데이터일 때 수정 가능"
- 복잡하지만 유연

### 실무 권장
- 역할은 RBAC로 관리
- 도메인 객체 접근은 코드 레벨에서 속성 검사
- 정말 복잡하면 OPA(Open Policy Agent) 등 정책 엔진

---

## 6. 권한 검사 위치

### ❌ 잘못된 곳
- 프론트엔드만 (메뉴 숨김)
- 컨트롤러 일부만
- AOP·필터에서 일부만

### ✅ 권장: 서비스/도메인 레이어
- 모든 경로(컨트롤러, 스케줄러, 메시지 큐 컨슈머)가 결국 서비스를 거치므로 누락 없음
- `@PreAuthorize` 메서드 보안

### Spring Method Security
```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class MethodSecurityConfig {}

@Service
public class OrderService {
    @PreAuthorize("hasRole('USER') and @orderSec.isOwner(#orderId, authentication.name)")
    public Order get(Long orderId) { ... }

    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long orderId) { ... }

    @PostAuthorize("returnObject.owner == authentication.name")
    public Order getMine(Long id) { ... }   // 결과를 검사 (어떤 ID든 받아 결과 검사)
}
```

---

## 7. 비즈니스 로직 우회 (Logic Flaws)

코드 자체는 정상이지만 **비즈니스 규칙 누락**.

### 예시
- 쿠폰 사용 횟수 검증 없음 → 같은 쿠폰 100번 사용
- 결제 금액 클라이언트 계산 → 1원으로 변조
- 환불을 결제 전에 호출 가능
- 송금 한도 검사 없음
- 비밀번호 변경 시 현재 PW 검증 안 함
- 회원가입 후 이메일 인증 전인데 글쓰기 가능
- 패스워드 재설정 토큰 발급 시 차단 없음 (대량 발급 → 메일 폭탄)

### 방어
- **상태 전이 모델**로 명시 (UML 상태도)
- **테스트에 어뷰즈 케이스 포함** (음수, 0, 매우 큰 값, 동시 요청)
- 비즈니스 규칙은 데이터베이스 제약·트랜잭션으로도 강제
  ```sql
  ALTER TABLE coupons ADD CONSTRAINT used_le_max CHECK (used_count <= max_count);
  ```

### Race Condition
```java
// ❌ 동시 호출 시 한 번만 쓸 수 있는 쿠폰이 여러 번 사용됨
public void useCoupon(Long couponId) {
    Coupon c = couponRepo.findById(couponId).orElseThrow();
    if (c.isUsed()) throw new IllegalState();
    c.setUsed(true);
    couponRepo.save(c);
}

// ✅ 락 또는 원자적 UPDATE
@Transactional
public void useCoupon(Long couponId) {
    Coupon c = couponRepo.findByIdForUpdate(couponId)  // SELECT ... FOR UPDATE
        .orElseThrow();
    if (c.isUsed()) throw new IllegalState();
    c.setUsed(true);
}

// 또는 update 카운트로 검증
@Modifying
@Query("UPDATE Coupon c SET c.used = true WHERE c.id = :id AND c.used = false")
int markUsed(@Param("id") Long id);
// 반환 1이면 성공, 0이면 이미 사용됨
```

---

## 8. 페이지네이션·검색에서의 정보 누출

```java
// ❌
@GetMapping("/posts")
public List<Post> list(@RequestParam(required = false) String author) {
    if (author != null) {
        return postRepo.findByAuthor(author);  // 비공개 게시판도 나옴?
    }
    return postRepo.findAll();  // 비공개 글이 섞여 나옴
}
```

- 검색·목록은 항상 사용자 권한 반영
- "조회수", "좋아요" 같은 부가 정보로 비공개 글 존재 추정 가능
- ID 순차로 페이지네이션 시 비공개 글 ID 알면 직접 접근 시도

---

## 9. 관리자 기능 분리

대형 사고는 보통 **관리자 계정 탈취**에서 발생.

### 권장
- 관리자 UI는 **별도 호스트** (`admin.company.com`) — IP 화이트리스트·VPN
- 관리자 계정 MFA 필수
- 관리자 행위 전수 감사 로그
- 위험 행위(대량 데이터 조회·삭제)는 별도 승인 워크플로우

---

## 10. 실습

### 실습 5.1 — IDOR
- `vulnerable_app/`의 `/vuln/orders/{id}` 에 본인 주문 ID로 접근
- ID를 ±1 변경 → 다른 사용자 주문 보이는지 확인
- 패치 후 (`/safe/orders/{id}`) 동일 시도 → 404 또는 403

### 실습 5.2 — 권한 상승 시도
- 일반 사용자 로그인 후 `/admin/*` 직접 호출
- Burp로 `Role: ADMIN` 같은 헤더 추가해 봄 (서버가 신뢰하는지)
- 본인 폼에 `role=ADMIN` 추가 POST (Mass Assignment)

### 실습 5.3 — 본인 서비스 권한 매트릭스 작성
| 리소스 | 익명 | 일반 | 매니저 | 관리자 |
|------|------|------|------|------|
| /게시글 목록 | R | R | R | R |
| /게시글 작성 | - | C | C | C |
| /게시글 수정 | - | 본인만 | 본인+팀 | All |
| /게시글 삭제 | - | 본인만 | 본인+팀 | All |
| /사용자 관리 | - | - | - | CRUD |

코드의 실제 검사와 비교.

### 실습 5.4 — Race Condition 시도
간단한 쿠폰 차감 코드를 직접 만들고:
```bash
# 동시 100회 호출
seq 100 | xargs -P 50 -I{} curl -X POST http://localhost:8080/coupon/use/1
```
사용 횟수가 1번 이상 되는지 확인. 트랜잭션·락 추가 후 재시도.

---

## 정리
- **접근 통제는 비즈니스 로직과 얽혀 자동 도구로 거의 못 찾는다.**
- 코드 리뷰에서 "**이 데이터의 주인은 누구?**" 를 매번 묻는다.
- 컨트롤러보다 서비스/도메인 레이어에서 검사.
- 동시성·상태 전이까지 고려한 비즈니스 룰.
