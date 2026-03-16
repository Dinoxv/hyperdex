import test from "node:test";
import assert from "node:assert/strict";
import { aggregateSummaryState, runDesignReview } from "../src/design_review_runner.mjs";

test("aggregateSummaryState prioritizes fail before blocked before pass", () => {
  assert.equal(aggregateSummaryState([{ status: "PASS" }, { status: "PASS" }]), "PASS");
  assert.equal(aggregateSummaryState([{ status: "PASS" }, { status: "BLOCKED" }]), "BLOCKED");
  assert.equal(aggregateSummaryState([{ status: "BLOCKED" }, { status: "FAIL" }]), "FAIL");
});

test("runDesignReview dry-run exposes the required pass matrix and review widths", async () => {
  const result = await runDesignReview({}, {
    dryRun: true,
    targetIds: ["trade-route"]
  });

  assert.equal(result.dryRun, true);
  assert.deepEqual(result.passes, [
    "visual",
    "native-control",
    "styling-consistency",
    "interaction",
    "layout-regression",
    "jank-perf"
  ]);
  assert.deepEqual(
    result.viewports.map((entry) => entry.width),
    [375, 768, 1280, 1440]
  );
  assert.deepEqual(result.selection.targets.map((target) => target.id), ["trade-route"]);
});
