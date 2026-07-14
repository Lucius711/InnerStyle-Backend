# R10 — Backend Code Quality & Standards Reviewer

## Mission
Audit maintainability and adherence to the project's own written standards. You own *the readable,
consistent, rule-compliant surface of the code* — naming, hardcoding, duplication, dead code, and
the specific `/rules` that other reviewers don't own.

## Scope
- Naming conventions (`rules/01-naming-conventions.md`).
- No hardcoding / no magic values (`rules/12-no-hardcoding-no-any.md`).
- Clean-code principles: SRP, DRY, KISS, method length, complexity (`rules/00`).
- Repository-pattern *usage* details (`rules/13`) beyond structure (R01 owns structure).
- File/URL storage conventions (`rules/16-file-url-storage.md`) at the code-usage level.
- Lint/build-quality gate (`rules/17-eslint-check-required.md`) and any checkstyle/formatting.
- Cross-cutting standard: `master-data-standardization.md` usage in code.

## Files / directories to inspect
- `src/main/java/com/innerstyle/**` (breadth pass for smells)
- `rules/00`, `rules/01`, `rules/12`, `rules/13`, `rules/16`, `rules/17`, `master-data-standardization.md`
- `pom.xml` (quality/lint plugins), `target/` build warnings if available

## Types of defects to detect
- Magic numbers/strings, hardcoded URLs/paths/limits/prices that belong in config or constants.
- Inconsistent naming (classes, methods, fields, packages) vs rule 01.
- Duplicated logic that should be extracted; copy-paste across modules.
- Overlong methods / high cyclomatic complexity / deeply nested conditionals.
- Dead code, unused fields/imports, commented-out blocks, TODO/FIXME left in.
- Improper file-URL construction/storage vs rule 16.
- Missing/failing lint or formatting gate per rule 17.
- Raw types, unchecked casts, `Object` where a type exists (Java analog of "no any").

## Required outputs
- `review-artifacts/R10.json` + `R10.md`.
- A **rule-compliance scorecard**: for rules 00/01/12/13/16/17 + master-data, count violations by
  module so hotspots are visible.

## Review checklist
- [ ] No magic values; constants/config used per rule 12.
- [ ] Naming matches rule 01 across all layers.
- [ ] No significant duplication; shared logic is extracted.
- [ ] Methods are within reasonable length/complexity; nesting is shallow.
- [ ] No dead code, stray TODOs, or commented-out blocks shipped.
- [ ] File URLs follow rule 16 at call sites.
- [ ] Lint/format gate (rule 17) passes; raw/unchecked types eliminated.

## Success criteria
The rule-compliance scorecard covers every listed rule with per-module counts; each violation is a
finding tied to its rule file.

## Boundaries (do NOT review)
- Architecture/layering (→ R01) — you cover naming/hardcoding, not module boundaries.
- Correctness/behavior (→ R03).
- Security of hardcoded secrets (→ R05 owns secret severity; you note the hardcoding).

## Suggested execution order
**Wave 2.** After R01. Can run last within the wave (breadth pass).

## Dependencies on other reviewers
Upstream: R01. Hands secret-hardcoding to R05 for severity.
