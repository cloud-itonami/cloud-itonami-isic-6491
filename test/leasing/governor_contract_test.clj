(ns leasing.governor-contract-test
  "The governor contract as executable tests -- the leasing analog of
  `cloud-itonami-isic-6512`'s `casualty.governor-contract-test`. The
  single invariant under test:

    Leasing-LLM never disburses lease funding the Leasing Governor
    would reject, `:disbursement/fund` NEVER auto-commits at any
    phase, `:lease/intake` (no direct capital risk) MAY auto-commit
    when clean, and every decision (commit OR hold) leaves exactly one
    ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [leasing.store :as store]
            [leasing.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :lessor-officer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(defn- screen!
  "Walks `subject` through creditworthiness screening -> approve,
  leaving a screening on file. Only safe to call for a lease whose
  credit status is already clean -- an adverse flag HARD-holds the
  screen itself (see
  `adverse-credit-flag-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :creditworthiness/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :lease/intake :subject "lease-1"
                   :patch {:id "lease-1" :lessee-name "Sakura Tanaka"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Tanaka" (:lessee-name (store/lease db "lease-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "lease-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "lease-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "lease-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "lease-1")) "no assessment written"))))

(deftest disbursement-fund-without-assessment-is-held
  (testing "disbursement/fund before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :disbursement/fund :subject "lease-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest collateral-coverage-insufficient-is-held
  (testing "a lease whose collateral value falls short of the required coverage ratio -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "lease-3")
          res (exec-op actor "t5" {:op :disbursement/fund :subject "lease-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:collateral-coverage-insufficient} (-> (store/ledger db) last :basis)))
      (is (empty? (store/disbursement-history db))))))

(deftest adverse-credit-flag-is-held-and-unoverridable
  (testing "an unresolved adverse credit flag on a lease -> HOLD, and never reaches request-approval -- exercised via :creditworthiness/screen DIRECTLY, not via the actuation op against an unscreened lease (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's and association's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :creditworthiness/screen :subject "lease-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:adverse-credit-flag-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/credit-screen-of db "lease-4")) "no clearance written"))))

(deftest disbursement-fund-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, fully-collateralized lease still ALWAYS interrupts for human approval -- actuation/fund-lease-disbursement is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "lease-1")
          _ (screen! actor "t7pre2" "lease-1")
          r1 (exec-op actor "t7" {:op :disbursement/fund :subject "lease-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, disbursement record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:disbursed? (store/lease db "lease-1"))))
          (is (= 1 (count (store/disbursement-history db))) "one draft disbursement record"))))))

(deftest disbursement-fund-double-disbursement-is-held
  (testing "funding the same lease's disbursement twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "lease-1")
          _ (screen! actor "t8pre2" "lease-1")
          _ (exec-op actor "t8a" {:op :disbursement/fund :subject "lease-1"} operator)
          _ (approve! actor "t8a")
          res (exec-op actor "t8" {:op :disbursement/fund :subject "lease-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-disbursed} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/disbursement-history db))) "still only the one earlier disbursement"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :lease/intake :subject "lease-1"
                          :patch {:id "lease-1" :lessee-name "Sakura Tanaka"}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "lease-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
