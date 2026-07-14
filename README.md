# cloud-itonami-isic-6491

Open Business Blueprint for **ISIC Rev.5 6491**: Financial leasing.
This repository publishes a leasing actor -- lease intake, jurisdiction
assessment, creditworthiness screening and lease-funding disbursement
-- as an OSS business that any qualified, licensed lessor can fork,
deploy, run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412)) --
a second financial-services vertical alongside `6492`'s credit
granting, but for asset-backed lease financing rather than general
lending. Here it is **Leasing-LLM ⊣ Leasing Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a lease-
> intake summary, normalizing records, and checking whether a lease's
> own collateral value actually covers its own financed amount at the
> lessor's own required minimum ratio -- but it has **no notion of
> which jurisdiction's leasing/consumer-credit requirements are
> official, no license to disburse real lease funding, and no way to
> know on its own whether an adverse credit flag against the lessee
> has actually stayed unresolved**. Letting it disburse lease funding
> directly invites fabricated jurisdiction citations, funding
> disbursed against under-collateralized leases, and an unresolved
> adverse credit flag being quietly overlooked -- and liability, and
> financial risk, for whoever runs it. This project seals the Leasing-
> LLM into a single node and wraps it with an independent **Leasing
> Governor**, a human **approval workflow**, and an immutable **audit
> ledger**.

## Scope: what this actor does and does not do

This actor covers lease intake through jurisdiction assessment,
creditworthiness screening and lease-funding disbursement. It does
**not**, by itself, hold any license required to operate a leasing
business in a given jurisdiction, and it does not claim to. It also
does **not** model a full asset-valuation/residual-value-forecasting
engine -- no equipment-category-specific depreciation curve, no
market-comparable appraisal workflow, no full loan-servicing system
(see `leasing.registry`'s own docstring for the honest simplification
this makes: a single representative minimum collateral-coverage ratio,
not an asset-category-by-asset-category survey of every risk-appetite
variant). Whoever deploys and operates a live instance (a licensed
lessor) supplies any jurisdiction-specific license, the real asset-
valuation and credit-underwriting expertise and the real loan-
servicing-system integrations, and bears that jurisdiction's liability
-- the software supplies the governed, spec-cited, audited execution
scaffold so that operator does not have to build the compliance layer
from scratch for every new market.

### Actuation

**Disbursing real lease funding is never autonomous, at any phase, by
construction.** Two independent layers enforce this (`leasing.
governor`'s `:actuation/fund-lease-disbursement` high-stakes gate and
`leasing.phase`'s phase table, which never puts `:disbursement/fund`
in any phase's `:auto` set) -- see `leasing.phase`'s docstring and
`test/leasing/phase_test.clj`'s `disbursement-fund-never-auto-at-any-
phase`. The actor may draft, check and recommend; a human lessor
officer is always the one who actually disburses lease funding. Like
`6511`/`6621`/`6629`/`6612`/`6492`/`7120`/`8620`/`7500`/`9603`/`9321`/
`9602`/`9000`/`9311`, this actor has ONE actuation event.

## The core contract

```
lease intake + jurisdiction facts (leasing.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Leasing-     │ ─────────────▶ │ Leasing                       │  (independent system)
   │ LLM (sealed) │  + citations    │ Governor:                     │
   └──────────────┘                 │ spec-basis · evidence-       │
                             commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ collateral-coverage-
                           record + ledger  escalate ─▶ human   insufficient (RATIO-
                                             (ALWAYS for         based, non-temporal) ·
                                              :disbursement/         adverse-credit-flag
                                              fund)                   unresolved (unconditional) ·
                                                                       already-disbursed
```

**The Leasing-LLM never disburses lease funding the Leasing Governor
would reject, and never does so without a human sign-off.** Hard
violations (fabricated jurisdiction requirements; unsupported
evidence; insufficient collateral coverage; an unresolved adverse
credit flag; a double disbursement) force **hold** and *cannot* be
approved past; a clean disbursement proposal still always routes to a
human.

## Run

```bash
clojure -M:dev:run     # walk one clean single-actuation lifecycle + four HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here an asset-inspection robot
documents leased-equipment condition before funding, under the actor,
gated by the independent **Leasing Governor**. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions require
human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Leasing Governor, lease-funding-disbursement draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`6491`). Related capability contracts (accounts/IBAN/double-entry-
ledger/clearing shapes) are published as [`kotoba-lang/banking`](https://github.com/kotoba-lang/banking);
this actor's `leasing.*` namespaces are a self-contained governed
implementation -- it does not require the capability lib directly, the
same "self-contained sibling" relationship `credit.*` (`6492`) has
toward its own `:banking` capability reference.

## Layout

| File | Role |
|---|---|
| `src/leasing/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + lease-funding-disbursement history. No dynamically-filed sub-record -- the actuation op acts directly on a pre-seeded lease, and the double-disbursement guard checks a dedicated `:disbursed?` boolean rather than a `:status` value |
| `src/leasing/registry.cljc` | Lease-funding-disbursement draft records, plus `collateral-coverage-ratio-insufficient?` -- the FIRST RATIO-based sufficiency check in this fleet's check-family taxonomy (every prior family compares two fields directly; this compares their QUOTIENT against a required minimum ratio) |
| `src/leasing/facts.cljc` | Per-jurisdiction leasing/consumer-credit catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/leasing/leasingllm.cljc` | **Leasing-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/creditworthiness-screening/funding-disbursement proposals |
| `src/leasing/governor.cljc` | **Leasing Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · collateral-coverage-insufficient, pure ground-truth RATIO-based recompute · adverse-credit-flag-unresolved, unconditional evaluation, the TWENTY-FIRST grounding of this discipline and FIRST specifically for an adverse-credit/financial-history-flag concept) + already-disbursed guard + 1 soft (confidence/actuation gate) |
| `src/leasing/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (funding disbursement always human; lease intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/leasing/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/leasing/sim.cljc` | demo driver |
| `test/leasing/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |
| `wasm/collateral_coverage.kotoba` | PoC: a WASM-compiled (`kotoba-lang/kotoba` -> `kotoba-lang/kototama`'s `actor:host` ABI) integer-cross-multiplication port of `leasing.registry/collateral-coverage-ratio-insufficient?`, i.e. `leasing.governor`'s `:collateral-coverage-insufficient` HARD check -- see `wasm/README.md` for the offset layout and cross-multiplication rationale |

## Business-process coverage (honest)

This actor covers lease intake through jurisdiction assessment,
creditworthiness screening and lease-funding disbursement -- the core
governed lifecycle this blueprint's own `docs/business-model.md` names
as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Lease intake + per-jurisdiction leasing/consumer-credit checklisting, HARD-gated on an official spec-basis citation (`:lease/intake`/`:jurisdiction/assess`) | A full asset-valuation/residual-value-forecasting engine (equipment-category-specific depreciation curves, market-comparable appraisal workflows -- see `leasing.registry`'s docstring) |
| Creditworthiness screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:creditworthiness/screen`) | Real loan-servicing-system integration, tax/regulatory reporting |
| Lease-funding disbursement, HARD-gated on full evidence and collateral-coverage sufficiency, plus a double-disbursement guard (`:disbursement/fund`) | Ongoing lease-servicing/collections workflows themselves |
| Immutable audit ledger for every intake/assessment/screening/disbursement decision | |

Extending coverage is additive: add the next gate (e.g. a
residual-value-shortfall check) as its own governed op with its own
HARD checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`leasing.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `leasing.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `leasing.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to make
coverage look bigger.

## Maturity

`:implemented` -- `Leasing-LLM` + `Leasing Governor` run as real,
tested code (see `Run` above), promoted from the originally-published
`:blueprint`-tier scaffold, modeled closely on the thirty prior
actors' architecture. See `docs/adr/0001-architecture.md` for the
history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
