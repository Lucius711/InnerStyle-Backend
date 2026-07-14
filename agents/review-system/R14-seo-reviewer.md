# R14 — SEO & Metadata Reviewer

## Mission
Audit discoverability and crawlability of the SPA: meta tags, Open Graph, structured data, robots,
sitemap, canonical URLs, and SPA-specific SEO hazards. The repo already ships a `SEO_AUDIT.md` — you
verify the code matches it and close the remaining gaps. You own *how search engines and social
platforms see the site*.

## Scope
- `src/components/seo/Seo.jsx` and its usage across pages.
- `index.html` head: title, description, robots, Open Graph, theme-color, `lang`, viewport.
- Robots/sitemap/canonical: `public/**`, `vercel.json`, `nginx.conf` routing/headers.
- Per-route metadata (title/description/OG) driven by the router (uses R11's route map).
- The existing `SEO_AUDIT.md` as the baseline spec.

## Files / directories to inspect
- `InnerStyle-Frontend/src/components/seo/Seo.jsx`
- `InnerStyle-Frontend/index.html`
- `InnerStyle-Frontend/public/**` (robots.txt, sitemap.xml, favicons, OG images)
- `InnerStyle-Frontend/vercel.json`, `nginx.conf`
- `InnerStyle-Frontend/SEO_AUDIT.md`

## Types of defects to detect
- Missing/duplicate per-route `<title>`/meta description; no canonical URLs.
- Open Graph / Twitter card gaps (missing image, wrong dimensions, absolute URLs).
- No `robots.txt` / `sitemap.xml`, or robots blocking indexable content (recall `robots: index,follow`).
- SPA SEO hazard: client-only rendering with no prerender/SSR/meta injection — crawlers see an
  empty shell (assess impact and recommend prerender if warranted).
- Missing structured data (JSON-LD) for product/organization where relevant.
- Broken legacy redirects / non-canonical duplicates (cross-check `legacy-redirects.spec.ts`).
- `lang` not reflecting active locale (ties to R13); missing hreflang for en/vi.
- Caching/headers in `nginx.conf`/`vercel.json` that harm crawl or serve stale meta.

## Required outputs
- `review-artifacts/R14.json` + `R14.md`.
- A **per-route SEO matrix**: route × title × description × canonical × OG × indexable — built on
  R11's route map, so no route is missed.
- A reconciliation note: `SEO_AUDIT.md` claims vs current code (closed / still open).

## Review checklist
- [ ] Every indexable route has a unique title, description, and canonical.
- [ ] Open Graph + Twitter tags are complete with absolute image URLs.
- [ ] `robots.txt` and `sitemap.xml` exist and are correct; nothing indexable is blocked.
- [ ] SPA meta is visible to crawlers (prerender/SSR or documented acceptance).
- [ ] Structured data present where it adds value.
- [ ] Legacy redirects resolve to canonical URLs.
- [ ] hreflang/`lang` correct for en/vi.

## Success criteria
The per-route SEO matrix covers every route from R11's map; all `SEO_AUDIT.md` items are marked
closed or filed as findings.

## Boundaries (do NOT review)
- Component structure/routing correctness (→ R11; you consume the route map).
- a11y and i18n text (→ R13; you cover crawlability of localized routes).
- Deploy/hosting correctness beyond SEO headers (→ R16).

## Suggested execution order
**Wave 4.** After R11 (needs route map).

## Dependencies on other reviewers
Upstream: R11 (route map). Coordinates with R13 (lang/hreflang), R16 (headers).
