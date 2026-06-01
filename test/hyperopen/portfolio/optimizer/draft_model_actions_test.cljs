(ns hyperopen.portfolio.optimizer.draft-model-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions :as actions]))

(deftest apply-portfolio-optimizer-setup-preset-updates-only-model-layer-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}]
                                               :objective {:kind :target-return
                                                           :target-return 0.2}
                                               :return-model {:kind :ew-mean}
                                               :risk-model {:kind :sample-covariance}
                                               :constraints {:max-asset-weight 0.4
                                                             :gross-max 2.0}}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective] {:kind :minimum-variance}]
              [[:portfolio :optimizer :draft :return-model] {:kind :historical-mean}]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]]
           (actions/apply-portfolio-optimizer-setup-preset state :conservative)))
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective] {:kind :max-sharpe}]
              [[:portfolio :optimizer :draft :return-model] {:kind :historical-mean}]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]]
           (actions/apply-portfolio-optimizer-setup-preset state :risk-adjusted)))
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective] {:kind :max-sharpe}]
              [[:portfolio :optimizer :draft :return-model] {:kind :black-litterman
                                                             :views []}]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]]
           (actions/apply-portfolio-optimizer-setup-preset state :use-my-views)))
    (is (= []
           (actions/apply-portfolio-optimizer-setup-preset state :unknown)))))

(deftest set-draft-model-layer-actions-update-draft-and-mark-dirty-test
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :objective]
                                {:kind :max-sharpe}]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-objective-kind
          {}
          "maxSharpe")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :return-model]
                                {:kind :black-litterman
                                 :views []}]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-return-model-kind
          {}
          :black-litterman)))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :return-model]
                                {:kind :ew-mean
                                 :alpha 0.015159678336035098}]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-return-model-kind
          {}
          :ew-mean)))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :risk-model]
                                {:kind :sample-covariance}]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-risk-model-kind
          {}
          "sampleCovariance")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :risk-model]
                                {:kind :mixed-frequency}]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-risk-model-kind
          {}
          :mixed-frequency))))

(deftest set-draft-model-layer-actions-ignore-invalid-kinds-test
  (is (= []
         (actions/set-portfolio-optimizer-objective-kind {} "not-real")))
  (is (= []
         (actions/set-portfolio-optimizer-return-model-kind {} "not-real")))
  (is (= []
         (actions/set-portfolio-optimizer-risk-model-kind {} "not-real"))))
