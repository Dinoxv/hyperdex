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

async function seedBlackLittermanPendingBtcRunState(page) {
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
    const ethInstrument = map([
      kw("instrument-id"), "perp:ETH",
      kw("market-type"), kw("perp"),
      kw("coin"), "ETH",
      kw("symbol"), "ETH-USDC"
    ]);
    const draft = map([
      kw("universe"), vector([btcInstrument, ethInstrument]),
      kw("objective"), map([kw("kind"), kw("max-sharpe")]),
      kw("return-model"), map([kw("kind"), kw("black-litterman"), kw("views"), vector([])]),
      kw("risk-model"), map([kw("kind"), kw("sample-covariance")]),
      kw("constraints"), map([kw("long-only?"), true, kw("max-asset-weight"), 1])
    ]);
    const editorState = map([
      kw("selected-kind"), kw("absolute"),
      kw("drafts"), map([
        kw("absolute"), map([
          kw("instrument-id"), "perp:BTC",
          kw("return-text"), "20",
          kw("return-text-touched?"), true,
          kw("confidence"), kw("high"),
          kw("horizon"), kw("1y"),
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
        [kw("portfolio"), kw("optimizer"), kw("history-data"), kw("candle-history-by-coin")],
        true
      ),
      map([
        "BTC",
        vector([
          candle(1000, "100"),
          candle(2000, "99.92"),
          candle(3000, "100.01"),
          candle(4000, "99.7")
        ]),
        "ETH",
        vector([
          candle(1000, "2000"),
          candle(2000, "2020"),
          candle(3000, "2010"),
          candle(4000, "2030")
        ])
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
      map(["BTC", 100, "ETH", 100])
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("runtime"), kw("as-of-ms")], true),
      5000
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("history-load-state")], true),
      map([kw("status"), kw("idle")])
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

async function seedBlackLittermanDirtyRetainedResultState(page) {
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
    const activeView = map([
      kw("id"), "view-1",
      kw("kind"), kw("absolute"),
      kw("instrument-id"), "perp:BTC",
      kw("return"), 0.2,
      kw("confidence"), 0.75,
      kw("weights"), map(["perp:BTC", 1])
    ]);
    const draft = map([
      kw("universe"), vector([btcInstrument]),
      kw("objective"), map([kw("kind"), kw("max-sharpe")]),
      kw("return-model"), map([kw("kind"), kw("black-litterman"), kw("views"), vector([activeView])]),
      kw("risk-model"), map([kw("kind"), kw("sample-covariance")]),
      kw("constraints"), map([kw("long-only?"), true, kw("max-asset-weight"), 1]),
      kw("metadata"), map([kw("dirty?"), true])
    ]);
    const lastSuccessfulRun = map([
      kw("request-signature"), map([kw("seed"), "old-result"]),
      kw("computed-at-ms"), 4000,
      kw("result"), map([
        kw("status"), kw("solved"),
        kw("instrument-ids"), vector(["perp:BTC"]),
        kw("target-weights"), vector([1]),
        kw("current-weights"), vector([0]),
        kw("frontier"), vector([]),
        kw("frontier-overlays"), map([])
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
      c.PersistentVector.fromArray(
        [kw("portfolio"), kw("optimizer"), kw("history-data"), kw("candle-history-by-coin"), "BTC"],
        true
      ),
      vector([
        candle(1000, "100"),
        candle(2000, "95"),
        candle(3000, "92"),
        candle(4000, "90")
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
      map(["BTC", 100])
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("runtime"), kw("as-of-ms")], true),
      5000
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("history-load-state")], true),
      map([kw("status"), kw("idle")])
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("run-state")], true),
      map([
        kw("status"), kw("running"),
        kw("run-id"), "run-new-view",
        kw("started-at-ms"), 5000
      ])
    );
    state = c.assoc_in(
      state,
      c.PersistentVector.fromArray([kw("portfolio"), kw("optimizer"), kw("last-successful-run")], true),
      lastSuccessfulRun
    );
    c.reset_BANG_(store, state);
  });
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
}

async function readBlackLittermanDraftViews(page) {
  return page.evaluate(() => {
    const c = globalThis.cljs.core;
    const kw = (name) => c.keyword(name);
    const path = (...segments) => c.PersistentVector.fromArray(segments.map(kw), true);
    const store = globalThis.hyperopen.system.store;
    const state = c.deref(store);
    const views = c.get_in(
      state,
      path("portfolio", "optimizer", "draft", "return-model", "views")
    );
    const firstView = c.first(views);
    const errors = c.get_in(
      state,
      path("portfolio-ui", "optimizer", "black-litterman-editor", "errors")
    );
    return {
      count: c.count(views),
      firstInstrumentId: firstView ? c.get(firstView, kw("instrument-id")) : null,
      firstReturn: firstView ? c.get(firstView, kw("return")) : null,
      firstConfidence: firstView ? c.get(firstView, kw("confidence")) : null,
      errorCount: c.count(errors)
    };
  });
}

async function readBlackLittermanRunResult(page) {
  return page.evaluate(() => {
    const c = globalThis.cljs.core;
    const kw = (name) => c.keyword(name);
    const path = (...segments) => c.PersistentVector.fromArray(segments.map(kw), true);
    const store = globalThis.hyperopen.system.store;
    const state = c.deref(store);
    const result = c.get_in(
      state,
      path("portfolio", "optimizer", "last-successful-run", "result")
    );

    if (!result) {
      return null;
    }

    const expectedByInstrument = c.get(result, kw("expected-returns-by-instrument"));
    const diagnostics = c.get(result, kw("black-litterman-diagnostics"));
    const overlays = c.get(result, kw("frontier-overlays"));
    const standalone = overlays ? c.get(overlays, kw("standalone")) : null;
    let standaloneBtc = null;

    for (let seq = c.seq(standalone); seq; seq = c.next(seq)) {
      const point = c.first(seq);
      if (c.get(point, kw("instrument-id")) === "perp:BTC") {
        standaloneBtc = c.get(point, kw("expected-return"));
        break;
      }
    }

    return {
      status: String(c.get(result, kw("status"))),
      returnModel: String(c.get(result, kw("return-model"))),
      viewCount: diagnostics ? c.get(diagnostics, kw("view-count")) : null,
      expectedBtc: expectedByInstrument ? c.get(expectedByInstrument, "perp:BTC") : null,
      standaloneBtc
    };
  });
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

async function visitOptimizerNew(page) {
  const routeSurface = page.locator("[data-role='portfolio-optimizer-setup-route-surface']");
  let lastError = null;

  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      await visitRoute(page, "/portfolio/optimize/new", {
        routeModuleTimeoutMs: 30_000,
        idleOptions: { quietMs: 400, timeoutMs: 8_000, pollMs: 50 }
      });
      await expect(routeSurface).toBeVisible({ timeout: 30_000 });
      return;
    } catch (error) {
      lastError = error;
      if (attempt === 2) {
        throw error;
      }
      await page.goto("/trade");
      await page.waitForTimeout(250);
    }
  }

  throw lastError;
}

test("portfolio optimizer use my views editor flow exposes the Edit Views contract @regression", async ({ page }) => {
  test.setTimeout(90_000);

  await page.setViewportSize({ width: 900, height: 900 });
  await visitOptimizerNew(page);

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
  await visitOptimizerNew(page);

  await seedBlackLittermanAutomaticReturnState(page);

  const panel = page.locator("[data-role='portfolio-optimizer-black-litterman-panel']");
  const returnInput = panel.locator("[data-role='portfolio-optimizer-black-litterman-editor-return']");
  await expect(returnInput).toHaveValue("7.3");
  await expect(panel.locator("[data-role='portfolio-optimizer-black-litterman-preview-text']"))
    .toContainText("BTC expected return +7.3% annualized");
});

test("portfolio optimizer run applies a valid pending BTC view through the worker result @regression", async ({ page }) => {
  test.setTimeout(90_000);

  await page.setViewportSize({ width: 900, height: 900 });
  await visitOptimizerNew(page);

  await seedBlackLittermanPendingBtcRunState(page);

  const panel = page.locator("[data-role='portfolio-optimizer-black-litterman-panel']");
  await expect(panel.locator("[data-role='portfolio-optimizer-black-litterman-preview-text']"))
    .toContainText("BTC expected return +20% annualized");
  await expect(panel.locator("[data-role='portfolio-optimizer-black-litterman-pending-view-status']"))
    .toContainText("Pending view will apply on run.");

  await page.locator("[data-role='portfolio-optimizer-run-draft']").click();

  await expect
    .poll(() => readBlackLittermanDraftViews(page), {
      message: "pending BTC view should be materialized before the run pipeline reads the draft",
      timeout: 4_000
    })
    .toMatchObject({
      count: 1,
      firstInstrumentId: "perp:BTC",
      firstReturn: 0.2,
      firstConfidence: 0.75,
      errorCount: 0
    });

  await expect(page.locator("[data-role='portfolio-optimizer-run-status-panel']"))
    .toContainText("Succeeded", { timeout: 15_000 });

  await expect
    .poll(() => readBlackLittermanRunResult(page), {
      message: "worker-backed BL run should produce positive BTC effective return",
      timeout: 4_000
    })
    .toMatchObject({
      status: ":solved",
      returnModel: "black-litterman",
      viewCount: 1
    });

  const result = await readBlackLittermanRunResult(page);
  expect(result.expectedBtc).toBeGreaterThan(0);
  expect(result.standaloneBtc).toBeGreaterThan(0);
});

test("portfolio optimizer setup hides stale retained weights during a view rerun @regression", async ({ page }) => {
  test.setTimeout(90_000);

  await page.setViewportSize({ width: 900, height: 900 });
  await visitOptimizerNew(page);

  await seedBlackLittermanDirtyRetainedResultState(page);

  await expect(page.locator("[data-role='portfolio-optimizer-last-successful-run']"))
    .toContainText("Retaining last successful result while rerunning.");
  await expect(page.locator("[data-role='portfolio-optimizer-view-weights']"))
    .toHaveCount(0);
  await expect(page.locator("[data-role='portfolio-optimizer-results-link']"))
    .toHaveCount(0);
});

test("portfolio optimizer use my views preview renders vertical bars across review widths @regression", async ({ page }) => {
  test.setTimeout(90_000);

  for (const viewport of DESIGN_REVIEW_VIEWPORTS) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await visitOptimizerNew(page);

    const { vaultId } = await seedBlackLittermanVaultPreviewState(page);
    const preview = page.locator("[data-role='portfolio-optimizer-black-litterman-preview-panel']");
    const workspace = page.locator("[data-role='portfolio-optimizer-setup-use-my-views-workspace']");
    const legend = page.locator("[data-role='portfolio-optimizer-setup-use-my-views-legend']");
    const chartShell = page.locator("[data-role='portfolio-optimizer-setup-use-my-views-chart-shell']");
    const cards = page.locator("[data-role='portfolio-optimizer-setup-use-my-views-insight-cards']");
    const actionBar = page.locator("[data-role='portfolio-optimizer-setup-bottom-actions']");
    await preview.scrollIntoViewIfNeeded();

    await expect(workspace).toBeVisible();
    await expect(workspace).toContainText("What the model assumes and what your views change");
    await expect(page.locator("[data-role='portfolio-optimizer-setup-summary-heading']")).toHaveCount(0);
    await expect(page.locator("[data-role='portfolio-optimizer-setup-summary-panel']")).toHaveCount(0);
    await expect(legend.locator("[data-role='portfolio-optimizer-setup-use-my-views-legend-market-reference']"))
      .toContainText("Market reference");
    await expect(legend.locator("[data-role='portfolio-optimizer-setup-use-my-views-legend-your-view']"))
      .toContainText("Your view");
    await expect(legend.locator("[data-role='portfolio-optimizer-setup-use-my-views-legend-combined-output']"))
      .toContainText("Combined output");
    await expect(chartShell).toBeVisible();
    const marketReferenceCard = cards.locator(
      "[data-role='portfolio-optimizer-setup-use-my-views-card-market-reference']"
    );
    const yourViewsCard = cards.locator(
      "[data-role='portfolio-optimizer-setup-use-my-views-card-your-views']"
    );
    const combinedOutputCard = cards.locator(
      "[data-role='portfolio-optimizer-setup-use-my-views-card-combined-output']"
    );
    await expect(marketReferenceCard).toHaveCount(1);
    await expect(yourViewsCard).toHaveCount(1);
    await expect(combinedOutputCard).toHaveCount(1);
    await expect(marketReferenceCard)
      .toContainText("What the model assumes before your views");
    await expect(marketReferenceCard).toContainText("BTC");
    await expect(marketReferenceCard).toContainText("Alpha Yield");
    await expect(yourViewsCard).toContainText("What you're changing");
    await expect(yourViewsCard).toContainText("1 view active");
    await expect(yourViewsCard).toContainText("Alpha Yield");
    await expect(yourViewsCard).toContainText("+4%");
    await expect(yourViewsCard).toContainText("high");
    await expect(combinedOutputCard)
      .toContainText("How much your views actually matter");
    await expect(combinedOutputCard).toContainText("Alpha Yield");
    await expect(combinedOutputCard).toContainText("→");
    await expect(combinedOutputCard).toContainText("(");
    await expect(actionBar).toBeVisible();
    await expect(actionBar.locator("[data-role='portfolio-optimizer-run-draft']"))
      .toBeVisible();
    await expect(actionBar.locator("[data-role='portfolio-optimizer-save-scenario']"))
      .toBeVisible();
    await expect(preview).toBeVisible();
    await expect(preview).toContainText("Alpha Yield");
    await expect(preview).not.toContainText(vaultId);
    await expect(preview.locator("[data-role='portfolio-optimizer-black-litterman-preview-legend']"))
      .toHaveCount(0);

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
          legendTransform: svg
            .querySelector("[data-role='portfolio-optimizer-black-litterman-preview-legend']")
            ?.getAttribute("transform"),
          legendItemTransforms: Array.from(
            svg.querySelectorAll("[data-role='portfolio-optimizer-black-litterman-preview-legend'] > g")
          ).map((legendItem) => legendItem.getAttribute("transform")),
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
    expect(chartGeometry.legendTransform, `${viewport.id} legend position`)
      .toBeUndefined();
    expect(chartGeometry.legendItemTransforms, `${viewport.id} legend columns`)
      .toEqual([]);
    expect(chartGeometry.horizontalOverflow, `${viewport.id} horizontal overflow`).toBeLessThanOrEqual(1);
  }
});
