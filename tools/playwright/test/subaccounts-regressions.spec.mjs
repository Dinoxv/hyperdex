import { expect, test } from "@playwright/test";
import { visitRoute, waitForIdle } from "../support/hyperopen.mjs";

const ownerAddress = "0x1234567890abcdef1234567890abcdef12345678";
const subaccountAddress = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";

async function seedSubaccountsState(page) {
  await page.evaluate(({ ownerAddress: owner, subaccountAddress: sub }) => {
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
            }
          ]
        }
      },
      opts
    );

    let nextState = c.deref(store);
    nextState = c.assoc_in(nextState, kwPath("wallet", "address"), owner);
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
    nextState = c.assoc_in(nextState, kwPath("webdata2"), webdata2);
    nextState = c.assoc_in(nextState, kwPath("spot"), spot);

    c.reset_BANG_(store, nextState);
    const renderApp = globalThis.hyperopen?.app?.bootstrap?.render_app_BANG_;
    if (typeof renderApp === "function") {
      renderApp(c.deref(store));
    }
  }, { ownerAddress, subaccountAddress });
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
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
    const viewportWidth = window.innerWidth;

    return {
      rowHeight: rowRect.height,
      popoverWidth: popoverRect.width,
      popoverLeft: popoverRect.left,
      popoverRight: popoverRect.right,
      amountHeight: amountRect.height,
      amountWidth: amountRect.width,
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
    await expect(page.locator(`[data-role="subaccounts-transfer-token-${subaccountAddress}"]`))
      .toHaveValue("USDC");
    await expect(page.locator(`[data-role="subaccounts-transfer-max-${subaccountAddress}"]`))
      .toContainText(/^MAX: [0-9,.]+(?:\.\d+)? USDC$/);

    const geometry = await readTransferGeometry(page);
    expect(geometry.rowHeight).toBeLessThanOrEqual(96);
    expect(geometry.popoverWidth).toBeLessThanOrEqual(Math.min(width * 0.92, 520) + 2);
    expect(geometry.popoverLeft).toBeGreaterThanOrEqual(-1);
    expect(geometry.popoverRight).toBeLessThanOrEqual(geometry.viewportWidth + 1);
    expect(geometry.amountHeight).toBeGreaterThanOrEqual(20);
    expect(geometry.amountWidth).toBeGreaterThan(80);

    await page.locator(`[data-role="subaccounts-transfer-direction-${subaccountAddress}"]`)
      .selectOption("withdraw");
    await expect(page.locator(`[data-role="subaccounts-transfer-source-${subaccountAddress}"]`))
      .toContainText("test");
    await expect(page.locator(`[data-role="subaccounts-transfer-destination-${subaccountAddress}"]`))
      .toContainText("Master Account");

    await page.locator(`[data-role="subaccounts-transfer-close-${subaccountAddress}"]`).click();
    await expect(popover).toHaveCount(0);
  }
});
