(ns leasing.kernels.gate-test
  "The safety kernel's executable spec, three ways:

  1. battery lock — the kernel's own in-subset battery must pass
     case-for-case (`battery-case-count` == `(battery-pass-count)`),
     so a silently dropped case can't survive review.
  2. parity matrix — the kernel's phase core is compared against an
     independent reference copy of the ORIGINAL set-based cond logic
     over the FULL input space (all phases incl. out-of-range, all op
     codes incl. unknown, all governor dispositions). The façade
     delegates, so this is the guard that delegation didn't change
     semantics.
  3. governor boundary — the confidence floor boundary, the
     fail-closed treatment of out-of-range confidence, and the
     collateral-coverage minimum-ratio boundary, exercised through the
     real `leasing.governor/check` façade."
  (:require [clojure.test :refer [deftest is testing]]
            [leasing.facts :as facts]
            [leasing.governor :as governor]
            [leasing.kernels.gate :as gate]
            [leasing.registry :as registry]
            [leasing.store :as store]))

(deftest battery-lock
  (is (= gate/battery-case-count (gate/battery-pass-count))
      "every battery case must pass; update battery-case-count only when adding cases"))

(deftest confidence-floor-pinned-to-facade-constant
  (is (= gate/confidence-floor-x100
         (Math/round (* 100.0 governor/confidence-floor)))
      "the façade's documented 0.6 and the kernel's deciding 60 must not drift"))

(deftest coverage-ratio-pinned-to-facade-constant
  (is (= gate/minimum-coverage-ratio-x100
         (Math/round (* 100.0 registry/minimum-coverage-ratio)))
      "the registry's documented 1.0 and the kernel's deciding 100 must not drift"))

;; ---------------------------------------------------------------
;; Independent oracle for the parity matrix: the pre-kernel phase
;; logic (sets + cond) restated over wire codes, PLUS the kernel's
;; fail-closed contract for out-of-range phases (no writes at all).
;; The original façade normalized an unknown phase to default-phase 3
;; BEFORE this logic and still does — so out-of-range rows here pin
;; the kernel's own contract, not a façade behavior change. Op 0 is
;; the fleet-wide read code (this actor's read-ops is empty; the
;; façade never emits it) and op 5 the unknown-write code.

(def ^:private ref-read-ops #{0})
(def ^:private ref-phases
  {0 {:writes #{}          :auto #{}}
   1 {:writes #{1}         :auto #{}}
   2 {:writes #{1 2 3}     :auto #{}}
   3 {:writes #{1 2 3 4}   :auto #{1}}})

(defn- ref-gate [phase op gov]
  (let [{:keys [writes auto]} (get ref-phases phase {:writes #{} :auto #{}})]
    (cond
      (= gov 2)                        {:d 2 :r 0}
      (contains? ref-read-ops op)      {:d gov :r 0}
      (not (contains? writes op))      {:d 2 :r 1}
      (and (= gov 0)
           (not (contains? auto op)))  {:d 1 :r 2}
      :else                            {:d gov :r 0})))

(deftest phase-parity-matrix
  (testing "kernel == reference over the full input space (162 combos)"
    (doseq [phase [-1 0 1 2 3 4 7 100 -99]
            op    [0 1 2 3 4 5]
            gov   [0 1 2]]
      (let [expected (ref-gate phase op gov)]
        (is (= (:d expected) (gate/phase-disposition phase op gov))
            (str "disposition mismatch at phase=" phase " op=" op " gov=" gov))
        (is (= (:r expected) (gate/phase-reason phase op gov))
            (str "reason mismatch at phase=" phase " op=" op " gov=" gov))))))

(deftest disbursement-fund-auto-enabled-nowhere
  (testing "op 4 (:disbursement/fund) and op 3 (:creditworthiness/screen) are
            auto-enabled at NO phase — kernel restates the phase table's
            permanent structural invariants"
    (doseq [phase [-1 0 1 2 3 4 7]]
      (is (= 0 (gate/op-auto-enabled phase 4)))
      (is (= 0 (gate/op-auto-enabled phase 3))))))

;; ---------------------------------------------------------------
;; Governor boundary through the real façade. op :lease/intake touches
;; neither the store nor the evidence/coverage/disbursement checks, so
;; the verdict is decided purely by confidence/actuation — nil store
;; is safe (the unconditional adverse-credit check only consults the
;; store for screen/fund ops).

(defn- verdict [proposal]
  (governor/check {:op :lease/intake :subject "lease-x"} {} proposal nil))

(deftest confidence-floor-boundary
  (testing "0.59 escalates, 0.60 clears (kernel decides at integer x100)"
    (is (true?  (:escalate? (verdict {:confidence 0.59}))))
    (is (false? (:ok? (verdict {:confidence 0.59}))))
    (is (true?  (:ok? (verdict {:confidence 0.6}))))
    (is (false? (:escalate? (verdict {:confidence 0.6}))))))

(deftest out-of-range-confidence-fails-closed
  (testing "an advisor reporting impossible confidence gets MORE scrutiny,
            not auto-commit (kernel is deliberately stricter than the old
            inline `(< conf floor)` here)"
    (is (true? (:escalate? (verdict {:confidence 1.5}))))
    (is (false? (:ok? (verdict {:confidence 1.5}))))
    (is (true? (:escalate? (verdict {:confidence -0.2}))))))

;; ---------------------------------------------------------------
;; Collateral-coverage minimum-ratio boundary through the real façade
;; — the kernel decides in exact integers (100*collateral <
;; 100*financed), the façade still produces the human-readable
;; violation map, and both must agree at the boundary. The lease is
;; fully assessed (evidence satisfied) and clean otherwise, so the
;; verdict isolates the coverage check.

(defn- fund-verdict [collateral-value financed-amount]
  (let [st (store/with-leases
             (store/seed-db)
             {"lease-x" {:id "lease-x" :lessee-name "n" :jurisdiction "JPN"
                         :collateral-value collateral-value
                         :financed-amount financed-amount
                         :adverse-credit-flag? false :disbursed? false
                         :status :intake}})]
    (store/commit-record! st {:effect :assessment/set :path ["lease-x"]
                              :payload {:jurisdiction "JPN"
                                        :checklist (facts/evidence-checklist "JPN")}})
    (governor/check {:op :disbursement/fund :subject "lease-x"} {}
                    {:confidence 0.9 :cites ["JPN-spec"]} st)))

(deftest coverage-ratio-boundary-through-facade
  (testing "coverage ratio exactly at the 1.0 minimum clears (strict <)"
    (let [v (fund-verdict 100000 100000)]
      (is (true? (:ok? v)))
      (is (false? (:hard? v)))
      (is (empty? (:violations v)))))
  (testing "one currency unit short of full coverage hard-holds, kernel and
            violation map agreeing"
    (let [v (fund-verdict 99999 100000)]
      (is (true? (:hard? v)))
      (is (false? (:ok? v)))
      (is (some #{:collateral-coverage-insufficient} (mapv :rule (:violations v))))))
  (testing "one currency unit above full coverage clears"
    (is (true? (:ok? (fund-verdict 100001 100000))))))

(deftest actuation-still-escalates-and-hard-still-wins
  (is (true? (:escalate? (verdict {:confidence 0.99 :stake :actuation/fund-lease-disbursement}))))
  (testing "a hard violation dominates actuation escalation"
    (let [v (governor/check {:op :jurisdiction/assess :subject "lease-x"} {}
                            {:confidence 0.99 :stake :actuation/fund-lease-disbursement :cites []} nil)]
      (is (true? (:hard? v)))
      (is (false? (:escalate? v)))
      (is (some #{:no-spec-basis} (mapv :rule (:violations v)))))))
