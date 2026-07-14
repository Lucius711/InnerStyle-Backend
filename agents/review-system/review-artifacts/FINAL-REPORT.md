# InnerStyle Full-Repository Review — 2026-07-14

Produced by running the review system defined in `../00-orchestrator.md` across both repositories.
This was a **risk-weighted execution**: the crown-jewel subsystems (auth, payments, database,
concurrency, external integration, frontend token/i18n) were read at source level; the remainder
were assessed at directory/grep level. Files actually inspected are listed in `manifest.json`.

## Executive summary

| Severity | Count |
|----------|-------|
| 🔴 CRITICAL | 2 |
| 🟠 MAJOR | 6 |
| 🟡 MINOR | 6 |
| ⚪ NIT | 2 |
| **Total** | **16** |

**Go / no-go:** 🔴 **No-go for production** until C1 and C2 are fixed. Both are exploitable in
normal operation (C1 by ordinary gateway retries, C2 by anyone who reads the public default secret).

**Top 5 risks**
1. **C1 — Double-fulfillment of payments** under concurrent/retried callbacks (no lock, no `@Version`, no DB idempotency key).
2. **C2 — Forgeable JWTs** because the signing secret defaults to a public placeholder that `.env` still uses.
3. **M1 — Unauthenticated PUT** lets anyone overwrite a task's AR (USDZ) asset.
4. **M3 — Refresh token in localStorage** turns any XSS into 7-day account takeover.
5. **M6 — No CI/CD** means none of the above would be caught automatically before shipping.

The encouraging counter-picture: JWT parsing enforces issuer + ≥32-byte key + revocation blacklist;
ownership checks are consistently applied through the service layer for every non-public Meshy
endpoint; gateway signatures use a constant-time compare; the Meshy HTTP client is properly
timed-out; money columns are exact `NUMERIC(15,2)` with `CHECK (>= 0)` and an optimistic `version`
on wallets. The failures are concentrated, not systemic.

---

## Critical findings

### C1 — Payment settlement is a non-atomic check-then-act (double-fulfillment)
- **Where:** `wallet/service/impl/PaymentServiceImpl.java:101` (also 119, 144); `wallet/entity/PaymentOrder.java` (no `@Version`); `db/migration/…131000__create_wallet_payment_tables.sql:104` (no unique callback key).
- **Reported by:** R06 + R07 + R04 (composite / correlated).
- **Why:** duplicate defense is only `if (order.getStatus()==SUCCEEDED) return; else settle()`. No `SELECT … FOR UPDATE`, no `@Version` on the order, no DB unique constraint on `(provider, provider_txn_ref)`. Two concurrent callbacks (VNPay IPN + browser return, or a MoMo IPN retry) both read `PENDING`, both pass, both `settle()`.
- **Impact:** one payment activates a plan / grants credits / marks a print order paid **twice**. Gateways retry IPNs as a matter of course, so this triggers without an attacker.
- **Fix:** add `UNIQUE(provider, provider_txn_ref)` on the callback (or order) row **and** lock the order (`FOR UPDATE` or `@Version`) then re-check status inside the locked transaction before settling. Prefer DB-enforced idempotency over the in-memory status check.

### C2 — JWT signing secret defaults to a public placeholder
- **Where:** `src/main/resources/application.yml:90` (`app.jwt.secret` default `change-me-please-…`); the committed dev `.env` sets `JWT_SECRET=change-m…` (the same value).
- **Reported by:** R05 + R16.
- **Why:** the placeholder is ≥32 bytes, so `Keys.hmacShaKeyFor` accepts it and the app runs. The string is in the repo, so it is public.
- **Impact:** anyone can forge an access token with arbitrary `roles` → full auth bypass / privilege escalation in any environment running the default (dev now; prod if the env var is ever unset).
- **Fix:** refuse to boot in non-dev if the secret is missing or equals the placeholder; use a unique random secret per environment; rotate immediately if the default was ever deployed.

---

## Major findings

| ID | Title | Location | Reported by |
|----|-------|----------|-------------|
| M1 | Public unauthenticated `PUT …/tasks/{id}/usdz` overwrites any task's AR asset | `meshy/controller/MeshyController.java:270` | R05 |
| M2 | CORS `allowCredentials(true)` with `*.vercel.app` / `localhost:*` patterns | `auth/config/SecurityConfig.java:118` | R05 |
| M3 | Access **and** refresh token in `localStorage` (7-day refresh) | `frontend src/lib/http.js:9` | R05, R11 |
| M4 | MoMo gateway `RestClient.create()` has no timeout | `wallet/gateway/MomoGateway.java:30` | R08 |
| M5 | Rate limiter fails **open** on Redis errors incl. login/payment | `redis/ratelimit/RateLimiterService.java:46` | R07, R05 |
| M6 | No CI/CD pipeline (no `.github/workflows`) | repo root, both | R16, R15 |

Details for each are in `findings.json` (impact + concrete fix). Highlights:
- **M1** is the one Meshy endpoint that skips the otherwise-consistent ownership guard — fix by requiring auth+ownership or writing the USDZ server-side.
- **M4** contrasts with the correctly-configured Meshy client (10s/60s) — mirror that config for MoMo.
- **M5** should be split into fail-open (benign reads) vs fail-closed/local-fallback (login, OTP, payment).

---

## Minor findings

| ID | Title | Location | Reported by |
|----|-------|----------|-------------|
| m1 | `dtb_payment_callbacks` lacks a unique constraint (hardens C1) | migration …131000:104 | R04 |
| m2 | ~40 `vite.config.js.timestamp-*.mjs` + `dist/`/reports/scratch committed | frontend root | R16 |
| m3 | i18n en/vi key-parity gap (~2 keys) | `src/locales/vi.js` | R13 |
| m4 | Payment/auth/concurrency paths untested; no coverage gate | `src/test`, e2e | R15 |
| m5 | Redis SSL enabled vs localhost + empty password | `application.yml:28` | R16, R07 |
| m6 | No metrics/tracing for a payment + external-API system | `application.yml` | R09 |

---

## Nits

| ID | Title | Location |
|----|-------|----------|
| n1 | VNPay signature compare is case-sensitive hex | `wallet/gateway/CryptoSigner.java:32` |
| n2 | Pricing/fee literals duplicated across yml + DB + FE | `application.yml`, `mtb_pricing`, `pricing.js` |

---

## Composite / correlated findings

- **C1** is itself a correlation the orchestrator predicted in `00-orchestrator.md` §3: R04's
  "missing unique constraint" (m1) + R06's "check-then-act settlement" + R07's "no lock" combine
  into a single duplicate-charge CRITICAL rather than three separate MINORs. Fixing **m1** (DB
  unique key) is the cheapest structural mitigation for C1.

---

## Coverage confirmation

Every top-level path in both repos has a named owner — see [`../COVERAGE.md`](../COVERAGE.md).
0 unowned paths. This run inspected the high-risk owners at source level; the following were
assessed only at directory level and are the recommended next deep-dive targets:
R02 (full endpoint inventory), R03 (meshy/print state machines), R12 (Three.js resource disposal),
R14 (per-route SEO vs `SEO_AUDIT.md`).

---

## Per-subsystem appendix

- **Auth / security (R05):** strong core (issuer + key-length + blacklist + BCrypt + consistent
  ownership). Weaknesses are the default secret (C2), the public USDZ write (M1), CORS breadth (M2),
  and FE token storage (M3).
- **Payments / wallet (R06):** correct server-side amount validation and app-level idempotency
  intent, undermined by the missing lock/constraint (C1, m1). Hold model (`dtb_holds`) is
  well-designed with CHECK constraints.
- **Database (R04):** clean money types, good indexing and CHECKs; gap is idempotency uniqueness (m1).
- **Concurrency / Redis (R07):** fail-open policy too broad (M5); payment race (C1).
- **Integration (R08):** Meshy client exemplary (timeouts, error mapping); MoMo client not (M4).
- **DevOps (R16):** Docker/nginx/Vercel present; missing CI (M6) and repo hygiene (m2).
- **Frontend (R11/R13):** token storage (M3) and i18n parity (m3); deeper 3D-disposal (R12) and
  SEO (R14) passes still pending.
- **Testing (R15):** the biggest structural gap — critical paths lack regression tests (m4), and
  nothing runs automatically (M6).

*Severity rubric and finding schema: see `../README.md` §3–§4.*
