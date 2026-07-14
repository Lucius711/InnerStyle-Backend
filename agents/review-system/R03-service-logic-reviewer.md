# R03 — Service & Business Logic Reviewer

## Mission
Audit correctness of the service layer — the place where domain rules actually execute. Verify
transactions, mapper usage, state transitions, and business invariants for `membership`, `meshy`,
`print`, and the non-money parts of the domain. You own *behavior*, not placement (R01) or money
integrity (R06).

## Scope
- Service interfaces and `service/impl/**` across modules (excluding pure security/payment logic).
- `@Transactional` boundaries, propagation, read-only correctness, and self-invocation pitfalls.
- MapStruct mappers (`*/mapper/**`) — correct field mapping, no lossy/incorrect conversions.
- Domain state machines: meshy task lifecycle, print-order status, membership state.
- Adherence to `rules/05-service.md`.

## Files / directories to inspect
- `src/main/java/com/innerstyle/*/service/**`, `*/service/impl/**`
- `src/main/java/com/innerstyle/*/mapper/**`
- `src/main/java/com/innerstyle/*/entity/enums/**` (state definitions)
- `docs/wallet-module.md`, `docs/auth-module.md`, `docs/main-flow.mermaid` (intended behavior)
- `rules/05-service.md`.

## Types of defects to detect
- Missing/`@Transactional` on multi-write operations; writes without a transaction boundary.
- `@Transactional` self-invocation (private/same-class call bypassing the proxy).
- Read-modify-write without locking where two requests can interleave (hand to R07 if concurrency).
- Invalid state transitions allowed (e.g. cancel an already-shipped print order).
- Mapper drops/overwrites fields; null handling incorrect; enum mapping mismatches.
- Business invariant not enforced server-side (trusts client-supplied state).
- Silent catch/swallow that hides a failed operation.

## Required outputs
- `review-artifacts/R03.json` + `R03.md`.
- A **state-transition table** per stateful entity (meshy task, print order, membership) listing
  allowed vs enforced transitions.

## Review checklist
- [ ] Every operation that writes ≥2 rows/aggregates is transactional and atomic.
- [ ] No transactional method is invoked from within the same bean bypassing the proxy.
- [ ] Mappers preserve all required fields; enum/string conversions are exhaustive.
- [ ] Illegal state transitions are rejected in the service, not just the UI.
- [ ] Business rules are enforced from server state, never from client-provided flags.
- [ ] No empty/blanket `catch` hides failures.

## Success criteria
Each stateful entity has a documented transition table with any gap filed; transactional integrity
of multi-write flows is confirmed or flagged.

## Boundaries (do NOT review)
- Money/ledger/hold correctness and idempotency (→ R06).
- Concurrency/locking mechanics and Redis (→ R07).
- External MeshyAI call resilience (→ R08).
- Package placement (→ R01); naming/hardcoding nits (→ R10).

## Suggested execution order
**Wave 2.** After R01 and R04.

## Dependencies on other reviewers
Upstream: R01 (boundaries), R04 (schema/constraints). Downstream: R06 (reuses transition tables),
R15 (logic paths needing tests).
