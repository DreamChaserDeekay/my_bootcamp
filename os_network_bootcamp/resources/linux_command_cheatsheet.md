# Linux 명령어 치트시트

운영자가 매일 쓰는 Linux 명령어 모음. 부트캠프 보조 자료.

## 1. 디렉터리·파일

```bash
pwd                              # 현재 경로
cd /path                         # 이동
cd -                             # 직전 경로
cd ~                             # 홈

ls -la                           # 상세 + 숨김
ls -lh                           # 사람이 읽기 쉬운 크기
ls -lt                           # 최근 수정 순
ls -lS                           # 크기 순
ls -laR                          # 재귀

tree -L 2                        # 트리 (depth 2)
mkdir -p a/b/c                   # 중간 디렉터리까지
rmdir empty_dir                  # 빈 디렉터리만
rm -rf garbage/                  # 재귀 강제 (위험!)

cp -r src/ dst/                  # 재귀 복사
cp -p file backup                # 권한·타임 보존
cp -a src/ dst/                  # archive (보존 모두)

mv old new                       # 이동/이름변경
touch file                       # 빈 파일 / mtime 갱신
ln -s target link                # 심볼릭링크
ln -sfn target link              # 기존 링크 덮어쓰기 (atomic)

# 안전 임시 디렉터리
TMP=$(mktemp -d)
trap "rm -rf $TMP" EXIT
```

## 2. 권한·소유권

```bash
chmod 644 file                   # rw-r--r--
chmod 755 dir                    # rwxr-xr-x
chmod u+x script                 # 사용자 실행 추가
chmod -R 755 /var/www            # 재귀

# 특수 권한
chmod 4755 binary                # suid
chmod 1755 dir                   # sticky
chmod 2755 dir                   # sgid

chown user:group file
chown -R nginx:nginx /var/www
chgrp developers project/

umask 022                        # 새 파일 644, 새 디렉터리 755
umask 077                        # 본인만

getfacl file                     # POSIX ACL
setfacl -m u:alice:rw file
```

## 3. 텍스트·검색

```bash
cat file                         # 전체
less file                        # 페이저 (q, /, n, ?, b)
head -n 20 file
tail -n 50 file
tail -f /var/log/syslog          # 실시간 (rotation: -F)
tail -n 100 -f file

# grep
grep "ERROR" app.log
grep -i "exception" app.log      # 대소문자 무시
grep -in "todo" *.java           # 줄번호
grep -rn "ERROR" /var/log
grep -v "DEBUG"                  # 제외
grep -E "error|warn"             # 확장
grep -P '(?<=user=)\w+'          # PCRE (lookbehind)
grep -A 3 -B 1 "Exception"       # 컨텍스트
grep -c "ERROR"                  # 카운트
grep -l "ERROR" *.log            # 매치된 파일명만
grep -o '\b[0-9.]\+\b'           # 매치 부분만

# ripgrep (rg) — 권장
rg "ERROR"
rg -t java "TODO"
rg --hidden

# 텍스트 변형
wc -l file                       # 줄
wc -w file                       # 단어
sort file
sort -n                          # 숫자
sort -r                          # 내림차순
sort -u                          # 중복 제거
sort -t, -k2                     # CSV의 2열로 정렬
uniq                             # 인접 중복
uniq -c                          # 카운트
uniq -d                          # 중복 줄만

cut -d, -f1,3 file.csv
cut -c5-10 file
tr 'a-z' 'A-Z' < file
tr -d '\r' < windows.txt
tr -s ' '                        # 공백 압축

awk '{print $1}' file
awk -F, '{print $2}' csv
awk 'NR>1 && $3>100' file
awk -F, '{sum+=$4} END {print sum}' csv

sed 's/foo/bar/g' file
sed -i.bak 's/old/new/g' file
sed -E '/^#/d; /^$/d' config

# 파일 차이
diff -u old new
diff -r dir1 dir2
comm -12 sorted_a sorted_b       # 공통 줄
paste -d, a.txt b.txt            # 가로로 붙이기
```

## 4. 프로세스·시그널

```bash
ps aux                           # BSD 스타일
ps -ef --forest                  # 트리
ps -p 1234 -o pid,ppid,user,cmd
pstree -p
pgrep -af java
pidof nginx

top                              # 실시간
htop                             # 보기 좋게
btop                             # 더 예쁨

# 시그널
kill 1234                        # SIGTERM (default)
kill -TERM 1234
kill -9 1234                     # SIGKILL (강제)
kill -HUP 1234                   # 보통 reload
pkill -f "java.*MyApp"
killall nginx

# 우선순위
nice -n 10 cmd
renice -n 5 -p 1234

# 잡 컨트롤
cmd &                            # 백그라운드
jobs
fg %1
bg %1
nohup cmd > out.log 2>&1 &
disown -h %1

# tmux (또는 screen)
tmux new -s work
tmux ls
tmux attach -t work
# Ctrl+B D (detach), Ctrl+B "%" (split), Ctrl+B C (new window)
```

## 5. systemd

```bash
sudo systemctl start nginx
sudo systemctl stop nginx
sudo systemctl restart nginx
sudo systemctl reload nginx                # SIGHUP
sudo systemctl enable nginx                # 부팅 자동
sudo systemctl enable --now nginx          # enable + start
sudo systemctl disable nginx
sudo systemctl status nginx
sudo systemctl daemon-reload               # 유닛 파일 변경 후

systemctl list-units --type=service
systemctl list-units --state=failed
systemctl is-active nginx
systemctl is-enabled nginx

# 로그
journalctl -u nginx -f                     # 실시간
journalctl -u nginx --since "1 hour ago"
journalctl -u nginx --since today -p err
journalctl -b                              # 현재 부팅
journalctl -b -1                           # 이전 부팅
journalctl -k                              # 커널
journalctl --vacuum-time=7d
journalctl --disk-usage

# 타이머
systemctl list-timers
```

## 6. 네트워크

```bash
# 정보
ip a                             # 인터페이스 + IP
ip route                         # 라우팅
ip neigh                         # ARP (= arp -a)
ip -s link show eth0             # 통계
ethtool eth0                     # NIC

hostname -I                      # IP만
nmcli con show                   # NetworkManager

# 진단
ping -c 4 host
ping -i 0.2 host                 # 0.2초 간격
traceroute host
mtr host

dig host
dig host A; dig host AAAA; dig host MX
dig +short host
dig +trace host
dig @1.1.1.1 host

nc -zv host 443                  # 포트 확인
nc -l 8080                       # 리스닝
nc host 8080                     # 연결

curl URL; curl -I URL; curl -v URL; curl -L URL
wget URL; wget -O file URL

# 소켓 상태
ss -tlnp                         # TCP 리스닝
ss -tan                          # 모든 TCP
ss -tan state established
ss -tan state time-wait | wc -l
ss -s                            # 통계

# 패킷
sudo tcpdump -i any -nn -c 20 port 443
sudo tcpdump -w /tmp/cap.pcap -G 60 -W 5 port 80

# 방화벽 (iptables)
sudo iptables -L -n -v
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -t nat -L

sudo ufw status
sudo ufw allow 22/tcp
```

## 7. 디스크·파일시스템

```bash
df -h                            # 마운트별
df -ih                           # inode
du -sh /var/log/*
du -h --max-depth=1 / | sort -h
lsblk                            # 블록 디바이스
findmnt /                        # 마운트 정보
mount; umount /mnt/...
sudo mount /dev/sdb1 /mnt/data
sudo mount -o remount,rw /

# 큰 파일·오래된 파일
find / -size +500M 2>/dev/null
find /var/log -name "*.log" -mtime +7

# 열린 파일·소켓
lsof
lsof -p <pid>
lsof -i :8080
sudo lsof +D /var/log
fuser /var/log/myapp.log

# fd 한계
ulimit -n
cat /proc/<pid>/limits
ls /proc/<pid>/fd | wc -l
```

## 8. 압축·아카이브

```bash
tar -czvf out.tar.gz dir/        # 압축
tar -xzvf in.tar.gz              # 해제
tar -tzvf in.tar.gz              # 목록만
tar -xzvf in.tar.gz -C /target

zip -r out.zip dir/
unzip in.zip
unzip -l in.zip                  # 목록

gzip file                        # → file.gz
gunzip file.gz
zcat file.gz | grep ...

# rsync
rsync -avzP src/ dst/            # 동기화
rsync -avzP src/ user@host:/dst/
rsync --delete                   # 대상에서 사라진 것 삭제
```

## 9. 패키지

```bash
# Debian/Ubuntu
sudo apt update && sudo apt upgrade
sudo apt install nginx
sudo apt remove nginx
apt search keyword
dpkg -l | grep nginx
dpkg -L nginx                    # 설치된 파일 목록

# RHEL/CentOS
sudo dnf install nginx           # 또는 yum
rpm -qa | grep nginx
rpm -ql nginx
```

## 10. 사용자·그룹

```bash
whoami; id; groups
sudo useradd -m -s /bin/bash alice
sudo passwd alice
sudo userdel -r alice
sudo usermod -aG sudo alice      # 그룹 추가

sudo visudo                      # /etc/sudoers
cat /etc/passwd
cat /etc/group
```

## 11. 자주 쓰는 한 줄

```bash
# nginx 톱 10 IP
awk '{print $1}' /var/log/nginx/access.log | sort | uniq -c | sort -rn | head

# 5xx 응답
awk '$9 ~ /^5/' access.log

# 가장 큰 디렉터리
du -h --max-depth=1 /var | sort -h | tail

# 큰 파일 톱 10
find / -type f -printf '%s %p\n' 2>/dev/null | sort -rn | head

# 환경변수
env | sort

# 명령 시간
time long_command

# 명령 출력 + 파일에 동시 (tee)
cmd | tee log.txt
cmd | tee -a log.txt             # append
cmd 2>&1 | tee log.txt            # stderr도

# 백그라운드 + 완료 알림
long_cmd && notify-send "Done"

# 명령 반복 (Ctrl+R로 검색)
!!                               # 직전 명령
!ssh                             # ssh로 시작하는 마지막 명령
sudo !!                          # sudo + 직전 명령
!$                               # 직전 명령의 마지막 인자

# 디스크 정리 (오래된 journal)
sudo journalctl --vacuum-time=7d

# 메모리 캐시 비우기 (운영서버에서는 신중)
sync; echo 1 | sudo tee /proc/sys/vm/drop_caches
```

## 12. 디버깅·트러블슈팅

```bash
# 시스템 콜
strace -c cmd                    # 통계
strace -p <pid> -f -e openat
strace -p <pid> -tt -T -e all > out.log

# 라이브러리 콜
ltrace cmd
ltrace -p <pid>

# eBPF (Linux 4.9+)
sudo bpftrace -e 'tracepoint:syscalls:sys_enter_openat { @[comm] = count(); }'

# 누가 이 파일 쓰나
sudo fuser -v /var/log/myapp.log
sudo lsof /var/log/myapp.log
sudo inotifywait -m /var/log

# 코어 덤프
ulimit -c unlimited              # 활성화
# 분석
gdb /usr/bin/myapp /tmp/core.1234
```
