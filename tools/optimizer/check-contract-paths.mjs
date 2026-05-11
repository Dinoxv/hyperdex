import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

const roots = [
  "src/hyperopen/portfolio/optimizer",
  "src/hyperopen/runtime/effect_adapters",
];

const allowedFiles = new Set([
  "src/hyperopen/portfolio/optimizer/contracts.cljs",
]);

const patterns = [
  /\[:portfolio\s+:optimizer\b/,
  /\[:portfolio-ui\s+:optimizer\b/,
];

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return walk(full);
    return full.endsWith(".cljs") ? [full] : [];
  });
}

export function findViolations() {
  const files = roots.flatMap((root) => walk(path.join(repoRoot, root)));

  return files.flatMap((file) => {
    const rel = path.relative(repoRoot, file);
    if (allowedFiles.has(rel)) return [];

    const text = fs.readFileSync(file, "utf8");
    return text.split("\n").flatMap((line, idx) => {
      if (patterns.some((pattern) => pattern.test(line))) {
        return [`${rel}:${idx + 1}: hardcoded optimizer state path`];
      }
      return [];
    });
  });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const violations = findViolations();
  if (violations.length) {
    console.error(violations.join("\n"));
    process.exit(1);
  }

  console.log("Optimizer contract path check passed.");
}
