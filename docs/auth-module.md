# InnerStyle — Auth module

Xác thực & vòng đời tài khoản: đăng ký, xác minh email, đăng nhập/refresh/logout,
quên & đặt lại mật khẩu, đăng nhập social (Google/Facebook). Stateless JWT + RBAC (USER/ADMIN).

## Endpoint (đều dưới prefix `/api`)

| Method | Path | Bảo vệ | Mô tả |
|--------|------|--------|-------|
| POST | `/api/user/auth/register` | public | Đăng ký email/mật khẩu, gửi email xác minh |
| POST | `/api/user/auth/verify-email` | public | Xác minh email bằng token |
| POST | `/api/user/auth/resend-verification` | public | Gửi lại email xác minh |
| POST | `/api/user/auth/login` | public | Đăng nhập → access + refresh token |
| POST | `/api/user/auth/refresh` | public | Xoay refresh token → access token mới |
| POST | `/api/user/auth/logout` | public | Thu hồi refresh token |
| POST | `/api/user/auth/forgot-password` | public | Gửi email đặt lại mật khẩu |
| POST | `/api/user/auth/reset-password` | public | Đặt lại mật khẩu bằng token |
| POST | `/api/user/auth/oauth/{provider}` | public | Social login (`google`/`facebook`) |
| GET  | `/api/user/account/me` | Bearer JWT | Hồ sơ user hiện tại |

## Đặc điểm bảo mật
- Mật khẩu băm **BCrypt**. Access token **HS256** TTL 15 phút; refresh token opaque (chỉ lưu **SHA-256 hash**), TTL 7 ngày, **xoay vòng + thu hồi**, phát hiện reuse.
- Khoá đăng nhập sau 5 lần sai (`locked_until`). Audit mọi lần đăng nhập (`dtb_login_audit`).
- Email/token reset chỉ lưu hash, có hạn dùng. Các endpoint nhạy cảm trả lỗi không lộ tồn tại tài khoản (silent).
- Lỗi trả về mã ổn định (vd `auth.invalidCredentials`, `auth.emailNotVerified`) — frontend i18n dịch, theo `rules/09`.

## Cách chạy / test
1. Cấu hình `.env` (xem `.env.example`): `JWT_SECRET`, `DATABASE_*`. Email ở dev chỉ **log link** ra console (`LoggingEmailSender`) — chưa cần SMTP.
2. `mvn spring-boot:run` (Java 21). Flyway tự chạy migration tạo bảng + seed role USER/ADMIN.
3. Thử nhanh:
```bash
# Đăng ký
curl -X POST localhost:2207/api/user/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"huy@example.com","password":"S3curePass!","fullName":"Do Huy"}'
# -> xem link verify trong log app, gọi:
curl -X POST localhost:2207/api/user/auth/verify-email -H 'Content-Type: application/json' \
  -d '{"token":"<token-từ-log>"}'
# Đăng nhập
curl -X POST localhost:2207/api/user/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"huy@example.com","password":"S3curePass!"}'
# Gọi endpoint cần auth
curl localhost:2207/api/user/account/me -H 'Authorization: Bearer <accessToken>'
```
Swagger UI: `http://localhost:2207/swagger-ui.html`.

## Việc còn lại / nâng cấp sau
- Tích hợp **SMTP/SES** thật (thay `LoggingEmailSender`).
- Đưa **JWT access blacklist** + **rate limit** sang Redis (đã thiết kế ở `redis-cache-design.md`).
- Endpoint admin (`/api/admin/auth/login`, quản lý user) khi làm module Admin.
- Đặt sinh 3D sau cổng auth + trừ ví (module Wallet — bước sau).

> Lưu ý: môi trường tạo file này chỉ có Java 11 nên **chưa biên dịch tự động được**. Hãy chạy
> `mvn -q clean compile` trên máy (Java 21) để xác nhận build trước khi sang bước tiếp theo.
