---
owner: platform
status: canonical
last_reviewed: 2026-03-03
review_cycle_days: 90
source_of_truth: true
---

# Planning and Execution

## Scope
This document governs planning artifacts for implementation work.

## ExecPlan Contract
- ExecPlans must follow `/hyperopen/.agents/PLANS.md`.
- Use an ExecPlan for complex features and significant refactors.

## Tracking Boundary
- Issue lifecycle tracking lives in `/hyperopen/docs/WORK_TRACKING.md` and `bd`.
- ExecPlan checklists/progress entries are required implementation artifacts and do not replace `bd` issue status tracking.

## Storage Layout
- Active plans: `/hyperopen/docs/exec-plans/active/`
- Completed plans: `/hyperopen/docs/exec-plans/completed/`
- Deferred plans: `/hyperopen/docs/exec-plans/deferred/`
- Debt tracker: `/hyperopen/docs/exec-plans/tech-debt-tracker.md`

## Workflow
1. Capture intent, assumptions, and acceptance criteria in an active plan.
2. Link the active plan to live `bd` work and keep progress, discoveries, decision log, and retrospective updated while implementing.
3. Keep at least one unchecked progress item in an active plan while work remains.
4. Move the plan to completed after acceptance criteria pass.
5. Move stale or intentionally paused planning notes out of active and into deferred.

## Guardrails
- `active` means work is being executed now, not “maybe later” or “historical context.”
- Active ExecPlans must reference at least one live `bd` issue and must retain at least one unchecked progress item.
- `npm run check` enforces the active-plan guardrails through `/hyperopen/dev/check_docs.clj`.
- `completed` is for accepted or otherwise closed historical records.
- `deferred` is for non-active planning notes; `bd` remains the source of truth for whether deferred work is actually open backlog.
