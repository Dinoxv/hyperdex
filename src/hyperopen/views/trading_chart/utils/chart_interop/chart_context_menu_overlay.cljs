(ns hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay
  (:require [hyperopen.utils.formatting :as fmt]
            [hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.geometry :as geometry]
            [hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.listeners :as listeners]
            [hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.presentation :as presentation]
            [hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.pricing :as pricing]
            [hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.support :as support]))

(declare close-menu!)

(defn- overlay-state
  [chart-obj]
  (support/overlay-state chart-obj))

(defn- focus-menu-item!
  [chart-obj item-id]
  (let [state (support/overlay-state chart-obj)
        button (case item-id
                 :reset-view (:reset-button state)
                 :copy-price (:copy-button state)
                 nil)]
    (when button
      (doseq [item (remove nil? [(:reset-button state)
                                 (:copy-button state)])]
        (presentation/set-button-highlight! item false))
      (presentation/set-button-highlight! button true)
      (support/focus-node! button)
      (support/update-overlay-state! chart-obj assoc :focused-item item-id))))

(defn- clear-copy-feedback-timeout!
  [chart-obj]
  (let [{:keys [copy-feedback-timeout-id clear-timeout-fn]} (support/overlay-state chart-obj)]
    (when (and copy-feedback-timeout-id (fn? clear-timeout-fn))
      (clear-timeout-fn copy-feedback-timeout-id))
    (support/update-overlay-state! chart-obj dissoc :copy-feedback-timeout-id)))

(defn- close-menu!
  ([chart-obj]
   (close-menu! chart-obj {}))
  ([chart-obj {:keys [restore-focus?]
               :or {restore-focus? true}}]
   (when chart-obj
     (clear-copy-feedback-timeout! chart-obj)
     (let [{:keys [copy-button reset-button focus-return-target root]} (support/overlay-state chart-obj)
           active-element (some-> root .-ownerDocument .-activeElement)
           active-inside-menu? (support/node-inside? root active-element)]
       (support/update-overlay-state! chart-obj assoc
                                      :menu-open? false
                                      :anchor nil
                                      :copy-label nil
                                      :copy-enabled? false
                                      :copied? false
                                      :focused-item nil)
       (presentation/set-button-highlight! reset-button false)
       (presentation/set-button-highlight! copy-button false)
       (presentation/sync-copy-button-label! chart-obj)
       (presentation/sync-root-visibility! chart-obj)
       (cond
         restore-focus?
         (support/focus-node! focus-return-target)

         active-inside-menu?
         (support/blur-node! active-element))))))

(defn- schedule-copy-feedback-close!
  [chart-obj]
  (clear-copy-feedback-timeout! chart-obj)
  (let [{:keys [set-timeout-fn]} (support/overlay-state chart-obj)
        timeout-id ((or set-timeout-fn support/default-set-timeout!)
                    (fn []
                      (support/update-overlay-state! chart-obj assoc :copied? false)
                      (presentation/sync-copy-button-label! chart-obj)
                      (close-menu! chart-obj))
                    support/copy-feedback-duration-ms)]
    (support/update-overlay-state! chart-obj assoc :copy-feedback-timeout-id timeout-id)))

(defn- open-menu!
  [chart-obj anchor]
  (let [{:keys [container format-price candles root panel context-key price-decimals]}
        (support/overlay-state chart-obj)
        {:keys [copy-enabled? label]} (pricing/resolve-copy-price-data chart-obj
                                                                       candles
                                                                       anchor
                                                                       format-price
                                                                       price-decimals)
        position (geometry/menu-position container anchor)]
    (support/update-overlay-state! chart-obj assoc
                                   :menu-open? true
                                   :anchor anchor
                                   :copy-label label
                                   :copy-enabled? copy-enabled?
                                   :copied? false
                                   :focus-return-target container
                                   :open-context-key context-key)
    (when root
      (let [style (.-style root)]
        (support/set-style-value! style "left" (str (:left position) "px"))
        (support/set-style-value! style "top" (str (:top position) "px"))))
    (presentation/sync-reset-button! chart-obj)
    (presentation/sync-copy-button-label! chart-obj)
    (presentation/sync-root-visibility! chart-obj)
    (focus-menu-item! chart-obj :reset-view)
    (when (and panel (not copy-enabled?))
      (.setAttribute panel "data-copy-state" "disabled"))))

(defn- copy-price!
  [chart-obj]
  (let [{:keys [copy-label copy-enabled? clipboard]} (support/overlay-state chart-obj)
        clipboard* (support/resolve-clipboard clipboard)
        write-text-fn (some-> clipboard* .-writeText)]
    (when copy-enabled?
      (cond
        (not (and clipboard* (fn? write-text-fn)))
        (close-menu! chart-obj)

        :else
        (try
          (-> (.writeText clipboard* copy-label)
              (.then (fn []
                       (support/update-overlay-state! chart-obj assoc :copied? true)
                       (presentation/sync-copy-button-label! chart-obj)
                       (schedule-copy-feedback-close! chart-obj)))
              (.catch (fn [_]
                        (close-menu! chart-obj))))
          (catch :default _
            (close-menu! chart-obj)))))))

(defn- perform-action!
  [chart-obj action-id]
  (case action-id
    :reset-view (do
                  (when-let [on-reset (:on-reset (support/overlay-state chart-obj))]
                    (on-reset))
                  (close-menu! chart-obj))
    :copy-price (copy-price! chart-obj)
    nil))

(defn- button-keydown-handler
  [chart-obj item-id]
  (fn [event]
    (let [key (.-key event)
          state (support/overlay-state chart-obj)
          items (vec (remove nil? [{:id :reset-view :button (:reset-button state)}
                                   (when (:copy-enabled? state)
                                     {:id :copy-price :button (:copy-button state)})]))
          ids (mapv :id items)
          current-index (or (first (keep-indexed (fn [idx id]
                                                   (when (= id item-id)
                                                     idx))
                                                 ids))
                            0)
          next-id (fn [direction]
                    (when (seq ids)
                      (nth ids (mod (+ current-index direction) (count ids)))))]
      (case key
        "ArrowDown" (do
                      (.preventDefault event)
                      (.stopPropagation event)
                      (when-let [target-id (next-id 1)]
                        (focus-menu-item! chart-obj target-id)))
        "ArrowUp" (do
                    (.preventDefault event)
                    (.stopPropagation event)
                    (when-let [target-id (next-id -1)]
                      (focus-menu-item! chart-obj target-id)))
        "Escape" (do
                   (.preventDefault event)
                   (.stopPropagation event)
                   (close-menu! chart-obj))
        "Enter" (do
                  (.preventDefault event)
                  (.stopPropagation event)
                  (perform-action! chart-obj item-id))
        " " (do
              (.preventDefault event)
              (.stopPropagation event)
              (perform-action! chart-obj item-id))
        nil))))

(defn clear-chart-context-menu-overlay!
  [chart-obj]
  (when chart-obj
    (clear-copy-feedback-timeout! chart-obj)
    (listeners/teardown-container-listeners! (:container-listeners (support/overlay-state chart-obj)))
    (listeners/teardown-document-listeners! (:document-listeners (support/overlay-state chart-obj)))
    (when-let [root (:root (support/overlay-state chart-obj))]
      (when-let [parent (.-parentNode root)]
        (.removeChild parent root)))
    (support/delete-overlay-state! chart-obj)))

(defn- presentation-callbacks
  [chart-obj]
  {:keydown-handler (partial button-keydown-handler chart-obj)
   :action-handler (partial perform-action! chart-obj)
   :close-menu close-menu!})

(defn- listener-callbacks []
  {:open-menu open-menu!
   :close-menu close-menu!})

(defn- sync-chart-context-menu-overlay-internal!
  [chart-obj container candles {:keys [document
                                       clipboard
                                       format-price
                                       price-decimals
                                       on-reset
                                       context-key
                                       set-timeout-fn
                                       clear-timeout-fn]}]
  (let [state (support/overlay-state chart-obj)
        current-container-listeners (:container-listeners state)
        reuse-container-listeners? (and current-container-listeners
                                        (identical? container (:container current-container-listeners)))
        next-container-listeners (if reuse-container-listeners?
                                   current-container-listeners
                                   (do
                                     (listeners/teardown-container-listeners! current-container-listeners)
                                     (listeners/attach-container-listeners! chart-obj
                                                                            container
                                                                            (listener-callbacks))))
        current-document-listeners (:document-listeners state)
        reuse-document-listeners? (and current-document-listeners
                                       (identical? document (:document current-document-listeners)))
        next-document-listeners (if reuse-document-listeners?
                                  current-document-listeners
                                  (do
                                    (listeners/teardown-document-listeners! current-document-listeners)
                                    (listeners/attach-document-listeners! chart-obj
                                                                         document
                                                                         (listener-callbacks))))
        root-state (presentation/ensure-overlay-root! chart-obj
                                                      container
                                                      document
                                                      (presentation-callbacks chart-obj))]
    (support/set-overlay-state!
     chart-obj
     (assoc state
            :chart-obj chart-obj
            :container container
            :document document
            :window (support/resolve-window document)
            :candles (if (sequential? candles) candles [])
            :clipboard clipboard
            :format-price format-price
            :price-decimals price-decimals
            :on-reset on-reset
            :context-key context-key
            :set-timeout-fn (or set-timeout-fn
                                (:set-timeout-fn state)
                                support/default-set-timeout!)
            :clear-timeout-fn (or clear-timeout-fn
                                  (:clear-timeout-fn state)
                                  support/default-clear-timeout!)
            :root (:root root-state)
            :panel (:panel root-state)
            :reset-button (:reset-button root-state)
            :copy-button (:copy-button root-state)
            :container-listeners next-container-listeners
            :document-listeners next-document-listeners))
    (presentation/sync-reset-button! chart-obj)
    (presentation/sync-copy-button-label! chart-obj)
    (when (and (:menu-open? state)
               (not= (:open-context-key state) context-key))
      (close-menu! chart-obj {:restore-focus? false}))
    (presentation/sync-root-visibility! chart-obj)))

(defn sync-chart-context-menu-overlay!
  ([chart-obj container candles]
   (sync-chart-context-menu-overlay! chart-obj container candles {}))
  ([chart-obj container candles {:keys [document
                                        clipboard
                                        format-price
                                        price-decimals
                                        on-reset
                                        context-key
                                        set-timeout-fn
                                        clear-timeout-fn]
                                 :or {format-price fmt/format-trade-price-plain
                                      on-reset (fn [] nil)}}]
   (if (not (and chart-obj container))
     (clear-chart-context-menu-overlay! chart-obj)
     (let [document* (support/resolve-document document)]
       (if (not document*)
         (clear-chart-context-menu-overlay! chart-obj)
         (sync-chart-context-menu-overlay-internal!
          chart-obj
          container
          candles
          {:document document*
           :clipboard clipboard
           :format-price format-price
           :price-decimals price-decimals
           :on-reset on-reset
           :context-key context-key
           :set-timeout-fn set-timeout-fn
           :clear-timeout-fn clear-timeout-fn}))))))
