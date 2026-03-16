import fs from "node:fs/promises";
import path from "node:path";
import { captureSnapshot } from "./capture_pipeline.mjs";
import { loadDesignReviewConfig, resolveDesignReviewSelection } from "./design_review_loader.mjs";
import { assertDesignReviewSummary } from "./design_review_contracts.mjs";
import { classifyErrorMessage } from "./failure_classification.mjs";
import { writeSnapshotArtifacts } from "./service.mjs";
import {
  getBoundingBoxes,
  getComputedStyles,
  listNativeControls,
  runFocusWalk,
  runLayoutAudit,
  traceInteraction
} from "./dom_probes.mjs";
import { ensureDir, safeNowIso, writeJsonFile } from "./util.mjs";

const PASS_ORDER = [
  "visual",
  "native-control",
  "styling-consistency",
  "interaction",
  "layout-regression",
  "jank-perf"
];

function parseCsvArg(value) {
  if (!value) {
    return [];
  }
  return String(value)
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function issueId(passName, targetId, viewport, ordinal) {
  return `${passName}:${targetId}:${viewport}:${ordinal}`;
}

function flatten(list) {
  return list.flatMap((entry) => entry);
}

function parsePx(value) {
  if (typeof value !== "string") {
    return null;
  }
  const trimmed = value.trim().toLowerCase();
  if (!trimmed.endsWith("px")) {
    return null;
  }
  const number = Number.parseFloat(trimmed.slice(0, -2));
  return Number.isFinite(number) ? number : null;
}

function hasAllowedValue(value, allowed = []) {
  const parsed = parsePx(value);
  if (parsed === null) {
    return true;
  }
  return allowed.some((candidate) => Math.abs(parsed - candidate) < 0.26);
}

function buildIssue({
  passName,
  target,
  viewportName,
  severity,
  selector,
  artifactPath,
  observed,
  expected,
  confidence,
  category,
  reproSteps
}) {
  return {
    severity,
    pass: passName,
    route: target.route,
    viewport: viewportName,
    selector,
    reproSteps,
    artifactPath,
    observedBehavior: observed,
    expectedBehavior: expected,
    confidence,
    category
  };
}

function gradeVisualPass({ target, references, screenshotPath }) {
  if (!references.length) {
    return {
      status: "BLOCKED",
      summary: "No governed or route-level design references were configured for this target.",
      issues: [],
      blindSpots: [
        `${target.route}: visual comparison is blocked because no design reference was resolved.`
      ]
    };
  }
  return {
    status: "PASS",
    summary: "Design references were resolved and screenshots were captured for agent inspection.",
    issues: [],
    blindSpots: [],
    evidencePaths: [screenshotPath]
  };
}

function gradeNativeControls({ target, viewportName, nativeControls, artifactPath }) {
  const unexpected = nativeControls?.unexpectedSpecialNative || [];
  const issues = unexpected.map((entry) =>
    buildIssue({
      passName: "native-control",
      target,
      viewportName,
      severity: "medium",
      selector:
        entry.parityId ||
        entry.dataRole ||
        entry.id ||
        `${entry.tag}${entry.inputType ? `[type=${entry.inputType}]` : ""}`,
      artifactPath,
      observed: `Unexpected native control ${entry.descriptor} is visible.`,
      expected: "Special native controls must be explicitly allowlisted before shipping.",
      confidence: 0.92,
      category: "native-control-leak",
      reproSteps: [
        `Open ${target.route} at ${viewportName}.`,
        `Inspect the visible ${entry.descriptor} control.`
      ]
    })
  );

  return {
    status: issues.length > 0 ? "FAIL" : "PASS",
    summary:
      issues.length > 0
        ? `Found ${issues.length} unexpected special native control(s).`
        : "No unexpected special native controls were detected.",
    issues,
    blindSpots: [],
    evidencePaths: [artifactPath]
  };
}

function gradeStyleConsistency({
  target,
  viewportName,
  computedStyles,
  artifactPath,
  designConfig
}) {
  const allowed = designConfig.approvedValues || {};
  const issues = [];
  const styleGroups = [
    {
      props: ["fontSize"],
      allowed: allowed.fontSizePx || [],
      category: "token-drift",
      expected: "Font sizes should stay on approved design-system values."
    },
    {
      props: ["lineHeight"],
      allowed: allowed.lineHeightPx || [],
      category: "token-drift",
      expected: "Line heights should stay on approved design-system values."
    },
    {
      props: ["letterSpacing"],
      allowed: allowed.letterSpacingPx || [],
      category: "token-drift",
      expected: "Letter spacing should stay on approved design-system values."
    },
    {
      props: [
        "paddingTop",
        "paddingRight",
        "paddingBottom",
        "paddingLeft",
        "marginTop",
        "marginRight",
        "marginBottom",
        "marginLeft",
        "gap",
        "rowGap",
        "columnGap"
      ],
      allowed: allowed.spacingPx || [],
      category: "spacing-mismatch",
      expected: "Spacing values should stay on approved rhythm values."
    },
    {
      props: ["borderRadius"],
      allowed: allowed.radiusPx || [],
      category: "radius-mismatch",
      expected: "Border radii should stay on approved geometry values."
    },
    {
      props: ["borderTopWidth", "borderRightWidth", "borderBottomWidth", "borderLeftWidth"],
      allowed: allowed.borderWidthPx || [],
      category: "token-drift",
      expected: "Border widths should stay on approved design-system values."
    }
  ];

  for (const selectorResult of computedStyles?.selectors || []) {
    for (const match of selectorResult.matches || []) {
      for (const group of styleGroups) {
        for (const prop of group.props) {
          if (!hasAllowedValue(match.styles?.[prop], group.allowed)) {
            issues.push(
              buildIssue({
                passName: "styling-consistency",
                target,
                viewportName,
                severity: group.category === "spacing-mismatch" ? "medium" : "low",
                selector: selectorResult.selector,
                artifactPath,
                observed: `${prop} resolved to ${match.styles?.[prop]}.`,
                expected: group.expected,
                confidence: 0.75,
                category: group.category,
                reproSteps: [
                  `Open ${target.route} at ${viewportName}.`,
                  `Inspect ${selectorResult.selector} computed ${prop}.`
                ]
              })
            );
          }
        }
      }
    }
  }

  return {
    status: issues.length > 0 ? "FAIL" : "PASS",
    summary:
      issues.length > 0
        ? `Found ${issues.length} out-of-scale computed style values.`
        : "Computed style values stayed within the configured design-system scales.",
    issues,
    blindSpots: [],
    evidencePaths: [artifactPath]
  };
}

function gradeInteraction({
  target,
  viewportName,
  focusWalk,
  interactionTrace,
  artifactPath
}) {
  const issues = [];
  if ((focusWalk?.count || 0) === 0) {
    return {
      status: "BLOCKED",
      summary: "No focusable controls were found for keyboard traversal.",
      issues: [],
      blindSpots: [`${target.route}: hover, active, disabled, and loading states were not reachable.`],
      evidencePaths: [artifactPath]
    };
  }

  for (const missing of focusWalk?.steps?.filter((entry) => !entry.hasVisibleFocusIndicator) || []) {
    issues.push(
      buildIssue({
        passName: "interaction",
        target,
        viewportName,
        severity: "high",
        selector: missing.parityId || missing.dataRole || missing.id || missing.tag,
        artifactPath,
        observed: "Focused element did not expose a visible focus indicator.",
        expected: "Keyboard-focusable controls must expose a visible focus indicator.",
        confidence: 0.88,
        category: "focus-regression",
        reproSteps: [
          `Open ${target.route} at ${viewportName}.`,
          "Move keyboard focus onto the affected control."
        ]
      })
    );
  }

  const blindSpots = [
    `${target.route}: hover, active, disabled, and loading states still require targeted route actions when not present by default.`
  ];
  if (!interactionTrace?.performanceObserverSupported) {
    blindSpots.push(`${target.route}: performance observers were unavailable for interaction tracing.`);
  }

  return {
    status: issues.length > 0 ? "FAIL" : "PASS",
    summary:
      issues.length > 0
        ? `Found ${issues.length} focus-indicator regression(s) during keyboard traversal.`
        : "Keyboard traversal completed with visible focus indicators on sampled controls.",
    issues,
    blindSpots,
    evidencePaths: [artifactPath]
  };
}

function gradeLayout({
  target,
  viewportName,
  layoutAudit,
  artifactPath
}) {
  const issues = [];
  if ((layoutAudit?.documentHorizontalOverflowPx || 0) > 1) {
    issues.push(
      buildIssue({
        passName: "layout-regression",
        target,
        viewportName,
        severity: "high",
        selector: target.selectors[0] || target.route,
        artifactPath,
        observed: `Document overflowed horizontally by ${layoutAudit.documentHorizontalOverflowPx}px.`,
        expected: "The route should not introduce horizontal viewport overflow.",
        confidence: 0.9,
        category: "layout-overflow",
        reproSteps: [
          `Open ${target.route} at ${viewportName}.`,
          "Observe the page width and horizontal scrolling behavior."
        ]
      })
    );
  }

  for (const entry of layoutAudit?.overflowIssues || []) {
    issues.push(
      buildIssue({
        passName: "layout-regression",
        target,
        viewportName,
        severity: entry.issues.includes("out-of-viewport") ? "high" : "medium",
        selector: entry.selector,
        artifactPath,
        observed: `Layout issue(s): ${entry.issues.join(", ")}.`,
        expected: "Reviewed surfaces should not clip or overflow the viewport unexpectedly.",
        confidence: 0.82,
        category: entry.issues.includes("out-of-viewport") ? "layout-overflow" : "z-index-overlap",
        reproSteps: [
          `Open ${target.route} at ${viewportName}.`,
          `Inspect ${entry.selector}.`
        ]
      })
    );
  }

  return {
    status: issues.length > 0 ? "FAIL" : "PASS",
    summary:
      issues.length > 0
        ? `Found ${issues.length} overflow or viewport clipping issue(s).`
        : "No horizontal overflow or selector-level clipping was detected.",
    issues,
    blindSpots: [],
    evidencePaths: [artifactPath]
  };
}

function gradeJankPerf({
  target,
  viewportName,
  interactionTrace,
  artifactPath,
  designConfig
}) {
  if (!interactionTrace?.performanceObserverSupported) {
    return {
      status: "BLOCKED",
      summary: "Performance observers were unavailable in this browser session.",
      issues: [],
      blindSpots: [`${target.route}: layout-shift and long-task metrics were unavailable.`],
      evidencePaths: [artifactPath]
    };
  }

  const thresholds = designConfig.interactionTrace || {};
  const issues = [];
  if ((interactionTrace.layoutShiftValue || 0) >= (thresholds.layoutShiftFailThreshold || 0.1)) {
    issues.push(
      buildIssue({
        passName: "jank-perf",
        target,
        viewportName,
        severity: "high",
        selector: target.selectors[0] || target.route,
        artifactPath,
        observed: `Layout shift accumulated ${interactionTrace.layoutShiftValue.toFixed(3)} during the sampled interaction trace.`,
        expected: "Repeated interaction traces should not introduce visible layout shifts.",
        confidence: 0.86,
        category: "flicker-jank",
        reproSteps: [
          `Open ${target.route} at ${viewportName}.`,
          "Repeat focus and scroll interactions several times."
        ]
      })
    );
  }
  if ((interactionTrace.maxLongTaskMs || 0) >= (thresholds.longTaskFailThresholdMs || 120)) {
    issues.push(
      buildIssue({
        passName: "jank-perf",
        target,
        viewportName,
        severity: "medium",
        selector: target.selectors[0] || target.route,
        artifactPath,
        observed: `Interaction trace recorded a long task of ${interactionTrace.maxLongTaskMs.toFixed(1)}ms.`,
        expected: "Repeated interaction traces should avoid long blocking tasks.",
        confidence: 0.8,
        category: "flicker-jank",
        reproSteps: [
          `Open ${target.route} at ${viewportName}.`,
          "Repeat focus and scroll interactions several times."
        ]
      })
    );
  }

  return {
    status: issues.length > 0 ? "FAIL" : "PASS",
    summary:
      issues.length > 0
        ? `Interaction tracing detected ${issues.length} jank or long-task issue(s).`
        : "Interaction tracing did not detect excessive layout shifts or long tasks.",
    issues,
    blindSpots: [],
    evidencePaths: [artifactPath]
  };
}

function statusRank(status) {
  switch (status) {
    case "FAIL":
      return 3;
    case "BLOCKED":
      return 2;
    case "PASS":
    default:
      return 1;
  }
}

export function aggregateSummaryState(passEntries) {
  if (passEntries.some((entry) => entry.status === "FAIL")) {
    return "FAIL";
  }
  if (passEntries.some((entry) => entry.status === "BLOCKED")) {
    return "BLOCKED";
  }
  return "PASS";
}

function renderSummaryMarkdown(summary) {
  const passLines = summary.passes
    .map(
      (entry) =>
        `- ${entry.pass}: ${entry.status} (${entry.issueCount} issue(s))${entry.blockedReason ? ` - ${entry.blockedReason}` : ""}`
    )
    .join("\n");

  const issueLines = summary.issues.length
    ? summary.issues
        .map(
          (issue) =>
            `- [${issue.severity}] ${issue.pass} / ${issue.route} / ${issue.viewport}: ${issue.observedBehavior}`
        )
        .join("\n")
    : "- None.";

  const blindSpotLines = summary.residualBlindSpots.length
    ? summary.residualBlindSpots.map((entry) => `- ${entry}`).join("\n")
    : "- None.";

  return `# Design Review - ${summary.runId}

## Summary

- State: \`${summary.state}\`
- Targets: ${summary.targets.map((target) => `\`${target.id}\``).join(", ")}
- Viewports: ${summary.inspectedViewports.map((entry) => `\`${entry}\``).join(", ")}
- Started: \`${summary.startedAt}\`
- Ended: \`${summary.endedAt}\`

## Passes

${passLines}

## Issues

${issueLines}

## Residual Blind Spots

${blindSpotLines}
`;
}

async function writeProbe(runDir, targetId, viewportName, name, value) {
  const filePath = path.join(runDir, targetId, viewportName, "probes", `${name}.json`);
  await writeJsonFile(filePath, value);
  return filePath;
}

async function safeProbe(fn) {
  try {
    return { ok: true, value: await fn() };
  } catch (error) {
    return { ok: false, error };
  }
}

function blockedPass(reason, evidencePaths = []) {
  return {
    status: "BLOCKED",
    summary: reason,
    issueCount: 0,
    blockedReason: reason,
    evidencePaths
  };
}

export async function runDesignReview(service, options = {}) {
  const designConfig = await loadDesignReviewConfig(options.designConfigPath);
  const selection = await resolveDesignReviewSelection({
    changedFiles: options.changedFiles || [],
    routingPath: options.routingPath,
    targetIds: options.targetIds || []
  });
  const viewports = Object.entries(designConfig.viewports).filter(
    ([name]) => !options.viewports || options.viewports.length === 0 || options.viewports.includes(name)
  );
  if (viewports.length === 0) {
    throw new Error("No design-review viewports were selected.");
  }

  if (options.dryRun) {
    return {
      dryRun: true,
      passes: designConfig.passes,
      viewports: viewports.map(([name, viewport]) => ({
        name,
        width: viewport.width,
        height: viewport.height
      })),
      selection
    };
  }

  const startedAt = safeNowIso();
  const run = await service.sessionManager.store.createRun("design-review", {
    requestedAt: startedAt,
    changedFiles: selection.changedFiles,
    targetIds: selection.targets.map((target) => target.id)
  });

  let sessionId = options.sessionId;
  let tempSession = null;

  try {
    if (!sessionId) {
      tempSession = await service.startSession({
        headless: options.headless,
        manageLocalApp: options.manageLocalApp ?? true,
        localAppUrl: options.localUrl,
        attachPort: options.attachPort || null,
        attachHost: options.attachHost || null,
        targetId: options.targetId || null,
        readOnly: true
      });
      sessionId = tempSession.id;
    }

    const reviewSpec = {
      createdAt: safeNowIso(),
      changedFiles: selection.changedFiles,
      matchedRuleIds: selection.matchedRuleIds,
      passes: designConfig.passes,
      targets: selection.targets,
      viewports: viewports.map(([name, viewport]) => ({
        name,
        width: viewport.width,
        height: viewport.height
      }))
    };
    const reviewSpecPath = path.join(run.runDir, "review-spec.json");
    await writeJsonFile(reviewSpecPath, reviewSpec);

    const passEntries = [];
    const issues = [];
    const blindSpots = [];
    const targetResults = [];

    for (const target of selection.targets) {
      const targetResult = {
        id: target.id,
        route: target.route,
        url: target.url,
        referenceDocs: target.referenceDocs,
        workbenchScenes: target.workbenchScenes,
        viewports: []
      };

      for (const [viewportName, viewport] of viewports) {
        const targetDir = path.join(run.runDir, target.id, viewportName);
        await ensureDir(targetDir);

        let snapshotPath = null;
        let screenshotPath = null;
        let evidencePaths = [];

        try {
          const payload = await captureSnapshot(service.sessionManager, sessionId, {
            url: target.url,
            targetLabel: target.id,
            viewportName,
            viewport,
            maskSelectors: service.config.masking.selectors,
            computedStyleKeys: designConfig.computedStyleKeys,
            maxSemanticNodes: designConfig.maxSemanticNodes
          });
          const persisted = await writeSnapshotArtifacts(run.runDir, payload);
          snapshotPath = persisted.snapshotPath;
          screenshotPath = persisted.screenshotPath;
          evidencePaths = [snapshotPath, screenshotPath];
          await service.sessionManager.store.appendArtifact(run.runDir, {
            type: "snapshot",
            target: target.id,
            viewport: viewportName,
            snapshotPath,
            screenshotPath
          });
        } catch (error) {
          const classification = classifyErrorMessage(error?.message || error);
          const status = classification?.classification === "automation-gap" ? "BLOCKED" : "FAIL";
          const reason = error?.message || String(error);
          for (const passName of PASS_ORDER) {
            passEntries.push({
              pass: passName,
              status,
              summary: reason,
              targetId: target.id,
              route: target.route,
              viewport: viewportName,
              issueCount: 0,
              blockedReason: status === "BLOCKED" ? reason : undefined,
              evidencePaths: []
            });
          }
          if (status === "FAIL") {
            issues.push(
              buildIssue({
                passName: "visual",
                target,
                viewportName,
                severity: "high",
                selector: target.selectors[0] || target.route,
                artifactPath: run.runDir,
                observed: `The route failed to capture: ${reason}`,
                expected: "The reviewed route should render and capture successfully.",
                confidence: 0.95,
                category: "layout-overflow",
                reproSteps: [`Open ${target.route} at ${viewportName}.`]
              })
            );
          } else {
            blindSpots.push(`${target.route} / ${viewportName}: capture was blocked by tooling (${reason}).`);
          }
          continue;
        }

        const computedStylesProbe = await safeProbe(() =>
          getComputedStyles(service, sessionId, {
            selectors: target.selectors,
            props: designConfig.computedStyleKeys,
            maxMatches: designConfig.maxSelectorMatches
          })
        );
        const nativeControlsProbe = await safeProbe(() =>
          listNativeControls(service, sessionId, {
            allowlist: target.nativeControlAllowlist
          })
        );
        const boundingBoxesProbe = await safeProbe(() =>
          getBoundingBoxes(service, sessionId, {
            selectors: target.selectors
          })
        );
        const focusWalkProbe = await safeProbe(() =>
          runFocusWalk(service, sessionId, {
            selectors: target.selectors,
            limit: designConfig.focusWalk?.limit || 20
          })
        );
        const layoutAuditProbe = await safeProbe(() =>
          runLayoutAudit(service, sessionId, {
            selectors: target.selectors,
            maxMatches: designConfig.maxSelectorMatches
          })
        );
        const interactionTraceProbe = await safeProbe(() =>
          traceInteraction(service, sessionId, {
            selectors: target.selectors,
            focusLimit: designConfig.interactionTrace?.focusLimit,
            scrollFractions: designConfig.interactionTrace?.scrollFractions,
            delayMs: designConfig.interactionTrace?.delayMs
          })
        );

        const probePaths = {};
        if (computedStylesProbe.ok) {
          probePaths.computedStyles = await writeProbe(
            run.runDir,
            target.id,
            viewportName,
            "computed-styles",
            computedStylesProbe.value
          );
        }
        if (nativeControlsProbe.ok) {
          probePaths.nativeControls = await writeProbe(
            run.runDir,
            target.id,
            viewportName,
            "native-controls",
            nativeControlsProbe.value
          );
        }
        if (boundingBoxesProbe.ok) {
          probePaths.boundingBoxes = await writeProbe(
            run.runDir,
            target.id,
            viewportName,
            "bounding-boxes",
            boundingBoxesProbe.value
          );
        }
        if (focusWalkProbe.ok) {
          probePaths.focusWalk = await writeProbe(
            run.runDir,
            target.id,
            viewportName,
            "focus-walk",
            focusWalkProbe.value
          );
        }
        if (layoutAuditProbe.ok) {
          probePaths.layoutAudit = await writeProbe(
            run.runDir,
            target.id,
            viewportName,
            "layout-audit",
            layoutAuditProbe.value
          );
        }
        if (interactionTraceProbe.ok) {
          probePaths.interactionTrace = await writeProbe(
            run.runDir,
            target.id,
            viewportName,
            "interaction-trace",
            interactionTraceProbe.value
          );
        }

        const references = [...(target.referenceDocs || []), ...(target.workbenchScenes || [])];
        const reviewResults = [
          {
            pass: "visual",
            ...gradeVisualPass({
              target,
              references,
              screenshotPath
            })
          },
          nativeControlsProbe.ok
            ? {
                pass: "native-control",
                ...gradeNativeControls({
                  target,
                  viewportName,
                  nativeControls: nativeControlsProbe.value,
                  artifactPath: probePaths.nativeControls
                })
              }
            : {
                pass: "native-control",
                ...blockedPass(
                  nativeControlsProbe.error?.message || "Native-control probe failed.",
                  evidencePaths
                )
              },
          computedStylesProbe.ok
            ? {
                pass: "styling-consistency",
                ...gradeStyleConsistency({
                  target,
                  viewportName,
                  computedStyles: computedStylesProbe.value,
                  artifactPath: probePaths.computedStyles,
                  designConfig
                })
              }
            : {
                pass: "styling-consistency",
                ...blockedPass(
                  computedStylesProbe.error?.message || "Computed-style probe failed.",
                  evidencePaths
                )
              },
          focusWalkProbe.ok
            ? {
                pass: "interaction",
                ...gradeInteraction({
                  target,
                  viewportName,
                  focusWalk: focusWalkProbe.value,
                  interactionTrace: interactionTraceProbe.ok ? interactionTraceProbe.value : null,
                  artifactPath: probePaths.focusWalk
                })
              }
            : {
                pass: "interaction",
                ...blockedPass(
                  focusWalkProbe.error?.message || "Focus-walk probe failed.",
                  evidencePaths
                )
              },
          layoutAuditProbe.ok
            ? {
                pass: "layout-regression",
                ...gradeLayout({
                  target,
                  viewportName,
                  layoutAudit: layoutAuditProbe.value,
                  artifactPath: probePaths.layoutAudit
                })
              }
            : {
                pass: "layout-regression",
                ...blockedPass(
                  layoutAuditProbe.error?.message || "Layout-audit probe failed.",
                  evidencePaths
                )
              },
          interactionTraceProbe.ok
            ? {
                pass: "jank-perf",
                ...gradeJankPerf({
                  target,
                  viewportName,
                  interactionTrace: interactionTraceProbe.value,
                  artifactPath: probePaths.interactionTrace,
                  designConfig
                })
              }
            : {
                pass: "jank-perf",
                ...blockedPass(
                  interactionTraceProbe.error?.message || "Interaction trace probe failed.",
                  evidencePaths
                )
              }
        ];

        reviewResults.sort((left, right) => PASS_ORDER.indexOf(left.pass) - PASS_ORDER.indexOf(right.pass));

        for (const result of reviewResults) {
          result.targetId = target.id;
          result.route = target.route;
          result.viewport = viewportName;
          result.issueCount = result.issues?.length || 0;
          result.evidencePaths = result.evidencePaths || evidencePaths;
          passEntries.push(result);
          issues.push(
            ...(result.issues || []).map((issue, index) => ({
              id: issueId(result.pass, target.id, viewportName, index + 1),
              ...issue
            }))
          );
          blindSpots.push(...(result.blindSpots || []));
        }

        targetResult.viewports.push({
          name: viewportName,
          width: viewport.width,
          height: viewport.height,
          snapshotPath,
          screenshotPath,
          probePaths,
          passes: reviewResults.map((entry) => ({
            pass: entry.pass,
            status: entry.status,
            issueCount: entry.issueCount
          }))
        });
      }

      targetResults.push(targetResult);
    }

    const aggregatedPasses = PASS_ORDER.map((passName) => {
      const matches = passEntries.filter((entry) => entry.pass === passName);
      const issueCount = matches.reduce((sum, entry) => sum + (entry.issueCount || 0), 0);
      const status = [...matches].sort((left, right) => statusRank(right.status) - statusRank(left.status))[0]?.status || "BLOCKED";
      const blockedReason = matches.find((entry) => entry.blockedReason)?.blockedReason;
      return {
        pass: passName,
        status,
        issueCount,
        blockedReason,
        evidencePaths: [...new Set(flatten(matches.map((entry) => entry.evidencePaths || [])))]
      };
    });

    const summary = {
      runId: run.runId,
      runDir: run.runDir,
      state: aggregateSummaryState(aggregatedPasses),
      startedAt,
      endedAt: safeNowIso(),
      reviewSpecPath,
      inspectedViewports: viewports.map(([, viewport]) => viewport.width),
      targets: targetResults.map((entry) => ({
        id: entry.id,
        route: entry.route
      })),
      targetResults,
      passes: aggregatedPasses,
      issues,
      residualBlindSpots: [...new Set(blindSpots)].sort()
    };

    assertDesignReviewSummary(summary);

    const passesDir = path.join(run.runDir, "passes");
    await ensureDir(passesDir);
    for (const passEntry of aggregatedPasses) {
      const details = passEntries.filter((entry) => entry.pass === passEntry.pass);
      await writeJsonFile(path.join(passesDir, `${passEntry.pass}.json`), {
        ...passEntry,
        details
      });
    }

    const summaryPath = path.join(run.runDir, "summary.json");
    const summaryMarkdownPath = path.join(run.runDir, "summary.md");
    await writeJsonFile(summaryPath, summary);
    await fs.writeFile(summaryMarkdownPath, renderSummaryMarkdown(summary));

    if (summary.state === "PASS") {
      await service.sessionManager.store.completeRun(run.runDir, {
        state: summary.state,
        summaryPath,
        summaryMarkdownPath
      });
    } else {
      await service.sessionManager.store.failRun(run.runDir, summary.state);
    }

    return summary;
  } catch (error) {
    await service.sessionManager.store
      .failRun(run.runDir, error?.message || String(error))
      .catch(() => null);
    throw error;
  } finally {
    if (tempSession) {
      await service.stopSession(tempSession.id).catch(() => null);
    }
  }
}

export { parseCsvArg };
