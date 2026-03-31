import { expect, test } from "@playwright/test";
import { debugCall, dispatch, expectOracle, visitRoute, waitForIdle } from "../support/hyperopen.mjs";

test("staking route defaults to disconnected gating when no wallet is connected @regression", async ({ page }) => {
  await visitRoute(page, "/staking");

  await expect(page.locator("[data-parity-id='staking-root']")).toBeVisible();
  await expect(page.locator("[data-role='staking-establish-connection']")).toBeVisible();
  await expect(page.locator("[data-role='staking-action-transfer-button']")).toHaveCount(0);
  await expect(page.locator("[data-role='staking-action-unstake-button']")).toHaveCount(0);
  await expect(page.locator("[data-role='staking-action-stake-button']")).toHaveCount(0);
});

test("staking timeframe menu opens and selects a deterministic option via debug actions @regression", async ({ page }) => {
  await visitRoute(page, "/staking");

  const trigger = page.locator("[data-role='staking-timeframe-menu-trigger']");
  const menu = page.locator("[data-role='staking-timeframe-menu']");
  const dayOption = page.locator("[data-role='staking-timeframe-option-day']");

  await expect(trigger).toContainText("7D");
  await expect(trigger).not.toHaveAttribute("aria-expanded", "true");

  await dispatch(page, [":actions/toggle-staking-validator-timeframe-menu"]);
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });

  await expect(trigger).toHaveAttribute("aria-expanded", "true");
  await expect(menu).toBeVisible();
  await expect(dayOption).toBeVisible();

  await dispatch(page, [":actions/set-staking-validator-timeframe", ":day"]);
  await waitForIdle(page, { quietMs: 150, timeoutMs: 4_000, pollMs: 50 });

  await expect(trigger).toContainText("1D");
  await expect(trigger).not.toHaveAttribute("aria-expanded", "true");
  await expect(menu).not.toHaveClass(/opacity-100/);
});

test("loaded staking account data renders from simulated account requests @regression", async ({ page }) => {
  const address = "0x1111111111111111111111111111111111111111";

  await visitRoute(page, "/trade");

  await debugCall(page, "installWalletSimulator", {
    accounts: [address],
    requestAccounts: [address],
    chainId: "0xa4b1"
  });
  await debugCall(page, "setWalletConnectedHandlerMode", "suppress");
  await debugCall(page, "installAccountRequestSimulator", {
    defaultUser: address,
    validatorSummaries: [
      {
        validator: "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        name: "Alpha",
        description: "Alpha validator",
        stake: 100,
        isActive: true,
        isJailed: false,
        commission: 0.01,
        stats: {
          week: {
            uptimeFraction: 0.99,
            predictedApr: 0.14,
            sampleCount: 7
          }
        }
      }
    ],
    delegatorSummary: {
      delegated: 12,
      undelegated: 3
    },
    delegations: [
      {
        validator: "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        amount: 12,
        lockedUntilTimestamp: 1700000000000
      }
    ],
    delegatorRewards: [
      {
        time: 1700000000000,
        source: "alpha",
        totalAmount: 1.5
      }
    ],
    delegatorHistory: [
      {
        time: 1700000001000,
        hash: "0xdeadbeef",
        delta: {
          cDeposit: {
            amount: 2.5
          }
        }
      }
    ],
    spotClearinghouseState: {
      balances: [
        {
          coin: "HYPE",
          total: "3.25"
        }
      ]
    }
  });
  await dispatch(page, [":actions/navigate", "/staking", { "replace?": true }]);
  await waitForIdle(page, { quietMs: 250, timeoutMs: 5_000, pollMs: 50 });
  await expectOracle(page, "staking", {
    route: "/staking",
    connected: false,
    activeTab: "validator-performance",
    validatorCount: 1,
    delegationCount: 0,
    rewardCount: 0,
    historyCount: 0,
    connectButtonPresent: true,
    transferButtonPresent: false,
    unstakeButtonPresent: false,
    stakeButtonPresent: false,
    balancePanelPresent: true,
    validatorTablePresent: true,
    validatorRowCount: 1,
    loading: {
      validatorSummaries: false,
      delegatorSummary: false,
      delegations: false,
      rewards: false,
      history: false
    }
  });

  await dispatch(page, [":actions/connect-wallet"]);
  await waitForIdle(page, { quietMs: 250, timeoutMs: 5_000, pollMs: 50 });

  await expectOracle(page, "wallet-status", {
    connected: true,
    address
  });
  await expectOracle(page, "staking", {
    route: "/staking",
    connected: true,
    address,
    activeTab: "validator-performance",
    validatorCount: 1,
    delegationCount: 1,
    rewardCount: 1,
    historyCount: 1,
    connectButtonPresent: false,
    transferButtonPresent: true,
    unstakeButtonPresent: true,
    stakeButtonPresent: true,
    balancePanelPresent: true,
    validatorTablePresent: true,
    validatorRowCount: 1,
    loading: {
      validatorSummaries: false,
      delegatorSummary: false,
      delegations: false,
      rewards: false,
      history: false
    }
  });

  await expect(page.locator("[data-parity-id='staking-root']")).toBeVisible();
  await expect(page.locator("[data-role='staking-validator-row']")).toHaveCount(1);

  await dispatch(page, [":actions/set-staking-active-tab", ":staking-reward-history"]);
  await waitForIdle(page, { quietMs: 200, timeoutMs: 4_000, pollMs: 50 });
  await expectOracle(page, "staking", {
    activeTab: "staking-reward-history",
    rewardCount: 1,
    historyCount: 1,
    loading: {
      rewards: false,
      history: false
    }
  });

  await expect(page.getByText("alpha")).toBeVisible();
});
