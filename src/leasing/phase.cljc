(ns leasing.phase
  "Phase 0->3 staged rollout -- the leasing analog of `cloud-itonami-
  isic-6512`'s `casualty.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- lease intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-assess  -- adds jurisdiction assessment +
                                 creditworthiness screening writes,
                                 still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:lease/intake` (no capital risk yet)
                                 may auto-commit. `:disbursement/fund`
                                 NEVER auto-commits, at any phase.

  `:disbursement/fund` is deliberately ABSENT from every phase's
  `:auto` set, including phase 3 -- a permanent structural fact, not a
  rollout milestone still to come. Disbursing real lease funding is
  the one real-world legal act this actor performs; it is always a
  human lessor officer's call. `leasing.governor`'s `:actuation/fund-
  lease-disbursement` high-stakes gate enforces the same invariant
  independently -- two layers, not one, agree on this.
  `:creditworthiness/screen` is likewise never auto-eligible, at any
  phase -- the same posture every sibling's screening op has. Phase
  3's `:auto` set here has only ONE member (`:lease/intake`) -- this
  domain has no separate no-capital-risk 'file' lifecycle distinct
  from the lease record itself.")

(def read-ops  #{})
(def write-ops #{:lease/intake :jurisdiction/assess :creditworthiness/screen
                 :disbursement/fund})

;; NOTE the invariant: `:disbursement/fund` is a member of `write-ops`
;; (governor-gated like any write) but is NEVER a member of any
;; phase's `:auto` set below. Do not add it there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"       :writes #{}                                                          :auto #{}}
   1 {:label "assisted-intake" :writes #{:lease/intake}                                             :auto #{}}
   2 {:label "assisted-assess" :writes #{:lease/intake :jurisdiction/assess :creditworthiness/screen} :auto #{}}
   3 {:label "supervised-auto" :writes write-ops
      :auto #{:lease/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:disbursement/fund` is never auto-eligible at any phase, so it
    always escalates once the governor clears it (or holds if the
    governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Leasing Governor verdict to a base disposition before the
  phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
