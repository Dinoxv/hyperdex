(ns hyperopen.core-bootstrap.order-entry-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.core.compat :as core]
            [hyperopen.state.trading :as trading]
            [hyperopen.core-bootstrap.test-support.effect-extractors :as effect-extractors]))

(def extract-saved-order-form effect-extractors/extract-saved-order-form)
(def extract-saved-order-form-ui effect-extractors/extract-saved-order-form-ui)

(deftest select-order-entry-mode-market-emits-single-batched-projection-test
  (let [state {:order-form (assoc (trading/default-order-form)
                                  :type :stop-market)
               :order-form-ui {:entry-mode :pro
                               :pro-order-type-dropdown-open? true}}
        effects (core/select-order-entry-mode state :market)
        saved-form (extract-saved-order-form effects)
        saved-ui (extract-saved-order-form-ui effects)]
    (is (= 1 (count effects)))
    (is (= :effects/save-many (ffirst effects)))
    (is (map? saved-form))
    (is (= :market (:type saved-form)))
    (is (= :market (:entry-mode saved-ui)))
    (is (= false (:pro-order-type-dropdown-open? saved-ui)))))

(deftest select-order-entry-mode-limit-forces-limit-type-test
  (let [state {:order-form (assoc (trading/default-order-form)
                                  :type :stop-limit)
               :order-form-ui {:entry-mode :pro
                               :pro-order-type-dropdown-open? true}}
        effects (core/select-order-entry-mode state :limit)
        saved-form (extract-saved-order-form effects)
        saved-ui (extract-saved-order-form-ui effects)]
    (is (= 1 (count effects)))
    (is (= :effects/save-many (ffirst effects)))
    (is (map? saved-form))
    (is (= :limit (:type saved-form)))
    (is (= :limit (:entry-mode saved-ui)))
    (is (= false (:pro-order-type-dropdown-open? saved-ui)))))

(deftest select-order-entry-mode-pro-sets-pro-entry-and-normalized-pro-type-test
  (let [state {:order-form (assoc (trading/default-order-form) :type :limit)}
        effects (core/select-order-entry-mode state :pro)
        saved-form (extract-saved-order-form effects)
        saved-ui (extract-saved-order-form-ui effects)]
    (is (= 1 (count effects)))
    (is (= :effects/save-many (ffirst effects)))
    (is (map? saved-form))
    (is (= :stop-market (:type saved-form)))
    (is (= :pro (:entry-mode saved-ui)))))

(deftest select-pro-order-type-closes-dropdown-and-persists-pro-selection-test
  (let [state {:order-form (assoc (trading/default-order-form)
                                  :type :stop-market)
               :order-form-ui {:entry-mode :pro
                               :pro-order-type-dropdown-open? true}}
        effects (core/select-pro-order-type state :scale)
        saved-form (extract-saved-order-form effects)
        saved-ui (extract-saved-order-form-ui effects)]
    (is (= 1 (count effects)))
    (is (= :effects/save-many (ffirst effects)))
    (is (= :pro (:entry-mode saved-ui)))
    (is (= :scale (:type saved-form)))
    (is (= false (:pro-order-type-dropdown-open? saved-ui)))))

(deftest toggle-pro-order-type-dropdown-flips-open-flag-test
  (let [closed-state {:order-form (trading/default-order-form)
                      :order-form-ui {:pro-order-type-dropdown-open? false}}
        open-state {:order-form (trading/default-order-form)
                    :order-form-ui {:pro-order-type-dropdown-open? true}}
        closed-effects (core/toggle-pro-order-type-dropdown closed-state)
        open-effects (core/toggle-pro-order-type-dropdown open-state)]
    (is (= [[:effects/save-many [[[:order-form-ui :pro-order-type-dropdown-open?] true]]]]
           closed-effects))
    (is (= [[:effects/save-many [[[:order-form-ui :pro-order-type-dropdown-open?] false]]]]
           open-effects))))

(deftest close-pro-order-type-dropdown-forces-open-flag-false-test
  (let [state {:order-form (trading/default-order-form)
               :order-form-ui {:pro-order-type-dropdown-open? true}}
        effects (core/close-pro-order-type-dropdown state)]
    (is (= [[:effects/save-many [[[:order-form-ui :pro-order-type-dropdown-open?] false]]]]
           effects))))

(deftest handle-pro-order-type-dropdown-keydown-closes-only-on-escape-test
  (let [state {:order-form (trading/default-order-form)
               :order-form-ui {:pro-order-type-dropdown-open? true}}
        escape-effects (core/handle-pro-order-type-dropdown-keydown state "Escape")
        enter-effects (core/handle-pro-order-type-dropdown-keydown state "Enter")]
    (is (= [[:effects/save-many [[[:order-form-ui :pro-order-type-dropdown-open?] false]]]]
           escape-effects))
    (is (= [] enter-effects))))

(deftest toggle-order-tpsl-panel-noops-for-scale-test
  (let [state {:order-form (assoc (trading/default-order-form)
                                  :type :scale)
               :order-form-ui {:entry-mode :pro
                               :tpsl-panel-open? false}}
        effects (core/toggle-order-tpsl-panel state)]
    (is (= [] effects))))

(deftest set-order-size-percent-emits-single-batched-projection-and-no-network-effects-test
  (let [state {:active-asset "BTC"
               :active-market {:coin "BTC" :mark 100 :maxLeverage 40 :szDecimals 4}
               :orderbooks {"BTC" {:bids [{:px "99"}]
                                   :asks [{:px "101"}]}}
               :webdata2 {:clearinghouseState {:marginSummary {:accountValue "1000"
                                                               :totalMarginUsed "250"}}}
               :order-form (assoc (trading/default-order-form) :type :limit :price "100")}
        effects (core/set-order-size-percent state 25)
        saved-form (-> effects first second first second)]
    (is (= 1 (count effects)))
    (is (= :effects/save-many (ffirst effects)))
    (is (= 25 (:size-percent saved-form)))
    (is (not-any? #(= (first %) :effects/api-submit-order) effects))
    (is (not-any? #(= (first %) :effects/subscribe-orderbook) effects))))

(deftest set-order-size-display-preserves-user-entered-value-test
  (let [state {:active-asset "BTC"
               :active-market {:coin "BTC" :mark 100 :maxLeverage 40 :szDecimals 4}
               :orderbooks {"BTC" {:bids [{:px "99"}]
                                   :asks [{:px "101"}]}}
               :webdata2 {:clearinghouseState {:marginSummary {:accountValue "1000"
                                                               :totalMarginUsed "250"}}}
               :order-form (assoc (trading/default-order-form) :type :limit :price "")}
        effects (core/set-order-size-display state "202")
        saved-form (-> effects first second first second)
        saved-ui (extract-saved-order-form-ui effects)]
    (is (= 1 (count effects)))
    (is (= :effects/save-many (ffirst effects)))
    (is (= "202" (:size-display saved-ui)))
    (is (= "2" (:size saved-form)))
    (is (not-any? #(= (first %) :effects/api-submit-order) effects))))

(deftest set-order-size-display-truncates-canonical-size-to-market-decimals-test
  (let [state {:active-asset "BTC"
               :active-market {:coin "BTC" :mark 70179 :maxLeverage 40 :szDecimals 5}
               :orderbooks {"BTC" {:bids [{:px "70150"}]
                                   :asks [{:px "70160"}]}}
               :webdata2 {:clearinghouseState {:marginSummary {:accountValue "1000"
                                                               :totalMarginUsed "250"}}}
               :order-form (assoc (trading/default-order-form) :type :limit :price "70179")}
        effects (core/set-order-size-display state "2")
        saved-form (-> effects first second first second)
        saved-ui (extract-saved-order-form-ui effects)
        summary (trading/order-summary state saved-form)]
    (is (= "2" (:size-display saved-ui)))
    (is (= "0.00002" (:size saved-form)))
    (is (<= (js/Math.abs (- 1.4 (:order-value summary))) 0.01))))

(deftest focus-order-price-input-locks-price-and-captures-current-fallback-test
  (let [state {:active-asset "BTC"
               :active-market {:coin "BTC" :mark 70000 :maxLeverage 40 :szDecimals 4}
               :orderbooks {"BTC" {:bids [{:px "70120"} {:px "70150"} {:px "70090"}]
                                   :asks [{:px "70240"} {:px "70160"} {:px "70210"}]}}
               :order-form (assoc (trading/default-order-form) :type :limit :price "")
               :order-form-ui {:price-input-focused? false}}
        effects (core/focus-order-price-input state)
        saved-form (extract-saved-order-form effects)
        saved-ui (extract-saved-order-form-ui effects)]
    (is (= 1 (count effects)))
    (is (= :effects/save-many (ffirst effects)))
    (is (= true (:price-input-focused? saved-ui)))
    (is (= "70155" (:price saved-form)))))

(deftest focus-order-price-input-does-not-overwrite-manual-price-test
  (let [state {:active-asset "BTC"
               :active-market {:coin "BTC" :mark 70000 :maxLeverage 40 :szDecimals 4}
               :orderbooks {"BTC" {:bids [{:px "70120"}]
                                   :asks [{:px "70160"}]}}
               :order-form (assoc (trading/default-order-form)
                                  :type :limit
                                  :price "70133.5")
               :order-form-ui {:price-input-focused? false}}
        effects (core/focus-order-price-input state)
        saved-form (extract-saved-order-form effects)
        saved-ui (extract-saved-order-form-ui effects)]
    (is (= 1 (count effects)))
    (is (= :effects/save-many (ffirst effects)))
    (is (= true (:price-input-focused? saved-ui)))
    (is (= "70133.5" (:price saved-form)))))

(deftest blur-order-price-input-releases-focus-lock-without-mutating-price-test
  (let [state {:order-form (assoc (trading/default-order-form)
                                  :type :limit
                                  :price "70155")
               :order-form-ui {:price-input-focused? true}}
        effects (core/blur-order-price-input state)
        saved-ui (extract-saved-order-form-ui effects)]
    (is (= 1 (count effects)))
    (is (= :effects/save-many (ffirst effects)))
    (is (= false (:price-input-focused? saved-ui)))))

(deftest set-order-price-to-mid-uses-best-bid-ask-midpoint-test
  (let [state {:active-asset "BTC"
               :active-market {:coin "BTC" :mark 70000 :maxLeverage 40 :szDecimals 4}
               :orderbooks {"BTC" {:bids [{:px "70120"} {:px "70150"} {:px "70090"}]
                                   :asks [{:px "70240"} {:px "70160"} {:px "70210"}]}}
               :order-form (assoc (trading/default-order-form) :type :limit :price "")}
        effects (core/set-order-price-to-mid state)
        saved-form (-> effects first second first second)]
    (is (= 1 (count effects)))
    (is (= :effects/save-many (ffirst effects)))
    (is (= "70155" (:price saved-form)))
    (is (not-any? #(= (first %) :effects/api-submit-order) effects))))

(deftest submit-order-emits-single-api-submit-order-effect-test
  (let [state {:active-asset "BTC"
               :active-market {:coin "BTC" :market-type :perp}
               :asset-contexts {:BTC {:idx 0}}
               :wallet {:connected? true
                        :address "0xabc"
                        :agent {:status :ready
                                :storage-mode :session
                                :agent-address "0xagent"}}
               :orderbooks {"BTC" {:bids [{:px "99"}]
                                   :asks [{:px "101"}]}}
               :order-form (assoc (trading/default-order-form)
                                  :type :limit
                                  :side :buy
                                  :size "1"
                                  :price "100")}
        effects (core/submit-order state)
        api-submit-effects (filter #(= (first %) :effects/api-submit-order) effects)]
    (is (= 1 (count api-submit-effects)))))

(deftest submit-order-limit-with-blank-price-uses-fallback-and-emits-single-submit-effect-test
  (let [state {:active-asset "BTC"
               :active-market {:coin "BTC" :market-type :perp}
               :asset-contexts {:BTC {:idx 0}}
               :wallet {:connected? true
                        :address "0xabc"
                        :agent {:status :ready
                                :storage-mode :session
                                :agent-address "0xagent"}}
               :orderbooks {"BTC" {:bids [{:px "99"}]
                                   :asks [{:px "101"}]}}
               :order-form (assoc (trading/default-order-form)
                                  :type :limit
                                  :side :buy
                                  :size "1"
                                  :price "")}
        effects (core/submit-order state)
        api-submit-effects (filter #(= (first %) :effects/api-submit-order) effects)
        saved-form (some (fn [effect]
                           (when (and (= :effects/save (first effect))
                                      (= [:order-form] (second effect)))
                             (nth effect 2)))
                         effects)]
    (is (= 1 (count api-submit-effects)))
    (is (seq (:price saved-form)))))

(deftest submit-order-requires-agent-ready-session-test
  (let [state {:active-asset "BTC"
               :active-market {:coin "BTC" :market-type :perp}
               :asset-contexts {:BTC {:idx 0}}
               :wallet {:connected? true
                        :address "0xabc"
                        :agent {:status :not-ready
                                :storage-mode :session}}
               :orderbooks {"BTC" {:bids [{:px "99"}]
                                   :asks [{:px "101"}]}}
               :order-form (assoc (trading/default-order-form)
                                  :type :limit
                                  :side :buy
                                  :size "1"
                                  :price "100")}
        effects (core/submit-order state)]
    (is (not-any? #(= (first %) :effects/api-submit-order) effects))
    (is (= [[:effects/save [:order-form-runtime :error] "Enable trading before submitting orders."]]
           effects))))

(deftest cancel-order-requires-agent-ready-session-test
  (let [state {:wallet {:connected? true
                        :address "0xabc"
                        :agent {:status :not-ready
                                :storage-mode :session}}
               :asset-contexts {:BTC {:idx 0}}}
        order {:coin "BTC"
               :oid 101}
        effects (core/cancel-order state order)]
    (is (= [[:effects/save [:orders :cancel-error] "Enable trading before cancelling orders."]]
           effects))))

(deftest cancel-order-ready-agent-emits-single-api-cancel-effect-test
  (let [state {:wallet {:connected? true
                        :address "0xabc"
                        :agent {:status :ready
                                :storage-mode :session
                                :agent-address "0xagent"}}
               :asset-contexts {:BTC {:idx 0}}}
        order {:coin "BTC"
               :oid 202}
        effects (core/cancel-order state order)
        cancel-effects (filter #(= (first %) :effects/api-cancel-order) effects)]
    (is (= 1 (count cancel-effects)))))

(deftest cancel-order-falls-back-to-asset-selector-market-index-test
  (let [state {:wallet {:connected? true
                        :address "0xabc"
                        :agent {:status :ready
                                :storage-mode :session
                                :agent-address "0xagent"}}
               :asset-contexts {}
               :asset-selector {:market-by-key {"perp:SOL" {:coin "SOL"
                                                            :idx 12}}}}
        order {:coin "SOL"
               :oid "307891000622"}
        effects (core/cancel-order state order)]
    (is (= [[:effects/api-cancel-order {:action {:type "cancel"
                                                 :cancels [{:a 12 :o 307891000622}]}}]]
           effects))))

(deftest prune-canceled-open-orders-removes-canceled-oid-across-all-sources-test
  (let [state {:orders {:open-orders [{:order {:coin "BTC" :oid 101}}
                                      {:order {:coin "ETH" :oid 102}}]
                        :open-orders-snapshot {:orders [{:order {:coin "BTC" :oid 101}}
                                                        {:order {:coin "SOL" :oid 103}}]}
                        :open-orders-snapshot-by-dex {"dex-a" [{:order {:coin "BTC" :oid 101}}]
                                                      "dex-b" [{:order {:coin "XRP" :oid 104}}]}}}
        request {:action {:type "cancel"
                          :cancels [{:a 0 :o 101}]}}
        next-state (core/prune-canceled-open-orders state request)]
    (is (= #{102}
           (->> (get-in next-state [:orders :open-orders])
                (map #(get-in % [:order :oid]))
                set)))
    (is (= #{103}
           (->> (get-in next-state [:orders :open-orders-snapshot :orders])
                (map #(get-in % [:order :oid]))
                set)))
    (is (= []
           (get-in next-state [:orders :open-orders-snapshot-by-dex "dex-a"])))
    (is (= #{104}
           (->> (get-in next-state [:orders :open-orders-snapshot-by-dex "dex-b"])
                (map #(get-in % [:order :oid]))
                set)))))

