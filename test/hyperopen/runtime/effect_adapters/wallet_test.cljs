(ns hyperopen.runtime.effect-adapters.wallet-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.runtime.effect-adapters.wallet :as wallet-adapters]))

(deftest facade-wallet-adapters-delegate-to-wallet-module-test
  (is (identical? wallet-adapters/connect-wallet effect-adapters/connect-wallet))
  (is (identical? wallet-adapters/set-agent-storage-mode effect-adapters/set-agent-storage-mode))
  (is (identical? wallet-adapters/copy-wallet-address effect-adapters/copy-wallet-address))
  (is (identical? wallet-adapters/make-copy-wallet-address effect-adapters/make-copy-wallet-address))
  (is (identical? wallet-adapters/copy-spectate-link effect-adapters/copy-spectate-link))
  (is (identical? wallet-adapters/make-copy-spectate-link effect-adapters/make-copy-spectate-link)))
