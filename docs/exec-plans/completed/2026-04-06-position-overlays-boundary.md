# Split the trading chart position overlay boundary

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `/hyperopen/docs/PLANS.md` and `/hyperopen/.agents/PLANS.md`.

Tracked `bd` issue: `hyperopen-wmr7` (`Split trading chart position overlays boundary`).

## Purpose / Big Picture

Today, `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` is the largest remaining requested UI hotspot after the websocket runtime reducer split. It mixes pure overlay formatting and geometry, DOM row creation and patching, repaint scheduling, chart subscription wiring, and liquidation-drag runtime state inside one oversized namespace. After this change, the public `sync-position-overlays!` and `clear-position-overlays!` entrypoints must continue to drive the same chart behavior, but dedicated helper namespaces will own overlay layout and DOM patching with direct boundary tests so contributors can reason about the chart overlay stack in smaller pieces.

The visible proof is behavior-preserving. The trade chart must still render the retained PNL and liquidation rows, keep badge alignment and colors stable, preserve live liquidation-drag preview and confirm callbacks, and avoid full DOM teardown on ordinary repaint updates. A contributor should be able to run the focused CLJS suites, the smallest relevant Playwright trade smoke, the governed trade-route browser QA, and the required repo gates to verify that the split changed boundaries, not user-visible behavior.

## Progress

- [x] (2026-04-06 22:23Z) Reviewed `/hyperopen/AGENTS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, `/hyperopen/docs/MULTI_AGENT.md`, `/hyperopen/docs/BROWSER_TESTING.md`, and `/hyperopen/docs/agent-guides/browser-qa.md` for the current repo contract.
- [x] (2026-04-06 22:24Z) Compared the requested exception-list candidates against current source sizes and selected `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` as the next highest remaining target after the completed `runtime_reducer` split.
- [x] (2026-04-06 22:24Z) Created and claimed `bd` issue `hyperopen-wmr7` for this boundary refactor.
- [x] (2026-04-06 22:26Z) Mapped the current overlay namespace into three concerns: pure layout and drag-suggestion logic, DOM row creation and patching, and runtime orchestration for subscriptions, repaint scheduling, and drag listeners.
- [x] (2026-04-06 22:28Z) Created this active ExecPlan.
- [x] (2026-04-06 22:31Z) Extracted the pure layout and formatting helpers into `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_layout.cljs` and added direct boundary tests under `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_layout_test.cljs`.
- [x] (2026-04-06 22:34Z) Extracted DOM node ownership and row patching helpers into `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_dom.cljs` and moved DOM-focused regression coverage into `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_dom_test.cljs`.
- [x] (2026-04-06 22:35Z) Rewired `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` to remain the runtime facade only, preserving `render-overlays!`, `sync-position-overlays!`, and `clear-position-overlays!` behavior.
- [x] (2026-04-06 22:36Z) Reduced the source and root test owners below the namespace-size threshold, removed their stale entries from `/hyperopen/dev/namespace_size_exceptions.edn`, and regenerated `/hyperopen/test/test_runner_generated.cljs`.
- [x] (2026-04-06 22:40Z) Restored the incomplete local `node_modules` state with `npm ci` after the first `node out/test.js` run failed on missing `lucide/dist/esm/icons/external-link.js`, then reran the CLJS suite successfully.
- [x] (2026-04-06 22:41Z) Passed the smallest relevant committed browser regression with `npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "trade"` (`9 passed`).
- [x] (2026-04-06 22:41Z) Passed governed browser QA with `npm run qa:design-ui -- --targets trade-route --manage-local-app`; the trade-route design review reported `PASS` across widths `375`, `768`, `1280`, and `1440`, then browser-inspection cleanup completed with `npm run browser:cleanup`.
- [x] (2026-04-06 22:42Z) Passed the required repo gates: `npm run check`, `npm test`, and `npm run test:websocket`.
- [x] (2026-04-06 22:42Z) Updated this ExecPlan with final evidence, closed `hyperopen-wmr7`, and moved the plan to `/hyperopen/docs/exec-plans/completed/`.

## Surprises & Discoveries

- Observation: the current overlay file already groups naturally into three boundaries by line range instead of one tangled state machine.
  Evidence: lines `1-259` are mostly parsing, formatting, badge-placement, and drag math; lines `260-689` are overlay-root and row DOM ownership; lines `690-1028` are repaint scheduling, subscriptions, drag runtime, render orchestration, and the public sync/clear entrypoints.

- Observation: the root overlay test file is also oversized and already mixes runtime, DOM-retention, and formatting assertions.
  Evidence: `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs` is `679` lines long, with some tests asserting retained-node patching and text-node identity while others assert subscription coalescing and drag callback behavior.

- Observation: there is no existing dedicated boundary split for `position_overlays` despite multiple earlier feature and performance changes in the same file.
  Evidence: the completed plans on `2026-03-05` and `2026-03-06` improved incremental DOM patching and live drag behavior in place, but `/hyperopen/dev/namespace_size_exceptions.edn` still carries the source file at `1028` lines and the root test at `679` lines.

- Observation: the current worktree still had the incomplete `node_modules` state seen in recent hotspot plans, and the first main CLJS test run failed before exercising the overlay changes.
  Evidence: `node out/test.js` failed on `Cannot find module 'lucide/dist/esm/icons/external-link.js'`; `npm ls lucide` returned `(empty)`, and `npm ci` restored the dependency tree so `npm test` and the other required gates could run.

- Observation: the first DOM boundary pass surfaced a real split hazard: prop-text destructuring can mask retained node locals and make style patching try to write onto strings.
  Evidence: the first run of the new suites failed with `TypeError: Cannot read properties of undefined (reading 'whiteSpace')` until `apply-pnl-row!` and `apply-liquidation-row!` stopped destructuring `:badge-text`, `:chip-text`, `:label-text`, `:price-text`, and `:drag-note-text` into locals with the same names as the DOM elements.

## Decision Log

- Decision: target `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` as the next requested exception-list owner.
  Rationale: `runtime_reducer` is already complete, `funding/effects` and `vaults/effects` have had prior decomposition waves, and `position_overlays` remains both the largest requested source owner and an interaction-heavy trade-route boundary with no dedicated split yet.
  Date/Author: 2026-04-06 / Codex

- Decision: split the overlay owner into a pure layout namespace and a DOM namespace, leaving the existing root file as the runtime facade.
  Rationale: this follows the actual concern boundaries already present in the file and preserves the public overlay API that callers in `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs` and `/hyperopen/src/hyperopen/views/trading_chart/core.cljs` already use.
  Date/Author: 2026-04-06 / Codex

- Decision: split the oversized test owner the same way as the source owner instead of only adding more assertions to the root suite.
  Rationale: the request explicitly calls for boundary tests during the split, and leaving all new assertions in the current root test would preserve the same oversized test hotspot even if the source file shrinks.
  Date/Author: 2026-04-06 / Codex

## Outcomes & Retrospective

Implementation landed and reduced overall complexity. `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` now stays focused on sidecar runtime state, repaint scheduling, chart subscriptions, and liquidation drag orchestration, while `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_layout.cljs` owns the pure badge, color, label, visibility, and drag-suggestion rules, and `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_dom.cljs` owns retained row DOM creation and patching.

The split also cleaned up the tests instead of leaving the same oversized test concentration in place. `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs` now covers runtime behavior only, while the new `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_layout_test.cljs` and `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_dom_test.cljs` provide direct boundary coverage for the extracted logic.

The guardrail result is concrete. The source root dropped from `1028` lines to `408`, and the root test dropped from `679` lines to `275`. Both stale namespace-size exceptions were removed from `/hyperopen/dev/namespace_size_exceptions.edn`. Complexity went down because three different responsibilities no longer need to be understood, edited, and tested through one mutable owner.

## Context and Orientation

The public chart overlay path begins in `/hyperopen/src/hyperopen/views/trading_chart/core.cljs`, which computes a `position-overlay` data map and passes it into `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs`. That interop facade delegates to `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs`, which currently owns everything: sidecar runtime state in a `WeakMap`, overlay formatting and color rules, badge placement, DOM creation and patching for the PNL and liquidation rows, repaint scheduling with `requestAnimationFrame`, chart subscription wiring, and the liquidation drag listener lifecycle.

In this chart stack, a “boundary” means a namespace that owns one coherent responsibility and can be tested directly without driving unrelated runtime machinery. The current file contains two obvious candidate boundaries that are presently embedded inside the runtime facade. First, pure layout and drag-suggestion logic determines tone, text, badge positions, chip labels, and margin-delta suggestions from overlay data. Second, DOM ownership logic creates the retained overlay row nodes and patches their styles and text nodes in place. The remaining runtime owner needs to keep the sidecar, schedule or cancel repaint frames, subscribe to chart events, react to drag pointer events, and call the new helper namespaces to render or hide the rows.

The current tests mirror the same mixing problem. `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs` already checks retained-node behavior, text-node identity, badge alignment, drag preview and confirm callbacks, and repaint coalescing. That file can be reduced by moving direct layout assertions into a new boundary suite and moving direct DOM patching assertions into a DOM boundary suite, while the root test keeps runtime behavior such as unchanged-input short-circuiting, repaint scheduling, and drag callback emission.

The repo enforces a namespace-size threshold of `500` lines via `/hyperopen/dev/check_namespace_sizes.clj`. Both `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` and `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs` are still on `/hyperopen/dev/namespace_size_exceptions.edn`. A successful split should reduce both files below threshold and remove those stale exceptions if the final line counts allow it.

Because this work touches the trade route UI under `/hyperopen/src/hyperopen/views/**`, the repo requires explicit browser accounting. Use the smallest relevant committed Playwright route smoke first, then the governed trade-route browser QA pass that records `PASS`, `FAIL`, or `BLOCKED` across all required review widths and passes. The relevant command contract is in `/hyperopen/docs/BROWSER_TESTING.md` and `/hyperopen/docs/agent-guides/browser-qa.md`.

## Plan of Work

First, extract the pure formatting and geometry logic from `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` into `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_layout.cljs`. This new namespace should own number parsing helpers, tone and color selection, price and PNL text formatting, badge width and anchor calculations, visibility checks, margin-delta and drag-suggestion derivation, event-anchor geometry, and small data builders that describe the PNL row and liquidation row in a DOM-agnostic way. Keep this namespace pure wherever possible so its tests can drive it directly without fake chart nodes.

Second, extract retained DOM ownership into `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_dom.cljs`. This namespace should own overlay-root creation, retained row node creation, text-node allocation, style patching, root attachment, and row hide operations. It should consume already-computed layout data from the layout namespace rather than recomputing formatting or geometry internally. This keeps the DOM namespace focused on node lifecycle and patch semantics.

Third, rewrite `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` as the runtime facade. It should keep the sidecar `WeakMap`, schedule and cancel repaint frames, subscribe and unsubscribe chart listeners, manage drag listeners, compute chart coordinates and pane dimensions, call the layout namespace to produce row data, call the DOM namespace to patch or hide the rows, and preserve the existing public APIs: `render-overlays!`, `sync-position-overlays!`, and `clear-position-overlays!`. It must preserve the current drag callback payload shape, unchanged-input short-circuiting, and root cleanup semantics.

Fourth, split the tests by the same boundaries. Add `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_layout_test.cljs` for direct coverage of badge placement clamps, price and PNL label formatting, visible-row gating, and liquidation drag suggestion rules. Add `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_dom_test.cljs` for retained-node, text-node, and row patching behavior using the existing fake DOM helpers. Keep `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs` for runtime behaviors such as subscription repaint coalescing, unchanged-input short-circuiting, and drag preview or confirm emission through the public facade.

Finally, run focused validation before the broad repo gates. For UI work, the smallest relevant Playwright check is the trade route smoke. After the focused CLJS overlay suites are green, run the trade smoke, the governed trade-route design review, and then the required repo gates. If the source and root test both end below `500` lines, remove their entries from `/hyperopen/dev/namespace_size_exceptions.edn`; otherwise refresh the exact allowed line counts honestly.

## Concrete Steps

Work from `/Users/barry/.codex/worktrees/52fe/hyperopen`.

1. Add new source namespaces:

   - `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_layout.cljs`
   - `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_dom.cljs`

2. Update the runtime facade:

   - `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs`

3. Add and split tests:

   - `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_layout_test.cljs`
   - `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_dom_test.cljs`
   - `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs`

4. Update the namespace-size registry only after final line counts are known:

   - `/hyperopen/dev/namespace_size_exceptions.edn`

5. Run focused CLJS validation for the split overlay suites:

   - `npm run test:runner:generate`
   - `npx shadow-cljs --force-spawn compile test`
   - `node out/test.js`

6. Run the smallest relevant committed browser regression first:

   - `npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "trade"`

7. Run governed browser QA for the trade route:

   - `npm run qa:design-ui -- --targets trade-route --manage-local-app`

8. Run the required repo gates:

   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

9. Update this ExecPlan with concrete command transcripts, close `hyperopen-wmr7`, and move the file to `/hyperopen/docs/exec-plans/completed/`.

## Validation and Acceptance

The split is complete only when all of the following are true.

First, the layout boundary has direct tests proving the pure rules that were previously buried in the runtime owner: badge position clamping stays inside readable chart space, PNL and axis labels preserve the current output formatting, liquidation margin suggestions preserve add or remove semantics and minimum thresholds, and visible-row gating still hides rows that drift outside the chart pane.

Second, the DOM boundary has direct tests proving the retained-node contract: row and text nodes are allocated once, updates patch those same nodes in place, colors and labels still reflect overlay tone and liquidation preview state, and hiding clears only visibility and text state without destroying the retained rows.

Third, the runtime facade still passes its behavior tests: identical overlay references skip redundant rerenders, subscription-triggered repaint events still coalesce per frame, and liquidation drag preview or confirm callbacks still emit the same payload shape and anchor information through the public `sync-position-overlays!` path.

Fourth, UI validation is explicitly accounted for. The smallest relevant Playwright trade smoke must pass, and the governed `trade-route` browser QA must report all required passes across widths `375`, `768`, `1280`, and `1440` as `PASS`, `FAIL`, or `BLOCKED` with no missing pass accounting.

Fifth, the repo gates must pass unchanged: `npm run check`, `npm test`, and `npm run test:websocket`.

Sixth, the namespace-size registry must be honest. If `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` and `/hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs` both fall below `500` lines, remove their exception entries. If not, update the exact line limits and explain why the remaining concentration is still intentionally deferred.

## Idempotence and Recovery

This refactor is code-local and safe to repeat. The safest implementation order is additive: create the new helper namespaces first, rewire the runtime facade second, and trim the root test suite only after the new boundary tests exist. If a partial extraction breaks the overlay behavior, keep the public `sync-position-overlays!` and `clear-position-overlays!` signatures stable, revert only the most recent helper wiring inside the active worktree, and rerun the focused overlay suites before attempting the broad repo gates again.

If browser QA creates inspection sessions, clean them up explicitly with `npm run browser:cleanup` before concluding the task. Playwright should exit on its own; Browser MCP or browser-inspection sessions must not be left running.

## Artifacts and Notes

Baseline line counts before implementation:

    src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs -> 1028 lines
    test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs -> 679 lines

Tracked issue and current target:

    bd issue: hyperopen-wmr7
    requested hotspot owner: src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs

Post-implementation evidence:

    src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs -> 408 lines
    src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_layout.cljs -> 416 lines
    src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_dom.cljs -> 222 lines
    test/hyperopen/views/trading_chart/utils/chart_interop/position_overlays_test.cljs -> 275 lines
    test/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_layout_test.cljs -> 127 lines
    test/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_dom_test.cljs -> 214 lines

    npm run test:runner:generate
    Generated test/test_runner_generated.cljs with 449 namespaces.

    npx shadow-cljs --force-spawn compile test && node out/test.js
    Ran 3061 tests containing 16306 assertions.
    0 failures, 0 errors.

    npx playwright test tools/playwright/test/routes.smoke.spec.mjs --grep "trade"
    9 passed (47.9s)

    npm run qa:design-ui -- --targets trade-route --manage-local-app
    reviewOutcome: PASS
    inspected widths: 375, 768, 1280, 1440
    artifact: /Users/barry/.codex/worktrees/52fe/hyperopen/tmp/browser-inspection/design-review-2026-04-06T22-41-18-437Z-fa7adc10

    npm run browser:cleanup
    {"ok": true, "stopped": [], "results": []}

    npm run check
    passed

    npm test
    Ran 3061 tests containing 16306 assertions.
    0 failures, 0 errors.

    npm run test:websocket
    Ran 433 tests containing 2478 assertions.
    0 failures, 0 errors.

## Interfaces and Dependencies

The public overlay interface must remain:

    hyperopen.views.trading-chart.utils.chart-interop.position-overlays/render-overlays!
    hyperopen.views.trading-chart.utils.chart-interop.position-overlays/sync-position-overlays!
    hyperopen.views.trading-chart.utils.chart-interop.position-overlays/clear-position-overlays!

Callers that must remain compatible:

    /hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop.cljs
    /hyperopen/src/hyperopen/views/trading_chart/core.cljs
    /hyperopen/test/hyperopen/views/trading_chart/core_test.cljs
    /hyperopen/test/hyperopen/views/trading_chart/utils/chart_interop_test.cljs

The new helper namespaces should remain pure or side-effect-bounded:

- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_layout.cljs` should depend on `clojure.string` and `/hyperopen/src/hyperopen/views/account_info/shared.cljs` formatting helpers only.
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_dom.cljs` should depend on the layout namespace only for prepared row data and should own DOM mutation, row creation, and root attachment behavior.
- `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlays.cljs` should keep chart object mutation, sidecar state, repaint scheduling, subscription wiring, and drag listener orchestration.

Plan update note: 2026-04-06 22:42Z - Completed the position overlay boundary split by extracting `/hyperopen/src/hyperopen/views/trading_chart/utils/chart_interop/position_overlay_{layout,dom}.cljs`, splitting the root test into runtime plus direct boundary suites, removing the stale namespace-size exceptions after the root files dropped below `500` lines, recovering the incomplete local dependency install with `npm ci`, and passing the required Playwright, browser-QA, `npm run check`, `npm test`, and `npm run test:websocket` validations.
