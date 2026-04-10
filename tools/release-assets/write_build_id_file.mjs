import fs from "node:fs/promises";
import path from "node:path";

import { resolveReleaseBuildId } from "./generate_release_artifacts.mjs";

const DEFAULT_OUTPUT_PATH = path.resolve("resources/public/build-id.txt");

export async function writeBuildIdFile({
  outputPath = DEFAULT_OUTPUT_PATH,
  buildId = resolveReleaseBuildId(),
} = {}) {
  const normalizedBuildId =
    typeof buildId === "string" && buildId.trim().length > 0 ? buildId.trim() : "";
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.writeFile(outputPath, `${normalizedBuildId}\n`, "utf8");
  return { outputPath, buildId: normalizedBuildId };
}

const invokedFromCli =
  process.argv[1] && path.resolve(process.argv[1]) === path.resolve(new URL(import.meta.url).pathname);

if (invokedFromCli) {
  await writeBuildIdFile();
}
