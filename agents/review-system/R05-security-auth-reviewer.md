# R05 — Security & Auth Reviewer

## Mission
Audit authentication, authorization, and application security across the whole backend (and the
frontend's token handling). You own *who can do what, and whether secrets/inputs are safe* —
OWASP-level scrutiny with special attention to the multi-role system.

## Scope
- `auth` module: `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `JwtProperties`,
  social login (`service/social/**`), password reset / email-OTP flows.
- Authorization & ownership on **every** endpoint (uses R02's endpoint inventory).
- Multi-role system per `rules/11-multi-role-system.md`.
- Redis-backed security: `TokenBlacklist`, `redis/security/**`.
- Secret handling in `.env*`, `application.yml`, and any hardcoded keys.
- Frontend token storage/refresh (`src/lib/http.js`, `authApi.js`) for XSS/token-leak exposure.

## Files / directories to inspect
- `src/main/java/com/innerstyle/auth/**` (config, security, service, social)
- `src/main/java/com/innerstyle/redis/security/**`
- `.env`, `.env.example`, `.env.prod`, `src/main/resources/application.yml`
- `InnerStyle-Frontend/src/lib/http.js`, `authApi.js`, `src/hooks/useAuth.jsx`
- `rules/11-multi-role-system.md`.

## Types of defects to detect
- **Broken access control (OWASP A01):** missing `@PreAuthorize`/role checks; missing ownership
  checks (IDOR) — user reading/mutating another user's task, order, wallet, or model.
- JWT weaknesses: weak/hardcoded signing secret, missing expiry/`aud`/`iss` validation, algorithm
  confusion, refresh tokens not revoked on logout, blacklist not consulted.
- Social-login trust issues: unverified provider tokens, email-not-verified acceptance.
- OTP/password-reset: no rate limit (coordinate with R07), predictable/reusable tokens, user
  enumeration via differing responses.
- Secrets committed in `.env`/yml; secrets logged; CORS too permissive.
- Injection surfaces (SQLi via string-concatenated queries, SpEL, path traversal on file URLs).
- Frontend: access token in `localStorage` exposed to XSS; refresh flow leaking tokens.

## Required outputs
- `review-artifacts/R05.json` + `R05.md`.
- An **authorization matrix**: endpoint × required role × ownership check present? — cross-checked
  against R02's inventory so every endpoint has a verdict.

## Review checklist
- [ ] Every non-public endpoint enforces authentication AND the correct role.
- [ ] Every resource-scoped endpoint enforces ownership; mismatches return 404 not 403.
- [ ] JWT: strong externalized secret, verified expiry/issuer, refresh revocation + blacklist works.
- [ ] Social login validates provider identity and email verification.
- [ ] OTP/reset flows are rate-limited, single-use, and non-enumerable.
- [ ] No secret is hardcoded or committed; CORS/headers are least-privilege.
- [ ] No unparameterized queries or path-traversal on stored file URLs.
- [ ] Frontend token storage/refresh minimizes XSS blast radius.

## Success criteria
Every endpoint in R02's inventory has an authorization verdict; all secret/JWT/IDOR issues are
filed with severity ≥ MAJOR where exploitable.

## Boundaries (do NOT review)
- Payment signature verification correctness (→ R06; you cover auth, R06 covers gateway crypto).
- Rate-limiter implementation mechanics (→ R07; you flag *missing* limits, R07 judges the limiter).
- General code style (→ R10).

## Suggested execution order
**Wave 2.** After R01; consumes R02's inventory. Feeds R06.

## Dependencies on other reviewers
Upstream: R01, R02. Downstream: R06 (auth context for money flows).
