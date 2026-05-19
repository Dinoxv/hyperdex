import { expect, test } from "@playwright/test";
import { dispatch, visitRoute, waitForIdle } from "../support/hyperopen.mjs";
import {
  keyword as optimizerKeyword,
  readOptimizerState,
  seedOptimizerMarkets
} from "../support/optimizer_state.mjs";

async function seedRetainedDraftScenario(page) {
  await page.evaluate(() => {
    const c = globalThis.cljs.core;
    const kw = (name) => c.keyword(name);
    const map = (entries) => c.PersistentArrayMap.fromArray(entries, true);
    const vector = (items) => c.PersistentVector.fromArray(items, true);
    const path = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => kw(segment)), true);

    const btcInstrument = map([
      kw("instrument-id"), "perp:BTC",
      kw("market-type"), kw("perp"),
      kw("coin"), "BTC",
      kw("symbol"), "BTC-USDC",
      kw("name"), "Bitcoin"
    ]);
    const draft = map([
      kw("id"), "draft-current",
      kw("name"), "Retained Draft Smoke",
      kw("universe"), vector([btcInstrument]),
      kw("objective"), map([kw("kind"), kw("max-sharpe")]),
      kw("return-model"), map([kw("kind"), kw("historical-mean")]),
      kw("risk-model"), map([kw("kind"), kw("sample-covariance")]),
      kw("constraints"), map([
        kw("long-only?"), true,
        kw("max-asset-weight"), 1,
        kw("gross-max"), 1
      ]),
      kw("metadata"), map([kw("dirty?"), false])
    ]);
    const result = map([
      kw("status"), kw("solved"),
      kw("scenario-id"), "draft",
      kw("instrument-ids"), vector(["perp:BTC"]),
      kw("target-weights"), vector([1]),
      kw("current-weights"), vector([0.25]),
      kw("target-weights-by-instrument"), map(["perp:BTC", 1]),
      kw("current-weights-by-instrument"), map(["perp:BTC", 0.25]),
      kw("labels-by-instrument"), map(["perp:BTC", "BTC"]),
      kw("expected-return"), 0.12,
      kw("volatility"), 0.2,
      kw("return-model"), kw("historical-mean"),
      kw("risk-model"), kw("sample-covariance"),
      kw("as-of-ms"), 1777046100000,
      kw("frontier"), vector([
        map([
          kw("id"), 0,
          kw("expected-return"), 0.12,
          kw("volatility"), 0.2,
          kw("sharpe"), 0.6
        ])
      ]),
      kw("frontier-overlays"), map([]),
      kw("performance"), map([
        kw("in-sample-sharpe"), 0.58,
        kw("shrunk-sharpe"), 0.6
      ]),
      kw("diagnostics"), map([
        kw("gross-exposure"), 1,
        kw("net-exposure"), 1,
        kw("effective-n"), 1,
        kw("turnover"), 0.75,
        kw("binding-constraints"), vector([]),
        kw("covariance-conditioning"), map([kw("status"), kw("ok")])
      ]),
      kw("rebalance-preview"), map([
        kw("status"), kw("ready"),
        kw("capital-usd"), 10000,
        kw("summary"), map([
          kw("ready-count"), 1,
          kw("blocked-count"), 0,
          kw("gross-trade-notional-usd"), 7500,
          kw("estimated-fees-usd"), 2.5,
          kw("estimated-slippage-usd"), 8,
          kw("margin"), map([kw("after-utilization"), 0.2])
        ]),
        kw("rows"), vector([])
      ])
    ]);
    const requestSignature = map([kw("seed"), "retained-draft-smoke"]);
    const lastSuccessfulRun = map([
      kw("request-signature"), requestSignature,
      kw("computed-at-ms"), 1777046100000,
      kw("result"), result
    ]);

    const store = globalThis.hyperopen.system.store;
    let state = c.deref(store);
    state = c.assoc_in(state, path("portfolio", "optimizer", "draft"), draft);
    state = c.assoc_in(
      state,
      path("portfolio", "optimizer", "active-scenario"),
      map([kw("loaded-id"), null, kw("status"), kw("computed")])
    );
    state = c.assoc_in(
      state,
      path("portfolio", "optimizer", "last-successful-run"),
      lastSuccessfulRun
    );
    state = c.assoc_in(
      state,
      path("portfolio", "optimizer", "run-state"),
      map([kw("status"), kw("succeeded"), kw("request-signature"), requestSignature])
    );
    state = c.assoc_in(
      state,
      path("portfolio", "optimizer", "scenario-load-state"),
      map([kw("status"), kw("loading"), kw("scenario-id"), "draft"])
    );
    state = c.assoc_in(
      state,
      path("portfolio-ui", "optimizer", "results-tab"),
      kw("recommendation")
    );
    c.reset_BANG_(store, state);
  });
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
}

test("portfolio optimizer setup and retained draft detail routes render through view models @smoke @regression", async ({ page }) => {
  test.setTimeout(90_000);

  await page.setViewportSize({ width: 1280, height: 900 });
  await visitRoute(page, "/portfolio/optimize/new", {
    routeModuleTimeoutMs: 30_000,
    idleOptions: { quietMs: 400, timeoutMs: 8_000, pollMs: 50 }
  });

  const setup = page.locator("[data-role='portfolio-optimizer-setup-route-surface']");
  await expect(setup).toBeVisible({ timeout: 30_000 });
  await expect(page.locator("[data-role='portfolio-optimizer-universe-search-input']"))
    .toBeVisible();
  await expect(page.locator("[data-role='portfolio-optimizer-run-draft']"))
    .toBeDisabled();

  await seedRetainedDraftScenario(page);
  await dispatch(page, [
    ":actions/navigate",
    "/portfolio/optimize/draft",
    { "replace?": true }
  ]);
  await waitForIdle(page, { quietMs: 250, timeoutMs: 8_000, pollMs: 50 });

  const detail = page.locator("[data-role='portfolio-optimizer-scenario-detail-surface']");
  await expect(page).toHaveURL(/\/portfolio\/optimize\/draft/);
  await expect(detail).toBeVisible();
  await expect(detail).toHaveAttribute("data-scenario-id", "draft");
  await expect(page.locator("[data-role='portfolio-optimizer-scenario-header']"))
    .toContainText("Retained Draft Smoke");
  await expect(page.locator("[data-role='portfolio-optimizer-scenario-loading-state']"))
    .toHaveCount(0);
  await expect(page.locator("[data-role='portfolio-optimizer-scenario-tabs']"))
    .toBeVisible();
  await expect(page.locator("[data-role='portfolio-optimizer-scenario-kpi-strip']"))
    .toContainText("Expected Return");
});

test("portfolio optimizer draft allocation add asset selector updates draft and starts recompute @smoke @regression", async ({ page }) => {
  test.setTimeout(90_000);

  await page.setViewportSize({ width: 1280, height: 900 });
  await visitRoute(page, "/portfolio/optimize/new", {
    routeModuleTimeoutMs: 30_000,
    idleOptions: { quietMs: 400, timeoutMs: 8_000, pollMs: 50 }
  });

  await seedRetainedDraftScenario(page);
  await seedOptimizerMarkets(page, [
    {
      key: "perp:BTC",
      "market-type": optimizerKeyword("perp"),
      coin: "BTC",
      symbol: "BTC-USDC",
      base: "BTC",
      quote: "USDC",
      volume24h: 96000000
    },
    {
      key: "perp:ETH",
      "market-type": optimizerKeyword("perp"),
      coin: "ETH",
      symbol: "ETH-USDC",
      base: "ETH",
      quote: "USDC",
      volume24h: 84000000
    }
  ]);
  await dispatch(page, [
    ":actions/navigate",
    "/portfolio/optimize/draft",
    { "replace?": true }
  ]);
  await waitForIdle(page, { quietMs: 250, timeoutMs: 8_000, pollMs: 50 });

  await expect(page.locator("[data-role='portfolio-optimizer-results-surface']"))
    .toBeVisible();

  const addAsset = page.locator("[data-role='portfolio-optimizer-draft-add-asset']");
  const popover = page.locator("[data-role='portfolio-optimizer-draft-add-asset-popover']");
  const searchInput = page.locator("[data-role='portfolio-optimizer-draft-add-asset-search-input']");
  const ethRow = page.locator("[data-role='portfolio-optimizer-draft-add-asset-candidate-row-perp:ETH']");

  await addAsset.click();
  await expect(popover).toBeVisible();
  await expect(searchInput).toBeVisible();
  await searchInput.fill("eth");
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
  await expect(ethRow).toBeVisible();
  await ethRow.click();

  await expect(popover).toHaveCount(0);
  await expect(page.locator("[data-role='portfolio-optimizer-results-surface']"))
    .toBeVisible();
  await expect(page.locator("[data-role='portfolio-optimizer-recommendation-stale-blocked']"))
    .toHaveCount(0);
  await expect(page.locator("[data-role='portfolio-optimizer-scenario-stale-banner']"))
    .toHaveCount(0);
  await expect(page.locator("[data-role='portfolio-optimizer-recompute-banner']"))
    .toBeVisible();
  await expect(page.locator("[data-role='portfolio-optimizer-progress-panel']"))
    .toBeVisible();
  await expect.poll(async () => {
    const universe = await readOptimizerState(page, ["portfolio", "optimizer", "draft", "universe"]);
    return universe
      .map((row) => row["instrument-id"])
      .some((instrumentId) => instrumentId === "perp:ETH" || instrumentId === "hl:perp:ETH");
  }).toBe(true);
  await expect.poll(async () => {
    const progress = await readOptimizerState(page, ["portfolio", "optimizer", "optimization-progress"]);
    return progress?.status;
  }).not.toBe("idle");
});

test("portfolio optimizer draft add asset selector stays contained and focused across viewports @smoke @regression", async ({ page }) => {
  test.setTimeout(180_000);

  for (const viewport of [
    { width: 375, height: 812 },
    { width: 768, height: 900 },
    { width: 1280, height: 900 },
    { width: 1440, height: 900 }
  ]) {
    await test.step(`viewport ${viewport.width}`, async () => {
      await page.setViewportSize(viewport);
      await visitRoute(page, "/portfolio/optimize/new", {
        routeModuleTimeoutMs: 30_000,
        idleOptions: { quietMs: 400, timeoutMs: 8_000, pollMs: 50 }
      });

      await seedRetainedDraftScenario(page);
      await seedOptimizerMarkets(page, [
        {
          key: "perp:BTC",
          "market-type": optimizerKeyword("perp"),
          coin: "BTC",
          symbol: "BTC-USDC",
          base: "BTC",
          quote: "USDC",
          volume24h: 96000000
        },
        {
          key: "perp:ETH",
          "market-type": optimizerKeyword("perp"),
          coin: "ETH",
          symbol: "ETH-USDC",
          base: "ETH",
          quote: "USDC",
          volume24h: 84000000
        }
      ]);
      await dispatch(page, [
        ":actions/navigate",
        "/portfolio/optimize/draft",
        { "replace?": true }
      ]);
      await waitForIdle(page, { quietMs: 250, timeoutMs: 8_000, pollMs: 50 });

      const addAsset = page.locator("[data-role='portfolio-optimizer-draft-add-asset']");
      const popover = page.locator("[data-role='portfolio-optimizer-draft-add-asset-popover']");
      const searchInput = page.locator("[data-role='portfolio-optimizer-draft-add-asset-search-input']");
      const ethRow = page.locator("[data-role='portfolio-optimizer-draft-add-asset-candidate-row-perp:ETH']");

      await addAsset.scrollIntoViewIfNeeded();
      await addAsset.click();
      await expect(popover).toBeVisible();
      await expect(searchInput).toHaveAttribute("type", "text");
      await expect(searchInput).toBeFocused();
      await page.keyboard.type("eth");
      await expect(searchInput).toHaveValue("eth");
      await expect(ethRow).toBeVisible();

      const box = await popover.boundingBox();
      expect(box).not.toBeNull();
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(viewport.width);
      expect(box.y).toBeGreaterThanOrEqual(0);
      expect(box.y + box.height).toBeLessThanOrEqual(viewport.height);
    });
  }
});
