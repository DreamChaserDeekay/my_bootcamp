# Week 3 자가 점검 체크리스트

## 네트워크 기초
- [ ] OSI 7계층 vs 공격·통제 매핑
- [ ] TCP/UDP 차이와 보안 함의
- [ ] DNS 보안 레코드(SPF/DMARC/CAA) 점검
- [ ] Wireshark로 HTTP/HTTPS 트래픽 분석
- [ ] Nmap 종합 옵션 사용

## TLS·인증서
- [ ] SSL Labs A+ (또는 A) 받는 설정 가능
- [ ] testssl.sh 사용
- [ ] 자동 갱신 (Let's Encrypt) 셋업
- [ ] crt.sh 모니터링 등록

## 방화벽·세그멘테이션
- [ ] 본인 외부 노출 자산 인벤토리
- [ ] DB·Redis·Elasticsearch 외부 노출 확인 → 차단
- [ ] WAF 적용 여부 점검
- [ ] SSH 키 인증·root 차단·fail2ban

## 클라우드 (AWS 기준)
- [ ] IAM Access Analyzer 활성
- [ ] CloudTrail 모든 리전
- [ ] GuardDuty + Security Hub
- [ ] S3 Block Public Access 계정 ON
- [ ] IMDSv2 강제
- [ ] Prowler 또는 ScoutSuite 실행

## 컨테이너·K8s
- [ ] Trivy로 운영 이미지 스캔
- [ ] 비-root 사용자
- [ ] 읽기 전용 파일시스템 가능 여부
- [ ] Secret 환경변수 평문 X
- [ ] (K8s 사용 시) PSS Restricted, NetworkPolicy

## 산출물
- [ ] 외부 노출 자산 인벤토리·위험도
- [ ] TLS A+ 룰북
- [ ] Docker 이미지 1개 하드닝 PR
- [ ] IAM 최소 권한 정책 1개

## 다음 주 준비
- [ ] WebGoat 컨테이너 학습
- [ ] CI/CD 파이프라인 1개 파악 (회사 또는 사이드)
