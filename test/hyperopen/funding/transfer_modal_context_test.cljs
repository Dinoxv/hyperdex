(ns hyperopen.funding.transfer-modal-context-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.funding.actions :as funding-actions]))

(defn- base-state
  []
  {:wallet {:address "0x1234567890abcdef1234567890abcdef12345678"}
   :funding-ui {:modal (funding-actions/default-funding-modal-state)}})

(defn- saved-funding-modal
  [effects]
  (some (fn [effect]
          (when (and (vector? effect)
                     (= :effects/save (first effect))
                     (= [:funding-ui :modal] (second effect)))
            (nth effect 2)))
        effects))

(deftest open-funding-transfer-modal-applies-named-dex-context-test
  (let [saved (saved-funding-modal
               (funding-actions/open-funding-transfer-modal (base-state) nil nil
                                                            {:dex "xyz"
                                                             :to-perp? false}))]
    (is (= :transfer (:mode saved)))
    (is (= "xyz" (:transfer-dex saved)))
    (is (= false (:to-perp? saved)))))

(deftest open-funding-transfer-modal-applies-spot-row-context-test
  ;; Spot row context selects the default perps dex and the spot -> perps direction.
  (let [saved (saved-funding-modal
               (funding-actions/open-funding-transfer-modal (base-state) nil nil
                                                            {:dex ""
                                                             :to-perp? true}))]
    (is (= "" (:transfer-dex saved)))
    (is (= true (:to-perp? saved)))))

(deftest open-funding-transfer-modal-without-context-keeps-defaults-test
  ;; The context-free global transfer modal keeps today's defaults.
  (let [saved (saved-funding-modal
               (funding-actions/open-funding-transfer-modal (base-state)))]
    (is (= "" (:transfer-dex saved)))
    (is (= true (:to-perp? saved)))))
