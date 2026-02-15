(ns hyperopen.domain.trading.indicators.catalog.structure)

(def structure-indicator-definitions
  [{:id :pivot-points-standard
    :name "Pivot Points Standard"
    :short-name "Pivots"
    :description "PP, R1-R3 and S1-S3 from previous window"
    :supports-period? true
    :default-period 20
    :min-period 2
    :max-period 400
    :default-config {:period 20}
    :migrated-from :wave3}
   {:id :rank-correlation-index
    :name "Rank Correlation Index"
    :short-name "RCI"
    :description "Spearman rank correlation oscillator"
    :supports-period? true
    :default-period 9
    :min-period 3
    :max-period 400
    :default-config {:period 9}
    :migrated-from :wave3}
   {:id :zig-zag
    :name "Zig Zag"
    :short-name "ZigZag"
    :description "Swing-line connecting pivots that exceed threshold"
    :supports-period? false
    :default-config {:threshold-percent 5}
    :migrated-from :wave3}
   {:id :williams-fractal
    :name "Williams Fractal"
    :short-name "Fractal"
    :description "Five-bar high/low fractal markers"
    :supports-period? false
    :default-config {}
    :migrated-from :wave3}])

