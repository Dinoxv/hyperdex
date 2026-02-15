(ns hyperopen.domain.trading.indicators.catalog.price)

(def price-indicator-definitions
  [{:id :average-price
    :name "Average Price"
    :short-name "OHLC4"
    :description "(Open + High + Low + Close) / 4"
    :supports-period? false
    :default-config {}
    :migrated-from :indicators}
   {:id :median-price
    :name "Median Price"
    :short-name "Median"
    :description "(High + Low) / 2"
    :supports-period? false
    :default-config {}
    :migrated-from :wave2}
   {:id :typical-price
    :name "Typical Price"
    :short-name "Typical"
    :description "(High + Low + Close) / 3"
    :supports-period? false
    :default-config {}
    :migrated-from :wave2}])

