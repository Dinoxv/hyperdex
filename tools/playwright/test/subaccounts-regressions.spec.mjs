import { expect, test } from "@playwright/test";
import { debugCall, visitRoute, waitForIdle } from "../support/hyperopen.mjs";

const ownerAddress = "0x1234567890abcdef1234567890abcdef12345678";
const subaccountAddress = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";

async function seedAccountSurface(page, options = {}) {
  await page.evaluate(({ accountMode }) => {
    const c = globalThis.cljs?.core;
    const store = globalThis.hyperopen?.system?.store;

    if (!c || !store) {
      throw new Error("Hyperopen store or cljs core unavailable");
    }

    const keyword = c.keyword;
    const kwPath = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => keyword(segment)), true);
    const opts = c.PersistentArrayMap.fromArray([keyword("keywordize-keys"), true], true);
    const webdata2 = c.js__GT_clj(
      {
        clearinghouseState: {
          marginSummary: {
            accountValue: "300.61686499"
          }
        }
      },
      opts
    );
    const spot = c.js__GT_clj(
      {
        "clearinghouse-state": {
          balances: [
            {
              coin: "USDC",
              total: "358.56"
            },
            {
              coin: "USDH",
              token: "USDH:0xabc",
              total: "8.28"
            },
            {
              coin: "STAR",
              token: "STAR:0x456",
              total: "0"
            }
          ]
        }
      },
      opts
    );
    const spotBalances = c.js__GT_clj(
      [
        {
          coin: "USDC",
          total: "358.56"
        },
        {
          coin: "USDH",
          token: "USDH:0xabc",
          total: "8.28"
        },
        {
          coin: "STAR",
          token: "STAR:0x456",
          total: "0"
        }
      ],
      opts
    );

    let nextState = c.deref(store);
    nextState = c.assoc_in(nextState, kwPath("account", "mode"), keyword(accountMode));
    nextState = c.assoc_in(nextState, kwPath("webdata2"), webdata2);
    nextState = c.assoc_in(nextState, kwPath("spot"), spot);
    nextState = c.assoc_in(
      nextState,
      kwPath("spot", "clearinghouse-state", "balances"),
      spotBalances
    );
    c.reset_BANG_(store, nextState);
    const renderApp = globalThis.hyperopen?.app?.bootstrap?.render_app_BANG_;
    if (typeof renderApp === "function") {
      renderApp(c.deref(store));
    }
  }, { accountMode: options.accountMode ?? "classic" });
}

async function seedSubaccountsState(page, options = {}) {
  await page.evaluate(({ ownerAddress: owner, subaccountAddress: sub, accountMode }) => {
    const c = globalThis.cljs?.core;
    const store = globalThis.hyperopen?.system?.store;

    if (!c || !store) {
      throw new Error("Hyperopen store or cljs core unavailable");
    }

    const keyword = c.keyword;
    const kwPath = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => keyword(segment)), true);
    const opts = c.PersistentArrayMap.fromArray([keyword("keywordize-keys"), true], true);
    const rows = c.js__GT_clj(
      [
        {
          name: "test",
          master: String(owner).toLowerCase(),
          subAccountUser: String(sub).toLowerCase(),
          clearinghouseState: {
            marginSummary: {
              accountValue: "300.61686499"
            }
          },
          spotState: {
            balances: [
              {
                coin: "USDC",
                total: "0"
              },
              {
                coin: "MEOW",
                token: "MEOW:0xdef",
                total: "0.02"
              }
            ]
          }
        }
      ],
      opts
    );
    const webdata2 = c.js__GT_clj(
      {
        clearinghouseState: {
          marginSummary: {
            accountValue: "300.61686499"
          }
        }
      },
      opts
    );
    const spot = c.js__GT_clj(
      {
        "clearinghouse-state": {
          balances: [
            {
              coin: "USDC",
              total: "358.56"
            },
            {
              coin: "USDH",
              token: "USDH:0xabc",
              total: "8.28"
            },
            {
              coin: "STAR",
              token: "STAR:0x456",
              total: "0"
            }
          ]
        }
      },
      opts
    );
    const spotBalances = c.js__GT_clj(
      [
        {
          coin: "USDC",
          total: "358.56"
        },
        {
          coin: "USDH",
          token: "USDH:0xabc",
          total: "8.28"
        },
        {
          coin: "STAR",
          token: "STAR:0x456",
          total: "0"
        }
      ],
      opts
    );

    let nextState = c.deref(store);
    nextState = c.assoc_in(nextState, kwPath("wallet", "address"), owner);
    nextState = c.assoc_in(nextState, kwPath("account", "mode"), keyword(accountMode));
    nextState = c.assoc_in(nextState, kwPath("account-context", "subaccounts", "rows"), rows);
    nextState = c.assoc_in(nextState, kwPath("account-context", "subaccounts", "status"), keyword("loaded"));
    nextState = c.assoc_in(
      nextState,
      kwPath("account-context", "subaccounts", "loaded-for-owner"),
      String(owner).toLowerCase()
    );
    nextState = c.assoc_in(nextState, kwPath("account-context", "subaccounts", "error"), null);
    nextState = c.assoc_in(nextState, kwPath("account-context", "subaccounts", "selected-address"), null);
    nextState = c.assoc_in(nextState, kwPath("account-context", "subaccounts", "selection-loaded?"), true);
    nextState = c.assoc_in(nextState, kwPath("account-context", "subaccounts", "transfer-amount"), "");
    nextState = c.assoc_in(
      nextState,
      kwPath("account-context", "subaccounts", "transfer-direction"),
      keyword("deposit")
    );
    nextState = c.assoc_in(
      nextState,
      kwPath("account-context", "subaccounts", "transfer-account"),
      keyword("trading")
    );
    nextState = c.assoc_in(
      nextState,
      kwPath("account-context", "subaccounts", "transfer-account-menu-open?"),
      false
    );
    nextState = c.assoc_in(nextState, kwPath("account-context", "subaccounts", "transfer-token"), "USDC");
    nextState = c.assoc_in(
      nextState,
      kwPath("account-context", "subaccounts", "transfer-token-menu-open?"),
      false
    );
    nextState = c.assoc_in(nextState, kwPath("webdata2"), webdata2);
    nextState = c.assoc_in(nextState, kwPath("spot"), spot);
    nextState = c.assoc_in(
      nextState,
      kwPath("spot", "clearinghouse-state", "balances"),
      spotBalances
    );

    c.reset_BANG_(store, nextState);
    const renderApp = globalThis.hyperopen?.app?.bootstrap?.render_app_BANG_;
    if (typeof renderApp === "function") {
      renderApp(c.deref(store));
    }
  }, { ownerAddress, subaccountAddress, accountMode: options.accountMode ?? "classic" });
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
  await seedAccountSurface(page, { accountMode: options.accountMode ?? "classic" });
}

async function interceptSubaccountsApi(page) {
  await page.route("https://api.hyperliquid.xyz/info", async (route) => {
    const payload = JSON.parse(route.request().postData() || "{}");
    if (payload?.type === "subAccounts") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          {
            name: "test",
            master: ownerAddress.toLowerCase(),
            subAccountUser: subaccountAddress.toLowerCase(),
            clearinghouseState: {
              marginSummary: {
                accountValue: "300.61686499"
              }
            },
            spotState: {
              balances: [
                {
                  coin: "USDC",
                  total: "0"
                },
                {
                  coin: "MEOW",
                  token: "MEOW:0xdef",
                  total: "0.02"
                }
              ]
            }
          }
        ])
      });
      return;
    }

    await route.continue();
  });
}

async function readTransferGeometry(page) {
  return page.evaluate((subaccountAddress) => {
    const row = document.querySelector(`[data-role="subaccounts-row-${subaccountAddress}"]`);
    const popover = document.querySelector(
      `[data-role="subaccounts-transfer-popover-${subaccountAddress}"]`
    );
    const amount = document.querySelector(
      `[data-role="subaccounts-transfer-amount-${subaccountAddress}"]`
    );

    if (!row || !popover || !amount) {
      throw new Error("Subaccount transfer geometry unavailable");
    }

    const rowRect = row.getBoundingClientRect();
    const popoverRect = popover.getBoundingClientRect();
    const amountRect = amount.getBoundingClientRect();
    const consoleNode = document.querySelector(`[data-role="subaccounts-console"]`);
    const direction = document.querySelector(
      `[data-role="subaccounts-transfer-direction-${subaccountAddress}"]`
    );
    const token = document.querySelector(
      `[data-role="subaccounts-transfer-token-${subaccountAddress}"]`
    );
    const readBorderWidths = (node) => {
      const styles = window.getComputedStyle(node);
      return {
        top: styles.borderTopWidth,
        right: styles.borderRightWidth,
        bottom: styles.borderBottomWidth,
        left: styles.borderLeftWidth
      };
    };
    const viewportWidth = window.innerWidth;

    return {
      popoverInsideRow: row.contains(popover),
      popoverInsideConsole: consoleNode ? consoleNode.contains(popover) : null,
      rowHeight: rowRect.height,
      rowBottom: rowRect.bottom,
      popoverWidth: popoverRect.width,
      popoverLeft: popoverRect.left,
      popoverRight: popoverRect.right,
      popoverTop: popoverRect.top,
      popoverBottom: popoverRect.bottom,
      amountHeight: amountRect.height,
      amountWidth: amountRect.width,
      directionBorders: readBorderWidths(direction),
      tokenBorders: readBorderWidths(token),
      amountBorders: readBorderWidths(amount),
      viewportWidth
    };
  }, subaccountAddress);
}

test("subaccounts transfer opens a compact send tokens popover @regression", async ({ page }) => {
  await interceptSubaccountsApi(page);

  for (const width of [375, 768, 1280, 1440]) {
    await page.setViewportSize({ width, height: 720 });
    await visitRoute(page, "/subAccounts");
    await seedSubaccountsState(page);

    const trigger = page.locator(`[data-role="subaccounts-transfer-${subaccountAddress}"]`);
    await expect(trigger).toBeVisible();
    await trigger.click();

    const popover = page.locator(
      `[data-role="subaccounts-transfer-popover-${subaccountAddress}"]`
    );
    await expect(popover).toBeVisible();
    await expect(popover).toContainText("Send Tokens");
    await expect(popover).toContainText("Transfer tokens between sub-account and master account.");
    await expect(page.locator(`[data-role="subaccounts-transfer-source-${subaccountAddress}"]`))
      .toContainText("Master Account");
    await expect(page.locator(`[data-role="subaccounts-transfer-destination-${subaccountAddress}"]`))
      .toContainText("test");
    await expect(page.locator(`[data-role="subaccounts-transfer-flow-arrow-${subaccountAddress}"]`))
      .toHaveText("->");
    await expect(page.locator(`[data-role="subaccounts-transfer-token-${subaccountAddress}"]`))
      .toContainText("USDC");
    await expect(page.locator(`[data-role="subaccounts-transfer-max-${subaccountAddress}"]`))
      .toContainText(/^MAX: [0-9,.]+(?:\.\d+)? USDC$/);

    const geometry = await readTransferGeometry(page);
    expect(geometry.popoverInsideRow).toBe(false);
    expect(geometry.popoverInsideConsole).toBe(false);
    expect(geometry.rowHeight).toBeLessThanOrEqual(96);
    expect(geometry.popoverWidth).toBeLessThanOrEqual(Math.min(width * 0.92, 520) + 2);
    expect(geometry.popoverLeft).toBeGreaterThanOrEqual(-1);
    expect(geometry.popoverRight).toBeLessThanOrEqual(geometry.viewportWidth + 1);
    expect(geometry.popoverTop).toBeGreaterThanOrEqual(-1);
    expect(geometry.popoverTop).toBeGreaterThanOrEqual(geometry.rowBottom);
    expect(geometry.amountHeight).toBeGreaterThanOrEqual(20);
    expect(geometry.amountWidth).toBeGreaterThan(80);
    expect(Object.values(geometry.directionBorders)).toEqual(["0px", "0px", "0px", "0px"]);
    expect(Object.values(geometry.tokenBorders)).toEqual(["0px", "0px", "0px", "0px"]);
    expect(Object.values(geometry.amountBorders)).toEqual(["0px", "0px", "0px", "0px"]);

    await page.locator(`[data-role="subaccounts-transfer-toggle-direction-${subaccountAddress}"]`).click();
    await expect(page.locator(`[data-role="subaccounts-transfer-source-${subaccountAddress}"]`))
      .toContainText("test");
    await expect(page.locator(`[data-role="subaccounts-transfer-destination-${subaccountAddress}"]`))
      .toContainText("Master Account");
    await expect(page.locator(`[data-role="subaccounts-transfer-flow-arrow-${subaccountAddress}"]`))
      .toHaveText("->");

    const accountTrigger = page.locator(`[data-role="subaccounts-transfer-direction-${subaccountAddress}"]`);
    await expect(accountTrigger).toHaveAttribute("aria-haspopup", "listbox");
    await accountTrigger.click();
    const accountMenu = page.locator(`[data-role="subaccounts-transfer-account-menu-${subaccountAddress}"]`);
    await expect(accountMenu).toBeVisible();
    await expect(accountMenu).toContainText("Trading Account");
    await expect(accountMenu).toContainText("Spot Account");
    await page.locator(`[data-role="subaccounts-transfer-account-option-${subaccountAddress}-spot"]`).click();
    await expect(accountTrigger).toContainText("Spot Account");
    await expect(accountTrigger).toHaveAttribute("aria-expanded", "false");

    await page.locator(`[data-role="subaccounts-transfer-token-${subaccountAddress}"]`).click();
    const tokenMenu = page.locator(`[data-role="subaccounts-transfer-token-menu-${subaccountAddress}"]`);
    await expect(tokenMenu).toBeVisible();
    await expect(tokenMenu).toContainText("MEOW");
    await expect(tokenMenu).toContainText("0.02");
    await page.locator(`[data-role="subaccounts-transfer-token-option-${subaccountAddress}-MEOW:0xdef"]`).click();
    await expect(page.locator(`[data-role="subaccounts-transfer-token-${subaccountAddress}"]`))
      .toContainText("MEOW");
    await expect(page.locator(`[data-role="subaccounts-transfer-max-${subaccountAddress}"]`))
      .toContainText(/^MAX: [0-9,.]+(?:\.\d+)? MEOW$/);

    await page.locator(`[data-role="subaccounts-transfer-close-${subaccountAddress}"]`).click();
    await expect(popover).toHaveCount(0);
  }
});

test("subaccounts transfer uses trading-only controls for unified accounts @regression", async ({ page }) => {
  await interceptSubaccountsApi(page);

  await page.setViewportSize({ width: 768, height: 720 });
  await visitRoute(page, "/subAccounts");
  await seedSubaccountsState(page, { accountMode: "unified" });

  await page.locator(`[data-role="subaccounts-transfer-${subaccountAddress}"]`).click();
  await seedAccountSurface(page, { accountMode: "unified" });

  const accountTrigger = page.locator(`[data-role="subaccounts-transfer-direction-${subaccountAddress}"]`);
  await expect(accountTrigger).toBeVisible();
  await expect(accountTrigger).toContainText("Trading Account");
  await expect(page.locator(`[data-role="subaccounts-transfer-max-${subaccountAddress}"]`))
    .toContainText("MAX: 358.56 USDC");

  await accountTrigger.click();

  const accountMenu = page.locator(`[data-role="subaccounts-transfer-account-menu-${subaccountAddress}"]`);
  await expect(accountMenu).toBeVisible();
  await expect(accountMenu).toContainText("Trading Account");
  await expect(page.locator(`[data-role="subaccounts-transfer-account-option-${subaccountAddress}-spot"]`))
    .toHaveCount(0);

  await page.locator(`[data-role="subaccounts-transfer-token-${subaccountAddress}"]`).click();
  const tokenMenu = page.locator(`[data-role="subaccounts-transfer-token-menu-${subaccountAddress}"]`);
  await expect(tokenMenu).toBeVisible();
  await expect(tokenMenu).toContainText("USDC");
  await expect(page.locator(`[data-role="subaccounts-transfer-token-option-${subaccountAddress}-USDH:0xabc"]`))
    .toHaveCount(0);
});

test("unified subaccounts transfer submits sendAsset instead of subAccountTransfer @regression", async ({
  page
}) => {
  const signature = `0x${"a".repeat(64)}${"b".repeat(64)}1c`;

  await interceptSubaccountsApi(page);
  await visitRoute(page, "/subAccounts");
  await debugCall(page, "installWalletSimulator", {
    accounts: [ownerAddress],
    requestAccounts: [ownerAddress],
    chainId: "0xa4b1",
    typedDataSignature: signature
  });
  await debugCall(page, "installExchangeSimulator", {
    signedActions: {
      sendAsset: {
        responses: [{ status: "ok", response: { type: "default" } }]
      },
      subAccountTransfer: {
        responses: [
          { status: "err", response: "Action disabled when unified account is active" }
        ]
      }
    },
    info: {
      subAccounts: {
        responses: [
          [
            {
              name: "test",
              master: ownerAddress.toLowerCase(),
              subAccountUser: subaccountAddress.toLowerCase(),
              clearinghouseState: { marginSummary: { accountValue: "0" } },
              spotState: { balances: [] }
            }
          ]
        ]
      }
    }
  });
  await seedSubaccountsState(page, { accountMode: "unified" });

  await page.locator(`[data-role="subaccounts-transfer-${subaccountAddress}"]`).click();
  await seedAccountSurface(page, { accountMode: "unified" });
  await page.locator(`[data-role="subaccounts-transfer-amount-${subaccountAddress}"]`).fill("1.23");
  await page.locator(`[data-role="subaccounts-transfer-submit-${subaccountAddress}"]`).click();
  await waitForIdle(page, { quietMs: 350, timeoutMs: 8_000, pollMs: 50 });

  await expect
    .poll(
      async () => {
        const snapshot = await debugCall(page, "exchangeSimulatorSnapshot");
        return (snapshot?.calls ?? [])
          .map((call) => call?.request)
          .filter((request) => request?.action?.type);
      },
      { timeout: 10_000 }
    )
    .toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          action: expect.objectContaining({
            type: "sendAsset",
            destination: subaccountAddress,
            sourceDex: "spot",
            destinationDex: "spot",
            token: "USDC:0x6d1e7cde53ba9467b783cb7c530ce054",
            amount: "1.23",
            fromSubAccount: ""
          })
        })
      ])
    );

  const snapshot = await debugCall(page, "exchangeSimulatorSnapshot");
  const submittedRequests = (snapshot?.calls ?? [])
    .map((call) => call?.request)
    .filter((request) => request?.action?.type);
  const submittedTypes = submittedRequests.map((request) => request.action.type);
  expect(submittedTypes).not.toContain("subAccountTransfer");
  expect(submittedRequests.some((request) => Object.hasOwn(request, "vaultAddress"))).toBe(false);
});
