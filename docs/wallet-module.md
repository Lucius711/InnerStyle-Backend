# InnerStyle — Payment module

> Cập nhật: mô hình **ví ảo giữ tiền (authorization hold)** mô tả trong các bản trước của tài
> liệu này đã bị **gỡ bỏ hoàn toàn** (migration
> `V20260620160000__remove_wallet_repurpose_payments.sql`) — lưu tiền hộ người dùng đòi hỏi giấy
> phép trung gian thanh toán/e-money tại Việt Nam. Kiến trúc hiện tại là **thanh toán trực tiếp,
> không giữ số dư**: mỗi lần thanh toán payOS tài trợ thẳng cho một mục đích cụ thể (gói
> subscription hoặc đơn in), không có `WalletService`, không có `dtb_wallet_transactions`, không
> có "nạp tiền vào ví".

## Mô hình

`PaymentOrder` (bảng `dtb_payment_orders`) là nguồn sự thật duy nhất: `purpose` (SUBSCRIPTION /
PRINT) + `reference` (id gói / id đơn in) cho biết đơn thanh toán này tài trợ cho cái gì —
không có ví ảo trung gian. Giá luôn được tính **server-side** tại thời điểm tạo đơn (từ
`MembershipPlanRepository` hoặc `PrintPricingService`), không bao giờ nhận từ client.

```
Tạo đơn : client gọi API nghiệp vụ tương ứng (subscribe / đặt in) → backend tạo
          dtb_payment_orders (PENDING, amount = giá tra server-side) → trả payUrl.
Thanh toán: cổng gọi IPN → backend verify chữ ký HMAC + khớp số tiền + khoá đơn
          (SELECT ... FOR UPDATE) → nếu hợp lệ và đơn chưa SUCCEEDED: đổi trạng thái
          SUCCEEDED rồi kích hoạt hiệu lực (activatePlan hoặc cập nhật đơn in).
```

## Endpoint

| Method | Path | Bảo vệ | Mô tả |
|--------|------|--------|-------|
| POST | `/api/user/membership/subscribe` | Bearer (USER) | Tạo đơn thanh toán gói subscription → trả `payUrl` |
| POST | `/api/user/print/orders` | Bearer (USER) | Tạo đơn in (giá server-side) → trả `payUrl` |
| POST | `/api/common/payments/payos/webhook` | public (HMAC) | Webhook payOS (server-to-server) |
| GET  | `/api/common/payments/payos/return` | public (payOS lookup) | Redirect người dùng sau khi thanh toán payOS |

## An toàn

- Chữ ký HMAC verify bằng so sánh **constant-time** (`CryptoSigner.matches`). Không bao giờ
  `settle()` nếu chữ ký sai.
- Idempotent: `PaymentOrderRepository` khoá đơn bằng `SELECT ... FOR UPDATE` trong
  `@Transactional`, cộng với early-return khi đơn đã `SUCCEEDED` — hai IPN cho cùng một đơn
  không thể cùng settle. DB có unique index từng phần trên `(provider, provider_txn_ref)`.
- Số tiền đơn được so khớp với số tiền cổng thanh toán báo về trước khi settle.
- IPN/webhook endpoint public nhưng chỉ tin khi chữ ký hợp lệ.
- payOS: `returnUrl` redirect không có chữ ký, nên `/payos/return` tra cứu trạng thái đơn trực tiếp từ payOS (`GET /v2/payment-requests/{orderCode}`) thay vì tin query param trên URL.

## Cấu hình (`.env`)
payOS: `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` (lấy ở my.payos.vn).

## Meshy 3D generation billing

3D generation không đi qua module thanh toán này — nó dùng **credit theo gói membership**
(xem `MembershipController` / `MeshyTaskServiceImpl.beginBilling`), trừ credit trực tiếp khi
tạo task và hoàn credit khi submit lỗi hoặc task thất bại. Không có hold/capture/release.
