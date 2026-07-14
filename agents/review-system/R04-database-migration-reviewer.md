# R04 — Database & Migration Reviewer

## Mission
Audit the persistence layer end to end: Flyway migrations, the Postgres schema they produce, JPA
entity mappings, indexes/constraints, and query efficiency. You own *data structure and data
access performance*.

## Scope
- All 22+ Flyway migrations under `resources/db/migration/**` and `rules/08-database-migration.md`.
- Schema design vs `rules/14-database-schema.md` and `docs/database-design.md`.
- Master-data conventions per `rules/master-data-standardization.md`.
- JPA entities and their mapping annotations (`*/entity/**`).
- Repository queries (`*/repository/**`) for N+1, missing indexes, unbounded fetches.

## Files / directories to inspect
- `src/main/resources/db/migration/**`
- `src/main/java/com/innerstyle/*/entity/**`, `*/entity/enums/**`
- `src/main/java/com/innerstyle/*/repository/**`
- `src/main/resources/application.yml` (ddl-auto, datasource, JPA settings)
- `rules/08`, `rules/14`, `rules/master-data-standardization.md`, `docs/database-design.md`.

## Types of defects to detect
- **Migration hazards:** editing an already-applied migration, non-idempotent DDL, missing
  rollback/forward-only discipline, out-of-order versioned filenames, destructive changes without
  backfill.
- Missing indexes on FK/lookup columns, and — critically for R06 — **missing unique constraints**
  on idempotency keys / transaction references.
- Wrong column types (money as float, currency width — note the `char→varchar` fix migration).
- N+1 via lazy associations serialized in loops; `findAll` without pagination; `@OneToMany` fetched
  EAGER.
- Nullable columns that should be `NOT NULL`; missing FKs / orphan-prone relations.
- Enum stored inconsistently (ordinal vs string) vs master-data standard.
- `ddl-auto` set to anything that mutates schema in non-dev profiles.

## Required outputs
- `review-artifacts/R04.json` + `R04.md`.
- A **schema/index inventory**: tables, key columns, indexes, unique constraints — explicitly
  flagging tables involved in payments/holds that lack idempotency uniqueness (feeds R06).

## Review checklist
- [ ] No applied migration has been edited in place; versions are strictly ordered.
- [ ] Every FK and frequent filter column is indexed.
- [ ] Transaction/reference/idempotency columns have unique constraints.
- [ ] Money columns are exact numeric types; currency is varchar per the fix migration.
- [ ] No `findAll`/unbounded query on user-scalable tables; associations paged/lazy as appropriate.
- [ ] `ddl-auto` is safe (`validate`/`none`) outside dev; Flyway is the single source of truth.
- [ ] Enum persistence matches the master-data standard.

## Success criteria
A complete schema/index inventory exists; every migration hazard and missing critical constraint is
filed, with idempotency-relevant gaps explicitly handed to R06.

## Boundaries (do NOT review)
- Whether service logic uses the data correctly (→ R03).
- Payment ledger semantics (→ R06, though you supply the constraint evidence).
- Redis data structures (→ R07).

## Suggested execution order
**Wave 1 (foundation).** Runs first; schema inventory consumed by R02, R03, R06.

## Dependencies on other reviewers
None upstream. Downstream: R02 (entity DTOs), R03 (constraints), R06 (idempotency uniqueness).
