# Day 4 — OS 성능 분석 · 튜닝

## 한 줄 요약

운영서버가 느려졌을 때, **USE 메소드**(Utilization, Saturation, Errors)를 따라 CPU·메모리·디스크·네트워크 순서로 점검한다. Brendan Gregg의 도구 지도가 그 지침이다. 각 도구가 무엇을 보는지·언제 쓰는지 외워두면 대부분의 운영 장애 1차 진단이 가능하다.

## 학습 목표

- [ ] **USE 메소드**의 의미와 적용 순서를 안다
- [ ] CPU 분석: `top`, `htop`, `mpstat`, `pidstat`, `perf top`
- [ ] 메모리 분석: `free`, `vmstat`, `/proc/meminfo`, `slabtop`
- [ ] 디스크: `iostat`, `iotop`, `lsof`
- [ ] 네트워크: `ss`, `iftop`, `nload`, `bmon`
- [ ] 시스템 콜·라이브러리 추적: `strace`, `ltrace`, `bpftrace`
- [ ] Windows: `Performance Monitor`, `ETW`, `Process Explorer`, `PerfView`

---

## 1. Brendan Gregg's USE 메소드

각 자원에 대해:

- **Utilization (사용률)**: 자원이 일하는 시간 비율
- **Saturation (포화)**: 자원이 처리하지 못해 큐가 쌓이는 정도
- **Errors (에러)**: 자원이 보고하는 에러

| 자원 | Utilization | Saturation | Errors |
|---|---|---|---|
| CPU | `top` %CPU | load average, `vmstat` r | (드뭄) |
| 메모리 | `free`, `vmstat` | swap 사용, `vmstat` si/so | OOM kill, `dmesg` |
| 디스크 | `iostat` %util | `iostat` await, avgqu-sz | `dmesg`, `smartctl` |
| 네트워크 | `ifstat`, `sar -n DEV` | TX/RX drop, `netstat -i` | 위와 동일 |

---

## 2. CPU 분석

### top / htop

```bash
top
# load average: 1.23, 0.95, 0.78 ← 1분/5분/15분 평균 (코어 수와 비교)
# us, sy, id, wa, st: 사용자, 커널, idle, IO wait, 가상화 steal
# 각 프로세스의 %CPU (한 코어 = 100%, 멀티코어면 그 이상)

htop      # 더 보기 좋음. F2로 설정, F4로 필터, F5로 트리
btop      # 가장 예쁨
```

| 컬럼 | 의미 |
|---|---|
| %CPU | 마지막 샘플의 CPU 사용 |
| %MEM | RSS / 전체 메모리 |
| VIRT | 가상 메모리 (의미 적음) |
| RES (RSS) | 실제 물리 메모리 |
| SHR | 공유 메모리 |
| PR/NI | 우선순위 / nice |
| TIME+ | 누적 CPU 시간 |
| S | 상태 (R, S, D, Z) |

> **착각 주의**: `D` (uninterruptible sleep)는 보통 디스크 IO 대기. 많이 보이면 디스크 문제. `Z` (zombie)는 별도 자원은 안 먹지만 PID 슬롯을 점유.

### load average

- 시스템 load = "대기열 + 실행 중" 평균
- 4코어 시스템에서 load 4.0 = 풀가동, 8.0 = 50% 대기
- `D` (uninterruptible) 상태도 포함 → 디스크 병목이면 load 높음

### mpstat — 코어별

```bash
mpstat -P ALL 1
# 03:00:00 AM  CPU    %usr   %nice   %sys %iowait    %irq   %soft  %steal  %guest  %idle
# 03:00:01 AM  all   45.32    0.00   8.21    1.50    0.00    0.50    0.00    0.00   44.47
# 03:00:01 AM    0   80.10    0.00  15.00    0.00    0.00    1.00    0.00    0.00    3.90
# 03:00:01 AM    1    5.00    0.00   1.00    1.00    0.00    0.00    0.00    0.00   93.00

# CPU 0만 매우 바쁘면 부하가 한 코어에 몰림 (NIO single-thread, IRQ pinning 등)
```

### pidstat — 프로세스별 시계열

```bash
pidstat 1                       # 매초 활성 프로세스
pidstat -p $(pgrep java) 1
pidstat -t -p $(pgrep java) 1    # 스레드별
pidstat -d 1                     # 디스크 IO
```

### perf — 함수 단위 핫스팟

```bash
sudo apt install -y linux-tools-common linux-tools-generic

# 실시간 top
sudo perf top -p $(pgrep java)

# 캡처 후 분석
sudo perf record -F 99 -p $(pgrep java) -g -- sleep 30
sudo perf report

# Flame Graph
sudo perf record -F 99 -p $(pgrep java) -g -- sleep 30
sudo perf script > out.stacks
# https://github.com/brendangregg/FlameGraph
FlameGraph/stackcollapse-perf.pl out.stacks | FlameGraph/flamegraph.pl > flame.svg
```

> Java의 경우 `async-profiler`(<https://github.com/async-profiler/async-profiler>)가 perf보다 친화적.

### Java 전용 도구

```bash
jps -l                                   # JVM 프로세스 목록
jstack <pid> > thread-dump.txt           # 스레드 덤프
jcmd <pid> Thread.print                  # 동일
jcmd <pid> GC.heap_info                  # 힙 정보
jcmd <pid> VM.native_memory summary      # 네이티브 메모리
jstat -gcutil <pid> 1s                   # GC 상황 실시간

# JFR (Java Flight Recorder)
jcmd <pid> JFR.start duration=60s filename=/tmp/rec.jfr
jcmd <pid> JFR.dump filename=/tmp/rec.jfr
# JDK Mission Control로 열기
```

---

## 3. 메모리 분석

```bash
free -h
#              total  used   free  shared  buff/cache  available
# Mem:           15G   8.2G  1.0G    100M        6.0G       6.5G
# Swap:         2.0G    50M  1.9G
```

| 컬럼 | 의미 |
|---|---|
| total | 전체 메모리 |
| used | 사용 중 |
| free | 비어있음 (보통 매우 적음, 정상) |
| buff/cache | 페이지 캐시 (필요 시 즉시 회수 가능) |
| available | 실제 가용 (free + 회수 가능 캐시) |
| swap used | 스왑 사용 |

> **흔한 오해**: `free`가 적다고 메모리 부족이 아님. **`available`**을 봐야 함. 캐시는 빈 메모리를 재활용하는 것.

```bash
# 더 자세히
cat /proc/meminfo

# 가장 메모리 많이 쓰는 프로세스 톱 10
ps aux --sort=-%mem | head

# 슬랩 캐시 (커널 구조체)
sudo slabtop
```

### vmstat — 시스템 전체 추세

```bash
vmstat 1
# procs ---memory---- ---swap-- -----io---- -system-- ------cpu-----
#  r  b   swpd   free   si   so    bi    bo   in   cs us sy id wa st
#  1  0      0  1024M    0    0   100   200 1000 2000 30 10 55  5  0
```

| 컬럼 | 의미 |
|---|---|
| r | 실행 대기 큐 (코어 수와 비교) |
| b | uninterruptible 대기 (IO 병목 의심) |
| si/so | swap in/out (있으면 메모리 부족) |
| bi/bo | blocks in/out (디스크 IO) |
| in | 인터럽트/초 |
| cs | 컨텍스트 스위치/초 |
| us/sy/id/wa | CPU 분배 |

### OOM killer

메모리 부족 시 커널이 점수 매겨 프로세스 죽임:

```bash
dmesg | grep -i "oom\|killed process"

# /var/log/syslog에 영구
sudo journalctl -k | grep -i oom

# 특정 프로세스 보호
echo -1000 | sudo tee /proc/<pid>/oom_score_adj    # OOM 면제
echo 1000 | sudo tee /proc/<pid>/oom_score_adj      # 우선 죽임

# 스왑 활성도
cat /proc/sys/vm/swappiness   # 0~100, 낮을수록 swap 안 씀
```

---

## 4. 디스크 IO

```bash
# 종합
iostat -xz 1
# Device     r/s     w/s     rkB/s    wkB/s   await   %util
# sda        50.0    20.0   2048.00   800.00    5.20   85.0

# %util 100%: 디스크 풀가동
# await: 평균 IO 대기 시간 (ms)
# avgqu-sz: 평균 큐 깊이

# 어느 프로세스가 IO 많이?
sudo iotop -oP

# 열린 파일 (특정 디렉터리에 누가 쓰나)
sudo lsof +D /var/log
sudo lsof -p <pid>            # 프로세스의 열린 fd 전부
sudo lsof -i :8080             # 8080 포트 쓰는 프로세스

# 디스크 한계
sudo dd if=/dev/zero of=/tmp/test bs=1M count=1024 conv=fdatasync   # 쓰기 속도
sudo hdparm -Tt /dev/sda                                              # 읽기 속도
```

### 파일 디스크립터 한계 — 운영의 흔한 함정

```bash
# 시스템 전체
cat /proc/sys/fs/file-max

# 프로세스별 한계
ulimit -n

# 특정 프로세스의 fd 사용량
ls /proc/$(pgrep -f java)/fd | wc -l
cat /proc/$(pgrep -f java)/limits | grep -i open

# 운영서버에서 늘리기
sudo vim /etc/security/limits.conf
# myapp soft nofile 65536
# myapp hard nofile 65536
# 또는 systemd 서비스의 LimitNOFILE=65536
```

**증상**: "Too many open files" → 새 연결 거부. 풀 leak 또는 한계 too low.

---

## 5. 네트워크 모니터링

```bash
# 인터페이스별 트래픽
ifstat 1
sar -n DEV 1
nload
bmon

# 인터페이스 통계
ip -s link show eth0
ethtool -S eth0 | grep -i -E "drop|error"

# 연결별 트래픽 (Linux)
iftop                          # GUI like
sudo nethogs                   # 프로세스별 대역폭

# TCP 통계
nstat
nstat -z                       # 모두 (0 포함)
netstat -s | grep -E "retrans|drop"   # 재전송, 드롭
```

### 흔한 신호

| 신호 | 의미 |
|---|---|
| `dropped packets` (`ip -s link`) | NIC 큐 가득 — 처리 속도 부족 |
| `TCP retransmits` 폭증 | 네트워크 손실 또는 폭주 |
| `accept queue overflows` | listen backlog 부족 또는 앱 accept 못 따라감 |

---

## 6. strace · bpftrace

### strace — 시스템 콜 추적

```bash
# 통계
strace -c -p $(pgrep -f java)
# % time     seconds  usecs/call     calls    errors syscall
# ------ ----------- ----------- --------- --------- ----------------
#  50.00    0.500000        500       1000           futex
#  ...

# 시간 비싼 콜
strace -T -tt -e openat,read,write -p $(pgrep -f java)

# 자식 프로세스까지
strace -f -p <pid>
```

> ⚠ **strace는 매우 느려진다 (붙은 프로세스가).** 프로덕션에서는 짧게만, 또는 `bpftrace`/`perf` 사용.

### bpftrace — 차세대 (Linux 4.9+)

```bash
sudo apt install -y bpftrace

# 모든 프로세스의 openat
sudo bpftrace -e 'tracepoint:syscalls:sys_enter_openat { @[comm] = count(); }'

# TCP retransmit 추적
sudo bpftrace -e 'kprobe:tcp_retransmit_skb { printf("%s\n", comm); }'

# 한 줄 마법
sudo bpftrace -e 'tracepoint:syscalls:sys_enter_read /pid == 1234/ { @[ustack] = count(); }'
```

---

## 7. Windows 성능 분석

| 도구 | 용도 |
|---|---|
| **Task Manager** | top 등가 |
| **Resource Monitor (resmon)** | iostat + nethogs + lsof 통합 |
| **Performance Monitor (perfmon)** | 시계열 카운터 (CPU, 메모리, 디스크, 네트워크, .NET CLR, ...) |
| **Process Explorer** | htop + lsof + strace 통합 (강력) |
| **PerfView** | perf의 Windows 버전 (.NET 분석 강력) |
| **WPA / WPR** (Windows Performance Toolkit) | ETW 트레이스 분석 |

### PowerShell로 카운터

```powershell
# 현재 CPU 사용률
Get-Counter '\Processor(_Total)\% Processor Time'

# 디스크 큐
Get-Counter '\PhysicalDisk(_Total)\Current Disk Queue Length'

# 시계열
Get-Counter '\Processor(_Total)\% Processor Time' -SampleInterval 1 -MaxSamples 10
```

### 이벤트 로그

```powershell
Get-WinEvent -LogName System -MaxEvents 50 |
    Where LevelDisplayName -in 'Error','Warning' |
    Select TimeCreated, Id, ProviderName, Message
```

---

## 8. 흔한 운영 시나리오

### 시나리오 1: "Java 프로세스 CPU 200%"

```bash
# 어느 스레드가?
jps -l
TOP_TID=$(ps -L -p <jvm-pid> -o tid,pcpu | sort -rk2 | head -2 | tail -1 | awk '{print $1}')
HEX_TID=$(printf '%x' $TOP_TID)

# 스레드 덤프에서 그 nid 찾기
jstack <jvm-pid> | grep "nid=0x$HEX_TID" -A 30
# → 어느 메서드가 도는지 보임 (busy loop? GC? deadlock?)
```

### 시나리오 2: "메모리가 계속 늘어요" (leak 의심)

```bash
# JVM 힙
jcmd <pid> GC.heap_info
jstat -gcutil <pid> 5s

# 힙 덤프
jcmd <pid> GC.heap_dump /tmp/heap.hprof
# Eclipse MAT 또는 jhat으로 분석

# 네이티브 메모리
jcmd <pid> VM.native_memory baseline
# (시간 후)
jcmd <pid> VM.native_memory summary.diff
```

### 시나리오 3: "디스크 100% util"

```bash
# 누가?
sudo iotop -oP

# 어느 파일?
sudo lsof +D /var/lib/postgresql

# 시스템 콜 추적
sudo strace -e write -p <pid>
```

### 시나리오 4: "Connection refused" 폭증

```bash
# accept queue overflow?
ss -tln 'sport = :8080'         # Send-Q (backlog), Recv-Q (대기)
nstat -z TcpExtListenOverflows TcpExtListenDrops
# Listen overflows가 보이면 backlog 부족
```

---

## 9. 실습

### Step 1: USE 메소드로 본인 PC 진단

```bash
# 1) CPU
top -bn1 | head -10
mpstat -P ALL 1 3

# 2) 메모리
free -h
cat /proc/meminfo | head -10

# 3) 디스크
iostat -xz 1 3
df -h

# 4) 네트워크
ip -s link show
ss -s
```

### Step 2: 인위적 부하 생성

```bash
# CPU 부하 (모든 코어)
sudo apt install -y stress-ng
stress-ng --cpu 4 --timeout 30s

# 메모리 부하
stress-ng --vm 2 --vm-bytes 1G --timeout 30s

# 디스크 부하
stress-ng --hdd 2 --timeout 30s

# 동시에 htop, iostat, vmstat 보면서 어디 보이는지 확인
```

### Step 3: Java 프로세스 분석

```bash
# 임의 Java 앱 띄우고
java -jar your-app.jar &
JPID=$!

# 종합 정보
jcmd $JPID VM.flags
jcmd $JPID VM.system_properties
jcmd $JPID Thread.print | head -50

# 실시간 GC
jstat -gcutil $JPID 1s 10
```

### Step 4: Windows 동등

```powershell
# CPU
Get-Process | Sort CPU -Desc | Select -First 10 Name, CPU, WS

# 카운터
Get-Counter -Counter '\Processor(_Total)\% Processor Time','\Memory\Available MBytes','\PhysicalDisk(_Total)\Current Disk Queue Length' -SampleInterval 1 -MaxSamples 5

# 자세한 분석은 Resource Monitor 또는 PerfView GUI
```

---

## 10. 운영 튜닝 체크리스트

### Linux 서버 시작 시 점검

```bash
# 한계
ulimit -n                        # 파일 디스크립터
ulimit -u                        # 프로세스 수

# 커널 파라미터
sysctl net.ipv4.tcp_max_syn_backlog
sysctl net.core.somaxconn
sysctl vm.swappiness
sysctl fs.file-max

# CPU 거버너 (성능 모드)
cpupower frequency-info
```

### 권장 sysctl (HTTP 서버)

```bash
# /etc/sysctl.d/99-tuning.conf
net.core.somaxconn = 65535
net.ipv4.tcp_max_syn_backlog = 65535
net.core.netdev_max_backlog = 10000
net.ipv4.tcp_tw_reuse = 1
net.ipv4.ip_local_port_range = 10000 65535
net.ipv4.tcp_fin_timeout = 15
net.ipv4.tcp_keepalive_time = 600
vm.swappiness = 10
fs.file-max = 1000000
```

적용:

```bash
sudo sysctl -p /etc/sysctl.d/99-tuning.conf
```

---

## 더 읽어볼 자료

- 📘 『Systems Performance』 (Brendan Gregg, 2nd ed.)
- 📘 『BPF Performance Tools』 (Brendan Gregg)
- 🔗 Brendan Gregg's Linux Performance: <https://www.brendangregg.com/linuxperf.html>
- 🔗 USE Method: <https://www.brendangregg.com/usemethod.html>
- 🔗 Async Profiler (Java): <https://github.com/async-profiler/async-profiler>
- 🔗 Microsoft Performance Toolkit: <https://learn.microsoft.com/windows-hardware/test/wpt/>

---

## 자가 점검

- [ ] USE 메소드의 U/S/E 의미를 안다
- [ ] `top`의 load average와 wa, st 컬럼을 해석한다
- [ ] `free`의 `available`이 진짜 가용 메모리임을 안다
- [ ] `iostat -xz` 출력에서 await, %util의 의미를 안다
- [ ] Java 프로세스가 200% CPU일 때 어느 스레드인지 찾는 절차를 안다
- [ ] 파일 디스크립터 한계가 어디 있는지(`ulimit -n`, systemd `LimitNOFILE`) 안다

다음: [`05_capstone.md`](05_capstone.md)
