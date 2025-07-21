(ns hyperopen.utils.data-normalization)

(defn normalize-asset-contexts
  "Takes the raw vector `data` returned by /info(type=metaAndAssetCtxs)`
   and returns a map keyed by asset keyword, each value containing:
   * :info        → entry from universe
   * :margin      → resolved margin-table
   * :funding     → funding/latestPx struct
   * :idx         → original index (handy for other endpoint look-ups)"
  [data]
  (let [[{:keys [universe marginTables]} funding] data
        margin-map (into {} marginTables)]
    (->> (map-indexed vector universe)
         ;; Filter out assets with zero volume and open interest
         (filter (fn [[idx {:keys [name] :as info}]]
                   (let [funding-data (nth funding idx)
                         day-ntl-vlm (js/parseFloat (:dayNtlVlm funding-data))
                         open-interest (js/parseFloat (:openInterest funding-data))]
                     (and (not (js/isNaN day-ntl-vlm)) (> day-ntl-vlm 0)
                          (not (js/isNaN open-interest)) (> open-interest 0)))))
         ;; Build normalized map
         (reduce (fn [m [idx {:keys [name marginTableId] :as info}]]
                   (assoc m (keyword name)
                          {:idx     idx
                           :info    info
                           :margin  (margin-map marginTableId)
                           :funding (nth funding idx)}))
                 {}))))

(defn preprocess-webdata2
  "Given a response map with keys
     :meta       → {:universe […] :marginTables […]}
     :assetCtxs  → [ {…} {…} … ]
   return the [meta assetCtxs] vector that normalize-asset-contexts needs."
  [{:keys [meta assetCtxs]}]
  ;; pull out just the universe and marginTables from meta:
  [ (select-keys meta [:universe :marginTables])
    ;; ensure it’s a plain vector in case it was some other seq:
    (vec assetCtxs) ]) 