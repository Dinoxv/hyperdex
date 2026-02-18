import fs from "node:fs/promises";
import path from "node:path";

const COVERAGE_SUMMARY_PATH =
  process.env.COVERAGE_SUMMARY_PATH ?? "coverage/coverage-summary.json";
const COVERAGE_BADGE_PATH =
  process.env.COVERAGE_BADGE_PATH ?? ".github/badges/coverage.json";

function coverageColor(percent) {
  if (percent >= 90) return "brightgreen";
  if (percent >= 80) return "green";
  if (percent >= 70) return "yellowgreen";
  if (percent >= 60) return "yellow";
  if (percent >= 50) return "orange";
  return "red";
}

async function main() {
  const rawSummary = await fs.readFile(COVERAGE_SUMMARY_PATH, "utf8");
  const summary = JSON.parse(rawSummary);
  const lineCoverage = summary?.total?.lines?.pct;

  if (typeof lineCoverage !== "number" || Number.isNaN(lineCoverage)) {
    throw new Error(
      `Expected numeric total.lines.pct in ${COVERAGE_SUMMARY_PATH}`,
    );
  }

  const badgePayload = {
    schemaVersion: 1,
    label: "coverage",
    message: `${lineCoverage.toFixed(2)}%`,
    color: coverageColor(lineCoverage),
  };

  await fs.mkdir(path.dirname(COVERAGE_BADGE_PATH), { recursive: true });
  await fs.writeFile(
    COVERAGE_BADGE_PATH,
    `${JSON.stringify(badgePayload, null, 2)}\n`,
    "utf8",
  );

  console.log(
    `Wrote coverage badge payload (${badgePayload.message}) to ${COVERAGE_BADGE_PATH}`,
  );
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
