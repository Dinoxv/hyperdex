# Add Staking Account-Request Simulator Coverage For Browser QA

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document follows `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, and `/hyperopen/docs/WORK_TRACKING.md`. The tracked work item for this plan is `hyperopen-4y37`, and that `bd` issue must remain the lifecycle source of truth while this plan tracks implementation.

## Purpose / Big Picture

After this change, deterministic browser QA for `/staking` will be able to exercise the loaded staking route instead of stopping at disconnected and menu-only states. A contributor will be able to install a dev-only account-request simulator through `HYPEROPEN_DEBUG`, connect a simulated wallet on `/staking`, and see validator summaries, delegator summary, delegations, rewards, history, and staking spot-balance data render from stable fixtures rather than from live account `/info` traffic.

The user-visible proof is a committed Playwright regression and a checked-in browser-inspection scenario that both load `/staking` with simulated wallet plus simulated account data and assert the populated route. This bead does not redesign `/staking`; it closes the remaining deterministic data seam that the March 31, 2026 staking baseline work explicitly left open.

## Progress

- [x] (2026-03-31 00:27Z) Claimed `hyperopen-4y37` in `bd`.
- [x] (2026-03-31 00:28Z) Re-read `/hyperopen/AGENTS.md`, `/hyperopen/docs/MULTI_AGENT.md`, `/hyperopen/.agents/PLANS.md`, `/hyperopen/docs/PLANS.md`, `/hyperopen/docs/WORK_TRACKING.md`, `/hyperopen/docs/BROWSER_TESTING.md`, and `/hyperopen/docs/agent-guides/browser-qa.md`.
- [x] (2026-03-31 00:31Z) Confirmed the current gap: `/hyperopen/src/hyperopen/telemetry/console_preload/simulators.cljs` exposes wallet and exchange simulators, but staking fetches still go through `/hyperopen/src/hyperopen/api/default.cljs` -> `post-info!` -> active account `/info` request paths.
- [x] (2026-03-31 00:35Z) Mapped the exact staking request types and normalizers in `/hyperopen/src/hyperopen/api/endpoints/account.cljs`: `validatorSummaries`, `delegatorSummary`, `delegations`, `delegatorRewards`, `delegatorHistory`, and `spotClearinghouseState`.
- [x] (2026-03-31 00:39Z) Confirmed the browser-facing refresh path already exists: connecting a wallet while `/staking` is active dispatches `[:actions/load-staking-route "/staking"]` from `/hyperopen/src/hyperopen/runtime/action_adapters/wallet.cljs`.
- [x] (2026-03-31 00:44Z) Froze the spec and test-contract artifacts under `/hyperopen/tmp/multi-agent/hyperopen-4y37/`, including `spec.json`, proposal artifacts, and `approved-test-contract.json`.
- [x] (2026-03-31 00:42Z) Materialized RED-phase tests in `/hyperopen/test/hyperopen/api/default_test.cljs`, `/hyperopen/test/hyperopen/telemetry/console_preload/simulators_test.cljs`, `/hyperopen/test/hyperopen/telemetry/console_preload_test.cljs`, `/hyperopen/test/hyperopen/telemetry/console_preload_debug_api_test.cljs`, and `/hyperopen/tools/playwright/test/staking-regressions.spec.mjs`.
- [x] (2026-03-31 00:43Z) Ran the smallest authoritative RED command after installing missing local JS dependencies: `npm run test:runner:generate && npx shadow-cljs compile test && node out/test.js` failed for the intended missing behavior, including absent debug methods, unknown `staking` oracle, and simulator-backed staking requests falling through to current live/default data.
- [x] (2026-03-31 00:44Z) Implemented the default-facade account-request simulator seam in `/hyperopen/src/hyperopen/api/default.cljs`, exposed the install and clear hooks through `/hyperopen/src/hyperopen/telemetry/console_preload/simulators.cljs`, added the `staking` oracle plus debug bridge methods in `/hyperopen/src/hyperopen/telemetry/console_preload.cljs`, and wired `qaReset` to clear the new seam.
- [x] (2026-03-31 00:58Z) Added deterministic populated `/staking` browser coverage in `/hyperopen/tools/playwright/test/staking-regressions.spec.mjs`, checked in `/hyperopen/tools/browser-inspection/scenarios/staking-loaded-account-simulated.json`, and validated the focused browser path with `npx playwright test tools/playwright/test/staking-regressions.spec.mjs -g "loaded staking account data"`, `node tools/browser-inspection/src/cli.mjs scenario list --ids staking-loaded-account-simulated`, and `node tools/browser-inspection/src/cli.mjs scenario run --ids staking-loaded-account-simulated --dry-run`.
- [x] (2026-03-31 01:05Z) Ran `npm run check`, `npm test`, and `npm run test:websocket` successfully after updating the namespace-size exception registry for the expanded simulator/debug namespaces and moving an unrelated stale closed-issue ExecPlan out of `active/`.

## Surprises & Discoveries

- Observation: the missing seam is narrower than “all account requests,” but broader than “staking only.”
  Evidence: the exact missing loaded-state set is the five staking request bodies plus `spotClearinghouseState`, and all six already share the same default-facade `post-info!` boundary in `/hyperopen/src/hyperopen/api/default.cljs`.

- Observation: a wallet connect on `/staking` already retriggers the route load.
  Evidence: `/hyperopen/src/hyperopen/runtime/action_adapters/wallet.cljs` dispatches `[:actions/load-staking-route route]` when `staking-route?` is true, so browser tests do not need a new action just to refresh the route after simulated connect.

- Observation: the existing debug idle digest does not include staking load state.
  Evidence: `/hyperopen/src/hyperopen/telemetry/console_preload.cljs` currently waits on route, wallet, funding, mobile-surface, account-tab, trace count, and telemetry count; it does not watch staking loading flags or row counts. Browser assertions for the new populated-state path should therefore poll a staking-specific oracle or stable DOM content instead of assuming generic idle means staking data is present.

- Observation: the RED tests are failing for the exact seam this bead is supposed to add, not for unrelated staking logic.
  Evidence: the first authoritative run after installing local JS deps reported `installAccountRequestSimulator` and `clearAccountRequestSimulator` as missing from `HYPEROPEN_DEBUG`, `oracle! "staking"` throwing `Unknown QA oracle: staking`, and the new staking simulator tests receiving current default/live values such as `ValiDAO`, zeroed delegator summary, empty rewards/history, and empty spot balances instead of the requested fixtures.

- Observation: async `with-redefs` around `api-service/request-info!` is not reliable for asserting live fallthrough from these new promise-based request paths.
  Evidence: the initial post-implementation test pass still failed in `/hyperopen/test/hyperopen/api/default_test.cljs` even though direct node-level calls proved the simulator worked. Rewriting the matcher test to exercise the private interception helpers directly avoided false negatives from namespace-level async test scheduling while preserving the intended guard.

- Observation: the repo-wide `npm run check` gate also enforces active-plan hygiene and namespace-size exceptions, so this bead needed minor repository bookkeeping beyond the functional staking changes.
  Evidence: the first green implementation pass hit `lint:docs` because `/hyperopen/docs/exec-plans/active/2026-03-30-vault-detail-tvl-cold-load-fix.md` still referenced closed issue `hyperopen-6w7x`, and then hit `lint:namespace-sizes` because the new simulator work increased `/hyperopen/src/hyperopen/api/default.cljs`, `/hyperopen/src/hyperopen/telemetry/console_preload.cljs`, and `/hyperopen/test/hyperopen/telemetry/console_preload/simulators_test.cljs` beyond their current thresholds.

## Decision Log

- Decision: intercept the default facade’s account `/info` request boundary in `/hyperopen/src/hyperopen/api/default.cljs` instead of adding a new simulator layer inside the staking effects or view-model code.
  Rationale: all required staking loaded-state requests already pass through that single facade boundary, and the issue description explicitly names `request-info!` / active service account request paths as the gap. This keeps the production seam small and avoids leaking simulator-specific conditionals into staking feature code.
  Date/Author: 2026-03-31 / Codex

- Decision: scope the simulator to the exact six request kinds needed for loaded `/staking`: `validatorSummaries`, `delegatorSummary`, `delegations`, `delegatorRewards`, `delegatorHistory`, and `spotClearinghouseState`.
  Rationale: this closes the bead while avoiding an under-specified “simulate every account endpoint” abstraction. The config can still be generic enough to reuse later, but acceptance for this bead should stay pinned to the actual staking route contract.
  Date/Author: 2026-03-31 / Codex

- Decision: expose simulator install, clear, and snapshot through `HYPEROPEN_DEBUG` beside the existing wallet and exchange simulator helpers.
  Rationale: browser QA already uses that bridge as the repo-owned deterministic control surface. Reusing it avoids inventing a second browser-only API and matches the existing Playwright and browser-inspection patterns.
  Date/Author: 2026-03-31 / Codex

- Decision: add a staking-specific oracle for deterministic loaded-state assertions.
  Rationale: generic `waitForIdle` does not observe staking loads, and DOM-only assertions would be more brittle than a small debug oracle that reports row counts, loading flags, and active tab state from the canonical store.
  Date/Author: 2026-03-31 / Codex

## Outcomes & Retrospective

Implementation completed and the acceptance criteria were satisfied. The landed change keeps the simulator concentrated at the existing default-facade boundary, which avoided store-patching shortcuts and preserved the normal account endpoint normalization path for validator summaries, delegator summary, delegations, rewards, history, and spot balances.

The committed browser proof is now in place for both deterministic systems this bead targeted: Playwright and browser-inspection. The remaining architectural risk is the same pre-existing one already called out by namespace-size policy: `/hyperopen/src/hyperopen/api/default.cljs` and `/hyperopen/src/hyperopen/telemetry/console_preload.cljs` still want a later split, but the new staking simulator behavior is internally cohesive and validated.

## Context and Orientation

There are two separate browser-QA systems in this repository. Committed deterministic coverage lives under `/hyperopen/tools/playwright/test/**` and talks to the app through the dev-only `HYPEROPEN_DEBUG` bridge from `/hyperopen/src/hyperopen/telemetry/console_preload.cljs`. Browser-inspection scenarios live under `/hyperopen/tools/browser-inspection/scenarios/**` and use the same debug bridge for deterministic setup. This bead is about making those systems capable of loading `/staking` with deterministic account-backed data.

The staking route reads from six request paths. `/hyperopen/src/hyperopen/staking/actions.cljs` emits effects for validator summaries, delegator summary, delegations, rewards, history, and staking spot balances. `/hyperopen/src/hyperopen/staking/effects.cljs` resolves those through injected request functions. `/hyperopen/src/hyperopen/runtime/effect_adapters/staking.cljs` binds those request functions to `/hyperopen/src/hyperopen/api/default.cljs`. That default facade uses a private `post-info!` helper which delegates to `api-service/request-info!` on the active installed service.

The request bodies and response normalization already exist in `/hyperopen/src/hyperopen/api/endpoints/account.cljs`. The relevant body `type` values are `validatorSummaries`, `delegatorSummary`, `delegations`, `delegatorRewards`, `delegatorHistory`, and `spotClearinghouseState`. The loaded staking route depends on those normalized responses, not on raw payload shape, so the simulator must feed data through the same request functions rather than patching the store directly.

The current debug simulator surface lives in `/hyperopen/src/hyperopen/telemetry/console_preload/simulators.cljs`. It already exposes wallet and exchange simulators plus `qaReset`. `/hyperopen/src/hyperopen/telemetry/console_preload.cljs` publishes those helpers on `globalThis.HYPEROPEN_DEBUG`. That is the right extension point for a new account-request simulator because Playwright and browser-inspection already call those methods directly.

Finally, wallet connect already refreshes `/staking`. `/hyperopen/src/hyperopen/runtime/action_adapters/wallet.cljs` dispatches `[:actions/load-staking-route route]` when a wallet connects while the current route is staking. That means a deterministic browser flow can be: visit `/staking`, install wallet simulator, install account-request simulator, connect wallet, then assert the populated route.

## Plan of Work

First, add a small simulator state and public installation seam at the default API facade. In `/hyperopen/src/hyperopen/api/default.cljs`, introduce private simulator state plus public helpers to install, clear, and snapshot the account-request simulator. Keep the simulator keyed by request body `type` and requested user where needed. Extend the facade’s private `post-info!` helper so that before it delegates to `api-service/request-info!`, it checks whether the request body matches one of the simulated account-request kinds. When a simulator match exists, return a resolved or rejected promise that behaves like the real request path from the caller’s perspective. Preserve the existing normalization path by returning the simulated raw payload to the existing account endpoint functions, not by writing normalized staking state directly.

Second, expose that seam through the dev debug bridge. In `/hyperopen/src/hyperopen/telemetry/console_preload/simulators.cljs`, add wrappers that normalize JS config objects, call the new default-facade install and clear functions, and return a serializable snapshot. Update `qa-reset!` to clear the new simulator alongside the wallet and exchange simulators. In `/hyperopen/src/hyperopen/telemetry/console_preload.cljs`, publish `installAccountRequestSimulator`, `clearAccountRequestSimulator`, and optionally `accountRequestSimulatorSnapshot` on `HYPEROPEN_DEBUG`.

Third, add one staking-specific oracle so browser tests can assert loaded state without brittle timing. In `/hyperopen/src/hyperopen/telemetry/console_preload.cljs`, define a small oracle that reads the canonical store and reports whether the route is populated: connected address, validator row count, selected tab, delegations count, rewards count, history count, balance availability, and loading flags. Also include this data in `qaSnapshot` only if doing so stays compact.

Fourth, add deterministic tests before implementation is declared complete. The narrow RED-phase unit coverage should verify that the default facade routes matching request bodies through the simulator, falls back to the real active service for non-simulated requests, and clears correctly. The debug-simulator tests should verify JS config normalization, snapshot shape, and `qaReset` clearing semantics. The browser coverage should then prove the end-to-end value: install wallet plus account simulators, connect on `/staking`, and assert the populated route with a stable oracle or stable DOM anchors. A browser-inspection scenario should mirror that path for artifact-backed QA.

Finally, validate in layers. Run the smallest unit namespaces first, then the smallest Playwright staking command, then the browser-inspection scenario dry run, and finally the required repo gates. Because this change touches browser tooling and deterministic test flows, the focused Playwright run must happen before the broader repository gates.

## Concrete Steps

Run these commands from `/Users/barry/.codex/worktrees/a942/hyperopen`.

1. Create or refresh the active plan and test-contract artifacts:

   - `bd show hyperopen-4y37 --json`
   - `mkdir -p tmp/multi-agent/hyperopen-4y37`

   Expected result: the issue is `in_progress` and the artifact directory exists.

2. Materialize unit tests for the simulator seam and debug bridge:

   - edit `test/hyperopen/api/default_test.cljs`
   - edit `test/hyperopen/telemetry/console_preload/simulators_test.cljs`
   - edit any additional focused test namespace only if needed for oracle coverage

   Expected result: tests initially fail because the default facade and debug bridge do not yet expose the new simulator.

3. Implement the simulator and bridge:

   - edit `src/hyperopen/api/default.cljs`
   - edit `src/hyperopen/telemetry/console_preload/simulators.cljs`
   - edit `src/hyperopen/telemetry/console_preload.cljs`

   Expected result: `HYPEROPEN_DEBUG.installAccountRequestSimulator(...)` can install fixtures for the six staking request kinds, and `HYPEROPEN_DEBUG.qaReset()` removes them.

4. Add deterministic browser coverage:

   - edit `tools/playwright/test/staking-regressions.spec.mjs`
   - create or edit one scenario under `tools/browser-inspection/scenarios/`

   Expected result: there is a committed regression and a checked-in scenario that both prove the loaded `/staking` route with simulator-backed data.

5. Run focused validation in this order:

   - `npm test -- --focus test/hyperopen/api/default_test.cljs`
   - `npm test -- --focus test/hyperopen/telemetry/console_preload/simulators_test.cljs`
   - `npx playwright test tools/playwright/test/staking-regressions.spec.mjs --grep staking`
   - `node tools/browser-inspection/src/cli.mjs scenario list --ids <new-staking-simulated-scenario-id>`
   - `node tools/browser-inspection/src/cli.mjs scenario run --ids <new-staking-simulated-scenario-id> --dry-run`

   Expected result: the unit tests pass, the focused Playwright staking suite passes, and the scenario is recognized and parses successfully.

6. Finish with required repository gates:

   - `npm run check`
   - `npm test`
   - `npm run test:websocket`

   Expected result: all required gates pass, or any pre-existing unrelated failure is recorded explicitly in this plan and the final handoff.

## Validation and Acceptance

This bead is accepted when all of the following are true.

First, the dev debug bridge can install and clear an account-request simulator without touching live staking code paths directly. A local dev session must expose `HYPEROPEN_DEBUG.installAccountRequestSimulator(...)`, `HYPEROPEN_DEBUG.clearAccountRequestSimulator()`, and `HYPEROPEN_DEBUG.qaReset()` must clear the simulator.

Second, the simulator covers the loaded `/staking` route data contract. The six requests that must be simulatable are `validatorSummaries`, `delegatorSummary`, `delegations`, `delegatorRewards`, `delegatorHistory`, and `spotClearinghouseState`. A deterministic browser flow must be able to populate validator rows, balance data, reward rows, and action-history rows without live account traffic.

Third, committed browser coverage proves the new seam. Running `npx playwright test tools/playwright/test/staking-regressions.spec.mjs --grep staking` must pass with at least one regression that uses both simulated wallet connection and simulated account requests to populate `/staking`.

Fourth, browser-inspection workflows can reuse the same seam. A checked-in scenario id under `/hyperopen/tools/browser-inspection/scenarios/` must install the simulator and parse successfully in a dry run.

Fifth, required repository gates are accounted for: `npm run check`, `npm test`, and `npm run test:websocket`.

## Idempotence and Recovery

The simulator installation and clearing APIs must be safe to repeat. Reinstalling the simulator should replace prior fixtures deterministically. `qaReset` should return the app to the same no-simulator baseline it uses today. If a simulated request path leaks into unrelated non-staking requests, the safe rollback is to narrow the request-type matcher back to the six staking-related account request kinds and keep the debug bridge methods wired while unit tests enforce that non-target requests still hit the active service.

Browser-inspection scenario runs remain idempotent because they only create timestamped artifacts under `/hyperopen/tmp/browser-inspection/`. If the populated staking Playwright path proves flaky, keep the unit seam and debug bridge changes, narrow the browser assertion to the stable oracle contract, and record the discovery in this plan rather than weakening determinism with sleeps or live network assumptions.

## Artifacts and Notes

Current-state evidence gathered before implementation:

- `/hyperopen/src/hyperopen/telemetry/console_preload/simulators.cljs` exposes wallet and exchange simulators only.
- `/hyperopen/src/hyperopen/api/default.cljs` routes staking account requests through its private `post-info!` helper into the active service.
- `/hyperopen/src/hyperopen/api/endpoints/account.cljs` normalizes the six request types needed for loaded `/staking`.
- `/hyperopen/src/hyperopen/runtime/action_adapters/wallet.cljs` already refreshes `/staking` after wallet connect.

Expected touched files for the initial implementation cut:

- `/hyperopen/src/hyperopen/api/default.cljs`
- `/hyperopen/src/hyperopen/telemetry/console_preload/simulators.cljs`
- `/hyperopen/src/hyperopen/telemetry/console_preload.cljs`
- `/hyperopen/test/hyperopen/api/default_test.cljs`
- `/hyperopen/test/hyperopen/telemetry/console_preload/simulators_test.cljs`
- `/hyperopen/tools/playwright/test/staking-regressions.spec.mjs`
- `/hyperopen/tools/browser-inspection/scenarios/<staking-loaded-simulated>.json`
- `/hyperopen/docs/exec-plans/completed/2026-03-30-staking-account-request-simulator-coverage.md`
- `/hyperopen/tmp/multi-agent/hyperopen-4y37/spec.json`

## Interfaces and Dependencies

The implementation should end with these concrete interfaces:

- In `/hyperopen/src/hyperopen/api/default.cljs`, public helpers to install, clear, and snapshot the account-request simulator for the active default facade.
- In the same file, a private request interception path inside `post-info!` that can match the six staking-related account request body types and return simulated promise results while preserving the existing endpoint normalizers.
- In `/hyperopen/src/hyperopen/telemetry/console_preload/simulators.cljs`, bridge wrappers for the new simulator helpers and `qa-reset!` coverage.
- In `/hyperopen/src/hyperopen/telemetry/console_preload.cljs`, `HYPEROPEN_DEBUG.installAccountRequestSimulator`, `HYPEROPEN_DEBUG.clearAccountRequestSimulator`, and a staking-specific oracle suitable for deterministic browser assertions.
- In `/hyperopen/tools/playwright/test/staking-regressions.spec.mjs`, at least one regression that proves loaded `/staking` behavior using the new simulator plus the existing wallet simulator.

No public product API should change. The only new externally visible behavior is the dev-only debug bridge capability used by browser QA.

Plan revision note: 2026-03-31 00:40Z - Initial active ExecPlan created after claiming `hyperopen-4y37`, mapping the exact staking request kinds, and freezing the default-facade interception strategy for the account-request simulator seam.
Plan revision note: 2026-03-31 00:44Z - Wrote the repo-local multi-agent artifacts for `hyperopen-4y37` and froze the approved test contract before starting the RED phase.
Plan revision note: 2026-03-31 00:43Z - Completed the RED phase with one authoritative failing CLJS command; the failures now point directly at the missing debug bridge methods, missing staking oracle, and missing account-request interception seam.
Plan revision note: 2026-03-31 01:05Z - Completed implementation, focused browser validation, browser-inspection scenario validation, and the required repository gates; final validation also required namespace-size exception updates and moving an unrelated closed-issue plan out of `active/` so `npm run check` could pass.
