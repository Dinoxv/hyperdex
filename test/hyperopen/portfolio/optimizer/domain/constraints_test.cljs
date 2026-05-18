(ns hyperopen.portfolio.optimizer.domain.constraints-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.domain.constraints :as constraints]))

(defn- sparse-cadence
  [interval-count]
  {:kind :sparse
   :sparse? true
   :interval-count interval-count})

(defn- daily-rows
  [n]
  (mapv (fn [idx]
          {:time-ms (* idx 86400000)
           :close (+ 100 idx)})
        (range n)))

(deftest normalize-universe-applies-allowlist-and-blocklist-before-solving-test
  (let [universe [{:instrument-id "A"}
                  {:instrument-id "B"}
                  {:instrument-id "C"}]]
    (is (= [{:instrument-id "A"}]
           (constraints/normalize-universe universe
                                           {:allowlist #{"A" "B"}
                                            :blocklist #{"B"}})))))

(deftest normalize-universe-accepts-vector-filters-from-worker-payload-test
  (let [universe [{:instrument-id "A"}
                  {:instrument-id "B"}
                  {:instrument-id "C"}]]
    (is (= [{:instrument-id "A"}]
           (constraints/normalize-universe universe
                                           {:allowlist ["A" "B"]
                                            :blocklist ["B"]})))))

(deftest encode-long-only-bounds-applies-global-cap-overrides-and-held-locks-test
  (let [encoded (constraints/encode-constraints
                 {:universe [{:instrument-id "A"}
                             {:instrument-id "B"}
                             {:instrument-id "C"}]
                  :current-weights {"C" 0.2}
                  :constraints {:long-only? true
                                :max-asset-weight 0.6
                                :per-asset-overrides {"B" {:max-weight 0.25}}
                                :held-position-locks #{"C"}}})]
    (is (= ["A" "B" "C"] (:instrument-ids encoded)))
    (is (= [0 0 0.2] (:lower-bounds encoded)))
    (is (= [0.6 0.25 0.2] (:upper-bounds encoded)))
    (is (= [{:instrument-id "C"
             :weight 0.2}]
           (:locked-weights encoded)))
    (is (= :ok (:status encoded)))))

(deftest encode-constraints-accepts-vector-held-locks-from-worker-payload-test
  (let [encoded (constraints/encode-constraints
                 {:universe [{:instrument-id "A"}
                             {:instrument-id "B"}]
                  :current-weights {"B" 0.35}
                  :constraints {:long-only? true
                                :max-asset-weight 0.8
                                :held-position-locks ["B"]}})]
    (is (= [0 0.35] (:lower-bounds encoded)))
    (is (= [0.8 0.35] (:upper-bounds encoded)))
    (is (= [{:instrument-id "B"
             :weight 0.35}]
           (:locked-weights encoded)))))

(deftest presolve-reports-infeasible-long-only-cap-before-solver-test
  (let [encoded (constraints/encode-constraints
                 {:universe [{:instrument-id "A"}
                             {:instrument-id "B"}]
                  :constraints {:long-only? true
                                :max-asset-weight 0.4}})]
    (is (= :infeasible (:status encoded)))
    (is (= [{:code :sum-upper-below-target
             :sum-upper 0.8
             :target-net 1}]
           (:violations encoded)))))

(deftest encode-constraints-applies-runtime-sparse-cap-tiers-test
  (let [encoded (constraints/encode-constraints
                 {:universe [{:instrument-id "A"}
                             {:instrument-id "B"}
                             {:instrument-id "C"}
                             {:instrument-id "D"}
                             {:instrument-id "E"}]
                  :history {:cadence-by-instrument
                            {"A" (sparse-cadence 1)
                             "B" (sparse-cadence 7)
                             "C" (sparse-cadence 29)
                             "D" (sparse-cadence 59)
                             "E" (sparse-cadence 60)}}
                  :constraints {:long-only? false
                                :max-asset-weight 1}})]
    (is (= :ok (:status encoded)))
    (is (= [0 0.05 0.1 0.2 1]
           (:upper-bounds encoded)))
    (is (= [:sparse-history-weight-cap-applied
            :sparse-history-weight-cap-applied
            :sparse-history-weight-cap-applied
            :sparse-history-weight-cap-applied]
           (mapv :code (:warnings encoded))))))

(deftest encode-constraints-keeps-runtime-sparse-cap-when-cap-makes-request-infeasible-test
  (let [encoded (constraints/encode-constraints
                 {:universe [{:instrument-id "vault:solo"
                              :market-type :vault}]
                  :history {:cadence-by-instrument
                            {"vault:solo" (sparse-cadence 1)}}
                  :constraints {:long-only? true
                                :max-asset-weight 1}})]
    (is (= :infeasible (:status encoded)))
    (is (= [0] (:upper-bounds encoded)))
    (is (= [{:code :sum-upper-below-target
             :sum-upper 0
             :target-net 1}]
           (:violations encoded)))
    (is (= :sparse-history-weight-cap-applied
           (get-in encoded [:warnings 0 :code])))))

(deftest encode-constraints-treats-vault-without-native-cadence-as-sparse-test
  (let [encoded (constraints/encode-constraints
                 {:universe [{:instrument-id "vault:manual"
                              :market-type :vault}]
                  :history {:price-series-by-instrument
                            {"vault:manual" (daily-rows 30)}}
                  :constraints {:long-only? false
                                :max-asset-weight 1}})
        warning (first (:warnings encoded))]
    (is (= :ok (:status encoded)))
    (is (= [0.2] (:upper-bounds encoded)))
    (is (= :sparse-history-weight-cap-applied (:code warning)))
    (is (nil? (:interval-count warning)))
    (is (= 0.2 (:max-weight warning)))))

(deftest encode-signed-mode-bounds-supports-gross-and-net-exposure-contract-test
  (let [encoded (constraints/encode-constraints
                 {:universe [{:instrument-id "A"}
                             {:instrument-id "B"}]
                  :current-weights {"A" 0.3
                                    "B" -0.1}
                  :constraints {:long-only? false
                                :max-asset-weight 0.7
                                :gross-leverage 1.4
                                :max-turnover 0.25
                                :net-exposure {:min -0.2
                                               :max 0.8}}})]
    (is (= [-0.7 -0.7] (:lower-bounds encoded)))
    (is (= [0.7 0.7] (:upper-bounds encoded)))
    (is (= [0.3 -0.1] (:current-weights encoded)))
    (is (= {:max 1.4} (:gross-exposure encoded)))
    (is (= 0.25 (:max-turnover encoded)))
    (is (= {:min -0.2 :max 0.8} (:net-exposure encoded)))))
