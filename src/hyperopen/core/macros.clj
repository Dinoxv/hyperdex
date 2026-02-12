(ns hyperopen.core.macros)

(defmacro def-public-action-aliases
  [actions-alias publics]
  (let [alias-name (name actions-alias)]
    `(do
       ~@(for [sym publics]
           `(def ~sym ~(symbol alias-name (name sym)))))))
