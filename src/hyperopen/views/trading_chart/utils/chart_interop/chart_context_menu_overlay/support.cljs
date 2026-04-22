(ns hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.support)

(defonce ^:private chart-context-menu-overlay-sidecar (js/WeakMap.))

(def panel-width-px 196)
(def panel-padding-px 6)
(def row-height-px 32)
(def divider-height-px 1)
(def anchor-offset-px 8)
(def edge-padding-px 8)
(def copy-feedback-duration-ms 900)

(defn menu-height-px []
  (+ (* row-height-px 2)
     divider-height-px
     (* panel-padding-px 2)))

(defn overlay-state
  [chart-obj]
  (if chart-obj
    (or (.get chart-context-menu-overlay-sidecar chart-obj) {})
    {}))

(defn set-overlay-state!
  [chart-obj state]
  (when chart-obj
    (.set chart-context-menu-overlay-sidecar chart-obj state))
  state)

(defn update-overlay-state!
  [chart-obj f & args]
  (let [next-state (apply f (overlay-state chart-obj) args)]
    (set-overlay-state! chart-obj next-state)))

(defn delete-overlay-state!
  [chart-obj]
  (when chart-obj
    (.delete chart-context-menu-overlay-sidecar chart-obj)))

(defn resolve-document
  [document]
  (or document
      (some-> js/globalThis .-document)))

(defn resolve-window
  [document]
  (or (some-> document .-defaultView)
      js/globalThis))

(defn resolve-clipboard
  [clipboard]
  (or clipboard
      (some-> js/globalThis .-navigator .-clipboard)))

(defn default-set-timeout!
  [callback ms]
  (js/setTimeout callback ms))

(defn default-clear-timeout!
  [timeout-id]
  (js/clearTimeout timeout-id))

(defn invoke-method
  [target method-name & args]
  (let [method (when target
                 (aget target method-name))]
    (when (fn? method)
      (.apply method target (to-array args)))))

(defn parse-number
  [value]
  (cond
    (number? value) (when (js/isFinite value) value)
    (string? value) (let [num (js/parseFloat value)]
                      (when (js/isFinite num) num))
    :else nil))

(defn finite-number?
  [value]
  (boolean (parse-number value)))

(defn set-style-value!
  [style prop value]
  (when (and style
             (not= (aget style prop) value))
    (aset style prop value)
    true))

(defn ensure-relative-container!
  [container]
  (let [style (.-style container)]
    (when (or (not (.-position style))
              (= (.-position style) "static"))
      (set! (.-position style) "relative"))))

(defn node-inside?
  [ancestor node]
  (loop [current node]
    (cond
      (nil? ancestor) false
      (nil? current) false
      (identical? ancestor current) true
      :else (recur (.-parentNode current)))))

(defn event-target-inside-root?
  [root event]
  (node-inside? root (some-> event .-target)))

(defn focus-node!
  [node]
  (when node
    (let [owner-document (.-ownerDocument node)
          focus-fn (aget node "focus")
          prior-active-element (some-> owner-document .-activeElement)]
      (when (fn? focus-fn)
        (.call focus-fn node))
      ;; Fake DOM nodes do not always implement native focus semantics, so keep
      ;; tests deterministic without overriding focus on real browser elements.
      (when (and owner-document
                 (not (identical? (.-activeElement owner-document) node))
                 (not (identical? prior-active-element node)))
        (try
          (aset owner-document "activeElement" node)
          (catch :default _ nil))))))

(defn blur-node!
  [node]
  (when node
    (let [owner-document (.-ownerDocument node)
          blur-fn (aget node "blur")]
      (when (fn? blur-fn)
        (.call blur-fn node))
      (when (and owner-document
                 (identical? (.-activeElement owner-document) node))
        (try
          (aset owner-document "activeElement" nil)
          (catch :default _ nil))))))
