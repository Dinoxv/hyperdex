(ns hyperopen.portfolio.optimizer.runtime-catalog-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.set :as set]
            [clojure.string :as str]
            [hyperopen.app.actions :as app-actions]
            [hyperopen.app.effects :as app-effects]
            [hyperopen.portfolio.optimizer.runtime-catalog :as optimizer-runtime-catalog]
            [hyperopen.schema.runtime-registration.portfolio :as portfolio-registration]))

(def ^:private fs (js/require "fs"))

(defn- source
  [path]
  (.readFileSync fs path "utf8"))

(deftest app-runtime-merges-optimizer-owned-catalog-test
  (let [actions-source (source "src/hyperopen/app/actions.cljs")
        effects-source (source "src/hyperopen/app/effects.cljs")]
    (is (str/includes? actions-source
                       "hyperopen.portfolio.optimizer.runtime-catalog")
        "app action deps should require the optimizer-owned runtime catalog")
    (is (str/includes? actions-source
                       "(optimizer-runtime-catalog/action-deps)")
        "app action deps should merge the optimizer action catalog once")
    (is (not (str/includes? actions-source
                            ":run-portfolio-optimizer action-adapters/run-portfolio-optimizer-action"))
        "app action deps should not enumerate optimizer handlers inline")
    (is (str/includes? effects-source
                       "hyperopen.portfolio.optimizer.runtime-catalog")
        "app effect deps should require the optimizer-owned runtime catalog")
    (is (str/includes? effects-source
                       "(optimizer-runtime-catalog/effect-deps runtime)")
        "app effect deps should merge the optimizer effect catalog once")
    (is (not (str/includes? effects-source
                            ":run-portfolio-optimizer\n"))
        "app effect deps should not enumerate optimizer handlers inline")))

(defn- optimizer-handler-key?
  [handler-key]
  (str/includes? (name handler-key) "portfolio-optimizer"))

(deftest optimizer-catalog-covers-registration-handler-keys-test
  (let [action-catalog-keys (set (keys (:portfolio-optimizer
                                        (optimizer-runtime-catalog/action-deps))))
        effect-catalog-keys (set (keys (:portfolio-optimizer
                                        (optimizer-runtime-catalog/effect-deps nil))))
        action-registration-keys (->> portfolio-registration/action-binding-rows
                                      (map second)
                                      (filter optimizer-handler-key?)
                                      set)
        effect-registration-keys (->> portfolio-registration/effect-binding-rows
                                      (map second)
                                      (filter optimizer-handler-key?)
                                      set)]
    (is (= action-registration-keys action-catalog-keys)
        (str "optimizer action catalog drifted from registration rows: missing="
             (pr-str (set/difference action-registration-keys action-catalog-keys))
             " extra="
             (pr-str (set/difference action-catalog-keys action-registration-keys))))
    (is (= effect-registration-keys effect-catalog-keys)
        (str "optimizer effect catalog drifted from registration rows: missing="
             (pr-str (set/difference effect-registration-keys effect-catalog-keys))
             " extra="
             (pr-str (set/difference effect-catalog-keys effect-registration-keys))))))

(deftest app-runtime-consumes-optimizer-catalog-values-test
  (let [runtime {:runtime-id :optimizer-catalog-test}
        action-handler (fn [& _] :sentinel-action)
        effect-handler (fn [& _] :sentinel-effect)]
    (with-redefs [optimizer-runtime-catalog/action-deps
                  (fn []
                    {:portfolio-optimizer
                     {:sentinel-action action-handler}})
                  optimizer-runtime-catalog/effect-deps
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    {:portfolio-optimizer
                     {:sentinel-effect effect-handler}})]
      (is (identical? action-handler
                      (get-in (app-actions/runtime-action-deps)
                              [:portfolio-optimizer :sentinel-action])))
      (is (identical? effect-handler
                      (get-in (app-effects/runtime-effect-deps runtime)
                              [:portfolio-optimizer :sentinel-effect]))))))
