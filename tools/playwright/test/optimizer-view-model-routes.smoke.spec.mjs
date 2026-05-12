import { expect, test } from "@playwright/test";
import { dispatch, visitRoute, waitForIdle } from "../support/hyperopen.mjs";

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
    const lastSuccessfulRun = map([
      kw("request-signature"), map([kw("seed"), "retained-draft-smoke"]),
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
