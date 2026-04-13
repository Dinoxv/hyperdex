(ns hyperopen.startup.route-refresh
  (:require [hyperopen.api-wallets.actions :as api-wallets-actions]
            [hyperopen.funding-comparison.actions :as funding-comparison-actions]
            [hyperopen.leaderboard.actions :as leaderboard-actions]
            [hyperopen.portfolio.routes :as portfolio-routes]
            [hyperopen.router :as router]
            [hyperopen.staking.actions :as staking-actions]
            [hyperopen.vaults.infrastructure.routes :as vault-routes]))

(defn current-route-path
  [state]
  (router/normalize-path (or (get-in state [:router :path])
                             "/trade")))

(defn current-route-refresh-effects
  [state new-address]
  (let [route (current-route-path state)]
    (cond-> (cond
              (leaderboard-actions/leaderboard-route? route)
              [[:actions/load-leaderboard-route route]]

              (vault-routes/vault-route? route)
              [[:actions/load-vault-route route]]

              (funding-comparison-actions/funding-comparison-route? route)
              [[:actions/load-funding-comparison-route route]]

              (staking-actions/staking-route? route)
              [[:actions/load-staking-route route]]

              (api-wallets-actions/api-wallet-route? route)
              [[:actions/load-api-wallet-route route]]

              :else [])
      (and new-address
           (portfolio-routes/portfolio-route? route))
      (conj [:actions/select-portfolio-chart-tab
             (get-in state [:portfolio-ui :chart-tab])]))))
