# GitHub repo·branch protection을 Terraform으로 관리하는 예제
# 실제 적용 전 GitHub PAT 필요 (Settings → Developer settings → Personal access tokens)

terraform {
  required_version = ">= 1.5"
  required_providers {
    github = {
      source  = "integrations/github"
      version = "~> 6.0"
    }
  }

  # 운영 환경 권장 (학습용 주석 처리)
  # backend "s3" {
  #   bucket         = "my-tfstate"
  #   key            = "devops-lab/terraform.tfstate"
  #   region         = "ap-northeast-2"
  #   dynamodb_table = "terraform-lock"
  #   encrypt        = true
  # }
}

variable "github_token" {
  type        = string
  sensitive   = true
  description = "GitHub PAT with repo·admin permission"
}

variable "github_owner" {
  type        = string
  description = "GitHub organization or username"
}

variable "repo_name" {
  type    = string
  default = "devops-lab"
}

provider "github" {
  token = var.github_token
  owner = var.github_owner
}

resource "github_repository" "app" {
  name               = var.repo_name
  description        = "DevOps bootcamp practice app"
  visibility         = "private"
  has_issues         = true
  has_projects       = false
  has_wiki           = false
  vulnerability_alerts   = true
  delete_branch_on_merge = true

  # 자동 init (README + .gitignore)
  auto_init = true
  gitignore_template = "Java"
}

resource "github_branch_protection" "main" {
  repository_id   = github_repository.app.node_id
  pattern         = "main"

  required_status_checks {
    strict   = true
    contexts = ["test"]                     # CI workflow의 test job
  }

  required_pull_request_reviews {
    required_approving_review_count = 1
    dismiss_stale_reviews            = true
    require_code_owner_reviews       = false
  }

  enforce_admins          = false
  required_linear_history = true
  allows_force_pushes     = false
  allows_deletions        = false
}

# Secret (CI에서 사용)
# 실제 비밀번호는 별도 secret manager에서 가져옴 (이건 학습용)
# resource "github_actions_secret" "docker_password" {
#   repository      = github_repository.app.name
#   secret_name     = "DOCKER_PASSWORD"
#   plaintext_value = var.docker_password
# }

# Environment (배포 승인용)
resource "github_repository_environment" "production" {
  repository  = github_repository.app.name
  environment = "production"

  reviewers {
    users = [data.github_user.me.id]
  }

  deployment_branch_policy {
    protected_branches     = false
    custom_branch_policies = true
  }
}

resource "github_repository_environment_deployment_policy" "prod_tag" {
  repository     = github_repository.app.name
  environment    = github_repository_environment.production.environment
  branch_pattern = "v*"                     # tag만 prod에 배포 가능
}

data "github_user" "me" {
  username = var.github_owner
}

output "repo_url" {
  value = github_repository.app.html_url
}

output "ssh_clone_url" {
  value = github_repository.app.ssh_clone_url
}
