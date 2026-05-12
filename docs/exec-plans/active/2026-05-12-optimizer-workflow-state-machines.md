# Extract optimizer workflow state machines

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept current while the work proceeds. This document follows `.agents/PLANS.md` from the repository root.

## Purpose / Big Picture

The `/portfolio/optimize` runtime currently hides important workflow decisions inside nested Promise chains, recursive polling, and a global worker-run atom. After this refactor, the same optimizer behavior should be easier to reason about because workflow decisions will live in pure functions that return command maps, while runtime adapters only interpret those commands.

This is internal boundary work. A human can see it working by running ClojureScript tests that fail before the pure workflow reducers exist and pass after the adapters are rewired without changing public effect signatures.

## Context References

Public refs:

- Direct user/maintainer request on 2026-05-12: assuming “portfolio optimization” means the `/portfolio/optimize` optimizer feature, extract async optimizer workflows into explicit state machines.

Repo artifacts:

- Root operating contract: `AGENTS.md`.
- Planning contract: `.agents/PLANS.md` and `docs/PLANS.md`.
- Multi-agent workflow contract: `docs/MULTI_AGENT.md`.
- Current runtime adapter hotspots: `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_pipeline.cljs`, `src/hyperopen/runtime/effect_adapters/portfolio_optimizer/history.cljs`, `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs`, and `src/hyperopen/portfolio/optimizer/application/run_bridge.cljs`.
- Existing test surfaces: `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_pipeline_test.cljs`, `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_history_test.cljs`, `test/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios_test.cljs`, and `test/hyperopen/portfolio/optimizer/application/run_bridge_test.cljs`.

Local scratch refs (non-authoritative):

- None.

## Progress

- [x] (2026-05-12 16:05Z) Loaded the repo workflow instructions and relevant Superpowers skills for feature-flow, planning, TDD, subagents, worktree detection, and verification.
- [x] (2026-05-12 16:08Z) Confirmed this session is already in an isolated linked worktree at `/Users/barry/.codex/worktrees/3471/hyperopen` on detached HEAD.
- [x] (2026-05-12 16:15Z) Inspected the optimizer pipeline, history prefetch, scenario persistence, worker run bridge, contracts, and existing tests.
- [x] (2026-05-12 16:20Z) Spawned explorer and test-proposal agents; one explorer returned the current async behavior map and reducer boundary candidates, and another returned existing test coverage and suggested missing cases.
- [x] (2026-05-12 16:26Z) Ran the initial ClojureScript baseline command; Shadow compiled the test build, but Node failed before tests because dependencies were not installed in this worktree.
- [x] (2026-05-12 16:27Z) Ran `npm ci` to install locked dependencies after the baseline failed at `Cannot find module 'lucide/dist/esm/icons/external-link.js'`.
- [x] (2026-05-12 16:28Z) Reran the ClojureScript baseline after dependency install; `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js` passed with 3842 tests and 21246 assertions.
- [x] (2026-05-12 16:29Z) Received acceptance and edge-case test proposals and froze a focused contract around pure workflow command planning, stale worker messages, history prefetch failure/termination, and scenario persistence command ordering.
- [x] (2026-05-12 16:34Z) Added RED tests for pure workflow reducers and command plans under `test/hyperopen/portfolio/optimizer/application/*_workflow_test.cljs`.
- [x] (2026-05-12 16:35Z) Verified RED with `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test`; Shadow failed because `hyperopen.portfolio.optimizer.application.history-workflow` was not available.
- [x] (2026-05-12 16:54Z) Implemented pure workflow reducers for pipeline, history, scenario save, and worker run bridge; rewired the corresponding runtime adapters as interpreters.
- [x] (2026-05-12 16:56Z) Ran `npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js`; it passed with 3853 tests and 21285 assertions.
- [ ] Run review and required validation gates.

## Surprises & Discoveries

- Observation: The optimizer namespace-size debt has already been retired, so this plan should avoid broad file splitting and instead target the remaining async workflow boundaries.
  Evidence: Recent commit `0338bc1b` is `refactor: retire optimizer namespace size exceptions`, and the user explicitly identified boundary/workflow clarity as the remaining debt.
- Observation: The first baseline compile succeeded but the Node test runner could not start because this worktree lacked installed dependencies.
  Evidence: `npx shadow-cljs --force-spawn compile test` completed with `0 warnings`, then `node out/test.js` failed with `Cannot find module 'lucide/dist/esm/icons/external-link.js'`.
- Observation: Existing scenario state transitions are already pure in `src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenario_state.cljs`, but the persistence workflow ordering remains embedded in nested Promise chains.
  Evidence: `portfolio_optimizer_scenarios.cljs` re-exports state helpers from `portfolio_optimizer_scenario_state.cljs`, while load, save, archive, duplicate, and manual-tracking functions sequence persistence calls inline.
- Observation: The worker bridge has good behavioral tests, but the pure decision boundary is private and the global `last-run-request` dedupe can block same-signature retry after a failed run unless an explicit run id bypasses it.
  Evidence: Existing `request-run-dedupes-identical-in-flight-signature-test` covers in-flight dedupe, while the test explorer proposed `request-run-retries-identical-signature-after-failed-run-test` as missing coverage.

## Decision Log

- Decision: Keep public effect function names and Promise-returning behavior stable while extracting pure reducer functions behind them.
  Rationale: Existing runtime registration, facade tests, and action effects call these adapters directly. Changing those APIs would increase blast radius without improving workflow clarity.
  Date/Author: 2026-05-12 / Codex.
- Decision: Use small workflow namespaces under `src/hyperopen/portfolio/optimizer/application/` instead of introducing a generic workflow framework.
  Rationale: The debt is localized to optimizer adapters. A command vocabulary per workflow keeps behavior explicit and testable without adding a new cross-app abstraction.
  Date/Author: 2026-05-12 / Codex.
- Decision: Treat this refactor as non-UI-facing for browser QA.
  Rationale: No view, CSS, browser storage, or interaction flow behavior is intended to change. Deterministic ClojureScript tests and required repo gates are the appropriate validation path.
  Date/Author: 2026-05-12 / Codex.

## Outcomes & Retrospective

Work is in progress. At completion, summarize which async decisions moved into pure reducers, which adapters remain as interpreters, whether complexity decreased or increased, and any remaining workflow debt intentionally left outside this pass.

## Context and Orientation

The portfolio optimizer stores application data under `[:portfolio :optimizer]`. Shared path constants live in `src/hyperopen/portfolio/optimizer/contracts.cljs`. Runtime effect adapters live under `src/hyperopen/runtime/effect_adapters/**`; they are allowed to touch browser APIs, worker clients, persistence, and network functions. Application workflow namespaces under `src/hyperopen/portfolio/optimizer/application/**` should remain pure: given ordinary maps and event data, they return a new state and command maps, but they do not call Promise APIs, mutate atoms, post worker messages, or read global state.

The four debt hotspots are independent but related. The pipeline adapter decides whether to fail, run immediately, wait for active history prefetch, or load history before running. The history adapter drains one queued selection prefetch at a time and applies success or failure while guarding stale request signatures. The scenario adapter sequences persistence calls for index load, scenario load, archive, duplicate, manual-tracking enablement, and save. The worker run bridge dedupes run requests, installs a worker handler, posts worker messages, applies progress, and ignores stale results.

## Plan of Work

First, establish test-first coverage for the command boundary. Add tests that call pure workflow functions directly and assert command maps for the important branches: pipeline waits for active prefetch instead of loading immediately, selection prefetch starts the next queued request and terminates when no queue remains, scenario save and archive produce explicit persistence command sequences, and run bridge start/message reducers return worker commands while preserving stale-response guards.

Second, add pure workflow namespaces under `src/hyperopen/portfolio/optimizer/application/`. `pipeline_workflow.cljs` should own branch planning and progress state updates for pipeline runs. `history_workflow.cljs` should own history load and selection-prefetch state transitions plus commands for requesting bundles. `scenario_workflow.cljs` should wrap existing scenario state reducers and build persistence command steps. `run_bridge_workflow.cljs` should own run dedupe, run state transitions, stale message detection, and worker command production.

Third, rewire the runtime adapters as interpreters. Each adapter should read the store, call the workflow reducer, `swap!` only through the returned state update, and interpret commands with the existing environment functions. Promise chaining may remain in interpreters where an effect truly depends on an earlier result, but branch decisions and state transitions should be in pure functions that tests can exercise without timers, workers, or persistence.

Fourth, preserve compatibility. Keep `run-portfolio-optimizer-pipeline-effect`, `load-portfolio-optimizer-history-effect`, `save-portfolio-optimizer-scenario-effect`, `request-run!`, and `handle-worker-message!` signatures stable. Keep existing dynamic vars in `portfolio_optimizer.cljs` so tests and facade wiring continue to work.

Fifth, run validation. Because code changes touch optimizer runtime behavior, required gates are `npm run check`, `npm test`, and `npm run test:websocket`. If any browser-flow tooling changes unexpectedly, run the smallest relevant Playwright command, but none is planned.

## Concrete Steps

All commands run from `/Users/barry/.codex/worktrees/3471/hyperopen`.

Install dependencies if the worktree has no usable `node_modules`:

    npm ci

Observed on this worktree:

    added 335 packages, and audited 336 packages in 3s

Run the baseline before adding RED tests:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

Expected baseline after dependency install: the command exits with code 0 and reports zero ClojureScript failures and errors.

Observed baseline after dependency install:

    Ran 3842 tests containing 21246 assertions.
    0 failures, 0 errors.

Add RED tests in the relevant test files, then rerun:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

Expected before implementation: failures name missing workflow functions or mismatched command maps in the new reducer tests.

Observed RED validation:

    The required namespace "hyperopen.portfolio.optimizer.application.history-workflow" is not available, it was required by "hyperopen/portfolio/optimizer/application/history_workflow_test.cljs".

Implement source changes and rerun:

    npm run test:runner:generate && npx shadow-cljs --force-spawn compile test && node out/test.js

Expected after implementation: all ClojureScript tests pass with 0 failures and 0 errors.

Observed GREEN validation:

    Ran 3853 tests containing 21285 assertions.
    0 failures, 0 errors.

Required final gates:

    npm run check
    npm test
    npm run test:websocket

Expected final result: each command exits with code 0. If a command fails for an unrelated pre-existing reason, record the exact output and residual risk before handoff.

## Validation and Acceptance

Acceptance is met when the optimizer runtime adapters no longer own the main workflow decisions named in the user request. The decisions must be observable in pure tests: command maps describe wait, load, run, request bundle, persist scenario, post worker, and no-op stale message outcomes. The adapter tests must still pass, proving the existing public effect behavior did not change.

The worker run bridge must still dedupe identical in-flight signatures, but the new tests should clarify retry behavior after failed or completed runs. Stale worker result, error, and progress messages must not mutate current state when the run id or active scenario no longer matches.

The history selection-prefetch workflow must still drain queued instruments sequentially and terminate cleanly when active work exists or the queue is empty. Removed instruments must not merge stale history into optimizer data.

The scenario workflows must still persist records and indexes in the same order as today, but their next persistence steps must be represented by explicit pure commands before interpretation.

## Idempotence and Recovery

The refactor is additive before adapter rewiring. If a new reducer test fails for the wrong reason, keep the test file and correct only the expected command shape before touching source. If rewiring an adapter causes an existing behavior regression, keep the new pure namespace and revert only the adapter hunk before applying a smaller interpreter change.

Running `npm ci`, test generation, Shadow compilation, and Node tests is safe to repeat. Do not change persisted scenario record schemas, optimizer request contracts, worker wire schemas, or browser storage keys in this plan.

## Artifacts and Notes

Important source hotspots:

    src/hyperopen/runtime/effect_adapters/portfolio_optimizer_pipeline.cljs
      Current pipeline branch decisions and prefetch polling interpreter.

    src/hyperopen/runtime/effect_adapters/portfolio_optimizer/history.cljs
      Current history load state transitions and selection-prefetch drain interpreter.

    src/hyperopen/runtime/effect_adapters/portfolio_optimizer_scenarios.cljs
      Current scenario persistence workflows.

    src/hyperopen/portfolio/optimizer/application/run_bridge.cljs
      Current global last-run dedupe atom, worker command posting, and worker message state transitions.

Planned pure workflow modules:

    src/hyperopen/portfolio/optimizer/application/pipeline_workflow.cljs
    src/hyperopen/portfolio/optimizer/application/history_workflow.cljs
    src/hyperopen/portfolio/optimizer/application/scenario_workflow.cljs
    src/hyperopen/portfolio/optimizer/application/run_bridge_workflow.cljs

## Interfaces and Dependencies

The pure workflow functions should return maps shaped like:

    {:state next-state
     :commands [{:command/type :optimizer.workflow/some-command
                 ...}]}

Commands are plain data. Interpreters may choose to return Promises, but reducers must not construct Promises or call side effects.

The command vocabulary is intentionally local. Pipeline commands include waiting for history idle, loading history, and requesting a worker run. History commands include requesting a history bundle. Scenario commands include loading and saving scenario persistence records and indexes. Run bridge commands include installing the worker handler and posting a worker run.

## Plan Revision Notes

2026-05-12 / Codex: Created the active plan from the maintainer request after inspecting the optimizer runtime adapters, existing tests, recent completed optimizer boundary plans, and subagent findings. The plan keeps public effect APIs stable and moves decision logic into small pure workflow namespaces.
