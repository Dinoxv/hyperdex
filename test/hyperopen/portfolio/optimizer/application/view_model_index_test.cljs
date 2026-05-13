(ns hyperopen.portfolio.optimizer.application.view-model-index-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.view-model :as view-model]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(def ^:private first-summary
  {:id "scn_01"
   :name "Core Hedge"
   :status :partially-executed
   :objective-kind :max-sharpe
   :return-model-kind :black-litterman
   :risk-model-kind :diagonal-shrink
   :expected-return 0.18
   :volatility 0.42
   :updated-at-ms 3000})

(def ^:private second-summary
  {:id "scn_02"
   :name "Fresh Run"
   :status :saved
   :objective-kind :minimum-variance
   :return-model-kind :historical-mean
   :risk-model-kind :diagonal-shrink
   :expected-return 0.12
   :volatility 0.24
   :updated-at-ms 4000})

(deftest index-model-projects-ordered-scenario-summaries-test
  (let [model (view-model/index-model
               (assoc-in {}
                         contracts/scenario-index-path
                         {:ordered-ids ["scn_02" "scn_missing" "scn_01"]
                          :by-id {"scn_01" first-summary
                                  "scn_02" second-summary}}))]
    (is (= [second-summary first-summary]
           (:scenario-summaries model)))))

(deftest index-model-defaults-empty-scenario-summaries-test
  (is (= {:scenario-summaries []}
         (view-model/index-model {}))))
