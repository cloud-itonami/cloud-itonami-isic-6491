(ns leasing.facts
  "Per-jurisdiction financial-leasing regulatory catalog -- the
  G2-style spec-basis table the Leasing Governor checks every
  jurisdiction/assess proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's leasing/consumer-
  credit requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official financial-
  leasing/consumer-credit regulator (see `:provenance`); they are a
  STARTING catalog, not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to
  `catalog`, cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.

  The USA entry cites the Consumer Financial Protection Bureau's
  Regulation M (the Consumer Leasing Act's implementing regulation),
  the GBR entry cites the FCA's Consumer Credit sourcebook (CONC)
  under the Consumer Credit Act 1974, the DEU entry cites BaFin's
  supervision of finance-leasing activities under the
  Kreditwesengesetz (KWG) §1 Abs. 1a, and the JPN entry cites the
  Financial Services Agency's oversight of money-lending/installment-
  finance activities under the Money Lending Business Act and the
  Installment Sales Act -- the closest official regulatory framing
  available for lease financing in each jurisdiction, not a generic
  banking regulator citation.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  lease-application/creditworthiness-disclosure/collateral-valuation/
  funding-source evidence set submitted in some form; `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any :jurisdiction/assess proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "金融庁 (Financial Services Agency, FSA)"
          :legal-basis "貸金業法 (Money Lending Business Act) / 割賦販売法 (Installment Sales Act)"
          :national-spec "リース契約における与信審査・担保評価・資金源泉に係る運営基準"
          :provenance "https://www.fsa.go.jp/"
          :required-evidence ["リース申込書 (lease-application record)"
                              "与信審査開示書 (creditworthiness-disclosure document)"
                              "担保評価証明書 (collateral-valuation certificate)"
                              "資金源泉確認書 (funding-source verification document)"]}
   "USA" {:name "United States"
          :owner-authority "Consumer Financial Protection Bureau (CFPB)"
          :legal-basis "Consumer Leasing Act (Regulation M, 12 CFR Part 1013)"
          :national-spec "Consumer lease disclosure, creditworthiness and collateral-valuation requirements"
          :provenance "https://www.consumerfinance.gov/rules-policy/regulations/1013/"
          :required-evidence ["Lease-application record"
                              "Creditworthiness-disclosure document"
                              "Collateral-valuation certificate"
                              "Funding-source verification document"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Financial Conduct Authority (FCA)"
          :legal-basis "Consumer Credit Act 1974 / FCA Consumer Credit sourcebook (CONC)"
          :national-spec "CONC creditworthiness assessment and collateral-disclosure requirements"
          :provenance "https://www.fca.org.uk/"
          :required-evidence ["Lease-application record"
                              "Creditworthiness-disclosure document"
                              "Collateral-valuation certificate"
                              "Funding-source verification document"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesanstalt für Finanzdienstleistungsaufsicht (BaFin)"
          :legal-basis "Kreditwesengesetz (KWG) §1 Abs. 1a (Finanzierungsleasing)"
          :national-spec "Bonitätsprüfung, Sicherheitenbewertung und Mittelherkunftsnachweis für Finanzierungsleasing"
          :provenance "https://www.bafin.de/"
          :required-evidence ["Leasingantrag (lease-application record)"
                              "Bonitätsoffenlegung (creditworthiness-disclosure document)"
                              "Sicherheitenbewertungsnachweis (collateral-valuation certificate)"
                              "Mittelherkunftsnachweis (funding-source verification document)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to fund a lease
  disbursement on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-6491 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `leasing.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
