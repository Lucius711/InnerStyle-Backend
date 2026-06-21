# Test VNPay / MoMo locally (public URL via Cloudflare Tunnel)

VNPay/MoMo need a **public URL** — they can't call `localhost`. The `scripts/tunnel.ps1` helper
creates one with **cloudflared** (no signup) and writes the URLs into `.env` for you.

## 1. Start your app
- Docker (recommended): `docker compose up -d --build`  (nginx on port 80)
- or local dev: `mvn spring-boot:run` (2207) + `npm run dev` (5173)

## 2. Run the tunnel
From the backend folder (`InnerStyle-Backend/InnerStyle-Backend`) in PowerShell:
```powershell
# docker mode (port 80):
powershell -ExecutionPolicy Bypass -File scripts\tunnel.ps1
# local backend only:
powershell -ExecutionPolicy Bypass -File scripts\tunnel.ps1 -Port 2207
```
It prints something like `https://random-name.trycloudflare.com` and updates `.env`:
`FRONTEND_BASE_URL`, `VNPAY_RETURN_URL`, `MOMO_REDIRECT_URL`, `MOMO_IPN_URL`.

## 3. Register the IPN URL
- **VNPay portal** → IPN URL: `https://<tunnel>/api/common/payments/vnpay/ipn`
- (MoMo IPN is sent automatically in each request — `MOMO_IPN_URL` above.)
- For VNPay registration's "website URL" field, you can paste the tunnel URL (or any public URL —
  it's informational; only the runtime IPN/Return URLs must be public).

## 4. Restart backend to pick up the new URLs
```powershell
docker compose up -d backend     # docker mode
# or stop & re-run mvn spring-boot:run for local mode
```

## 5. Set your gateway keys in `.env`
```
VNPAY_TMN_CODE=...        # from https://sandbox.vnpayment.vn/devreg/
VNPAY_HASH_SECRET=...
MOMO_PARTNER_CODE=...     # from https://developers.momo.vn (test env)
MOMO_ACCESS_KEY=...
MOMO_SECRET_KEY=...
```

## Notes
- cloudflared (and ngrok) generate a **new URL each run** — re-run the script and restart the
  backend when it changes.
- Keep the tunnel window open; closing it drops the public URL.
- In production (EC2 + domain), you don't need this — use your real domain in the URLs.
