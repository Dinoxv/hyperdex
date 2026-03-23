import { test } from "@playwright/test";
import {
  dispatch,
  dispatchMany,
  expectOracle,
  mobileViewport,
  oracle,
  sourceRectForLocator,
  visitRoute,
  waitForIdle
} from "../support/hyperopen.mjs";

const spectateRoute =
  "/trade?spectate=0x162cc7c861ebd0c06b3d72319201150482518185";

test.describe("mobile browser regressions @mobile", () => {
  test.use(mobileViewport);

  test("account surface positions tab stays reachable on mobile @regression", async ({ page }) => {
    await visitRoute(page, spectateRoute);

    await dispatchMany(page, [
      [":actions/select-trade-mobile-surface", ":account"],
      [":actions/select-account-info-tab", ":positions"]
    ]);
    await waitForIdle(page, { quietMs: 200, timeoutMs: 4_000, pollMs: 50 });
    await expectOracle(page, "account-surface", {
      mobileSurface: "account",
      selectedTab: "positions",
      mobileAccountPanelPresent: true
    });
  });

  test("position margin opens as a mobile sheet @regression", async ({ page }) => {
    await visitRoute(page, spectateRoute);

    await dispatchMany(page, [
      [":actions/select-trade-mobile-surface", ":chart"],
      [":actions/select-account-info-tab", ":positions"]
    ]);
    await waitForIdle(page, { quietMs: 200, timeoutMs: 4_000, pollMs: 50 });
    await expectOracle(
      page,
      "first-position",
      { present: true },
      { timeoutMs: 8_000 }
    );

    const firstPosition = await oracle(page, "first-position");
    const sourceRect = await sourceRectForLocator(
      page,
      page.locator("[data-role^='mobile-position-card-']").first()
    );

    await dispatch(page, [
      ":actions/open-position-margin-modal",
      firstPosition.positionData,
      sourceRect
    ]);
    await waitForIdle(page, { quietMs: 200, timeoutMs: 4_000, pollMs: 50 });
    await expectOracle(
      page,
      "position-overlay",
      {
        open: true,
        presentationMode: "mobile-sheet"
      },
      { args: { surface: "margin" } }
    );
  });
});
