# R09 — Error Handling, Logging & Observability Reviewer

## Mission
Audit how the system fails and how it reports. Verify the global exception handling, the response
envelope, logging discipline, and the observability gaps (metrics/health/tracing). You own *failure
signaling and operational visibility*.

## Scope
- `common/exception/**` (global handler, custom exceptions) and `common/response/**` (envelope).
- Error handling patterns vs `rules/09-error-handling.md`.
- Logging: levels, structure, correlation, and sensitive-data leakage.
- Observability: health checks, metrics/actuator exposure, request tracing — including what's
  *missing* (no monitoring stack detected).

## Files / directories to inspect
- `src/main/java/com/innerstyle/common/exception/**`, `common/response/**`
- All `service/impl/**` and `controller/**` for local try/catch patterns and logging
- `src/main/resources/application.yml` (logging config, actuator, error properties)
- `rules/09-error-handling.md`

## Types of defects to detect
- Missing global `@RestControllerAdvice`, or handlers that leak stack traces / internal details to
  clients (info disclosure — coordinate severity with R05).
- Inconsistent error envelope: some endpoints return the standard `{success,message,data}`, others
  raw Spring error JSON.
- Swallowed exceptions (empty catch, `catch` that logs and continues corrupt state).
- Generic 500s where a domain-specific 4xx is correct; wrong status mapping.
- Logging secrets/PII/tokens/payment payloads; `printStackTrace`; `System.out`.
- No/insufficient logging on critical paths (payment callbacks, auth failures, Meshy errors).
- No health endpoint, no metrics, no request correlation id — an observability blind spot for a
  system that calls external paid APIs and processes payments.

## Required outputs
- `review-artifacts/R09.json` + `R09.md`.
- An **error-taxonomy table**: exception type → HTTP status → client message → logged? → leaks?
- An **observability gap list** with recommended minimum (health, metrics, correlation id).

## Review checklist
- [ ] A global exception handler maps all exceptions to the standard envelope.
- [ ] No stack trace / internal detail reaches the client.
- [ ] No swallowed exceptions; failures either recover or propagate cleanly.
- [ ] Status codes are domain-appropriate (validation→400, auth→401/403, missing→404, ext→502/503).
- [ ] No secrets/PII/payment data in logs; no `printStackTrace`/`System.out`.
- [ ] Critical paths (payments, auth, external calls) are logged at appropriate levels.
- [ ] Health check + basic metrics + request correlation exist (or are filed as gaps).

## Success criteria
The error taxonomy covers every exception type; every leak/swallow/mis-status is filed; the
observability gap list is concrete and actionable.

## Boundaries (do NOT review)
- Whether the business logic that threw is correct (→ R03).
- Security exploitability of a leak beyond noting it (→ R05 for severity).
- Frontend error/toast UX (→ R13).

## Suggested execution order
**Wave 2.** After R01.

## Dependencies on other reviewers
Upstream: R01. Coordinates with R05 (info-disclosure severity), R06/R08 (logging on money/external).
