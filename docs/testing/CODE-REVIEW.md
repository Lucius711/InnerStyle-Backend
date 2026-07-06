# Code Review — OTP verification feature, QA suite, and CORS fix

## Summary
Reviewed all code changed/added across this engagement: the registration email-OTP flow
(entity/migration/service/DTO/SMTP sender), the CORS hardening, and the new backend + Playwright
test suites. Overall quality is high and consistent with the project rules (layered architecture,
stable message codes, hashed secrets, server-authoritative pricing). No Critical defects in the
changed code; a few low-severity items are tracked in `BUG-REGISTER.md`.

## Critical Issues
| # | File | Issue | Severity |
|---|------|-------|----------|
| — | — | None found in the changed code | — |

## Suggestions
| # | File | Line/Area | Suggestion | Category |
|---|------|-----------|------------|----------|
| 1 | `AuthServiceImpl.verifyEmail` | attempt guard | `noRollbackFor = BadRequestException` correctly persists `attempt_count` on a wrong OTP. Add an integration test against a real DB to lock in that the increment truly commits (unit test asserts state, not TX semantics). | Correctness |
| 2 | `AuthServiceImpl.verifyEmail` | enumeration | Consider collapsing `alreadyVerified`/unknown into one code if strict anti-enumeration is required (BUG-002). | Security |
| 3 | `AuthProperties.otpLength` | config | Add `@Min(4)@Max(9)` so bad config fails at startup, not at first register (BUG-003). | Correctness |
| 4 | `SmtpEmailSender.send` | delivery | SMTP send is synchronous inside the register transaction; a slow SMTP handshake holds the DB transaction open. Consider sending after commit (`@TransactionalEventListener(AFTER_COMMIT)`) or async. | Performance |
| 5 | `CreditServiceImpl.getOrCreateMembership` | renewal | Read-path renewal lacks the pessimistic lock the mutation paths use (BUG-004). | Concurrency |
| 6 | `EmailVerificationTokenRepository` | brute force | OTP brute force is bounded per-token (5) but a user can `resend` (rate-limited 3/10min) to reset attempts. For a 6-digit space this is safe; if OTP length is ever lowered, add a per-user attempt cap. | Security |

## What Looks Good
- **Secret hygiene:** only SHA-256 hashes of the OTP are stored; the raw code never touches the DB.
- **Brute-force guard:** per-token `attempt_count` + expiry + token burn on exhaustion; the
  `noRollbackFor` detail is subtle and correctly handled.
- **Deterministic sender selection:** `EmailSenderConfig` factory avoids the empty-string ambiguity
  of `@ConditionalOnProperty`, and HTML values are escaped in `SmtpEmailSender`.
- **CORS fix:** removing the wildcard MVC mapping leaves a single, curated, env-overridable
  allow-list in `SecurityConfig` — a genuine security improvement.
- **Payments:** verified that settlement is gated on signature + amount and is idempotent by status;
  tests now lock this in.
- **Server-authoritative pricing:** print amount comes from `PrintProperties`, not the client.
- **Validation contract:** DTO `@Pattern`/`@Email`/`@Size` map cleanly to `validation.*` codes via
  the global handler; the priority-prefix ordering scheme is deterministic.

## Test suite review
- Backend unit tests use real `TokenHasher`/`AuthProperties` so hash-compare and OTP formatting are
  exercised for real, not mocked away — good.
- MockMvc standalone tests assert the actual error-envelope shape (`$.error.<field>`), covering the
  controller + `GlobalExceptionHandler` contract without a DB/security context.
- Playwright specs mock the API at the network layer, so they are runnable against just the Vite dev
  server and remain deterministic.
- **Integration coverage (added):** `EmailVerificationIntegrationTest` (`@SpringBootTest` +
  Testcontainers Postgres/Redis) boots the full context, runs Flyway for real, validates the
  `attempt_count` column + schema, and proves the wrong-OTP attempt increment actually **commits**
  (the `noRollbackFor` semantics) — which a Mockito unit test cannot show.
- **Coverage gap to close next:** Meshy 3D generation service tests beyond the existing one, and a
  MockMvc security-layer slice (401/403) with the JWT filter wired.

## Verdict
**Approve with minor follow-ups.** The changed code is correct and well-structured. The open items
are Low severity and tracked in `BUG-REGISTER.md`; none block merge. Recommended pre-merge action:
run `mvn test` and `npm run test:e2e` locally (neither could execute in this sandbox — see notes).
