# ADR-0001: cloud-itonami-isic-6491 -- Leasing-LLM as a contained intelligence node

- Status: Accepted (2026-07-08)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/
  `7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/
  `8610`/`9311`/`8510`/`9412` ADR-0001s (the pattern this ADR ports);
  ADR-2607071250/ADR-2607071320/ADR-2607071351/ADR-2607071618/
  ADR-2607071640/ADR-2607071654/ADR-2607071717/ADR-2607071732/
  ADR-2607071752/ADR-2607071819/ADR-2607071849/ADR-2607071922/
  ADR-2607072715/ADR-2607072730/ADR-2607072745/ADR-2607072800/
  ADR-2607072815/ADR-2607072830/ADR-2607072845/ADR-2607072900/
  ADR-2607072915/ADR-2607080100 (`6612`/`6492`/`6920`/`6611`/`7120`/
  `8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/`8730`/`9102`/
  `9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`/`9412`, the twenty-
  two verticals built outside ADR-2607032000's original insurance/
  real-estate batch -- this is the twenty-third)
- Context: Continuing the standing "pick a new ISIC blueprint
  vertical" direction past `9412`, this ADR deepens `cloud-itonami-
  isic-6491` (financial leasing) from `:blueprint` to `:implemented`,
  the thirty-seventh actor in this fleet -- a SECOND financial-
  services vertical alongside `6492`'s credit granting, but for asset-
  backed lease financing rather than general lending.

## Problem

A lessor's lease-funding-disbursement workflow bundles several
distinct concerns under one governed workflow:

1. **Jurisdiction leasing/consumer-credit correctness** -- an official
   spec-basis citation from a real regulator (金融庁/the CFPB's
   Regulation M/the FCA's CONC/BaFin under the KWG), never
   fabricated.
2. **Collateral coverage sufficiency** -- does a lease's own
   collateral value cover its own financed amount at the lessor's own
   required minimum ratio? The FIRST RATIO-based instance in this
   fleet's check-family taxonomy (every prior sufficiency family
   compares two fields directly; this compares their QUOTIENT against
   a required minimum).
3. **Adverse-credit-flag resolution verification** -- has an adverse
   credit flag against the lessee actually stayed unresolved before
   funding is disbursed? The leasing-specific application of the
   unconditional-evaluation screening discipline this fleet's
   `casualty.governor/sanctions-violations` originally established --
   a TWENTY-FIRST distinct grounding overall, and the FIRST
   specifically for an adverse-credit/financial-history-flag concept
   (`credit.governor`'s own affordability check is a ground-truth
   recompute, not an unconditional-evaluation screening check, so this
   is a genuinely new sub-concept, not a reuse).
4. **Real, high-stakes actuation, once** -- disbursing real lease
   funding is a single actuation event with direct financial stakes.

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run a leasing business with an LLM" but
"seal the LLM inside a trust boundary and layer evidence-sufficiency,
collateral-coverage verification, adverse-credit-flag-resolution
verification, audit and human-approval on top of it, while
structurally fixing the one real actuation event as human-only."

## Decision

### 1. Leasing-LLM is sealed into the bottom node; it never disburses lease funding directly

`leasing.leasingllm` returns exactly four kinds of proposal: intake
normalization, jurisdiction leasing/consumer-credit checklist,
creditworthiness screening, and lease-funding-disbursement draft. No
proposal writes the SSoT or commits a real disbursement directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 leasing operation

`leasing.operation/build` is the SAME StateGraph shape as every
sibling actor's operation namespace, copied verbatim.

### 3. `collateral-coverage-ratio-insufficient?` is the FIRST ratio-based instance in this fleet's check-family taxonomy

Every prior sufficiency-check family in this fleet compares two fields
DIRECTLY: the TEMPORAL/non-temporal MINIMUM-threshold family
(`veterinary`/`funeral`/`hospital`/`association`, `a < b`), the
TEMPORAL/non-temporal MAXIMUM-ceiling family (`eldercare`/`museum`/
`salon`/`facility`/`school`, `a > b`), the two-sided range family
(`testlab`/`conservation`, `lo <= a <= hi`). `collateral-coverage-
ratio-insufficient?` instead compares a QUOTIENT of two fields against
a required minimum ratio (`(/ collateral-value financed-amount) <
minimum-coverage-ratio`) -- a genuinely different mathematical
relationship, not a renamed instance of an existing family. This
reflects the actual domain shape: collateral sufficiency for asset-
backed financing is naturally expressed as a coverage RATIO
(loan-to-value, inverted), not a raw difference.

### 4. Adverse-credit-flag screening reuses the unconditional-evaluation discipline for a twenty-first distinct grounding, and a first for this concept

`adverse-credit-flag-unresolved-violations` reuses `casualty.
governor/sanctions-violations`'s fix (evaluated unconditionally, not
scoped to a specific op, so the screening op itself can HARD-hold on
its own finding) for `:creditworthiness/screen` AND `:disbursement/
fund` -- the TWENTY-FIRST distinct application of this exact
discipline in this fleet overall. It is the FIRST specifically for an
adverse-credit/financial-history-flag concept: `credit.governor`
(`6492`)'s own `affordability-exceeded-violations` check is a pure
ground-truth recompute from the application's own debt-to-income
fields, not an unconditional-evaluation screening check reused from
`casualty`'s lineage, so this is a genuinely new sub-concept rather
than a reuse -- verified by reading `credit.governor`'s own source
before writing this check, to avoid mis-attributing a false
precedent.

### 5. The unconditional-evaluation check is tested via the SCREENING op directly, per the lesson already recorded by `parksafety` and ten later siblings

`adverse-credit-flag-is-held-and-unoverridable` calls
`:creditworthiness/screen` directly against `lease-4` (an adverse
flag), NOT `:disbursement/fund` against an unscreened lease -- because
a failing screen is itself a HARD hold whose payload never persists
to the store, so the actuation op alone could never discover the bad
ground-truth flag through this check family without the screening op
having actually been run first. This build applied that lesson
PROACTIVELY for an eleventh consecutive vertical (after `eldercare`,
`museum`, `conservation`, `salon`, `entertainment`, `casework`,
`hospital`, `facility`, `school` and `association`), further
reinforcing that lessons recorded in this fleet's ADRs transfer
forward reliably.

### 6. Single actuation, matching `6511`/`6621`/`6629`/`6612`/`6492`/`7120`/`8620`/`7500`/`9603`/`9321`/`9602`/`9000`/`9311`'s shape

`leasing.governor`'s `high-stakes` set has exactly one member
(`:actuation/fund-lease-disbursement`) -- this domain has ONE distinct
real-world, financially-critical act (disbursing lease funding), not
several independently-gated acts, matching the blueprint's own stated
scope.

### 7. Double-disbursement guard checks a dedicated boolean, not `:status`

`already-disbursed-violations` checks `:disbursed?`, a dedicated
boolean set once and never cleared, rather than a `:status` value that
could legitimately advance past a checked state (the exact trap
`cloud-itonami-isic-6492`'s ADR-0001 documents in detail, explicitly
avoided BY DESIGN in every sibling actor's equivalent guard since).
This actor's `:status` never needs to encode "has this actuation
already happened" at all -- a deliberate architectural choice applied
here for a twenty-second consecutive time.

### 8. Related capability contract cited, but not directly required (matching `6492`'s posture)

Like `credit.*` (`6492`), this actor's `leasing.*` namespaces cite
[`kotoba-lang/banking`](https://github.com/kotoba-lang/banking) as the
related capability contract for accounts/IBAN/double-entry-ledger/
clearing shapes, but do not require it directly -- `leasing.*` is a
self-contained governed implementation, the same "self-contained
sibling" relationship every prior actor with a related capability
contract maintains.

## Consequences

- (+) Financial leasing gets the same governed, auditable-actor
  treatment as the thirty prior actors, and this fleet now has a
  TWENTY-THIRD concrete precedent for extending past ADR-2607032000's
  original scope, deepening financial-services coverage alongside
  `6492`'s credit granting with a genuinely different financing model
  (asset-backed lease vs. general lending).
- (+) `collateral-coverage-ratio-insufficient?` is a genuine
  structural contribution: the first ratio-based sufficiency check in
  this fleet's taxonomy, distinct from every prior direct-comparison
  family.
- (+) `adverse-credit-flag-unresolved-violations` is a genuine domain-
  modeling contribution: the first unconditional-evaluation grounding
  for an adverse-credit/financial-history-flag concept, correctly
  distinguished from `credit.governor`'s unrelated ground-truth
  affordability recompute after directly reading that sibling's
  source rather than assuming a precedent existed.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/leasing/phase_test.clj`'s `disbursement-
  fund-never-auto-at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/leasing/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- (+) The adverse-credit-flag test/demo correctly applied the
  established SCREENING-op-directly pattern for an eleventh
  consecutive vertical, further evidence that lessons recorded in
  this fleet's ADRs continue to transfer forward reliably.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `leasing.facts/coverage`
  reports this honestly rather than claiming broader coverage.
- (-) `collateral-coverage-ratio-insufficient?` models only a single
  representative minimum coverage ratio (1.0, full collateralization),
  not a full asset-valuation/residual-value-forecasting engine
  (equipment-category-specific depreciation curves, market-comparable
  appraisal workflows are out of scope -- see that fn's own
  docstring); real loan-servicing-system integration and ongoing
  lease-servicing/collections workflows are all out of scope for this
  OSS actor -- each operator's responsibility (see README's coverage
  table).
- 30 tests / 128 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Add this as an addendum to any prior post-batch ADR | ❌ | All twenty-two of those ADRs' titles and scopes are explicitly `cloud-itonami-isic-6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`/`9412`; mixing a different financial-services sub-domain into `6492`'s ADR would blur scope boundaries even where the broad sector overlaps |
| Keep `cloud-itonami-isic-6491` at `:blueprint` only | ❌ | The standing direction continues past `9412`; financial leasing is a natural, well-precedented next domain, deepening this fleet's financial-services coverage alongside `6492`'s credit granting |
| Model `collateral-coverage-ratio-insufficient?` as a reuse of an existing MINIMUM-threshold check | ❌ | The actual comparison shape (a ratio of two fields against a required minimum, not a direct field-to-field comparison) is genuinely different; honestly framing this as a NEW ratio-based family, not a renamed instance, keeps the fleet's check-family taxonomy accurate |
| Assume `credit.governor` (`6492`) already established an adverse-credit-flag unconditional-evaluation check, and frame this as a reuse | ❌ | Reading `credit.governor`'s actual source showed its `affordability-exceeded-violations` check is a ground-truth recompute, not an unconditional-evaluation screening check reused from `casualty`'s lineage -- claiming a false precedent would misrepresent this fleet's own check-family history; verifying against the actual sibling source before writing the docstring avoided this |
| Test `adverse-credit-flag-unresolved-violations` via an actuation op against an unscreened lease (the shape `parksafety`'s ORIGINAL, buggy test used) | ❌ | Already proven wrong by `parksafety`'s own ADR-2607071922 Decision 5 and reconfirmed by ten later siblings' ADR-0001s -- a failing screen never persists its payload to the store, so the actuation op alone cannot discover the bad ground-truth flag through this check family; this build tested the SCREENING op directly from the start |
| Actually integrate `kotoba-lang/banking` as a code dependency | ❌ | `credit.governor` (`6492`), the closest sibling with the same `:banking` capability-tech tag, treats it as a cited-but-not-required related capability contract, not a code dependency -- matching that established posture keeps this actor self-contained like every other sibling, rather than introducing the fleet's first real inter-repo code coupling without a clear need |

## References

- ADR-2607071250/ADR-2607071320/ADR-2607071351/ADR-2607071618/
  ADR-2607071640/ADR-2607071654/ADR-2607071717/ADR-2607071732/
  ADR-2607071752/ADR-2607071819/ADR-2607071849/ADR-2607071922/
  ADR-2607072715/ADR-2607072730/ADR-2607072745/ADR-2607072800/
  ADR-2607072815/ADR-2607072830/ADR-2607072845/ADR-2607072900/
  ADR-2607072915/ADR-2607080100 (`6612`/`6492`/`6920`/`6611`/`7120`/
  `8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/`8730`/`9102`/
  `9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`/`9412`, first
  twenty-two post-batch verticals)
- ADR-2607032000 (original insurance/real-estate batch, Addenda 1-7)
- `cloud-itonami-isic-6491/docs/adr/0001-architecture.md` (this ADR)
- `kotoba-lang/industry` `resources/kotoba/industry/registry.edn`
  (fleet-wide maturity registry)
