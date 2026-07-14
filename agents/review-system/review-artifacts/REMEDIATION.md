# Remediation — fixes applied for FINAL-REPORT.md findings (2026-07-14)

All 16 findings were addressed. Backend paths are under `InnerStyle-Backend/`, frontend under
`InnerStyle-Frontend/`. Verification note: the sandbox build mount was out of sync with the edits,
so changes were verified by code review + updating the affected unit tests; the newly added CI
(M6) runs the real `mvn verify` / `vite build` gate.

| ID | Status | Change |
|----|--------|--------|
| **C1** | Fixed | New migration `V20260714120000__payment_order_version_and_idempotency.sql` adds a `version` column + partial `UNIQUE(provider, provider_txn_ref)`. `PaymentOrder` gains `@Version`. New `PaymentOrderRepository.findByOrderCodeForUpdate` (PESSIMISTIC_WRITE). `PaymentServiceImpl` now loads the order under lock (`lockOrder`) in all three callback handlers, so concurrent/retried callbacks serialize and cannot double-settle. |
| **C2** | Fixed | `JwtService` constructor now takes `Environment` and fails fast (throws) outside dev/test when the secret is blank or the public placeholder; warns in dev. |
| **M1** | Fixed | `MeshyController.cacheTaskUsdz` now requires `@AuthenticationPrincipal`; `storeUsdz(id, userId, data)` enforces ownership (404 on mismatch). `SecurityConfig` no longer permits public PUT `.../usdz`. Frontend `ar.js` uploads via `authedFetch(..., auth:true)`. |
| **M2** | Fixed | `application.yml` CORS default narrowed to explicit known origins (removed `http://localhost:*`, `https://*.vercel.app`, ngrok wildcards); extra origins added per-env via `APP_CORS_ALLOWED_ORIGIN_PATTERNS`. |
| **M3** | Fixed | Refresh token now delivered as an HttpOnly, path-scoped cookie. `AuthController` sets/reads/clears it (body fallback for non-browser clients); secure/SameSite env-driven. Frontend `http.js` stops storing the refresh token in localStorage, sends `credentials:"include"`, refreshes via cookie; `authApi.logout` no longer reads it. |
| **M4** | Fixed | `MomoGateway` RestClient built with explicit 5s connect / 10s read timeouts. |
| **M5** | Fixed | `RateLimiterService.check(..., failClosed)` overload; `RateLimitFilter` marks login/register/email-OTP/payment buckets fail-CLOSED, benign API buckets fail-OPEN. |
| **M6** | Fixed | Added `.github/workflows/backend-ci.yml` (JDK 21 `mvn verify`) and `frontend-ci.yml` (build + locale parity + Playwright e2e). |
| **m1** | Fixed | Partial `UNIQUE(provider, provider_txn_ref)` added in the C1 migration (DB-level replay defence). |
| **m2** | Fixed (script) | `.gitignore` updated (playwright-report, test-results, `vite.config.js.timestamp-*.mjs`, scratch scripts) + `scripts/clean-repo-artifacts.sh` to untrack/delete the 56 committed timestamp files (run locally — the sandbox mount is read-only for deletes). |
| **m3** | Corrected + guarded | Deep key diff shows en/vi are actually at parity (607 keys each) — the reported ~2-key gap was blank-line noise. Added `scripts/check-locale-parity.mjs` (+ `npm run check:locales`, wired into CI) to prevent future drift. |
| **m4** | Addressed | Expanded the risk-path unit suite (see below): payment settlement (VNPay + MoMo idempotency/signature/amount, PRINT fulfilment), JWT round-trip + C2 fail-fast, rate-limiter fail-open/closed, and signature comparison. A coverage *threshold* in the build is the remaining follow-up. |
| **m5** | Fixed | Redis `ssl.enabled` is now `${REDIS_SSL_ENABLED:false}` (env-driven; on for managed/prod). |
| **m6** | Fixed | Added `CorrelationIdFilter` (request id → MDC + `X-Request-Id`), log pattern includes `%X{requestId}`, actuator exposes `health,info,metrics,prometheus`. |
| **n1** | Fixed | `CryptoSigner.matches` compares hex case-insensitively (still constant-time). |
| **n2** | Fixed | New public `GET /api/common/print/pricing` (`PrintPricingController` + `PrintPricingResponse`) serves prices from the single `PrintProperties` source; frontend `pricing.js` adds `loadPrintPricing()` fetching it, constant demoted to fallback. |

## Tests updated / added
- `MeshyTaskServiceImplTest`: `storeUsdz` calls updated to the 3-arg signature; added `storeUsdzRejectsNonOwner` (M1).
- `PaymentServiceImplTest`: callback stubs switched to `findByOrderCodeForUpdate` (new locking path, C1); added 4 MoMo IPN tests (success/bad-signature/idempotent/amount-mismatch) and a VNPay PRINT-purpose fulfilment test.
- `JwtServiceTest` (new): token round-trip (subject/roles/email), tamper rejection, and C2 boot fail-fast on default/blank secret in prod vs dev.
- `RateLimiterServiceTest` (new): under/over limit, and Redis-error fail-OPEN (benign) vs fail-CLOSED (sensitive) — M5.
- `CryptoSignerTest` (new): deterministic HMAC and case-insensitive, null-safe, length-checked `matches` — n1.

Note: the sandbox here runs JDK 11 with no Maven, so these were verified by inspection; run `mvn verify` locally or let the new backend CI (JDK 21) execute them.

## Follow-ups (not code-fixable here)
- Run `InnerStyle-Frontend/scripts/clean-repo-artifacts.sh` and commit to physically drop the 56 tracked `vite.config.js.timestamp-*.mjs` files.
- Set a real `JWT_SECRET`, `REDIS_SSL_ENABLED=true`, and `app.auth.refresh-cookie.secure=true` (+ `same-site=None` if the API and frontend are cross-site) in production env.
- Expand the automated test suite for the payment/auth/concurrency paths and add a coverage threshold (m4).
