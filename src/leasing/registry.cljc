(ns leasing.registry
  "Pure-function lease-funding-disbursement record construction -- an
  append-only leasing book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a lease-funding-disbursement
  reference number -- every lessor/jurisdiction assigns its own
  reference format. This namespace does NOT invent one; it builds a
  jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `leasing.facts` uses.

  `collateral-coverage-ratio-insufficient?` is the FIRST RATIO-based
  sufficiency check in this fleet's check-family taxonomy -- distinct
  from every prior family (the TEMPORAL/non-temporal MINIMUM-threshold
  family established by `veterinary`/`funeral`/`hospital`/
  `association`, the TEMPORAL/non-temporal MAXIMUM-ceiling family
  established by `eldercare`/`museum`/`salon`/`facility`/`school`, the
  two-sided range family established by `testlab`/`conservation`, the
  set-membership/conflict family and the set-containment/subset
  family). Rather than comparing two fields directly (`a < b` or
  `a > b`), it compares their QUOTIENT against a required minimum
  ratio: a lease's own collateral value divided by its own financed
  amount must reach the lessor's own recorded minimum coverage ratio
  before funding can be disbursed. A genuinely different mathematical
  relationship from every prior
  check family, not a renamed instance of an existing one.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real loan-servicing/leasing system. It builds the RECORD
  a lessor would keep, not the act of disbursing the lease funding
  itself (that is `leasing.operation`'s `:disbursement/fund`, always
  human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  lessor's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(def minimum-coverage-ratio
  "A single representative minimum collateral-coverage ratio (1.0 --
  full collateralization) required before lease funding may be
  disbursed. A single representative figure, not a lessor-by-lessor/
  jurisdiction-by-jurisdiction survey of every risk-appetite variant
  -- see ns docstring for the honest simplification this makes."
  1.0)

(defn collateral-coverage-ratio-insufficient?
  "Does `lease`'s own `:collateral-value` divided by its own
  `:financed-amount` fall short of `minimum-coverage-ratio`? A pure
  ground-truth check against the lease's own permanent fields -- no
  upstream comparison needed. The FIRST ratio-based instance in this
  fleet's sufficiency-check taxonomy (see ns docstring)."
  [{:keys [collateral-value financed-amount]}]
  (and (number? collateral-value) (number? financed-amount) (pos? financed-amount)
       (< (/ collateral-value financed-amount) minimum-coverage-ratio)))

(defn register-lease-funding-disbursement
  "Validate + construct the LEASE-FUNDING-DISBURSEMENT registration
  DRAFT -- the lessor's own legal act of disbursing real lease funding
  to a lessee. Pure function -- does not touch any real loan-
  servicing/leasing system; it builds the RECORD a lessor would keep.
  `leasing.governor` independently re-verifies the lease's own
  collateral-coverage sufficiency and adverse-credit-flag status, and
  blocks a double-disbursement of the same lease, before this is ever
  allowed to commit."
  [lease-id jurisdiction sequence]
  (when-not (and lease-id (not= lease-id ""))
    (throw (ex-info "lease-funding-disbursement: lease_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "lease-funding-disbursement: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "lease-funding-disbursement: sequence must be >= 0" {})))
  (let [disbursement-number (str (str/upper-case jurisdiction) "-DSB-" (zero-pad sequence 6))
        record {"record_id" disbursement-number
                "kind" "lease-funding-disbursement-draft"
                "lease_id" lease-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "disbursement_number" disbursement-number
     "certificate" (unsigned-certificate "LeaseFundingDisbursement" disbursement-number disbursement-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
