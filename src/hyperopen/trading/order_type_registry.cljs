(ns hyperopen.trading.order-type-registry)

(def order-type-config
  {:stop-market {:label "Stop Market"
                 :sections [:trigger]
                 :limit-like? false
                 :supports-tpsl? true}
   :stop-limit {:label "Stop Limit"
                :sections [:trigger]
                :limit-like? true
                :supports-tpsl? true}
   :take-market {:label "Take Market"
                 :sections [:trigger]
                 :limit-like? false
                 :supports-tpsl? true}
   :take-limit {:label "Take Limit"
                :sections [:trigger]
                :limit-like? true
                :supports-tpsl? true}
   :scale {:label "Scale"
           :sections [:scale]
           :limit-like? false
           :supports-tpsl? false}
   :twap {:label "TWAP"
          :sections [:twap]
          :limit-like? false
          :supports-tpsl? true}})

(def pro-order-type-order
  [:scale :stop-limit :stop-market :take-limit :take-market :twap])

(defn pro-order-types []
  pro-order-type-order)

(defn order-type-entry [order-type]
  (get order-type-config order-type))

(defn order-type-label [order-type]
  (or (get-in order-type-config [order-type :label])
      "Stop Market"))

(defn order-type-sections [order-type]
  (or (get-in order-type-config [order-type :sections])
      []))

(defn order-type-limit-like? [order-type]
  (boolean (get-in order-type-config [order-type :limit-like?])))

(defn order-type-supports-tpsl? [order-type]
  (boolean (get-in order-type-config [order-type :supports-tpsl?] true)))
