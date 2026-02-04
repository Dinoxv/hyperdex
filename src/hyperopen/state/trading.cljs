(ns hyperopen.state.trading
  (:require [clojure.string :as str]))

(def order-types
  [:market :limit :stop-market :stop-limit :take-market :take-limit :scale :twap])

(def tif-options [:gtc :ioc :alo])

(defn default-order-form []
  {:type :limit
   :side :buy
   :size ""
   :price ""
   :trigger-px ""
   :reduce-only false
   :post-only false
   :tif :gtc
   :slippage 0.5
   :scale {:start ""
           :end ""
           :count 5
           :skew :even}
   :twap {:minutes 5
          :randomize true}
   :tp {:enabled? false
        :trigger ""
        :is-market true
        :limit ""}
   :sl {:enabled? false
        :trigger ""
        :is-market true
        :limit ""}
   :submitting? false
   :error nil})

(defn parse-num [v]
  (cond
    (number? v) v
    (string? v) (let [s (str/trim v)
                      n (js/parseFloat s)]
                  (when (and (not (str/blank? s)) (not (js/isNaN n))) n))
    :else nil))

(defn validate-order-form [form]
  (let [size (parse-num (:size form))
        price (parse-num (:price form))
        trigger (parse-num (:trigger-px form))
        scale-start (parse-num (get-in form [:scale :start]))
        scale-end (parse-num (get-in form [:scale :end]))
        scale-count (parse-num (get-in form [:scale :count]))
        twap-min (parse-num (get-in form [:twap :minutes]))
        tp-enabled? (get-in form [:tp :enabled?])
        sl-enabled? (get-in form [:sl :enabled?])
        tp-trigger (parse-num (get-in form [:tp :trigger]))
        sl-trigger (parse-num (get-in form [:sl :trigger]))]
    (cond-> []
      (or (nil? size) (<= size 0)) (conj "Size must be greater than 0.")
      (and (#{:limit :stop-limit :take-limit} (:type form))
           (or (nil? price) (<= price 0))) (conj "Price is required for limit orders.")
      (and (#{:stop-market :stop-limit :take-market :take-limit} (:type form))
           (or (nil? trigger) (<= trigger 0))) (conj "Trigger price is required for stop/take orders.")
      (and (= :scale (:type form))
           (or (nil? scale-start) (nil? scale-end) (<= scale-count 1)))
      (conj "Scale orders need start/end prices and count > 1.")
      (and (= :twap (:type form))
           (or (nil? twap-min) (<= twap-min 0)))
      (conj "TWAP minutes must be greater than 0.")
      (and tp-enabled? (or (nil? tp-trigger) (<= tp-trigger 0)))
      (conj "TP trigger price is required when TP is enabled.")
      (and sl-enabled? (or (nil? sl-trigger) (<= sl-trigger 0)))
      (conj "SL trigger price is required when SL is enabled."))))

(defn order-side->is-buy [side]
  (= side :buy))

(defn opposite-side [side]
  (if (= side :buy) :sell :buy))

(defn scale-weights [count skew]
  (let [n (int count)
        base (range 1 (inc n))
        weights (case skew
                  :front (reverse base)
                  :back base
                  base)
        total (reduce + weights)]
    (map #(/ % total) weights)))

(defn build-scale-orders [asset-idx side total-size start end reduce-only post-only]
  (let [count (max 2 (int (or (parse-num (get-in total-size [:count])) 2)))
        start-px (parse-num start)
        end-px (parse-num end)
        weights (scale-weights count (get-in total-size [:skew] :even))
        size (parse-num (get-in total-size [:size]))
        step (if (= count 1) 0 (/ (- end-px start-px) (dec count)))
        tif (if post-only "Alo" "Gtc")]
    (map-indexed
      (fn [i w]
        (let [px (+ start-px (* step i))
              sz (* size w)]
          {:a asset-idx
           :b (order-side->is-buy side)
           :p (str px)
           :s (str sz)
           :r reduce-only
           :t {:limit {:tif tif}}}))
      weights)))

(defn build-tpsl-orders [asset-idx side form]
  (let [tp (get-in form [:tp])
        sl (get-in form [:sl])
        tp-enabled? (:enabled? tp)
        sl-enabled? (:enabled? sl)
        close-side (opposite-side side)
        mk-trigger (fn [tpsl cfg]
                     {:a asset-idx
                      :b (order-side->is-buy close-side)
                      :p (str (or (parse-num (:limit cfg)) (parse-num (:trigger cfg))))
                      :s (str (parse-num (:size form)))
                      :r true
                      :t {:trigger {:isMarket (:is-market cfg)
                                    :triggerPx (parse-num (:trigger cfg))
                                    :tpsl tpsl}}})]
    (cond-> []
      tp-enabled? (conj (mk-trigger "tp" tp))
      sl-enabled? (conj (mk-trigger "sl" sl)))))

(defn build-order-action
  "Return {:action action :grouping grouping}"
  [state form]
  (let [active-asset (:active-asset state)
        asset-idx (get-in state [:asset-contexts (keyword active-asset) :idx])
        side (:side form)
        size (parse-num (:size form))
        price (parse-num (:price form))
        trigger (parse-num (:trigger-px form))
        reduce-only (:reduce-only form)
        post-only (:post-only form)
        tif (case (:tif form)
              :ioc "Ioc"
              :alo "Alo"
              "Gtc")
        grouping (if (or (get-in form [:tp :enabled?]) (get-in form [:sl :enabled?]))
                   "normalTpsl"
                   "na")]
    (when (and active-asset asset-idx size)
      (let [base-order {:a asset-idx
                        :b (order-side->is-buy side)
                        :p (str price)
                        :s (str size)
                        :r reduce-only}
            order (case (:type form)
                    :limit (assoc base-order :t {:limit {:tif (if post-only "Alo" tif)}})
                    :market (assoc base-order :t {:limit {:tif "Ioc"}})
                    :stop-market (assoc base-order :p (str (or price trigger))
                                        :t {:trigger {:isMarket true :triggerPx trigger :tpsl "sl"}})
                    :stop-limit (assoc base-order :t {:trigger {:isMarket false :triggerPx trigger :tpsl "sl"}})
                    :take-market (assoc base-order :p (str (or price trigger))
                                        :t {:trigger {:isMarket true :triggerPx trigger :tpsl "tp"}})
                    :take-limit (assoc base-order :t {:trigger {:isMarket false :triggerPx trigger :tpsl "tp"}})
                    base-order)
            tpsl-orders (build-tpsl-orders asset-idx side form)
            orders (cond-> [order]
                     (seq tpsl-orders) (into tpsl-orders))]
        {:action {:type "order"
                  :grouping grouping
                  :orders orders}
         :asset-idx asset-idx
         :orders orders}))))

(defn build-twap-action [state form]
  (let [active-asset (:active-asset state)
        asset-idx (get-in state [:asset-contexts (keyword active-asset) :idx])
        side (:side form)
        size (parse-num (:size form))
        minutes (parse-num (get-in form [:twap :minutes]))
        randomize (boolean (get-in form [:twap :randomize]))]
    (when (and active-asset asset-idx size minutes)
      {:action {:type "twapOrder"
                :twap {:a asset-idx
                       :b (order-side->is-buy side)
                       :s (str size)
                       :r (boolean (:reduce-only form))
                       :m (int minutes)
                       :t randomize}}
       :asset-idx asset-idx})))

(defn best-price [state side]
  (let [active-asset (:active-asset state)
        orderbook (get-in state [:orderbooks active-asset])
        bids (:bids orderbook)
        asks (:asks orderbook)
        best-bid (first bids)
        best-ask (first asks)]
    (if (= side :buy)
      (some-> best-ask :px parse-num)
      (some-> best-bid :px parse-num))))

(defn apply-market-price [state form]
  (let [side (:side form)
        px (best-price state side)
        slippage (or (parse-num (:slippage form)) 0.5)
        adj (if (= side :buy) (+ 1 (/ slippage 100)) (- 1 (/ slippage 100)))]
    (when px
      (assoc form :price (str (* px adj))))))

(defn build-order-request [state form]
  (case (:type form)
    :twap (build-twap-action state form)
    :scale (let [active-asset (:active-asset state)
                 asset-idx (get-in state [:asset-contexts (keyword active-asset) :idx])
                 side (:side form)
                 size (parse-num (:size form))
                 scale (get-in form [:scale])
                 orders (when (and asset-idx size)
                          (build-scale-orders
                            asset-idx
                            side
                            {:size size :count (:count scale) :skew (:skew scale)}
                            (get scale :start)
                            (get scale :end)
                            (:reduce-only form)
                            (:post-only form)))]
             (when (seq orders)
               {:action {:type "order"
                         :grouping "na"
                         :orders (vec orders)}
                :asset-idx asset-idx
                :orders orders}))
    (build-order-action state form)))
