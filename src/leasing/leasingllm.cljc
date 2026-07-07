(ns leasing.leasingllm
  "Leasing-LLM client -- the *contained intelligence node* for the
  leasing actor.

  It normalizes lease-intake, drafts a per-jurisdiction leasing/
  consumer-credit evidence checklist, screens leases for an adverse
  credit flag, and drafts the lease-funding-disbursement action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real funding disbursement. Every output is
  censored downstream by `leasing.governor` before anything touches
  the SSoT, and `:disbursement/fund` proposals NEVER auto-commit at
  any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/fund-lease-disbursement | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [leasing.facts :as facts]
            [leasing.registry :as registry]
            [leasing.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the lease, collateral/financed-amount figures or
  jurisdiction. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "リース記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :lease/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction leasing/consumer-credit evidence checklist draft.
  `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `leasing.facts` -- the Leasing Governor must reject this (never
  invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [l (store/lease db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction l))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "leasing.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-creditworthiness
  "Adverse-credit-flag screening draft. `:adverse-credit-flag?` on the
  lease record injects the failure mode: the Leasing Governor must
  HOLD, un-overridably, on any unresolved adverse credit flag."
  [db {:keys [subject]}]
  (let [l (store/lease db subject)]
    (cond
      (nil? l)
      {:summary "対象リース記録が見つかりません" :rationale "no lease record"
       :cites [] :effect :credit-screen/set :value {:lease-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:adverse-credit-flag? l))
      {:summary    (str (:lessee-name l) ": 与信不適格フラグを検出")
       :rationale  "スクリーニングが未解決の与信不適格フラグを検出。人手確認とホールドが必須。"
       :cites      [:credit-check]
       :effect     :credit-screen/set
       :value      {:lease-id subject :verdict :adverse}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:lessee-name l) ": 与信不適格フラグなし")
       :rationale  "与信スクリーニング完了。"
       :cites      [:credit-check]
       :effect     :credit-screen/set
       :value      {:lease-id subject :verdict :clean}
       :stake      nil
       :confidence 0.9})))

(defn- propose-lease-funding-disbursement
  "Draft the actual LEASE-FUNDING-DISBURSEMENT action -- disbursing
  real lease funding to a lessee. ALWAYS `:stake :actuation/fund-lease-
  disbursement` -- this is a REAL-WORLD funding act, never a draft the
  actor may auto-run. See README `Actuation`: no phase ever adds this
  op to a phase's `:auto` set (`leasing.phase`); the governor also
  always escalates on `:actuation/fund-lease-disbursement`. Two
  independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [l (store/lease db subject)]
    {:summary    (str subject " 向けリース資金拠出提案"
                      (when l (str " (lessee=" (:lessee-name l) ")")))
     :rationale  (if l
                   (str "collateral-value=" (:collateral-value l)
                        " financed-amount=" (:financed-amount l))
                   "リース記録が見つかりません")
     :cites      (if l [subject] [])
     :effect     :lease/mark-disbursed
     :value      {:lease-id subject}
     :stake      :actuation/fund-lease-disbursement
     :confidence (if (and l (not (registry/collateral-coverage-ratio-insufficient? l))) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :lease/intake            (normalize-intake db request)
    :jurisdiction/assess     (assess-jurisdiction db request)
    :creditworthiness/screen (screen-creditworthiness db request)
    :disbursement/fund       (propose-lease-funding-disbursement db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたはリース会社の資金拠出エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:lease/upsert|:assessment/set|:credit-screen/set|"
       ":lease/mark-disbursed) "
       ":stake(:actuation/fund-lease-disbursement か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess      {:lease (store/lease st subject)}
    :creditworthiness/screen  {:lease (store/lease st subject)}
    :disbursement/fund        {:lease (store/lease st subject)}
    {:lease (store/lease st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Leasing Governor escalates/
  holds -- an LLM hiccup can never auto-disburse lease funding."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :leasingllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
