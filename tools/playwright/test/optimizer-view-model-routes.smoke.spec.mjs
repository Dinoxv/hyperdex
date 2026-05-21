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

async function seedTwoAssetDraftScenario(page) {
  await seedRetainedDraftScenario(page);
  await page.evaluate(() => {
    const c = globalThis.cljs.core;
    const kw = (name) => c.keyword(name);
    const map = (entries) => c.PersistentArrayMap.fromArray(entries, true);
    const vector = (items) => c.PersistentVector.fromArray(items, true);
    const path = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => kw(segment)), true);

    const btcInstrument = map([
      kw("instrument-id"), "hl:perp:BTC",
      kw("market-type"), kw("perp"),
      kw("coin"), "BTC",
      kw("symbol"), "BTC-USDC",
      kw("name"), "Bitcoin"
    ]);
    const ethInstrument = map([
      kw("instrument-id"), "hl:perp:ETH",
      kw("market-type"), kw("perp"),
      kw("coin"), "ETH",
      kw("symbol"), "ETH-USDC",
      kw("name"), "Ethereum"
    ]);
    const draft = map([
      kw("id"), "draft-current",
      kw("name"), "Two Asset Draft Smoke",
      kw("universe"), vector([btcInstrument, ethInstrument]),
      kw("objective"), map([kw("kind"), kw("max-sharpe")]),
      kw("return-model"), map([kw("kind"), kw("historical-mean")]),
      kw("risk-model"), map([kw("kind"), kw("sample-covariance")]),
      kw("constraints"), map([
        kw("long-only?"), true,
        kw("max-asset-weight"), 1,
        kw("gross-max"), 1,
        kw("blocklist"), vector([])
      ]),
      kw("execution-assumptions"), map([
        kw("manual-capital-usdc"), 10000,
        kw("fallback-slippage-bps"), 20,
        kw("prices-by-id"), map(["perp:BTC", 100000, "perp:ETH", 5000, "perp:HYPE", 54]),
        kw("fee-bps-by-id"), map(["perp:BTC", 4, "perp:ETH", 4, "perp:HYPE", 4])
      ]),
      kw("metadata"), map([kw("dirty?"), false])
    ]);
    const result = map([
      kw("status"), kw("solved"),
      kw("scenario-id"), "draft",
      kw("instrument-ids"), vector(["perp:BTC", "perp:ETH"]),
      kw("target-weights"), vector([0.5, 0.5]),
      kw("current-weights"), vector([0.25, 0.15]),
      kw("target-weights-by-instrument"), map(["perp:BTC", 0.5, "perp:ETH", 0.5]),
      kw("current-weights-by-instrument"), map(["perp:BTC", 0.25, "perp:ETH", 0.15]),
      kw("labels-by-instrument"), map(["perp:BTC", "BTC", "perp:ETH", "ETH"]),
      kw("expected-returns-by-instrument"), map([
        "hl:perp:BTC", 0.195,
        "hl:perp:ETH", 0.165
      ]),
      kw("expected-return"), 0.12,
      kw("volatility"), 0.2,
      kw("return-model"), kw("historical-mean"),
      kw("risk-model"), kw("sample-covariance"),
      kw("as-of-ms"), 1777046100000,
      kw("frontier"), vector([
        map([kw("id"), 0, kw("expected-return"), 0.1, kw("volatility"), 0.18, kw("sharpe"), 0.55]),
        map([kw("id"), 1, kw("expected-return"), 0.12, kw("volatility"), 0.2, kw("sharpe"), 0.6])
      ]),
      kw("frontier-overlays"), map([]),
      kw("performance"), map([
        kw("in-sample-sharpe"), 0.58,
        kw("shrunk-sharpe"), 0.6
      ]),
      kw("diagnostics"), map([
        kw("gross-exposure"), 1,
        kw("net-exposure"), 1,
        kw("effective-n"), 2,
        kw("turnover"), 0.6,
        kw("binding-constraints"), vector([]),
        kw("covariance-conditioning"), map([kw("status"), kw("ok")])
      ]),
      kw("rebalance-preview"), map([
        kw("status"), kw("ready"),
        kw("capital-usd"), 10000,
        kw("summary"), map([
          kw("ready-count"), 2,
          kw("blocked-count"), 0,
          kw("gross-trade-notional-usd"), 6000,
          kw("estimated-fees-usd"), 2.5,
          kw("estimated-slippage-usd"), 8,
          kw("margin"), map([kw("after-utilization"), 0.2])
        ]),
        kw("rows"), vector([])
      ])
    ]);
    const requestSignature = map([kw("seed"), "two-asset-draft-smoke"]);
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
      path("portfolio", "optimizer", "history-data"),
      map([
        kw("candle-history-by-coin"), map([
          "BTC", vector([
            map([kw("time-ms"), 1000, kw("close"), "100"]),
            map([kw("time-ms"), 2000, kw("close"), "102"]),
            map([kw("time-ms"), 3000, kw("close"), "105"]),
            map([kw("time-ms"), 4000, kw("close"), "109"])
          ]),
          "ETH", vector([
            map([kw("time-ms"), 1000, kw("close"), "100"]),
            map([kw("time-ms"), 2000, kw("close"), "101"]),
            map([kw("time-ms"), 3000, kw("close"), "100"]),
            map([kw("time-ms"), 4000, kw("close"), "102"])
          ]),
          "HYPE", vector([
            map([kw("time-ms"), 1000, kw("close"), "44"]),
            map([kw("time-ms"), 2000, kw("close"), "46"]),
            map([kw("time-ms"), 3000, kw("close"), "51"]),
            map([kw("time-ms"), 4000, kw("close"), "54"])
          ])
        ]),
        kw("funding-history-by-coin"), map([
          "BTC", vector([map([kw("time-ms"), 1000, kw("funding-rate-raw"), 0])]),
          "ETH", vector([map([kw("time-ms"), 1000, kw("funding-rate-raw"), 0])]),
          "HYPE", vector([map([kw("time-ms"), 1000, kw("funding-rate-raw"), 0])])
        ])
      ])
    );
    state = c.assoc_in(
      state,
      path("portfolio", "optimizer", "runtime"),
      map([kw("as-of-ms"), 5000, kw("stale-after-ms"), 60000])
    );
    state = c.assoc_in(state, path("portfolio", "optimizer", "last-successful-run"), lastSuccessfulRun);
    state = c.assoc_in(
      state,
      path("portfolio", "optimizer", "run-state"),
      map([kw("status"), kw("succeeded"), kw("request-signature"), requestSignature])
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

  await seedTwoAssetDraftScenario(page);
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
    },
    {
      key: "perp:HYPE",
      "market-type": optimizerKeyword("perp"),
      coin: "HYPE",
      symbol: "HYPE-USDC",
      base: "HYPE",
      quote: "USDC",
      volume24h: 774000000
    },
    {
      key: "perp:SOL",
      "market-type": optimizerKeyword("perp"),
      coin: "SOL",
      symbol: "SOL-USDC",
      base: "SOL",
      quote: "USDC",
      volume24h: 62000000
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
  const searchHint = page.locator("[data-role='portfolio-optimizer-draft-add-asset-search-add-hint']");
  const searchClear = page.locator("[data-role='portfolio-optimizer-draft-add-asset-search-clear']");
  const searchResults = page.locator("[data-role='portfolio-optimizer-draft-add-asset-search-results']");
  const hypeRow = page.locator("[data-role='portfolio-optimizer-draft-add-asset-candidate-row-perp:HYPE']");
  const candidateRows = page.locator(
    "[data-role^='portfolio-optimizer-draft-add-asset-candidate-row-']"
  );
  const activeCandidate = page.locator(
    "[data-role^='portfolio-optimizer-draft-add-asset-candidate-row-'][data-active='true']"
  );

  await addAsset.click();
  await expect(popover).toBeVisible();
  await expect(searchInput).toBeVisible();
  await expect(searchHint).toHaveCount(0);
  await expect(popover).toHaveCSS("background-color", "rgb(14, 16, 19)");
  await expect(popover).toHaveCSS("overflow-y", "hidden");
  await expect(searchInput).toHaveCSS("box-shadow", "none");
  await searchInput.fill("hype");
  await expect(searchClear).toHaveCount(0);
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
  await expect(hypeRow).toBeVisible();
  await expect(searchResults).toHaveCSS("scrollbar-width", "none");
  await expect(activeCandidate).toHaveCount(1);
  const firstActiveRole = await activeCandidate.getAttribute("data-role");
  expect(firstActiveRole).toEqual("portfolio-optimizer-draft-add-asset-candidate-row-perp:HYPE");
  const candidateCount = await candidateRows.count();
  if (candidateCount > 1) {
    await searchInput.press("ArrowDown");
    await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
    await expect(activeCandidate).toHaveCount(1);
    const secondActiveRole = await activeCandidate.getAttribute("data-role");
    expect(secondActiveRole).not.toEqual(firstActiveRole);
    await expect(activeCandidate).toHaveCSS("background-color", "rgba(212, 181, 88, 0.12)");
    await searchInput.press("ArrowUp");
    await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
    await expect(activeCandidate).toHaveAttribute(
      "data-role",
      firstActiveRole
    );
  }
  const selectedMarketKey = firstActiveRole.replace(
    "portfolio-optimizer-draft-add-asset-candidate-row-",
    ""
  );
  await searchInput.press("Enter");

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
      .some((instrumentId) =>
        instrumentId === selectedMarketKey || instrumentId === `hl:${selectedMarketKey}`
      );
  }).toBe(true);
  await expect.poll(async () => {
    const progress = await readOptimizerState(page, ["portfolio", "optimizer", "optimization-progress"]);
    return progress?.status;
  }, { timeout: 15_000 }).toBe("succeeded");
  await expect.poll(async () => {
    const result = await readOptimizerState(page, ["portfolio", "optimizer", "last-successful-run", "result"]);
    return result?.["instrument-ids"]?.map((instrumentId) =>
      String(instrumentId).replace(/^hl:/, "")
    );
  }).toContain("perp:HYPE");
});

test("portfolio optimizer draft allocation row can be excluded and rerun @smoke @regression", async ({ page }) => {
  test.setTimeout(90_000);

  await page.setViewportSize({ width: 1280, height: 900 });
  await visitRoute(page, "/portfolio/optimize/new", {
    routeModuleTimeoutMs: 30_000,
    idleOptions: { quietMs: 400, timeoutMs: 8_000, pollMs: 50 }
  });

  await seedTwoAssetDraftScenario(page);
  await dispatch(page, [
    ":actions/navigate",
    "/portfolio/optimize/draft",
    { "replace?": true }
  ]);
  await waitForIdle(page, { quietMs: 250, timeoutMs: 8_000, pollMs: 50 });

  const ethRow = page.locator("[data-role='portfolio-optimizer-target-exposure-asset-ETH']");
  const excludeEth = page.locator("[data-role='portfolio-optimizer-target-exposure-exclude-perp-ETH']");

  await expect(ethRow).toBeVisible();
  await ethRow.hover();
  await excludeEth.click();

  await expect(ethRow).toHaveAttribute("data-excluded", "true");
  await expect(ethRow).toContainText("excluded");
  await expect(ethRow).toContainText("sell to 0");
  await expect(ethRow).toContainText("0.00%");
  await expect.poll(async () => {
    const blocklist = await readOptimizerState(page, ["portfolio", "optimizer", "draft", "constraints", "blocklist"]);
    return blocklist;
  }).toContain("perp:ETH");
  await expect.poll(async () => {
    const progress = await readOptimizerState(page, ["portfolio", "optimizer", "optimization-progress"]);
    return progress?.status;
  }).toBe("succeeded");
  await expect.poll(async () => {
    const result = await readOptimizerState(page, ["portfolio", "optimizer", "last-successful-run", "result"]);
    return result?.["instrument-ids"]?.map((instrumentId) =>
      String(instrumentId).replace(/^hl:/, "")
    );
  }).toEqual(["perp:BTC"]);
  await expect.poll(async () => {
    const result = await readOptimizerState(page, ["portfolio", "optimizer", "last-successful-run", "result"]);
    return result?.frontier?.every((point) => point.weights?.length === 1);
  }).toBe(true);
});

test("portfolio optimizer draft objective menu changes objective and reruns frontier @smoke @regression", async ({ page }) => {
  test.setTimeout(90_000);

  await page.setViewportSize({ width: 1280, height: 900 });
  await visitRoute(page, "/portfolio/optimize/new", {
    routeModuleTimeoutMs: 30_000,
    idleOptions: { quietMs: 400, timeoutMs: 8_000, pollMs: 50 }
  });

  await seedTwoAssetDraftScenario(page);
  await dispatch(page, [
    ":actions/navigate",
    "/portfolio/optimize/draft",
    { "replace?": true }
  ]);
  await waitForIdle(page, { quietMs: 250, timeoutMs: 8_000, pollMs: 50 });

  const trigger = page.locator("[data-role='portfolio-optimizer-objective-menu-trigger']");
  const menu = page.locator("[data-role='portfolio-optimizer-objective-menu']");
  const minimumVolatility = page.locator(
    "[data-role='portfolio-optimizer-objective-menu-option-minimum-volatility']"
  );
  const apply = page.locator("[data-role='portfolio-optimizer-objective-menu-apply']");

  await expect(page.locator("[data-role='portfolio-optimizer-results-surface']"))
    .toBeVisible();
  await expect(trigger).toContainText("Maximum Sharpe");
  await trigger.click();
  await expect(menu).toBeVisible();
  await expect(menu).toContainText("Change objective");
  await expect(apply).toBeDisabled();
  await minimumVolatility.click();
  await expect(minimumVolatility).toHaveAttribute("data-selected", "true");
  await expect(apply).toBeEnabled();
  await apply.click();

  await expect(menu).toHaveCount(0);
  await expect(page.locator("[data-role='portfolio-optimizer-recompute-banner']"))
    .toBeVisible();
  await expect.poll(async () => {
    const draftObjective = await readOptimizerState(page, [
      "portfolio",
      "optimizer",
      "draft",
      "objective",
      "kind"
    ]);
    return draftObjective;
  }).toBe("minimum-variance");
  await expect.poll(async () => {
    const progress = await readOptimizerState(page, [
      "portfolio",
      "optimizer",
      "optimization-progress"
    ]);
    return progress?.status;
  }, { timeout: 15_000 }).toBe("succeeded");
  await expect.poll(async () => {
    const result = await readOptimizerState(page, [
      "portfolio",
      "optimizer",
      "last-successful-run",
      "result"
    ]);
    return result?.solver?.["objective-kind"];
  }).toBe("minimum-variance");
  await expect.poll(async () => {
    const result = await readOptimizerState(page, [
      "portfolio",
      "optimizer",
      "last-successful-run",
      "result"
    ]);
    return result?.frontier?.length ?? 0;
  }).toBeGreaterThan(0);
  await expect(trigger).toContainText("Minimum volatility");
});

test("portfolio optimizer draft objective menu captures use my views returns and confidence @smoke @regression", async ({ page }) => {
  test.setTimeout(90_000);

  await page.setViewportSize({ width: 1280, height: 900 });
  await visitRoute(page, "/portfolio/optimize/new", {
    routeModuleTimeoutMs: 30_000,
    idleOptions: { quietMs: 400, timeoutMs: 8_000, pollMs: 50 }
  });

  await seedTwoAssetDraftScenario(page);
  await dispatch(page, [
    ":actions/navigate",
    "/portfolio/optimize/draft",
    { "replace?": true }
  ]);
  await waitForIdle(page, { quietMs: 250, timeoutMs: 8_000, pollMs: 50 });

  const trigger = page.locator("[data-role='portfolio-optimizer-objective-menu-trigger']");
  const menu = page.locator("[data-role='portfolio-optimizer-objective-menu']");
  const useMyViews = page.locator(
    "[data-role='portfolio-optimizer-objective-menu-option-use-my-views']"
  );
  const editor = page.locator(
    "[data-role='portfolio-optimizer-objective-menu-use-my-views-editor']"
  );
  const btcReturn = page.locator(
    "[data-role='portfolio-optimizer-objective-menu-view-hl:perp:BTC-return']"
  );
  const ethReturn = page.locator(
    "[data-role='portfolio-optimizer-objective-menu-view-hl:perp:ETH-return']"
  );
  const btcHigh = page.locator(
    "[data-role='portfolio-optimizer-objective-menu-view-hl:perp:BTC-confidence-high']"
  );
  const ethLow = page.locator(
    "[data-role='portfolio-optimizer-objective-menu-view-hl:perp:ETH-confidence-low']"
  );
  const apply = page.locator("[data-role='portfolio-optimizer-objective-menu-apply']");

  await trigger.click();
  await expect(menu).toBeVisible();
  await useMyViews.click();
  await expect(editor).toBeVisible();
  await expect(editor).toContainText("Your return views");
  await expect(menu.locator("select")).toHaveCount(0);
  await expect(btcReturn).toHaveValue("19.5");
  await expect(ethReturn).toHaveValue("16.5");

  await btcReturn.fill("18");
  await ethReturn.fill("16.5");
  await btcHigh.click();
  await ethLow.click();
  await expect(btcHigh).toHaveAttribute("data-selected", "true");
  await expect(ethLow).toHaveAttribute("data-selected", "true");
  await expect(apply).toBeEnabled();
  await apply.click();

  await expect(menu).toHaveCount(0);
  await expect.poll(async () => {
    const returnModel = await readOptimizerState(page, [
      "portfolio",
      "optimizer",
      "draft",
      "return-model"
    ]);
    return {
      kind: returnModel?.kind,
      views: (returnModel?.views || []).map((view) => ({
        instrumentId: view["instrument-id"],
        value: view.return,
        confidence: view.confidence,
        confidenceLevel: view["confidence-level"]
      }))
    };
  }).toEqual({
    kind: "black-litterman",
    views: [
      {
        instrumentId: "hl:perp:BTC",
        value: 0.18,
        confidence: 0.75,
        confidenceLevel: "high"
      },
      {
        instrumentId: "hl:perp:ETH",
        value: 0.165,
        confidence: 0.25,
        confidenceLevel: "low"
      }
    ]
  });
});

test("portfolio optimizer use my views objective popover stays usable across review widths @smoke @regression", async ({ page }) => {
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

      await seedTwoAssetDraftScenario(page);
      await dispatch(page, [
        ":actions/navigate",
        "/portfolio/optimize/draft",
        { "replace?": true }
      ]);
      await waitForIdle(page, { quietMs: 250, timeoutMs: 8_000, pollMs: 50 });

      const trigger = page.locator("[data-role='portfolio-optimizer-objective-menu-trigger']");
      const menu = page.locator("[data-role='portfolio-optimizer-objective-menu']");
      const useMyViews = page.locator(
        "[data-role='portfolio-optimizer-objective-menu-option-use-my-views']"
      );
      const editor = page.locator(
        "[data-role='portfolio-optimizer-objective-menu-use-my-views-editor']"
      );

      await trigger.scrollIntoViewIfNeeded();
      await trigger.click();
      await expect(menu).toBeVisible();
      await useMyViews.click();
      await expect(editor).toBeVisible();
      await expect(editor).toContainText("Your return views");
      await expect(menu.locator("select")).toHaveCount(0);
      await expect(menu.locator("input[type='number']")).toHaveCount(0);
      await expect(menu.locator("[data-role$='confidence-high']")).toHaveCount(2);

      const box = await menu.boundingBox();
      expect(box).not.toBeNull();
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(viewport.width);
      expect(box.y).toBeGreaterThanOrEqual(0);
      expect(box.y + Math.min(box.height, viewport.height)).toBeLessThanOrEqual(
        viewport.height + 1
      );

      await page.locator(
        "[data-role='portfolio-optimizer-objective-menu-view-hl:perp:BTC-return']"
      ).fill("18");
      await page.locator(
        "[data-role='portfolio-optimizer-objective-menu-view-hl:perp:BTC-confidence-high']"
      ).click();
      await expect(page.locator(
        "[data-role='portfolio-optimizer-objective-menu-view-hl:perp:BTC-confidence-high']"
      )).toHaveAttribute("data-selected", "true");
    });
  }
});

test("portfolio optimizer draft objective menu stays contained across viewports @smoke @regression", async ({ page }) => {
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
      await dispatch(page, [
        ":actions/navigate",
        "/portfolio/optimize/draft",
        { "replace?": true }
      ]);
      await waitForIdle(page, { quietMs: 250, timeoutMs: 8_000, pollMs: 50 });

      const trigger = page.locator("[data-role='portfolio-optimizer-objective-menu-trigger']");
      const menu = page.locator("[data-role='portfolio-optimizer-objective-menu']");
      const backdrop = page.locator("[data-role='portfolio-optimizer-objective-menu-backdrop']");

      await trigger.scrollIntoViewIfNeeded();
      const triggerBox = await trigger.boundingBox();
      expect(triggerBox).not.toBeNull();

      await trigger.click();
      await expect(menu).toBeVisible();
      await expect(menu).toHaveCSS("background-color", "rgb(16, 19, 22)");
      await expect(backdrop).toHaveCount(0);

      const box = await menu.boundingBox();
      expect(box).not.toBeNull();
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(viewport.width);
      expect(box.y).toBeGreaterThanOrEqual(0);
      expect(box.y + box.height).toBeLessThanOrEqual(viewport.height);
      expect(box.y).toBeGreaterThanOrEqual(triggerBox.y + triggerBox.height - 2);
      expect(Math.abs(box.x - triggerBox.x)).toBeLessThanOrEqual(
        viewport.width >= 1280 ? 24 : 180
      );
    });
  }
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
      await expect(searchInput).toHaveAttribute("type", "search");
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
