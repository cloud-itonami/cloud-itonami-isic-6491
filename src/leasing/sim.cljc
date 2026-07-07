(ns leasing.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean lease through
  intake -> jurisdiction assessment -> creditworthiness screening ->
  funding-disbursement proposal (always escalates) -> human approval
  -> commit, then shows four HARD holds (a jurisdiction with no spec-
  basis, insufficient collateral coverage, an unresolved adverse
  credit flag screened directly via `:creditworthiness/screen` [never
  via the actuation op against an unscreened lease -- see this actor's
  own governor ns docstring / the lesson `parksafety`'s
  ADR-2607071922 Decision 5, `eldercare`'s, `museum`'s,
  `conservation`'s, `salon`'s, `entertainment`'s, `casework`'s,
  `hospital`'s, `facility`'s, `school`'s and `association`'s
  ADR-0001s already recorded], and a double disbursement of an
  already-funded lease) that never reach a human at all, and prints
  the audit ledger + the draft lease-funding-disbursement records."
  (:require [langgraph.graph :as g]
            [leasing.store :as store]
            [leasing.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :lessor-officer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== lease/intake lease-1 (JPN, clean; fully collateralized, no adverse credit flag) ==")
    (println (exec! actor "t1" {:op :lease/intake :subject "lease-1"
                                :patch {:id "lease-1" :lessee-name "Sakura Tanaka"}} operator))

    (println "== jurisdiction/assess lease-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :jurisdiction/assess :subject "lease-1"} operator))
    (println (approve! actor "t2"))

    (println "== creditworthiness/screen lease-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :creditworthiness/screen :subject "lease-1"} operator))
    (println (approve! actor "t3"))

    (println "== disbursement/fund lease-1 (always escalates -- actuation/fund-lease-disbursement) ==")
    (let [r (exec! actor "t4" {:op :disbursement/fund :subject "lease-1"} operator)]
      (println r)
      (println "-- human lessor officer approves --")
      (println (approve! actor "t4")))

    (println "== jurisdiction/assess lease-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t5" {:op :jurisdiction/assess :subject "lease-2" :no-spec? true} operator))

    (println "== jurisdiction/assess lease-3 (escalates -- human approves; sets up the collateral test) ==")
    (println (exec! actor "t6" {:op :jurisdiction/assess :subject "lease-3"} operator))
    (println (approve! actor "t6"))

    (println "== disbursement/fund lease-3 (60000/100000 collateral coverage -> HARD hold) ==")
    (println (exec! actor "t7" {:op :disbursement/fund :subject "lease-3"} operator))

    (println "== creditworthiness/screen lease-4 (adverse -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t8" {:op :creditworthiness/screen :subject "lease-4"} operator))

    (println "== disbursement/fund lease-1 AGAIN (double-disbursement -> HARD hold) ==")
    (println (exec! actor "t9" {:op :disbursement/fund :subject "lease-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft lease-funding-disbursement records ==")
    (doseq [r (store/disbursement-history db)] (println r))))
