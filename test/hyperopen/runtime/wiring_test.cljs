(ns hyperopen.runtime.wiring-test
  (:require [clojure.set :as set]
            [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.runtime-catalog :as optimizer-runtime-catalog]
            [hyperopen.runtime.action-adapters :as action-adapters]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.schema.runtime-registration-catalog :as runtime-registration-catalog]
            [hyperopen.runtime.wiring :as wiring]))

(defn- flatten-leaf-keys
  [node]
  (reduce-kv (fn [acc k v]
               (if (map? v)
                 (into acc (flatten-leaf-keys v))
                 (conj acc k)))
             #{}
             (or node {})))

(deftest runtime-effect-deps-uses-extracted-effect-adapter-overrides-test
  (let [deps (wiring/runtime-effect-deps)
        optimizer-effect-deps (:portfolio-optimizer
                               (optimizer-runtime-catalog/effect-deps nil))]
    (is (identical? effect-adapters/save
                    (get-in deps [:storage :save])))
    (is (identical? effect-adapters/persist-leaderboard-preferences-effect
                    (get-in deps [:storage :persist-leaderboard-preferences])))
    (is (identical? effect-adapters/sync-asset-selector-active-ctx-subscriptions
                    (get-in deps [:asset-selector :sync-asset-selector-active-ctx-subscriptions])))
    (is (identical? effect-adapters/load-trading-indicators-module-effect
                    (get-in deps [:navigation :load-trading-indicators-module])))
    (is (fn? (get-in deps [:navigation :load-surface-module])))
    (is (identical? effect-adapters/replace-shareable-route-query
                    (get-in deps [:navigation :replace-shareable-route-query])))
    (is (identical? effect-adapters/fetch-candle-snapshot
                    (get-in deps [:websocket :fetch-candle-snapshot])))
    (is (identical? effect-adapters/ws-reset-subscriptions
                    (get-in deps [:diagnostics :ws-reset-subscriptions])))
    (is (identical? effect-adapters/api-fetch-predicted-fundings-effect
                    (get-in deps [:api :api-fetch-predicted-fundings])))
    (is (identical? effect-adapters/api-fetch-leaderboard-effect
                    (get-in deps [:api :api-fetch-leaderboard])))
    (is (identical? effect-adapters/api-fetch-vault-index-effect
                    (get-in deps [:api :api-fetch-vault-index])))
    (is (identical? effect-adapters/api-fetch-vault-index-with-cache-effect
                    (get-in deps [:api :api-fetch-vault-index-with-cache])))
    (is (identical? effect-adapters/api-fetch-vault-ledger-updates-effect
                    (get-in deps [:api :api-fetch-vault-ledger-updates])))
    (is (identical? effect-adapters/api-fetch-staking-validator-summaries-effect
                    (get-in deps [:api :api-fetch-staking-validator-summaries])))
    (is (fn? (get-in deps [:portfolio-optimizer :run-portfolio-optimizer])))
    (is (fn? (get-in deps [:portfolio-optimizer :run-portfolio-optimizer-pipeline])))
    (is (identical? (:load-portfolio-optimizer-history optimizer-effect-deps)
                    (get-in deps [:portfolio-optimizer :load-portfolio-optimizer-history])))
    (is (identical? (:load-portfolio-optimizer-scenario-index optimizer-effect-deps)
                    (get-in deps [:portfolio-optimizer :load-portfolio-optimizer-scenario-index])))
    (is (identical? (:load-portfolio-optimizer-scenario optimizer-effect-deps)
                    (get-in deps [:portfolio-optimizer :load-portfolio-optimizer-scenario])))
    (is (identical? (:archive-portfolio-optimizer-scenario optimizer-effect-deps)
                    (get-in deps [:portfolio-optimizer :archive-portfolio-optimizer-scenario])))
    (is (identical? (:duplicate-portfolio-optimizer-scenario optimizer-effect-deps)
                    (get-in deps [:portfolio-optimizer :duplicate-portfolio-optimizer-scenario])))
    (is (identical? (:save-portfolio-optimizer-scenario optimizer-effect-deps)
                    (get-in deps [:portfolio-optimizer :save-portfolio-optimizer-scenario])))
    (is (identical? (:execute-portfolio-optimizer-plan optimizer-effect-deps)
                    (get-in deps [:portfolio-optimizer :execute-portfolio-optimizer-plan])))
    (is (identical? (:refresh-portfolio-optimizer-tracking optimizer-effect-deps)
                    (get-in deps [:portfolio-optimizer :refresh-portfolio-optimizer-tracking])))
    (is (identical? (:enable-portfolio-optimizer-manual-tracking optimizer-effect-deps)
                    (get-in deps [:portfolio-optimizer :enable-portfolio-optimizer-manual-tracking])))
    (is (identical? action-adapters/enable-agent-trading
                    (get-in deps [:wallet :enable-agent-trading])))))

(deftest runtime-action-deps-uses-extracted-action-adapter-overrides-test
  (let [deps (wiring/runtime-action-deps)
        optimizer-action-deps (:portfolio-optimizer
                               (optimizer-runtime-catalog/action-deps))]
    (is (identical? action-adapters/init-websockets
                    (get-in deps [:core :init-websockets])))
    (is (identical? action-adapters/reconnect-websocket-action
                    (get-in deps [:core :reconnect-websocket-action])))
    (is (identical? action-adapters/refresh-asset-markets
                    (get-in deps [:asset-selector :refresh-asset-markets])))
    (is (identical? action-adapters/load-vault-route-action
                    (get-in deps [:vaults :load-vault-route])))
    (is (identical? action-adapters/load-funding-comparison-route-action
                    (get-in deps [:funding-comparison :load-funding-comparison-route])))
    (is (identical? action-adapters/load-leaderboard-route-action
                    (get-in deps [:leaderboard :load-leaderboard-route])))
    (is (identical? action-adapters/load-staking-route-action
                    (get-in deps [:staking :load-staking-route])))
    (is (identical? action-adapters/navigate
                    (get-in deps [:core :navigate])))
    (is (identical? (:run-portfolio-optimizer optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :run-portfolio-optimizer])))
    (is (identical? (:set-portfolio-optimizer-objective-kind optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :set-portfolio-optimizer-objective-kind])))
    (is (identical? (:set-portfolio-optimizer-objective-parameter optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :set-portfolio-optimizer-objective-parameter])))
    (is (identical? (:set-portfolio-optimizer-execution-assumption optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :set-portfolio-optimizer-execution-assumption])))
    (is (identical? (:set-portfolio-optimizer-instrument-filter optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :set-portfolio-optimizer-instrument-filter])))
    (is (identical? (:set-portfolio-optimizer-asset-override optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :set-portfolio-optimizer-asset-override])))
    (is (identical? (:set-portfolio-optimizer-universe-search-query optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :set-portfolio-optimizer-universe-search-query])))
    (is (identical? (:set-portfolio-optimizer-draft-add-asset-open optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer
                                  :set-portfolio-optimizer-draft-add-asset-open])))
    (is (identical? (:handle-portfolio-optimizer-universe-search-keydown optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer
                                  :handle-portfolio-optimizer-universe-search-keydown])))
    (is (identical? (:handle-portfolio-optimizer-draft-add-asset-keydown optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer
                                  :handle-portfolio-optimizer-draft-add-asset-keydown])))
    (is (identical? (:set-portfolio-optimizer-frontier-overlay-mode optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer
                                  :set-portfolio-optimizer-frontier-overlay-mode])))
    (is (identical? (:set-portfolio-optimizer-constrain-frontier optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer
                                  :set-portfolio-optimizer-constrain-frontier])))
    (is (identical? (:add-portfolio-optimizer-universe-instrument optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :add-portfolio-optimizer-universe-instrument])))
    (is (identical? (:add-portfolio-optimizer-universe-instrument-and-run optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer
                                  :add-portfolio-optimizer-universe-instrument-and-run])))
    (is (identical? (:toggle-portfolio-optimizer-universe-instrument-exclusion-and-run optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer
                                  :toggle-portfolio-optimizer-universe-instrument-exclusion-and-run])))
    (is (identical? (:remove-portfolio-optimizer-universe-instrument optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :remove-portfolio-optimizer-universe-instrument])))
    (is (identical? (:set-portfolio-optimizer-universe-from-current optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :set-portfolio-optimizer-universe-from-current])))
    (is (identical? (:load-portfolio-optimizer-history-from-draft optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :load-portfolio-optimizer-history-from-draft])))
    (is (identical? (:save-portfolio-optimizer-scenario-from-current optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :save-portfolio-optimizer-scenario-from-current])))
    (is (identical? (:load-portfolio-optimizer-route optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :load-portfolio-optimizer-route])))
    (is (identical? (:archive-portfolio-optimizer-scenario optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :archive-portfolio-optimizer-scenario])))
    (is (identical? (:duplicate-portfolio-optimizer-scenario optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :duplicate-portfolio-optimizer-scenario])))
    (is (identical? (:open-portfolio-optimizer-execution-modal optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :open-portfolio-optimizer-execution-modal])))
    (is (identical? (:close-portfolio-optimizer-execution-modal optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :close-portfolio-optimizer-execution-modal])))
    (is (identical? (:confirm-portfolio-optimizer-execution optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :confirm-portfolio-optimizer-execution])))
    (is (identical? (:refresh-portfolio-optimizer-tracking optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :refresh-portfolio-optimizer-tracking])))
    (is (identical? (:enable-portfolio-optimizer-manual-tracking optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :enable-portfolio-optimizer-manual-tracking])))
    (is (identical? (:run-portfolio-optimizer-from-draft optimizer-action-deps)
                    (get-in deps [:portfolio-optimizer :run-portfolio-optimizer-from-draft])))))

(deftest runtime-registration-deps-builds-effect-and-action-handlers-test
  (let [deps (wiring/runtime-registration-deps)
        optimizer-action-deps (:portfolio-optimizer
                               (optimizer-runtime-catalog/action-deps))
        optimizer-effect-deps (:portfolio-optimizer
                               (optimizer-runtime-catalog/effect-deps nil))]
    (is (fn? (:register-effects! deps)))
    (is (fn? (:register-actions! deps)))
    (is (fn? (:register-system-state! deps)))
    (is (fn? (:register-placeholders! deps)))
    (is (identical? action-adapters/navigate
                    (get-in deps [:action-handlers :navigate])))
    (is (identical? effect-adapters/save
                    (get-in deps [:effect-handlers :save])))
    (is (fn? (get-in deps [:effect-handlers :load-surface-module])))
    (is (identical? (:run-portfolio-optimizer optimizer-action-deps)
                    (get-in deps [:action-handlers :run-portfolio-optimizer])))
    (is (fn? (get-in deps [:effect-handlers :run-portfolio-optimizer])))
    (is (fn? (get-in deps [:effect-handlers :run-portfolio-optimizer-pipeline])))
    (is (identical? (:load-portfolio-optimizer-history optimizer-effect-deps)
                    (get-in deps [:effect-handlers :load-portfolio-optimizer-history])))
    (is (identical? (:load-portfolio-optimizer-scenario-index optimizer-effect-deps)
                    (get-in deps [:effect-handlers :load-portfolio-optimizer-scenario-index])))
    (is (identical? (:load-portfolio-optimizer-scenario optimizer-effect-deps)
                    (get-in deps [:effect-handlers :load-portfolio-optimizer-scenario])))
    (is (identical? (:archive-portfolio-optimizer-scenario optimizer-effect-deps)
                    (get-in deps [:effect-handlers :archive-portfolio-optimizer-scenario])))
    (is (identical? (:duplicate-portfolio-optimizer-scenario optimizer-effect-deps)
                    (get-in deps [:effect-handlers :duplicate-portfolio-optimizer-scenario])))
    (is (identical? (:save-portfolio-optimizer-scenario optimizer-effect-deps)
                    (get-in deps [:effect-handlers :save-portfolio-optimizer-scenario])))
    (is (identical? (:execute-portfolio-optimizer-plan optimizer-effect-deps)
                    (get-in deps [:effect-handlers :execute-portfolio-optimizer-plan])))
    (is (identical? (:refresh-portfolio-optimizer-tracking optimizer-effect-deps)
                    (get-in deps [:effect-handlers :refresh-portfolio-optimizer-tracking])))
    (is (identical? (:enable-portfolio-optimizer-manual-tracking optimizer-effect-deps)
                    (get-in deps [:effect-handlers :enable-portfolio-optimizer-manual-tracking])))))

(deftest runtime-action-deps-cover-catalog-handler-keys-test
  (let [action-deps (wiring/runtime-action-deps)
        available-handler-keys (flatten-leaf-keys action-deps)
        required-handler-keys (runtime-registration-catalog/action-handler-keys)
        missing (set/difference required-handler-keys available-handler-keys)]
    (is (empty? missing)
        (str "Runtime action deps missing catalog handler keys: "
             (pr-str missing)))))

(deftest runtime-effect-deps-cover-catalog-handler-keys-test
  (let [effect-deps (wiring/runtime-effect-deps)
        available-handler-keys (flatten-leaf-keys effect-deps)
        required-handler-keys (runtime-registration-catalog/effect-handler-keys)
        missing (set/difference required-handler-keys available-handler-keys)]
    (is (empty? missing)
        (str "Runtime effect deps missing catalog handler keys: "
             (pr-str missing)))))
