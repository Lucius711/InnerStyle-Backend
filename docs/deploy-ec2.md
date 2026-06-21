# InnerStyle — Deploy to AWS EC2 (Docker Compose)

Single-host deployment: **Postgres + Redis + Spring Boot backend + nginx (SPA + API proxy)**.
nginx serves the built React SPA and reverse-proxies `/api`, `/actuator`, `/swagger-ui` to the
backend, so the browser talks to one origin (no CORS issues, real client IP forwarded for rate limiting).

```
Internet ──▶ :80 nginx (frontend) ──┬─ static SPA (/, /login, /wallet, …)
                                     └─ /api/** ─▶ backend:2207 ─┬─ postgres:5432
                                                                 └─ redis:6379
```

## 1. Prerequisites
- An EC2 instance (Ubuntu 22.04/24.04, `t3.small`+ recommended — 2 GB RAM min for the Maven build,
  or build locally and push images).
- Security group inbound: **80** (HTTP), **443** (HTTPS, if using TLS), **22** (SSH). Do NOT expose
  5432 / 6379 / 2207 publicly — they stay on the internal Docker network.
- The two repos cloned **side by side** under a common parent:
  ```
  ~/innerstyle/InnerStyle-Backend/InnerStyle-Backend   (docker-compose.yml lives here)
  ~/innerstyle/InnerStyle-Frontend/InnerStyle-Frontend
  ```

## 2. Install Docker
```bash
sudo apt-get update && sudo apt-get install -y ca-certificates curl
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker   # run docker without sudo
```

## 3. Configure
```bash
cd ~/innerstyle/InnerStyle-Backend/InnerStyle-Backend
cp .env.deploy.example .env
nano .env     # set POSTGRES_PASSWORD, JWT_SECRET, FRONTEND_BASE_URL=http://<EC2 public DNS>,
              # MESHY_API_KEY, VNPAY_*, MOMO_*  (use your EC2 DNS in the return/ipn URLs)
```

## 4. Build & run
```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend     # watch Flyway migrations + startup
```
- Flyway runs all migrations on first boot (creates tables + seeds roles & pricing).
- Open `http://<EC2 public DNS>/` for the app, `…/swagger-ui.html` for the API docs.

## 5. Payment gateway callbacks
VNPay / MoMo call your server directly. In each dashboard set the IPN/return URLs to your public host:
- VNPay IPN: `http://<host>/api/common/payments/vnpay/ipn`
- MoMo IPN:  `http://<host>/api/common/payments/momo/ipn`
These must be reachable from the internet (port 80/443 open). Use **HTTPS in production**.

## 6. HTTPS (recommended)
Put a TLS terminator in front (simplest options):
- **Caddy** or **Traefik** as a reverse proxy with automatic Let's Encrypt, or
- nginx + certbot on the host, proxying to the `frontend` container.
Then update `FRONTEND_BASE_URL` and gateway URLs to `https://...`.

## 7. Operations
```bash
docker compose pull && docker compose up -d --build   # update
docker compose logs -f backend                         # logs
docker compose exec postgres pg_dump -U innerstyle innerstyle > backup.sql   # DB backup
docker compose down                                    # stop (keeps volumes/data)
docker compose down -v                                 # stop + DELETE data (careful)
```

## 8. Production hardening checklist
- Strong `JWT_SECRET`, `POSTGRES_PASSWORD`; never commit `.env`.
- Use **HTTPS** (gateways and email links should be https).
- Consider **AWS RDS (Postgres)** + **ElastiCache (Redis)** instead of in-container DB/cache for
  durability and backups; point `DATABASE_URL` / `REDIS_HOST` at them and drop those services.
- Set a real SMTP provider (replace `LoggingEmailSender`) so verification/reset emails are sent.
- Restrict CORS origins for production (currently permissive for dev).
- Put the instance behind an ALB / CloudFront if you need scaling or a CDN.

> Note: the build image compiles with Maven (Java 21). On a small instance, build locally and use a
> registry (ECR) if the on-host Maven build runs out of memory.
