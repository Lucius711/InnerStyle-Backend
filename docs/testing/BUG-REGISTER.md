# Bug / Risk Register — InnerStyle

Findings from the end-to-end QA + static-analysis pass. Severity per the tester severity matrix.
"Fixed" items were changed in this pass; "Documented" items are recommendations with a proposed fix.

---

## BUG-001 — Overly permissive MVC-level CORS overrides the security allow-list
- **Severity:** Medium (Security)
- **Area:** `config/ApiPrefixConfig.java` vs `auth/config/SecurityConfig.java`
- **Problem:** `ApiPrefixConfig.addCorsMappings` registered a global CORS mapping with
  `allowedOriginPatterns("*")`, `allowedHeaders("*")`, `exposedHeaders("*")` for `/**`. This runs
  in parallel with the curated, env-overridable allow-list in
  `SecurityConfig.corsConfigurationSource()`. The wildcard MVC mapping effectively lets **any**
  origin call the API, silently defeating the security-layer allow-list — a real risk in production.
- **Reproduction:** In prod config, send a cross-origin request with an `Origin` not in
  `APP_CORS_ALLOWED_ORIGIN_PATTERNS`; the MVC mapping still echoes an allow header.
- **Fix (applied):** Removed the `addCorsMappings` override. CORS is now owned solely by
  `SecurityConfig` (which already registers `/**` and includes localhost/ngrok patterns for dev).
- **Regression risk:** Low. Dev origins (`localhost:*`, ngrok) remain in the security allow-list.
- **Test:** TC-SEC-007.

## BUG-002 — Email enumeration on `/verify-email`
- **Severity:** Low (Security)
- **Area:** `auth/service/impl/AuthServiceImpl.verifyEmail`
- **Problem:** The endpoint returns distinct codes: `auth.verification.alreadyVerified` for a known
  + verified address vs `auth.verification.invalid` for an unknown address. An attacker can probe
  which emails are registered and verified.
- **Trade-off:** The distinct `alreadyVerified` message is good UX for a legitimate user who taps an
  old link/tab. Enumeration risk here is low because registration already reveals existence via
  `auth.emailExists` (409) on `/register`, and the email endpoints are rate-limited
  (`email-per-10min: 3`).
- **Recommended fix (not applied — product decision):** If strict anti-enumeration is required,
  collapse `alreadyVerified`/unknown/no-token into a single generic `auth.verification.invalid`,
  and rely on the login flow to guide already-verified users.
- **Test:** TC-AUTH-024, TC-AUTH-025.

## BUG-003 — OTP length misconfiguration fails at request time (500) instead of at startup
- **Severity:** Low
- **Area:** `auth/security/TokenHasher.generateOtp` + `auth/config/AuthProperties.otpLength`
- **Problem:** `generateOtp` throws `IllegalArgumentException` when `otp-length` is outside 4..9.
  Because the value is only used during `register`, a bad `EMAIL_OTP_LENGTH` env value produces a
  500 on the first registration rather than failing fast at boot.
- **Recommended fix:** Add `@Min(4) @Max(9)` (validated `@ConfigurationProperties`) on
  `otpLength`, or a `@PostConstruct` assertion, so misconfiguration fails at startup.
- **Test:** TC-AUTH-029 (boundary), config validation.

## BUG-004 — Lazy monthly renewal on the read path lacks a pessimistic lock
- **Severity:** Low (Concurrency)
- **Area:** `membership/service/impl/CreditServiceImpl.getOrCreateMembership → renewIfNeeded`
- **Problem:** `getOrCreateMembership` renews (resets credits + appends a RENEWAL ledger row)
  without taking the pessimistic lock that `consume`/`refund`/`activatePlan` use via
  `lockAndRenew`. Two concurrent reads immediately after a period boundary could both renew,
  double-granting credits / writing duplicate RENEWAL rows.
- **Recommended fix:** Route the read-path renewal through `findByIdForUpdate` (as the mutation
  paths do), or guard renewal with an optimistic `@Version` check (the entity already has a
  `version` field) and make the RENEWAL ledger idempotent per period.
- **Test:** TC-CRD-009, TC-MEM-003.

## BUG-005 — Password reset still uses an emailed link, not an OTP
- **Severity:** Info (By design)
- **Area:** `auth/service/impl/AuthServiceImpl.forgotPassword`
- **Note:** The prior task migrated only **registration** verification to OTP. Password reset
  intentionally still emails a tokenized link (`/reset-password?token=`). Flagged for awareness in
  case the product wants OTP parity across both flows.

---

## Verified-correct behaviours (no defect)
These were audited and found correct; tests lock them in to prevent regressions:
- Payment settlement is **not** performed on invalid signature or amount mismatch (payOS/MoMo).
- Payment fulfilment is **idempotent** by order status (no double credit/activation).
- Print order amount is derived **server-side** from `PrintProperties`, never from the client.
- Login does not leak account existence (same `auth.invalidCredentials` for unknown vs wrong pw).
- Only SHA-256 hashes of OTP / reset / refresh tokens are persisted, never the raw value.
- Provider strings on subscribe/print are `@Pattern`-validated → bad values give 400, not 500.
- Global handler maps unhandled exceptions to `500 common.serverError` with no stack-trace leak.
