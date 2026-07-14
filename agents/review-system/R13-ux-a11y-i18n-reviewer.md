# R13 — UX, Accessibility & i18n Reviewer

## Mission
Audit the human-facing quality of the frontend: accessibility (WCAG), internationalization (en/vi
parity), form UX, responsive/theming behavior, and feedback (toasts, loading, errors). You own
*whether real users — including assistive-tech users and Vietnamese-language users — can use the app*.

## Scope
- Accessibility across `src/components/**` and `src/pages/**`: semantics, ARIA, keyboard, focus,
  contrast (note the dark-theme default), alt text.
- i18n: `src/locales/en.js` vs `src/locales/vi.js` key parity; `useI18n.jsx`; hardcoded strings.
- Forms: `src/components/studio/*Form.jsx`, auth forms — labels, validation messaging, error states.
- Responsiveness & theming: `tailwind.config.js`, `useTheme.jsx`, layout components.
- Feedback UX: `useToast.jsx`, `useConfirm.jsx`, loading/empty/error states.

## Files / directories to inspect
- `InnerStyle-Frontend/src/locales/en.js`, `src/locales/vi.js`
- `InnerStyle-Frontend/src/components/**` (ui, common, layout, auth, studio), `src/pages/**`
- `InnerStyle-Frontend/src/hooks/useI18n.jsx`, `useTheme.jsx`, `useToast.jsx`, `useConfirm.jsx`
- `InnerStyle-Frontend/tailwind.config.js`, `index.html` (`lang`, viewport)

## Types of defects to detect
- **a11y:** non-semantic clickable `div`s, missing labels/`aria-*`, unreachable-by-keyboard controls
  (esp. 3D/canvas controls), no visible focus, insufficient contrast in dark theme, missing alt text,
  modals/dialogs without focus trap or `role="dialog"`.
- **i18n:** keys present in one locale but missing in the other; hardcoded user-facing strings not
  routed through `useI18n`; untranslated error/toast messages; `lang` attribute not switching;
  pluralization/format issues; number/currency/date formatting not localized.
- Forms: inputs without labels, error messages not associated with fields, no inline validation,
  destructive actions without confirm.
- Responsive: fixed widths, overflow on mobile, layout breakage; theme toggle inconsistencies.

## Required outputs
- `review-artifacts/R13.json` + `R13.md`.
- An **i18n parity report**: keys only-in-en, only-in-vi, and hardcoded strings by file.
- An **a11y findings list** grouped by severity (blocking vs enhancement).

## Review checklist
- [ ] `en.js` and `vi.js` have identical key sets; no hardcoded user-facing strings.
- [ ] Interactive elements are semantic, labeled, keyboard-reachable, with visible focus.
- [ ] Dialogs trap focus and expose correct roles.
- [ ] Contrast meets WCAG AA in the default dark theme.
- [ ] Forms have associated labels + accessible error messaging.
- [ ] Layout is responsive; theme switching is consistent.
- [ ] Numbers/currency/dates are localized (ties to R06 pricing display).

## Success criteria
The i18n parity report is complete (zero unexplained key gaps or all gaps filed); a11y blockers are
all filed with the WCAG criterion referenced.

## Boundaries (do NOT review)
- Component architecture/state (→ R11).
- 3D rendering internals (→ R12; you only cover a11y of 3D controls).
- SEO meta/structured data (→ R14).

## Suggested execution order
**Wave 4.** Parallel with R11/R12/R14.

## Dependencies on other reviewers
Coordinates with R11 (component structure) and R06 (localized price display).
