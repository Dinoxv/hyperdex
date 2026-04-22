# Split the oversized chart context menu overlay namespace

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. It is self-contained so a future contributor can continue from this file without relying on conversation history.

Tracked issue: `hyperopen-h4nb` ("Retire oversized chart context menu overlay namespace").

## Purpose / Big Picture

`src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs` is now the largest production namespace-size exception, measured at 844 lines. It owns the custom right-click chart menu for reset-view and copy-price actions. Because this is trading UI code, the split must preserve keyboard access, right-click behavior, copy precision, focus restoration, and cleanup. After this change, the public overlay namespace should remain the same for callers, the implementation should be split into smaller focused namespaces, and the stale source exception should be removed from `dev/namespace_size_exceptions.edn`.

## Progress

- [x] (2026-04-22 13:58Z) Created branch `codex/split-chart-context-menu-overlay`.
- [x] (2026-04-22 13:58Z) Created and claimed `bd` issue `hyperopen-h4nb`.
- [x] (2026-04-22 13:59Z) Identified `src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs` as the largest remaining production exception at 844 lines.
- [x] (2026-04-22 14:03Z) Split the overlay into focused implementation namespaces while preserving the public sync/clear API.
- [x] (2026-04-22 14:03Z) Removed the retired `src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs` entry from `dev/namespace_size_exceptions.edn`.
- [x] (2026-04-22 14:04Z) Ran focused context-menu overlay and wrapper tests; 18 tests, 93 assertions, 0 failures, 0 errors.
- [x] (2026-04-22 14:08Z) Ran required repository gates. `npm run check`, `npm test`, and `npm run test:websocket` passed.
- [x] (2026-04-22 14:09Z) Ran Playwright smoke. The full suite had one transient portfolio desktop route failure unrelated to this trade-chart overlay split; rerunning the failing portfolio/trader-portfolio smoke passed.
- [x] (2026-04-22 14:09Z) Ran governed design-review QA for `/trade`; all six passes returned `PASS` at 375, 768, 1280, and 1440 widths, with only state-sampling residual blind spots for hover/active/disabled/loading states not present by default.

## Surprises & Discoveries

- Observation: The broader `hyperopen.views.trading-chart.core-test` namespace fails when run directly by itself, but passes in the full `npm test` order.
  Evidence: `node out/test.js --test=hyperopen.views.trading-chart.core-test` failed on identity-cache and fallback-dispatch assertions. `npm test` later ran `hyperopen.views.trading-chart.core-test` as part of the full suite and passed with 3,383 tests and 18,432 assertions. The directly changed context-menu overlay suite also passed independently.

- Observation: The first full Playwright smoke run failed on `portfolio desktop root renders`, not on the trade route or chart surface.
  Evidence: `npm run test:playwright:smoke` reported 23 passed and one failed portfolio desktop smoke because the portfolio root oracle stayed absent for 10 seconds. A targeted rerun with `npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "portfolio desktop root renders"` passed both portfolio and trader-portfolio desktop smoke tests.

- Observation: Browser design QA for the changed route completed without issues.
  Evidence: `npm run qa:design-ui -- --targets trade-route --manage-local-app` returned `reviewOutcome: "PASS"` and `state: "PASS"` for `/trade` across 375, 768, 1280, and 1440 widths.

## Decision Log

- Decision: Preserve `hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay` as the public facade.
  Rationale: `src/hyperopen/views/trading_chart/utils/chart_interop.cljs`, chart runtime code, and tests already require this namespace. Keeping the facade stable makes the refactor behavior-preserving and avoids moving public API boundaries during a namespace-size cleanup.
  Date/Author: 2026-04-22 / Codex

- Decision: Split by operational responsibility: support/state, geometry, pricing, presentation, listeners, and facade orchestration.
  Rationale: The current file naturally mixes weak-map state, DOM style helpers, coordinate math, price label logic, menu DOM construction, event listener wiring, and action orchestration. Splitting along those seams lets future changes load only the relevant context without introducing a broader chart interop abstraction.
  Date/Author: 2026-04-22 / Codex

- Decision: Treat the context-menu overlay and wrapper tests as the focused validation command, and record isolated `core-test` behavior as an existing order-dependent test issue rather than expanding this refactor.
  Rationale: This change does not touch `src/hyperopen/views/trading_chart/core.cljs`; the directly changed overlay tests pass independently, and the full suite passes. Fixing isolated core-test assumptions would be a separate test-order cleanup outside this namespace split.
  Date/Author: 2026-04-22 / Codex

## Outcomes & Retrospective

Implemented and validated. The 844-line context menu overlay namespace is now a 296-line public orchestrator. Implementation code moved into five focused internal namespaces under `src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/`: `geometry.cljs` is 107 lines, `listeners.cljs` is 92 lines, `presentation.cljs` is 206 lines, `pricing.cljs` is 52 lines, and `support.cljs` is 137 lines. `dev/namespace_size_exceptions.edn` no longer carries the chart context-menu overlay exception, bringing the registry to 65 entries.

The change reduces maintenance complexity because future work on menu positioning, listener wiring, DOM presentation, or copy-price formatting can load a focused namespace. Runtime behavior remains the same public sync/clear API and the same chart-interaction behavior, backed by the focused fake-DOM tests, full test gates, Playwright smoke evidence, and design-review QA.

## Context and Orientation

The working directory is `/Users/barry/.codex/worktrees/c80b/hyperopen`.

The chart context menu overlay is the custom menu that opens on secondary mouse context-menu events inside the trading chart. It provides `Reset chart view` and `Copy price` menu items. The menu also opens from keyboard context-menu shortcuts, restores focus on close, closes on outside pointer/wheel/Escape interactions, ignores touch context-menu events, and removes DOM/listeners on cleanup.

The public namespace is `src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs`. Its public functions are:

- `sync-chart-context-menu-overlay!`, which mounts or updates the menu for a chart object, container, candles, and optional dependencies.
- `clear-chart-context-menu-overlay!`, which removes the menu DOM, listeners, timeouts, and weak-map state.

The wrapper namespace `src/hyperopen/views/trading_chart/utils/chart_interop.cljs` delegates to those functions. The focused test owner is `test/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay_test.cljs`; it intentionally reaches the private `overlay-state` var in the public namespace to assert state after fake DOM interactions. The facade should keep a private `overlay-state` helper that forwards to the new state owner so those tests and debug expectations remain stable.

Because this touches UI code under `src/hyperopen/views/**`, the relevant UI contracts are `/hyperopen/docs/FRONTEND.md`, `/hyperopen/docs/BROWSER_TESTING.md`, `/hyperopen/docs/agent-guides/browser-qa.md`, `/hyperopen/docs/agent-guides/ui-foundations.md`, and `/hyperopen/docs/agent-guides/trading-ui-policy.md`. This is a behavior-preserving source split, not a visual redesign. Browser QA must still be accounted for before signoff; if full governed browser design review cannot run, the blocker and residual blind spots must be recorded.

## Plan of Work

Create `src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/support.cljs` for shared weak-map state and low-level DOM/number helpers. It should own `overlay-state`, `set-overlay-state!`, `update-overlay-state!`, `delete-overlay-state!`, document/window/clipboard resolution, default timeout helpers, method invocation, numeric parsing, style setting, relative container enforcement, node containment, focus, blur, and menu sizing constants.

Create `geometry.cljs` for event coordinate extraction and menu placement. It should compute relative pointer anchors, keyboard anchors, and clamped menu positions using constants from `support.cljs`.

Create `pricing.cljs` for chart-coordinate price resolution, fallback candle price selection, precision-aware copy labels, and the final `resolve-copy-price-data` map.

Create `presentation.cljs` for menu DOM construction and visual state updates. It should build the root, panel, reset button, copy button, icon nodes, visibility state, button presentation, copy label text, reset state, highlight state, and root mounting. It must accept callback functions for keydown, click, and panel Escape behavior to avoid circular dependencies with orchestration.

Create `listeners.cljs` for container and document listener attach/teardown. It should own secondary-mouse and touch context-menu detection, call provided `open-menu!` and `close-menu!` callbacks, and preserve the current close/open behavior.

Rewrite `chart_context_menu_overlay.cljs` as the public orchestrator. It should keep `sync-chart-context-menu-overlay!`, `clear-chart-context-menu-overlay!`, private `overlay-state`, copy feedback scheduling, open/close, focus-menu-item, copy-price, action dispatch, and sync orchestration. It should delegate support, geometry, pricing, presentation, and listener work to the focused modules.

Remove the matching source exception from `dev/namespace_size_exceptions.edn` once all new source files and the public facade are under 500 lines.

## Concrete Steps

1. Confirm the starting line count:

    wc -l src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs

2. Add focused implementation namespaces under `src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/`.

3. Rewrite the public overlay namespace as a facade/orchestrator that preserves the same public function names and behavior.

4. Remove only the `src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs` entry from `dev/namespace_size_exceptions.edn`.

5. Verify line counts:

    wc -l src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/*.cljs

   Completed output:

       296 src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs
       107 src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/geometry.cljs
        92 src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/listeners.cljs
       206 src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/presentation.cljs
        52 src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/pricing.cljs
       137 src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/support.cljs
       890 total

6. Run focused chart overlay coverage:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay-test --test=hyperopen.views.trading-chart.utils.chart-interop-test

   Completed result: 18 tests, 93 assertions, 0 failures, 0 errors.

7. Run required repository gates:

    npm run check
    npm test
    npm run test:websocket

   Completed results: `npm run check` passed; `npm test` passed with 3,383 tests, 18,432 assertions, 0 failures, and 0 errors; `npm run test:websocket` passed with 461 tests, 2,798 assertions, 0 failures, and 0 errors.

8. Account for UI/browser QA. The smallest deterministic browser command for this trading chart interaction surface is:

    npm run test:playwright:smoke

   Completed result: first full run had 23 passed and one transient portfolio desktop route failure. Targeted rerun of the failing portfolio smoke passed 2 tests with 0 failures. Trade desktop and trade mobile smoke checks passed in the full run.

9. Run governed design-review QA for `/trade`:

    npm run qa:design-ui -- --targets trade-route --manage-local-app
    npm run browser:cleanup

   Completed result: design review returned `PASS` for visual evidence, native-control, styling-consistency, interaction, layout-regression, and jank/perf at 375, 768, 1280, and 1440 widths. Artifact run directory: `/Users/barry/.codex/worktrees/c80b/hyperopen/tmp/browser-inspection/design-review-2026-04-22T14-09-05-513Z-356b0806`. Cleanup returned `{"ok": true, "stopped": []}`.

## Validation and Acceptance

The work is accepted when all of these are true:

1. `src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs` is under 500 lines and still exposes `sync-chart-context-menu-overlay!` and `clear-chart-context-menu-overlay!`.

2. Every new `src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/*.cljs` file is under 500 lines.

3. `dev/namespace_size_exceptions.edn` no longer includes `src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs`.

4. Existing callers still require `hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay` without code changes.

5. Focused tests still cover right-click open and bounds flipping, keyboard open/navigation/Escape close, copy feedback and timeout close, full-precision copy payload, disabled copy state, context-key close, cleanup, outside close focus blur, and touch context-menu ignore.

6. `npm run check`, `npm test`, and `npm run test:websocket` pass, or any unrelated blocker is recorded with exact output.

7. Browser QA for the UI-facing chart interaction is explicitly accounted for with `PASS`, `FAIL`, or `BLOCKED` for visual, native-control, styling-consistency, interaction, layout-regression, and jank/perf passes.

## Idempotence and Recovery

The split is source-only and behavior-preserving. It can be retried safely by rerunning focused tests after each module extraction. If a circular dependency or private test seam breaks, keep the public facade stable and move only the problematic helper back into the facade until tests pass. Do not run destructive git commands or revert unrelated user work.

## Artifacts and Notes

Initial baseline:

    src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs: 844 lines

Completed source line counts:

    src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay.cljs: 296 lines
    src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/geometry.cljs: 107 lines
    src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/listeners.cljs: 92 lines
    src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/presentation.cljs: 206 lines
    src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/pricing.cljs: 52 lines
    src/hyperopen/views/trading_chart/utils/chart_interop/chart_context_menu_overlay/support.cljs: 137 lines

Focused validation:

    Testing hyperopen.views.trading-chart.utils.chart-interop-test
    Testing hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay-test
    Ran 18 tests containing 93 assertions.
    0 failures, 0 errors.

Required validation:

    npm run check: passed
    npm test: Ran 3383 tests containing 18432 assertions. 0 failures, 0 errors.
    npm run test:websocket: Ran 461 tests containing 2798 assertions. 0 failures, 0 errors.

Browser QA:

    npm run test:playwright:smoke: 23 passed, 1 transient portfolio desktop route failure.
    npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "portfolio desktop root renders": 2 passed.
    npm run qa:design-ui -- --targets trade-route --manage-local-app: PASS.
    npm run browser:cleanup: ok, stopped 0 sessions.

Design-review pass accounting:

    visual: PASS
    native-control: PASS
    styling-consistency: PASS
    interaction: PASS
    layout-regression: PASS
    jank/perf: PASS

Residual browser-QA blind spots: the design-review tool reported state-sampling limits for hover, active, disabled, and loading states that are not present by default at each required width. The focused fake-DOM context-menu tests cover right-click open, keyboard open/navigation/Escape, copy enabled/disabled states, timeout close, outside close, cleanup, and touch context-menu ignore.

## Interfaces and Dependencies

The public interface remains `hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay`. New implementation namespaces are internal to the chart context-menu overlay boundary and should not be required by unrelated chart modules unless a later plan intentionally promotes them.

## Revision Notes

- 2026-04-22 / Codex: Created the active ExecPlan after creating branch `codex/split-chart-context-menu-overlay`, claiming `hyperopen-h4nb`, reading UI browser QA docs, and selecting the largest remaining production namespace-size exception.
- 2026-04-22 / Codex: Recorded the completed module split, namespace-size exception removal, validation results, Playwright transient rerun, and governed design-review QA evidence before moving the plan to completed.
