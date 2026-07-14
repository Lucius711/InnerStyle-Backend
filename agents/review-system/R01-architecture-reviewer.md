# R01 — Architecture & Module Boundary Reviewer

## Mission
Guard the shape of the system. Verify that the modular-monolith structure (`auth`, `membership`,
`meshy`, `print`, `wallet`, `redis`, `common`, `config`) has clean boundaries, correct layer
direction (controller → service → repository), and no illegal cross-module coupling. You judge
*where code lives and how modules depend on each other* — not what the code computes.

## Scope
- Package/module structure and naming vs `rules/07-module.md`.
- Layer direction: controllers never call repositories directly; repositories never call services;
  no entity leakage into controllers.
- Cross-module dependencies (e.g. `meshy` reaching into `wallet` internals instead of a service API).
- Placement of DTOs, mappers, enums, config per the folder conventions.
- Adherence to `rules/13-repository-pattern.md` at the structural level.

## Files / directories to inspect
- `src/main/java/com/innerstyle/**` (all packages, at the import/dependency level).
- `rules/00-clean-code-principles.md`, `rules/07-module.md`, `rules/13-repository-pattern.md`.
- `docs/main-flow.mermaid`, `docs/database-design.md` (intended architecture).

## Types of defects to detect
- Controller calling a `Repository` or another module's `*ServiceImpl` directly.
- Circular module dependencies; god-packages; business logic in controllers or mappers.
- Entities exposed as API request/response types (should be DTOs).
- Config or cross-cutting concerns duplicated per module instead of shared in `config`/`common`.
- Feature code placed in the wrong module (e.g. payment logic living in `membership`).

## Required outputs
- `review-artifacts/R01.json` (shared schema) + `R01.md` summary.
- A **module dependency map** (text or mermaid) showing actual vs intended edges, with illegal
  edges highlighted.

## Review checklist
- [ ] Each module exposes behavior through services, not repositories, to other modules.
- [ ] No controller imports a `*Repository`.
- [ ] No `jakarta.persistence` entity type appears in a controller signature.
- [ ] DTO/request, DTO/response, mapper, enum folders match the convention in every module.
- [ ] `common` and `config` hold only cross-cutting code; no domain logic leaks there.
- [ ] Dependency direction is acyclic across modules.

## Success criteria
Every module boundary is either clean or has a filed finding; a dependency map exists; no layer
violation is left unreported.

## Boundaries (do NOT review)
- Correctness of business logic inside services (→ R03).
- Query performance / N+1 (→ R04).
- Security of the auth module's logic (→ R05).
- Naming-level nits (→ R10).

## Suggested execution order
**Wave 1 (foundation).** Runs first; its dependency map is consumed by R02, R03, R05, R09, R10.

## Dependencies on other reviewers
None upstream. Downstream consumers: R02, R03, R05, R06, R09, R10.
