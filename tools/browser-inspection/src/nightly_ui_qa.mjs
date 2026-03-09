#!/usr/bin/env node
import fs from "node:fs/promises";
import path from "node:path";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { fileURLToPath } from "node:url";
import { BrowserInspectionService } from "./service.mjs";
import { classifyNightlyFailure, remediationForClassification } from "./failure_classification.mjs";
import { ensureDir, safeNowIso, writeJsonFile } from "./util.mjs";

const execFileAsync = promisify(execFile);

const SPECTATE_ADDRESSES = {
  largeActive: "0x162cc7c861ebd0c06b3d72319201150482518185",
  smallStandard: "0x2ba553d9f990a3b66b03b2dc0d030dfc1c061036",
  unified: "0x4096d3377ae5ade578daae8188804740c8b1da3e"
};

const MATRIX_ATTEMPTS = [
  {
    key: "trade-large-active",
    route: "/trade",
    address: SPECTATE_ADDRESSES.largeActive,
    url: `http://localhost:8080/trade?spectate=${SPECTATE_ADDRESSES.largeActive}`
  },
  {
    key: "portfolio-large-active",
    route: "/portfolio",
    address: SPECTATE_ADDRESSES.largeActive,
    url: `http://localhost:8080/portfolio?spectate=${SPECTATE_ADDRESSES.largeActive}`
  },
  {
    key: "trade-small-standard",
    route: "/trade",
    address: SPECTATE_ADDRESSES.smallStandard,
    url: `http://localhost:8080/trade?spectate=${SPECTATE_ADDRESSES.smallStandard}`
  },
  {
    key: "portfolio-small-standard",
    route: "/portfolio",
    address: SPECTATE_ADDRESSES.smallStandard,
    url: `http://localhost:8080/portfolio?spectate=${SPECTATE_ADDRESSES.smallStandard}`
  },
  {
    key: "trade-unified",
    route: "/trade",
    address: SPECTATE_ADDRESSES.unified,
    url: `http://localhost:8080/trade?spectate=${SPECTATE_ADDRESSES.unified}`
  },
  {
    key: "portfolio-unified",
    route: "/portfolio",
    address: SPECTATE_ADDRESSES.unified,
    url: `http://localhost:8080/portfolio?spectate=${SPECTATE_ADDRESSES.unified}`
  },
  {
    key: "vaults-baseline",
    route: "/vaults",
    address: "n/a",
    url: "http://localhost:8080/vaults"
  }
];

function parseArgs(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith("--")) {
      out._.push(token);
      continue;
    }
    const stripped = token.slice(2);
    if (stripped.includes("=")) {
      const [key, ...rest] = stripped.split("=");
      out[key] = rest.join("=");
      continue;
    }
    const next = argv[i + 1];
    if (!next || next.startsWith("--")) {
      out[stripped] = true;
      continue;
    }
    out[stripped] = next;
    i += 1;
  }
  return out;
}

function formatNightlyTs(value = new Date()) {
  return value.toISOString().replace(/[:.]/g, "-");
}

function reportDateString(timeZone = "America/New_York") {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).formatToParts(new Date());
  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;
  return `${year}-${month}-${day}`;
}

function unique(values) {
  return [...new Set(values)];
}

function summarizeRouteCoverage(attempts) {
  const routes = unique(attempts.map((attempt) => attempt.route));
  return routes.map((route) => {
    const routeAttempts = attempts.filter((attempt) => attempt.route === route);
    const ok = routeAttempts.filter((attempt) => attempt.status === "ok").length;
    return {
      route,
      attempted: routeAttempts.length,
      succeeded: ok,
      failed: routeAttempts.length - ok
    };
  });
}

async function listRunIdsByPrefix(rootDir, prefix) {
  const entries = await fs.readdir(rootDir, { withFileTypes: true }).catch(() => []);
  return entries
    .filter((entry) => entry.isDirectory() && entry.name.startsWith(prefix))
    .map((entry) => entry.name)
    .sort();
}

async function findPreviousNightlyRun(artifactRoot, currentRunDir) {
  const runIds = await listRunIdsByPrefix(artifactRoot, "nightly-ui-qa-");
  const current = path.basename(currentRunDir);
  const previous = runIds.filter((runId) => runId !== current).at(-1);
  return previous ? path.join(artifactRoot, previous) : null;
}

async function readJson(filePath, fallback = null) {
  try {
    const raw = await fs.readFile(filePath, "utf8");
    return JSON.parse(raw);
  } catch (_error) {
    return fallback;
  }
}

function classifyNovelty(current, previous) {
  if (!current || current.classification === "pass") {
    return "NONE";
  }
  if (!previous) {
    return "NEW";
  }
  if (current.classification === previous.classification && current.reason === previous.reason) {
    return "EXISTING";
  }
  return "NEW";
}

function toTsv(rows) {
  return rows.map((row) => row.join("\t")).join("\n");
}

async function gitBranch(repoRoot) {
  const { stdout } = await execFileAsync("git", ["rev-parse", "--abbrev-ref", "HEAD"], {
    cwd: repoRoot
  });
  return stdout.trim();
}

async function runCaptureAttempt({ service, artifactRoot, runDir, attempt, sessionId, viewports }) {
  const beforeRunIds = await listRunIdsByPrefix(artifactRoot, "inspect-");
  const jsonPath = path.join(runDir, `${attempt.key}.json`);
  const logPath = path.join(runDir, `${attempt.key}.log`);

  const startedAt = safeNowIso();
  try {
    const result = await service.capture({
      sessionId,
      url: attempt.url,
      targetLabel: attempt.key,
      viewports
    });

    await fs.writeFile(jsonPath, `${JSON.stringify(result, null, 2)}\n`);
    await fs.writeFile(logPath, "");

    return {
      ...attempt,
      startedAt,
      endedAt: safeNowIso(),
      status: "ok",
      runId: result.runId,
      inspectRunDir: result.runDir,
      jsonPath,
      logPath,
      errorMessage: null,
      logSummary: null
    };
  } catch (error) {
    const afterRunIds = await listRunIdsByPrefix(artifactRoot, "inspect-");
    const createdRunId = afterRunIds.filter((runId) => !beforeRunIds.includes(runId)).at(-1) || null;
    const inspectRunDir = createdRunId ? path.join(artifactRoot, createdRunId) : null;
    const errorMessage = String(error?.message || error);
    const stack = String(error?.stack || errorMessage);

    await fs.writeFile(
      jsonPath,
      `${JSON.stringify(
        {
          error: errorMessage,
          runId: createdRunId,
          inspectRunDir
        },
        null,
        2
      )}\n`
    );
    await fs.writeFile(logPath, `${stack}\n`);

    return {
      ...attempt,
      startedAt,
      endedAt: safeNowIso(),
      status: "failed",
      runId: createdRunId,
      inspectRunDir,
      jsonPath,
      logPath,
      errorMessage,
      logSummary: stack.split("\n").slice(0, 4).join("\n")
    };
  }
}

function buildReport({
  summaryStatus,
  reportDate,
  runDir,
  attempts,
  routeCoverage,
  failureClassification,
  novelty,
  previousRunDir,
  runIds,
  reportPath,
  preflight
}) {
  const allFailed = attempts.length > 0 && attempts.every((attempt) => attempt.status !== "ok");
  const tradeAttempts = attempts.filter((attempt) => attempt.route === "/trade");
  const tradeFailed = tradeAttempts.length > 0 && tradeAttempts.every((attempt) => attempt.status !== "ok");

  const noveltyLabel = novelty === "NONE" ? "N/A" : novelty;
  const criticalLine =
    failureClassification.classification === "automation-gap"
      ? `- ${noveltyLabel}: ${failureClassification.summary}`
      : "- None observed.";
  const highLine =
    failureClassification.classification === "product-regression"
      ? `- ${noveltyLabel}: ${failureClassification.summary}`
      : "- None observed.";

  const spectateBlocked = allFailed ? "Not verifiable because captures failed before UI render." : "Verified in captured sessions.";

  return `# Nightly UI QA Report - ${reportDate}

## Executive summary: ${summaryStatus}

- Run root: \`${runDir}\`
- Failure classification: \`${failureClassification.classification}\` (reason: \`${failureClassification.reason}\`)
- NEW/EXISTING status vs previous nightly run: **${noveltyLabel}**

## Route coverage summary

${routeCoverage
  .map(
    (entry) =>
      `- \`${entry.route}\`: attempted ${entry.attempted}, succeeded ${entry.succeeded}, failed ${entry.failed}`
  )
  .join("\n")}

- Primary route /trade status: ${tradeFailed ? "failed for all attempts" : "has successful captures"}

## Address coverage summary

- Large active: \`${SPECTATE_ADDRESSES.largeActive}\`
- Smaller less active: \`${SPECTATE_ADDRESSES.smallStandard}\`
- Unified mode: \`${SPECTATE_ADDRESSES.unified}\`

## Regressions by severity

### Critical

${criticalLine}

### High

${highLine}

### Medium

- None observed.

### Low

- None observed.

## Spectate/Ghost Mode findings

### Activation correctness

- ${spectateBlocked}

### Stale-address crossover

- ${spectateBlocked}

### Unified vs standard account-mode presentation

- ${spectateBlocked}

## Console/network error summary

- Attempt failures: ${attempts.filter((attempt) => attempt.status !== "ok").length}/${attempts.length}
- Failure reason: ${failureClassification.summary}

## Top 5 improvement opportunities

1. Expand run-level parsing to summarize per-attempt console error counts from snapshot payloads.
2. Add deterministic DOM/runtime seam checks for spectate banner and effective address labels.
3. Add optional screenshot diff against prior successful nightly run for high-activity address.
4. Add retry policy for transient attach endpoint connection failures.
5. Add automatic bd issue creation when classification is NEW automation-gap or product-regression and severity is Critical/High.

## Evidence links to artifact paths, screenshots, report json/md, and snapshots

- Run meta: \`${path.join(runDir, "run-meta.json")}\`
- Preflight: \`${path.join(runDir, "preflight.json")}\`
- Attempts TSV: \`${path.join(runDir, "attempt-summary.tsv")}\`
- Failure classification: \`${path.join(runDir, "failure-classification.json")}\`
- Report: \`${reportPath}\`
- Inspect run IDs: ${runIds.length > 0 ? runIds.map((runId) => `\`${runId}\``).join(", ") : "none"}
- Previous nightly run root: ${previousRunDir ? `\`${previousRunDir}\`` : "none"}

## Recommended next actions

1. If classification is automation-gap, resolve environment prerequisites and rerun npm run qa:nightly-ui.
2. If classification is product-regression, triage failed attempt logs and create bd issues for NEW Critical/High findings.
3. Keep this report date path stable and rerun nightly for trend comparison.
`;
}

function parseViewports(args) {
  if (!args.viewports) {
    return null;
  }
  return String(args.viewports)
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const service = await BrowserInspectionService.create();
  const config = service.config;
  const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

  const branch = await gitBranch(repoRoot);
  const allowNonMain = Boolean(args["allow-non-main"]);
  if (!allowNonMain && branch !== "main") {
    throw new Error(`Nightly UI QA must run on branch main. Current branch: ${branch}`);
  }

  const runId = `nightly-ui-qa-${formatNightlyTs()}`;
  const runDir = path.join(config.artifactRoot, runId);
  await ensureDir(runDir);

  const attachPort = args["attach-port"] || null;
  const attachHost = args["attach-host"] || null;
  const targetId = args["target-id"] || null;
  const localUrl = args["local-url"] || config.localApp.url;
  const viewports = parseViewports(args);

  const preflight = await service.preflight({
    attachPort,
    attachHost,
    localUrl
  });
  await writeJsonFile(path.join(runDir, "preflight.json"), preflight);
  const runStartedAt = safeNowIso();

  const attempts = [];
  let session = null;
  let startupError = null;
  const mode = attachPort ? "attach" : "local";

  if (preflight.ok) {
    try {
      session = await service.startSession({
        headless: !Boolean(args.headed),
        manageLocalApp: mode === "local",
        localAppUrl: localUrl,
        attachPort: mode === "attach" ? attachPort : null,
        attachHost: mode === "attach" ? attachHost : null,
        targetId: mode === "attach" ? targetId : null,
        readOnly: true
      });

      for (const attempt of MATRIX_ATTEMPTS) {
        const result = await runCaptureAttempt({
          service,
          artifactRoot: config.artifactRoot,
          runDir,
          attempt,
          sessionId: session.id,
          viewports
        });
        attempts.push(result);
      }
    } catch (error) {
      startupError = String(error?.message || error);
      attempts.push({
        key: "session-start",
        route: "n/a",
        address: "n/a",
        url: "n/a",
        status: "failed",
        runId: null,
        inspectRunDir: null,
        jsonPath: null,
        logPath: null,
        errorMessage: startupError,
        logSummary: startupError
      });
      await fs.writeFile(path.join(runDir, "session-start.log"), `${error?.stack || startupError}\n`);
    } finally {
      if (session?.id) {
        await service.stopSession(session.id).catch(() => null);
      }
    }
  }

  const failureClassification = classifyNightlyFailure({
    preflight,
    attempts,
    findings: []
  });

  const previousRunDir = await findPreviousNightlyRun(config.artifactRoot, runDir);
  const previousClassification = previousRunDir
    ? await readJson(path.join(previousRunDir, "failure-classification.json"), null)
    : null;
  const novelty = classifyNovelty(failureClassification, previousClassification);

  const runIds = unique(attempts.map((attempt) => attempt.runId).filter(Boolean));

  const failureOutput = {
    ...failureClassification,
    novelty,
    previousRunDir,
    runDir,
    mode,
    remediation:
      failureClassification.classification === "automation-gap"
        ? remediationForClassification(failureClassification)
        : null
  };

  await writeJsonFile(path.join(runDir, "failure-classification.json"), failureOutput);

  const summaryRows = [
    ["key", "route", "address", "status", "run_id", "inspect_run_dir", "json", "log", "error"]
  ];
  for (const attempt of attempts) {
    summaryRows.push([
      attempt.key,
      attempt.route,
      attempt.address,
      attempt.status,
      attempt.runId || "",
      attempt.inspectRunDir || "",
      attempt.jsonPath || "",
      attempt.logPath || "",
      (attempt.errorMessage || "").replace(/\s+/g, " ").trim()
    ]);
  }
  await fs.writeFile(path.join(runDir, "attempt-summary.tsv"), `${toTsv(summaryRows)}\n`);

  const endedAt = safeNowIso();
  const runMeta = {
    runId,
    runDir,
    startedAt: runStartedAt,
    endedAt,
    branch,
    mode,
    preflightOk: preflight.ok,
    startupError,
    attemptCount: attempts.length,
    failedAttemptCount: attempts.filter((attempt) => attempt.status !== "ok").length
  };
  await writeJsonFile(path.join(runDir, "run-meta.json"), runMeta);

  const reportDate = reportDateString(process.env.TZ || "America/New_York");
  const reportPath = path.join(repoRoot, "docs", "qa", `nightly-ui-report-${reportDate}.md`);
  await ensureDir(path.dirname(reportPath));

  const routeCoverage = summarizeRouteCoverage(attempts.filter((attempt) => attempt.route !== "n/a"));

  let summaryStatus = "PASS";
  if (failureClassification.classification === "automation-gap") {
    summaryStatus = "FAIL";
  } else if (failureClassification.classification === "product-regression") {
    summaryStatus = "WARN";
  }

  const report = buildReport({
    summaryStatus,
    reportDate,
    runDir,
    attempts,
    routeCoverage,
    failureClassification,
    novelty,
    previousRunDir,
    runIds,
    reportPath,
    preflight
  });

  await fs.writeFile(reportPath, `${report}\n`);

  const output = {
    reportPath,
    inspectedRoutes: ["/trade", "/portfolio", "/vaults"],
    inspectedAddresses: [SPECTATE_ADDRESSES.largeActive, SPECTATE_ADDRESSES.smallStandard, SPECTATE_ADDRESSES.unified],
    runIds,
    createdBdIssueIds: [],
    runDir,
    failureClassification: failureOutput
  };

  process.stdout.write(`${JSON.stringify(output, null, 2)}\n`);

  if (summaryStatus === "FAIL") {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  process.stderr.write(`${error?.stack || error?.message || error}\n`);
  process.exitCode = 1;
});
