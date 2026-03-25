# Tech Debt Tracker

## Purpose
Track known debt with clear owner and retirement path.

## Entries
- Debt summary: Locale-aware numeric parsing now covers audited high-traffic decimal boundaries and low-traffic integer input boundaries. Remaining debt is primarily regression prevention for newly added input surfaces.
  Owner team: Platform
  Impact: Uncovered/new input surfaces can still regress for international users if they bypass locale-aware parsing utilities.
  Retirement criteria: Maintain a recurring boundary audit for user-entered decimal paths, require locale-aware parsing in new numeric input transitions, enforce boundary lint guardrails in `npm run check`, and keep regression coverage plus gate validation (`npm test`, `npm run check`, `npm run test:websocket`) for each new input feature.
  Tracking reference: `/hyperopen/docs/exec-plans/completed/2026-03-02-international-number-formatting-migration.md`; `/hyperopen/docs/exec-plans/completed/2026-03-02-locale-input-parsing-boundaries-order-and-position-modals.md`; `/hyperopen/docs/exec-plans/completed/2026-03-02-locale-input-parsing-order-form-leverage.md`; `/hyperopen/docs/exec-plans/completed/2026-03-02-locale-input-parsing-currency-helper-standardization.md`; `/hyperopen/docs/exec-plans/completed/2026-03-02-locale-input-parsing-low-traffic-integer-boundaries.md`; `/hyperopen/docs/exec-plans/completed/2026-03-02-input-parsing-guardrail-lint.md`
- Debt summary: Namespace-size guardrails are now enforced, but the baseline repo still carries explicit temporary exceptions for oversized source and test namespaces in `/hyperopen/dev/namespace_size_exceptions.edn`.
  Owner team: Platform
  Impact: Large namespaces still concentrate change fan-out, make reviews slower, and increase merge pressure until the split wave retires those exceptions.
  Retirement criteria: Each entry must shrink to `<= 500` lines or move behind a thinner facade before its `:retire-by` date, and the registry must stay free of stale or expired entries while `npm run check`, `npm test`, and `npm run test:websocket` remain green.
  Tracking reference: `/hyperopen/docs/exec-plans/active/2026-03-24-architecture-audit-remediation-wave.md`; `/hyperopen/docs/exec-plans/deferred/2026-02-25-file-size-guardrail-exceptions-splitting-strategy-maintainability.md`; `/hyperopen/dev/namespace_size_exceptions.edn`
- Debt summary: Non-view imports from `hyperopen.views.*` are now enforced through `/hyperopen/dev/namespace_boundary_exceptions.edn`, and `DIP-01` has already retired the generic helper imports, but six temporary exceptions still remain for bootstrap bridges and `DIP-02`.
  Owner team: Platform
  Impact: These imports still blur ownership boundaries and let non-view code depend on view-layer helpers or models until the planned extractions land.
  Retirement criteria: `DIP-02` must retire the `views.vaults.vm` and `portfolio.vm.metrics-bridge` exceptions, and the remaining bootstrap / console-preload bridges need either extraction or an explicit permanent owner before their `:retire-by` dates. The registry must stay aligned with the live imports so resolved exceptions are removed immediately.
  Tracking reference: `/hyperopen/docs/exec-plans/active/2026-03-24-architecture-audit-remediation-wave.md`; `/hyperopen/dev/namespace_boundary_exceptions.edn`
