(ns hyperopen.app.effects-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.app.effects :as app-effects]
            [hyperopen.runtime.effect-adapters :as effect-adapters]))

(deftest runtime-effect-deps-builds-runtime-bound-handlers-via-factories-test
  (let [runtime {:runtime-id :test}
        queue-handler (fn [& _] nil)
        refresh-handler (fn [& _] nil)
        disconnect-handler (fn [& _] nil)
        copy-handler (fn [& _] nil)
        submit-handler (fn [& _] nil)
        cancel-handler (fn [& _] nil)]
    (with-redefs [effect-adapters/make-queue-asset-icon-status
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    queue-handler)
                  effect-adapters/make-refresh-websocket-health
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    refresh-handler)
                  effect-adapters/make-disconnect-wallet
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    disconnect-handler)
                  effect-adapters/make-copy-wallet-address
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    copy-handler)
                  effect-adapters/make-api-submit-order
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    submit-handler)
                  effect-adapters/make-api-cancel-order
                  (fn [runtime*]
                    (is (identical? runtime runtime*))
                    cancel-handler)]
      (let [deps (app-effects/runtime-effect-deps runtime)]
        (is (identical? queue-handler
                        (get-in deps [:asset-selector :queue-asset-icon-status])))
        (is (identical? refresh-handler
                        (get-in deps [:websocket :refresh-websocket-health])))
        (is (identical? disconnect-handler
                        (get-in deps [:wallet :disconnect-wallet])))
        (is (identical? copy-handler
                        (get-in deps [:wallet :copy-wallet-address])))
        (is (identical? submit-handler
                        (get-in deps [:orders :api-submit-order])))
        (is (identical? cancel-handler
                        (get-in deps [:orders :api-cancel-order])))))))
