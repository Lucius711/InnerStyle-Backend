# InnerStyle Autonomous Review System

A repository-specific, multi-agent code-audit system for the **InnerStyle** platform
(Spring Boot 3.3.5 / Java 21 backend + React 18 / Vite 5 / Three.js frontend, MeshyAI 3D pipeline).

This folder does **not** contain a review of the code. It contains the **design of a review
machine**: a roster of single-responsibility reviewer agents plus an orchestrator that runs
them so that **every subsystem is audited and nothing is reviewed twice**.

---

## 1. How this system was derived

The roster was not chosen from a fixed template. It was derived from what actually exists in
the two repositories:

| Detected fact | Consequence for the roster |
|---|---|
| Modular monolith: `auth`, `membership`, `meshy`, `print`, `wallet`, `redis`, `common`, `config` | Architecture + per-concern reviewers, not one "backend" reviewer |
| JWT + social login + `SecurityConfig` + multi-role (rule 11) + Redis token blacklist | Dedicated **Security & Auth** reviewer |
| `wallet` with payOS, Momo, `CryptoSigner`, holds/ledger, payment-return flow | Dedicated **Payments & Wallet Integrity** reviewer (money = highest blast radius) |
| MeshyAI `RestClient` external dependency + task polling | Dedicated **Third-party Integration** reviewer |
| Redis cache + rate-limit filter + async task polling | Dedicated **Caching / Rate-limit / Concurrency** reviewer |
| 22 Flyway migrations, Postgres, `master-data-standardization` rule | Dedicated **Database & Migration** reviewer |
| 17 enforced rule files in `/rules` | Every backend reviewer cites the specific rule it enforces |
| React + Three.js/R3F/drei, AR, heavy bundle chunking | Split **FE Architecture** vs **3D/Rendering Performance** reviewers |
| i18n (en/vi), SEO components, `SEO_AUDIT.md`, `index.html` meta | Dedicated **UX/a11y/i18n** and **SEO** reviewers |
| ~182 Java files but only ~10 test files; Playwright e2e but no FE unit tests | Dedicated **Testing & QA Coverage** reviewer (known weak spot) |
| Docker, docker-compose, nginx, Vercel, `.env*`, **no `.github/workflows`** | Dedicated **DevOps / Build / Dependencies** reviewer |

## 2. Reviewer roster (16 agents + orchestrator)

| ID | Agent | Primary target |
|----|-------|----------------|
| `00` | [Orchestrator](./00-orchestrator.md) | Runs the fleet, dedups, normalizes severity, writes final report |
| `R01` | [Architecture & Module Boundary](./R01-architecture-reviewer.md) | Layering, package boundaries, dependency direction |
| `R02` | [API & Controller](./R02-api-controller-reviewer.md) | Controllers, REST contract, DTO in/out, OpenAPI |
| `R03` | [Service & Business Logic](./R03-service-logic-reviewer.md) | Service layer, transactions, mappers, domain rules |
| `R04` | [Database & Migration](./R04-database-migration-reviewer.md) | Flyway, schema, entities, indexes, master data |
| `R05` | [Security & Auth](./R05-security-auth-reviewer.md) | JWT, authz, roles, OWASP, secrets |
| `R06` | [Payments & Wallet Integrity](./R06-payments-wallet-reviewer.md) | Gateways, signatures, ledger, idempotency, money |
| `R07` | [Caching, Rate-limit & Concurrency](./R07-concurrency-caching-reviewer.md) | Redis, races, async, polling |
| `R08` | [Third-party Integration](./R08-integration-reviewer.md) | MeshyAI client, resilience, external contracts |
| `R09` | [Error Handling, Logging & Observability](./R09-error-logging-reviewer.md) | Exceptions, envelope, logs, metrics gaps |
| `R10` | [Backend Code Quality & Standards](./R10-code-quality-reviewer.md) | Naming, hardcoding, dead code, lint/checkstyle |
| `R11` | [Frontend Architecture & Components](./R11-frontend-architecture-reviewer.md) | React structure, hooks, routing, HTTP layer |
| `R12` | [3D / Rendering & Frontend Performance](./R12-frontend-3d-performance-reviewer.md) | Three.js/R3F, disposal, bundle, runtime perf |
| `R13` | [UX, Accessibility & i18n](./R13-ux-a11y-i18n-reviewer.md) | a11y, en/vi parity, forms, responsiveness |
| `R14` | [SEO & Metadata](./R14-seo-reviewer.md) | Meta, robots, sitemap, structured data |
| `R15` | [Testing & QA Coverage](./R15-testing-reviewer.md) | JUnit + Playwright coverage & quality |
| `R16` | [DevOps, Build & Dependencies](./R16-devops-build-reviewer.md) | Docker, nginx, Vercel, deps, secrets, missing CI/CD |

## 3. Shared finding schema (every agent emits this)

Each reviewer writes one artifact to `./review-artifacts/<ID>.json`. The array holds findings of
this exact shape, so the orchestrator can merge, dedup and sort them mechanically:

```jsonc
{
  "reviewer": "R05",
  "generated_at": "2026-07-14T00:00:00Z",
  "repo": "backend",                 // backend | frontend | both
  "findings": [
    {
      "id": "R05-001",
      "title": "Ownership check missing on GET /meshy/tasks/{id}",
      "severity": "CRITICAL",        // CRITICAL | MAJOR | MINOR | NIT (see §4)
      "confidence": "HIGH",          // HIGH | MEDIUM | LOW
      "file": "src/main/java/com/innerstyle/meshy/controller/MeshyController.java",
      "line": 88,
      "rule": "11-multi-role-system", // rule file or standard (OWASP-A01, etc.) or null
      "evidence": "Returns task without checking task.userId == principal.id",
      "impact": "Any authenticated user can read another user's task and model URLs",
      "recommendation": "Add ownership guard in service; return 404 (not 403) on mismatch",
      "fingerprint": "meshy:MeshyController:getTask:missing-ownership"  // for dedup
    }
  ]
}
```

`fingerprint` = stable hash of `{primary_file}:{symbol}:{defect-class}`. Two reviewers reporting
the same defect must produce the same fingerprint so the orchestrator can collapse them.

## 4. Severity model (aligned with the repo's existing `reviewer-agent.md`)

| Severity | Meaning | Merge gate |
|----------|---------|-----------|
| 🔴 **CRITICAL** | Security hole, data/money loss, corruption, crash | Blocks merge |
| 🟠 **MAJOR** | Wrong logic, likely bug, serious perf/scaling issue | Blocks merge |
| 🟡 **MINOR** | Code smell, rule violation without runtime failure | Should fix |
| ⚪ **NIT** | Style, naming, comment, formatting | Optional |

## 5. Running the system

Human or lead agent runs the orchestrator, which dispatches reviewers in waves (see
[`00-orchestrator.md`](./00-orchestrator.md) §Execution waves). Each reviewer is invoked with its
own `.md` file as the system prompt and produces its `review-artifacts/<ID>.json` plus a short
human-readable `review-artifacts/<ID>.md`. The orchestrator then merges everything into
`review-artifacts/FINAL-REPORT.md`.

## 6. Design invariants

- **Single responsibility** — each reviewer owns exactly one concern; its "Boundaries" section
  names what it must *not* touch.
- **No uncovered subsystem** — see the coverage matrix in the orchestrator (§Coverage matrix).
- **Minimal overlap** — where two reviewers see the same file, one owns *structure*, the other
  owns *behavior*; the matrix records the split.
- **Deterministic output** — shared schema + fingerprints make merge/dedup mechanical.
