# InnerStyle — Deploy lên OVH Cloud VPS (Docker Compose)

Deploy 1 host: **Postgres + Redis + Spring Boot backend + nginx (SPA + proxy /api)**.
nginx phục vụ SPA React đã build và reverse-proxy `/api`, `/actuator`, `/swagger-ui` sang backend,
nên trình duyệt chỉ nói chuyện với 1 origin (không CORS, forward IP thật cho rate limiter).

```
Internet ─▶ :80/:443 nginx (frontend) ─┬─ static SPA (/, /login, /wallet, …)
                                        └─ /api/** ─▶ backend:2207 ─┬─ postgres:5432
                                                                    └─ redis:6379
```

> OVH VPS = 1 máy Ubuntu thuần. Toàn bộ stack Docker Compose sẵn có chạy y nguyên.
> Khác EC2 ở 3 điểm: **firewall dùng `ufw`** (OVH không có Security Group), **DNS/tên miền**, và **TLS**.

---

## 1. Chọn cấu hình VPS

| Hạng mục | Khuyến nghị | Ghi chú |
|---|---|---|
| Gói | OVH **VPS ≥ 2 vCPU / 4 GB RAM** (VLE-4 hoặc tương đương) | Build Maven (Java 21) + `npm build` (three.js) ngốn RAM. 2 GB dễ OOM khi build. |
| OS | **Ubuntu 24.04 LTS** (hoặc 22.04) | Debian 12 cũng chạy được. |
| Disk | ≥ 40 GB | Image Docker + Postgres data + build cache. |
| RAM ít (2 GB) | Build image ở máy local rồi push registry | Xem mục 8. |

Sau khi tạo VPS, OVH gửi IP public + hostname mặc định (`vpsXXXXXX.ovh.net`) và mật khẩu root qua email.

---

## 2. Đăng nhập & tạo user (chạy 1 lần)

```bash
ssh ubuntu@<VPS_IP>        # hoặc ssh debian@... / root@... tùy image OVH
# (nếu login root, tạo user thường để không chạy Docker bằng root):
adduser deploy && usermod -aG sudo deploy
```

Khuyến nghị bật SSH key và tắt password login (`/etc/ssh/sshd_config`: `PasswordAuthentication no`).

---

## 3. Firewall — `ufw` (quan trọng với OVH)

OVH **không có Security Group** như AWS. Mặc định VPS mở hết cổng → phải tự chặn bằng `ufw`.
Chỉ mở **22, 80, 443**. Tuyệt đối **không** mở 5432 / 6379 / 2207 ra ngoài — chúng chỉ chạy trong Docker network nội bộ.

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

> OVH còn có "Network Firewall" + Anti-DDoS ở giao diện OVH Manager. Anti-DDoS bật tự động, không cần cấu hình.
> Network Firewall là tùy chọn — nếu dùng, nhớ vẫn cho phép 22/80/443 và **stateless** nên phải mở cả cổng ephemeral. Đa số trường hợp chỉ cần `ufw` là đủ.

---

## 4. Cài Docker

```bash
sudo apt-get update && sudo apt-get install -y ca-certificates curl git
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker   # chạy docker không cần sudo
docker compose version
```

---

## 5. Lấy code — 2 repo đặt cạnh nhau

`docker-compose.yml` build frontend từ đường dẫn tương đối `../../InnerStyle-Frontend/InnerStyle-Frontend`,
nên **bắt buộc** 2 repo nằm cùng 1 thư mục cha đúng cấu trúc:

```bash
mkdir -p ~/innerstyle && cd ~/innerstyle
git clone <BACKEND_REPO_URL>  InnerStyle-Backend
git clone <FRONTEND_REPO_URL> InnerStyle-Frontend
# Kết quả phải là:
#   ~/innerstyle/InnerStyle-Backend/InnerStyle-Backend/docker-compose.yml
#   ~/innerstyle/InnerStyle-Frontend/InnerStyle-Frontend/
```

Nếu layout repo của bạn khác, sửa `frontend.build.context` trong `docker-compose.yml` cho khớp.

---

## 6. Cấu hình `.env`

```bash
cd ~/innerstyle/InnerStyle-Backend/InnerStyle-Backend
cp .env.deploy.example .env
nano .env
```

Bắt buộc đổi trước khi chạy production:

| Biến | Đặt thành |
|---|---|
| `POSTGRES_PASSWORD` | mật khẩu mạnh (không để `change_me...`) |
| `JWT_SECRET` | chuỗi random ≥ 32 byte — `openssl rand -base64 48` |
| `FRONTEND_BASE_URL` | `https://your-domain.com` (dùng cho link verify email / reset) |
| `MESHY_API_KEY`, `MESHY_WEBHOOK_SECRET` | key MeshyAI thật |
| `PAYOS_*` | thông tin merchant; đổi `*_RETURN_URL` / `*_CANCEL_URL` / `*_IPN_URL` sang domain thật (mục 9) |
| `GOOGLE_CLIENT_ID`, `FACEBOOK_APP_ID` + `VITE_*` | nếu bật social login (giá trị Google/Facebook dùng chung backend & frontend) |

> `.env.deploy.example` còn ghi `YOUR_EC2_PUBLIC_DNS` — thay hết bằng domain OVH của bạn.

---

## 7. Build & chạy

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend     # xem Flyway migrate + khởi động
```

- Flyway tự tạo bảng + seed roles/pricing ở lần boot đầu.
- Mở `http://<VPS_IP>/` để test app, `…/swagger-ui.html` xem API docs.
- Redis fail-open: Redis chết thì auth/API vẫn chạy (mất cache + rate limit).

---

## 8. (Tùy chọn) VPS RAM thấp — build ở nơi khác

Nếu build trên VPS 2 GB bị OOM (Maven/`npm build`), build ở máy local hoặc CI rồi push image lên registry (Docker Hub / GHCR / OVH Managed Private Registry), trên VPS chỉ `pull` và `up`:

```bash
# local: build & push
docker build -t <registry>/innerstyle-backend:latest  ./InnerStyle-Backend/InnerStyle-Backend
docker build -t <registry>/innerstyle-frontend:latest ./InnerStyle-Frontend/InnerStyle-Frontend \
  --build-arg VITE_GOOGLE_CLIENT_ID=... --build-arg VITE_FACEBOOK_APP_ID=... # + các VITE_ khác (xem cảnh báo dưới)
docker push <registry>/innerstyle-backend:latest && docker push <registry>/innerstyle-frontend:latest
```

Trên VPS sửa `docker-compose.yml` từ `build:` sang `image: <registry>/...:latest`, rồi `docker compose pull && up -d`.

---

## 9. Tên miền + HTTPS (khuyến nghị mạnh)

### 9.1 Trỏ domain
Tạo bản ghi **A** trỏ `your-domain.com` (và `www`) về IP VPS. Nếu domain ở OVH: **OVH Manager → Web Cloud → Domain → DNS zone**.
Có thể set Reverse DNS trong trang VPS cho đẹp mail/log.

### 9.2 TLS
Frontend container đang chiếm cổng 80 trực tiếp. Cách gọn nhất: **đổi frontend chỉ nghe nội bộ, đặt nginx + certbot của host phía trước**.

Sửa `docker-compose.yml` — service `frontend`:
```yaml
    ports:
      - "127.0.0.1:8080:80"   # thay cho "80:80"
```
Rồi trên host:
```bash
sudo apt-get install -y nginx certbot python3-certbot-nginx
```
`/etc/nginx/sites-available/innerstyle`:
```nginx
server {
    listen 80;
    server_name your-domain.com www.your-domain.com;
    client_max_body_size 80m;                 # khớp giới hạn upload multi-image
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```
```bash
sudo ln -s /etc/nginx/sites-available/innerstyle /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d your-domain.com -d www.your-domain.com   # tự cấp + tự gia hạn Let's Encrypt
docker compose up -d   # áp lại port mapping mới
```

> Cách khác: thêm 1 container **Caddy** hoặc **Traefik** làm reverse proxy tự lo Let's Encrypt — hợp nếu muốn "all-in-docker".

Sau khi có HTTPS: `FRONTEND_BASE_URL`, các `PAYOS_*` URL đều dùng `https://...` rồi `docker compose up -d`.

---

## 10. Callback cổng thanh toán
payOS gọi thẳng vào server bạn — URL phải public (đã mở 80/443):
- payOS webhook: `https://<domain>/api/common/payments/payos/webhook`

Đặt đúng các URL này trong dashboard payOS. **Bắt buộc HTTPS ở production.**

---

## 11. Vận hành

```bash
git -C ~/innerstyle/InnerStyle-Backend/InnerStyle-Backend pull
docker compose up -d --build                                   # cập nhật
docker compose logs -f backend                                 # log
docker compose exec postgres pg_dump -U innerstyle innerstyle > backup_$(date +%F).sql   # backup DB
docker compose down                                            # dừng (giữ data)
docker compose down -v                                         # dừng + XÓA data (cẩn thận!)
```

Nên bật **Automated Backup / Snapshot** của OVH cho VPS + cron `pg_dump` hằng ngày ra nơi khác.

---

## ⚠️ Cần lưu ý trong codebase (kiểm tra ra được)

1. **Thiếu build-arg cho Maps ở compose.** Service `frontend` trong `docker-compose.yml` chỉ truyền
   `VITE_API_BASE_URL`, `VITE_GOOGLE_CLIENT_ID`, `VITE_FACEBOOK_APP_ID`. Các key
   `VITE_GOOGLE_MAPS_API_KEY`, `VITE_GOONG_API_KEY`, `VITE_GOONG_MAPTILES_KEY` **không được truyền** →
   picker địa chỉ/bản đồ ở form giao hàng sẽ fallback OSM (hoặc tắt). Nếu cần bản đồ Goong/Google,
   thêm chúng vào `frontend.build.args` (và `Dockerfile` frontend đã có sẵn `ARG` tương ứng chưa thì bổ sung).

2. **Key Goong đang commit trong `InnerStyle-Frontend/.env`.** Đây là key client-side (embed vào bundle)
   nên không phải secret chết người, nhưng nên **restrict theo domain** trong dashboard Goong và không để lộ
   key không giới hạn. `.env` production nên nằm ngoài git.

3. **Email:** kiểm tra đang dùng `LoggingEmailSender` hay Resend/SMTP thật. Production cần provider thật
   để gửi email verify/reset (link dựng từ `FRONTEND_BASE_URL`).

4. **CORS:** production siết `APP_CORS_ALLOWED_ORIGIN_PATTERNS` về đúng domain (mặc định dev khá thoáng).

5. **Durability:** in-container Postgres/Redis ổn cho 1 host. Nếu cần bền/scale, cân nhắc DB quản lý riêng
   (OVH Managed Databases) rồi trỏ `DATABASE_URL`/`REDIS_HOST` sang, bỏ 2 service đó khỏi compose.

---

## Checklist trước khi go-live
- [ ] `JWT_SECRET`, `POSTGRES_PASSWORD` đã đổi, `.env` không commit.
- [ ] `ufw` chỉ mở 22/80/443; 5432/6379/2207 không lộ.
- [ ] HTTPS hoạt động; `FRONTEND_BASE_URL` + URL cổng thanh toán đều `https://`.
- [ ] Webhook payOS cấu hình đúng và gọi được từ internet.
- [ ] Maps build-arg (nếu dùng) đã truyền; key Goong đã restrict domain.
- [ ] Có backup: OVH snapshot + `pg_dump` định kỳ.
- [ ] SMTP/Resend thật đã cấu hình; CORS đã siết.
