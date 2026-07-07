(ns leasing.governor
  "Leasing Governor -- the independent compliance layer that earns the
  Leasing-LLM the right to commit. The LLM has no notion of
  jurisdictional leasing/consumer-credit law, whether a lease's own
  collateral value actually covers its own financed amount at the
  lessor's own required minimum ratio, whether an adverse credit flag
  against the lessee has actually stayed unresolved, or when an act
  stops being a draft and becomes a real-world funding disbursement,
  so this MUST be a separate system able to *reject* a proposal and
  fall back to HOLD -- the leasing analog of `cloud-itonami-isic-
  6512`'s CasualtyGovernor.

  Five checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them (you don't get to approve your way
  past a fabricated jurisdiction spec-basis, incomplete evidence,
  insufficient collateral coverage, an unresolved adverse credit flag,
  or a double disbursement). The confidence/actuation gate is SOFT: it
  asks a human to look (low confidence / actuation), and the human may
  approve -- but see `leasing.phase`: for `:stake :actuation/fund-
  lease-disbursement` (a real funding act) NO phase ever allows auto-
  commit either. Two independent layers agree that actuation is always
  a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`leasing.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:disbursement/fund`, has the
                                       jurisdiction actually been
                                       assessed with a full lease-
                                       application/creditworthiness-
                                       disclosure/collateral-valuation/
                                       funding-source evidence
                                       checklist on file?
    3. Collateral coverage
       insufficient                  -- for `:disbursement/fund`,
                                       INDEPENDENTLY recompute whether
                                       the lease's own collateral value
                                       divided by its own financed
                                       amount reaches `leasing.
                                       registry/minimum-coverage-ratio`
                                       (`leasing.registry/collateral-
                                       coverage-ratio-insufficient?`)
                                       -- needs no proposal inspection
                                       or stored-verdict lookup at all.
                                       The FIRST RATIO-based instance
                                       in this fleet's sufficiency-
                                       check taxonomy (see that fn's
                                       own docstring).
    4. Adverse credit flag
       unresolved                     -- reported by THIS proposal
                                       itself (a `:creditworthiness/
                                       screen` that just found an
                                       adverse credit flag), or already
                                       on file for the lease
                                       (`:creditworthiness/screen`/
                                       `:disbursement/fund`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...
                                       (twenty prior siblings)...
                                       established -- the TWENTY-FIRST
                                       distinct application of this
                                       exact discipline, and the FIRST
                                       specifically for an adverse-
                                       credit/financial-history-flag
                                       concept (`credit.governor`'s own
                                       affordability check is a
                                       ground-truth recompute, not an
                                       unconditional-evaluation
                                       screening check, so this is a
                                       genuinely new sub-concept, not a
                                       reuse). Like the ten most recent
                                       siblings' equivalent checks,
                                       this is exercised in
                                       tests/demo via `:creditworthiness/
                                       screen` DIRECTLY, not via the
                                       actuation op against an
                                       unscreened lease -- see this
                                       ns's own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:disbursement/
                                       fund` (a REAL funding act) ->
                                       escalate.

  One more guard, double-disbursement prevention, is enforced but NOT
  listed as a numbered HARD check above because it needs no upstream
  comparison at all -- `already-disbursed-violations` refuses to fund
  a lease's disbursement twice, off a dedicated `:disbursed?` fact
  (never a `:status` value) -- the SAME 'check a dedicated boolean,
  not status' discipline every prior sibling governor's guards
  establish, informed by `cloud-itonami-isic-6492`'s status-lifecycle
  bug (ADR-2607071320)."
  (:require [leasing.facts :as facts]
            [leasing.registry :as registry]
            [leasing.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Disbursing real lease funding is the ONE real-world actuation event
  this actor performs -- a single-member set, matching every prior
  single-actuation sibling's shape."
  #{:actuation/fund-lease-disbursement})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:disbursement/fund`) proposal with no
  spec-basis citation is a HARD violation -- never invent a
  jurisdiction's leasing/consumer-credit requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :disbursement/fund} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:disbursement/fund`, the jurisdiction's required lease-
  application/creditworthiness-disclosure/collateral-valuation/
  funding-source evidence must actually be satisfied -- do not trust
  the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (= op :disbursement/fund)
    (let [l (store/lease st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction l) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(リース申込書/与信審査開示書/担保評価証明書/資金源泉確認書等)が充足していない状態での提案"}]))))

(defn- collateral-coverage-insufficient-violations
  "For `:disbursement/fund`, INDEPENDENTLY recompute whether the
  lease's own collateral-value/financed-amount ratio reaches
  `leasing.registry/minimum-coverage-ratio` via `leasing.registry/
  collateral-coverage-ratio-insufficient?` -- needs no proposal
  inspection or stored-verdict lookup at all, since its input is a
  permanent ground-truth field already on the lease."
  [{:keys [op subject]} st]
  (when (= op :disbursement/fund)
    (let [l (store/lease st subject)]
      (when (registry/collateral-coverage-ratio-insufficient? l)
        [{:rule :collateral-coverage-insufficient
          :detail (str subject " の担保評価額(" (:collateral-value l)
                      ")が融資額(" (:financed-amount l) ")に対する必要充足率に満たない")}]))))

(defn- adverse-credit-flag-unresolved-violations
  "An unresolved adverse credit flag -- reported by THIS proposal (e.g.
  a `:creditworthiness/screen` that itself just found one), or already
  on file in the store for the lease (`:creditworthiness/screen`/
  `:disbursement/fund`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :adverse (get-in proposal [:value :verdict]))
        lease-id (when (contains? #{:creditworthiness/screen :disbursement/fund} op) subject)
        hit-on-file? (and lease-id (= :adverse (:verdict (store/credit-screen-of st lease-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :adverse-credit-flag-unresolved
        :detail "未解決の与信不適格フラグがある状態でのリース資金拠出提案は進められない"}])))

(defn- already-disbursed-violations
  "For `:disbursement/fund`, refuses to fund the SAME lease's
  disbursement twice, off a dedicated `:disbursed?` fact (never a
  `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :disbursement/fund)
    (when (store/lease-already-disbursed? st subject)
      [{:rule :already-disbursed
        :detail (str subject " は既に資金拠出済み")}])))

(defn check
  "Censors a Leasing-LLM proposal against the governor rules. Returns
  {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (collateral-coverage-insufficient-violations request st)
                           (adverse-credit-flag-unresolved-violations request proposal st)
                           (already-disbursed-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
