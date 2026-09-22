# InnerStyle — End-to-End Test Case Catalog

Comprehensive test-case catalog across every backend module and frontend page.
IDs follow `TC-{MODULE}-{NNN}`. Priority: Critical / High / Medium / Low.
Type: Positive (P) / Negative (N) / Edge (E) / Security (S).

Automated coverage lives in:
- Backend unit/integration: `src/test/java/com/innerstyle/**` (JUnit 5 + Mockito + MockMvc)
- Frontend E2E: `InnerStyle-Frontend/e2e/**` (Playwright)

Legend for expected error contract: the API returns the standard envelope. Errors use
`{ success:false, error:{field:code}, errors:{field:[codes]}, message:reason }`. Field
validation codes are namespaced `validation.*`; business codes are e.g. `auth.verification.invalid`.

---

## 1. AUTH — Registration & Email OTP (`/api/user/auth`)

| TC-ID | Title | Type | Priority | Steps | Expected |
|-------|-------|------|----------|-------|----------|
| TC-AUTH-001 | Register with valid email/password/fullName | P | Critical | POST /register valid body | 201; data = profile with status PENDING_VERIFICATION; OTP issued & emailed |
| TC-AUTH-002 | Register duplicate email | N | High | POST /register with existing email | 409 `auth.emailExists` |
| TC-AUTH-003 | Register blank email | N | High | email="" | 400 `validation.email.required` |
| TC-AUTH-004 | Register invalid email format | N | High | email="not-an-email" | 400 `validation.email.invalid` |
| TC-AUTH-005 | Register email > 255 chars | E | Medium | 256-char email | 400 `validation.email.tooLong` |
| TC-AUTH-006 | Register password < 8 | N | High | password="short" | 400 `validation.password.length` |
| TC-AUTH-007 | Register password > 72 | E | Medium | 73-char password | 400 `validation.password.length` |
| TC-AUTH-008 | Register blank fullName | N | High | fullName="" | 400 `validation.fullName.required` |
| TC-AUTH-009 | Register fullName > 255 | E | Low | 256-char name | 400 `validation.fullName.tooLong` |
| TC-AUTH-010 | Register malformed JSON body | N | Medium | body="{" | 400 `validation.body.malformed` |
| TC-AUTH-011 | Register trims email/fullName | E | Low | " a@b.com "/" Huy " | Stored trimmed |
| TC-AUTH-012 | Email is case-insensitive unique | E | High | Register A@b.com then a@b.com | 409 second time |
| TC-AUTH-013 | Password stored as BCrypt hash | S | Critical | Register then inspect | password_hash != plaintext, BCrypt prefix |
| TC-AUTH-020 | Verify email with correct OTP | P | Critical | POST /verify-email {email, otp} | 200 `auth.emailVerified`; user ACTIVE, emailVerified=true |
| TC-AUTH-021 | Verify with wrong OTP | N | High | wrong otp | 400 `auth.verification.invalid`; attempt_count++ persisted |
| TC-AUTH-022 | Verify with expired OTP | E | High | otp past otp-ttl | 400 `auth.verification.expired` |
| TC-AUTH-023 | Verify after max attempts | E | High | 5 wrong then any | 400 `auth.verification.tooManyAttempts`; token burned |
| TC-AUTH-024 | Verify already-verified email | E | Medium | verify twice | 400 `auth.verification.alreadyVerified` |
| TC-AUTH-025 | Verify unknown email | N/S | Medium | email not registered | 400 `auth.verification.invalid` (see BUG-002 enumeration) |
| TC-AUTH-026 | Verify OTP non-numeric | N | Medium | otp="abcd12" | 400 `validation.otp.invalid` |
| TC-AUTH-027 | Verify OTP blank | N | Medium | otp="" | 400 `validation.otp.required` |
| TC-AUTH-028 | Verify email blank | N | Medium | email="" | 400 `validation.email.required` |
| TC-AUTH-029 | OTP is numeric, length = configured otp-length | E | Medium | inspect generated OTP | matches `\d{otp-length}` |
| TC-AUTH-030 | Resend invalidates prior OTP | E | High | resend, old OTP no longer works | old → invalid; new → ok |
| TC-AUTH-031 | Resend for unknown email | S | Medium | unknown email | 200 (silent, no enumeration) |
| TC-AUTH-032 | Resend for already-verified | E | Medium | verified email | 200, no email sent |
| TC-AUTH-033 | OTP only SHA-256 hash stored | S | Critical | inspect token_hash | 64-hex, not the OTP |
| TC-AUTH-034 | SMTP failure surfaces stable code | E | Medium | mail provider throws | 502 `auth.email.sendFailed` |
| TC-AUTH-035 | Dev fallback logs OTP when SMTP unset | E | Low | no MAIL_USERNAME | LoggingEmailSender used |

## 2. AUTH — Login / Refresh / Logout

| TC-ID | Title | Type | Priority | Expected |
|-------|-------|------|----------|----------|
| TC-AUTH-040 | Login valid + verified | P | Critical | 200; accessToken+refreshToken+user |
| TC-AUTH-041 | Login wrong password | N | Critical | 401 `auth.invalidCredentials`; failed_login_count++ |
| TC-AUTH-042 | Login unknown email | N/S | High | 401 `auth.invalidCredentials` (same as wrong pw — no enumeration) |
| TC-AUTH-043 | Login unverified account | N | High | 401 `auth.emailNotVerified` |
| TC-AUTH-044 | Login suspended/banned | N | High | 401 `auth.accountDisabled` |
| TC-AUTH-045 | Account locks after N failures | S | High | after max-failed-logins → 401 `auth.accountLocked` |
| TC-AUTH-046 | Locked account rejects even correct pw | E | High | during lock window → `auth.accountLocked` |
| TC-AUTH-047 | Successful login resets failed count/lock | E | Medium | count=0, locked_until=null |
| TC-AUTH-048 | Login blank email/password | N | Medium | 400 `validation.email.required` / `validation.password.required` |
| TC-AUTH-049 | Login writes audit row (success & fail) | E | Low | dtb_login_audit row created |
| TC-AUTH-050 | Refresh rotates token | P | Critical | new access + new refresh; old refresh revoked |
| TC-AUTH-051 | Refresh with revoked/old token | N | High | 401 (rotation reuse detected) |
| TC-AUTH-052 | Refresh with garbage token | N | Medium | 401 |
| TC-AUTH-053 | Logout revokes refresh + blacklists access | P | High | subsequent use of tokens rejected |
| TC-AUTH-054 | Logout blank refresh | N | Low | 400 `validation.refreshToken.required` |
| TC-AUTH-055 | Access with blacklisted access token | S | High | 401 |

## 3. AUTH — Forgot / Reset password

| TC-ID | Title | Type | Priority | Expected |
|-------|-------|------|----------|----------|
| TC-AUTH-060 | Forgot for known email | P | High | 200; reset token issued + link emailed |
| TC-AUTH-061 | Forgot for unknown email | S | Medium | 200 (silent, no enumeration) |
| TC-AUTH-062 | Forgot invalid email format | N | Medium | 400 `validation.email.invalid` |
| TC-AUTH-063 | Reset with valid token | P | Critical | 200; password changed; all refresh tokens revoked |
| TC-AUTH-064 | Reset with expired token | E | High | 400 `auth.reset.invalid` |
| TC-AUTH-065 | Reset with used token | E | High | 400 `auth.reset.invalid` |
| TC-AUTH-066 | Reset password too short | N | High | 400 `validation.password.length` |
| TC-AUTH-067 | Reset also verifies email if pending | E | Medium | emailVerified=true, status ACTIVE |
| TC-AUTH-068 | Reset forces re-login (revokeAll) | S | High | old refresh tokens invalid |
| TC-AUTH-069 | New forgot request invalidates prior token | E | Medium | prior reset token unusable |

## 4. AUTH — Social login

| TC-ID | Title | Type | Priority | Expected |
|-------|-------|------|----------|----------|
| TC-AUTH-080 | Social login unsupported provider | N | Medium | 400 `auth.social.unsupportedProvider` |
| TC-AUTH-081 | Social login blank token | N | Medium | 400 `validation.token.required` |
| TC-AUTH-082 | Social login new user auto-creates ACTIVE verified | P | High | user created, emailVerified=true |
| TC-AUTH-083 | Social login links to existing email | E | Medium | oauth_account linked to existing user |
| TC-AUTH-084 | Social login existing oauth account | P | High | returns tokens for linked user |

## 5. ACCOUNT / STAFF ACCOUNT (`/api/user/account`, `/api/staff/account`)

| TC-ID | Title | Type | Priority | Expected |
|-------|-------|------|----------|----------|
| TC-ACC-001 | GET /user/account/me authenticated USER | P | High | 200 profile |
| TC-ACC-002 | GET /user/account/me no token | S | High | 401 |
| TC-ACC-003 | GET /user/account/me with STAFF-only token | S | High | 403 |
| TC-ACC-004 | GET /staff/account/me with STAFF token | P | High | 200 profile |
| TC-ACC-005 | GET /staff/account/me with USER token | S | High | 403 |

## 6. MEMBERSHIP (`/api/user/membership`, `/api/common/membership`)

| TC-ID | Title | Type | Priority | Expected |
|-------|-------|------|----------|----------|
| TC-MEM-001 | GET /membership/me creates FREE on first access | P | High | 200; FREE plan + monthly credits |
| TC-MEM-002 | GET /membership/me no auth | S | High | 401 |
| TC-MEM-003 | Lazy renewal after period end | E | High | credits reset to plan allowance; RENEWAL ledger |
| TC-MEM-004 | Public list plans | P | Medium | 200; active plans sorted by sortOrder |
| TC-MEM-005 | Public list operation-credits | P | Medium | 200; active operation costs only |
| TC-MEM-006 | Subscribe PRO via PAYOS | P | Critical | 200; payUrl returned; PENDING payment order |
| TC-MEM-007 | Subscribe invalid planCode | N | High | 400 `validation.planCode.invalid` |
| TC-MEM-008 | Subscribe FREE (non-payable) | N | High | 400 `membership.plan.notPayable` |
| TC-MEM-009 | Subscribe invalid provider | N | High | 400 `validation.provider.invalid` |
| TC-MEM-010 | Subscribe unknown plan (valid pattern, missing seed) | E | Medium | 404 `membership.plan.notFound` |
| TC-MEM-011 | Subscribe no auth | S | High | 401 |

## 7. CREDITS (service-level, consumed by Meshy jobs)

| TC-ID | Title | Type | Priority | Expected |
|-------|-------|------|----------|----------|
| TC-CRD-001 | Consume credits sufficient balance | P | Critical | balance decremented; CONSUME ledger |
| TC-CRD-002 | Consume insufficient balance | N | Critical | 400 `credit.insufficient`; no decrement |
| TC-CRD-003 | Consume zero-cost operation | E | Medium | no-op, no ledger |
| TC-CRD-004 | Refund credits | P | High | balance incremented; REFUND ledger |
| TC-CRD-005 | Refund non-positive amount | E | Low | no-op |
| TC-CRD-006 | Activate plan grants monthly credits | P | Critical | credits set to plan allowance; period reset; GRANT ledger |
| TC-CRD-007 | Activate unknown plan | N | High | 404 `membership.plan.notFound` |
| TC-CRD-008 | Ledger balance_after tracks running balance | E | Medium | balance_after correct each row |
| TC-CRD-009 | Concurrent consume respects pessimistic lock | S | High | no oversell (see BUG-004) |

## 8. 3D PRINT ORDERS (`/api/user/print`)

| TC-ID | Title | Type | Priority | Expected |
|-------|-------|------|----------|----------|
| TC-PRT-001 | Place order for owned SUCCEEDED model | P | Critical | 201; PENDING order + payUrl; server-side price |
| TC-PRT-002 | Place order for non-owned task | S | Critical | 400 `print.task.notOwned` |
| TC-PRT-003 | Place order for non-SUCCEEDED task | N | High | 400 `print.task.notReady` |
| TC-PRT-004 | Place order for unknown task | N | High | 404 `meshy.task.notFound` |
| TC-PRT-005 | Place order invalid sizeCm | N | High | 400 `print.size.invalid` |
| TC-PRT-006 | Amount derives from server price, not client | S | Critical | amount = configured price for size |
| TC-PRT-007 | Missing recipient fields | N | High | 400 `validation.recipientName.required` etc |
| TC-PRT-008 | Invalid recipient email | N | Medium | 400 `validation.recipientEmail.invalid` |
| TC-PRT-009 | Invalid phone format | N | Medium | 400 `validation.recipientPhone.invalid` |
| TC-PRT-010 | Latitude/longitude out of range | E | Low | 400 `validation.latitude.invalid` |
| TC-PRT-011 | Note > 500 chars | E | Low | 400 `validation.note.tooLong` |
| TC-PRT-012 | List my orders paginated desc | P | Medium | 200; page sorted createdAt desc |
| TC-PRT-013 | List orders no auth | S | High | 401 |
| TC-PRT-014 | List only returns caller's orders | S | High | no other users' orders |

## 9. STAFF ORDERS (`/api/staff/orders`)

| TC-ID | Title | Type | Priority | Expected |
|-------|-------|------|----------|----------|
| TC-STF-001 | Staff list all orders | P | High | 200; page of all orders |
| TC-STF-002 | Staff list filtered by status | P | Medium | 200; only that status |
| TC-STF-003 | Staff list invalid status filter | E | Low | 200 empty or 400 (define) |
| TC-STF-004 | Staff get order by id | P | High | 200 full detail |
| TC-STF-005 | Staff get unknown order | N | Medium | 404 |
| TC-STF-006 | Update status valid transition | P | High | 200 updated |
| TC-STF-007 | Update status invalid value | N | High | 400 `validation.status.invalid` |
| TC-STF-008 | Staff endpoints with USER token | S | Critical | 403 |
| TC-STF-009 | Staff endpoints no token | S | High | 401 |
| TC-STF-010 | Download model ZIP | P | Medium | 200 application/zip stream |
| TC-STF-011 | Thumbnail proxy missing → 404 | E | Low | 404 |
| TC-STF-012 | Printability analysis | P | Low | 200 stats |
| TC-STF-013 | Repair model | P | Low | 200 before/after |

## 10. PAYMENTS (`/api/common/payments`) — gateway callbacks

| TC-ID | Title | Type | Priority | Expected |
|-------|-------|------|----------|----------|
| TC-PAY-001 | payOS IPN valid success | P | Critical | RspCode 00; order SUCCEEDED; fulfilment run |
| TC-PAY-002 | payOS IPN invalid signature | S | Critical | RspCode 97; not settled; callback recorded |
| TC-PAY-003 | payOS IPN unknown order | N | High | RspCode 01 |
| TC-PAY-004 | payOS IPN amount mismatch | S | Critical | RspCode 04; not settled |
| TC-PAY-005 | payOS IPN already confirmed (idempotent) | E | Critical | RspCode 02; single fulfilment |
| TC-PAY-006 | MoMo IPN valid success | P | Critical | 204; order SUCCEEDED; fulfilment |
| TC-PAY-007 | MoMo IPN invalid signature | S | Critical | 204; not settled (logged) |
| TC-PAY-008 | MoMo IPN amount mismatch | S | Critical | 204; not settled |
| TC-PAY-009 | Return verifies + credits idempotently | P | Critical | SUCCESS with justCredited true once |
| TC-PAY-010 | Return already succeeded → SUCCESS not re-credited | E | Critical | justCredited=false |
| TC-PAY-011 | Return bad signature | S | High | FAILED |
| TC-PAY-012 | Return amount mismatch | S | High | FAILED |
| TC-PAY-013 | SUBSCRIPTION settle activates plan | P | Critical | plan activated, credits granted |
| TC-PAY-014 | PRINT settle marks print order PAID | P | Critical | print order PAID |
| TC-PAY-015 | Every callback recorded (valid & invalid) | E | Medium | dtb_payment_callback row |
| TC-PAY-016 | Order code generation collision-safe | E | Low | retries, unique |

## 11. MESHY 3D GENERATION (`/api/common/3d`)

| TC-ID | Title | Type | Priority | Expected |
|-------|-------|------|----------|----------|
| TC-MSH-001 | Text-to-3D valid prompt authenticated | P | Critical | 202/200 task created; credits consumed |
| TC-MSH-002 | Text-to-3D blank prompt | N | High | 400 validation |
| TC-MSH-003 | Image-to-3D valid | P | High | task created |
| TC-MSH-004 | Multi-image-to-3D | P | Medium | task created |
| TC-MSH-005 | Create job insufficient credits | N | Critical | 400 `credit.insufficient` |
| TC-MSH-006 | Create job unauthenticated | S | High | 401 |
| TC-MSH-007 | Get task not owned | S | High | 404 (owner-scoped) |
| TC-MSH-008 | Model/texture/thumbnail proxies are public | E | Medium | 200 without auth |
| TC-MSH-009 | USDZ PUT public (AR page) | E | Low | 200 |
| TC-MSH-010 | Webhook valid signature updates status | P | High | task status updated |
| TC-MSH-011 | Webhook bad signature | S | High | rejected |
| TC-MSH-012 | Failed job refunds credits | E | High | REFUND ledger |
| TC-MSH-013 | Oversized image upload | E | Medium | 413 `validation.image.tooLarge` |

## 12. CROSS-CUTTING — Security / Rate limit / CORS / Errors

| TC-ID | Title | Type | Priority | Expected |
|-------|-------|------|----------|----------|
| TC-SEC-001 | Protected route without token | S | Critical | 401 |
| TC-SEC-002 | Protected route expired token | S | Critical | 401 |
| TC-SEC-003 | Role mismatch (USER→staff path) | S | Critical | 403 |
| TC-SEC-004 | Rate limit login (login-per-minute) | S | High | 429 after threshold |
| TC-SEC-005 | Rate limit register (register-per-hour) | S | High | 429 after threshold |
| TC-SEC-006 | Rate limit email (email-per-10min) | S | Medium | 429 after threshold |
| TC-SEC-007 | CORS allow-list enforced (prod) | S | High | disallowed origin blocked — see BUG-001 |
| TC-SEC-008 | Unknown route | E | Low | 404 |
| TC-SEC-009 | Type mismatch path var (bad UUID) | E | Low | 400 `validation.parameter.invalid` |
| TC-SEC-010 | Unhandled exception masked | S | Medium | 500 `common.serverError` (no stack leak) |

---

## 13. FRONTEND E2E (Playwright) — pages & flows

| TC-ID | Page/Flow | Type | Priority | Expected |
|-------|-----------|------|----------|----------|
| TC-E2E-001 | Register → redirected to /verify-email?email= | P | Critical | OTP form shown with email prefilled |
| TC-E2E-002 | Register duplicate email shows toast | N | High | error toast "email already exists" |
| TC-E2E-003 | Register client-side validation (short pw) | N | Medium | native minLength blocks submit |
| TC-E2E-004 | Verify email: OTP input accepts digits only | E | Medium | non-digits stripped |
| TC-E2E-005 | Verify email wrong code shows error toast | N | High | toast, stays on page |
| TC-E2E-006 | Verify email success → success screen + Continue | P | Critical | "Your email is verified" + link to /login |
| TC-E2E-007 | Verify email resend code | P | Medium | success toast |
| TC-E2E-008 | Verify email resend without email | N | Low | error toast "Email required" |
| TC-E2E-009 | Login valid USER → /studio | P | Critical | lands studio |
| TC-E2E-010 | Login valid STAFF → /staff | P | High | lands staff dashboard |
| TC-E2E-011 | Login invalid → error toast | N | High | "Incorrect email or password" |
| TC-E2E-012 | Login unverified → verify hint | N | High | "Please verify your email" |
| TC-E2E-013 | Forgot password shows confirmation | P | Medium | "reset link is on its way" |
| TC-E2E-014 | Reset password missing token | N | Medium | invalid state |
| TC-E2E-015 | Protected route while logged out → redirect /login | S | Critical | redirected, `from` preserved |
| TC-E2E-016 | customerOnly route with staff account | S | High | blocked/redirected |
| TC-E2E-017 | Logout clears session | P | High | back to public, tokens cleared |
| TC-E2E-018 | 401 triggers silent refresh then retry | E | High | request succeeds after refresh |
| TC-E2E-019 | Refresh failure emits logout + clears | E | High | session cleared, redirect |
| TC-E2E-020 | 404 unknown route renders NotFound | E | Low | 404 page |
| TC-E2E-021 | Membership page lists plans | P | Medium | plan cards render |
| TC-E2E-022 | Subscribe redirects to gateway payUrl | P | High | navigation to payUrl |
| TC-E2E-023 | Print order form validation | N | Medium | required fields flagged |
| TC-E2E-024 | Payment return page shows result | P | Medium | success/failure state |
| TC-E2E-025 | AR view route standalone (no chrome) | E | Low | bare layout |

---

## 14. Bug / Risk Register (found during this pass)

See `docs/testing/BUG-REGISTER.md` for full detail, reproduction, and fixes.

| Bug | Severity | Area | Status |
|-----|----------|------|--------|
| BUG-001 | Medium (security) | MVC-level CORS `allowedOriginPatterns("*")` overrides curated allow-list | Fixed |
| BUG-002 | Low (security) | `/verify-email` distinguishes unknown vs verified email (enumeration) | Documented (UX trade-off) |
| BUG-003 | Low | OTP `otp-length` out of 4–9 throws at register (500) instead of config-time guard | Documented |
| BUG-004 | Low (concurrency) | Lazy monthly renewal on read path lacks pessimistic lock (double-grant risk) | Documented |
| BUG-005 | Info | Password-reset still uses emailed link (not OTP) — by design, out of prior scope | Noted |
