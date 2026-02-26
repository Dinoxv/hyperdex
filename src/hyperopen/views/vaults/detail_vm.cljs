(ns hyperopen.views.vaults.detail-vm
  (:require [clojure.string :as str]
            [hyperopen.vaults.actions :as vault-actions]))

(def ^:private chart-width
  560)

(def ^:private chart-height
  190)

(defn- optional-number
  [value]
  (cond
    (number? value)
    (when (js/isFinite value)
      value)

    (string? value)
    (let [trimmed (str/trim value)]
      (when (seq trimmed)
        (let [parsed (js/Number trimmed)]
          (when (js/isFinite parsed)
            parsed))))

    :else nil))

(defn- normalize-address
  [value]
  (some-> value str str/trim str/lower-case))

(defn- normalize-percent-value
  [value]
  (let [n (or (optional-number value) 0)]
    (if (<= (js/Math.abs n) 1)
      (* 100 n)
      n)))

(defn- snapshot-point-value
  [entry]
  (cond
    (number? entry) entry

    (and (sequential? entry)
         (>= (count entry) 2))
    (optional-number (second entry))

    (map? entry)
    (or (optional-number (:value entry))
        (optional-number (:pnl entry))
        (optional-number (:account-value entry))
        (optional-number (:accountValue entry)))

    :else
    nil))

(defn- last-snapshot-value
  [snapshot-values]
  (if (sequential? snapshot-values)
    (or (some->> snapshot-values
                 (keep snapshot-point-value)
                 seq
                 last)
        0)
    0))

(defn- row-by-address
  [state vault-address]
  (some (fn [row]
          (when (= vault-address (normalize-address (:vault-address row)))
            row))
        (or (get-in state [:vaults :merged-index-rows]) [])))

(defn- portfolio-summary
  [details snapshot-range]
  (let [portfolio (or (:portfolio details) {})]
    (or (get portfolio snapshot-range)
        (get portfolio :all-time)
        (get portfolio :month)
        (get portfolio :week)
        (get portfolio :day)
        {})))

(defn- history-point
  [row]
  (cond
    (and (sequential? row)
         (>= (count row) 2))
    {:time-ms (optional-number (first row))
     :value (optional-number (second row))}

    (map? row)
    {:time-ms (or (optional-number (:time row))
                  (optional-number (:timestamp row))
                  (optional-number (:time-ms row))
                  (optional-number (:timeMs row))
                  (optional-number (:ts row))
                  (optional-number (:t row)))
     :value (or (optional-number (:value row))
                (optional-number (:account-value row))
                (optional-number (:accountValue row))
                (optional-number (:pnl row)))}

    :else
    nil))

(defn- history-points
  [rows]
  (->> (if (sequential? rows) rows [])
       (keep history-point)
       (filter (fn [{:keys [time-ms value]}]
                 (and (number? time-ms)
                      (number? value))))
       (sort-by :time-ms)
       vec))

(defn- chart-render-points
  [points]
  (if (seq points)
    (let [min-value (apply min (map :value points))
          max-value (apply max (map :value points))
          range-value (max 1e-9 (- max-value min-value))
          max-index (max 1 (dec (count points)))]
      (mapv (fn [[idx {:keys [time-ms value]}]]
              {:time-ms time-ms
               :value value
               :x (* (/ idx max-index) chart-width)
               :y (- chart-height (* (/ (- value min-value) range-value) chart-height))})
            (map-indexed vector points)))
    []))

(defn- line-path
  [points]
  (when (seq points)
    (let [segments (map-indexed
                    (fn [idx {:keys [x y]}]
                      (str (if (zero? idx) "M" "L")
                           (.toFixed x 2)
                           " "
                           (.toFixed y 2)))
                    points)]
      (str/join " " segments))))

(defn- activity-fills
  [webdata]
  (let [fills (or (:fills webdata)
                  (get-in webdata [:data :fills])
                  (get-in webdata [:user :fills])
                  [])]
    (->> (if (sequential? fills) fills [])
         (keep (fn [row]
                 (when (map? row)
                   {:time-ms (or (optional-number (:time row))
                                 (optional-number (:timestamp row))
                                 (optional-number (:timeMs row)))
                    :coin (some-> (or (:coin row)
                                      (:symbol row)
                                      (:asset row))
                                  str
                                  str/trim)
                    :side (some-> (or (:side row)
                                      (:dir row))
                                  str
                                  str/trim)
                    :size (optional-number (or (:sz row)
                                               (:size row)))
                    :price (optional-number (or (:px row)
                                                (:price row)))})))
         (sort-by (fn [{:keys [time-ms]}]
                    (or time-ms 0))
                  >)
         (take 8)
         vec)))

(defn- snapshot-value-by-range
  [row snapshot-range]
  (normalize-percent-value
   (last-snapshot-value (get-in row [:snapshot-by-key snapshot-range]))))

(defn vault-detail-vm
  [state]
  (let [route (get-in state [:router :path])
        {:keys [kind vault-address]} (vault-actions/parse-vault-route route)
        detail-tab (vault-actions/normalize-vault-detail-tab
                    (get-in state [:vaults-ui :detail-tab]))
        snapshot-range (vault-actions/normalize-vault-snapshot-range
                        (get-in state [:vaults-ui :snapshot-range]))
        detail-loading? (true? (get-in state [:vaults-ui :detail-loading?]))
        details (get-in state [:vaults :details-by-address vault-address])
        row (row-by-address state vault-address)
        webdata (get-in state [:vaults :webdata-by-vault vault-address])
        user-equity (get-in state [:vaults :user-equity-by-address vault-address])
        tvl (or (optional-number (:tvl details))
                (optional-number (:tvl row))
                0)
        apr (or (optional-number (:apr details))
                (optional-number (:apr row))
                0)
        month-return (snapshot-value-by-range row :month)
        your-deposit (or (optional-number (:equity user-equity))
                         (optional-number (get-in details [:follower-state :vault-equity]))
                         0)
        all-time-earned (or (optional-number (get-in details [:follower-state :all-time-pnl]))
                            0)
        summary (portfolio-summary details snapshot-range)
        account-value-history (history-points (:accountValueHistory summary))
        render-points (chart-render-points account-value-history)
        relationship (or (:relationship details)
                         (:relationship row)
                         {:type :normal})
        detail-error (get-in state [:vaults :errors :details-by-address vault-address])
        webdata-error (get-in state [:vaults :errors :webdata-by-vault vault-address])]
    {:kind kind
     :vault-address vault-address
     :invalid-address? (and (= :detail kind)
                            (nil? vault-address))
     :loading? detail-loading?
     :error (or detail-error webdata-error)
     :name (or (:name details)
               (:name row)
               vault-address
               "Vault")
     :leader (or (:leader details)
                 (:leader row))
     :description (or (:description details) "")
     :relationship relationship
     :allow-deposits? (true? (:allow-deposits? details))
     :always-close-on-withdraw? (true? (:always-close-on-withdraw? details))
     :followers (or (optional-number (:followers details)) 0)
     :leader-commission (normalize-percent-value (:leader-commission details))
     :leader-fraction (normalize-percent-value (:leader-fraction details))
     :metrics {:tvl tvl
               :past-month-return month-return
               :your-deposit your-deposit
               :all-time-earned all-time-earned
               :apr (normalize-percent-value apr)}
     :tabs [{:value :about
             :label "About"}
            {:value :vault-performance
             :label "Vault Performance"}
            {:value :your-performance
             :label "Your Performance"}]
     :selected-tab detail-tab
     :snapshot-range snapshot-range
     :snapshot {:day (snapshot-value-by-range row :day)
                :week (snapshot-value-by-range row :week)
                :month (snapshot-value-by-range row :month)
                :all-time (snapshot-value-by-range row :all-time)}
     :chart {:width chart-width
             :height chart-height
             :points render-points
             :path (line-path render-points)}
     :activity-fills (activity-fills webdata)
     :activity-summary {:fill-count (count (activity-fills webdata))
                        :open-order-count (count (if (sequential? (:openOrders webdata))
                                                   (:openOrders webdata)
                                                   []))
                        :position-count (count (if (sequential? (:assetPositions webdata))
                                                 (:assetPositions webdata)
                                                 []))}}))
