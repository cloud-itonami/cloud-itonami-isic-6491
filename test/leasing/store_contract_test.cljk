(ns leasing.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [leasing.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Tanaka" (:lessee-name (store/lease s "lease-1"))))
      (is (= "JPN" (:jurisdiction (store/lease s "lease-1"))))
      (is (= 100000 (:collateral-value (store/lease s "lease-1"))))
      (is (= 100000 (:financed-amount (store/lease s "lease-1"))))
      (is (false? (:adverse-credit-flag? (store/lease s "lease-1"))))
      (is (= 60000 (:collateral-value (store/lease s "lease-3"))))
      (is (true? (:adverse-credit-flag? (store/lease s "lease-4"))))
      (is (false? (:disbursed? (store/lease s "lease-1"))))
      (is (= ["lease-1" "lease-2" "lease-3" "lease-4"]
             (mapv :id (store/all-leases s))))
      (is (nil? (store/credit-screen-of s "lease-1")))
      (is (nil? (store/assessment-of s "lease-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/disbursement-history s)))
      (is (zero? (store/next-disbursement-sequence s "JPN")))
      (is (false? (store/lease-already-disbursed? s "lease-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :lease/upsert
                                 :value {:id "lease-1" :lessee-name "Sakura Tanaka"}})
        (is (= "Sakura Tanaka" (:lessee-name (store/lease s "lease-1"))))
        (is (= 100000 (:collateral-value (store/lease s "lease-1"))) "unrelated field preserved"))
      (testing "assessment / credit-screen payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["lease-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "lease-1")))
        (store/commit-record! s {:effect :credit-screen/set :path ["lease-1"]
                                 :payload {:lease-id "lease-1" :verdict :clean}})
        (is (= {:lease-id "lease-1" :verdict :clean} (store/credit-screen-of s "lease-1"))))
      (testing "disbursement drafts a disbursement record and advances the sequence"
        (store/commit-record! s {:effect :lease/mark-disbursed :path ["lease-1"]})
        (is (= "JPN-DSB-000000" (get (first (store/disbursement-history s)) "record_id")))
        (is (= "lease-funding-disbursement-draft" (get (first (store/disbursement-history s)) "kind")))
        (is (true? (:disbursed? (store/lease s "lease-1"))))
        (is (= 1 (count (store/disbursement-history s))))
        (is (= 1 (store/next-disbursement-sequence s "JPN")))
        (is (true? (store/lease-already-disbursed? s "lease-1")))
        (is (false? (store/lease-already-disbursed? s "lease-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/lease s "nope")))
    (is (= [] (store/all-leases s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/disbursement-history s)))
    (is (zero? (store/next-disbursement-sequence s "JPN")))
    (store/with-leases s {"x" {:id "x" :lessee-name "n" :collateral-value 100000
                               :financed-amount 100000 :adverse-credit-flag? false
                               :disbursed? false :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:lessee-name (store/lease s "x"))))))
