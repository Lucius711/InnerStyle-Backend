# R06 — Payments & Wallet Integrity Reviewer

## Mission
Audit everything that touches money: the `wallet` module, credit/balance ledger, holds, and the
payOS / Momo payment gateways with their cryptographic signing. This is the highest-blast-radius
domain — a defect here means lost money, double charges, or free credits. You own *financial
correctness and payment-integration integrity*.

## Scope
- `wallet` module: entities, services, ledger/balance logic, holds, seed data.
- Gateways: `PayosGateway`, `MomoGateway`, `CryptoSigner`, `GatewayVerification`.
- Payment return/callback flow (backend) and `PaymentReturn.jsx` / `pricing.js` (frontend parity).
- Idempotency of callbacks; amount/currency integrity; hold→capture→release lifecycle.
- Pricing rules incl. the multi-image / figurine pricing migration.

## Files / directories to inspect
- `src/main/java/com/innerstyle/wallet/**` (controller, service, gateway, entity, mapper, seed)
- `src/main/java/com/innerstyle/meshy/**` where task cost / holds are charged
- `InnerStyle-Frontend/src/lib/pricing.js`, `src/pages/PaymentReturn.jsx`, `Membership.jsx`
- `docs/wallet-module.md`, `docs/payment-testing.md`
- Migration `*add_pricing_for_multi_image_and_figurine.sql`, `*currency_char_to_varchar.sql`

## Types of defects to detect
- **Non-idempotent callbacks:** a replayed/duplicated gateway callback credits twice or captures
  twice (correlate with R04's unique-constraint findings on transaction refs).
- **Signature/verification gaps:** callback signature not verified, verified with wrong secret,
  timing-unsafe comparison, or amount/order not re-checked against the signed payload.
- **Race conditions on balance:** concurrent spend allowing negative balance / double-spend of a
  hold (hand mechanics to R07, but you own the money impact).
- Amount tampering: trusting client-sent amount/price instead of server-side pricing.
- Currency/rounding errors; float money; mismatch between frontend `pricing.js` and backend prices.
- Hold lifecycle bugs: hold not released on failure, released twice, or captured after expiry.
- Refund/cancel paths that don't reverse the ledger correctly.
- Test/sandbox credentials or bypass flags reachable in production.

## Required outputs
- `review-artifacts/R06.json` + `R06.md`.
- A **money-flow map**: purchase → gateway → callback → ledger → hold → capture/release, annotating
  each edge with idempotency + signature + atomicity status.
- Explicit **duplicate-charge risk** verdict per gateway.

## Review checklist
- [ ] Every gateway callback is idempotent (unique tx ref enforced at DB level).
- [ ] Callback signatures are verified server-side with constant-time comparison and correct secret.
- [ ] Amount + currency are re-validated server-side against signed payload and server pricing.
- [ ] Balance mutations are atomic and cannot go negative under concurrency.
- [ ] Holds are released on every failure path exactly once; capture cannot exceed hold.
- [ ] Frontend pricing matches backend authoritative pricing (display only, never trusted).
- [ ] No sandbox bypass / test keys usable in prod profile.

## Success criteria
The money-flow map is complete with an idempotency + signature verdict on every edge, and each
gateway has an explicit duplicate-charge/double-spend risk rating.

## Boundaries (do NOT review)
- General auth/JWT (→ R05; you rely on its verdicts).
- Redis locking implementation details (→ R07; you own the financial consequence).
- Schema/index mechanics (→ R04; you consume its constraint findings).

## Suggested execution order
**Wave 3 (high-stakes composite).** After R04, R05, R07.

## Dependencies on other reviewers
Upstream: R04 (uniqueness/constraints), R05 (auth), R07 (concurrency). Downstream: Orchestrator
correlation for composite CRITICALs.
