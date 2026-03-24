import { expect, test } from "@playwright/test";
import { expectOracle, mobileViewport, visitRoute } from "../support/hyperopen.mjs";

const routeCases = [
  { name: "trade", route: "/trade", parityId: "trade-root" },
  { name: "portfolio", route: "/portfolio", parityId: "portfolio-root" },
  {
    name: "trader-portfolio",
    route: "/portfolio/trader/0x3333333333333333333333333333333333333333",
    parityId: "portfolio-root"
  },
  { name: "leaderboard", route: "/leaderboard", parityId: "leaderboard-root" },
  { name: "vaults", route: "/vaults", parityId: "vaults-root" }
];

test.describe("main route smoke @smoke", () => {
  for (const routeCase of routeCases) {
    test(`${routeCase.name} desktop root renders @smoke`, async ({ page }) => {
      await visitRoute(page, routeCase.route);
      await expectOracle(
        page,
        "parity-element",
        { present: true },
        { args: { parityId: routeCase.parityId } }
      );
    });
  }
});

test.describe("main route smoke mobile @smoke", () => {
  test.use(mobileViewport);

  for (const routeCase of routeCases) {
    test(`${routeCase.name} mobile root renders @smoke`, async ({ page }) => {
      await visitRoute(page, routeCase.route);
      await expectOracle(
        page,
        "parity-element",
        { present: true },
        { args: { parityId: routeCase.parityId } }
      );
    });
  }
});

test("leaderboard preferences persist across reload via IndexedDB @smoke", async ({ page }) => {
  await visitRoute(page, "/leaderboard");

  const allTimeButton = page.locator("[data-role='leaderboard-timeframes'] button", { hasText: "All Time" });
  const volumeHeader = page.getByRole("button", { name: "Volume" });
  const pageSizeButton = page.locator("#leaderboard-page-size");

  await allTimeButton.click();
  await volumeHeader.click();
  await pageSizeButton.click();
  await page.getByRole("option", { name: "25" }).click();

  await expect(pageSizeButton).toContainText("25");
  await page.reload();
  await expectOracle(
    page,
    "parity-element",
    { present: true },
    { args: { parityId: "leaderboard-root" } }
  );

  await expect(page.locator("#leaderboard-page-size")).toContainText("25");
  await expect(page.locator("[data-role='leaderboard-timeframes'] button", { hasText: "All Time" }))
    .toHaveClass(/text-\[#97fce4\]/);
  await expect(page.locator("button:has-text('Volume') svg")).toHaveClass(/rotate-0/);
});
