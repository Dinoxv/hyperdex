(ns hyperopen.views.ui.dialog-focus-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.platform :as platform]
            [hyperopen.views.ui.dialog-focus :as dialog-focus]))

(defn- make-focus-node
  ([document]
   (make-focus-node document {}))
  ([document attrs]
  (let [focus-calls (atom 0)
        node #js {:isConnected true}]
    (aset node
          "getAttribute"
          (fn [attr-name]
            (get attrs (keyword attr-name))))
    (aset node
          "focus"
          (fn []
            (swap! focus-calls inc)
            (set! (.-activeElement document) node)))
    {:node node
     :focus-calls focus-calls})))

(defn- make-dialog-node
  [document children]
  (let [listeners (atom {})
        node #js {:isConnected true}]
    (aset node
          "querySelectorAll"
          (fn [_selector]
            (into-array children)))
    (aset node
          "contains"
          (fn [candidate]
            (boolean
             (or (= candidate node)
                 (some #(= candidate %) children)))))
    (aset node
          "addEventListener"
          (fn [event-name handler]
            (swap! listeners assoc event-name handler)))
    (aset node
          "removeEventListener"
          (fn [event-name _handler]
            (swap! listeners dissoc event-name)))
    (aset node
          "focus"
          (fn []
            (set! (.-activeElement document) node)))
    {:node node
     :listeners listeners}))

(deftest dialog-focus-on-render-traps-tab-and-restores-previous-focus-test
  (let [original-document (.-document js/globalThis)
        original-get-computed-style (.-getComputedStyle js/globalThis)
        document #js {}
        body-node (:node (make-focus-node document))
        opener (make-focus-node document)
        first-focusable (make-focus-node document)
        last-focusable (make-focus-node document)
        {:keys [node listeners]} (make-dialog-node document [(:node first-focusable)
                                                             (:node last-focusable)])
        remembered* (atom nil)
        prevented* (atom 0)
        on-render (dialog-focus/dialog-focus-on-render)]
    (set! (.-body document) body-node)
    (set! (.-activeElement document) (:node opener))
    (set! (.-document js/globalThis) document)
    (set! (.-getComputedStyle js/globalThis)
          (fn [_node]
            #js {:display "block"
                 :visibility "visible"}))
    (try
      (with-redefs [platform/queue-microtask! (fn [f] (f))]
        (on-render {:replicant/life-cycle :replicant.life-cycle/mount
                    :replicant/node node
                    :replicant/remember (fn [memory]
                                          (reset! remembered* memory))})
        (is (= 1 @(-> first-focusable :focus-calls)))
        (is (= (:node first-focusable) (.-activeElement document)))
        (is (fn? (get @listeners "keydown")))

        (set! (.-activeElement document) (:node last-focusable))
        ((get @listeners "keydown")
         #js {:key "Tab"
              :shiftKey false
              :preventDefault (fn []
                                (swap! prevented* inc))})
        (is (= 1 @prevented*))
        (is (= (:node first-focusable) (.-activeElement document)))

        (set! (.-activeElement document) (:node first-focusable))
        ((get @listeners "keydown")
         #js {:key "Tab"
              :shiftKey true
              :preventDefault (fn []
                                (swap! prevented* inc))})
        (is (= 2 @prevented*))
        (is (= (:node last-focusable) (.-activeElement document)))

        (on-render {:replicant/life-cycle :replicant.life-cycle/unmount
                    :replicant/node node
                    :replicant/memory @remembered*})
        (is (nil? (get @listeners "keydown")))
        (is (= 1 @(-> opener :focus-calls)))
        (is (= (:node opener) (.-activeElement document))))
      (finally
        (set! (.-document js/globalThis) original-document)
        (set! (.-getComputedStyle js/globalThis) original-get-computed-style)))))

(deftest dialog-focus-on-render-refocuses-when-focus-escapes-on-update-test
  (let [original-document (.-document js/globalThis)
        original-get-computed-style (.-getComputedStyle js/globalThis)
        document #js {}
        body-node (:node (make-focus-node document))
        opener (make-focus-node document)
        first-focusable (make-focus-node document)
        second-focusable (make-focus-node document)
        outside-node (:node (make-focus-node document))
        {:keys [node listeners]} (make-dialog-node document [(:node first-focusable)
                                                             (:node second-focusable)])
        remembered* (atom nil)
        on-render (dialog-focus/dialog-focus-on-render)]
    (set! (.-body document) body-node)
    (set! (.-activeElement document) (:node opener))
    (set! (.-document js/globalThis) document)
    (set! (.-getComputedStyle js/globalThis)
          (fn [_node]
            #js {:display "block"
                 :visibility "visible"}))
    (try
      (with-redefs [platform/queue-microtask! (fn [f] (f))]
        (on-render {:replicant/life-cycle :replicant.life-cycle/mount
                    :replicant/node node
                    :replicant/remember (fn [memory]
                                          (reset! remembered* memory))})
        (is (= 1 @(-> first-focusable :focus-calls)))

        (set! (.-activeElement document) outside-node)
        (on-render {:replicant/life-cycle :replicant.life-cycle/update
                    :replicant/node node
                    :replicant/memory @remembered*
                    :replicant/remember (fn [memory]
                                          (reset! remembered* memory))})
        (is (= 2 @(-> first-focusable :focus-calls)))
        (is (= (:node first-focusable) (.-activeElement document)))
        (is (fn? (get @listeners "keydown"))))
      (finally
        (set! (.-document js/globalThis) original-document)
        (set! (.-getComputedStyle js/globalThis) original-get-computed-style)))))

(deftest dialog-focus-on-render-restores-focus-via-selector-when-opener-is-replaced-test
  (let [original-document (.-document js/globalThis)
        original-get-computed-style (.-getComputedStyle js/globalThis)
        document #js {}
        body-node (:node (make-focus-node document))
        opener (make-focus-node document {:data-role "funding-action-deposit"})
        replacement-opener (make-focus-node document {:data-role "funding-action-deposit"})
        first-focusable (make-focus-node document)
        {:keys [node]} (make-dialog-node document [(:node first-focusable)])
        remembered* (atom nil)
        on-render (dialog-focus/dialog-focus-on-render)]
    (set! (.-body document) body-node)
    (set! (.-activeElement document) (:node opener))
    (aset document
          "querySelector"
          (fn [selector]
            (when (= selector "[data-role=\"funding-action-deposit\"]")
              (:node replacement-opener))))
    (set! (.-document js/globalThis) document)
    (set! (.-getComputedStyle js/globalThis)
          (fn [_node]
            #js {:display "block"
                 :visibility "visible"}))
    (try
      (with-redefs [platform/queue-microtask! (fn [f] (f))]
        (on-render {:replicant/life-cycle :replicant.life-cycle/mount
                    :replicant/node node
                    :replicant/remember (fn [memory]
                                          (reset! remembered* memory))})
        (set! (.-isConnected (:node opener)) false)
        (on-render {:replicant/life-cycle :replicant.life-cycle/unmount
                    :replicant/node node
                    :replicant/memory @remembered*})
        (is (= 1 @(-> replacement-opener :focus-calls)))
        (is (= (:node replacement-opener) (.-activeElement document))))
      (finally
        (set! (.-document js/globalThis) original-document)
        (set! (.-getComputedStyle js/globalThis) original-get-computed-style)))))
