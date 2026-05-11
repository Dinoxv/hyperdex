import test from "node:test";
import assert from "node:assert/strict";

import { findViolations } from "./check-contract-paths.mjs";

test("optimizer production code keeps root state paths in contracts namespace", () => {
  assert.deepEqual(findViolations(), []);
});
