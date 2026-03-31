import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
  DEFAULT_CANONICAL_ORIGIN,
  RELEASE_SEO_PLACEHOLDER,
  buildSiteMetadata,
  extractHeadRootAssetPublicPaths,
  normalizeCanonicalOrigin,
} from "./site_metadata.mjs";
import {
  collectReleaseJavaScriptFiles,
  fingerprintFileName,
  generateReleaseArtifacts,
  hashContent,
  normalizeModuleUriToRelativeJsPath,
  rewriteAppIndexHtml,
} from "./generate_release_artifacts.mjs";

const SAMPLE_CANONICAL_ORIGIN = "https://app.hyperopen.example";
const SAMPLE_RELEASE_SEO_HEAD_MARKUP =
  '<link rel="canonical" href="https://app.hyperopen.example/" />';

function buildSampleIndexHtml() {
  return `<!DOCTYPE html>
<html>
  <head>
    <meta
      name="description"
      content="Hyperopen - Open Source Trading Interface"
    />
    <title>Hyperopen</title>
    ${RELEASE_SEO_PLACEHOLDER}
    <meta property="og:image" content="/og/hyperopen-share.png" />
    <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png" />
    <link rel="icon" type="image/png" sizes="16x16" href="/favicon-16x16.png" />
    <link rel="apple-touch-icon" href="/apple-touch-icon.png" />
    <link rel="shortcut icon" href="/favicon.ico" />
    <link rel="stylesheet" href="/css/main.css" />
  </head>
  <body>
    <script>
      (function () {
        const manifestUrl = "/js/manifest.json";
        const defaultMainScriptUrl = "/js/main.js";

        fetch(manifestUrl, { cache: "no-cache" })
          .then((response) => (response.ok ? response.json() : null))
          .then(() => {
            document.body.dataset.loaded = defaultMainScriptUrl;
          });
      })();
    </script>
  </body>
</html>`;
}

async function writeFixtureFile(root, relativePath, content, missingRelativePaths) {
  if (missingRelativePaths.has(relativePath)) {
    return;
  }

  const absolutePath = path.join(root, relativePath);
  await fs.mkdir(path.dirname(absolutePath), { recursive: true });
  await fs.writeFile(absolutePath, content);
}

async function writeReleaseFixture(sourceRoot, { missingRelativePaths = [] } = {}) {
  const missing = new Set(missingRelativePaths);

  await fs.mkdir(sourceRoot, { recursive: true });
  await writeFixtureFile(sourceRoot, "index.html", buildSampleIndexHtml(), missing);
  await writeFixtureFile(sourceRoot, path.join("css", "main.css"), "body { color: white; }\n", missing);
  await writeFixtureFile(
    sourceRoot,
    path.join("js", "manifest.json"),
    JSON.stringify([
      { "module-id": "main", "output-name": "main.HASH.js" },
      { "module-id": "trade_chart", "output-name": "trade_chart.CHUNK.js" },
    ]),
    missing
  );
  await writeFixtureFile(
    sourceRoot,
    path.join("js", "module-loader.json"),
    JSON.stringify({
      "module-uris": {
        main: [],
        trade_chart: ["/js/trade_chart.CHUNK.js"],
      },
    }),
    missing
  );
  await writeFixtureFile(sourceRoot, path.join("js", "main.HASH.js"), "console.log('main');\n", missing);
  await writeFixtureFile(
    sourceRoot,
    path.join("js", "trade_chart.CHUNK.js"),
    "console.log('chunk');\n",
    missing
  );
  await writeFixtureFile(sourceRoot, path.join("js", "portfolio_worker.js"), "worker();\n", missing);
  await writeFixtureFile(sourceRoot, path.join("js", "vault_detail_worker.js"), "worker();\n", missing);
  await writeFixtureFile(sourceRoot, path.join("js", "main.js"), "stale();\n", missing);
  await writeFixtureFile(sourceRoot, path.join("js", "portfolio.js"), "stale();\n", missing);
  await writeFixtureFile(sourceRoot, path.join("js", "cljs-runtime", "stale.map"), "stale", missing);
  await writeFixtureFile(sourceRoot, path.join("fonts", "InterVariable.woff2"), "font", missing);
  await writeFixtureFile(
    sourceRoot,
    "sw.js",
    "self.addEventListener('fetch', () => {});",
    missing
  );
  await writeFixtureFile(sourceRoot, "favicon.svg", "<svg></svg>\n", missing);
  await writeFixtureFile(sourceRoot, "favicon-16x16.png", "png16", missing);
  await writeFixtureFile(sourceRoot, "favicon-32x32.png", "png32", missing);
  await writeFixtureFile(sourceRoot, "apple-touch-icon.png", "apple", missing);
  await writeFixtureFile(sourceRoot, "favicon.ico", "ico", missing);
  await writeFixtureFile(sourceRoot, path.join("og", "hyperopen-share.png"), "og", missing);
  await writeFixtureFile(sourceRoot, "ui-workbench.html", "<html></html>", missing);
}

test("rewriteAppIndexHtml replaces the default stylesheet href and injects release SEO markup", () => {
  const rewritten = rewriteAppIndexHtml(buildSampleIndexHtml(), {
    cssHref: "/css/main.ABC123.css",
    mainScriptHref: "/js/main.HASH.js",
    releaseSeoHeadMarkup: SAMPLE_RELEASE_SEO_HEAD_MARKUP,
  });

  assert.match(rewritten, /href="\/css\/main\.ABC123\.css"/);
  assert.doesNotMatch(rewritten, /href="\/css\/main\.css"/);
  assert.match(rewritten, /<script defer src="\/js\/main\.HASH\.js"><\/script>/);
  assert.doesNotMatch(rewritten, /manifestUrl/);
  assert.doesNotMatch(rewritten, new RegExp(RELEASE_SEO_PLACEHOLDER));
  assert.match(rewritten, /rel="canonical"/);
});

test("fingerprint helpers preserve the base filename and use uppercase hashes", () => {
  const fingerprint = hashContent(Buffer.from("abc"));
  assert.equal(fingerprint, fingerprint.toUpperCase());
  assert.equal(fingerprintFileName("main.css", fingerprint), `main.${fingerprint}.css`);
});

test("normalizeCanonicalOrigin prefers a stable default over hash-prefixed preview urls", () => {
  assert.equal(normalizeCanonicalOrigin(undefined), DEFAULT_CANONICAL_ORIGIN);
  assert.equal(
    normalizeCanonicalOrigin("https://2f1a3b4c.hyperopen.pages.dev/path?x=1#frag"),
    DEFAULT_CANONICAL_ORIGIN
  );
  assert.equal(
    normalizeCanonicalOrigin("https://app.hyperopen.example/trade"),
    "https://app.hyperopen.example"
  );
});

test("extractHeadRootAssetPublicPaths captures favicon and social assets from the head", () => {
  assert.deepEqual(extractHeadRootAssetPublicPaths(buildSampleIndexHtml()), [
    "/apple-touch-icon.png",
    "/favicon-16x16.png",
    "/favicon-32x32.png",
    "/favicon.ico",
    "/favicon.svg",
    "/og/hyperopen-share.png",
  ]);
});

test("collectReleaseJavaScriptFiles keeps only explicit release assets", () => {
  const files = collectReleaseJavaScriptFiles({
    manifest: [
      { "module-id": "main", "output-name": "main.HASH.js" },
      { "module-id": "trade_chart", "output-name": "trade_chart.HASH.js" },
    ],
    moduleLoader: {
      "module-uris": {
        main: [],
        trade_chart: ["/js/trade_chart.HASH.js"],
      },
    },
  });

  assert.deepEqual(files, [
    "main.HASH.js",
    "module-loader.json",
    "portfolio_worker.js",
    "trade_chart.HASH.js",
    "vault_detail_worker.js",
  ]);
});

test("normalizeModuleUriToRelativeJsPath preserves nested js paths and strips query strings", () => {
  assert.equal(
    normalizeModuleUriToRelativeJsPath("/js/chunks/trade_chart.HASH.js?v=1"),
    "chunks/trade_chart.HASH.js"
  );
  assert.equal(normalizeModuleUriToRelativeJsPath("/assets/trade_chart.HASH.js"), null);
});

test("buildSiteMetadata keeps /api lowercase in the generated metadata", () => {
  const metadata = buildSiteMetadata({
    canonicalOrigin: SAMPLE_CANONICAL_ORIGIN,
    indexHtml: buildSampleIndexHtml(),
  });

  assert.equal(metadata.origin, SAMPLE_CANONICAL_ORIGIN);
  assert.equal(metadata.routes.find((route) => route.id === "api")?.path, "/api");
  assert.ok(!metadata.routes.some((route) => route.path === "/API"));
});

test("generateReleaseArtifacts assembles a deterministic release root with SEO sidecars", async () => {
  const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "hyperopen-release-assets-"));
  const sourceRoot = path.join(tempRoot, "source");
  const outputRoot = path.join(tempRoot, "output");

  await writeReleaseFixture(sourceRoot);

  const result = await generateReleaseArtifacts({
    sourceRoot,
    outputRoot,
    canonicalOrigin: SAMPLE_CANONICAL_ORIGIN,
  });

  const generatedIndex = await fs.readFile(path.join(outputRoot, "index.html"), "utf8");
  const generatedCss = await fs.readFile(path.join(outputRoot, "css", result.cssFileName), "utf8");
  const copiedMain = await fs.readFile(path.join(outputRoot, "js", "main.HASH.js"), "utf8");
  const copiedFont = await fs.readFile(path.join(outputRoot, "fonts", "InterVariable.woff2"), "utf8");
  const robotsTxt = await fs.readFile(path.join(outputRoot, "robots.txt"), "utf8");
  const sitemapXml = await fs.readFile(path.join(outputRoot, "sitemap.xml"), "utf8");
  const siteMetadata = JSON.parse(
    await fs.readFile(path.join(outputRoot, "site-metadata.json"), "utf8")
  );

  assert.match(generatedIndex, new RegExp(`href="${result.cssHref.replace(".", "\\.")}"`));
  assert.match(generatedIndex, /<script defer src="\/js\/main\.HASH\.js"><\/script>/);
  assert.match(generatedIndex, /id="hyperopen-site-metadata"/);
  assert.match(generatedIndex, new RegExp(`href="${SAMPLE_CANONICAL_ORIGIN.replace(/\./g, "\\.")}\\/"`));
  assert.doesNotMatch(generatedIndex, /manifestUrl/);
  assert.equal(generatedCss, "body { color: white; }\n");
  assert.equal(copiedMain, "console.log('main');\n");
  assert.equal(copiedFont, "font");

  assert.equal(robotsTxt, `User-agent: *\nAllow: /\nSitemap: ${SAMPLE_CANONICAL_ORIGIN}/sitemap.xml\n`);
  assert.match(robotsTxt, /^User-agent: \*/);
  assert.match(sitemapXml, /^<\?xml version="1\.0" encoding="UTF-8"\?>/);
  for (const route of ["/", "/trade", "/portfolio", "/leaderboard", "/vaults", "/staking", "/funding-comparison", "/api"]) {
    assert.match(
      sitemapXml,
      new RegExp(`<loc>${SAMPLE_CANONICAL_ORIGIN.replace(/\./g, "\\.")}${route === "/" ? "/" : route.replace(/\//g, "\\/")}</loc>`)
    );
  }

  assert.equal(siteMetadata.origin, SAMPLE_CANONICAL_ORIGIN);
  assert.equal(siteMetadata.routes.find((route) => route.id === "api")?.path, "/api");
  assert.deepEqual(result.copiedRootAssetPaths, [
    "apple-touch-icon.png",
    "favicon-16x16.png",
    "favicon-32x32.png",
    "favicon.ico",
    "favicon.svg",
    "og/hyperopen-share.png",
    "sw.js",
  ]);
  assert.deepEqual(siteMetadata.rootAssetPaths, [
    "/apple-touch-icon.png",
    "/favicon-16x16.png",
    "/favicon-32x32.png",
    "/favicon.ico",
    "/favicon.svg",
    "/og/hyperopen-share.png",
    "/sw.js",
  ]);

  for (const relativePath of result.copiedRootAssetPaths) {
    await fs.access(path.join(outputRoot, relativePath));
  }

  await assert.rejects(fs.access(path.join(outputRoot, "css", "main.css")));
  await assert.rejects(fs.access(path.join(outputRoot, "js", "manifest.json")));
  await assert.rejects(fs.access(path.join(outputRoot, "js", "main.js")));
  await assert.rejects(fs.access(path.join(outputRoot, "js", "portfolio.js")));
  await assert.rejects(fs.access(path.join(outputRoot, "js", "cljs-runtime", "stale.map")));
  await assert.rejects(fs.access(path.join(outputRoot, "ui-workbench.html")));
});

test("generateReleaseArtifacts fails closed when a required release asset is missing", async () => {
  const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "hyperopen-release-assets-missing-"));
  const sourceRoot = path.join(tempRoot, "source");
  const outputRoot = path.join(tempRoot, "output");

  await writeReleaseFixture(sourceRoot, {
    missingRelativePaths: [path.join("js", "portfolio_worker.js")],
  });

  await assert.rejects(
    generateReleaseArtifacts({ sourceRoot, outputRoot }),
    /Expected release asset to exist: js\/portfolio_worker\.js/
  );
});

test("generateReleaseArtifacts fails closed when a declared root SEO asset is missing", async () => {
  const tempRoot = await fs.mkdtemp(
    path.join(os.tmpdir(), "hyperopen-release-assets-missing-root-")
  );
  const sourceRoot = path.join(tempRoot, "source");
  const outputRoot = path.join(tempRoot, "output");

  await writeReleaseFixture(sourceRoot, {
    missingRelativePaths: ["favicon.svg"],
  });

  await assert.rejects(
    generateReleaseArtifacts({ sourceRoot, outputRoot }),
    /Expected release root asset to exist: favicon\.svg/
  );
});

test("rewriteAppIndexHtml works against the real tracked app entry", async () => {
  const projectRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..", "..");
  const realIndexPath = path.join(projectRoot, "resources", "public", "index.html");
  const realIndexHtml = await fs.readFile(realIndexPath, "utf8");

  const rewritten = rewriteAppIndexHtml(realIndexHtml, {
    cssHref: "/css/main.TEST.css",
    mainScriptHref: "/js/main.TEST.js",
    releaseSeoHeadMarkup: SAMPLE_RELEASE_SEO_HEAD_MARKUP,
  });

  assert.match(rewritten, /href="\/css\/main\.TEST\.css"/);
  assert.match(rewritten, /<script defer src="\/js\/main\.TEST\.js"><\/script>/);
  assert.match(rewritten, /rel="canonical"/);
  assert.doesNotMatch(rewritten, /manifestUrl/);
  assert.doesNotMatch(rewritten, new RegExp(RELEASE_SEO_PLACEHOLDER));
});
