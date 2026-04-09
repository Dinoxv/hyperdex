const DEFAULT_BASE_URL = "http://127.0.0.1:8080";
const READY_TIMEOUT_MS = 120_000;
const POLL_MS = 500;
const DEV_ASSET_PATHS = ["/js/manifest.json", "/js/main.js"];

function sleep(ms) {
  return new Promise(resolve => {
    setTimeout(resolve, ms);
  });
}

async function assetReady(baseUrl, assetPath) {
  try {
    const response = await fetch(new URL(assetPath, baseUrl), {
      cache: "no-store",
      redirect: "follow"
    });
    return response.ok;
  } catch (_error) {
    return false;
  }
}

export default async function globalSetup() {
  const baseUrl = process.env.PLAYWRIGHT_BASE_URL || DEFAULT_BASE_URL;
  const deadline = Date.now() + READY_TIMEOUT_MS;

  while (Date.now() < deadline) {
    for (const assetPath of DEV_ASSET_PATHS) {
      if (await assetReady(baseUrl, assetPath)) {
        return;
      }
    }
    await sleep(POLL_MS);
  }

  throw new Error(
    `Timed out waiting for the interactive Playwright app bundle at ${baseUrl}. ` +
      `Expected one of: ${DEV_ASSET_PATHS.join(", ")}`
  );
}
