#!/usr/bin/env bb

(ns dev.formal-tooling-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests testing]]
            [tools.formal.core :as formal]))

(def vault-surface
  {:id "vault-transfer"
   :lean-module "Hyperopen.Formal.VaultTransfer"
   :status "modeled"
   :manifest "generated/vault-transfer.edn"
   :target-source "target/formal/vault-transfer-vectors.cljs"
   :committed-source "test/hyperopen/formal/vault_transfer_vectors.cljs"})

(def standard-surface
  {:id "order-request-standard"
   :lean-module "Hyperopen.Formal.OrderRequest.Standard"
   :status "modeled"
   :manifest "generated/order-request-standard.edn"
   :target-source "target/formal/order-request-standard-vectors.cljs"
   :committed-source "test/hyperopen/formal/order_request_standard_vectors.cljs"})

(def advanced-surface
  {:id "order-request-advanced"
   :lean-module "Hyperopen.Formal.OrderRequest.Advanced"
   :status "modeled"
   :manifest "generated/order-request-advanced.edn"
   :target-source "target/formal/order-request-advanced-vectors.cljs"
   :committed-source "test/hyperopen/formal/order_request_advanced_vectors.cljs"})

(def bootstrap-test-surface
  {:id "bootstrap-test"
   :lean-module "Hyperopen.Formal.Bootstrap"
   :status "bootstrap"
   :manifest "generated/bootstrap-test.edn"})

(def modeled-surfaces
  [vault-surface standard-surface advanced-surface])

(defn delete-recursive!
  [file]
  (when (.exists file)
    (doseq [child (reverse (file-seq file))]
      (.delete child))))

(defn with-temp-root
  [f]
  (let [tmp-path (java.nio.file.Files/createTempDirectory "formal-tooling" (make-array java.nio.file.attribute.FileAttribute 0))
        root (.toFile tmp-path)]
    (try
      (f (.getCanonicalPath root))
      (finally
        (delete-recursive! root)))))

(defn write-file!
  [root relative-path text]
  (let [file (io/file root relative-path)]
    (when-let [parent (.getParentFile file)]
      (.mkdirs parent))
    (spit file text)))

(deftest manifest-content-respects-surface-status-test
  (is (= "{:surface \"vault-transfer\" :module \"Hyperopen.Formal.VaultTransfer\" :status \"modeled\"}\n"
         (#'formal/manifest-content vault-surface)))
  (is (= "{:surface \"order-request-standard\" :module \"Hyperopen.Formal.OrderRequest.Standard\" :status \"modeled\"}\n"
         (#'formal/manifest-content standard-surface)))
  (is (= "{:surface \"order-request-advanced\" :module \"Hyperopen.Formal.OrderRequest.Advanced\" :status \"modeled\"}\n"
         (#'formal/manifest-content advanced-surface))))

(deftest sync-generated-source-copies-transient-export-into-committed-namespace-test
  (doseq [{:keys [target-source committed-source] :as surface} modeled-surfaces]
    (with-temp-root
      (fn [root]
        (write-file! root target-source "generated")
        (with-redefs [tools.formal.core/repo-root (constantly (io/file root))]
          (#'formal/sync-generated-source! surface)
          (is (= "generated"
                 (slurp (io/file root committed-source)))))))))

(deftest verify-generated-source-detects-stale-committed-namespace-test
  (doseq [{:keys [target-source committed-source] :as surface} modeled-surfaces]
    (with-temp-root
      (fn [root]
        (write-file! root target-source "generated")
        (write-file! root committed-source "stale")
        (with-redefs [tools.formal.core/repo-root (constantly (io/file root))]
          (is (thrown-with-msg?
               Exception
               #"Stale generated source"
               (#'formal/verify-generated-source! surface))))))))

(deftest bootstrap-surface-skips-generated-source-checks-test
  (is (nil? (#'formal/verify-generated-source! bootstrap-test-surface)))
  (is (nil? (#'formal/sync-generated-source! bootstrap-test-surface))))

(deftest run-sync-and-verify-support-modeled-generated-source-artifacts-test
  (doseq [{:keys [id target-source committed-source lean-module] :as surface} modeled-surfaces]
    (with-temp-root
      (fn [root]
        (write-file! root target-source "generated")
        (write-file! root committed-source "generated")
        (with-redefs [tools.formal.core/repo-root (constantly (io/file root))
                      tools.formal.core/ensure-lean-tools! (fn [] nil)
                      tools.formal.core/build-lean-workspace! (fn [] {:exit 0})
                      tools.formal.core/run-lean-entrypoint! (fn [_command _surface-id] {:exit 0})]
          (testing (str "sync writes manifest and committed source for " id)
            (let [output (with-out-str
                           (formal/run! ["sync" "--surface" id]))]
              (is (.contains output (str "Synced " id)))
              (is (= "generated"
                     (slurp (io/file root committed-source))))
              (is (= (str "{:surface \"" id "\" :module \"" lean-module "\" :status \"modeled\"}\n")
                     (slurp (io/file root (str "tools/formal/generated/" id ".edn")))))))
          (testing (str "verify accepts current generated source for " id)
            (let [output (with-out-str
                           (formal/run! ["verify" "--surface" id]))]
              (is (.contains output (str "Verified " id))))))))))

(defn -main
  [& _args]
  (let [{:keys [fail error]} (run-tests 'dev.formal-tooling-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
