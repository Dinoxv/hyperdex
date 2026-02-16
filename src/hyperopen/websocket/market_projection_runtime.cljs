(ns hyperopen.websocket.market-projection-runtime
  (:require [hyperopen.platform :as platform]))

(defonce market-projection-runtime
  (atom {:stores {}}))

(def ^:private scheduled-frame-handle
  ::scheduled-frame-handle)

(defn- default-apply-store!
  [store apply-update-fn]
  (swap! store apply-update-fn))

(defn- ordered-pending-updates
  [pending]
  (sort-by (comp pr-str key) pending))

(defn- clear-store-runtime
  [state store]
  (let [entry (get-in state [:stores store])]
    (if (and (map? entry)
             (empty? (:pending entry))
             (nil? (:frame-handle entry)))
      (update state :stores dissoc store)
      state)))

(defn- drain-store-pending!
  [store]
  (let [drained (volatile! {})]
    (swap! market-projection-runtime
           (fn [state]
             (let [pending (get-in state [:stores store :pending] {})]
               (vreset! drained pending)
               (-> state
                   (assoc-in [:stores store :pending] {})
                   (assoc-in [:stores store :frame-handle] nil)
                   (clear-store-runtime store)))))
    @drained))

(defn flush-store-updates!
  [{:keys [store apply-store!]
    :or {apply-store! default-apply-store!}}]
  (when store
    (let [pending (drain-store-pending! store)]
      (when (seq pending)
        (apply-store!
         store
         (fn [state]
           (reduce (fn [acc [_ apply-update-fn]]
                     (apply-update-fn acc))
                   state
                   (ordered-pending-updates pending))))))))

(defn queue-market-projection!
  [{:keys [store
           coalesce-key
           apply-update-fn
           schedule-animation-frame!
           apply-store!]
    :or {schedule-animation-frame! platform/request-animation-frame!
         apply-store! default-apply-store!}}]
  (when (and store
             (some? coalesce-key)
             (fn? apply-update-fn))
    (let [schedule? (volatile! false)]
      (swap! market-projection-runtime
             (fn [state]
               (let [state* (assoc-in state
                                      [:stores store :pending coalesce-key]
                                      apply-update-fn)
                     frame-handle (get-in state* [:stores store :frame-handle])]
                 (if (some? frame-handle)
                   state*
                   (do
                     (vreset! schedule? true)
                     (assoc-in state*
                               [:stores store :frame-handle]
                               scheduled-frame-handle))))))
      (when @schedule?
        (let [frame-handle (schedule-animation-frame!
                            (fn [_]
                              (flush-store-updates! {:store store
                                                     :apply-store! apply-store!})))]
          (swap! market-projection-runtime
                 (fn [state]
                   (if (= scheduled-frame-handle
                          (get-in state [:stores store :frame-handle]))
                     (assoc-in state [:stores store :frame-handle] frame-handle)
                     state))))))))

(defn reset-market-projection-runtime!
  []
  (reset! market-projection-runtime {:stores {}}))
