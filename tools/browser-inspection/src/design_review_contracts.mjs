function fail(message) {
  const error = new Error(`DesignReviewContractError: ${message}`);
  error.name = "DesignReviewContractError";
  throw error;
}

const REQUIRED_PASSES = [
  "visual",
  "native-control",
  "styling-consistency",
  "interaction",
  "layout-regression",
  "jank-perf"
];

const REQUIRED_VIEWPORTS = {
  "review-375": { width: 375, height: 812 },
  "review-768": { width: 768, height: 1024 },
  "review-1280": { width: 1280, height: 900 },
  "review-1440": { width: 1440, height: 900 }
};

function assertObject(value, key) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    fail(`${key} must be an object`);
  }
}

function assertString(value, key) {
  if (typeof value !== "string" || value.trim() === "") {
    fail(`${key} must be a non-empty string`);
  }
}

function assertArray(value, key) {
  if (!Array.isArray(value)) {
    fail(`${key} must be an array`);
  }
}

export function assertDesignReviewConfig(value) {
  assertObject(value, "designReviewConfig");
  assertArray(value.passes, "designReviewConfig.passes");
  assertObject(value.viewports, "designReviewConfig.viewports");
  if (JSON.stringify(value.passes) !== JSON.stringify(REQUIRED_PASSES)) {
    fail(`designReviewConfig.passes must equal ${JSON.stringify(REQUIRED_PASSES)}`);
  }
  const viewportNames = Object.keys(value.viewports || {}).sort();
  const requiredNames = Object.keys(REQUIRED_VIEWPORTS).sort();
  if (JSON.stringify(viewportNames) !== JSON.stringify(requiredNames)) {
    fail(`designReviewConfig.viewports must equal ${JSON.stringify(requiredNames)}`);
  }
  for (const [name, viewport] of Object.entries(value.viewports || {})) {
    assertObject(viewport, `designReviewConfig.viewports.${name}`);
    if (typeof viewport.width !== "number" || Number.isNaN(viewport.width)) {
      fail(`designReviewConfig.viewports.${name}.width must be a number`);
    }
    if (typeof viewport.height !== "number" || Number.isNaN(viewport.height)) {
      fail(`designReviewConfig.viewports.${name}.height must be a number`);
    }
    if (viewport.width !== REQUIRED_VIEWPORTS[name].width) {
      fail(
        `designReviewConfig.viewports.${name}.width must equal ${REQUIRED_VIEWPORTS[name].width}`
      );
    }
    if (viewport.height !== REQUIRED_VIEWPORTS[name].height) {
      fail(
        `designReviewConfig.viewports.${name}.height must equal ${REQUIRED_VIEWPORTS[name].height}`
      );
    }
  }
  assertArray(value.computedStyleKeys || [], "designReviewConfig.computedStyleKeys");
}

export function assertDesignReviewRouting(value) {
  assertObject(value, "designReviewRouting");
  assertArray(value.defaultTargets || [], "designReviewRouting.defaultTargets");
  assertArray(value.targets || [], "designReviewRouting.targets");
  assertArray(value.rules || [], "designReviewRouting.rules");

  for (const [index, target] of (value.targets || []).entries()) {
    assertObject(target, `designReviewRouting.targets[${index}]`);
    assertString(target.id, `designReviewRouting.targets[${index}].id`);
    assertString(target.route, `designReviewRouting.targets[${index}].route`);
    assertString(target.url, `designReviewRouting.targets[${index}].url`);
    assertArray(target.selectors || [], `designReviewRouting.targets[${index}].selectors`);
    assertArray(
      target.referenceDocs || [],
      `designReviewRouting.targets[${index}].referenceDocs`
    );
    assertArray(
      target.workbenchScenes || [],
      `designReviewRouting.targets[${index}].workbenchScenes`
    );
    assertArray(
      target.nativeControlAllowlist || [],
      `designReviewRouting.targets[${index}].nativeControlAllowlist`
    );
  }

  for (const [index, rule] of (value.rules || []).entries()) {
    assertObject(rule, `designReviewRouting.rules[${index}]`);
    assertString(rule.id, `designReviewRouting.rules[${index}].id`);
    assertString(rule.glob, `designReviewRouting.rules[${index}].glob`);
    assertArray(rule.targets || [], `designReviewRouting.rules[${index}].targets`);
  }
}

export function assertDesignReviewSummary(value) {
  assertObject(value, "designReviewSummary");
  assertString(value.runId, "designReviewSummary.runId");
  assertString(value.runDir, "designReviewSummary.runDir");
  assertString(value.state, "designReviewSummary.state");
  if (!["PASS", "FAIL", "BLOCKED"].includes(value.state)) {
    fail("designReviewSummary.state must be PASS, FAIL, or BLOCKED");
  }
  assertString(value.startedAt, "designReviewSummary.startedAt");
  assertString(value.endedAt, "designReviewSummary.endedAt");
  assertArray(value.passes || [], "designReviewSummary.passes");
  assertArray(value.issues || [], "designReviewSummary.issues");
  assertArray(
    value.inspectedViewports || [],
    "designReviewSummary.inspectedViewports"
  );
  assertArray(value.targets || [], "designReviewSummary.targets");
  assertArray(value.residualBlindSpots || [], "designReviewSummary.residualBlindSpots");

  for (const [index, passEntry] of (value.passes || []).entries()) {
    assertObject(passEntry, `designReviewSummary.passes[${index}]`);
    assertString(passEntry.pass, `designReviewSummary.passes[${index}].pass`);
    assertString(passEntry.status, `designReviewSummary.passes[${index}].status`);
    if (!["PASS", "FAIL", "BLOCKED"].includes(passEntry.status)) {
      fail(`designReviewSummary.passes[${index}].status must be PASS, FAIL, or BLOCKED`);
    }
    if (typeof passEntry.issueCount !== "number" || Number.isNaN(passEntry.issueCount)) {
      fail(`designReviewSummary.passes[${index}].issueCount must be a number`);
    }
    assertArray(
      passEntry.evidencePaths || [],
      `designReviewSummary.passes[${index}].evidencePaths`
    );
  }

  for (const [index, issue] of (value.issues || []).entries()) {
    assertObject(issue, `designReviewSummary.issues[${index}]`);
    assertString(issue.severity, `designReviewSummary.issues[${index}].severity`);
    assertString(issue.pass, `designReviewSummary.issues[${index}].pass`);
    assertString(issue.route, `designReviewSummary.issues[${index}].route`);
    assertString(issue.viewport, `designReviewSummary.issues[${index}].viewport`);
    assertString(issue.selector, `designReviewSummary.issues[${index}].selector`);
    assertArray(issue.reproSteps || [], `designReviewSummary.issues[${index}].reproSteps`);
    assertString(issue.artifactPath, `designReviewSummary.issues[${index}].artifactPath`);
    assertString(
      issue.observedBehavior,
      `designReviewSummary.issues[${index}].observedBehavior`
    );
    assertString(
      issue.expectedBehavior,
      `designReviewSummary.issues[${index}].expectedBehavior`
    );
    if (typeof issue.confidence !== "number" || Number.isNaN(issue.confidence)) {
      fail(`designReviewSummary.issues[${index}].confidence must be a number`);
    }
  }
}

export function assertBrowserQaEvalCorpus(value) {
  assertObject(value, "browserQaEvalCorpus");
  assertArray(value.cases || [], "browserQaEvalCorpus.cases");
  for (const [index, entry] of value.cases.entries()) {
    assertObject(entry, `browserQaEvalCorpus.cases[${index}]`);
    assertString(entry.id, `browserQaEvalCorpus.cases[${index}].id`);
    assertString(entry.sourcePath, `browserQaEvalCorpus.cases[${index}].sourcePath`);
    assertArray(
      entry.expectedCategories || [],
      `browserQaEvalCorpus.cases[${index}].expectedCategories`
    );
    assertObject(entry.report, `browserQaEvalCorpus.cases[${index}].report`);
  }
}

export function isDesignReviewContractError(error) {
  return error?.name === "DesignReviewContractError";
}
