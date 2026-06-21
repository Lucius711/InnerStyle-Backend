# InnerStyle — Redis: triển khai (cache + rate limit + JWT blacklist)

Hiện thực hóa thiết kế trong `redis-cache-design.md`. **Toàn bộ fail-open**: nếu Redis chết,
app vẫn chạy (cache miss → đọc DB; rate limit → cho qua; blacklist → coi như chưa thu hồi).

## Thành phần (`com.innerstyle.redis`)
| Lớp | Vai trò |
|-----|---------|
| `RedisConfig` | `RedisTemplate<String,Object>` (JSON) cho cache; `StringRedisTemplate` (auto) cho counter/blacklist |
| `RedisKeys` | Sinh key namespaced `is:dev:<domain>:...` |
| `cache/CacheService` | Cache-aside get/put/evict, fail-open |
| `ratelimit/RateLimiterService` | Fixed-window bằng **Lua atomic** (INCR + PEXPIRE), fail-open |
| `ratelimit/RateLimitFilter` | Chọn bucket theo path/method, định danh theo user/IP, trả `429` + headers |
| `security/TokenBlacklist` | Blacklist access-token (jti) khi logout |

## Rate limit (mặc định, chỉnh ở `app.rate-limit.*`)
| Bucket | Giới hạn | Định danh |
|--------|----------|-----------|
| `login` (`/auth/login`) | 5 / phút | IP |
| `register` | 5 / giờ | IP |
| `email` (forgot/resend) | 3 / 10 phút | IP |
| `generation` (POST `/api/common/3d/**`) | 10 / phút | user |
| `payment` (`/wallet/topup`) | 5 / phút | user |
| `api` (mọi `/api/**` còn lại) | 120 / phút | IP |
IPN cổng (`/api/common/payments/**`) và webhook được **bỏ qua** (server-to-server, đã verify chữ ký).
Vượt giới hạn → `429` body `ErrorResponse{rateLimit.exceeded}` + header `Retry-After`, `X-RateLimit-Remaining`.

## JWT blacklist
- Logout: revoke refresh token + **blacklist jti** của access token tới khi nó hết hạn.
- `JwtAuthenticationFilter` kiểm tra blacklist mỗi request; jti nằm trong blacklist → coi như chưa đăng nhập.

## Cache
- `cache:pricing:map` (TTL 1h): bảng giá `mtb_pricing` (taskType→giá). `PricingService.evictCache()` để làm mới khi admin sửa giá.
- TTL khác (gallery 60s, user 15m) cấu hình ở `app.cache.*`, dùng dần khi làm module Library.

## Chạy Redis (dev)
```bash
docker run -d --name innerstyle-redis -p 6379:6379 redis:7-alpine
```
Hoặc bỏ qua — app vẫn chạy không cần Redis (mất cache/giới hạn). Production: dùng **AWS ElastiCache**.

> Sandbox chỉ có Java 11 nên chưa biên dịch tự động. Chạy `mvn -q clean compile` (Java 21) để xác nhận.
