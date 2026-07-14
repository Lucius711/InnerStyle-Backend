# R08 — Third-party Integration Reviewer

## Mission
Audit resilience and correctness of outbound integrations, chiefly the **MeshyAI** 3D-generation
API accessed through the `meshy/client`. You own *how the system talks to external services and
survives their failures*. (Payment gateways' financial correctness belongs to R06; you may review
their transport-level resilience by handoff.)

## Scope
- `meshy/client`: `MeshyClient`, `MeshyRestClient`, client DTOs, `meshy/config`.
- HTTP client configuration: timeouts, retries, backoff, connection pooling, error mapping.
- External contract handling: response parsing, status polling, webhook/callback ingestion.
- Secret/credential handling for the external API (coordinate with R05 on storage).
- File URL handling from MeshyAI per `rules/16-file-url-storage.md`.

## Files / directories to inspect
- `src/main/java/com/innerstyle/meshy/client/**`, `meshy/client/impl/**`, `meshy/client/dto/**`
- `src/main/java/com/innerstyle/meshy/config/**`
- `src/main/java/com/innerstyle/meshy/util/**`
- `rules/16-file-url-storage.md`
- `docs/main-flow.mermaid` (pipeline integration points)

## Types of defects to detect
- **No timeout** on outbound calls (thread/connection exhaustion under Meshy slowness).
- Missing/incorrect retry with backoff; retrying non-idempotent calls; no circuit breaker.
- Fragile response parsing (assumes fields present, no handling of Meshy error payloads/status).
- Unbounded polling of task status; no give-up/expiry; no dead-letter for stuck tasks.
- Leaking the external API key (logs, client-visible responses, committed config).
- Trusting external file URLs blindly (SSRF, storing hostile URLs) vs `rules/16`.
- No handling for partial failure (model generated but download/convert fails).
- Tight coupling: Meshy response shape leaking into domain entities/DTOs.

## Required outputs
- `review-artifacts/R08.json` + `R08.md`.
- An **integration resilience table**: each outbound call × timeout × retry/backoff × error mapping
  × idempotency.

## Review checklist
- [ ] Every outbound call has explicit connect + read timeouts.
- [ ] Retries use bounded backoff and only wrap idempotent operations; failures surface clearly.
- [ ] Response parsing handles error status and missing fields without throwing raw.
- [ ] Task polling has a max duration / terminal failure state; stuck tasks are handled.
- [ ] External API key is externalized, never logged or returned to clients.
- [ ] File URLs from Meshy are validated/stored per rule 16; no SSRF.
- [ ] External DTOs are mapped to domain types, not reused as entities.

## Success criteria
The resilience table covers every outbound call; each missing timeout/retry/validation is filed
with the failure scenario it enables.

## Boundaries (do NOT review)
- Payment gateway signature/money correctness (→ R06).
- Local polling concurrency/threading (→ R07; you own the external-contract side).
- Domain logic consuming the result (→ R03).

## Suggested execution order
**Wave 2.** After R01.

## Dependencies on other reviewers
Upstream: R01. Coordinates with R05 (API-key storage), R07 (polling), R06 (gateway transport).
