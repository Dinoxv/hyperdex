# Shrink root bundle follow-up surfaces and runtime aggregators

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`. It is self-contained so a future contributor can continue from this file without relying on conversation history.

Tracked issue: `hyperopen-kefh` ("Shrink root bundle follow-up surfaces and runtime aggregators").

## Purpose / Big Picture

The first root-bundle reduction pass moved closed-by-default root modals out of the Shadow CLJS startup module. That reduced production `main.*.js` from 624,506 gzip bytes to 614,390 gzip bytes, but the root app still pulls non-critical surfaces into startup. The next high-return, medium-risk target is the trade account side surfaces: account info tables, account equity summary, account equity metrics, and related funding action UI. These surfaces are needed on the trade route after the shell is visible and on the portfolio route after the portfolio route module loads, but they do not need to execute before the portfolio route first paint.

After this plan is implemented, the production build should show a smaller `out/release-public/js/main.*.js` file and a new account surface chunk. The trade route should still render account panels after the account surface module loads, and the portfolio route should still load and render its account table through its route module dependency. The measurable proof is a production build benchmark before and after this follow-up, plus the repository's required validation gates.

## Progress

- [x] (2026-04-20 14:25Z) Committed the completed first-wave lazy root modal split as `435e4fa8 perf: lazy-load root modal surfaces`.
- [x] (2026-04-20 14:26Z) Created and claimed `bd` issue `hyperopen-kefh` for this follow-up wave.
- [x] (2026-04-20 14:27Z) Ran a fresh production build after the first-wave commit to establish the follow-up baseline.
- [x] (2026-04-20 14:28Z) Recorded the follow-up baseline bundle benchmark: `main.1E87D483EA5081F043A7396EDF38295F.js` measured 2,657,341 raw bytes, 614,390 gzip bytes, and 469,485 brotli bytes. The already-split `funding_modal` chunk measured 53,792 raw / 9,032 gzip / 7,770 brotli bytes, and `spectate_mode_modal` measured 18,092 raw / 4,075 gzip / 3,612 brotli bytes.
- [x] (2026-04-20 14:39Z) Added RED tests for lazy account surface resolution in the trade view and post-render trade module loading. The focused command failed as intended: startup dispatched only the trade chart/indicator effects, `surface-modules/resolved-surface-export` did not exist yet, and the trade view still called account info/equity render functions before lazy surface resolution.
- [x] (2026-04-20 14:43Z) Implemented the `:account_surfaces` Shadow module and shared exported account surface entry namespace.
- [x] (2026-04-20 14:43Z) Updated trade route rendering to tolerate the account surface module being absent and to render placeholders until the module resolves.
- [x] (2026-04-20 14:43Z) Updated portfolio route module dependencies so portfolio account surfaces are loaded with the portfolio route, not root `:main`.
- [x] (2026-04-20 14:43Z) Ran focused tests for the RED contracts and app compilation. The focused command passed with 12 tests, 47 assertions, 0 failures, and 0 errors; `npx shadow-cljs --force-spawn compile app` completed with 838 files, 39 compiled, and 0 warnings.
- [x] (2026-04-20 14:45Z) Ran a post-change production build and recorded raw, gzip, and brotli deltas. `main.550A98AED1183B45A4C1A5F0B8762A2A.js` measured 2,387,401 raw bytes, 558,847 gzip bytes, and 432,135 brotli bytes. Compared to the follow-up baseline, `main.*.js` is 269,940 raw bytes, 55,543 gzip bytes, and 37,350 brotli bytes smaller. The new `account_surfaces.5AF66281BCEC7255042DC241B62C0465.js` chunk measured 285,781 raw bytes, 58,359 gzip bytes, and 45,023 brotli bytes.
- [x] (2026-04-20 15:06Z) Ran `npm run check`, `npm test`, `npm run test:websocket`, and browser smoke coverage for this UI-facing module split. `npm run check` passed; `npm test` passed with 3,318 tests and 18,124 assertions; `npm run test:websocket` passed with 449 tests and 2,701 assertions. The stock `npm run test:playwright:smoke` command hit a reproducible web-server startup race on the staking desktop case after 21 of 22 tests passed. The same smoke suite passed with 22 of 22 tests against a prewarmed dev server using the same Playwright browser assertions, and a direct browser check confirmed `staking-root` was present.
- [x] (2026-04-20 15:06Z) Decided to commit the account-surface split before starting the higher-risk route/runtime aggregator milestone. The account-surface split beat the expected return target with a 55,543 gzip-byte root reduction, so the route/runtime aggregator work remains the next planned high-return milestone rather than being bundled into this commit.
- [ ] Start Milestone 2 by benchmarking the committed account-surface split as the route/runtime aggregator baseline and adding RED tests for route-specific registration ordering.

## Surprises & Discoveries

- Observation: The account info and account equity namespaces are shared between the trade view and the portfolio route.
  Evidence: `src/hyperopen/views/trade_view.cljs` requires both `hyperopen.views.account-info-view` and `hyperopen.views.account-equity-view`. `src/hyperopen/views/portfolio_view.cljs` and `src/hyperopen/views/portfolio/account_tabs.cljs` require `account-info-view`, and `src/hyperopen/views/portfolio/vm.cljs` requires `account-equity-view` for `account-equity-metrics`.

- Observation: Splitting the shared account surfaces into a trade-only module would likely move shared code back into `:main`.
  Evidence: Shadow CLJS places code needed by multiple sibling modules into their common ancestor. A trade-only account chunk and the existing `:portfolio_route` chunk would both require the same account namespaces, and their common ancestor is currently `:main`.

- Observation: The focused RED run confirmed the existing startup and trade view still use eager account surfaces.
  Evidence: `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.trade-view.startup-defer-test --test=hyperopen.app.startup-test` failed with 9 failures. The app startup tests saw only `[:effects/load-trade-chart-module]` and `[:effects/load-trading-indicators-module]`, while `trade-view-renders-account-placeholders-until-account-surfaces-load-test` observed one call each to account info, account equity metrics, and account equity view.

- Observation: The account-surface RED contracts passed after introducing named surface exports and trade placeholders.
  Evidence: The focused command `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.trade-view.startup-defer-test --test=hyperopen.app.startup-test` passed with 12 tests, 47 assertions, 0 failures, and 0 errors.

- Observation: The account-surface module split produced the largest root bundle reduction so far.
  Evidence: The follow-up baseline `main.1E87D483EA5081F043A7396EDF38295F.js` was 2,657,341 raw / 614,390 gzip / 469,485 brotli bytes. After the split, `main.550A98AED1183B45A4C1A5F0B8762A2A.js` was 2,387,401 raw / 558,847 gzip / 432,135 brotli bytes, while the new `account_surfaces.5AF66281BCEC7255042DC241B62C0465.js` chunk was 285,781 raw / 58,359 gzip / 45,023 brotli bytes.

- Observation: The stock Playwright smoke command can race the Shadow watch server before `HYPEROPEN_DEBUG` is ready.
  Evidence: `npm run test:playwright:smoke` passed 21 of 22 tests, but the staking desktop case timed out waiting for the debug bridge on a blank page. A direct browser diagnostic against the same page after the dev server finished compiling showed `HYPEROPEN_DEBUG` available. Running the same Playwright smoke assertions against that prewarmed server passed 22 of 22 tests in 1.5 minutes.

## Decision Log

- Decision: Use the committed first-wave production build as this plan's baseline.
  Rationale: The user asked for benchmarking before executing each plan. The first-wave commit is the current starting point for this follow-up, and the fresh production build measured the exact `main.*.js` artifact that this follow-up is trying to shrink.
  Date/Author: 2026-04-20 / Codex

- Decision: Create a shared `:account_surfaces` module, not a trade-only account module.
  Rationale: The portfolio route also depends on account info and account equity code. A shared module that both `:portfolio_route` and trade's lazy surface loader can depend on prevents the compiler from hoisting those shared namespaces into `:main`.
  Date/Author: 2026-04-20 / Codex

- Decision: Keep route/runtime aggregator self-registration in this plan as a later high-return milestone, but do not start it until the account-surface split is measured.
  Rationale: Runtime aggregators may remove more code from startup, but they are higher risk because action/effect registration order can break route actions, wallet flows, and lazy route initialization. The account-surface split is a direct continuation of the proven lazy-surface pattern and has a narrower regression surface.
  Date/Author: 2026-04-20 / Codex

- Decision: Commit the account-surface module split before beginning route/runtime aggregator work.
  Rationale: The account-surface split produced a large measured win on its own and touched UI rendering, startup loading, module maps, and test harnesses. Keeping it in a separate commit makes any later route/action/effect registration work easier to review and roll back independently.
  Date/Author: 2026-04-20 / Codex

## Outcomes & Retrospective

Milestone 1 is implemented and validated. The shared account info/equity surfaces now live in a new `:account_surfaces` Shadow module. The trade route requests that module after render and renders existing placeholders while account exports are unresolved. The portfolio route depends on the shared module so the account namespaces no longer need to live in root `:main`. The production root bundle moved from 614,390 gzip bytes to 558,847 gzip bytes, a reduction of 55,543 gzip bytes. Complexity increased moderately because the surface module loader now supports named exports and trade tests have to stub that lazy boundary, but the complexity is concentrated in the existing lazy surface mechanism and is justified by the measured root startup reduction. The next planned milestone is route/runtime aggregator splitting, which should start from a fresh benchmark after this milestone is committed.

## Context and Orientation

The working directory is `/Users/barry/.codex/worktrees/7bbe/hyperopen`.

This app is built with Shadow CLJS modules. A module is a JavaScript output chunk. The startup module is named `:main`; route modules such as `:portfolio_route` depend on `:main` and load on demand. The relevant configuration is `shadow-cljs.edn`. The production command is `npm run build`, which runs the release app and worker builds and generates fingerprinted assets under `out/release-public`.

The root surface module pattern already exists in `src/hyperopen/surface_modules.cljs`. It maps a surface id such as `:funding-modal` to a Shadow module name such as `funding_modal`, calls `shadow.loader/load`, resolves globally exported functions from the loaded namespace, and records loading state under `[:surface-modules ...]`. The runtime effect `[:effects/load-surface-module <surface-id>]` is wired through `src/hyperopen/app/effects.cljs`, `src/hyperopen/runtime/effect_adapters.cljs`, `src/hyperopen/schema/contracts/effect_args.cljs`, and `src/hyperopen/schema/runtime_registration/trade.cljs`.

The trade view is `src/hyperopen/views/trade_view.cljs`. It currently statically requires `hyperopen.views.account-info-view` and `hyperopen.views.account-equity-view`, then renders account panels after `[:trade-ui :desktop-secondary-panels-ready?]` becomes true. That startup flag is set after the first render by `src/hyperopen/app/startup.cljs` through `:mark-post-render-trade-secondary-panels-ready!`. The same startup namespace currently loads trade chart and trading indicators modules after render; it should also load `:account-surfaces` for trade routes.

The portfolio route is `src/hyperopen/views/portfolio_view.cljs`, with supporting code in `src/hyperopen/views/portfolio/account_tabs.cljs` and `src/hyperopen/views/portfolio/vm.cljs`. These namespaces require account info and account equity code. Because the portfolio route is already lazy-loaded through `:portfolio_route`, the account code must move into a shared `:account_surfaces` module that `:portfolio_route` depends on. The trade route can then request that same shared module after render.

The follow-up baseline benchmark command is:

    npm run build
    node - <<'NODE'
    const fs = require('fs');
    const zlib = require('zlib');
    const files = fs.readdirSync('out/release-public/js')
      .filter((file) => file.endsWith('.js'))
      .sort();
    for (const file of files) {
      const path = 'out/release-public/js/' + file;
      const bytes = fs.readFileSync(path);
      const gzip = zlib.gzipSync(bytes, {level: 9});
      const brotli = zlib.brotliCompressSync(bytes, {
        params: {[zlib.constants.BROTLI_PARAM_QUALITY]: 11}
      });
      console.log(`${file}\traw=${bytes.length}\tgzip=${gzip.length}\tbrotli=${brotli.length}`);
    }
    NODE

The follow-up baseline output for the startup and relevant chunks was:

    main.1E87D483EA5081F043A7396EDF38295F.js raw=2657341 gzip=614390 brotli=469485
    funding_modal.17E442983F2D566DCC410E40470FCE28.js raw=53792 gzip=9032 brotli=7770
    portfolio_route.DA7817C09B366890A46B6103B7740D91.js raw=126434 gzip=28877 brotli=23937
    spectate_mode_modal.96E486AA8EDBAA35B50BC320AE569D63.js raw=18092 gzip=4075 brotli=3612
    trade_chart.B0CD467C101144134B8D6504344E403B.js raw=412769 gzip=116366 brotli=97531

## Plan of Work

Milestone 1 moves shared account surfaces out of root startup. Add tests first. In `test/hyperopen/views/trade_view/startup_defer_test.cljs`, add a test proving that when desktop secondary panels are marked ready but `:account-surfaces` exports are unresolved, the trade view renders account placeholders and does not call account info or account equity render functions. In `test/hyperopen/app/startup_test.cljs`, add or update tests proving that post-render trade startup dispatches `[:effects/load-surface-module :account-surfaces]` when the module is not loaded, and that initial trade route changes still defer this work until post-render startup.

Then extend `src/hyperopen/surface_modules.cljs` so a surface module can expose multiple named functions. The existing modal surfaces can keep using a primary `:view` export. The new `:account-surfaces` id should resolve exports such as `:account-info-view`, `:account-equity-view`, `:account-equity-metrics`, and `:funding-actions-view`. The loader should still mark a module loaded only after a primary export is present, so a missing or broken entry namespace fails explicitly.

Add `src/hyperopen/views/account_surfaces_module.cljs`. This entry namespace should require `hyperopen.views.account-info-view` and `hyperopen.views.account-equity-view`, define thin exported wrapper functions for the four account surface functions, and call `goog/exportSymbol` for stable global names. Do not require this entry namespace from root app code.

Update `shadow-cljs.edn` in both the `:app` and `:release` module maps. Add `:account_surfaces {:entries [hyperopen.views.account-surfaces-module] :depends-on #{:main}}`. Change `:portfolio_route` so it depends on `#{:account_surfaces}` instead of `#{:main}`. Keep the other route modules unchanged.

Update `src/hyperopen/views/trade_view.cljs` to remove static requires of `account-info-view` and `account-equity-view`. The trade view should resolve account exports through `hyperopen.surface-modules` and render the existing skeleton placeholders while exports are missing. This means the first trade shell can render without pulling account surfaces into `:main`; after `:account-surfaces` loads, the same render paths should call the exported account render functions.

Update `src/hyperopen/app/startup.cljs` to include `:effects/load-surface-module :account-surfaces` for trade route module loads. Initial trade route startup should defer account surfaces together with the trade chart and indicators until `:load-post-render-route-effects!` runs. Later route changes into `/trade` can request the account surface module immediately through normal route-change effects.

Milestone 2 is the higher-risk route/runtime aggregator follow-up. Do not start it until Milestone 1 is measured. If Milestone 1 produces a useful reduction, preserve Milestone 2 as a separate planned continuation. If Milestone 1 underperforms, inspect `src/hyperopen/runtime/collaborators.cljs`, `src/hyperopen/app/effects.cljs`, and `src/hyperopen/runtime/action_adapters.cljs` to identify route-specific imports still anchored in `:main`. The preferred path is to split broad aggregator maps into smaller namespaces first, add tests for registry contents and route-load ordering, then pilot route-module self-registration for one non-critical route before touching wallet or funding flows.

## Concrete Steps

1. Confirm the working tree is clean after the first-wave commit:

    git status --short --branch

2. Write RED tests in `test/hyperopen/views/trade_view/startup_defer_test.cljs` and `test/hyperopen/app/startup_test.cljs`. Run the focused command and expect failures that mention missing account surface loading or unexpected account render calls:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js --test=hyperopen.views.trade-view.startup-defer-test --test=hyperopen.app.startup-test

3. Implement the account surface module support in `src/hyperopen/surface_modules.cljs`, the new entry namespace `src/hyperopen/views/account_surfaces_module.cljs`, `shadow-cljs.edn`, `src/hyperopen/views/trade_view.cljs`, and `src/hyperopen/app/startup.cljs`.

4. Rerun the focused command from step 2 and expect the new tests to pass.

5. Compile the app to catch module-map and Closure export mistakes:

    npx shadow-cljs --force-spawn compile app

6. Run the post-change production benchmark command from the Context section. Record exact raw, gzip, and brotli sizes for `main.*.js`, `account_surfaces.*.js`, `portfolio_route.*.js`, and any changed related chunks.

7. Run the required repository gates:

    npm run check
    npm test
    npm run test:websocket

8. Because this touches UI rendering and lazy browser modules, run the smallest deterministic browser smoke command that covers the shell and route startup:

    npm run test:playwright:smoke

9. Update this ExecPlan with benchmark results, validation outcomes, and the decision on whether to begin Milestone 2 now.

## Validation and Acceptance

The work is accepted when all of these are true:

1. This plan records the before and after production bundle sizes for `main.*.js` using raw, gzip, and brotli bytes.

2. `shadow-cljs.edn` emits a separate `account_surfaces.*.js` module in production builds.

3. `src/hyperopen/views/trade_view.cljs` no longer statically requires `hyperopen.views.account-info-view` or `hyperopen.views.account-equity-view`.

4. The trade view renders placeholders instead of crashing or calling absent account surface functions while `:account-surfaces` is unresolved.

5. The portfolio route still depends on and loads the shared account surfaces through the `:portfolio_route` module dependency.

6. Focused RED tests fail before the implementation and pass after it.

7. A post-change production build shows `main.*.js` smaller than the follow-up baseline, or the plan documents why the split did not reduce `:main` and what blocker remains.

8. `npm run check`, `npm test`, `npm run test:websocket`, and `npm run test:playwright:smoke` pass, or any failure is documented with a clear blocker that is unrelated to this change.

## Idempotence and Recovery

The benchmark command reads generated assets and prints sizes. `npm run build` overwrites generated JavaScript and CSS assets under `resources/public` and `out/release-public`; those generated files are not the source of truth for this source change. The test commands can be rerun safely.

If the account surface module breaks portfolio route loading, the safest recovery is to restore `:portfolio_route` to depend on `#{:main}` and temporarily keep account info and account equity in the portfolio route while preserving trade-side placeholder handling. If trade rendering breaks, restore the old static account requires in `trade_view.cljs`, remove `:account-surfaces` from the surface module map, and record the failed attempt here before choosing the next candidate. Do not run destructive git commands or revert unrelated user work.

## Artifacts and Notes

The first-wave commit before this plan is:

    435e4fa8 perf: lazy-load root modal surfaces

The follow-up baseline production build completed with:

    [:app] Build completed. (831 files, 0 compiled, 0 warnings, 34.86s)
    [:portfolio-worker] Build completed.
    [:vault-detail-worker] Build completed.

The full follow-up baseline benchmark currently has no `account_surfaces.*.js` chunk. Its key lines are:

    main.1E87D483EA5081F043A7396EDF38295F.js raw=2657341 gzip=614390 brotli=469485
    portfolio_route.DA7817C09B366890A46B6103B7740D91.js raw=126434 gzip=28877 brotli=23937
    trade_chart.B0CD467C101144134B8D6504344E403B.js raw=412769 gzip=116366 brotli=97531

The post-change account-surface benchmark key lines are:

    account_surfaces.5AF66281BCEC7255042DC241B62C0465.js raw=285781 gzip=58359 brotli=45023
    main.550A98AED1183B45A4C1A5F0B8762A2A.js raw=2387401 gzip=558847 brotli=432135
    portfolio_route.566B7972AF65D584BC990246B41A6F2F.js raw=126475 gzip=28948 brotli=24030

The root `main.*.js` delta for Milestone 1 is:

    raw:    -269940 bytes
    gzip:    -55543 bytes
    brotli:  -37350 bytes

## Interfaces and Dependencies

`src/hyperopen/surface_modules.cljs` should expose `resolved-surface-export` with the shape `(resolved-surface-export surface-id export-id)`. It should preserve `render-surface-view`, `resolved-surface-view`, `surface-ready?`, `surface-loading?`, `surface-error`, and `load-surface-module!` for existing modal surfaces.

`src/hyperopen/views/account_surfaces_module.cljs` should export these global functions:

    hyperopen.views.account_surfaces_module.account_info_view
    hyperopen.views.account_surfaces_module.account_equity_view
    hyperopen.views.account_surfaces_module.account_equity_metrics
    hyperopen.views.account_surfaces_module.funding_actions_view

`src/hyperopen/app/startup.cljs` should use the existing effect vector `[:effects/load-surface-module :account-surfaces]`. No new effect id is needed.

## Revision Notes

- 2026-04-20 / Codex: Created the active follow-up ExecPlan after committing the first-wave modal split, recording a fresh production build benchmark, and selecting shared account surfaces as the next direct root-bundle shrink candidate. The plan also includes the route/runtime aggregator follow-up as a later high-return milestone.
- 2026-04-20 / Codex: Recorded Milestone 1 implementation, validation results, and the decision to commit the account-surface split before starting route/runtime aggregator work.
