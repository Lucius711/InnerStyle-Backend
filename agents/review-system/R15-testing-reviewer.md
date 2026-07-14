# R15 — Testing & QA Coverage Reviewer

## Mission
Audit the test suites for coverage, quality, and gaps against the actual defect surface the other
reviewers found. The repo is test-light on the backend (~10 test files for ~182 sources) and has no
frontend unit tests (only Playwright e2e) — so this reviewer's central job is exposing what is
*untested but risky*. You own *confidence that changes are caught before production*.

## Scope
- Backend tests: `src/test/**` (JUnit/Mockito) vs `rules/10-testing.md`.
- Frontend e2e: `InnerStyle-Frontend/e2e/**`, `playwright.config.ts`, `playwright-report/`.
- Coverage of the highest-risk paths surfaced by R05 (auth/IDOR), R06 (payments), R07 (concurrency),
  R08 (integration) — i.e. risk-weighted, not line-count, coverage.
- Test quality: meaningful assertions, isolation, no flakiness, no over-mocking that hides bugs.

## Files / directories to inspect
- `InnerStyle-Backend/src/test/**`, `rules/10-testing.md`, `docs/testing/**`
- `InnerStyle-Frontend/e2e/**` (auth, membership, studio pipeline, payments-return, staff, api specs),
  `playwright.config.ts`
- All prior reviewers' artifacts (`review-artifacts/R0*.json`, `R1*.json`) to map risk → test presence.

## Types of defects to detect
- **Untested critical paths:** payment callbacks/idempotency, balance/hold concurrency, auth/IDOR,
  Meshy failure handling — presence checked against R05/R06/R07/R08 findings.
- Backend: services with branching logic and zero tests; happy-path-only tests; no negative/edge
  cases (null, boundary, unauthorized) per rule 10.
- Weak assertions (asserts not-null only), tests that mock the very logic under test, shared mutable
  state causing order-dependence.
- e2e gaps: routes/flows in R11's map with no spec; flaky specs (timing/`waitForTimeout` abuse);
  no negative-path e2e (rejected payment, unauthorized nav).
- No coverage measurement/threshold in the build; no test in CI (also flagged by R16).

## Required outputs
- `review-artifacts/R15.json` + `R15.md`.
- A **risk-vs-test matrix**: each CRITICAL/MAJOR finding from R05/R06/R07/R08 × is there a test that
  would catch a regression? (yes / partial / none).
- A **prioritized test backlog** (what to write first, highest risk first).

## Review checklist
- [ ] Every CRITICAL/MAJOR risk path has a regression test (or a filed gap).
- [ ] Backend services with logic have unit tests incl. negative/edge cases (rule 10).
- [ ] Payment, auth, and concurrency flows have explicit tests (unit and/or e2e).
- [ ] e2e covers every route/flow in R11's map, including negative paths.
- [ ] Assertions are meaningful; tests are isolated and deterministic (no flaky timing).
- [ ] Coverage is measured with a threshold enforced in the build/CI.

## Success criteria
The risk-vs-test matrix accounts for every CRITICAL/MAJOR finding; the prioritized backlog is
concrete; all coverage/quality gaps are filed.

## Boundaries (do NOT review)
- The correctness of the production code itself (that's the other reviewers'); you assess whether it
  is *tested*.
- CI wiring mechanics (→ R16; you flag "tests not run in CI", R16 owns the pipeline).

## Suggested execution order
**Wave 5.** After all other reviewers (needs the full risk surface).

## Dependencies on other reviewers
Upstream: R02, R05, R06, R07, R08, R11 (risk + route inputs). Downstream: Orchestrator.
