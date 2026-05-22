(ns hyperopen.runtime.effect-adapters.portfolio-optimizer-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.runtime.effect-adapters.portfolio-optimizer :as portfolio-optimizer-adapters]))

(def ^:private optimizer-history-api-browser-override
  @#'hyperopen.runtime.effect-adapters.portfolio-optimizer/optimizer-history-api-browser-override)

(def ^:private normalize-optimizer-history-api-browser-override
  @#'hyperopen.runtime.effect-adapters.portfolio-optimizer/normalize-optimizer-history-api-browser-override)

(def ^:private absent-override ::absent-override)

(defn- with-optimizer-history-api-override
  [value f]
  (let [property-name "__HYPEROPEN_OPTIMIZER_HISTORY_API__"
        original-descriptor (js/Object.getOwnPropertyDescriptor
                             js/globalThis
                             property-name)]
    (if (= absent-override value)
      (js/Reflect.deleteProperty js/globalThis property-name)
      (js/Object.defineProperty js/globalThis
                                property-name
                                #js {:value value
                                     :configurable true
                                     :writable true}))
    (try
      (f)
      (finally
        (if original-descriptor
          (js/Object.defineProperty js/globalThis
                                    property-name
                                    original-descriptor)
          (js/Reflect.deleteProperty js/globalThis property-name))))))

(deftest facade-portfolio-optimizer-adapter-delegates-to-owner-module-test
  (is (identical? portfolio-optimizer-adapters/run-portfolio-optimizer-effect
                  effect-adapters/run-portfolio-optimizer-effect))
  (is (identical? portfolio-optimizer-adapters/run-portfolio-optimizer-pipeline-effect
                  effect-adapters/run-portfolio-optimizer-pipeline-effect))
  (is (identical? portfolio-optimizer-adapters/load-portfolio-optimizer-history-effect
                  effect-adapters/load-portfolio-optimizer-history-effect))
  (is (identical? portfolio-optimizer-adapters/save-portfolio-optimizer-scenario-effect
                  effect-adapters/save-portfolio-optimizer-scenario-effect))
  (is (identical? portfolio-optimizer-adapters/load-portfolio-optimizer-scenario-index-effect
                  effect-adapters/load-portfolio-optimizer-scenario-index-effect))
  (is (identical? portfolio-optimizer-adapters/load-portfolio-optimizer-scenario-effect
                  effect-adapters/load-portfolio-optimizer-scenario-effect))
  (is (identical? portfolio-optimizer-adapters/archive-portfolio-optimizer-scenario-effect
                  effect-adapters/archive-portfolio-optimizer-scenario-effect))
  (is (identical? portfolio-optimizer-adapters/duplicate-portfolio-optimizer-scenario-effect
                  effect-adapters/duplicate-portfolio-optimizer-scenario-effect))
  (is (identical? portfolio-optimizer-adapters/execute-portfolio-optimizer-plan-effect
                  effect-adapters/execute-portfolio-optimizer-plan-effect))
  (is (identical? portfolio-optimizer-adapters/refresh-portfolio-optimizer-tracking-effect
                  effect-adapters/refresh-portfolio-optimizer-tracking-effect))
  (is (identical? portfolio-optimizer-adapters/enable-portfolio-optimizer-manual-tracking-effect
                  effect-adapters/enable-portfolio-optimizer-manual-tracking-effect)))

(deftest run-portfolio-optimizer-effect-calls-run-bridge-with-runtime-store-test
  (let [calls (atom [])
        store (atom {})
        request {:scenario-id "scenario-1"}
        signature {:scenario-id "scenario-1" :revision 1}]
    (with-redefs [portfolio-optimizer-adapters/*request-run!*
                  (fn [payload]
                    (swap! calls conj payload)
                    "run-1")]
      (is (= "run-1"
             (portfolio-optimizer-adapters/run-portfolio-optimizer-effect
              :ctx
              store
              request
              signature
              {:computed-at-ms 123})))
      (is (= [{:request request
               :request-signature signature
               :computed-at-ms 123
               :store store}]
             @calls)))))

(deftest runtime-bound-runner-passes-store-controller-to-run-bridge-test
  (let [calls (atom [])
        runtime (atom {})
        store (atom {})
        request {:scenario-id "scenario-1"}
        signature {:scenario-id "scenario-1" :revision 1}]
    (with-redefs [portfolio-optimizer-adapters/*request-run!*
                  (fn [payload]
                    (swap! calls conj payload)
                    "run-1")]
      (let [handler (portfolio-optimizer-adapters/make-run-portfolio-optimizer runtime)]
        (is (= "run-1"
               (handler :ctx
                        store
                        request
                        signature
                        {:computed-at-ms 123})))
        (is (= "run-1"
               (handler :ctx
                        store
                        request
                        signature
                        {:computed-at-ms 124})))
        (is (= 2 (count @calls)))
        (is (= {:request request
                :request-signature signature
                :computed-at-ms 123
                :store store}
               (dissoc (first @calls) :controller)))
        (is (some? (:controller (first @calls))))
        (is (identical? (:controller (first @calls))
                        (:controller (second @calls))))))))

(deftest optimizer-history-api-browser-override-returns-nil-when-global-is-absent-test
  (with-optimizer-history-api-override
    absent-override
    (fn []
      (is (nil? (optimizer-history-api-browser-override))))))

(deftest normalize-optimizer-history-api-browser-override-accepts-camel-case-aliases-test
  (is (= {:enabled? true
          :base-url "https://history.test"
          :proxy-policy :approved-proxy-allowed
          :include-aligned-returns? false
          :fallback-to-legacy? false}
         (normalize-optimizer-history-api-browser-override
          {:enabled true
           :baseUrl "https://history.test"
           :proxyPolicy "approved_proxy_allowed"
           :includeAlignedReturns false
           :fallbackToLegacy false}))))

(deftest normalize-optimizer-history-api-browser-override-prefers-kebab-aliases-and-defaults-invalid-booleans-test
  (is (= {:enabled? false
          :base-url "https://kebab.test"
          :proxy-policy :native-only
          :include-aligned-returns? true
          :fallback-to-legacy? true}
         (normalize-optimizer-history-api-browser-override
          {:enabled? "not-a-bool"
           :enabled true
           :base-url "https://kebab.test"
           :baseUrl "https://camel.test"
           :proxy-policy :native_only
           :proxyPolicy "approved_proxy_allowed"
           :include-aligned-returns? "yes"
           :includeAlignedReturns false
           :fallback-to-legacy? nil
           :fallbackToLegacy false}))))

(deftest optimizer-history-api-browser-override-normalizes-js-global-object-test
  (with-optimizer-history-api-override
    (js-obj "enabled" true
            "baseUrl" "https://history.test"
            "proxyPolicy" "native only"
            "includeAlignedReturns" true
            "fallbackToLegacy" false)
    (fn []
      (is (= {:enabled? true
              :base-url "https://history.test"
              :proxy-policy :native-only
              :include-aligned-returns? true
              :fallback-to-legacy? false}
             (optimizer-history-api-browser-override))))))
