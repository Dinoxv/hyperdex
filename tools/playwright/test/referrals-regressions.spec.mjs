import { expect, test } from "@playwright/test";
import { visitRoute, waitForIdle } from "../support/hyperopen.mjs";

const ownerAddress = "0x1234567890abcdef1234567890abcdef12345678";

async function interceptReferralApi(page, raw) {
  await page.route("https://api.hyperliquid.xyz/info", async (route) => {
    const payload = JSON.parse(route.request().postData() || "{}");
    if (payload?.type === "referral") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(raw)
      });
      return;
    }

    await route.continue();
  });
}

async function seedReferralsState(page, options = {}) {
  await page.evaluate(({ owner, referralState }) => {
    const c = globalThis.cljs?.core;
    const store = globalThis.hyperopen?.system?.store;

    if (!c || !store) {
      throw new Error("Hyperopen store or cljs core unavailable");
    }

    const keyword = c.keyword;
    const kwPath = (...segments) =>
      c.PersistentVector.fromArray(segments.map((segment) => keyword(segment)), true);
    const opts = c.PersistentArrayMap.fromArray([keyword("keywordize-keys"), true], true);
    const raw = c.js__GT_clj(referralState.raw, opts);
    const ui = c.js__GT_clj(referralState.ui, opts);

    let nextState = c.deref(store);
    nextState = c.assoc_in(nextState, kwPath("wallet", "address"), owner);
    nextState = c.assoc_in(nextState, kwPath("wallet", "connected?"), true);
    nextState = c.assoc_in(nextState, kwPath("referrals", "raw"), raw);
    nextState = c.assoc_in(nextState, kwPath("referrals", "loading?"), false);
    nextState = c.assoc_in(nextState, kwPath("referrals", "error"), null);
    nextState = c.assoc_in(nextState, kwPath("referrals-ui"), ui);
    c.reset_BANG_(store, nextState);

    const renderApp = globalThis.hyperopen?.app?.bootstrap?.render_app_BANG_;
    if (typeof renderApp === "function") {
      renderApp(c.deref(store));
    }
  }, {
    owner: ownerAddress,
    referralState: {
      raw: options.raw ?? {
        tokenToState: {
          USDC: {
            unclaimedRewards: "3.5",
            claimedRewards: "1.5"
          }
        },
        referrerState: {
          stage: options.stage ?? "needToCreateCode",
          data: options.data ?? {}
        }
      },
      ui: options.ui ?? {
        "active-tab": "referrals",
        "active-modal": null,
        "last-error": null,
        "submitting?": null,
        form: {
          code: "",
          "new-code": ""
        }
      }
    }
  });
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
}

test("referrals route opens modals, validates code, and switches tabs @regression", async ({ page }) => {
  await interceptReferralApi(page, {
    referrerState: {
      stage: "needToCreateCode",
      data: {}
    }
  });
  await visitRoute(page, "/referrals");
  await seedReferralsState(page);

  await expect(page.locator("[data-parity-id='referrals-root']")).toBeVisible();
  await expect(page.locator("[data-role='referrals-open-enter-code']")).toBeVisible();
  await expect(page.locator("[data-role='referrals-open-create-code']")).toBeVisible();

  await page.locator("[data-role='referrals-open-enter-code']").click();
  await expect(page.locator("[data-role='referrals-modal']")).toBeVisible();
  await expect(page.locator("[data-role='referrals-modal-title']")).toHaveText("Enter Referral Code");
  await page.locator("[data-role='referrals-modal-submit']").click();
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
  await expect(page.locator("[data-role='referrals-modal-error']")).toContainText("Enter a valid referral code.");

  await page.locator("[data-role='referrals-modal-code-input']").fill("friend1");
  await expect(page.locator("[data-role='referrals-modal-normalized-code']")).toHaveText("FRIEND1");
  await page.locator("[data-role='referrals-modal-close']").click();
  await expect(page.locator("[data-role='referrals-modal']")).toHaveCount(0);

  await page.locator("[data-role='referrals-open-create-code']").click();
  await expect(page.locator("[data-role='referrals-modal-title']")).toHaveText("Create Referral Code");
  await page.locator("[data-role='referrals-modal-new-code-input']").fill("mycode");
  await expect(page.locator("[data-role='referrals-modal-new-code-input']")).toHaveValue("mycode");
  await page.locator("[data-role='referrals-modal-close']").click();

  await page.locator("[data-role='referrals-tab-legacy-reward-history']").click();
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });
  await expect(page.locator("[data-role='referrals-legacy-empty']")).toHaveText("No rewards earned");
});

test("referrals ready state exposes share and claim modal flows @regression", async ({ page }) => {
  const readyPayload = {
    referrerState: {
      stage: "ready",
      data: {
        code: "MYCODE",
        nReferrals: 6,
        tokenToState: {
          USDC: {
            unclaimedRewards: "0.91",
            claimedRewards: "208.65"
          }
        },
        referralStates: [
          {
            user: "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd",
            cumVlm: "6226785.1799999997",
            cumRewardedFeesSinceReferred: "4428.91413651",
            cumFeesRewardedToReferrer: "187.35791924",
            timeJoined: 1761012412763
          }
        ]
      }
    }
  };
  await interceptReferralApi(page, readyPayload);
  await visitRoute(page, "/referrals");
  await seedReferralsState(page, {
    raw: readyPayload,
    stage: "ready",
    data: {
      code: "MYCODE",
      nReferrals: 2,
      referralStates: [
        {
          user: "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd",
          cumVlm: "6226785.1799999997",
          cumRewardedFeesSinceReferred: "4428.91413651",
          cumFeesRewardedToReferrer: "187.35791924",
          timeJoined: 1761012412763
        }
      ]
    }
  });

  await expect(page.locator("[data-role='referrals-own-code']")).toHaveText("MYCODE");
  await expect(page.locator("[data-role='referrals-join-link']")).toHaveText("/join/MYCODE");
  await expect(page.locator("[data-role='referrals-stat-traders']")).toContainText("6");
  await expect(page.locator("[data-role='referrals-stat-rewards']")).toContainText("$209.56");
  await expect(page.locator("[data-role='referrals-stat-claimable']")).toContainText("$0.91");
  await expect(page.locator("[data-role='referrals-rewards-panel']")).toHaveCount(0);
  const rowCells = page.locator("[data-role='referrals-row'] > span");
  await expect(rowCells.nth(0)).toHaveText(/0xabcd.*abcd/);
  await expect(rowCells.nth(1)).toHaveText("10/20/2025 - 22:06:52");
  await expect(rowCells.nth(2)).toHaveText("$6,226,785.18");
  await expect(rowCells.nth(3)).toHaveText("$4,428.91");
  await expect(rowCells.nth(4)).toHaveText("$187.36");

  const claimButtonBox = await page.locator("[data-role='referrals-open-claim-rewards']").boundingBox();
  const tableBox = await page.locator("[data-role='referrals-table-panel']").boundingBox();
  expect(claimButtonBox).not.toBeNull();
  expect(tableBox).not.toBeNull();
  expect(claimButtonBox.y).toBeLessThan(tableBox.y);

  await page.locator("[data-role='referrals-open-share-code']").click();
  await expect(page.locator("[data-role='referrals-modal-title']")).toHaveText("Share Referral Code");
  await expect(page.locator("[data-role='referrals-modal-join-link']")).toHaveText("/join/MYCODE");
  await page.locator("[data-role='referrals-modal-submit']").click();
  await expect(page.locator("[data-role='referrals-modal']")).toHaveCount(0);

  await page.locator("[data-role='referrals-open-claim-rewards']").click();
  await expect(page.locator("[data-role='referrals-modal-title']")).toHaveText("Claim Rewards");
  await expect(page.locator("[data-role='referrals-modal-claim-total']")).toHaveText("$0.91");
  await expect(page.locator("[data-role='referrals-modal-claim-row']").first()).toContainText("USDC");
});

test("join route preserves normalized code and requires confirmation @regression", async ({ page }) => {
  await visitRoute(page, "/join/friend1");

  await expect(page.locator("[data-parity-id='referrals-root']")).toBeVisible();
  await expect(page.locator("[data-role='referrals-modal']")).toBeVisible();
  await expect(page.locator("[data-role='referrals-modal-title']")).toHaveText("Confirm Referral Code");
  await expect(page.locator("[data-role='referrals-modal-normalized-code']")).toHaveText("FRIEND1");
  await expect(page.locator("[data-role='referrals-modal-code-input']")).toHaveValue("FRIEND1");
  await expect(page.locator("[data-role='referrals-modal-submit']")).toBeDisabled();
});

test("referrals layout contains core modal controls at review widths @regression", async ({ page }) => {
  await interceptReferralApi(page, {
    referrerState: {
      stage: "needToCreateCode",
      data: {}
    }
  });
  for (const width of [375, 768, 1280, 1440]) {
    await page.setViewportSize({ width, height: 760 });
    await visitRoute(page, "/referrals");
    await seedReferralsState(page);

    await expect(page.locator("[data-parity-id='referrals-root']")).toBeVisible();
    await page.locator("[data-role='referrals-open-enter-code']").click();
    await expect(page.locator("[data-role='referrals-modal']")).toBeVisible();

    const modalBox = await page.locator("[data-role='referrals-modal']").boundingBox();
    expect(modalBox).not.toBeNull();
    expect(modalBox.x).toBeGreaterThanOrEqual(0);
    expect(modalBox.x + modalBox.width).toBeLessThanOrEqual(width);
    await expect(page.locator("[data-role='referrals-modal-code-input']")).toBeVisible();
    await expect(page.locator("[data-role='referrals-modal-submit']")).toBeVisible();
    await page.locator("[data-role='referrals-modal-close']").click();
    await expect(page.locator("[data-role='referrals-modal']")).toHaveCount(0);
  }
});
