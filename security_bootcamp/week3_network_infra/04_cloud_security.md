# Day 4 — 클라우드 보안 (AWS 중심)

> 클라우드 보안의 90%는 **IAM + 네트워크 + 설정 관리**. 코드 결함보다 설정 결함으로 더 많이 털린다.

## 1. Shared Responsibility Model

```
[고객]  ← 데이터, 앱, IAM, OS 설정, 네트워크 설정
[클라우드]  ← 하이퍼바이저, 물리 인프라, 가용성
```

EC2 / S3 / 컨테이너 모두 **고객 측 책임이 더 큼**. 매니지드 서비스(RDS, Lambda)는 OS·런타임 책임이 클라우드로 이동.

---

## 2. AWS IAM — 가장 중요

### 2.1 핵심 개념
- **User** — 사람 (가능하면 SSO로 대체)
- **Role** — 임시 자격증명. EC2/Lambda가 이걸로 권한 가짐
- **Policy** — JSON으로 권한 명시 (Allow/Deny + Action + Resource + Condition)
- **Group** — 사용자 묶음

### 2.2 Least Privilege 정책 작성

```json
// 좋음 — 특정 버킷의 특정 prefix만
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:GetObject", "s3:PutObject"],
    "Resource": "arn:aws:s3:::myapp-uploads/${aws:username}/*"
  }]
}
```

```json
// 나쁨 — 모든 버킷 모든 액션
{
  "Effect": "Allow",
  "Action": "s3:*",
  "Resource": "*"
}
```

### 2.3 Condition으로 추가 제약
```json
{
  "Effect": "Allow",
  "Action": "s3:GetObject",
  "Resource": "arn:aws:s3:::confidential/*",
  "Condition": {
    "IpAddress": {"aws:SourceIp": "203.0.113.0/24"},
    "Bool": {"aws:SecureTransport": "true"},
    "DateGreaterThan": {"aws:CurrentTime": "2024-01-01T00:00:00Z"}
  }
}
```

### 2.4 AssumeRole — 권한 위임
사람·서비스가 정적 키를 갖는 대신, 짧은 수명의 **임시 자격증명**.

```
[Developer] -- assume role --> [DevOpsRole] -- 작업
```
- 정적 access key 제로화
- 모든 행위가 STS·CloudTrail에 로그
- 키 노출 시 영향 제한 (만료됨)

### 2.5 IAM의 함정
- 와일드카드 (`*`) 남발
- `iam:PassRole` 위임 잘못 → 권한 상승
- 신뢰 정책에 `*` (어떤 계정에서든 assume 가능)
- 관리자 정책(AdministratorAccess)을 일반 사용자에게
- AWS Managed Policy 이름 보고 안심 (실제 권한 검증 필요)

### 2.6 도구
- **AWS IAM Access Analyzer** — 외부 노출 자원 탐지
- **prowler** — 종합 audit (수백 개 체크)
- **ScoutSuite** — multi-cloud
- **Cloudsplaining** — IAM 정책 분석
- **AWS Trusted Advisor**

---

## 3. EC2 메타데이터·IMDS

**Capital One 사고의 핵심.**

```bash
# IMDSv1 (위험)
curl http://169.254.169.254/latest/meta-data/iam/security-credentials/MyRole

# IMDSv2 (안전)
TOKEN=$(curl -X PUT "http://169.254.169.254/latest/api/token" \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
curl -H "X-aws-ec2-metadata-token: $TOKEN" \
  http://169.254.169.254/latest/meta-data/
```

### 강제 IMDSv2
```bash
aws ec2 modify-instance-metadata-options \
  --instance-id i-... \
  --http-tokens required \
  --http-put-response-hop-limit 1
```

**Hop limit 1** — 컨테이너에서 IMDS 못 닿게 추가 방어.

---

## 4. S3 — 사고 단골

### 4.1 흔한 실수
- 버킷이 public (인덱싱 가능)
- ACL `AllUsers`, `AuthenticatedUsers` 부여
- Static website hosting + 잘못된 정책
- Pre-signed URL 무한 수명
- 백업이 별도 격리·암호화 안 됨

### 4.2 방어
- **S3 Block Public Access** 계정 수준 ON (4가지 옵션 모두)
- **버킷 정책**으로 명시적 deny
- **암호화** — SSE-S3 또는 SSE-KMS (KMS 권장 — 키 회전·감사)
- **버전 관리** — 실수·악성 삭제 대응
- **MFA Delete** — 중요 버킷
- **Object Lock** — WORM (랜섬웨어 대응)
- **로깅** — Server Access Log, CloudTrail Data Events

### 4.3 S3 스캐너
- **bucket-stream** — CT 로그 기반 신규 버킷 발견
- **s3scanner** — 버킷 이름 추측·점검

---

## 5. VPC 설계

### 5.1 기본 구조
```
VPC (10.0.0.0/16)
├── Public Subnet (10.0.1.0/24)    ← ALB, NAT GW, Bastion
└── Private Subnet (10.0.2.0/24)   ← EC2, RDS, ElastiCache
```
앱은 Private Subnet, 외부 노출은 ALB만.

### 5.2 Security Group vs NACL
| | SG | NACL |
|---|---|---|
| 레벨 | ENI(인스턴스) | Subnet |
| Stateful | ✅ | ❌ (양방향 명시 필요) |
| Rule | Allow만 | Allow + Deny |
| 평가 순서 | 모두 평가 | 번호순 |

### 5.3 VPC Endpoint (Gateway/Interface)
S3·DynamoDB로 가는 트래픽이 인터넷 거치지 않도록. **SSRF로 노출되어도 외부 인터넷 안 거치므로 일부 페이로드 차단**.

### 5.4 VPC Flow Logs
모든 트래픽 메타데이터. 침해 분석 필수.

---

## 6. CloudTrail · CloudWatch · GuardDuty

| 서비스 | 무엇 |
|--------|------|
| **CloudTrail** | API 호출 감사 로그 |
| **CloudWatch Logs** | 앱·시스템 로그 집중 |
| **GuardDuty** | 위협 탐지 (ML 기반) |
| **Security Hub** | 종합 대시보드 |
| **Detective** | 침해 분석 |
| **Config** | 설정 변경 추적·규정 준수 |
| **Inspector** | 취약점 스캔 (EC2, ECR, Lambda) |
| **Macie** | S3에서 PII 탐지 |

**최소 셋팅**: CloudTrail 모든 리전 ON + GuardDuty + Security Hub + IAM Access Analyzer.

---

## 7. KMS — 키 관리

- 데이터 암호화는 거의 모든 서비스가 KMS 통합
- **Customer Managed Key (CMK)** 사용 권장 (AWS Managed 보다 통제·감사 강함)
- **Key Policy** + **Grant**
- **Automatic rotation** (CMK는 1년)
- **CloudTrail** 로 키 사용 감사

### Envelope Encryption
큰 데이터는 데이터 키(DEK)로, DEK는 CMK로 암호화. 효율적.

---

## 8. 컨테이너·서버리스 추가 고려

### 8.1 ECS·EKS
- Task Role 최소화
- Network Mode (awsvpc) + Security Group per task
- 이미지 스캔 (ECR scanning, Trivy)
- Secrets는 Secrets Manager/Parameter Store, 환경변수 직접 X

### 8.2 Lambda
- 실행 역할 최소화
- 환경변수 암호화 (KMS)
- VPC 안에 둘지 결정 (cold start vs 보안)
- 함수당 별도 역할 (큰 함수에 큰 권한 X)

---

## 9. Multi-Cloud / GCP·Azure

원리는 같다. 용어만 다름:
| AWS | GCP | Azure |
|-----|-----|-------|
| IAM | IAM | Azure AD / RBAC |
| EC2 | Compute Engine | VM |
| S3 | Cloud Storage | Blob Storage |
| RDS | Cloud SQL | Azure SQL |
| Lambda | Cloud Functions | Functions |
| CloudTrail | Cloud Audit Logs | Activity Log |
| GuardDuty | Security Command Center | Defender for Cloud |
| IMDS | Metadata Server | IMDS |
| KMS | Cloud KMS | Key Vault |

---

## 10. CIS Benchmark · 규정 준수

- **CIS Benchmark** — 클라우드/OS/DB별 보안 모범 사례
- **PCI-DSS** — 카드 결제
- **ISO 27001** — 정보보안 관리
- **국내 ISMS-P** — 정보보호·개인정보보호 인증
- **K-ISMS, GDPR** — 한국 ↔ 유럽 개인정보

본인 산업·고객에 따라 어떤 게 적용되는지 확인.

---

## 11. 실제 사례

### Capital One (2019)
- WAF 잘못 구성 → SSRF
- IMDSv1 → IAM 자격증명 탈취
- 과다 IAM 권한 → 700개 S3 버킷 접근
- 1.06억 명 정보 유출, $190M 합의

### Verizon (2017)
- S3 버킷 public, 1400만 고객 정보 노출
- 협력사 실수

### Microsoft (2022, BlueBleed)
- Azure Blob misconfiguration
- 6.5만 기업 데이터 노출

### Twitch (2021)
- 잘못 구성된 서버 → 소스코드·payout 전체 유출 (125GB)
- 사용자 인증 결함

---

## 12. 실습

### 실습 4.1 — IAM Access Analyzer
AWS 계정에서 활성화 → 외부 접근 가능한 자원 리포트 확인.

### 실습 4.2 — Prowler 실행
```bash
docker run --rm -ti -v ~/.aws:/root/.aws toniblyx/prowler:latest aws --severity high
```
중대 권고사항 정리.

### 실습 4.3 — Least Privilege 정책 1개 만들기
시나리오: 앱이 특정 S3 prefix만 읽고 쓰고, 특정 SecretsManager 시크릿만 읽음. 1개 IAM Policy 작성.

### 실습 4.4 — IMDSv2 강제
EC2 인스턴스(랩) 1개에 IMDSv2 강제 후 SSRF 시뮬레이션:
- IMDSv1 호출 → 거부
- IMDSv2 호출 → 토큰 필요

### 실습 4.5 — S3 Public Block
모든 계정에 Account-level S3 Block Public Access 적용했는지 확인.
