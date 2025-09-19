(ns hyperopen.wallet.core)

;; ---------- Provider helpers -------------------------------------------------

(defn ^js provider [] (.-ethereum js/window))
(defn has-provider? [] (some? (provider)))

(defn short-addr [a]
  (when a (str (subs a 0 6) "…" (subs a (- (count a) 4)))))

;; ---------- Core EIP-1102 actions -------------------------------------------

(defn set-disconnected! [store]
  (swap! store update-in [:wallet] 
         (fn [wallet-state]
           {:connected? false
            :address    nil
            :chain-id   (:chain-id wallet-state) ; keep last chain id
            :connecting? false
            :error      nil})))

(defn set-connected! [store addr]
  (swap! store update-in [:wallet] merge {:connected? true
                                          :address    addr
                                          :connecting? false
                                          :error      nil}))

(defn set-chain! [store chain-id]
  (swap! store assoc-in [:wallet :chain-id] chain-id))

(defn set-error! [store e]
  (swap! store update-in [:wallet] merge {:error (.-message e)
                                          :connecting? false}))

(defn ->js [m] (clj->js m))

(defn check-connection! [store]
  (if-not (has-provider?)
    (set-disconnected! store)
    (-> (.request (provider) (->js {:method "eth_accounts"}))
        (.then (fn [accounts]
                 (if (seq accounts)
                   (set-connected! store (first accounts))
                   (set-disconnected! store))))
        (.catch #(set-error! store %)))))

;; Only call this from a user gesture (button click)
(defn request-connection! [store]
  (if-not (has-provider?)
    (set-disconnected! store)
    (do 
      ;; Set connecting state
      (swap! store update-in [:wallet] merge {:connecting? true :error nil})
      ;; Request accounts
      (-> (.request (provider) (->js {:method "eth_requestAccounts"}))
          (.then (fn [accounts]
                   ;; Small delay to prevent nested render warnings
                   (js/setTimeout 
                     #(if (seq accounts)
                        (set-connected! store (first accounts))
                        (set-disconnected! store))
                     10)))
          (.catch #(js/setTimeout 
                     (fn [] (set-error! store %))
                     10))))))

;; ---------- Event listeners (accounts/chain) --------------------------------

(defonce listeners-installed? (atom false))

(defn attach-listeners! [store]
  (when (and (has-provider?) (not @listeners-installed?))
    (reset! listeners-installed? true)
    (.on (provider) "accountsChanged"
         (fn [accounts]
           (if (seq accounts)
             (set-connected! store (first accounts))
             (set-disconnected! store))))
    (.on (provider) "chainChanged"
         (fn [chain-id]
           ;; chainId comes as hex string per EIP-1193, e.g. "0x1"
           (set-chain! store chain-id)))))

(defn init-wallet! [store]
  (attach-listeners! store)
  (check-connection! store))

;; ---------- Replicant view components ----------------------------------------

(defn wallet-status [state]
  (let [{:keys [connected? address chain-id error connecting?]}
        (get-in state [:wallet])]
    [:div.flex.items-center.gap-2
     (cond
       error            [:span.text-red-600 (str "Wallet error: " error)]
       connecting?      [:span.text-white.opacity-80 "Connecting…"]
       connected?       [:<>
                         [:span.inline-block.px-2.py-1.rounded.bg-teal-700.text-teal-100.text-sm
                          (str "Connected " (short-addr address))]
                         (when chain-id
                           [:span.text-sm.text-white.opacity-60.ml-1
                            (str " chain " chain-id)])]
       :else            [:span.text-white.opacity-80 "Not connected"])]))

(defn connect-button [state]
  (let [{:keys [connected? connecting?]} (get-in state [:wallet])]
    [:button.bg-teal-600.hover:bg-teal-700.text-teal-100.px-4.py-2.rounded-lg.font-medium.transition-colors
     {:disabled (or connected? connecting?)
      :on {:click [[:actions/connect-wallet]]}}
     (cond
       connecting? "Connecting…"
       connected?  "Connected"
       :else       "Connect Wallet")]))
