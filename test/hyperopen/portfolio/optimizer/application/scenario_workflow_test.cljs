(ns hyperopen.portfolio.optimizer.application.scenario-workflow-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.scenario-workflow :as workflow]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]))

(def address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")

(defn- save-state
  []
  {:router {:path "/portfolio/optimize/scn_01"}
   :wallet {:address address}
   :portfolio {:optimizer
               {:draft {:id "scn_01"
                        :name "Core Hedge"
                        :objective {:kind :max-sharpe}
                        :return-model {:kind :historical-mean}
                        :risk-model {:kind :diagonal-shrink}
                        :metadata {:dirty? true}}
                :scenario-index {:ordered-ids []
                                 :by-id {}}
                :last-successful-run
                (fixtures/sample-last-successful-run
                 {:result {:expected-return 0.18
                           :volatility 0.42}
                  :computed-at-ms 2000})}}})

(deftest begin-save-marks-save-state-and-loads-index-test
  (let [result (workflow/begin-save {:state (save-state)
                                     :address address
                                     :scenario-id "scn_01"
                                     :started-at-ms 3000})]
    (is (= {:status :saving
            :scenario-id "scn_01"
            :started-at-ms 3000
            :completed-at-ms nil
            :error nil}
           (get-in result [:state :portfolio :optimizer :scenario-save-state])))
    (is (= [{:command/type :optimizer.workflow/load-scenario-index
             :address address
             :scenario-id "scn_01"
             :started-at-ms 3000}]
           (:commands result)))))

(deftest continue-save-after-index-builds-ordered-save-commands-test
  (let [loaded-index {:ordered-ids []
                      :by-id {}}
        result (workflow/continue-save-after-index
                {:state (save-state)
                 :address address
                 :scenario-id "scn_01"
                 :started-at-ms 3000
                 :loaded-index loaded-index})
        scenario-record (:scenario-record result)
        scenario-index (:scenario-index result)]
    (is (= "scn_01" (:id scenario-record)))
    (is (= :saved (:status scenario-record)))
    (is (= ["scn_01"] (:ordered-ids scenario-index)))
    (is (= [:optimizer.workflow/save-scenario
            :optimizer.workflow/save-scenario-index]
           (mapv :command/type (:commands result))))
    (is (= ["scn_01" address]
           [(get-in result [:commands 0 :scenario-id])
            (get-in result [:commands 1 :address])]))))
