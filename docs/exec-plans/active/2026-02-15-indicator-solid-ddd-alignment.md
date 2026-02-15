# Indicator SOLID/DDD Alignment Roadmap

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `/hyperopen/.agents/PLANS.md`.

## Purpose / Big Picture

The indicator system still has domain math in view namespaces, fallback dispatch coupling, and mixed concerns between calculation and presentation. After this plan is complete, indicator calculation will live in semantic domain namespaces, the view layer will only project style metadata, and adding a new indicator family will not require editing the chart coordinator. The result is safer changes, lower regression risk, and reusable indicator logic for non-UI contexts.

## Progress

- [x] (2026-02-15 15:11Z) Created active ExecPlan and captured remaining SOLID/DDD gaps and migration milestones.
- [x] (2026-02-15 15:16Z) Milestone 1 completed: introduced domain-level registry orchestration and migrated remaining base calculators from `indicators.cljs` into domain namespaces.
- [x] (2026-02-15 15:18Z) Validated Milestone 1 with required gates: `npm run check`, `npm test`, `npm run test:websocket`.
- [ ] (2026-02-15 15:19Z) Milestone 2 in progress: completed wave3 trend-overlay family migration (`:guppy-multiple-moving-average`, `:mcginley-dynamic`, `:moving-average-adaptive`, `:moving-average-hamming`, `:williams-alligator`) and removed migrated wave3 implementations; remaining wave3 families are still pending.
- [ ] Milestone 3 pending: begin wave2 semantic extraction and progressively remove migrated wave2 implementations.
- [ ] Milestone 4 pending: harden boundaries (math adapter isolation, contract validation, parity/performance tests), then retire wave fallbacks.

## Surprises & Discoveries

- Observation: `src/hyperopen/views/trading_chart/utils/indicators.cljs` still contains many legacy private calculator functions that are no longer dispatched by `calculate-indicator`.
  Evidence: current `indicator-calculators` map only includes three IDs, while many private calculator fns remain in the file.
- Observation: Replacing the coordinator with a domain-registry path does not change behavior for existing tests when wave fallback order is preserved.
  Evidence: full required validation passed with 0 failures after the coordinator rewrite.
- Observation: Preserving wave3 parity for Williams Alligator required aligned RMA seeding semantics for shifted median series.
  Evidence: trend domain migration used `imath/rma-values` with `:aligned` for alligator lines and validation remained green.

## Decision Log

- Decision: Start this phase by extracting the last three coordinator-owned calculators (`:accumulation-distribution`, `:accumulative-swing-index`, `:average-price`) into domain namespaces and simplify the coordinator to orchestration-only.
  Rationale: This immediately removes active domain logic from the main view coordinator and creates a clean pattern for subsequent family migrations.
  Date/Author: 2026-02-15 / Codex
- Decision: Introduce a domain registry orchestration namespace before full polymorphic dispatch conversion.
  Rationale: This creates a single domain extension point now, while deferring a broader defmulti migration to later milestones to keep this slice low-risk.
  Date/Author: 2026-02-15 / Codex
- Decision: Migrate the wave3 trend-overlay family into `domain.trading.indicators.trend` as the first Milestone 2 batch.
  Rationale: This family is cohesive, high-visibility, and mostly independent from marker-heavy structure indicators, making it a low-risk semantic extraction step.
  Date/Author: 2026-02-15 / Codex

## Outcomes & Retrospective

Milestone 1 achieved the immediate separation-of-concerns target. `indicators.cljs` is now orchestration-only, three residual view-owned calculators were migrated into domain namespaces, and presentation metadata was centralized in the view adapter. Milestone 2 has begun with one complete wave3 family extraction (trend overlays) validated by required gates.

## Context and Orientation

Indicator code is currently split across these paths:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators.cljs` is the chart-facing coordinator and still contains legacy calculator code.
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave2.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/utils/indicators_wave3.cljs` are fallback calculation pools with many unmigrated indicators.
- `/hyperopen/src/hyperopen/domain/trading/indicators/*.cljs` holds the new semantic domain calculators (trend, oscillators, volatility, shared math/result contracts).
- `/hyperopen/src/hyperopen/views/trading_chart/utils/indicator_view_adapter.cljs` is the style projection layer and should be the only source of series names/colors/line styles.

For this plan, “domain calculator” means a pure-ish function that returns indicator type, pane, and raw series values via `hyperopen.domain.trading.indicators.result`; it does not construct chart point objects or hardcode display styling.

## Plan of Work

Milestone 1 removes residual domain logic from the coordinator and introduces a single domain registry entry point. Add a new domain registry namespace that aggregates indicator definitions and dispatches to family calculators. Migrate coordinator-owned calculators into dedicated domain namespaces and replace direct view-layer calculations with adapter projection of domain results.

Milestone 2 continues wave3 migration by semantic families. For each family, port calculators and definitions to domain modules, add view adapter metadata, remove migrated wave3 implementations, and re-run parity gates before proceeding.

Milestone 3 repeats the same pattern for wave2 families. The order will prioritize low-coupling, high-reuse indicators first, followed by heavier or externally-backed indicators.

Milestone 4 finalizes architecture boundaries. Isolate third-party math adapters behind domain math interfaces, add explicit parameter/result contract checks, add parity and performance tests for heavy algorithms, and remove wave fallbacks once all IDs are domain-owned.

## Concrete Steps

From `/hyperopen`:

1. Implement Milestone 1 files and coordinator simplification.
2. Run validation gates:
   - `npm run check`
   - `npm test`
   - `npm run test:websocket`
3. Continue Milestone 2 by extracting the next wave3 semantic family (structure/pattern or remaining oscillators), then re-run the same validation gates.
4. Record completed milestone details and move this plan to `/hyperopen/docs/exec-plans/completed/` when the full roadmap is done.

Expected validation transcript shape:

  - check: all lint/doc checks pass; `shadow-cljs compile app` and `shadow-cljs compile test` succeed with 0 warnings.
  - test: all test namespaces run; 0 failures, 0 errors.
  - websocket test: websocket suite runs; 0 failures, 0 errors.

## Validation and Acceptance

Milestone 1 acceptance:

- `indicators.cljs` is orchestration-only (domain registry + adapter projection + wave fallback).
- `:accumulation-distribution`, `:accumulative-swing-index`, and `:average-price` are calculated in domain namespaces, not view coordinator code.
- Presentation metadata for migrated series is owned by `indicator_view_adapter.cljs`.
- Required validation gates pass.

Roadmap acceptance:

- All indicator calculations are domain-owned.
- View namespaces contain projection/styling and chart interop only.
- Wave fallback namespaces are retired.
- Domain registry is the single extension point for indicator calculation.

## Idempotence and Recovery

Each family migration is additive then subtractive: first add domain implementation and adapter metadata, then remove wave/view implementation only after validation passes. If a regression appears, restore the removed family mapping in the prior owner namespace while preserving the domain implementation for investigation.

## Artifacts and Notes

Artifacts for each completed milestone will be added to dedicated completed ExecPlan records under `/hyperopen/docs/exec-plans/completed/` with validation evidence and file lists.

## Interfaces and Dependencies

The following public APIs must remain stable during this roadmap:

- `hyperopen.views.trading-chart.utils.indicators/get-available-indicators`
- `hyperopen.views.trading-chart.utils.indicators/calculate-indicator`
- `hyperopen.views.trading-chart.utils.indicators/calculate-sma`

New domain-level interfaces introduced by Milestone 1:

- `hyperopen.domain.trading.indicators.registry/get-domain-indicators`
- `hyperopen.domain.trading.indicators.registry/calculate-domain-indicator`

Plan revision note: 2026-02-15 15:11Z - Initial active roadmap created to drive remaining SOLID/DDD alignment work and start Milestone 1.
Plan revision note: 2026-02-15 15:18Z - Updated living sections after completing Milestone 1 implementation and validation.
Plan revision note: 2026-02-15 15:19Z - Updated living sections after completing first Milestone 2 migration batch (wave3 trend overlays) and validation.
