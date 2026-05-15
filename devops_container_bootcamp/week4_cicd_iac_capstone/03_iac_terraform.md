# Day 3 — IaC · Terraform

## 한 줄 요약

**Infrastructure as Code** — 인프라(VPC, EC2, k8s 클러스터, DB)를 코드로 정의. **Terraform**이 사실상 표준. **Declarative + State + Plan/Apply**로 안전하게 변경.

## 학습 목표

- [ ] IaC의 가치 (재현·버전관리·리뷰)
- [ ] Terraform 핵심 개념 (provider·resource·variable·output·state)
- [ ] plan vs apply
- [ ] state backend (S3·GCS·Terraform Cloud)
- [ ] module로 재사용
- [ ] terraform import (기존 자원 가져오기)

---

## IaC의 가치

### Before — 클릭으로 인프라 만들기

```
AWS Console에서 클릭:
  - VPC 만들기
  - Subnet 만들기
  - Security Group ...
```

문제:
- **재현 불가** — 같은 환경 두 번 만들기 어려움
- **버전 관리 X** — 누가 무엇을 언제 바꿨나?
- **리뷰 불가** — 다른 사람이 검토 X
- **drift** — 운영 중 수동 변경 → 의도와 다름

### After — Terraform

```hcl
resource "aws_vpc" "main" {
  cidr_block = "10.0.0.0/16"
  
  tags = {
    Name = "main"
    Env  = "prod"
  }
}
```

git에 commit → PR review → 적용. 같은 코드로 dev·staging·prod 환경 재현.

---

## Terraform 설치

```powershell
# Windows
choco install terraform

# Mac
brew install hashicorp/tap/terraform

# 또는 tfenv (버전 관리)
brew install tfenv
tfenv install 1.9.5

terraform version
```

---

## 핵심 개념

### Provider — 어떤 플랫폼?

```hcl
# main.tf
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    github = {
      source  = "integrations/github"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = "ap-northeast-2"
}

provider "github" {
  token = var.github_token
  owner = "my-org"
}
```

```bash
terraform init       # provider 다운로드
```

### Resource — 실제 자원

```hcl
resource "aws_instance" "web" {
  ami           = "ami-0c55b159cbfafe1f0"
  instance_type = "t3.micro"
  
  tags = {
    Name = "web-server"
  }
}
```

```bash
terraform plan       # 어떤 변경이 일어날지
terraform apply      # 실제 변경
```

### Variable

```hcl
# variables.tf
variable "region" {
  type        = string
  default     = "ap-northeast-2"
  description = "AWS region"
}

variable "instance_type" {
  type    = string
  default = "t3.micro"
}

variable "tags" {
  type = map(string)
  default = {
    Team = "platform"
  }
}
```

```hcl
# main.tf 안에서
resource "aws_instance" "web" {
  ami           = "ami-..."
  instance_type = var.instance_type
  tags          = var.tags
}
```

```bash
# 값 override
terraform apply -var="instance_type=t3.large"
# 또는 terraform.tfvars
echo 'instance_type = "t3.large"' > terraform.tfvars
```

### Output

```hcl
# outputs.tf
output "instance_ip" {
  value = aws_instance.web.public_ip
}

output "instance_id" {
  value = aws_instance.web.id
}
```

```bash
terraform output
# instance_ip = "1.2.3.4"
# instance_id = "i-..."

terraform output instance_ip
# 1.2.3.4
```

다른 모듈·CI에서 활용.

### Data — 기존 자원 조회

```hcl
data "aws_ami" "ubuntu" {
  most_recent = true
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-22.04-amd64-server-*"]
  }
  owners = ["099720109477"]
}

resource "aws_instance" "web" {
  ami = data.aws_ami.ubuntu.id
  # ...
}
```

---

## State — Terraform의 기억

```bash
terraform apply
# Apply complete!
ls
# terraform.tfstate              ← state 파일
```

`terraform.tfstate`에 **현재 인프라 상태**를 저장. plan/apply는 state ↔ 코드를 비교.

### 로컬 state 문제

- **분실 위험** (rm 또는 .gitignore 누락)
- **혼자만 사용** (팀에서 동시 사용 못 함)
- **민감 정보 노출** (state엔 secret 평문 포함 가능)

### Remote backend

```hcl
terraform {
  backend "s3" {
    bucket         = "my-tfstate-bucket"
    key            = "prod/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "terraform-lock"             # state lock
    encrypt        = true
  }
}
```

```bash
terraform init       # backend 설정 적용
# state가 S3에 저장됨
```

DynamoDB가 lock 제공 → **동시 apply 방지**.

### 옵션

- **AWS S3 + DynamoDB**
- **GCS (Google Cloud Storage)**
- **Azure Storage**
- **Terraform Cloud / HCP Terraform** (HashiCorp 매니지드)
- **GitLab managed state**

---

## Plan vs Apply

```bash
terraform plan
# Terraform will perform the following actions:
#   # aws_instance.web will be created
#   + resource "aws_instance" "web" {
#       + ami           = "ami-..."
#       + instance_type = "t3.micro"
#       ...
#     }
# Plan: 1 to add, 0 to change, 0 to destroy.
```

plan은 **변경 미리보기**. 운영에선 **plan을 PR에 첨부**·검토 후 apply.

```bash
# plan 결과를 파일로 저장
terraform plan -out=tfplan
terraform apply tfplan       # 정확히 그 plan만 적용
```

이게 운영 표준.

---

## Module — 재사용

```
project/
├── main.tf
└── modules/
    └── network/
        ├── main.tf
        ├── variables.tf
        └── outputs.tf
```

`modules/network/main.tf`:
```hcl
resource "aws_vpc" "this" {
  cidr_block = var.cidr
  tags = merge(var.tags, { Name = var.name })
}

resource "aws_subnet" "this" {
  count             = length(var.azs)
  vpc_id            = aws_vpc.this.id
  cidr_block        = cidrsubnet(var.cidr, 8, count.index)
  availability_zone = var.azs[count.index]
  # ...
}
```

`modules/network/variables.tf`:
```hcl
variable "cidr" {
  type = string
}
variable "azs" {
  type = list(string)
}
variable "name" {
  type    = string
  default = "vpc"
}
variable "tags" {
  type    = map(string)
  default = {}
}
```

`modules/network/outputs.tf`:
```hcl
output "vpc_id" {
  value = aws_vpc.this.id
}
output "subnet_ids" {
  value = aws_subnet.this[*].id
}
```

`main.tf`에서 사용:
```hcl
module "network" {
  source = "./modules/network"
  
  cidr = "10.0.0.0/16"
  azs  = ["ap-northeast-2a", "ap-northeast-2c"]
  name = "prod-vpc"
}

module "another_env" {
  source = "./modules/network"
  
  cidr = "10.1.0.0/16"
  azs  = ["ap-northeast-2a"]
  name = "dev-vpc"
}
```

```bash
terraform init       # module도 다운로드
```

### 공개 module (Terraform Registry)

```hcl
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.13.0"

  name = "my-vpc"
  cidr = "10.0.0.0/16"
  azs             = ["ap-northeast-2a", "ap-northeast-2c"]
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24"]
  # ...
}
```

널리 검증된 module로 인프라 빠르게.

---

## terraform import — 기존 자원 가져오기

수동으로 만든 자원을 Terraform 관리로:

```bash
# 1. 코드에 resource 정의 (config만, apply X)
cat >> main.tf <<EOF
resource "aws_instance" "existing" {
  # 이 부분은 비워두고 import 후 채움
}
EOF

# 2. import (state에 추가)
terraform import aws_instance.existing i-0123456789abcdef

# 3. terraform plan으로 diff 확인
terraform plan

# 4. 코드를 실제 자원과 일치시킴 (반복)

# 5. plan이 "no changes"면 완료
```

> Terraform 1.5+에선 `import` 블록을 코드로:
> ```hcl
> import {
>   to = aws_instance.existing
>   id = "i-0123..."
> }
> ```

---

## github provider — k8s 외 활용

Terraform은 AWS·k8s만이 아님. GitHub repo·CI/CD도 코드로:

```hcl
provider "github" {
  token = var.github_token
  owner = "my-org"
}

resource "github_repository" "app" {
  name        = "my-app"
  description = "My application"
  visibility  = "private"
  
  has_issues       = true
  has_projects     = false
  has_wiki         = false
  vulnerability_alerts = true
  delete_branch_on_merge = true
}

resource "github_branch_protection" "main" {
  repository_id  = github_repository.app.node_id
  pattern        = "main"
  required_status_checks {
    strict   = true
    contexts = ["CI / build", "CI / test"]
  }
  required_pull_request_reviews {
    required_approving_review_count = 1
  }
  required_linear_history = true
  allows_force_pushes     = false
}

resource "github_actions_secret" "docker_password" {
  repository      = github_repository.app.name
  secret_name     = "DOCKER_PASSWORD"
  plaintext_value = var.docker_password
}
```

조직 표준을 코드로 — **모든 repo가 동일한 보호 정책**.

---

## 운영 사례

### 사례 1 — terraform state 손실

backend 미설정·local state 삭제. 인프라는 살아있으나 Terraform이 모름.

조치: `terraform import`로 자원 다시 가져오기 (수십~수백 자원이면 끔찍).

**예방**: 처음부터 remote backend 사용.

### 사례 2 — 동시 apply로 state 손상

두 사람이 동시에 `terraform apply` → state 동시 쓰기 → 손상.

**예방**: DynamoDB lock 또는 Terraform Cloud의 자동 locking.

### 사례 3 — drift 발견

```bash
terraform plan
# # aws_instance.web has been changed externally
#   ~ instance_type = "t3.large" -> "t3.micro"
```

콘솔에서 수동 변경됨. plan이 "되돌리기" 보임.

조치:
- 변경이 의도였으면 코드 업데이트
- 아니면 apply로 원복
- 미래 방지: IAM으로 콘솔 변경 차단

---

## 표준 디렉토리 구조

```
infra/
├── environments/
│   ├── dev/
│   │   ├── main.tf
│   │   ├── backend.tf
│   │   └── terraform.tfvars
│   ├── staging/
│   └── prod/
├── modules/
│   ├── network/
│   ├── eks/
│   ├── rds/
│   └── ...
└── README.md
```

### 환경별 분리

각 환경이 독립된 state. 한 환경 망가져도 다른 환경 영향 X.

```bash
cd environments/dev
terraform init
terraform plan
terraform apply
```

---

## 실습 (Hands-on)

### 1단계 — local-only Terraform 

진짜 클라우드 자원 만들지 않고 학습용:

```hcl
# main.tf
terraform {
  required_providers {
    local = {
      source = "hashicorp/local"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

resource "random_pet" "name" {
  length    = 2
  separator = "-"
}

resource "local_file" "greeting" {
  filename = "${path.module}/hello.txt"
  content  = "Hello, ${random_pet.name.id}!"
}

output "filename" {
  value = local_file.greeting.filename
}

output "name" {
  value = random_pet.name.id
}
```

```bash
terraform init
terraform plan
terraform apply
cat hello.txt
# Hello, vital-mongoose!

terraform output
# filename = "./hello.txt"
# name = "vital-mongoose"
```

`terraform.tfstate` 생성됨. 다시 apply → 같은 결과 (idempotent).

### 2단계 — Resource 변경

random_pet의 length를 3으로:
```hcl
length = 3
```

```bash
terraform plan
# # random_pet.name will be replaced
#  -/+ resource "random_pet" "name" {
```

→ 재생성 트리거 (immutable attribute).

```bash
terraform apply
cat hello.txt
# Hello, vital-amazing-mongoose!
```

### 3단계 — Module 만들기

`modules/greeting/main.tf`:
```hcl
terraform {
  required_providers {
    local = { source = "hashicorp/local" }
    random = { source = "hashicorp/random" }
  }
}

variable "filename" { type = string }
variable "prefix"   { type = string, default = "Hello" }

resource "random_pet" "name" { length = 2 }

resource "local_file" "out" {
  filename = var.filename
  content  = "${var.prefix}, ${random_pet.name.id}!"
}

output "name" { value = random_pet.name.id }
```

루트 `main.tf`:
```hcl
module "hello1" {
  source = "./modules/greeting"
  filename = "greet1.txt"
}

module "hello2" {
  source   = "./modules/greeting"
  filename = "greet2.txt"
  prefix   = "Howdy"
}

output "names" {
  value = [module.hello1.name, module.hello2.name]
}
```

```bash
terraform init
terraform apply
cat greet1.txt greet2.txt
```

### 4단계 — github provider (옵션)

PAT 발급 후:

```hcl
provider "github" {
  token = var.github_token
  owner = "my-username"
}

resource "github_repository" "test" {
  name        = "tf-test-repo"
  description = "Created by Terraform"
  visibility  = "private"
  auto_init   = true
}
```

```bash
terraform init
terraform apply
# GitHub에 repo 생성됨

terraform destroy
# 삭제
```

---

## 더 읽어볼 자료

- 📘 『Terraform: Up & Running』 3rd (Yevgeniy Brikman, O'Reilly)
- 🔗 [Terraform Docs](https://developer.hashicorp.com/terraform)
- 🔗 [AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- 🔗 [Terraform Registry](https://registry.terraform.io/) — 공개 module
- 🔗 [Atlantis](https://www.runatlantis.io/) — PR 기반 terraform workflow
- 🔗 [tflint](https://github.com/terraform-linters/tflint)
- 🔗 [tfsec](https://github.com/aquasecurity/tfsec) — 보안 스캔
