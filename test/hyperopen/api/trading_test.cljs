(ns hyperopen.api.trading-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.trading :as trading]
            [hyperopen.utils.hl-signing :as signing]))

(deftest approve-agent-signs-and-posts-exchange-payload-test
  (async done
    (let [signed-payload (atom nil)
          fetch-call (atom nil)
          original-sign signing/sign-approve-agent-action!
          original-fetch (.-fetch js/globalThis)
          action {:type "approveAgent"
                  :agentAddress "0x9999999999999999999999999999999999999999"
                  :nonce 1700000004444
                  :hyperliquidChain "Mainnet"
                  :signatureChainId "0x66eee"}]
      (set! signing/sign-approve-agent-action!
            (fn [address action*]
              (reset! signed-payload [address action*])
              (js/Promise.resolve
               (clj->js {:r "0x1"
                         :s "0x2"
                         :v 27}))))
      (set! (.-fetch js/globalThis)
            (fn [url opts]
              (reset! fetch-call [url (js->clj opts :keywordize-keys true)])
              (js/Promise.resolve #js {:ok true})))
      (-> (trading/approve-agent! (atom {}) "0xowner" action)
          (.then (fn [_]
                   (let [[signed-address signed-action] @signed-payload
                         [url opts] @fetch-call
                         parsed-body (js->clj (js/JSON.parse (:body opts)) :keywordize-keys true)]
                     (is (= "0xowner" signed-address))
                     (is (= action signed-action))
                     (is (= trading/exchange-url url))
                     (is (= action (:action parsed-body)))
                     (is (= 1700000004444 (:nonce parsed-body)))
                     (is (= {:r "0x1" :s "0x2" :v 27}
                            (:signature parsed-body)))
                     (done))))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))
          (.finally
           (fn []
             (set! signing/sign-approve-agent-action! original-sign)
             (set! (.-fetch js/globalThis) original-fetch)))))))
