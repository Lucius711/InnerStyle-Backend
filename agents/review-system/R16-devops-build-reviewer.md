# R16 — DevOps, Build & Dependencies Reviewer

## Mission
Audit build, packaging, deployment, dependencies, secret hygiene, and repository cleanliness across
both repos. A notable gap already stands out: **no `.github/workflows`** (no CI/CD), alongside a
frontend polluted with dozens of `vite.config.js.timestamp-*.mjs` artifacts. You own *how the code
is built, shipped, and kept clean* — and the absence of automation that should exist.

## Scope
- Backend: `pom.xml` (deps, plugins, versions), `Dockerfile`, `docker-compose.yml`, `.dockerignore`,
  `scripts/`, `application.yml` build/profile config.
- Frontend: `package.json`, `package-lock.json`, `Dockerfile`, `nginx.conf`, `vercel.json`,
  `vite.config.js`, `postcss.config.js`, `tailwind.config.js`, `.dockerignore`.
- Secrets & env: `.env`, `.env.example`, `.env.prod`, `.env.deploy.example` in both repos.
- CI/CD: presence/absence of pipelines; build+test+lint automation.
- Repo hygiene: committed build artifacts, `dist/`, `node_modules` state, stray scratch files
  (`_axis.mjs`, `_t.mjs`, `_test3mf.mjs`, `chk.cjs`, `target/`, `playwright-report/`, `test-results/`).

## Files / directories to inspect
- `InnerStyle-Backend/pom.xml`, `Dockerfile`, `docker-compose.yml`, `.env*`, `scripts/`
- `InnerStyle-Frontend/package.json`, `Dockerfile`, `nginx.conf`, `vercel.json`, `vite.config.js`,
  `.env*`, and the `vite.config.js.timestamp-*.mjs` / stray `_*.mjs` / `chk.cjs` clutter
- `.gitignore` in both repos

## Types of defects to detect
- **No CI/CD:** no automated build/test/lint/deploy; releases are manual and unverified.
- **Secret hygiene:** real secrets committed in `.env`/`.env.prod` (not just `.example`); secrets not
  in `.gitignore`; secrets baked into Docker images or `application.yml` (hand severity to R05).
- Dependency risks: outdated/vulnerable deps, version ranges (`^`) allowing drift, no lockfile
  discipline, unpinned base images in Dockerfiles.
- Docker: running as root, missing multi-stage build, large images, missing healthcheck, secrets in
  build args, `.dockerignore` gaps leaking `.env`/`node_modules`.
- nginx/Vercel: missing security headers, wrong SPA fallback, caching that harms SEO (coordinate R14).
- Repo hygiene: `dist/`, `target/`, `playwright-report/`, `test-results/`, and 40+
  `vite.config.js.timestamp-*.mjs` files committed; stray scratch scripts shipped.
- `.env.example` drift from actual required vars.

## Required outputs
- `review-artifacts/R16.json` + `R16.md`.
- A **build/deploy inventory**: artifact → build step → deploy target → automated? → secret source.
- A **dependency risk list** (outdated/vulnerable/unpinned) for both `pom.xml` and `package.json`.
- A **repo-hygiene cleanup list** (files that should be gitignored/removed).

## Review checklist
- [ ] CI exists and runs build + test + lint on every push/PR (or filed as a CRITICAL process gap).
- [ ] No real secrets are committed; all secrets are externalized and gitignored.
- [ ] Dependencies are pinned/current; base images are pinned; no known-vulnerable versions.
- [ ] Dockerfiles use multi-stage, non-root, healthcheck, minimal context.
- [ ] nginx/Vercel set security headers and correct SPA fallback.
- [ ] Build artifacts and scratch files are gitignored, not committed.
- [ ] `.env.example` matches the real required variable set.

## Success criteria
The build/deploy inventory covers every artifact with an automation verdict; the missing-CI gap and
every committed-secret/hygiene issue is filed; dependency risk list is complete for both repos.

## Boundaries (do NOT review)
- Application code correctness (→ all code reviewers).
- Exploitability/severity of a leaked secret beyond flagging it (→ R05).
- SEO semantics of headers (→ R14; you own their presence/security).

## Suggested execution order
**Wave 1 (foundation).** Provides build/env context to R11; runs alongside R01/R04.

## Dependencies on other reviewers
None upstream. Downstream: R11 (build context), R15 (CI-runs-tests gap), R05 (secret severity),
R14 (headers).
