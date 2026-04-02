import { spawn } from "node:child_process";

const gates = [
  { label: "npm run check", cmd: "npm", args: ["run", "check"] },
  { label: "npm test", cmd: "npm", args: ["test"] },
  { label: "npm run test:websocket", cmd: "npm", args: ["run", "test:websocket"] }
];

function makeMetrics() {
  return {
    tests: 0,
    assertions: 0,
    nodeTests: 0
  };
}

function formatDuration(milliseconds) {
  const totalSeconds = Math.round(milliseconds / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  if (minutes === 0) {
    return `${seconds}s`;
  }

  return `${minutes}m ${seconds}s`;
}

function collectMetrics(metrics, text) {
  const cljsMatches = text.matchAll(/Ran\s+(\d+)\s+tests\s+containing\s+(\d+)\s+assertions\./g);
  for (const match of cljsMatches) {
    metrics.tests += Number(match[1]);
    metrics.assertions += Number(match[2]);
  }

  const nodeMatches = text.matchAll(/ℹ tests\s+(\d+)/g);
  for (const match of nodeMatches) {
    const count = Number(match[1]);
    metrics.tests += count;
    metrics.nodeTests += count;
  }
}

function runGate({ label, cmd, args }) {
  return new Promise((resolve) => {
    const startedAt = Date.now();
    const child = spawn(cmd, args, {
      stdio: ["inherit", "pipe", "pipe"],
      shell: process.platform === "win32"
    });
    let output = "";

    const handleChunk = (chunk, writer) => {
      const text = chunk.toString();
      writer.write(text);
      output += text;
    };

    child.stdout.on("data", (chunk) => {
      handleChunk(chunk, process.stdout);
    });

    child.stderr.on("data", (chunk) => {
      handleChunk(chunk, process.stderr);
    });

    child.on("error", (error) => {
      const metrics = makeMetrics();
      collectMetrics(metrics, output);
      resolve({
        label,
        ok: false,
        code: null,
        signal: null,
        error,
        metrics,
        durationMs: Date.now() - startedAt
      });
    });

    child.on("exit", (code, signal) => {
      const metrics = makeMetrics();
      collectMetrics(metrics, output);
      resolve({
        label,
        ok: code === 0,
        code,
        signal,
        error: null,
        metrics,
        durationMs: Date.now() - startedAt
      });
    });
  });
}

function renderStatus(result) {
  if (result.ok) return "PASS";
  if (result.signal) return `FAIL (signal ${result.signal})`;
  if (result.error) return `FAIL (${result.error.message})`;
  return `FAIL (exit ${result.code})`;
}

const results = [];

for (const gate of gates) {
  const result = await runGate(gate);
  results.push(result);

  if (!result.ok) {
    break;
  }
}

console.log("");
console.log("Gate summary:");

for (const gate of gates) {
  const result = results.find((entry) => entry.label === gate.label);
  const status = result ? renderStatus(result) : "SKIPPED";
  const counts = result
    ? ` tests=${String(result.metrics.tests).padStart(5)} assertions=${String(result.metrics.assertions).padStart(5)} time=${formatDuration(result.durationMs).padStart(6)}`
    : "";
  console.log(`  ${gate.label.padEnd(24)} ${status}${counts}`);
}

const allPassed = results.length === gates.length && results.every((result) => result.ok);
const totals = results.reduce((acc, result) => {
  acc.tests += result.metrics.tests;
  acc.assertions += result.metrics.assertions;
  acc.nodeTests += result.metrics.nodeTests;
  return acc;
}, makeMetrics());

console.log("");
console.log("Totals:");
console.log(`  tests run:               ${totals.tests}`);
console.log(`  assertions run:          ${totals.assertions}`);
console.log(`  total suite time:        ${formatDuration(results.reduce((sum, result) => sum + result.durationMs, 0))}`);
if (totals.nodeTests > 0) {
  console.log(`  node tests included:     ${totals.nodeTests}`);
}
console.log(`Overall: ${allPassed ? "PASS" : "FAIL"}`);

process.exit(allPassed ? 0 : 1);
