# R07 — Caching, Rate-limiting & Concurrency Reviewer

## Mission
Audit the Redis-backed infrastructure and all concurrency in the system: cache correctness,
rate-limiter behavior, distributed locking, and race conditions in async/polling flows. You own
*correctness under concurrent load and the caching layer*.

## Scope
- `redis` module: `CacheService`, `CacheProperties`, `RedisConfig`, `RedisKeys`,
  `RateLimiterService`, `RateLimitFilter`, `RateLimitProperties`, `TokenBlacklist`.
- Concurrency in service flows: balance mutation, hold capture, meshy task polling, status updates.
- Async execution, scheduled polling, and the frontend polling hook (`useTaskPolling.js`).
- Cache design vs `docs/redis-cache-design.md` and `docs/redis-implementation.md`.

## Files / directories to inspect
- `src/main/java/com/innerstyle/redis/**`
- Service methods performing read-modify-write on shared state (wallet balance, holds, task status)
- `src/main/java/com/innerstyle/meshy/service/impl/**` (polling/status update)
- `InnerStyle-Frontend/src/hooks/useTaskPolling.js`, `usePipelineActions.js`
- `docs/redis-cache-design.md`, `docs/redis-implementation.md`

## Types of defects to detect
- **Race conditions:** unguarded read-modify-write (double-spend, lost update); check-then-act on
  balances/holds/status without a lock or atomic op.
- Cache issues: stale cache after writes (no invalidation), unbounded keys / missing TTL, cache
  stampede, non-namespaced keys colliding, caching user-specific data under a shared key.
- Rate-limiter: bypassable limits (per-instance instead of distributed), wrong key (IP vs user),
  fail-open on Redis outage where it should fail-closed for sensitive endpoints.
- Token blacklist: race between logout and in-flight request; missing TTL aligned to token expiry.
- Async/polling: thread-safety of shared mutable state, missing backoff, tight polling loops,
  unbounded concurrent Meshy polls.
- Redis outage handling: does the app degrade safely or crash / leak?

## Required outputs
- `review-artifacts/R07.json` + `R07.md`.
- A **concurrency hotspot list**: each shared-state mutation with its protection mechanism (atomic
  op / lock / none) — the "none" rows feed R06 for money impact.

## Review checklist
- [ ] Every balance/hold/status mutation uses an atomic Redis op or DB lock, not check-then-act.
- [ ] Cache entries have TTLs, correct namespacing, and invalidation on the corresponding write.
- [ ] Rate limits are distributed (shared Redis), keyed correctly, and fail-closed where sensitive.
- [ ] Token blacklist TTL matches token lifetime; logout races handled.
- [ ] Polling uses backoff and bounded concurrency; no busy loops.
- [ ] Redis unavailability degrades gracefully with a defined policy.

## Success criteria
Every shared-state mutation is classified by protection mechanism; all unprotected ones are filed,
and money-relevant ones are handed to R06.

## Boundaries (do NOT review)
- The financial consequence/ledger correctness of a race (→ R06; you supply the mechanism).
- External Meshy API resilience/retry (→ R08; you cover local concurrency of polling).
- Auth semantics of the blacklist (→ R05; you cover its concurrency/TTL).

## Suggested execution order
**Wave 2.** After R01. Feeds R06 (Wave 3).

## Dependencies on other reviewers
Upstream: R01. Downstream: R06 (money impact of races).
