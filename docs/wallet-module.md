# InnerStyle — Wallet & Payment module

Ví theo mô hình **cọc/giữ tiền (authorization hold)** + nạp tiền qua **VNPay / MoMo**.

## Mô hình tiền
Mỗi user một ví: `availableBalance` (tiêu được) + `heldBalance` (đang giữ). Mọi thay đổi số dư
được khoá hàng (pessimistic lock) và **ghi 1 dòng sổ cái bất biến** (`dtb_wallet_transactions`).

```
Nạp tiền (IPN OK): available += amount        | ledger TOPUP
Bắt đầu job 3D   : available -= giá; held += giá   | hold HELD + ledger HOLD
Job thành công   : held -= giá                | hold CAPTURED + ledger CAPTURE
Job thất bại/huỷ : held -= giá; available += giá   | hold RELEASED + ledger RELEASE
```

`WalletService.placeHold / capture / release / credit` là các primitive này. Đơn giá lấy từ
`mtb_pricing` qua `PricingService.priceFor(taskType)`.

## Endpoint

| Method | Path | Bảo vệ | Mô tả |
|--------|------|--------|-------|
| GET  | `/api/user/wallet/balance` | Bearer (USER) | Số dư ví |
| GET  | `/api/user/wallet/transactions` | Bearer | Sổ cái (phân trang) |
| GET  | `/api/user/wallet/holds` | Bearer | Danh sách hold |
| POST | `/api/user/wallet/topup` | Bearer | Tạo đơn nạp → trả `payUrl` (VNPay/MoMo) |
| GET  | `/api/common/payments/vnpay/ipn` | public (HMAC) | IPN VNPay (server-to-server) |
| POST | `/api/common/payments/momo/ipn` | public (HMAC) | IPN MoMo (server-to-server) |

## Luồng nạp tiền
1. Client gọi `POST /wallet/topup {amount, provider}` → backend tạo `dtb_payment_orders` (PENDING),
   trả `payUrl`.
2. Client mở `payUrl` → thanh toán trên cổng.
3. Cổng gọi **IPN** về backend. Backend **verify chữ ký HMAC** (VNPay: SHA512, MoMo: SHA256),
   kiểm tra số tiền & idempotent (đơn đã SUCCEEDED thì bỏ qua) → nếu OK: đổi đơn SUCCEEDED và
   **cộng ví** (`credit`, ledger TOPUP). Mọi callback lưu thô tại `dtb_payment_callbacks`.

## An toàn
- Chữ ký HMAC verify bằng so sánh **constant-time**. Không bao giờ cộng tiền nếu chữ ký sai.
- Cộng tiền **idempotent** theo trạng thái đơn → IPN gửi lại nhiều lần không cộng trùng.
- Số dư có `CHECK >= 0` ở DB + optimistic `@Version` + pessimistic lock khi mutate.
- IPN endpoint public nhưng chỉ tin khi chữ ký hợp lệ; số tiền phải khớp đơn.

## Cấu hình (`.env`)
VNPay: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` (lấy ở sandbox vnpayment.vn).
MoMo: `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY` (test-payment.momo.vn).
Giá mỗi loại job: bảng `mtb_pricing` (seed sẵn; sửa qua DB hoặc module admin sau).

## Đã gắn vào luồng tạo 3D (Meshy x Wallet)
- Trước khi gọi MeshyAI: `beginBilling(type)` → `placeHold(giá)` (giá lấy từ `mtb_pricing`; loại
  không có giá thì miễn phí). Nếu submit lỗi → `abortBilling` (release) + cả transaction rollback.
- Lưu `user_id` + `hold_id` lên `dtb_meshy_tasks`.
- Webhook/poller đều đi qua `applyRemoteState`: khi job lần đầu sang **SUCCEEDED → capture**,
  **FAILED/CANCELED → release**. Idempotent: pre-check trạng thái hold (tránh poison transaction).
- `HoldExpirySweeper` (60s) tự release hold `HELD` quá hạn (job treo/bỏ dở).
- Endpoint tạo 3D `/api/common/3d/**` nay **yêu cầu đăng nhập** (SecurityConfig).

## Chưa làm (bước sau)
- `GET /api/common/3d/tasks` hiện liệt kê **mọi** task — nên lọc theo `user_id` của người đăng nhập
  (thêm `findByUserId` + sửa controller) để tránh lộ task của người khác.
- Đưa rate-limit tạo đơn nạp + blacklist JWT sang Redis.
- Trang admin chỉnh `mtb_pricing`.

> Môi trường này chỉ có Java 11 nên chưa biên dịch tự động. Chạy `mvn -q clean compile` (Java 21) để xác nhận.
