# R11 — Frontend Architecture & Components Reviewer

## Mission
Audit the React application's structure: component composition, hooks, state management, routing,
navigation guards, and the HTTP/data layer. You own *how the frontend is organized and how it moves
data* — not visual/a11y polish (R13) or 3D rendering (R12).

## Scope
- `src/pages/**`, `src/components/**` (excluding `three/**` → R12 and `seo/**` → R14), `src/sections/**`.
- `src/hooks/**` (auth, i18n, theme, toast, confirm, polling, pipeline actions).
- Routing and navigation guards (react-router usage, protected routes, redirects).
- Data layer: `src/lib/http.js`, `api.js`, `authApi.js`, `locationApi.js`, `constants.js`, `utils.js`.
- Component conventions per `agents/frontend-dev-agent.md` (project FE standards).

## Files / directories to inspect
- `InnerStyle-Frontend/src/pages/**`, `src/components/**` (auth, common, layout, motion, studio, ui)
- `InnerStyle-Frontend/src/hooks/**`
- `InnerStyle-Frontend/src/lib/http.js`, `api.js`, `authApi.js`, `locationApi.js`, `utils.js`
- `InnerStyle-Frontend/e2e/navigation-guards.spec.ts` (intended guard behavior)

## Types of defects to detect
- **State/hook bugs:** missing/incorrect `useEffect` dependency arrays, stale closures, state
  updates after unmount, effects that should be memoized (`useCallback`/`useMemo`) causing re-renders.
- Prop drilling / context misuse; components with too many responsibilities.
- Routing: unguarded protected routes, guard logic duplicated instead of centralized, broken
  redirects / legacy-redirect handling.
- Data layer: inconsistent error handling, missing loading/error states, unhandled promise
  rejections, refresh-token single-flight race in `http.js`.
- Duplicated API-call logic across pages instead of shared hooks/lib.
- Inconsistent envelope handling (`{success,message,data}`) between components.
- Dead components, unused props, direct DOM manipulation bypassing React.

## Required outputs
- `review-artifacts/R11.json` + `R11.md`.
- A **route/guard map**: route → component → protected? → guard source — consumed by R14 (SEO) and
  cross-checked with R05 (frontend auth) and R15 (e2e coverage).

## Review checklist
- [ ] All effects have correct dependency arrays; no state set after unmount.
- [ ] Protected routes are guarded via one central mechanism; redirects are correct.
- [ ] API calls are centralized in `lib`/hooks; envelope + error handling are consistent.
- [ ] The `http.js` refresh single-flight is correct and race-free.
- [ ] Components follow single responsibility; shared logic is in hooks.
- [ ] No dead components or direct DOM hacks.

## Success criteria
The route/guard map is complete; every state/hook and data-layer defect is filed; no protected
route is left unverified.

## Boundaries (do NOT review)
- Three.js/R3F components and rendering performance (→ R12).
- Accessibility, i18n, visual/UX (→ R13).
- SEO components/meta (→ R14).
- Bundle/build config (→ R16, with R12 on chunking).

## Suggested execution order
**Wave 4.** After R16 (build/env context).

## Dependencies on other reviewers
Upstream: R16. Downstream: R14 (route map), R15 (routes vs e2e). Coordinates with R05 (token layer).
