(ns hyperopen.views.footer-build-id-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.footer-view :as footer-view]))

(defn- find-node [pred node]
  (cond
    (vector? node)
    (or (when (pred node) node)
        (some #(find-node pred %) (rest node)))

    (seq? node)
    (some #(find-node pred %) node)

    :else nil))

(defn- collect-strings [node]
  (cond
    (string? node) [node]
    (vector? node) (mapcat collect-strings (rest node))
    (seq? node) (mapcat collect-strings node)
    :else []))

(defn- node-text [node]
  (str/join " " (collect-strings node)))

(defn- find-node-by-data-role
  [node data-role]
  (find-node #(and (vector? %)
                   (= data-role (get-in % [1 :data-role])))
             node))

(defn- base-state []
  {:websocket {:health {:generated-at-ms 10000
                        :transport {:state :connected
                                    :freshness :live
                                    :last-recv-at-ms 9500
                                    :expected-traffic? true}
                        :groups {:orders_oms {:worst-status :idle}
                                 :market_data {:worst-status :live}
                                 :account {:worst-status :n-a}}
                        :streams {}}}
   :websocket-ui {:diagnostics-open? false}})

(defn- with-global-build-id
  [build-id f]
  (let [build-id-descriptor (js/Object.getOwnPropertyDescriptor js/globalThis "HYPEROPEN_BUILD_ID")
        had-build-id? (some? build-id-descriptor)]
    (js/Object.defineProperty js/globalThis
                              "HYPEROPEN_BUILD_ID"
                              #js {:value build-id
                                   :configurable true
                                   :writable true})
    (try
      (f)
      (finally
        (if had-build-id?
          (js/Object.defineProperty js/globalThis "HYPEROPEN_BUILD_ID" build-id-descriptor)
          (js/Reflect.deleteProperty js/globalThis "HYPEROPEN_BUILD_ID"))))))

(deftest footer-renders-short-build-id-when-global-build-id-is-present-test
  (with-global-build-id
    "999fe1a1234567890"
    (fn []
      (let [view (footer-view/footer-view (base-state))
            utility-links (find-node-by-data-role view "footer-utility-links")
            build-id-node (find-node-by-data-role utility-links "footer-build-id")
            tooltip-node (find-node-by-data-role utility-links "footer-build-id-tooltip")
            caret-node (find-node-by-data-role utility-links "footer-build-id-tooltip-caret")]
        (is (some? utility-links))
        (is (some? build-id-node))
        (is (some? tooltip-node))
        (is (some? caret-node))
        (is (= "999fe1a" (node-text build-id-node)))
        (is (nil? (get-in build-id-node [1 :title])))
        (is (= #{"Build" "999fe1a1234567890"}
               (set (collect-strings tooltip-node))))))))
