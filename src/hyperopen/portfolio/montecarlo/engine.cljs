(ns hyperopen.portfolio.montecarlo.engine
  "Pure, deterministic Monte Carlo engine for the portfolio forecast surface.

  Two methods share one result shape, selected by `:method`:

    :shuffle   — the faithful QuantStats method (`quantstats/_montecarlo.py`).
                 Each simulation is a random *reordering* (permutation, no
                 replacement) of the realized returns, so the simulation length
                 equals the history length and there is no forecast horizon.
                 Reordering cannot change the product of (1+r), so every path
                 ends at the *same* realized terminal value; only the path —
                 hence the drawdown — varies. Sim 0 is the original order,
                 matching QuantStats's unshuffled first column.

    :bootstrap — draws the realized returns at random *with replacement* over a
                 forecast horizon, thousands of times, to map the range of
                 forward outcomes the same return distribution could produce.
                 The horizon is clamped to the sample size so a forecast can
                 never compound more days than were actually observed.

  Both preserve the empirical return distribution while breaking time-ordering,
  isolating luck from skill across drawdowns, Sharpe, and terminal value.

  Determinism: randomness comes from a seeded `mulberry32` generator, so a given
  set of inputs always produces the same result. The namespace performs no I/O
  and touches no DOM, so it is worker-safe and unit-testable. The hot simulation
  loop uses `js/Float64Array` and array access for speed; only the small,
  capped set of drawn paths and the per-grid percentile bands are materialized
  as ClojureScript vectors for the view layer.")

;; ---------------------------------------------------------------------------
;; Seeded RNG (mulberry32) — deterministic uniform floats in [0, 1).
;; ---------------------------------------------------------------------------

(defn mulberry32
  "Return a zero-argument function producing a deterministic stream of uniform
  floats in [0, 1), seeded by integer `seed`."
  [seed]
  (let [state (atom (bit-or 0 seed))]
    (fn []
      (let [a (bit-or 0 (+ @state 0x6D2B79F5))]
        (reset! state a)
        (let [t (js/Math.imul (bit-xor a (unsigned-bit-shift-right a 15))
                              (bit-or 1 a))
              t (bit-xor (+ t (js/Math.imul (bit-xor t (unsigned-bit-shift-right t 7))
                                            (bit-or 61 t)))
                         t)]
          (/ (unsigned-bit-shift-right (bit-xor t (unsigned-bit-shift-right t 14)) 0)
             4294967296))))))

;; ---------------------------------------------------------------------------
;; Statistics helpers
;; ---------------------------------------------------------------------------

(defn- f64->vec
  "Copy a (typed) array into a ClojureScript vector of numbers. Avoids relying on
  `vec`/`array-seq` semantics over `js/Float64Array`, which vary by build."
  [arr]
  (let [n (alength arr)]
    (loop [i 0 acc (transient [])]
      (if (< i n)
        (recur (inc i) (conj! acc (aget arr i)))
        (persistent! acc)))))

(defn percentile
  "Linear-interpolated percentile `p` (0..100) over an already-sorted numeric
  collection `sorted`. Returns 0 for an empty collection."
  [sorted p]
  (let [n (count sorted)]
    (if (zero? n)
      0
      (let [idx (* (/ p 100) (dec n))
            lo (js/Math.floor idx)
            hi (js/Math.ceil idx)]
        (if (== lo hi)
          (nth sorted lo)
          (let [a (nth sorted lo)
                b (nth sorted hi)]
            (+ a (* (- b a) (- idx lo)))))))))

(defn dist-stats
  "Summarize a numeric collection `xs` into the distribution map the view needs:
  min/max/mean/median/std and the p5..p95 percentiles, plus `:raw` (the original
  values, for histograms) and `:sorted`."
  [xs]
  (let [v (vec xs)
        n (count v)
        sorted (vec (sort v))
        sum (reduce + 0 v)
        mean (if (zero? n) 0 (/ sum n))
        variance (if (zero? n)
                   0
                   (/ (reduce (fn [acc x]
                                (let [d (- x mean)]
                                  (+ acc (* d d))))
                              0 v)
                      n))]
    {:min (if (zero? n) 0 (first sorted))
     :max (if (zero? n) 0 (peek sorted))
     :mean mean
     :median (percentile sorted 50)
     :std (js/Math.sqrt variance)
     :p5 (percentile sorted 5)
     :p10 (percentile sorted 10)
     :p25 (percentile sorted 25)
     :p50 (percentile sorted 50)
     :p75 (percentile sorted 75)
     :p90 (percentile sorted 90)
     :p95 (percentile sorted 95)
     :raw v
     :sorted sorted}))

(defn histogram
  "Bin numeric collection `xs` into `bins` equal-width buckets. Returns
  `{:counts [...] :min :max :span :bin-width}`."
  [xs bins]
  (let [v (vec xs)]
    (if (empty? v)
      {:counts (vec (repeat bins 0)) :min 0 :max 0 :span 1 :bin-width (/ 1 bins)}
      (let [mn (reduce min v)
            mx (reduce max v)
            span (let [s (- mx mn)] (if (zero? s) 1 s))
            counts (js/Array. bins)]
        (dotimes [i bins] (aset counts i 0))
        (doseq [x v]
          (let [b (js/Math.floor (* (/ (- x mn) span) bins))
                b (cond (>= b bins) (dec bins)
                        (< b 0) 0
                        :else b)]
            (aset counts b (inc (aget counts b)))))
        {:counts (vec counts)
         :min mn
         :max mx
         :span span
         :bin-width (/ span bins)}))))

;; ---------------------------------------------------------------------------
;; Simulation
;; ---------------------------------------------------------------------------

(defn- grid-indices
  "Sampled time grid (day indices 0..H) used to bound the memory of the
  percentile-band columns. Mirrors the prototype's GRID = min(H, 160)."
  [^number H]
  (let [grid (min H 160)
        out (js/Array.)]
    (dotimes [g (inc grid)]
      (.push out (js/Math.round (* (/ g grid) H))))
    out))

(def ^:private default-periods-per-year
  "Annualization basis. Hyperliquid is a 24/7 market and the rest of the
  portfolio metrics pipeline annualizes on 365 periods/year, so the Monte Carlo
  Sharpe/CAGR use the same basis (the React prototype used 252 trading days)."
  365)

(defn- identity-indices
  "An `Int32Array` of the identity permutation `0..n-1` (the original order)."
  [n]
  (let [arr (js/Int32Array. n)]
    (dotimes [i n] (aset arr i i))
    arr))

(defn- shuffled-indices
  "A deterministic Fisher–Yates permutation of `0..n-1`, drawing from `rng` (a
  `mulberry32` instance). Used by the QuantStats shuffle method to reorder the
  realized returns without replacement."
  [rng n]
  (let [arr (identity-indices n)]
    (loop [i (dec n)]
      (when (pos? i)
        (let [j (bit-or 0 (* (rng) (inc i)))   ; uniform integer in [0, i]
              tmp (aget arr i)]
          (aset arr i (aget arr j))
          (aset arr j tmp)
          (recur (dec i)))))
    arr))

(defn run
  "Run the bootstrap Monte Carlo simulation.

  Options:
    :returns          seq of realized daily simple returns (e.g. 0.012 = +1.2%)
    :method           :bootstrap (default, with replacement over a horizon) or
                      :shuffle (QuantStats permutation; length = sample size)
    :sims             number of simulated paths (default 1000)
    :horizon          forecast length in days (default 90); ignored for :shuffle
                      and clamped to the sample size for :bootstrap
    :bust             drawdown threshold counted as a bust, negative (default -0.3)
    :goal             total-return threshold counted as a goal (default 0.5)
    :seed             RNG seed (default 42)
    :start-equity     starting equity for ending-value scaling (default 1)
    :periods-per-year annualization basis for Sharpe/CAGR (default 365)

  Returns a map with `:meta`, `:draw-paths` (capped sample of equity paths as
  Float64Arrays), `:band` (p5/p25/p50/p75/p95 at each `:times` grid point),
  `:terminal`/`:maxdd`/`:sharpe`/`:cagr`/`:vol` distribution maps, and
  `:bust-prob`/`:goal-prob`."
  [{:keys [returns sims horizon bust goal seed start-equity periods-per-year method]
    :or {sims 1000 horizon 90 bust -0.3 goal 0.5 seed 42 start-equity 1
         periods-per-year default-periods-per-year method :bootstrap}}]
  (let [ret (js/Float64Array. (into-array returns))
        m (.-length ret)
        shuffle? (= method :shuffle)
        ;; :shuffle reorders the realized history, so its length is the history
        ;; length (no forecast horizon). :bootstrap clamps to the sample size so
        ;; it can never compound more days than were observed — the fix for a
        ;; short-history vault reporting a P5 "worst case" above its own reality.
        H (cond
            (zero? m) horizon
            shuffle? m
            :else (min horizon m))
        rng (mulberry32 seed)
        ppy periods-per-year
        ann (js/Math.sqrt ppy)
        grid-idx (grid-indices H)
        grid-n (.-length grid-idx)
        cols (vec (repeatedly grid-n #(js/Float64Array. sims)))
        draw-cap (min sims 220)
        terminal (js/Float64Array. sims)
        maxdd (js/Float64Array. sims)
        sharpe (js/Float64Array. sims)
        cagr (js/Float64Array. sims)
        vol (js/Float64Array. sims)
        draw-paths (transient [])
        busts (volatile! 0)
        goals (volatile! 0)]
    (if (zero? m)
      ;; No realized returns to resample from — return an empty/degenerate run.
      {:meta {:sims sims :horizon H :bust bust :goal goal :seed seed :method method
              :start-equity start-equity :periods-per-year ppy :sample-size 0}
       :draw-paths [] :times (vec grid-idx)
       :band {:p5 [] :p25 [] :p50 [] :p75 [] :p95 []}
       :terminal (dist-stats []) :maxdd (dist-stats [])
       :sharpe (dist-stats []) :cagr (dist-stats []) :vol (dist-stats [])
       :bust-prob 0 :goal-prob 0}
      (do
        (dotimes [s sims]
          (let [keep-path? (< s draw-cap)
                path (when keep-path? (js/Float64Array. (inc H)))
                ;; Shuffle mode: precompute this sim's permutation of the
                ;; realized returns. Sim 0 keeps the original order (QuantStats's
                ;; unshuffled first column); the rest are Fisher–Yates shuffles.
                perm (when shuffle?
                       (if (zero? s) (identity-indices m) (shuffled-indices rng m)))]
            (when keep-path? (aset path 0 start-equity))
            ;; Seed grid column 0 (always day 0) with the starting equity.
            (let [gp0 (if (== (aget grid-idx 0) 0)
                        (do (aset (nth cols 0) s start-equity) 1)
                        0)
                  ;; March the path forward day by day, accumulating moments,
                  ;; tracking peak/worst-drawdown, and filling grid columns.
                  result
                  (loop [t 1 equity 1.0 peak 1.0 worst-dd 0.0
                         sum-r 0.0 sum-r2 0.0 gp gp0]
                    (if (> t H)
                      [equity worst-dd sum-r sum-r2]
                      (let [r (if shuffle?
                                (aget ret (aget perm (dec t)))
                                (aget ret (bit-or 0 (* (rng) m))))
                            equity* (* equity (+ 1 r))
                            peak* (if (> equity* peak) equity* peak)
                            dd (- (/ equity* peak*) 1)
                            worst* (if (< dd worst-dd) dd worst-dd)
                            scaled (* equity* start-equity)]
                        (when keep-path? (aset path t scaled))
                        (let [gp* (loop [gp gp]
                                    (if (and (< gp grid-n)
                                             (== (aget grid-idx gp) t))
                                      (do (aset (nth cols gp) s scaled)
                                          (recur (inc gp)))
                                      gp))]
                          (recur (inc t) equity* peak* worst*
                                 (+ sum-r r) (+ sum-r2 (* r r)) gp*)))))
                  [equity worst-dd sum-r sum-r2] result
                  mean (/ sum-r H)
                  sd (js/Math.sqrt (max 1e-12 (- (/ sum-r2 H) (* mean mean))))]
              (aset terminal s (- equity 1))
              (aset maxdd s worst-dd)
              (aset sharpe s (* (/ mean sd) ann))
              (aset cagr s (- (js/Math.pow equity (/ ppy H)) 1))
              (aset vol s (* sd ann))
              (when (<= worst-dd bust) (vswap! busts inc))
              (when (>= (- equity 1) goal) (vswap! goals inc))
              (when keep-path? (conj! draw-paths path)))))
        ;; Percentile band envelope at each grid point, across all sims.
        (let [band (reduce
                    (fn [acc g]
                      (let [sorted (f64->vec (.sort (.slice (nth cols g))
                                                    (fn [a b] (- a b))))]
                        (-> acc
                            (update :p5 conj (percentile sorted 5))
                            (update :p25 conj (percentile sorted 25))
                            (update :p50 conj (percentile sorted 50))
                            (update :p75 conj (percentile sorted 75))
                            (update :p95 conj (percentile sorted 95)))))
                    {:p5 [] :p25 [] :p50 [] :p75 [] :p95 []}
                    (range grid-n))]
          {:meta {:sims sims :horizon H :bust bust :goal goal :seed seed :method method
                  :start-equity start-equity :periods-per-year ppy :sample-size m}
           :draw-paths (persistent! draw-paths)
           :band band
           :times (vec grid-idx)
           :terminal (dist-stats (f64->vec terminal))
           :maxdd (dist-stats (f64->vec maxdd))
           :sharpe (dist-stats (f64->vec sharpe))
           :cagr (dist-stats (f64->vec cagr))
           :vol (dist-stats (f64->vec vol))
           :bust-prob (/ @busts sims)
           :goal-prob (/ @goals sims)})))))
