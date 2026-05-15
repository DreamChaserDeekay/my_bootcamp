# Day 2 — Linux 파일시스템 · 권한 · 소유권

## 한 줄 요약

Linux는 **모든 것을 파일로 본다** (소켓·디바이스·파이프 포함). 그 모든 파일은 **소유자·그룹·기타** 세 주체에 대해 **읽기(r)·쓰기(w)·실행(x)** 권한을 조합한 9비트로 통제되며, 이 모델을 정확히 이해해야 운영 실수가 줄어든다.

## 학습 목표

- [ ] FHS(Filesystem Hierarchy Standard)의 주요 디렉터리 의미를 안다 (`/etc`, `/var`, `/usr`, `/proc`, `/sys`, `/dev`)
- [ ] `ls -l`의 모든 열을 해석할 수 있다
- [ ] `chmod`의 심볼릭/숫자 표기, `chown`, `chgrp`를 자유롭게 쓴다
- [ ] **suid · sgid · sticky bit** 의 의미와 위험을 안다
- [ ] `umask`가 새 파일의 권한에 어떻게 영향을 주는지 설명한다
- [ ] **하드링크 vs 심볼릭 링크** 차이를 inode 관점에서 안다
- [ ] Windows의 ACL과 비교한다 (`Get-Acl`, `icacls`)

---

## 1. Linux의 디렉터리 구조 (FHS)

| 경로 | 의미 | 예 |
|---|---|---|
| `/` | 루트 | 모든 경로의 시작 |
| `/bin`, `/sbin` | 필수 실행 파일 | `ls`, `cp`, `sh` (요즘은 `/usr/bin`으로 통합) |
| `/etc` | **설정 파일** | `/etc/passwd`, `/etc/nginx/nginx.conf` |
| `/home/<user>` | 사용자 홈 | `~`로도 표기 |
| `/var` | 가변 데이터 | `/var/log/`, `/var/lib/mysql/` |
| `/usr` | 사용자 공유 자원 | `/usr/local/bin`은 직접 설치한 바이너리 |
| `/opt` | 옵션 소프트웨어 | 보통 벤더 패키지 (Oracle, IBM 등) |
| `/tmp` | 임시 (재부팅 시 비워짐) | `mktemp -d`로 안전한 디렉터리 |
| `/proc` | **커널이 만들어 보여주는 가상 FS** (프로세스, 시스템 상태) | `/proc/cpuinfo`, `/proc/<pid>/` |
| `/sys` | sysfs — 디바이스·드라이버 인터페이스 | `/sys/class/net/eth0/` |
| `/dev` | 디바이스 파일 | `/dev/sda`, `/dev/null`, `/dev/random` |
| `/boot` | 부트로더, 커널 이미지 | `vmlinuz`, `initrd.img` |
| `/root` | root 사용자의 홈 (주의: `/`와 다름) | |

### Java/Spring 운영 관점

- 앱 로그 → `/var/log/myapp/` (rotate는 `logrotate`)
- 앱 설치 → `/opt/myapp/` 또는 `/usr/local/myapp/`
- 환경설정 → `/etc/myapp/application.yml`
- 임시 업로드 → `/tmp/` 또는 앱 전용 `/var/cache/myapp/`

---

## 2. `ls -l` 완전 해부

```
-rw-r--r--  1 alice  staff   1024 Mar  5 10:23 file.txt
└┬┘└┬┘└┬┘└┬┘  │   │      │      │       │         │
 │  │  │  │   │   │      │      │       │         └ 파일명
 │  │  │  │   │   │      │      │       └ 수정 시각
 │  │  │  │   │   │      │      └ 크기 (bytes)
 │  │  │  │   │   │      └ 그룹
 │  │  │  │   │   └ 소유자
 │  │  │  │   └ 하드링크 수
 │  │  │  └ 기타(other) 권한
 │  │  └ 그룹(group) 권한
 │  └ 소유자(user) 권한
 └ 파일 타입 (- 일반, d 디렉터리, l 심볼릭, c 캐릭터, b 블록, s 소켓, p 파이프)
```

### 권한 비트 — 숫자 vs 심볼

| 비트 | 심볼 | 8진수 | 디렉터리에서의 의미 |
|---|---|---|---|
| Read | r | 4 | 파일 목록 보기 |
| Write | w | 2 | 파일 생성/삭제 |
| Execute | x | 1 | 진입(`cd`) 가능 |

조합:

| 8진수 | 심볼 | 의미 |
|---|---|---|
| 7 | rwx | 모두 가능 |
| 6 | rw- | 읽기/쓰기 |
| 5 | r-x | 읽기/실행 |
| 4 | r-- | 읽기만 |
| 0 | --- | 없음 |

#### 자주 쓰는 조합

| 권한 | 용도 |
|---|---|
| 644 (`rw-r--r--`) | 일반 텍스트 파일 |
| 755 (`rwxr-xr-x`) | 실행 파일, 공개 디렉터리 |
| 700 (`rwx------`) | 본인만 (`~/.ssh/`) |
| 600 (`rw-------`) | 본인만 읽기/쓰기 (`~/.ssh/id_rsa`) |
| 640 (`rw-r-----`) | 그룹만 읽기 (`/etc/shadow`는 사실 640) |
| 777 (`rwxrwxrwx`) | **거의 항상 잘못된 선택** |

### chmod / chown / chgrp

```bash
# 숫자 모드
chmod 644 file.txt
chmod 755 script.sh

# 심볼릭 모드 (u=user, g=group, o=other, a=all)
chmod u+x script.sh        # 소유자에게 실행권 추가
chmod g-w,o-rwx file.txt   # 그룹에서 쓰기, 기타에서 모두 제거
chmod a=r file.txt         # 모두 읽기만

# 재귀
chmod -R 755 /var/www/html

# 소유자/그룹 변경
chown alice:developers file.txt
chown -R nginx:nginx /var/www/html
chgrp developers project/
```

### ❌ 위험 / ✅ 안전 패턴

```bash
# ❌ 모든 권한 문제를 777로 해결 — 보안 사고의 시작
chmod -R 777 /var/www/html

# ✅ 정확한 권한
sudo chown -R www-data:www-data /var/www/html
sudo find /var/www/html -type d -exec chmod 755 {} \;
sudo find /var/www/html -type f -exec chmod 644 {} \;
```

```bash
# ❌ 운영서버에서 sudo 없이 권한 변경하려고 chmod 4755 (suid) 추가
chmod 4755 /usr/local/bin/backup.sh

# ✅ sudo + sudoers의 NOPASSWD 화이트리스트
# /etc/sudoers.d/backup
# alice ALL=(root) NOPASSWD: /usr/local/bin/backup.sh
sudo /usr/local/bin/backup.sh
```

---

## 3. 특수 권한 — suid · sgid · sticky bit

| 비트 | 8진수 | 표시 | 의미 |
|---|---|---|---|
| **suid** | 4xxx | `-rwsr-xr-x` | 실행 시 **소유자 권한으로 실행**됨 |
| **sgid** | 2xxx | `-rwxr-sr-x` | 실행 시 그룹 권한 / 디렉터리에서는 새 파일이 디렉터리 그룹 상속 |
| **sticky** | 1xxx | `drwxrwxrwt` | 디렉터리 내 파일은 **소유자만 삭제 가능** (`/tmp`) |

### suid의 예와 위험

```bash
$ ls -l /usr/bin/passwd
-rwsr-xr-x 1 root root 68208 Mar  5  2024 /usr/bin/passwd
```

- `passwd`는 누구나 실행하지만, 내부에서 `/etc/shadow`를 수정해야 하므로 **root 권한으로 실행되도록 suid가 켜져 있다**.
- **위험**: 직접 작성한 셸 스크립트에 suid 주면 안 됨. 셸 인터프리터의 변수 인젝션·환경변수 공격으로 권한 상승 가능. Linux는 셸 스크립트의 suid를 의도적으로 무시한다.

```bash
# 시스템 전체에서 suid 파일 찾기 (보안 점검)
sudo find / -perm -4000 -type f 2>/dev/null
```

### sticky bit (`/tmp`)

```bash
$ ls -ld /tmp
drwxrwxrwt 22 root root 4096 Mar  5 10:00 /tmp
```

- `t`: 모두 쓰기 가능하지만, 자기가 만든 파일만 자기가 삭제 가능. 멀티유저 임시 디렉터리 필수.

---

## 4. umask — 기본 권한 마스크

새 파일을 만들 때 적용되는 **차감 마스크**.

```bash
$ umask
0022
$ touch newfile.txt
$ ls -l newfile.txt
-rw-r--r-- 1 alice ... newfile.txt
# 기본 666 - 022 = 644
```

| umask | 새 파일 권한 | 새 디렉터리 권한 |
|---|---|---|
| 022 (대부분 기본) | 644 (rw-r--r--) | 755 (rwxr-xr-x) |
| 027 (보안 강화) | 640 (rw-r-----) | 750 (rwxr-x---) |
| 077 (혼자만) | 600 (rw-------) | 700 (rwx------) |

`/etc/profile` 또는 `~/.bashrc`에 `umask 027` 설정으로 운영서버 보안 강화.

---

## 5. 하드링크 vs 심볼릭 링크

```
inode 12345 ──┬── /home/alice/report.txt  (하드링크)
              └── /backup/report-2024.txt  (하드링크)

/tmp/latest-report → /home/alice/report.txt  (심볼릭링크, 경로만 저장)
```

| 구분 | 하드링크 (hard link) | 심볼릭링크 (symlink, soft link) |
|---|---|---|
| 동작 | 같은 inode를 가리키는 이름 추가 | 별도 inode + 대상 경로 문자열 |
| 원본 삭제 | 데이터 살아있음 (참조 카운트 감소) | 깨진 링크(dangling) |
| 디렉터리 가능? | ❌ (loop 위험) | ⭕ |
| 다른 파일시스템 | ❌ | ⭕ |
| 만드는 법 | `ln target name` | `ln -s target name` |

```bash
# 심볼릭링크는 거의 모든 경우 이걸 씀
ln -s /opt/myapp-1.2.3 /opt/myapp-current

# 하드링크 vs 심볼릭 확인
ls -li file1 file2
# inode가 같으면 하드링크
```

> ⚠ **심볼릭링크 공격**: 임시 디렉터리에 `/etc/shadow`를 가리키는 심볼릭링크를 두고, 프로그램이 해당 경로에 root로 쓰면 `/etc/shadow`가 덮어써짐. `/tmp` 사용 시 `mktemp`로 안전한 디렉터리 만들고, `O_NOFOLLOW` 플래그 사용.

---

## 6. 자주 쓰는 파일시스템 명령어

```bash
# 디스크 사용량
df -h                          # 마운트별 용량
du -sh /var/log/*              # 디렉터리별 크기
du -h --max-depth=1 /var | sort -h

# 파일 찾기
find /etc -name "*.conf" -type f
find /home -mtime -7           # 7일 이내 수정
find / -size +100M 2>/dev/null # 100MB 초과
find . -name "*.log" -delete   # 위험! -delete는 신중히

# 파일 정보
stat /etc/passwd
file /usr/bin/java             # MIME 타입 추정

# 마운트
mount                          # 현재 마운트 목록
df -T                          # 파일시스템 타입 포함
lsblk                          # 블록 디바이스 트리
findmnt /                      # 마운트 정보

# inode 사용량 (파일이 너무 많을 때)
df -i
```

---

## 7. Windows의 권한 모델 (ACL) 비교

Linux의 9비트와 달리 Windows NTFS는 **ACL (Access Control List)**: 사용자/그룹별로 14개 권한을 세밀하게 부여.

### PowerShell

```powershell
# ACL 조회
Get-Acl C:\Windows\notepad.exe | Format-List

# ACL 변경 (직접)
$acl = Get-Acl "C:\data\file.txt"
$rule = New-Object System.Security.AccessControl.FileSystemAccessRule(
    "DOMAIN\alice", "Read", "Allow")
$acl.SetAccessRule($rule)
Set-Acl "C:\data\file.txt" $acl

# 소유자 변경
$acl = Get-Acl "C:\data\file.txt"
$acl.SetOwner([System.Security.Principal.NTAccount]"DOMAIN\alice")
Set-Acl "C:\data\file.txt" $acl
```

### cmd/icacls (전통)

```cmd
icacls C:\data\file.txt
icacls C:\data\file.txt /grant alice:R
icacls C:\data\file.txt /remove alice
```

### Linux ↔ Windows 매핑

| Linux | Windows |
|---|---|
| `chmod` | `icacls` 또는 `Set-Acl` |
| `chown` | `takeown` 또는 `Set-Acl` (Owner) |
| `umask` | UAC + 부모 디렉터리 ACL 상속 |
| `root` | `Administrator` / `SYSTEM` |
| suid | Windows에는 직접 대응 없음 (Run As, UAC 승격) |

---

## 8. 실제 사례 / CVE

### Dirty Pipe (CVE-2022-0847)

- Linux 커널 5.8+에서 파이프 버퍼의 권한 검사 누락으로 **읽기 전용 파일을 덮어쓸 수 있는** 취약점.
- 교훈: 권한 모델은 커널이 강제할 때만 의미가 있다. 패치 관리의 중요성.

### Spring Boot 운영 사례

- `application.yml`에 DB 패스워드를 평문으로 두고 권한이 `644` → 같은 서버의 일반 사용자가 읽을 수 있음.
- **해결**: `chmod 640 application.yml`, 그룹은 앱 그룹, 또는 환경변수·Vault·AWS Secrets Manager 사용.

```bash
# 권장 패턴
sudo chown myapp:myapp /opt/myapp/application.yml
sudo chmod 640 /opt/myapp/application.yml
```

---

## 9. 실습 (Hands-on)

### Step 1: 권한 실험실

```bash
mkdir ~/perm-lab && cd ~/perm-lab
echo "secret" > secret.txt
chmod 600 secret.txt
ls -l secret.txt

# 다른 사용자로 읽기 시도 (sudo로 시뮬레이션)
sudo -u nobody cat secret.txt    # Permission denied 확인
```

### Step 2: suid 위험 체험

```bash
# 시스템의 suid 파일 목록 보기
sudo find /usr -perm -4000 -type f 2>/dev/null | head

# 각각이 왜 suid인지 추정해보기 (passwd, sudo, ping 등)
```

### Step 3: umask 효과 확인

```bash
umask 077
touch private.txt
ls -l private.txt          # 600

umask 022
touch normal.txt
ls -l normal.txt           # 644
```

### Step 4: 심볼릭링크와 함정

```bash
ln -s /etc/passwd ~/perm-lab/link-to-passwd
cat ~/perm-lab/link-to-passwd     # 원본 읽힘
rm ~/perm-lab/link-to-passwd      # 링크만 제거 (원본 안전)

# 깨진 링크 만들기
ln -s /nonexistent broken
ls -l broken               # 빨갛게 표시
```

### Step 5: PowerShell ACL 실습

```powershell
$tmp = New-Item -Path "$env:TEMP\acl-lab" -ItemType Directory -Force
"hello" | Out-File "$tmp\test.txt"
Get-Acl "$tmp\test.txt" | Format-List

# 권한 제거
$acl = Get-Acl "$tmp\test.txt"
$acl.SetAccessRuleProtection($true, $false)   # 상속 끄기
Set-Acl "$tmp\test.txt" $acl
Get-Acl "$tmp\test.txt" | Format-List
```

---

## 더 읽어볼 자료

- 📘 『Linux Programming Interface』 Ch.14~17 (Files, Files I/O Buffering, File Systems, Access Control)
- 🔗 FHS 표준: <https://refspecs.linuxfoundation.org/FHS_3.0/fhs/index.html>
- 🔗 `man 7 path_resolution` — 경로 해석 규칙
- 🔗 `man 1 chmod`, `man 2 stat`
- 🔗 Microsoft Docs — Access Control Lists: <https://learn.microsoft.com/windows/win32/secauthz/access-control-lists>

---

## 자가 점검

- [ ] `-rw-r-----`을 8진수로 표기할 수 있는가? (640)
- [ ] suid가 켜진 파일이 왜 위험할 수 있는지 한 문장으로 설명할 수 있는가?
- [ ] umask 022에서 새 파일 권한이 왜 644가 되는지 계산할 수 있는가?
- [ ] 하드링크와 심볼릭링크의 차이를 inode 관점에서 설명할 수 있는가?
- [ ] PowerShell `Get-Acl`로 파일 권한을 조회했는가?

다음: [`03_bash_essentials.md`](03_bash_essentials.md)
