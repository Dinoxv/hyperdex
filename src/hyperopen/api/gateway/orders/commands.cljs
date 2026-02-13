(ns hyperopen.api.gateway.orders.commands
  (:require [hyperopen.domain.trading :as trading-domain]))

(defn- tif->wire [tif]
  (case tif
    :ioc "Ioc"
    :alo "Alo"
    "Gtc"))

(defn build-scale-orders [asset-idx side total-size start end reduce-only post-only]
  (let [legs (trading-domain/scale-order-legs (get total-size :size)
                                              (get total-size :count)
                                              (get total-size :skew)
                                              start
                                              end
                                              {:sz-decimals (get total-size :sz-decimals)})
        tif (if post-only "Alo" "Gtc")]
    (mapv (fn [{:keys [price size]}]
            (array-map :a asset-idx
                       :b (trading-domain/order-side->is-buy side)
                       :p (str price)
                       :s (str size)
                       :r reduce-only
                       :t {:limit {:tif tif}}))
          legs)))

(defn build-tpsl-orders [asset-idx side form]
  (let [tp (get-in form [:tp])
        sl (get-in form [:sl])
        tp-enabled? (:enabled? tp)
        sl-enabled? (:enabled? sl)
        close-side (trading-domain/opposite-side side)
        mk-trigger (fn [tpsl cfg]
                     (array-map :a asset-idx
                                :b (trading-domain/order-side->is-buy close-side)
                                :p (str (or (trading-domain/parse-num (:limit cfg))
                                            (trading-domain/parse-num (:trigger cfg))))
                                :s (str (trading-domain/parse-num (:size form)))
                                :r true
                                :t {:trigger (array-map :isMarket (:is-market cfg)
                                                        :triggerPx (trading-domain/parse-num (:trigger cfg))
                                                        :tpsl tpsl)}))]
    (cond-> []
      tp-enabled? (conj (mk-trigger "tp" tp))
      sl-enabled? (conj (mk-trigger "sl" sl)))))

(def ^:private order-type->builder
  {:limit (fn [base-order {:keys [post-only tif]}]
            (assoc base-order :t {:limit {:tif (if post-only "Alo" tif)}}))
   :market (fn [base-order _]
             (assoc base-order :t {:limit {:tif "Ioc"}}))
   :stop-market (fn [base-order {:keys [price trigger]}]
                  (assoc base-order
                         :p (str (or price trigger))
                         :t {:trigger (array-map :isMarket true :triggerPx trigger :tpsl "sl")}))
   :stop-limit (fn [base-order {:keys [trigger]}]
                 (assoc base-order :t {:trigger (array-map :isMarket false :triggerPx trigger :tpsl "sl")}))
   :take-market (fn [base-order {:keys [price trigger]}]
                  (assoc base-order
                         :p (str (or price trigger))
                         :t {:trigger (array-map :isMarket true :triggerPx trigger :tpsl "tp")}))
   :take-limit (fn [base-order {:keys [trigger]}]
                 (assoc base-order
                        :t {:trigger (array-map :isMarket false :triggerPx trigger :tpsl "tp")}))})

(defn- build-order [form base-order opts]
  (let [builder (get order-type->builder (:type form)
                     (fn [order _] order))]
    (builder base-order opts)))

(defn build-order-action
  "Return {:action action :grouping grouping}"
  [command-context form]
  (let [active-asset (:active-asset command-context)
        asset-idx (:asset-idx command-context)
        side (:side form)
        size (trading-domain/parse-num (:size form))
        price (trading-domain/parse-num (:price form))
        trigger (trading-domain/parse-num (:trigger-px form))
        reduce-only (:reduce-only form)
        post-only (:post-only form)
        tif (tif->wire (:tif form))
        grouping (if (or (get-in form [:tp :enabled?]) (get-in form [:sl :enabled?]))
                   "normalTpsl"
                   "na")]
    (when (and active-asset asset-idx size)
      (let [base-order (array-map :a asset-idx
                                  :b (trading-domain/order-side->is-buy side)
                                  :p (str price)
                                  :s (str size)
                                  :r reduce-only)
            order (build-order form
                               base-order
                               {:post-only post-only
                                :tif tif
                                :trigger trigger
                                :price price})
            tpsl-orders (build-tpsl-orders asset-idx side form)
            orders (cond-> [order]
                     (seq tpsl-orders) (into tpsl-orders))]
        {:action (array-map :type "order"
                            :orders orders
                            :grouping grouping)
         :asset-idx asset-idx
         :orders orders}))))

(defn build-twap-action [command-context form]
  (let [active-asset (:active-asset command-context)
        asset-idx (:asset-idx command-context)
        side (:side form)
        size (trading-domain/parse-num (:size form))
        minutes (trading-domain/parse-num (get-in form [:twap :minutes]))
        randomize (boolean (get-in form [:twap :randomize]))]
    (when (and active-asset asset-idx size minutes)
      {:action (array-map :type "twapOrder"
                          :twap (array-map :a asset-idx
                                           :b (trading-domain/order-side->is-buy side)
                                           :s (str size)
                                           :r (boolean (:reduce-only form))
                                           :m (int minutes)
                                           :t randomize))
       :asset-idx asset-idx})))

(defn build-order-request [command-context form]
  (case (:type form)
    :twap (build-twap-action command-context form)
    :scale (let [asset-idx (:asset-idx command-context)
                 side (:side form)
                 size (trading-domain/parse-num (:size form))
                 scale (get-in form [:scale])
                 orders (when (and asset-idx size)
                          (build-scale-orders
                           asset-idx
                           side
                           {:size size
                            :count (:count scale)
                            :skew (:skew scale)
                            :sz-decimals (:sz-decimals command-context)}
                           (get scale :start)
                           (get scale :end)
                           (:reduce-only form)
                           (:post-only form)))]
             (when (seq orders)
               {:action (array-map :type "order"
                                   :orders (vec orders)
                                   :grouping "na")
                :asset-idx asset-idx
                :orders orders}))
    (build-order-action command-context form)))
