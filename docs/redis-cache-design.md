# InnerStyle — Redis: Cache, TTL & Rate Limiting

> Thiết kế tầng Redis cho cache dữ liệu nóng, lưu token/OTP có TTL, và giới hạn
> tần suất (rate limit) cho các endpoint nhạy cảm. Dùng `spring-boot-starter-data-redis`
> (Lettuce). Một Redis instance, tách logic bằng **namespace key** + DB index.

## 1. Quy ước key

```
is:{env}:{domain}:{purpose}:{identifier}
```
Ví dụ: `is:prod:cache:model:8f3a...`, `is:prod:rl:login:ip:1.2.3.4`.
Mọi key BẮT BUỘC có TTL (trừ vài cache được refresh chủ động). Serialize JSON (GenericJackson2).

## 2. Cache dữ liệu (cache-aside)

| Mục đích | Key | TTL | Ghi chú / Invalidation |
|----------|-----|-----|------------------------|
| Master pricing | `cache:pricing:all` | 1 giờ | Xoá khi admin sửa `mtb_pricing` |
| Categories/Tags gallery | `cache:categories`, `cache:tags` | 6 giờ | Xoá khi master thay đổi |
| Hồ sơ user | `cache:user:{userId}` | 15 phút | Xoá khi update profile/role |
| Chi tiết model public | `cache:model:{slug}` | 10 phút | Xoá khi model sửa/đổi visibility |
| Feed gallery trang N | `cache:gallery:{cat}:{page}:{sort}` | 60 giây | TTL ngắn, chấp nhận hơi cũ |
| Đếm view (gom rồi flush) | `cnt:model:view:{modelId}` | tới khi flush | Cron 1 phút cộng dồn vào `dtb_models.view_count` |

Nguyên tắc: **chỉ cache dữ liệu PUBLIC hoặc ít đổi**. Không cache số dư ví / trạng thái thanh toán (luôn đọc DB trong transaction).

## 3. Token / phiên có TTL (Redis là nguồn nhanh; DB là nguồn sự thật cho audit)

| Dữ liệu | Key | TTL |
|---------|-----|-----|
| JWT access blacklist (logout sớm) | `jwt:bl:{jti}` | = thời gian sống còn lại của access token (~15 phút) |
| OTP / email verify code | `otp:verify:{userId}` | 10 phút |
| Mã reset mật khẩu (1 lần) | `otp:reset:{userId}` | 15 phút |
| Phiên refresh đang hoạt động (tra cứu nhanh) | `session:rt:{tokenHash}` | = TTL refresh (7–30 ngày) |
| Khoá đăng nhập (đếm sai mật khẩu) | `lock:login:{email}` | 15 phút sau 5 lần sai |
| Idempotency thanh toán (chống double-submit) | `idem:pay:{orderCode}` | 24 giờ |

Access token: 15 phút. Refresh token: 7 ngày (web) / 30 ngày (ghi nhớ). Logout = thêm `jti` vào blacklist + revoke refresh token trong DB.

## 4. Rate limiting (sliding-window log / token-bucket qua Lua atomic)

Triển khai bằng script Lua chạy nguyên tử trên Redis (INCR + EXPIRE) hoặc Bucket4j-Redis.
Mỗi giới hạn theo **IP** và/hoặc **userId**.

| Endpoint / hành động | Key | Giới hạn |
|----------------------|-----|----------|
| `POST /*/auth/login` | `rl:login:ip:{ip}` + `rl:login:email:{email}` | 5 / 1 phút, rồi khoá tăng dần |
| `POST /*/auth/register` | `rl:register:ip:{ip}` | 5 / giờ |
| Gửi lại email verify / quên mật khẩu | `rl:email:{userId|ip}` | 3 / 10 phút |
| Tạo job 3D (`POST /common/3d/**`) | `rl:gen:user:{userId}` | 10 / phút, 200 / ngày |
| Tạo đơn nạp tiền | `rl:pay:user:{userId}` | 5 / phút |
| Webhook Meshy / IPN cổng | `rl:webhook:ip:{ip}` | 60 / phút (kèm verify chữ ký) |
| API public gallery (mặc định) | `rl:api:ip:{ip}` | 120 / phút |

Khi vượt: trả `429 Too Many Requests` + header `Retry-After` và `X-RateLimit-Remaining`
(theo `rules/09-error-handling.md`, body `ErrorResponse` mã `rateLimit.exceeded`).

## 5. Cấu hình gợi ý (application.yml — thêm ở bước code)

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2s
app:
  rate-limit:
    enabled: true
    default-per-minute: 120
  cache:
    pricing-ttl: 1h
    user-ttl: 15m
    gallery-ttl: 60s
  jwt:
    access-ttl: 15m
    refresh-ttl: 7d
```

## 6. Lưu ý vận hành
- Redis **không phải nguồn sự thật** cho tiền/đơn hàng — chỉ tăng tốc & chống lạm dụng.
- Bật `maxmemory-policy allkeys-lru` cho phần cache; tách DB index cho rate-limit nếu cần flush riêng.
- Mọi key có TTL để tránh phình bộ nhớ; cron dọn `cnt:*` sau khi flush.
- Trên AWS: dùng **ElastiCache for Redis** ở production thay vì Redis trong docker-compose.
