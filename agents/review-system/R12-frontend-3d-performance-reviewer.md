# R12 — 3D / Rendering & Frontend Performance Reviewer

## Mission
Audit the Three.js / React-Three-Fiber / drei rendering stack and overall frontend runtime and
load performance. This app's core value is real-time 3D + AR, so GPU resource discipline and bundle
weight are first-class concerns. You own *rendering correctness, memory/GPU safety, and performance*.

## Scope
- `src/components/three/**`: `ModelViewer`, `ModelEditor`, `ArPreview`, `ShowcaseModel`, `SignaturePad`.
- 3D asset handling: model load (`convertModel.js`, `export3mf.js`), disposal, AR (`ar.js`, `ArView.jsx`).
- Bundle/perf: `vite.config.js` manualChunks (three/motion split), lazy loading, image/asset weight.
- Runtime: render loops, re-renders, `framer-motion` usage cost.

## Files / directories to inspect
- `InnerStyle-Frontend/src/components/three/**`
- `InnerStyle-Frontend/src/lib/convertModel.js`, `export3mf.js`, `ar.js`
- `InnerStyle-Frontend/src/pages/ArView.jsx`, `Studio.jsx`, `Gallery.jsx`, `Landing.jsx`
- `InnerStyle-Frontend/vite.config.js` (manualChunks), `index.html` (asset preloads)

## Types of defects to detect
- **GPU/memory leaks:** geometries/materials/textures/render targets not `dispose()`d on unmount;
  loaders not cleaned; growing scene graph on re-generation.
- Render-loop waste: unnecessary `useFrame` work, no frameloop="demand" where static, animating
  offscreen models.
- Excessive re-renders of R3F trees from unstable props/parent state (coordinate with R11).
- Oversized/uncompressed models or textures loaded eagerly on the Landing page.
- Bundle bloat: three/drei not effectively code-split; heavy libs on first paint; missing lazy
  routes for Studio/AR/Gallery.
- AR path: WebXR/quicklook feature-detection missing; failure not handled gracefully.
- Main-thread blocking during model conversion/export (should be async/worker where feasible).

## Required outputs
- `review-artifacts/R12.json` + `R12.md`.
- A **resource-lifecycle table** for each 3D component: created resources × disposed on unmount?
- A **bundle/perf note**: initial payload contributors and lazy-load opportunities.

## Review checklist
- [ ] Every geometry/material/texture/render target is disposed on unmount/replacement.
- [ ] Static scenes use on-demand rendering; no needless per-frame work.
- [ ] R3F subtrees don't re-render from unstable props; heavy props memoized.
- [ ] Heavy 3D libs are code-split; Studio/AR/Gallery are lazy-loaded.
- [ ] Landing page does not eagerly load large models/textures.
- [ ] AR capability is feature-detected and degrades gracefully.
- [ ] Long conversions/exports don't block the main thread.

## Success criteria
The resource-lifecycle table covers every 3D component with a disposal verdict; leaks and
bundle/perf regressions are filed with concrete fixes.

## Boundaries (do NOT review)
- Non-3D component structure/state (→ R11).
- Accessibility of 3D controls beyond noting keyboard/focus gaps (→ R13).
- Build tooling correctness itself (→ R16); you own the *performance* implication of chunking.

## Suggested execution order
**Wave 4.** Parallel with R11/R13/R14.

## Dependencies on other reviewers
Coordinates with R11 (re-render sources) and R16 (build chunking).
