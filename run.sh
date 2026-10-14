#!/usr/bin/env bash
# Mở nhiều cửa sổ client GoRide cùng lúc.
#   ./run.sh 4            -> mở 4 client (server phải đang chạy)
#   ./run.sh server 4     -> chạy server (nền, log ở target/server.log) rồi mở 4 client
#   ./run.sh              -> mở 1 client
# Mỗi client là một tiến trình riêng, cửa sổ xếp lệch nhau và có số [#1], [#2]... trên tiêu đề.
# Đóng một cửa sổ chỉ tắt client đó; Ctrl+C ở terminal này tắt hết các client (và server nếu script đã bật).
set -euo pipefail
cd "$(dirname "$0")"

START_SERVER=0
if [[ "${1:-}" == "server" ]]; then START_SERVER=1; shift; fi
N="${1:-1}"
if ! [[ "$N" =~ ^[0-9]+$ ]] || (( N < 1 || N > 20 )); then
  echo "Số cửa sổ phải từ 1 đến 20. Ví dụ: ./run.sh 4" >&2; exit 1
fi

echo "Biên dịch..."
mvn -q compile

PIDS=()
cleanup() { for p in "${PIDS[@]}"; do kill "$p" 2>/dev/null || true; done; }
trap cleanup INT TERM

if (( START_SERVER )); then
  mvn -q -Pserver exec:java > target/server.log 2>&1 &
  PIDS+=($!)
  echo -n "Đang bật server"
  for _ in $(seq 1 60); do
    grep -q "sẵn sàng nhận client" target/server.log 2>/dev/null && break
    sleep 1; echo -n "."
  done
  echo; grep -q "sẵn sàng nhận client" target/server.log || { echo "Server chưa lên, xem target/server.log" >&2; cleanup; exit 1; }
fi

for i in $(seq 1 "$N"); do
  mvn -q exec:java -Dgoride.slot="$i" > "target/client-$i.log" 2>&1 &
  PIDS+=($!)
  echo "Đã mở client #$i (log: target/client-$i.log)"
done
echo "Đang chạy $N client. Ctrl+C để tắt tất cả."
wait
