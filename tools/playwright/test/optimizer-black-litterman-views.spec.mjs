import { expect, test } from "@playwright/test";
import { visitRoute, waitForIdle } from "../support/hyperopen.mjs";

const DESIGN_REVIEW_VIEWPORTS = Object.freeze([
  { id: "review-375", width: 375, height: 812 },
  { id: "review-768", width: 768, height: 1024 },
  { id: "review-1280", width: 1280, height: 900 },
  { id: "review-1440", width: 1440, height: 900 }
]);

async function seedMarkets(page) {
  await page.evaluate(() => {
    const c = globalThis.cljs.core;
    const kw = (name) => c.keyword(name);
    const market = (key, marketType, coin, symbol, dex = null) => {
      const entries = [
        kw("key"), key,
        kw("market-type"), kw(marketType),
        kw("coin"), coin,
        kw("symbol"), symbol
      ];
      if (dex) {
        entries.push(kw("dex"), dex);
      }
      return c.PersistentArrayMap.fromArray(entries, true);
    };

    const btc = market("perp:BTC", "perp", "BTC", "BTC-USDC", "hl");
    const eth = market("perp:ETH", "perp", "ETH", "ETH-USDC", "hl");
    const sol = market("perp:SOL", "perp", "SOL", "SOL-USDC", "hl");
    const hype = market("perp:HYPE", "perp", "HYPE", "HYPE-USDC", "hl");
    const markets = c.PersistentVector.fromArray([btc, eth, sol, hype], true);
    const marketByKey = c.PersistentArrayMap.fromArray(
      [
        "perp:BTC", btc,
        "perp:ETH", eth,
        "perp:SOL", sol,
        "perp:HYPE", hype
      ],
      true
    );
    const state = c.deref(globalThis.hyperopen.system.store);
    const withMarkets = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("asset-selector"), kw("markets")], true),
      markets
    );
    const nextState = c.assoc_in(
      withMarkets,
      c.PersistentVector.fromArray([kw("asset-selector"), kw("market-by-key")], true),
      marketByKey
    );
    c.reset_BANG_(globalThis.hyperopen.system.store, nextState);
  });
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
}

async function seedBlackLittermanEditorState(page) {
  await page.evaluate(() => {
    const c = globalThis.cljs.core;
    const kw = (name) => c.keyword(name);
    const map = (entries) => c.PersistentArrayMap.fromArray(entries, true);
    const vector = (items) => c.PersistentVector.fromArray(items, true);
    const absoluteView = map([
      kw("id"), "view-1",
      kw("kind"), kw("absolute"),
      kw("instrument-id"), "perp:HYPE",
      kw("return"), 0.45,
      kw("confidence"), 0.75,
      kw("horizon"), kw("1y"),
      kw("notes"), "Momentum conviction"
    ]);
    const relativeView = map([
      kw("id"), "view-2",
      kw("kind"), kw("relative"),
      kw("instrument-id"), "perp:ETH",
      kw("comparator-instrument-id"), "perp:SOL",
      kw("direction"), kw("outperform"),
      kw("return"), 0.05,
      kw("confidence"), 0.5,
      kw("horizon"), kw("6m"),
      kw("notes"), "Pair view"
    ]);
    const draftReturnModel = map([
      kw("kind"), kw("black-litterman"),
      kw("views"), vector([absoluteView, relativeView])
    ]);
    const editorState = map([
      kw("selected-kind"), kw("absolute"),
      kw("drafts"), map([
        kw("absolute"), map([
          kw("instrument-id"), "perp:HYPE",
          kw("return-text"), "45",
          kw("confidence"), kw("high"),
          kw("horizon"), kw("1y"),
          kw("notes"), "Momentum conviction"
        ]),
        kw("relative"), map([
          kw("instrument-id"), "perp:ETH",
          kw("comparator-instrument-id"), "perp:SOL",
          kw("direction"), kw("outperform"),
          kw("return-text"), "5",
          kw("confidence"), kw("medium"),
          kw("horizon"), kw("6m"),
          kw("notes"), "Pair view"
        ])
      ]),
      kw("editing-view-id"), null,
      kw("clear-confirmation-open?"), false
    ]);
    const store = globalThis.hyperopen.system.store;
    let state = c.deref(store);
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray(
        [kw("portfolio"), kw("optimizer"), kw("draft"), kw("return-model")],
        true
      ),
      draftReturnModel
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray(
        [kw("portfolio-ui"), kw("optimizer"), kw("black-litterman-editor")],
        true
      ),
      editorState
    );
    c.reset_BANG_(store, state);
  });
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
}

async function seedBlackLittermanAutomaticReturnState(page) {
  await page.evaluate(() => {
    const c = globalThis.cljs.core;
    const kw = (name) => c.keyword(name);
    const map = (entries) => c.PersistentArrayMap.fromArray(entries, true);
    const vector = (items) => c.PersistentVector.fromArray(items, true);
    const candle = (time, close) => map([kw("time"), time, kw("close"), close]);
    const btcInstrument = map([
      kw("instrument-id"), "perp:BTC",
      kw("market-type"), kw("perp"),
      kw("coin"), "BTC",
      kw("symbol"), "BTC-USDC"
    ]);
    const draft = map([
      kw("universe"), vector([btcInstrument]),
      kw("objective"), map([kw("kind"), kw("max-sharpe")]),
      kw("return-model"), map([kw("kind"), kw("black-litterman"), kw("views"), vector([])]),
      kw("risk-model"), map([kw("kind"), kw("sample-covariance")]),
      kw("constraints"), map([])
    ]);
    const editorState = map([
      kw("selected-kind"), kw("absolute"),
      kw("drafts"), map([
        kw("absolute"), map([
          kw("instrument-id"), null,
          kw("return-text"), "",
          kw("return-text-touched?"), false,
          kw("confidence"), kw("medium"),
          kw("horizon"), kw("3m"),
          kw("notes"), ""
        ])
      ]),
      kw("editing-view-id"), null,
      kw("errors"), map([]),
      kw("clear-confirmation-open?"), false
    ]);
    const store = globalThis.hyperopen.system.store;
    let state = c.deref(store);
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("draft")], true),
      draft
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray(
        [kw("portfolio"), kw("optimizer"), kw("history-data"), kw("candle-history-by-coin"), "BTC"],
        true
      ),
      vector([
        candle(1000, "100"),
        candle(2000, "100.01"),
        candle(3000, "100.040003")
      ])
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray(
        [kw("portfolio"), kw("optimizer"), kw("history-data"), kw("funding-history-by-coin")],
        true
      ),
      map([])
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("market-cap-by-coin")], true),
      map(["BTC", 1])
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("runtime"), kw("as-of-ms")], true),
      5000
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("history-load-state")], true),
      map([
        kw("status"), kw("loading"),
        kw("reason"), kw("selection-prefetch")
      ])
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray(
        [kw("portfolio-ui"), kw("optimizer"), kw("black-litterman-editor")],
        true
      ),
      editorState
    );
    c.reset_BANG_(store, state);
  });
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
}

async function seedBlackLittermanVaultPreviewState(page) {
  const vaultAddress = "0x3333333333333333333333333333333333333333";
  const vaultId = `vault:${vaultAddress}`;

  await page.evaluate(({ vaultAddress, vaultId }) => {
    const c = globalThis.cljs.core;
    const kw = (name) => c.keyword(name);
    const map = (entries) => c.PersistentArrayMap.fromArray(entries, true);
    const vector = (items) => c.PersistentVector.fromArray(items, true);
    const candle = (time, close) => map([kw("time"), time, kw("close"), close]);
    const dayStartMs = (day) => new Date(`${day}T00:00:00.000Z`).getTime();
    const summaryFromPoints = (points) =>
      map([
        kw("accountValueHistory"),
        vector(points.map(([timeMs, accountValue]) => vector([timeMs, accountValue]))),
        kw("pnlHistory"),
        vector(points.map(([timeMs, _accountValue, pnlValue]) => vector([timeMs, pnlValue])))
      ]);

    const t0 = dayStartMs("2026-05-01");
    const t1 = dayStartMs("2026-05-02");
    const t2 = dayStartMs("2026-05-03");
    const t3 = dayStartMs("2026-05-04");
    const btcInstrument = map([
      kw("instrument-id"), "perp:BTC",
      kw("market-type"), kw("perp"),
      kw("coin"), "BTC",
      kw("symbol"), "BTC-USDC"
    ]);
    const vaultInstrument = map([
      kw("instrument-id"), vaultId,
      kw("market-type"), kw("vault"),
      kw("coin"), vaultId,
      kw("vault-address"), vaultAddress,
      kw("name"), "Alpha Yield"
    ]);
    const absoluteView = map([
      kw("id"), "view-1",
      kw("kind"), kw("absolute"),
      kw("instrument-id"), vaultId,
      kw("return"), 0.04,
      kw("confidence"), 0.8,
      kw("weights"), map([vaultId, 1])
    ]);
    const draft = map([
      kw("universe"), vector([btcInstrument, vaultInstrument]),
      kw("objective"), map([kw("kind"), kw("max-sharpe")]),
      kw("return-model"), map([
        kw("kind"), kw("black-litterman"),
        kw("views"), vector([absoluteView])
      ]),
      kw("risk-model"), map([kw("kind"), kw("sample-covariance")]),
      kw("constraints"), map([kw("long-only?"), true, kw("max-asset-weight"), 0.75])
    ]);
    const historyData = map([
      kw("candle-history-by-coin"),
      map([
        "BTC",
        vector([
          candle(t0, "100"),
          candle(t1, "101"),
          candle(t2, "99"),
          candle(t3, "102")
        ])
      ]),
      kw("funding-history-by-coin"),
      map([]),
      kw("vault-details-by-address"),
      map([
        vaultAddress,
        map([
          kw("portfolio"),
          map([
            kw("month"),
            summaryFromPoints([
              [t0, 100, 0],
              [t1, 102, 2],
              [t2, 99, -1],
              [t3, 104, 4]
            ])
          ])
        ])
      ])
    ]);
    const store = globalThis.hyperopen.system.store;
    let state = c.deref(store);
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("draft")], true),
      draft
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("history-data")], true),
      historyData
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("market-cap-by-coin")], true),
      map(["BTC", 100, vaultId, 100])
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("runtime"), kw("as-of-ms")], true),
      t3 + 24 * 60 * 60 * 1000
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("history-load-state")], true),
      map([kw("status"), kw("idle")])
    );
    c.reset_BANG_(store, state);
  }, { vaultAddress, vaultId });

  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
  return { vaultId };
}

async function widthRatio(child, parent) {
  const [childBox, parentBox] = await Promise.all([
    child.boundingBox(),
    parent.boundingBox()
  ]);

  if (!childBox || !parentBox || parentBox.width === 0) {
    return 0;
  }

  return childBox.width / parentBox.width;
}

test("portfolio optimizer use my views editor flow exposes the Edit Views contract @regression", async ({ page }) => {
  test.setTimeout(90_000);

  await page.setViewportSize({ width: 900, height: 900 });
  await visitRoute(page, "/portfolio/optimize/new");
  await expect(page.locator("[data-role='portfolio-optimizer-setup-route-surface']"))
    .toBeVisible({ timeout: 60_000 });

  await seedMarkets(page);
  await seedBlackLittermanEditorState(page);

  const panel = page.locator("[data-role='portfolio-optimizer-black-litterman-panel']");
  await expect(panel).toContainText("EDIT VIEWS");
  await expect(panel).toContainText("Tell the model what you believe");
  await expect(panel).toContainText("ACTIVE VIEWS (2/10)");
  await expect(panel.locator("[data-role='portfolio-optimizer-black-litterman-preview-text']"))
    .toContainText("HYPE expected return +45% annualized");
  await expect(panel).toContainText("ETH > SOL by 5% annualized");
  await expect(panel.locator("select")).toHaveCount(0);

  const instrumentGrid = panel.locator(
    "[data-role='portfolio-optimizer-black-litterman-editor-instrument-grid']"
  );
  const assetOptions = panel.locator(
    "[data-role='portfolio-optimizer-black-litterman-editor-asset-options']"
  );
  const comparatorOptions = panel.locator(
    "[data-role='portfolio-optimizer-black-litterman-editor-comparator-options']"
  );

  await expect(assetOptions).toBeVisible();
  await expect(comparatorOptions).toHaveCount(0);
  await expect
    .poll(() => widthRatio(assetOptions, instrumentGrid), {
      message: "absolute asset selector should span the instrument editor grid",
      timeout: 4_000
    })
    .toBeGreaterThan(0.95);

  await page.locator("[data-role='portfolio-optimizer-black-litterman-editor-type-relative']").click();
  await expect(comparatorOptions).toBeVisible();
  await expect
    .poll(() => widthRatio(assetOptions, instrumentGrid), {
      message: "relative asset selector should share the grid with comparator",
      timeout: 4_000
    })
    .toBeLessThan(0.65);
  await page.locator("[data-role='portfolio-optimizer-black-litterman-editor-type-absolute']").click();
  await expect(comparatorOptions).toHaveCount(0);

  await page.locator("[data-role='portfolio-optimizer-black-litterman-active-view-view-2-remove']").click();
  await expect(panel).toContainText("ACTIVE VIEWS (1/10)");

  await page.locator("[data-role='portfolio-optimizer-black-litterman-clear-all']").click();
  await expect(panel.locator("[data-role='portfolio-optimizer-black-litterman-clear-confirm']"))
    .toBeVisible();
  await page.locator("[data-role='portfolio-optimizer-black-litterman-clear-cancel']").click();
  await expect(panel).toContainText(/views adjust expected returns only/i);
});

test("portfolio optimizer use my views prepopulates absolute return while history is loading @regression", async ({ page }) => {
  test.setTimeout(90_000);

  await page.setViewportSize({ width: 900, height: 900 });
  await visitRoute(page, "/portfolio/optimize/new");
  await expect(page.locator("[data-role='portfolio-optimizer-setup-route-surface']"))
    .toBeVisible({ timeout: 60_000 });

  await seedBlackLittermanAutomaticReturnState(page);

  const panel = page.locator("[data-role='portfolio-optimizer-black-litterman-panel']");
  const returnInput = panel.locator("[data-role='portfolio-optimizer-black-litterman-editor-return']");
  await expect(returnInput).toHaveValue("7.3");
  await expect(panel.locator("[data-role='portfolio-optimizer-black-litterman-preview-text']"))
    .toContainText("BTC expected return +7.3% annualized");
});

test("portfolio optimizer use my views preview renders vertical bars across review widths @regression", async ({ page }) => {
  test.setTimeout(90_000);

  for (const viewport of DESIGN_REVIEW_VIEWPORTS) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await visitRoute(page, "/portfolio/optimize/new");
    await expect(page.locator("[data-role='portfolio-optimizer-setup-route-surface']"))
      .toBeVisible({ timeout: 60_000 });

    const { vaultId } = await seedBlackLittermanVaultPreviewState(page);
    const preview = page.locator("[data-role='portfolio-optimizer-black-litterman-preview-panel']");
    await preview.scrollIntoViewIfNeeded();

    await expect(preview).toBeVisible();
    await expect(preview).toContainText("Alpha Yield");
    await expect(preview).not.toContainText(vaultId);

    const chartGeometry = await preview
      .locator("[data-role='portfolio-optimizer-black-litterman-preview-svg']")
      .evaluate((svg) => {
        const readRect = (role) => {
          const rect = svg.querySelector(`[data-role="${role}"]`);
          return rect
            ? {
                tagName: rect.tagName.toLowerCase(),
                x: Number(rect.getAttribute("x")),
                y: Number(rect.getAttribute("y")),
                width: Number(rect.getAttribute("width")),
                height: Number(rect.getAttribute("height"))
              }
            : null;
        };

        return {
          prior: readRect("portfolio-optimizer-black-litterman-preview-bar-prior-perp:BTC"),
          posterior: readRect("portfolio-optimizer-black-litterman-preview-bar-posterior-perp:BTC"),
          horizontalOverflow: document.documentElement.scrollWidth - window.innerWidth
        };
      });

    expect(chartGeometry.prior?.tagName, `${viewport.id} prior bar`).toBe("rect");
    expect(chartGeometry.posterior?.tagName, `${viewport.id} posterior bar`).toBe("rect");
    expect(chartGeometry.prior.height, `${viewport.id} prior bar height`).toBeGreaterThan(0);
    expect(chartGeometry.posterior.height, `${viewport.id} posterior bar height`).toBeGreaterThan(0);
    expect(chartGeometry.prior.width, `${viewport.id} grouped bar width`)
      .toBe(chartGeometry.posterior.width);
    expect(chartGeometry.prior.x, `${viewport.id} grouped bar x separation`)
      .not.toBe(chartGeometry.posterior.x);
    expect(chartGeometry.horizontalOverflow, `${viewport.id} horizontal overflow`).toBeLessThanOrEqual(1);
  }
});
