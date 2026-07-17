# Operator Quickstart: Financial leasing

## Prerequisites

- **Clojure CLI** (`clojure` command) — [install](https://clojure.org/guides/install_clojure)
- **JDK 11+** — required by Clojure
- **Git** — to fork this repository

### Monorepo context (optional)

If running inside the `com-junkawasaki/cloud-itonami` monorepo workspace:
- `../../kotoba-lang/langgraph/` — langgraph-clj (StateGraph runtime)
- `../../kotoba-lang/langchain/` — langchain-clj (transitive dependency for offline dev)
- `../../kotoba-lang/kototama/` — WASM host for collateral-coverage PoC tests

Standalone forks should override these with GitHub coordinates in `deps.edn`.

## Run tests

```bash
clojure -M:dev:test
```

This runs the full test suite covering:
- **Governor contract** — HARD checks (spec-basis, evidence-incomplete, collateral-coverage-insufficient, adverse-credit-flag-unresolved) and double-disbursement guard
- **Phase invariants** — 4-phase workflow (read-only → assisted intake → assisted assess → supervised)
- **Store parity** — MemStore ‖ DatomicStore behavior equivalence
- **Registry conformance** — lease-funding-disbursement record schemas
- **Facts coverage** — jurisdiction catalog completeness and citation accuracy

## Run the demo

```bash
clojure -M:dev:run
```

This walks a single clean lease through the full lifecycle plus four HARD-hold cases:
1. Fabricated jurisdiction requirement (fails spec-basis check)
2. Incomplete evidence (fails evidence-incomplete check)
3. Insufficient collateral coverage (fails RATIO-based collateral-coverage-insufficient check)
4. Unresolved adverse credit flag (fails unconditional adverse-credit-flag-unresolved check)

Output includes decision records and audit trail for each path.

## The Leasing Governor

**Location:** `src/leasing/governor.cljc`

The Governor is an independent verification layer that sits between the Leasing-LLM advisor and disbursement. It enforces:

- **4 HARD checks** (cannot be overridden):
  - Spec-basis citation required — every jurisdiction requirement must cite an official source
  - Evidence-incomplete — all supporting documents must be present
  - Collateral-coverage-insufficient — asset collateral value must meet the minimum ratio requirement (first RATIO-based check in this fleet)
  - Adverse-credit-flag-unresolved — unconditional hold if lessee has unresolved credit flags

- **1 soft gate** — confidence/actuation scoring for human review

- **Double-disbursement guard** — tracks `:disbursed?` on each lease; a second disbursement attempt is rejected

- **Human-mandatory actuation** — lease-funding disbursement always requires human sign-off, enforced at both the Governor level (`:actuation/fund-lease-disbursement` high-stakes gate) and the Phase table (`:disbursement/fund` never in auto sets)

See `src/leasing/phase.cljc` for the 4-phase workflow definition and `test/leasing/governor_test.clj` for contract verification.

## Jurisdiction coverage

Current catalog in `src/leasing/facts.cljc`: 4 seeded jurisdictions (JPN, USA, GBR, DEU) out of ~194 worldwide.

To add a jurisdiction:
1. Add one map entry to `leasing.facts/catalog` with official spec-basis citation
2. Run tests to confirm coverage reporting

Never fabricate a jurisdiction's requirements to inflate coverage.

## Lint

```bash
clojure -M:lint
```

Runs clj-kondo static analysis (errors fail CI).

## What's next

- **Customize the Governor** — adjust HARD check thresholds or add new gates in `src/leasing/governor.cljc`
- **Extend the Phase workflow** — add new phases or operations in `src/leasing/phase.cljc`
- **Import historical records** — see `Operator Guide` for first deployment checklist
- **Deploy** — see `docs/business-model.md` for unit economics and `docs/operator-guide.md` for production controls
