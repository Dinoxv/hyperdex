(ns hyperopen.views.footer-view-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.views.footer-view :as footer-view]))

(defn- find-node [pred node]
  (cond
    (vector? node)
    (or (when (pred node) node)
        (some #(find-node pred %) (rest node)))

    (seq? node)
    (some #(find-node pred %) node)

    :else nil))

(deftest retry-button-visible-when-disconnected-test
  (let [view (footer-view/footer-view {:websocket {:status :disconnected}})
        retry-btn (find-node #(and (vector? %)
                                   (keyword? (first %))
                                   (str/starts-with? (name (first %)) "button")
                                   (= "Retry" (last %)))
                             view)]
    (is retry-btn)
    (is (= [[:actions/reconnect-websocket]]
           (get-in retry-btn [1 :on :click])))))

(deftest retry-button-hidden-when-connected-test
  (let [view (footer-view/footer-view {:websocket {:status :connected}})
        retry-btn (find-node #(and (vector? %)
                                   (keyword? (first %))
                                   (str/starts-with? (name (first %)) "button")
                                   (= "Retry" (last %)))
                             view)]
    (is (nil? retry-btn))))
