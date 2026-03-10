---
owner: platform
status: canonical
last_reviewed: 2026-03-07
review_cycle_days: 90
source_of_truth: true
---

# Toolchain and Build Reference

Repository build and test entry points:
- `npm run check`
- `npm test`
- `npm run test:crap`
- `npm run test:websocket`
- `npm run dev`
- `npm run portfolio`
- `bb tools/crap_report.clj --scope src`
- `npm run test:browser-inspection`

Local discovery and semantic analysis commands:
- `rg -n "<pattern>" src test`
- `clojure-lsp diagnostics --project-root .`
- `clojure-lsp references --project-root . --from <fqns> --raw`
- `clojure-lsp rename --project-root . --from <old-fqns> --to <new-fqns> --dry`

Usage guidance:
- Prefer `rg` for fast first-pass discovery, broad audits, and cases where the exact fully qualified symbol is not known yet.
- Prefer `clojure-lsp` for symbol-accurate references, rename planning, and editor-backed definition jumps once a persistent LSP session is available.
- Standalone `clj-kondo` is optional in local environments; use `clojure-lsp diagnostics` as the repo-safe semantic analysis default unless `clj-kondo` is explicitly installed and required.

Browser inspection and parity commands:
- `npm run browser:inspect -- --url <target-url> --target <label>`
- `npm run browser:compare`
- `npm run browser:mcp`
- `node tools/browser-inspection/src/cli.mjs session targets --attach-port <cdp-port>`
- `node tools/browser-inspection/src/cli.mjs session attach --attach-port <cdp-port>`
- `node tools/browser-inspection/src/cli.mjs session attach --attach-port <cdp-port> --target-id <cdp-target-id>`

Comprehensive tool surface and use guidance:
- `/hyperopen/docs/tools.md`

UI workbench reference:
- URL: `http://localhost:8080/ui-workbench.html`
- Shadow build: `:portfolio`
- Watch commands: `npm run dev` or `npm run portfolio`
- Scene tree: `/hyperopen/portfolio/hyperopen/workbench/scenes/**`
- Shared workbench helpers: `/hyperopen/portfolio/hyperopen/workbench/support/**`
- Naming convention: file names end with `_scenes.cljs`, namespaces end with `-scenes`

Deterministic target selection workflow:
- `/hyperopen/docs/runbooks/browser-live-inspection.md` (Attach to Your Own Browser and Deterministic Tab Identification)

CI workflow reference:
- `/hyperopen/.github/workflows/tests.yml`
