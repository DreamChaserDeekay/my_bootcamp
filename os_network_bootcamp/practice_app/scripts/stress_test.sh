#!/usr/bin/env bash
#
# stress_test.sh — practice_app에 부하를 가하고 시스템 상태를 캡처
#
set -euo pipefail
IFS=$'\n\t'

URL="${1:-http://localhost:8080/work?ms=50}"
DURATION="${2:-60s}"
CONNS="${3:-200}"
THREADS="${4:-10}"
OUT_DIR="${5:-./data/$(date +%Y%m%d_%H%M%S)}"

mkdir -p "$OUT_DIR"
echo "Output: $OUT_DIR"
echo "URL: $URL, $THREADS threads × $CONNS conns × $DURATION"

# 종속 도구 확인
for c in wrk mpstat vmstat ss jps; do
    command -v "$c" >/dev/null || { echo "$c not found. apt install $c (mpstat: sysstat)"; exit 1; }
done

JPID=$(jps -l | grep NetLabApp | awk '{print $1}' || true)
[[ -z "${JPID:-}" ]] && { echo "NetLabApp not running. Start with: ./gradlew bootRun"; exit 1; }
echo "Target JVM pid: $JPID"

# 백그라운드 측정
mpstat -P ALL 1 > "$OUT_DIR/mpstat.log" &
MP=$!
vmstat 1 > "$OUT_DIR/vmstat.log" &
VM=$!
(while true; do
    echo "$(date +%s) $(ss -tan | awk 'NR>1 {print $1}' | sort | uniq -c | tr '\n' ' ')";
    sleep 1
done) > "$OUT_DIR/sockets.log" &
SK=$!
jstat -gcutil "$JPID" 1000 > "$OUT_DIR/gc.log" &
JS=$!

cleanup() {
    kill $MP $VM $SK $JS 2>/dev/null || true
    echo "Logs in: $OUT_DIR"
}
trap cleanup EXIT

# 부하
wrk -t "$THREADS" -c "$CONNS" -d "$DURATION" --latency "$URL" | tee "$OUT_DIR/wrk.log"

# 요약
echo "=== Summary ==="
grep -E "Requests/sec|Latency|Socket errors" "$OUT_DIR/wrk.log" || true
echo "Final socket states:"
ss -tan | awk 'NR>1 {print $1}' | sort | uniq -c
echo "JVM:"
curl -s http://localhost:8080/info | jq . || curl -s http://localhost:8080/info
