import path from "node:path";

export const CANONICAL_ORIGIN_ENV_VAR = "HYPEROPEN_SITE_ORIGIN";
export const DEFAULT_CANONICAL_ORIGIN = "https://hyperopen.xyz";
export const SITE_METADATA_FILE_PATH = "site-metadata.json";
export const ROBOTS_FILE_PATH = "robots.txt";
export const SITEMAP_FILE_PATH = "sitemap.xml";
export const RELEASE_SEO_PLACEHOLDER = "<!-- HYPEROPEN_RELEASE_SEO_HEAD -->";
export const REQUIRED_ROOT_PUBLIC_PATHS = ["/sw.js"];

export const PUBLIC_ROUTE_METADATA = [
  {
    id: "home",
    path: "/",
    match: "exact",
    title: "Hyperopen | Open Source Trading Interface",
    description: "Hyperopen is the open source trading interface for Hyperliquid."
  },
  {
    id: "trade",
    path: "/trade",
    match: "prefix",
    title: "Trade | Hyperopen",
    description: "Trade spot and perp markets with charting, orderbook depth, and order controls."
  },
  {
    id: "portfolio",
    path: "/portfolio",
    match: "prefix",
    title: "Portfolio | Hyperopen",
    description: "Inspect portfolio performance, positions, and tearsheets in Hyperopen."
  },
  {
    id: "leaderboard",
    path: "/leaderboard",
    match: "exact",
    title: "Leaderboard | Hyperopen",
    description: "Track trader rankings, returns, and leaderboard performance in Hyperopen."
  },
  {
    id: "vaults",
    path: "/vaults",
    match: "prefix",
    title: "Vaults | Hyperopen",
    description: "Browse vault performance, depositor activity, and vault detail flows."
  },
  {
    id: "staking",
    path: "/staking",
    match: "prefix",
    title: "Staking | Hyperopen",
    description: "Review validator performance and manage staking activity in Hyperopen."
  },
  {
    id: "funding-comparison",
    path: "/funding-comparison",
    match: "exact",
    title: "Funding Comparison | Hyperopen",
    description: "Compare funding rates across exchanges from Hyperopen's funding dashboard."
  },
  {
    id: "api",
    path: "/api",
    match: "exact",
    title: "API Wallets | Hyperopen",
    description: "Create and manage API wallets for Hyperopen trading flows."
  }
];

function escapeHtmlJson(text) {
  return text
    .replace(/</g, "\\u003C")
    .replace(/>/g, "\\u003E")
    .replace(/&/g, "\\u0026");
}

function xmlEscape(text) {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

function parseHead(indexHtml) {
  const match = indexHtml.match(/<head\b[^>]*>([\s\S]*?)<\/head>/i);
  if (!match) {
    throw new Error("Expected app index.html to contain a <head> element.");
  }

  return match[1];
}

function looksLikeHashPrefixedPagesPreviewHost(hostname) {
  const host = String(hostname || "").toLowerCase();
  if (!host.endsWith(".pages.dev")) {
    return false;
  }

  const labels = host.split(".");
  if (labels.length < 4) {
    return false;
  }

  return /^[a-f0-9]{7,}$/i.test(labels[0]);
}

export function normalizePublicPath(publicPath) {
  const text = String(publicPath || "").trim();
  if (!text) {
    return "/";
  }

  const [withoutFragment] = text.split("#", 1);
  const [withoutQuery] = withoutFragment.split("?", 1);
  const prefixed = withoutQuery.startsWith("/") ? withoutQuery : `/${withoutQuery}`;
  const normalized = prefixed.replace(/\/+$/, "");
  return normalized || "/";
}

export function publicPathToRelativePath(publicPath) {
  const normalized = normalizePublicPath(publicPath);
  if (normalized === "/") {
    throw new Error("Expected a file-like public path, but received '/'.");
  }

  return normalized.replace(/^\/+/, "");
}

export function normalizeCanonicalOrigin(envValue = process.env[CANONICAL_ORIGIN_ENV_VAR]) {
  const rawValue = typeof envValue === "string" ? envValue.trim() : "";
  if (!rawValue) {
    return DEFAULT_CANONICAL_ORIGIN;
  }

  const candidate = /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(rawValue)
    ? rawValue
    : `https://${rawValue}`;

  let parsed;
  try {
    parsed = new URL(candidate);
  } catch (_error) {
    throw new Error(
      `Invalid ${CANONICAL_ORIGIN_ENV_VAR} value: ${rawValue}`
    );
  }

  if (!["http:", "https:"].includes(parsed.protocol)) {
    throw new Error(
      `Expected ${CANONICAL_ORIGIN_ENV_VAR} to use http or https, received: ${rawValue}`
    );
  }

  if (looksLikeHashPrefixedPagesPreviewHost(parsed.hostname)) {
    return DEFAULT_CANONICAL_ORIGIN;
  }

  return parsed.origin;
}

export function extractHeadRootAssetPublicPaths(indexHtml) {
  const headHtml = parseHead(indexHtml);
  const assetPaths = new Set();
  const attributePattern = /\b(?:href|content)=["']([^"']+)["']/gi;

  for (const match of headHtml.matchAll(attributePattern)) {
    const candidate = match[1];
    if (typeof candidate !== "string" || !candidate.startsWith("/")) {
      continue;
    }

    const normalized = normalizePublicPath(candidate);
    if (normalized.startsWith("/css/") || normalized.startsWith("/js/")) {
      continue;
    }

    if (!path.extname(normalized)) {
      continue;
    }

    assetPaths.add(normalized);
  }

  return [...assetPaths].sort();
}

export function collectReleaseRootAssetPublicPaths(indexHtml) {
  return [...new Set([
    ...REQUIRED_ROOT_PUBLIC_PATHS.map(normalizePublicPath),
    ...extractHeadRootAssetPublicPaths(indexHtml)
  ])].sort();
}

export function buildSiteMetadata({ canonicalOrigin, indexHtml }) {
  const origin = normalizeCanonicalOrigin(canonicalOrigin);

  return {
    siteName: "Hyperopen",
    origin,
    routes: PUBLIC_ROUTE_METADATA.map((route) => ({
      ...route,
      path: normalizePublicPath(route.path)
    })),
    rootAssetPaths: collectReleaseRootAssetPublicPaths(indexHtml)
  };
}

export function buildRobotsTxt(siteMetadata) {
  return [
    "User-agent: *",
    "Allow: /",
    `Sitemap: ${siteMetadata.origin}${normalizePublicPath(`/${SITEMAP_FILE_PATH}`)}`
  ].join("\n");
}

export function buildSitemapXml(siteMetadata) {
  const urls = siteMetadata.routes
    .map((route) => `  <url><loc>${xmlEscape(`${siteMetadata.origin}${route.path}`)}</loc></url>`)
    .join("\n");

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
    urls,
    "</urlset>"
  ].join("\n");
}

export function buildReleaseSeoHeadMarkup(siteMetadata) {
  const metadataJson = escapeHtmlJson(JSON.stringify(siteMetadata));

  return [
    `<link rel="canonical" href="${siteMetadata.origin}/" data-hyperopen-seo="canonical" />`,
    `<script id="hyperopen-site-metadata" type="application/json">${metadataJson}</script>`,
    "<script>",
    "  (function () {",
    "    const metadataElement = document.getElementById(\"hyperopen-site-metadata\");",
    "    if (!metadataElement) {",
    "      return;",
    "    }",
    "",
    "    let metadata;",
    "    try {",
    "      metadata = JSON.parse(metadataElement.textContent || \"{}\");",
    "    } catch (_error) {",
    "      return;",
    "    }",
    "",
    "    const origin = typeof metadata.origin === \"string\" ? metadata.origin : \"\";",
    "    const routes = Array.isArray(metadata.routes) ? metadata.routes : [];",
    "    if (!origin || routes.length === 0) {",
    "      return;",
    "    }",
    "",
    "    function normalizePath(pathname) {",
    "      const raw = typeof pathname === \"string\" ? pathname : \"/\";",
    "      const withoutHash = raw.split(\"#\", 1)[0] || \"/\";",
    "      const withoutQuery = withoutHash.split(\"?\", 1)[0] || \"/\";",
    "      const withSlash = withoutQuery.startsWith(\"/\") ? withoutQuery : `/${withoutQuery}`;",
    "      const normalized = withSlash.replace(/\\/+$/, \"\") || \"/\";",
    "      return normalized.toLowerCase();",
    "    }",
    "",
    "    function routeMatches(route, currentPath) {",
    "      const routePath = normalizePath(route.path || \"/\");",
    "      if ((route.match || \"exact\") === \"prefix\") {",
    "        return currentPath === routePath || currentPath.startsWith(`${routePath}/`);",
    "      }",
    "",
    "      return currentPath === routePath;",
    "    }",
    "",
    "    function ensureCanonicalLink() {",
    "      let link = document.querySelector(\"link[rel='canonical']\");",
    "      if (!link) {",
    "        link = document.createElement(\"link\");",
    "        link.setAttribute(\"rel\", \"canonical\");",
    "        document.head.appendChild(link);",
    "      }",
    "      return link;",
    "    }",
    "",
    "    function ensureDescriptionMeta() {",
    "      let meta = document.querySelector(\"meta[name='description']\");",
    "      if (!meta) {",
    "        meta = document.createElement(\"meta\");",
    "        meta.setAttribute(\"name\", \"description\");",
    "        document.head.appendChild(meta);",
    "      }",
    "      return meta;",
    "    }",
    "",
    "    const currentPath = normalizePath(window.location.pathname || \"/\");",
    "    const selectedRoute =",
    "      routes.find((route) => routeMatches(route, currentPath)) ||",
    "      routes.find((route) => normalizePath(route.path || \"/\") === \"/\") ||",
    "      routes[0];",
    "",
    "    if (!selectedRoute || typeof selectedRoute.path !== \"string\") {",
    "      return;",
    "    }",
    "",
    "    const canonicalLink = ensureCanonicalLink();",
    "    canonicalLink.setAttribute(\"href\", `${origin}${selectedRoute.path}`);",
    "",
    "    if (typeof selectedRoute.title === \"string\" && selectedRoute.title.trim()) {",
    "      document.title = selectedRoute.title;",
    "    }",
    "",
    "    if (typeof selectedRoute.description === \"string\" && selectedRoute.description.trim()) {",
    "      ensureDescriptionMeta().setAttribute(\"content\", selectedRoute.description);",
    "    }",
    "  })();",
    "</script>"
  ].join("\n");
}
