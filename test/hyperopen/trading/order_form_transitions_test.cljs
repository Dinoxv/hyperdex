(ns hyperopen.trading.order-form-transitions-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.state.trading :as trading]
            [hyperopen.trading.order-form-transitions :as transitions]))

(defn- base-state
  ([] (base-state {}))
  ([order-form-overrides]
   (let [order-form (merge (trading/default-order-form) order-form-overrides)]
     {:active-asset "BTC"
      :active-market {:coin "BTC"
                      :quote "USDC"
                      :mark 100
                      :maxLeverage 40
                      :market-type :perp
                      :szDecimals 4}
      :asset-contexts {:BTC {:idx 0}}
      :orderbooks {"BTC" {:bids [{:px "99"}]
                          :asks [{:px "101"}]}}
      :webdata2 {:clearinghouseState {:marginSummary {:accountValue "1000"
                                                      :totalMarginUsed "250"}}}
      :order-form order-form
      :order-form-ui (trading/default-order-form-ui)
      :order-form-runtime (trading/default-order-form-runtime)})))

(deftest transition-runtime-shape-invariant-test
  (let [state (base-state {:type :limit :size "1" :price "100"})]
    (doseq [transition [(transitions/select-entry-mode state :market)
                        (transitions/select-entry-mode state :limit)
                        (transitions/select-pro-order-type state :scale)
                        (transitions/set-order-ui-leverage state 25)
                        (transitions/set-order-size-percent state 45)
                        (transitions/set-order-size-display state "123")
                        (transitions/focus-order-price-input state)
                        (transitions/blur-order-price-input state)
                        (transitions/set-order-price-to-mid state)
                        (transitions/update-order-form state [:side] :sell)]]
      (is (map? transition))
      (when-let [runtime (:order-form-runtime transition)]
        (is (boolean? (:submitting? runtime)))
        (is (nil? (:error runtime)))))))

(deftest percent-application-property-test
  (let [state (base-state {:type :limit :price "100"})]
    (doseq [input [-100 -1 0 1 12 33 50 77 100 120 999]]
      (let [transition (transitions/set-order-size-percent state input)
            form (:order-form transition)
            pct (:size-percent form)]
        (is (number? pct))
        (is (<= 0 pct 100))
        (when (zero? pct)
          (is (= "" (:size form))))))))

(deftest submit-policy-disabled-reason-invariant-test
  (let [state (base-state)
        forms [(assoc (:order-form state) :type :limit :size "" :price "")
               (assoc (:order-form state) :type :market :size "1")
               (assoc (:order-form state) :type :stop-market :size "1" :trigger-px "")
               (assoc (:order-form state) :type :limit :size "1" :price "100")]
        modes [{:mode :view :submitting? false}
               {:mode :submit :agent-ready? true}]]
    (doseq [form forms
            opts modes]
      (let [policy (trading/submit-policy state form opts)]
        (is (= (boolean (:reason policy))
               (:disabled? policy)))))))
