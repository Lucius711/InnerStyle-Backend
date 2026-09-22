# Test payOS locally (public URL via Cloudflare Tunnel)

payOS needs a **public URL** — it can't call `localhost`. The `scripts/tunnel.ps1` helper
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
`FRONTEND_BASE_URL`, `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL`.

## 3. Register the webhook URL
- **payOS dashboard** (https://my.payos.vn) → Webhook URL:
  `https://<tunnel>/api/common/payments/payos/webhook`
  payOS pings this URL once to confirm it's reachable before saving it.

## 4. Restart backend to pick up the new URLs
```powershell
docker compose up -d backend     # docker mode
# or stop & re-run mvn spring-boot:run for local mode
```

## 5. Set your gateway keys in `.env`
```
PAYOS_CLIENT_ID=...       # from https://my.payos.vn (Payment channel → API keys)
PAYOS_API_KEY=...
PAYOS_CHECKSUM_KEY=...
```

## Notes
- cloudflared (and ngrok) generate a **new URL each run** — re-run the script, restart the
  backend, and re-save the payOS webhook URL when it changes.
- Keep the tunnel window open; closing it drops the public URL.
- In production (EC2 + domain), you don't need this — use your real domain in the URLs.
