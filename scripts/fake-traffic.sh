#!/bin/bash
# Mỗi ngày: chọn 9-10 giờ ngẫu nhiên (tăng dần), mỗi giờ đó thêm 1 lượt truy cập giả vào biểu đồ admin.
# Lượt giả KHÔNG phân biệt được với lượt thật (dtb_daily_visits chỉ đếm số lượt theo ngày).
#   Cài trên VPS:   (crontab -l; echo '5 17 * * * bash ~/InnerStyle-Backend/scripts/fake-traffic.sh') | crontab -
#                   (17:05 UTC = 00:05 giờ VN; VPS để giờ VN thì dùng '5 0 * * *')
#   Chạy thử ngay:  bash ~/InnerStyle-Backend/scripts/fake-traffic.sh now      (bỏ hết thời gian chờ)
#   Gỡ:             crontab -l | grep -v fake-traffic.sh | crontab -
#   Log:            tail ~/innerstyle-fake-traffic.log
# Backend trong docker-compose chỉ mở 127.0.0.1:2207 trên VPS -> gọi thẳng, không qua domain.
API=${API:-http://127.0.0.1:2207/api/common/visits}
exec >> "${LOG:-$HOME/innerstyle-fake-traffic.log}" 2>&1

# 9-10 mốc giây ngẫu nhiên trong 0..23h (tính từ lúc cron chạy 00:05), sắp tăng dần
slots=$(shuf -i 0-82800 -n "$(shuf -i 9-10 -n1)" | sort -n)
echo "$(date '+%F') lịch: $(for s in $slots; do date -d "+$s sec" '+%H:%M'; done | tr '\n' ' ')"

START=$(date +%s)
for s in $slots; do
  [ "$1" = now ] || { wait=$(( s - ($(date +%s) - START) )); [ "$wait" -gt 0 ] && sleep "$wait"; }
  if curl -sf -X POST "$API" >/dev/null; then
    echo "$(date '+%F %T') +1"
  else
    echo "$(date '+%F %T') lỗi gọi $API"
  fi
done
