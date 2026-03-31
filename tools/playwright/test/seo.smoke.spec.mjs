import { expect, test } from "@playwright/test";

function extractTitle(html) {
  const match = html.match(/<title>([\s\S]*?)<\/title>/i);
  return match ? match[1].trim() : null;
}

function extractCanonicalHref(html) {
  const match = html.match(/<link\b[^>]*rel=["']canonical["'][^>]*href=["']([^"']+)["'][^>]*>/i);
  return match ? match[1] : null;
}

test("robots.txt returns plain text without html @smoke", async ({ request }) => {
  const response = await request.get("/robots.txt");
  const body = await response.text();

  expect(response.ok()).toBe(true);
  expect(response.headers()["content-type"]).toContain("text/plain");
  expect(body).toContain("User-agent: *");
  expect(body).toContain("Sitemap:");
  expect(body).not.toMatch(/<html\b/i);
  expect(body).not.toMatch(/<!doctype html/i);
});

test("sitemap.xml returns xml @smoke", async ({ request }) => {
  const response = await request.get("/sitemap.xml");
  const body = await response.text();

  expect(response.ok()).toBe(true);
  expect(response.headers()["content-type"]).toMatch(/xml/i);
  expect(body).toMatch(/^<\?xml version="1\.0" encoding="UTF-8"\?>/i);
  expect(body).toContain("<urlset");
});

test("trade direct load returns the trade-specific title @smoke", async ({ request }) => {
  const response = await request.get("/trade");
  const body = await response.text();

  expect(response.ok()).toBe(true);
  expect(response.headers()["content-type"]).toContain("text/html");
  expect(extractTitle(body)).toBe("Trade perpetuals on Hyperliquid with an open-source client");
});

test("portfolio direct load returns the portfolio-specific title @smoke", async ({ request }) => {
  const response = await request.get("/portfolio");
  const body = await response.text();

  expect(response.ok()).toBe(true);
  expect(response.headers()["content-type"]).toContain("text/html");
  expect(extractTitle(body)).toBe("Portfolio analytics and tearsheets");
});

test("api direct load keeps canonical metadata lowercase @smoke", async ({ request }) => {
  const response = await request.get("/api");
  const body = await response.text();
  const canonicalHref = extractCanonicalHref(body);

  expect(response.ok()).toBe(true);
  expect(canonicalHref).toBeTruthy();
  expect(new URL(canonicalHref).pathname).toBe("/api");
  expect(canonicalHref).not.toContain("/API");
});
