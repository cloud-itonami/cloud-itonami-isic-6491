(ns leasing.registry-test
  (:require [clojure.test :refer [deftest is]]
            [leasing.registry :as r]))

;; ----------------------------- collateral-coverage-ratio-insufficient? -----------------------------

(deftest not-insufficient-when-at-or-above-minimum-ratio
  (is (not (r/collateral-coverage-ratio-insufficient? {:collateral-value 100000 :financed-amount 100000})))
  (is (not (r/collateral-coverage-ratio-insufficient? {:collateral-value 120000 :financed-amount 100000}))))

(deftest insufficient-when-below-minimum-ratio
  (is (r/collateral-coverage-ratio-insufficient? {:collateral-value 99999 :financed-amount 100000}))
  (is (r/collateral-coverage-ratio-insufficient? {:collateral-value 60000 :financed-amount 100000})))

(deftest insufficient-is-false-on-missing-or-zero-fields
  (is (not (r/collateral-coverage-ratio-insufficient? {})))
  (is (not (r/collateral-coverage-ratio-insufficient? {:collateral-value 60000})))
  (is (not (r/collateral-coverage-ratio-insufficient? {:collateral-value 0 :financed-amount 0}))
      "zero financed-amount -> no division by zero, never flagged"))

;; ----------------------------- register-lease-funding-disbursement -----------------------------

(deftest disbursement-is-a-draft-not-a-real-disbursement
  (let [result (r/register-lease-funding-disbursement "lease-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest disbursement-assigns-disbursement-number
  (let [result (r/register-lease-funding-disbursement "lease-1" "JPN" 7)]
    (is (= (get result "disbursement_number") "JPN-DSB-000007"))
    (is (= (get-in result ["record" "lease_id"]) "lease-1"))
    (is (= (get-in result ["record" "kind"]) "lease-funding-disbursement-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest disbursement-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-lease-funding-disbursement "" "JPN" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-lease-funding-disbursement "lease-1" "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-lease-funding-disbursement "lease-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-lease-funding-disbursement "lease-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-lease-funding-disbursement "lease-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-DSB-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-DSB-000001" (get-in hist2 [1 "record_id"])))))
