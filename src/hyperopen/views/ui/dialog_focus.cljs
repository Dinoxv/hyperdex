(ns hyperopen.views.ui.dialog-focus
  (:require [hyperopen.platform :as platform]))

(def ^:private focusable-selector
  "button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])")

(defn- connected-node?
  [node]
  (and node
       (true? (.-isConnected node))))

(defn- visible-node?
  [node]
  (when (connected-node? node)
    (let [style (js/getComputedStyle node)]
      (and (not= "none" (.-display style))
           (not= "hidden" (.-visibility style))))))

(defn- document-active-element
  []
  (some-> js/globalThis .-document .-activeElement))

(defn- document-body
  []
  (some-> js/globalThis .-document .-body))

(defn- document-query-selector
  [selector]
  (some-> js/globalThis .-document (.querySelector selector)))

(defn- contained-by?
  [parent child]
  (and parent
       child
       (or (= parent child)
           (.contains parent child))))

(defn- focusable-nodes
  [node]
  (->> (.querySelectorAll node focusable-selector)
       array-seq
       (filter visible-node?)
       vec))

(defn- focus-node!
  [node]
  (when node
    (platform/queue-microtask!
     (fn []
       (when (visible-node? node)
         (.focus node))))))

(defn- css-escape
  [value]
  (if-let [escape-fn (some-> js/globalThis .-CSS .-escape)]
    (escape-fn value)
    value))

(defn- focus-restore-selector
  [node]
  (when node
    (let [id (some-> node (.getAttribute "id"))
          data-role (some-> node (.getAttribute "data-role"))
          data-parity-id (some-> node (.getAttribute "data-parity-id"))]
      (cond
        (seq id) (str "#" (css-escape id))
        (seq data-role) (str "[data-role=\"" (css-escape data-role) "\"]")
        (seq data-parity-id) (str "[data-parity-id=\"" (css-escape data-parity-id) "\"]")
        :else nil))))

(defn- restore-focus!
  [previous-active-element restore-selector]
  (letfn [(resolve-restore-node []
            (let [candidate (cond
                              (visible-node? previous-active-element)
                              previous-active-element

                              (seq restore-selector)
                              (document-query-selector restore-selector)

                              :else nil)]
              (when (and candidate
                         (not= candidate (document-body))
                         (visible-node? candidate))
                candidate)))
          (attempt-restore!
            [attempts-left]
            (let [candidate (resolve-restore-node)]
              (when candidate
                (.focus candidate))
              (when (and (pos? attempts-left)
                         (not= candidate (document-active-element)))
                (js/setTimeout
                 (fn []
                   (attempt-restore! (dec attempts-left)))
                 32))
              candidate))]
    (platform/queue-microtask!
     (fn []
       (attempt-restore! 6)))))

(defn- trap-tab-key!
  [event node]
  (when (= "Tab" (.-key event))
    (let [focusables (focusable-nodes node)
          active-element (document-active-element)
          active-inside? (contained-by? node active-element)
          first-focusable (first focusables)
          last-focusable (last focusables)
          shift? (true? (.-shiftKey event))]
      (cond
        (empty? focusables)
        (do
          (.preventDefault event)
          (focus-node! node))

        (and shift?
             (or (not active-inside?)
                 (= active-element first-focusable)
                 (= active-element node)))
        (do
          (.preventDefault event)
          (focus-node! last-focusable))

        (and (not shift?)
             (or (not active-inside?)
                 (= active-element last-focusable)
                 (= active-element node)))
        (do
          (.preventDefault event)
          (focus-node! first-focusable))))))

(defn dialog-focus-on-render
  ([] (dialog-focus-on-render {}))
  ([{:keys [restore-selector]}]
  (fn [{:keys [:replicant/life-cycle :replicant/node :replicant/memory :replicant/remember]}]
    (case life-cycle
      :replicant.life-cycle/mount
      (let [previous-active-element (document-active-element)
            restore-selector* (or (focus-restore-selector previous-active-element)
                                  restore-selector)
            on-keydown (fn [event]
                         (trap-tab-key! event node))]
        (.addEventListener node "keydown" on-keydown)
        (focus-node! (or (first (focusable-nodes node))
                         node))
        (remember {:on-keydown on-keydown
                   :previous-active-element previous-active-element
                   :restore-selector restore-selector*}))

      :replicant.life-cycle/update
      (do
        (when-not (contained-by? node (document-active-element))
          (focus-node! (or (first (focusable-nodes node))
                           node)))
        (remember memory))

      :replicant.life-cycle/unmount
      (do
        (when-let [on-keydown (:on-keydown memory)]
          (.removeEventListener node "keydown" on-keydown))
        (restore-focus! (:previous-active-element memory)
                        (:restore-selector memory)))

      nil))))
