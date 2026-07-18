(ns leasing.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger seq 7): this repo previously had NO demo page and
  no generator at all. This namespace drives the REAL actor stack
  (`leasing.operation` -> `leasing.governor` -> `leasing.store`) through
  a scenario mined directly from this repo's own `leasing.sim` demo
  driver (`clojure -M:dev:run`, confirmed to run correctly against the
  real seeded lease directory before this file was written -- every id
  it drives (`lease-1`..`lease-4`) exists in `leasing.store/demo-data`,
  and every disposition it produces matches the Leasing Governor's own
  documented rules exactly, unlike `cloud-itonami-isic-851`'s
  `schoolops.sim`, which turned out to reference ids absent from its
  own seed data -- so it was safe to reuse rather than author from
  scratch), rendered deterministically -- no invented numbers, no
  timestamps in the page content, byte-identical across reruns against
  the same seed (verified by diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [leasing.store :as store]
            [leasing.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :lessor-officer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: lease-1 (JPN, fully collateralized, no adverse
  credit flag) clears intake (phase-3 auto-commit, the ONLY op this
  actor ever auto-commits), then its jurisdiction assessment,
  creditworthiness screening and lease-funding-disbursement proposal
  each ALWAYS escalate (per `leasing.phase`/the governor's
  `:actuation/fund-lease-disbursement` high-stakes gate) and are
  approved by a human lessor officer; lease-2's jurisdiction assessment
  is driven with `:no-spec?` (no official spec-basis catalog entry for
  the requested jurisdiction) -> HARD hold on `:no-spec-basis`, never
  reaching a human; lease-3 clears its own jurisdiction assessment
  (escalate -> approved) but its disbursement HARD-holds on
  `:collateral-coverage-insufficient` (60000 collateral against 100000
  financed -- independently recomputed by the governor, not trusted
  from the proposal); lease-4 (seeded with `:adverse-credit-flag? true`)
  HARD-holds its creditworthiness screening on
  `:adverse-credit-flag-unresolved`; and a second attempt to disburse
  lease-1's already-disbursed funding HARD-holds on
  `:already-disbursed`. Every HARD hold never reaches a human. Returns
  the resulting store -- every field read by `render` below is real
  governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "l1-intake" {:op :lease/intake :subject "lease-1"
                               :patch {:id "lease-1" :lessee-name "Sakura Tanaka"}})

    (exec! actor "l1-assess" {:op :jurisdiction/assess :subject "lease-1"})
    (approve! actor "l1-assess")

    (exec! actor "l1-screen" {:op :creditworthiness/screen :subject "lease-1"})
    (approve! actor "l1-screen")

    (exec! actor "l1-fund" {:op :disbursement/fund :subject "lease-1"})
    (approve! actor "l1-fund")

    (exec! actor "l2-assess" {:op :jurisdiction/assess :subject "lease-2" :no-spec? true})

    (exec! actor "l3-assess" {:op :jurisdiction/assess :subject "lease-3"})
    (approve! actor "l3-assess")

    (exec! actor "l3-fund" {:op :disbursement/fund :subject "lease-3"})

    (exec! actor "l4-screen" {:op :creditworthiness/screen :subject "lease-4"})

    (exec! actor "l1-fund-again" {:op :disbursement/fund :subject "lease-1"})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger lease-id]
  (last (filter #(= (:subject %) lease-id) ledger)))

(defn- status-cell [ledger lease-id]
  (let [f (last-fact-for ledger lease-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- lease-row [ledger {:keys [id lessee-name jurisdiction collateral-value financed-amount
                                  adverse-credit-flag? disbursed?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc lessee-name) (esc jurisdiction)
          (esc collateral-value) (esc financed-amount)
          (if adverse-credit-flag? "<span class=\"critical\">adverse</span>" "<span class=\"ok\">clean</span>")
          (if disbursed? "<span class=\"ok\">disbursed</span>" "<span class=\"muted\">not disbursed</span>")
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract (README `Ops`
  ;; table, `leasing.governor`/`leasing.phase`) -- documentation of
  ;; fixed behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:lease/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean -- the ONLY op this actor ever auto-commits</span></td></tr>"
   "        <tr><td><code>:jurisdiction/assess</code></td><td><span class=\"warn\">ALWAYS human approval &middot; spec-basis independently checked against <code>leasing.facts</code></span></td></tr>"
   "        <tr><td><code>:creditworthiness/screen</code></td><td><span class=\"warn\">ALWAYS human approval, never auto at any phase &middot; adverse-credit-flag HARD-holds un-overridably</span></td></tr>"
   "        <tr><td><code>:disbursement/fund</code></td><td><span class=\"warn\">ALWAYS human approval, never auto at any phase &middot; collateral-coverage independently recomputed, double-disbursement blocked</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        leases (store/all-leases db)
        lease-rows (str/join "\n" (map (partial lease-row ledger) leases))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-6491 &middot; financial leasing</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Financial leasing (ISIC 6491) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · lease-funding disbursement always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Leases</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>leasing.store</code> via <code>leasing.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Lease</th><th>Lessee</th><th>Jurisdiction</th><th>Collateral value</th><th>Financed amount</th><th>Credit screen</th><th>Funding</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     lease-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Leasing Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Jurisdiction spec-basis, evidence completeness, collateral-coverage ratio and adverse-credit-flag status are all independently checked from the lease's own permanent fields, never trusted from the advisor's proposal.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Lease</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/disbursement-history db)) "draft disbursement records )")))
