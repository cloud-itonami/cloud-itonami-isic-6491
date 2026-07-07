(ns leasing.store
  "SSoT for the leasing actor, behind a `Store` protocol so the
  backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/leasing/store_contract_test.clj), which is the whole point:
  the actor, the Leasing Governor and the audit ledger never know
  which SSoT they run on.

  Single-actuation shape (one history collection, one sequence
  counter, one dedicated double-actuation-guard boolean) -- matching
  `underwriting.store`'s/`testlab.store`'s/`clinic.store`'s/
  `veterinary.store`'s/`funeral.store`'s/`parksafety.store`'s/
  `salon.store`'s/`entertainment.store`'s/`facility.store`'s single
  real-world actuation event (disbursing lease funding), with a
  dedicated `:disbursed?` boolean (never a `:status` value) -- the
  same discipline every prior sibling governor's guards establish,
  informed by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320).

  The ledger stays append-only on every backend: 'which lease was
  screened for an adverse credit flag, which funding disbursement was
  made, on what jurisdictional basis, approved by whom' is always a
  query over an immutable log -- the audit trail a lessee trusting a
  lessor needs, and the evidence an operator needs if a disbursement
  is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [leasing.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (lease [s id])
  (all-leases [s])
  (credit-screen-of [s lease-id] "committed adverse-credit-flag screening verdict for a lease, or nil")
  (assessment-of [s lease-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (disbursement-history [s] "the append-only lease-funding-disbursement history (leasing.registry drafts)")
  (next-disbursement-sequence [s jurisdiction] "next disbursement-number sequence for a jurisdiction")
  (lease-already-disbursed? [s lease-id] "has this lease's funding already been disbursed?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-leases [s leases] "replace/seed the lease directory (map id->lease)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained lease set covering the actuation lifecycle
  (disbursing lease funding) so the actor + tests run offline."
  []
  {:leases
   {"lease-1" {:id "lease-1" :lessee-name "Sakura Tanaka"
               :collateral-value 100000 :financed-amount 100000 :adverse-credit-flag? false
               :disbursed? false :jurisdiction "JPN" :status :intake}
    "lease-2" {:id "lease-2" :lessee-name "Atlantis Doe"
               :collateral-value 100000 :financed-amount 100000 :adverse-credit-flag? false
               :disbursed? false :jurisdiction "ATL" :status :intake}
    "lease-3" {:id "lease-3" :lessee-name "鈴木一郎"
               :collateral-value 60000 :financed-amount 100000 :adverse-credit-flag? false
               :disbursed? false :jurisdiction "JPN" :status :intake}
    "lease-4" {:id "lease-4" :lessee-name "田中花子"
               :collateral-value 100000 :financed-amount 100000 :adverse-credit-flag? true
               :disbursed? false :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- finalize-disbursement!
  "Backend-agnostic `:lease/mark-disbursed` -- looks up the lease via
  the protocol and drafts the funding-disbursement record, and returns
  {:result .. :lease-patch ..} for the caller to persist."
  [s lease-id]
  (let [l (lease s lease-id)
        seq-n (next-disbursement-sequence s (:jurisdiction l))
        result (registry/register-lease-funding-disbursement lease-id (:jurisdiction l) seq-n)]
    {:result result
     :lease-patch {:disbursed? true
                  :disbursement-number (get result "disbursement_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (lease [_ id] (get-in @a [:leases id]))
  (all-leases [_] (sort-by :id (vals (:leases @a))))
  (credit-screen-of [_ id] (get-in @a [:credit-screens id]))
  (assessment-of [_ lease-id] (get-in @a [:assessments lease-id]))
  (ledger [_] (:ledger @a))
  (disbursement-history [_] (:disbursements @a))
  (next-disbursement-sequence [_ jurisdiction] (get-in @a [:disbursement-sequences jurisdiction] 0))
  (lease-already-disbursed? [_ lease-id] (boolean (get-in @a [:leases lease-id :disbursed?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :lease/upsert
      (swap! a update-in [:leases (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :credit-screen/set
      (swap! a assoc-in [:credit-screens (first path)] payload)

      :lease/mark-disbursed
      (let [lease-id (first path)
            {:keys [result lease-patch]} (finalize-disbursement! s lease-id)
            jurisdiction (:jurisdiction (lease s lease-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:disbursement-sequences jurisdiction] (fnil inc 0))
                       (update-in [:leases lease-id] merge lease-patch)
                       (update :disbursements registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-leases [s leases] (when (seq leases) (swap! a assoc :leases leases)) s))

(defn seed-db
  "A MemStore seeded with the demo lease set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :credit-screens {} :ledger [] :disbursement-sequences {}
                           :disbursements []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/credit-screen payloads, ledger
  facts, disbursement records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:lease/id                          {:db/unique :db.unique/identity}
   :assessment/lease-id               {:db/unique :db.unique/identity}
   :credit-screen/lease-id            {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :disbursement/seq                  {:db/unique :db.unique/identity}
   :disbursement-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- lease->tx [{:keys [id lessee-name collateral-value financed-amount adverse-credit-flag?
                          disbursed? jurisdiction status disbursement-number]}]
  (cond-> {:lease/id id}
    lessee-name                        (assoc :lease/lessee-name lessee-name)
    collateral-value                    (assoc :lease/collateral-value collateral-value)
    financed-amount                     (assoc :lease/financed-amount financed-amount)
    (some? adverse-credit-flag?)        (assoc :lease/adverse-credit-flag? adverse-credit-flag?)
    (some? disbursed?)                  (assoc :lease/disbursed? disbursed?)
    jurisdiction                        (assoc :lease/jurisdiction jurisdiction)
    status                              (assoc :lease/status status)
    disbursement-number                  (assoc :lease/disbursement-number disbursement-number)))

(def ^:private lease-pull
  [:lease/id :lease/lessee-name :lease/collateral-value :lease/financed-amount
   :lease/adverse-credit-flag? :lease/disbursed?
   :lease/jurisdiction :lease/status :lease/disbursement-number])

(defn- pull->lease [m]
  (when (:lease/id m)
    {:id (:lease/id m) :lessee-name (:lease/lessee-name m)
     :collateral-value (:lease/collateral-value m)
     :financed-amount (:lease/financed-amount m)
     :adverse-credit-flag? (boolean (:lease/adverse-credit-flag? m))
     :disbursed? (boolean (:lease/disbursed? m))
     :jurisdiction (:lease/jurisdiction m) :status (:lease/status m)
     :disbursement-number (:lease/disbursement-number m)}))

(defrecord DatomicStore [conn]
  Store
  (lease [_ id]
    (pull->lease (d/pull (d/db conn) lease-pull [:lease/id id])))
  (all-leases [_]
    (->> (d/q '[:find [?id ...] :where [?e :lease/id ?id]] (d/db conn))
         (map #(pull->lease (d/pull (d/db conn) lease-pull [:lease/id %])))
         (sort-by :id)))
  (credit-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?lid
                :where [?k :credit-screen/lease-id ?lid] [?k :credit-screen/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ lease-id]
    (dec* (d/q '[:find ?p . :in $ ?lid
                :where [?a :assessment/lease-id ?lid] [?a :assessment/payload ?p]]
              (d/db conn) lease-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (disbursement-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :disbursement/seq ?s] [?e :disbursement/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-disbursement-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :disbursement-sequence/jurisdiction ?j] [?e :disbursement-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (lease-already-disbursed? [s lease-id]
    (boolean (:disbursed? (lease s lease-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :lease/upsert
      (d/transact! conn [(lease->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/lease-id (first path) :assessment/payload (enc payload)}])

      :credit-screen/set
      (d/transact! conn [{:credit-screen/lease-id (first path) :credit-screen/payload (enc payload)}])

      :lease/mark-disbursed
      (let [lease-id (first path)
            {:keys [result lease-patch]} (finalize-disbursement! s lease-id)
            jurisdiction (:jurisdiction (lease s lease-id))
            next-n (inc (next-disbursement-sequence s jurisdiction))]
        (d/transact! conn
                     [(lease->tx (assoc lease-patch :id lease-id))
                      {:disbursement-sequence/jurisdiction jurisdiction :disbursement-sequence/next next-n}
                      {:disbursement/seq (count (disbursement-history s)) :disbursement/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-leases [s leases]
    (when (seq leases) (d/transact! conn (mapv lease->tx (vals leases)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:leases ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [leases]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-leases s leases))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo lease set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
