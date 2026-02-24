(ns hyperopen.account.history.position-tpsl
  (:require [hyperopen.account.history.position-tpsl-application :as position-tpsl-application]
            [hyperopen.account.history.position-tpsl-policy :as position-tpsl-policy]
            [hyperopen.account.history.position-tpsl-state :as position-tpsl-state]
            [hyperopen.account.history.position-tpsl-transitions :as position-tpsl-transitions]))

(def default-modal-state position-tpsl-state/default-modal-state)
(def open? position-tpsl-state/open?)
(def tp-gain-mode position-tpsl-state/tp-gain-mode)
(def sl-loss-mode position-tpsl-state/sl-loss-mode)

(def preview-submit-label position-tpsl-policy/preview-submit-label)
(def validate-modal position-tpsl-policy/validate-modal)
(def active-size position-tpsl-policy/active-size)
(def configured-size-percent position-tpsl-policy/configured-size-percent)
(def estimated-gain-usd position-tpsl-policy/estimated-gain-usd)
(def estimated-loss-usd position-tpsl-policy/estimated-loss-usd)
(def estimated-gain-percent position-tpsl-policy/estimated-gain-percent)
(def estimated-loss-percent position-tpsl-policy/estimated-loss-percent)
(def valid-size? position-tpsl-policy/valid-size?)

(def prepare-submit position-tpsl-application/prepare-submit)
(def from-position-row position-tpsl-application/from-position-row)

(def set-modal-field position-tpsl-transitions/set-modal-field)
(def set-configure-amount position-tpsl-transitions/set-configure-amount)
(def set-limit-price position-tpsl-transitions/set-limit-price)
