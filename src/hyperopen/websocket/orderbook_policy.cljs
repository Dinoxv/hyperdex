(ns hyperopen.websocket.orderbook-policy)

(def default-max-render-levels-per-side 80)

(defn parse-number [value]
  (cond
    (number? value) value
    (string? value) (let [n (js/parseFloat value)]
                      (when-not (js/isNaN n) n))
    :else nil))

(defn level-price [level]
  (or (:px-num level)
      (parse-number (:px level))
      (parse-number (:price level))
      (parse-number (:p level))))

(defn level-size [level]
  (or (:sz-num level)
      (parse-number (:sz level))
      (parse-number (:size level))
      (parse-number (:s level))))

(defn normalize-level [level]
  (if (map? level)
    (assoc level
           :px-num (level-price level)
           :sz-num (level-size level))
    level))

(defn normalize-levels [levels]
  (mapv normalize-level (or levels [])))

(defn- strip-normalized-level [level]
  (if (map? level)
    (dissoc level :px-num :sz-num :cum-size :cum-value)
    level))

(defn- sort-levels [levels comparator]
  (vec (sort-by #(or (level-price %) 0) comparator levels)))

(defn- sorted-normalized-levels [levels comparator]
  (sort-levels (normalize-levels levels) comparator))

(defn- strip-normalized-levels [levels]
  (mapv strip-normalized-level levels))

(defn- take-leading-levels [levels limit*]
  (if (> (count levels) limit*)
    (subvec levels 0 limit*)
    levels))

(defn sort-display-asks [asks]
  (sort-levels (normalize-levels asks) <))

(defn sort-bids [bids]
  (strip-normalized-levels
   (sorted-normalized-levels bids >)))

(defn sort-asks [asks]
  ;; Keep legacy sort direction for compatibility with existing consumers.
  (strip-normalized-levels
   (sorted-normalized-levels asks >)))

(defn calculate-cumulative-totals [orders]
  (if (empty? orders)
    []
    (loop [remaining orders
           cum-size 0
           cum-value 0
           result []]
      (if (empty? remaining)
        result
        (let [order (first remaining)
              price (or (level-price order) 0)
              size (or (level-size order) 0)
              new-cum-size (+ cum-size size)
              new-cum-value (+ cum-value (* price size))
              updated-order (assoc order
                                   :cum-size new-cum-size
                                   :cum-value new-cum-value)]
          (recur (rest remaining)
                 new-cum-size
                 new-cum-value
                 (conj result updated-order)))))))

(defn- normalized-depth-limit [max-levels]
  (if (and (number? max-levels) (pos? max-levels))
    (int max-levels)
    default-max-render-levels-per-side))

(defn build-book
  ([bids asks]
   (build-book bids asks default-max-render-levels-per-side))
  ([bids asks max-levels]
   (let [limit* (normalized-depth-limit max-levels)
         sorted-bids (sorted-normalized-levels bids >)
         sorted-asks (sorted-normalized-levels asks >)
         display-bids-limited (take-leading-levels sorted-bids limit*)
         display-asks-limited (into [] (take limit* (rseq sorted-asks)))]
     {:bids (strip-normalized-levels sorted-bids)
      :asks (strip-normalized-levels sorted-asks)
      :render {:display-bids display-bids-limited
               :display-asks display-asks-limited
               :bids-with-totals (calculate-cumulative-totals display-bids-limited)
               :asks-with-totals (calculate-cumulative-totals display-asks-limited)
               :best-bid (first sorted-bids)
               :best-ask (peek sorted-asks)}})))

(defn same-render-book?
  [left right]
  (= (dissoc (or left {}) :timestamp)
     (dissoc (or right {}) :timestamp)))

(defn normalize-aggregation-config [aggregation-config]
  (let [n-sig-figs (:nSigFigs aggregation-config)]
    (cond-> {}
      (contains? #{2 3 4 5} n-sig-figs) (assoc :nSigFigs n-sig-figs))))

(defn build-subscription [symbol aggregation-config]
  (merge {:type "l2Book"
          :coin symbol}
         (normalize-aggregation-config aggregation-config)))
