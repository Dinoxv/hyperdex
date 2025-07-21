(ns hyperopen.asset-selector.settings)

(def valid-sort-keys #{:name :price :volume :change :openInterest :funding})
(def valid-sort-directions #{:asc :desc})

(defn- load-sort-setting
  "Read `ls-key` from localStorage, default to `default`,
   coerce to keyword, and only accept it if `valid?` returns true."
  [ls-key default valid?]
  (let [v (keyword (or (js/localStorage.getItem ls-key) default))]
    (if (valid? v) v (keyword default))))

(defn restore-asset-selector-sort-settings! [store]
  (let [sort-by        (load-sort-setting "asset-selector-sort-by"        "volume" valid-sort-keys)
        sort-direction (load-sort-setting "asset-selector-sort-direction" "desc"   valid-sort-directions)]
    (swap! store
      update-in
      [:asset-selector]
      merge
      {:sort-by        sort-by
       :sort-direction sort-direction}))) 