# Day 1 — 운영체제·커널·프로세스·시스템 콜

## 한 줄 요약

운영체제(OS)는 **하드웨어 추상화 + 자원 중재자**다. 응용 프로그램이 CPU·메모리·디스크·네트워크를 안전하게 나눠 쓸 수 있도록 **커널(Kernel)** 이 보호된 영역에서 중재하며, 응용은 **시스템 콜(System Call)** 을 통해서만 이를 요청할 수 있다.

## 학습 목표

- [ ] OS가 응용에게 제공하는 4대 추상화(프로세스, 가상메모리, 파일시스템, 네트워크 소켓)를 설명한다
- [ ] 사용자 공간(User Space)과 커널 공간(Kernel Space)의 차이와 **컨텍스트 스위칭** 비용을 안다
- [ ] 프로세스와 스레드의 차이를 메모리 관점에서 설명한다
- [ ] `fork()`, `exec()`, `wait()`, `exit()`의 생명주기를 설명한다
- [ ] Linux의 `ps`, `top`, `htop`과 PowerShell의 `Get-Process`로 프로세스를 조회한다
- [ ] PID, PPID, UID, GID, 좀비(Zombie) 프로세스 개념을 안다

---

## 1. 운영체제는 무엇을 해주는가

응용 프로그램이 직접 하드웨어를 만지면 어떤 문제가 생길까?

| 만약 OS가 없다면 | OS의 해법 |
|---|---|
| 두 프로그램이 같은 메모리 주소에 동시에 쓰면 충돌 | **가상메모리 (Virtual Memory)**: 각 프로세스에 가상 주소공간을 부여, MMU가 물리주소로 매핑 |
| CPU를 한 프로그램이 독점하면 다른 프로그램은 멈춤 | **스케줄러 (Scheduler)**: 시간 단위로 CPU를 분배 |
| 디스크에 어디다 써야 할지 응용이 직접 정해야 함 | **파일시스템 (File System)**: `open/read/write/close` 추상화 |
| 악성 응용이 다른 응용의 메모리·파일을 훔쳐봄 | **권한 분리 (User/Kernel Mode)**: 권한 없는 명령은 트랩 |
| 네트워크 카드 드라이버를 각자 짜야 함 | **드라이버 + 소켓 API**: `socket/bind/listen/accept/...` |

### Java 개발자 비유

- JVM이 응용에게 `Thread`, `File`, `Socket` 같은 추상화를 제공하는 것처럼, **OS는 JVM에게 추상화를 제공**한다.
- 즉 JVM은 OS 위에서 도는 또 하나의 추상화 레이어. `new FileInputStream()` 호출 → JVM 네이티브 코드 → 시스템 콜 `open()` → 커널이 파일을 연다.

---

## 2. 사용자 공간 vs 커널 공간

```
┌─────────────────────────────────────┐
│  User Space (Ring 3)                │
│  - 응용 프로그램 (java, nginx, chrome)│
│  - 라이브러리 (glibc, JVM)            │
│         ↕  시스템 콜 (trap)          │
├─────────────────────────────────────┤
│  Kernel Space (Ring 0)              │
│  - 스케줄러 / VM 관리자                │
│  - 파일시스템 / 네트워크 스택           │
│  - 디바이스 드라이버                   │
└─────────────────────────────────────┘
          ↕
       Hardware (CPU, RAM, Disk, NIC)
```

**컨텍스트 스위칭(Context Switching)**: 사용자 모드 ↔ 커널 모드 전환, 또는 프로세스 ↔ 프로세스 전환 시 레지스터·페이지 테이블 등을 저장/복원하는 작업. 비용이 비싸기에(수 μs ~ 수십 μs), 성능 튜닝에서는 시스템 콜 횟수를 줄이는 게 중요(`sendfile()`, NIO의 `transferTo()`가 그 예).

### 직접 보기

```bash
# strace: 시스템 콜 추적
strace -c ls /tmp
# % time     seconds  usecs/call     calls    errors syscall
# ------ ----------- ----------- --------- --------- ----------------
#  ...      0.000023           1        17           openat
#  ...      0.000019           1        21           read
```

> `strace`는 디버깅의 황금 도구. `apt install strace`. macOS는 `dtruss`(SIP 끄거나 sudo).

### Java에서 보기

```bash
# Linux에서 java 프로세스의 시스템 콜 추적
strace -p $(pgrep -f java) -c -e openat,read,write,connect
```

---

## 3. 프로세스 vs 스레드

| 구분 | 프로세스(Process) | 스레드(Thread) |
|---|---|---|
| 메모리 공간 | **독립적**. 각자 가상주소공간 | 같은 프로세스의 스레드는 **메모리 공유** (스택은 분리) |
| 생성 비용 | 비쌈 (`fork()` + 페이지 테이블 복제) | 저렴 (스택만 새로) |
| 통신 | IPC(파이프, 소켓, 공유메모리, 시그널) | 변수 공유 + 동기화(mutex) |
| 장애 격리 | 한 프로세스 죽어도 다른 프로세스 안전 | 한 스레드가 SIGSEGV 내면 프로세스 전체 죽음 |
| Java 예 | `java -jar app.jar`로 띄운 JVM 인스턴스 | JVM 내부 `Thread` 객체들 |

### 프로세스 생명주기 (POSIX)

```
parent ──fork()──> child (복제됨)
                     │
                     ├──exec("ls")──> 자기 메모리를 'ls' 이미지로 교체
                     │
                     ├──work...
                     │
                     └──exit(0) ────> 좀비 상태
parent ←──wait()────                  좀비 수거
```

- **fork()**: 부모 프로세스를 그대로 복제. 반환값으로 자식은 0, 부모는 자식 PID.
- **exec()**: 현재 프로세스 이미지를 다른 프로그램으로 **덮어씀**. 셸이 명령을 실행할 때 매번 일어남.
- **wait()**: 자식의 종료 상태를 수거. 안 하면 **좀비(Zombie)**.
- **orphan**: 부모가 먼저 죽으면 자식은 `init`(PID 1)이 입양.

### 시연

```bash
# Bash가 'ls' 실행할 때 내부적으로 fork + exec
strace -f -e fork,execve,wait4 bash -c 'ls > /dev/null'
```

```c
// fork 예제 (C). Java에는 fork가 없지만 OS 동작 이해를 위해
#include <unistd.h>
#include <stdio.h>
int main() {
    pid_t p = fork();
    if (p == 0)  printf("child pid=%d, parent=%d\n", getpid(), getppid());
    else         printf("parent pid=%d, child=%d\n", getpid(), p);
    return 0;
}
```

### Java에서 외부 프로세스 실행

```java
// Java에서 외부 프로세스 띄우기: 내부적으로 fork + exec
ProcessBuilder pb = new ProcessBuilder("ls", "-la", "/tmp");
pb.redirectErrorStream(true);
Process p = pb.start();
int exit = p.waitFor();   // wait() 시스템 콜
```

---

## 4. 프로세스 식별자와 속성

| 속성 | 의미 | 예 |
|---|---|---|
| **PID** | 프로세스 ID, 1부터 (init/systemd) | `1234` |
| **PPID** | 부모 PID | `1` (init이 부모) |
| **UID/EUID** | 실행 사용자 / 유효 사용자 | suid 프로그램은 EUID가 다름 |
| **GID/EGID** | 그룹 ID / 유효 그룹 | 위와 동일 |
| **상태(STAT)** | R(실행), S(슬립), D(중단불가), Z(좀비), T(정지) | `Z` = 좀비 |
| **niceness** | 우선순위 (-20~19, 낮을수록 우선) | 기본 0 |

### Linux: ps, top, htop

```bash
# 모든 프로세스 BSD 스타일
ps aux
ps aux | head

# 프로세스 트리
ps -ef --forest
pstree -p

# 실시간
top              # 기본
htop             # 시각화 (설치 필요)
btop             # 더 예쁨

# 특정 프로세스
pgrep -af java
pidof java
ps -p 1234 -o pid,ppid,user,stat,%cpu,%mem,cmd

# 자식 프로세스만
ps --ppid 1234
```

### Windows: PowerShell의 Get-Process

```powershell
# 모든 프로세스
Get-Process

# 메모리 톱 10
Get-Process | Sort-Object WorkingSet -Descending | Select-Object -First 10 Name, Id, @{N='MB';E={[math]::Round($_.WorkingSet/1MB,1)}}

# 특정 이름
Get-Process -Name chrome
Get-Process java*

# 부모-자식 (CIM 사용)
Get-CimInstance Win32_Process | Select-Object ProcessId, ParentProcessId, Name | Sort ParentProcessId
```

> **PowerShell 5.1 vs 7.x**: `Get-Process` 자체는 동일하나, `Get-Process | Where Name -eq chrome` 같은 단축 구문은 5.1에서도 동작. `&&` 연산자만 7.x 전용.

### Linux와 Windows 비교표

| 작업 | Linux | PowerShell |
|---|---|---|
| 프로세스 목록 | `ps aux` | `Get-Process` |
| 이름으로 찾기 | `pgrep firefox` | `Get-Process firefox` |
| 메모리 정렬 | `ps aux --sort=-rss \| head` | `Get-Process \| Sort WS -Desc \| Select -First 10` |
| 종료 | `kill -TERM 1234` / `pkill firefox` | `Stop-Process -Id 1234` / `Stop-Process -Name firefox` |
| 강제 종료 | `kill -9 1234` | `Stop-Process -Id 1234 -Force` |
| 실시간 모니터 | `top` / `htop` | `Get-Process \| Sort CPU -Desc \| Select -First 5` (반복) |

---

## 5. 시스템 콜이란

응용은 직접 디스크에 쓰지 못한다. 대신 라이브러리(`glibc`의 `fopen`) → 시스템 콜(`open`, `write`) → 커널 → 디스크 순서로 요청한다.

| 카테고리 | 대표 시스템 콜 |
|---|---|
| 프로세스 | `fork`, `execve`, `exit`, `wait4`, `clone` |
| 파일 | `open`, `read`, `write`, `close`, `lseek`, `stat` |
| 메모리 | `mmap`, `munmap`, `brk` |
| 네트워크 | `socket`, `bind`, `listen`, `accept`, `connect`, `send`, `recv` |
| 시그널 | `kill`, `sigaction`, `pause` |

Linux 시스템 콜 전체 목록: `man 2 syscalls` 또는 <https://man7.org/linux/man-pages/man2/syscalls.2.html>.

### 시스템 콜이 보이는 순간

```bash
# Java 앱이 파일을 여는 모든 순간 추적
strace -e openat -p $(pgrep -f java)

# Java 앱이 TCP 연결을 맺는 모든 순간
strace -e connect,accept,read,write -p $(pgrep -f java)
```

> **성능 팁**: NIO의 `FileChannel.transferTo()`는 내부적으로 `sendfile()` 시스템 콜을 사용해 사용자 공간 복사를 건너뛴다(zero-copy). 시스템 콜 횟수가 곧 성능.

---

## 6. 좀비와 고아 — 가장 흔한 운영 함정

```bash
# 좀비 만들기: 자식이 죽었는데 부모가 wait() 안 함
# (실제로 안 하길 권장; 예제만 읽기)
$ ps aux | grep ' Z '   # STAT가 Z이면 좀비

# 좀비를 정리하는 방법
# 1) 부모 프로세스를 종료시키면 init이 입양 후 정리
# 2) 부모가 SIGCHLD 핸들러에서 wait() 호출하도록 코드 수정
```

### Java/Spring에서 자주 보는 케이스

- `Runtime.exec()`로 띄운 외부 프로세스의 stdout을 안 읽으면 **버퍼가 꽉 차 자식이 멈춤**(좀비 아닌 deadlock).
- 해결: `ProcessBuilder.redirectErrorStream(true)` + 별도 스레드로 stdout 소비, 또는 `redirectOutput(File)`.

```java
// ❌ 위험: 자식의 stdout을 읽지 않음 → deadlock
Process p = new ProcessBuilder("long_running_cmd").start();
int exit = p.waitFor();   // 영원히 안 끝날 수 있음

// ✅ 안전
Process p = new ProcessBuilder("long_running_cmd")
    .redirectErrorStream(true)
    .redirectOutput(ProcessBuilder.Redirect.to(new File("/tmp/out.log")))
    .start();
int exit = p.waitFor();
```

---

## 7. 실제 사례

### CVE-2022-0185 (Linux 커널 — heap overflow)

- 파일시스템 관련 시스템 콜의 검증 누락으로 user namespace에서 root 권한 획득 가능.
- 교훈: 커널과 사용자 공간의 경계가 곧 보안 경계. 시스템 콜 인자 검증이 부실하면 곧장 권한 상승.

### Spring Boot에서 ProcessBuilder의 OS Command Injection (Log4Shell 후속)

- 사용자 입력을 `Runtime.exec("sh -c ..." + input)`처럼 셸에 넘기면 `;`, `|`로 인젝션.
- **올바른 방법**: `new ProcessBuilder(List.of("git", "log", branch))` — 셸을 거치지 않고 인자 배열로.

---

## 8. 실습 (Hands-on)

### Step 1: 시스템 콜 보기

```bash
# WSL 또는 Linux
strace -c -e openat,read,write ls /etc
# 어느 시스템 콜이 가장 많이 호출되는가?
```

### Step 2: 프로세스 트리 그리기

```bash
# Linux
pstree -p $$    # 현재 셸의 트리 ($$는 PID)

# PowerShell
Get-CimInstance Win32_Process |
  Select-Object ProcessId, ParentProcessId, Name |
  Where-Object ProcessId -eq $PID
```

### Step 3: 좀비 만들지 않기

```bash
# 백그라운드 작업과 wait
sleep 5 &
echo "PID: $!"   # 백그라운드 프로세스 PID
wait $!          # 명시적으로 수거
```

### Step 4: Java 프로세스 자원 확인

```bash
# 임의의 Java 앱 띄운 뒤
JPID=$(pgrep -f java)
cat /proc/$JPID/status   # 상태, 메모리, 스레드 수, 시그널 마스크 등
ls -l /proc/$JPID/fd     # 열린 파일 디스크립터들 (소켓 포함)
```

> `/proc/<pid>/` 는 Linux의 살아있는 디버깅 창. **이것 하나만 익혀도 운영 90%는 해결**.

---

## 더 읽어볼 자료

- 📘 『Operating Systems: Three Easy Pieces』 (무료 PDF: <https://pages.cs.wisc.edu/~remzi/OSTEP/>) — OS 입문서 최고봉
- 📘 『Linux Programming Interface』 (Michael Kerrisk) — 시스템 콜 바이블
- 🔗 `man 2 syscalls` — 모든 Linux 시스템 콜 목록
- 🔗 Brendan Gregg, "Linux Performance Tools" — <https://www.brendangregg.com/linuxperf.html>
- 🎓 MIT 6.S081 Operating Systems Engineering — <https://pdos.csail.mit.edu/6.S081/>

---

## 자가 점검

- [ ] 프로세스와 스레드의 차이를 메모리 관점에서 설명할 수 있는가?
- [ ] `strace -e openat ls`로 ls가 어떤 파일을 여는지 확인했는가?
- [ ] 좀비 프로세스가 왜 생기는지 fork/exit/wait로 설명할 수 있는가?
- [ ] `/proc/<pid>/`에서 어떤 정보를 얻을 수 있는지 적어도 3가지 안다?
- [ ] PowerShell `Get-Process`로 메모리 톱 10을 출력할 수 있는가?

다음: [`02_linux_filesystem_permission.md`](02_linux_filesystem_permission.md)
