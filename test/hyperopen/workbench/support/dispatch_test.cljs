(ns hyperopen.workbench.support.dispatch-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [hyperopen.workbench.support.dispatch :as dispatch]
            [hyperopen.workbench.support.state :as ws]))

(defn- reset-dispatch-fixture
  [f]
  (dispatch/reset-registry!)
  (f)
  (dispatch/reset-registry!))

(use-fixtures :each reset-dispatch-fixture)

(defn- scene-node
  [scene-id]
  #js {:getAttribute (fn [attr]
                       (when (= attr "data-workbench-scene-id")
                         scene-id))
       :parentNode nil})

(deftest dispatch-interpolates-event-placeholders-into-scene-reducers-test
  (let [store (ws/create-store ::dispatch-test {:search "" :checked? false})
        scene-id (dispatch/install-dispatch! store
                                             {:actions/set-search
                                              (fn [state _dispatch-data value]
                                                (assoc state :search value))

                                              :actions/set-checked
                                              (fn [state _dispatch-data checked?]
                                                (assoc state :checked? checked?))})
        node (scene-node (dispatch/scene-attr store))
        dom-event #js {:target #js {:value "btc"
                                    :checked true}}]
    (dispatch/dispatch! {:replicant/node node
                         :replicant/dom-event dom-event}
                        [[:actions/set-search :event.target/value]
                         [:actions/set-checked :event.target/checked]])
    (is (= "btc" (:search @store)))
    (is (true? (:checked? @store)))))

(deftest dispatch-resolves-current-target-bounds-placeholder-test
  (let [store (ws/create-store ::bounds-test {:anchor nil})
        _scene-id (dispatch/install-dispatch! store
                                              {:actions/set-anchor
                                               (fn [state _dispatch-data bounds]
                                                 (assoc state :anchor bounds))})
        node (scene-node (dispatch/scene-attr store))
        dom-event #js {:currentTarget #js {:getBoundingClientRect
                                           (fn []
                                             #js {:left 100
                                                  :right 180
                                                  :top 240
                                                  :bottom 300
                                                  :width 80
                                                  :height 60})}}]
    (dispatch/dispatch! {:replicant/node node
                         :replicant/dom-event dom-event}
                        [[:actions/set-anchor :event.currentTarget/bounds]])
    (is (= {:left 100
            :right 180
            :top 240
            :bottom 300
            :width 80
            :height 60
            :viewport-width (some-> js/globalThis .-innerWidth)
            :viewport-height (some-> js/globalThis .-innerHeight)}
           (:anchor @store)))))
