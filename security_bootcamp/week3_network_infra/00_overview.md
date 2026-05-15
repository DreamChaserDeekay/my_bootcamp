# Week 3 — 네트워크·인프라·클라우드·컨테이너 보안

## 한 줄 요약
**앱 위·아래 계층**을 본다. 패킷이 어떻게 흐르고, TLS가 무엇을 지키며, 컨테이너·클라우드는 어디가 약한가.

## 학습 목표
- [ ] OSI/TCP-IP 모델의 보안 의미 이해
- [ ] TLS·PKI 동작·인증서 검증·중간자 공격 방어
- [ ] Wireshark로 트래픽 분석, Nmap·Masscan으로 자산 점검
- [ ] 방화벽·세그멘테이션·Zero Trust 네트워크 설계 원칙
- [ ] AWS(또는 GCP) IAM 최소 권한·VPC 격리
- [ ] Docker·Kubernetes 보안 핵심
- [ ] 시크릿 관리·SSH·SSO 보안

## 일정

| 일 | 내용 | 파일 |
|----|------|------|
| Day 1 | 네트워크 기초·OSI·TCP/UDP·DNS·Wireshark | [01_network_fundamentals.md](01_network_fundamentals.md) |
| Day 2 | TLS·PKI·인증서 | [02_tls_pki.md](02_tls_pki.md) |
| Day 3 | MITM·ARP/DNS poisoning·VPN·방화벽·세그멘테이션 | [03_mitm_firewall.md](03_mitm_firewall.md) |
| Day 4 | 클라우드 보안 (AWS 중심)·IAM·VPC·S3 | [04_cloud_security.md](04_cloud_security.md) |
| Day 5 | 컨테이너·Kubernetes·Linux 하드닝 | [05_container_k8s.md](05_container_k8s.md) |

## 실습 산출물
- 본인 시스템 외부 노출 자산 종합 보고서
- TLS Labs A+ 목표 설정 가이드
- Docker 이미지 1개 하드닝
- IAM 정책 최소 권한 1개 작성

## 체크리스트
[checklist.md](checklist.md)
