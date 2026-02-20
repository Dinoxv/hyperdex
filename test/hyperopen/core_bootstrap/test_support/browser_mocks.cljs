(ns hyperopen.core-bootstrap.test-support.browser-mocks)

(defn with-test-local-storage [f]
  (let [original-local-storage (.-localStorage js/globalThis)
        storage (atom {})]
    (set! (.-localStorage js/globalThis)
          #js {:setItem (fn [key value]
                          (swap! storage assoc (str key) (str value)))
               :getItem (fn [key]
                          (get @storage (str key)))
               :removeItem (fn [key]
                             (swap! storage dissoc (str key)))
               :clear (fn []
                        (reset! storage {}))})
    (try
      (f)
      (finally
        (set! (.-localStorage js/globalThis) original-local-storage)))))

(defn with-test-navigator [navigator-value f]
  (let [navigator-prop "navigator"
        original-navigator-descriptor (js/Object.getOwnPropertyDescriptor js/globalThis navigator-prop)]
    (js/Object.defineProperty js/globalThis navigator-prop
                              #js {:value navigator-value
                                   :configurable true})
    (try
      (f)
      (finally
        (if original-navigator-descriptor
          (js/Object.defineProperty js/globalThis navigator-prop original-navigator-descriptor)
          (js/Reflect.deleteProperty js/globalThis navigator-prop))))))
