# Lab 5 — 패킷 캡처로 추적하기

## 시나리오

"HTTPS 요청이 가끔 5초 이상 걸려요. 가끔이라 로그로는 안 잡혀요." → tcpdump를 항상 켜두고 사례 발생 시 .pcap을 받는다. Wireshark로 분석.

---

## 1. 환경 준비

```bash
# WSL Ubuntu에서
sudo apt install -y tcpdump tshark
```

Windows에 Wireshark 설치 (이미 했다면 패스).

## 2. 캡처

```bash
# WSL에서 백그라운드 캡처 시작 (rotation)
sudo tcpdump -i any -w /tmp/lab5_%H%M%S.pcap -G 60 -W 5 'port 443' &
TCPID=$!

# 다른 터미널에서 트래픽 발생
for i in 1 2 3 4 5; do
    curl -s -o /dev/null -w "Try $i: %{time_total}s\n" https://www.google.com/
done

# 종료
sudo kill $TCPID

ls -la /tmp/lab5_*.pcap
```

## 3. Wireshark로 분석

```bash
# WSL에서 Windows의 Wireshark로 열기
explorer.exe /tmp/lab5_*.pcap
# 또는 가장 최신만
ls -t /tmp/lab5_*.pcap | head -1 | xargs -I{} explorer.exe {}
```

### 분석 체크포인트

1. **TCP handshake 시간**: SYN의 frame.time과 첫 데이터의 시간 차이
   - 표시 필터: `tcp.flags.syn == 1 and tcp.flags.ack == 0`
   - 우클릭 → Follow TCP Stream

2. **TLS handshake 시간**: ClientHello ~ Application Data
   - 표시 필터: `tls.handshake.type == 1` (ClientHello)
   - 그 다음 Application Data까지

3. **재전송**: 표시 필터 `tcp.analysis.retransmission`
   - 있으면 네트워크 품질 의심

4. **RST**: 표시 필터 `tcp.flags.reset == 1`
   - 있으면 비정상 종료

5. **DNS**: 표시 필터 `dns`
   - 쿼리 → 응답 시간

### 통계

- Statistics → Conversations: 호스트 페어별
- Statistics → TCP > Round Trip Time: RTT 변동
- Statistics → I/O Graphs: 시간대별 패킷

---

## 4. tshark로 자동 추출

```bash
# RTT 통계
tshark -r /tmp/lab5_*.pcap -q -z io,stat,1,'COUNT(tcp.analysis.retransmission)tcp.analysis.retransmission'

# 핸드셰이크 시간 (TCP만)
tshark -r /tmp/lab5_*.pcap \
    -Y 'tcp.flags.syn == 1 and tcp.flags.ack == 0' \
    -T fields -e frame.time -e ip.src -e ip.dst -e tcp.dstport
```

---

## 5. 챌린지

다음 시나리오를 직접 만들고 분석:

### Q1: localhost에 단순 서버를 띄우고 GET 요청 캡처

```bash
# 한 터미널: 서버
python3 -m http.server 8000 &

# 다른 터미널: 캡처
sudo tcpdump -i lo -w /tmp/local.pcap -c 30 port 8000 &

# 또 다른 터미널: 요청
curl http://localhost:8000/

sudo kill $! 2>/dev/null || true
ls -la /tmp/local.pcap
```

Wireshark에서:
- 3-way handshake (SYN → SYN-ACK → ACK)
- HTTP 요청·응답 본문
- 4-way termination (FIN → ACK → FIN → ACK)

### Q2: 연결 끊김(RST) 만들기

```bash
sudo tcpdump -i lo -w /tmp/rst.pcap -c 20 port 8000 &

# 요청 보내자마자 클라이언트 강제 종료
curl http://localhost:8000/ &
sleep 0.1 && kill -9 $!

sudo kill $!
```

캡처에서 RST 또는 FIN을 확인.

### Q3: SYN flood 모방 (로컬만!)

```bash
sudo apt install -y hping3

# 별도 터미널에서 캡처
sudo tcpdump -i lo -w /tmp/syn.pcap -c 100 port 8000 &

# SYN만 (--flood는 매우 빠름. -c로 제한)
sudo hping3 -S -p 8000 -c 50 127.0.0.1

sudo kill $!
```

Wireshark의 Expert Info에서 비정상 신호 확인.

---

## 6. 회고

- 1ms 단위 RTT가 보이는가? 운영 환경의 RTT는 보통 1~10ms (사내) ~ 100~300ms (글로벌)
- 만약 캡처에서 TCP handshake가 500ms 걸린다면 어디 문제인가? (네트워크 지연, 서버 accept 못 따라감, etc.)
- TLS handshake가 길면? (1.3 미지원, OCSP 지연, 인증서 검증)

다음: [`lab6_firewall_iptables.md`](lab6_firewall_iptables.md)
