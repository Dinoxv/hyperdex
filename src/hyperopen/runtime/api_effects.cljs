(ns hyperopen.runtime.api-effects)

(defn fetch-asset-selector-markets!
  [{:keys [store opts fetch-asset-selector-markets-fn]}]
  (fetch-asset-selector-markets-fn store (or opts {:phase :full})))

(defn load-user-data!
  [{:keys [store
           address
           fetch-frontend-open-orders!
           fetch-user-fills!
           fetch-and-merge-funding-history!]}]
  (when address
    (fetch-frontend-open-orders! store address)
    (fetch-user-fills! store address)
    (fetch-and-merge-funding-history! store address {:priority :high})))
