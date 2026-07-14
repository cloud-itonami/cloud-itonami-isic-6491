# wasm/ — kotoba-wasm deployment of the collateral-coverage ratio check

`collateral_coverage.kotoba` is a port of
`leasing.registry/collateral-coverage-ratio-insufficient?` -- the check
`leasing.governor`'s `:collateral-coverage-insufficient` HARD violation
runs against a lease's own `:collateral-value` and `:financed-amount`
(see `src/leasing/registry.cljc`'s ns docstring: "the FIRST RATIO-based
sufficiency check in this fleet's check-family taxonomy") -- into the
minimal `.kotoba` language subset, compiled to a real WASM module via
`kotoba wasm emit`, and hosted via `kototama.tender`
(`test/wasm/collateral_coverage_test.clj`).

This follows the same `kotoba wasm emit` → `kototama.tender` pipeline
`cloud-itonami-isic-6492`'s `wasm/affordability.kotoba` and
`cloud-itonami-isic-6630`'s `wasm/fee_accrual.kotoba` established
(ADR-2607062330 addendum 5) -- `collateral_coverage.kotoba` is the
closest analog to `affordability.kotoba`: a ratio threshold compared via
integer cross-multiplication over two currency-unit inputs, no host
imports.

## Why the source differs from `leasing.registry`

The `.kotoba` compiler's actual WASM code-generator supports only a
small, empirically-verified subset: the special forms `do`/`let`/`if`
plus `+ - * quot / rem mod = < > <= >= zero? not inc dec` (confirmed by
reading `compile-wasm-expr` in `kotoba-lang/kotoba/src/kotoba/runtime.clj`
-- no `pos?`/`neg?`/`and`/`or`/`when`/map destructuring/top-level `def`,
unlike the broader tree-walking interpreter, same finding
`cloud-itonami-isic-6492`/`-6630` document). The port therefore:

- Uses plain positional args instead of `{:keys [collateral-value
  financed-amount]}` map destructuring (no maps in the wasm-compilable
  subset).
- Drops the `(number? collateral-value) (number? financed-amount)`
  type guards -- a WASM guest's two i32 memory reads are always numbers,
  so there is nothing left for those checks to reject.
- Replaces `(pos? financed-amount)` with `(> financed-amount 0)`, and
  the enclosing `and` with a nested `if` (no `and`/`or` in the subset) --
  the same substitution `affordability.kotoba` makes for
  `(pos? annual-income)`.
- Replaces the source's `(< (/ collateral-value financed-amount)
  minimum-coverage-ratio)` -- a floating-point ratio compared against
  `minimum-coverage-ratio` (`1.0`, "full collateralization", see
  `leasing.registry/minimum-coverage-ratio`) -- with the exact-integer
  cross-multiplication `(< (* 100 collateral-value) (* 100
  financed-amount))`. This mirrors, byte-for-byte in its inequality
  shape, the convention this SAME repo's own `leasing.kernels.gate`
  safety kernel already established for this exact check
  (`coverage-insufficient`, `src/leasing/kernels/gate.cljc`):
  `100*collateral < minimum-coverage-ratio-x100*financed`, with
  `minimum-coverage-ratio-x100 = 100`. `gate.cljc`'s own docstring notes
  `.kotoba`/wasm emission was deliberately NOT wired for it yet (owner
  decision 2026-07-12: ClojureScript + kotoba-datomic first) -- this
  module does not change that; it independently ports the ratio-compare
  arithmetic straight from `registry.cljc`, and happens to land on the
  identical cross-multiplied inequality `gate.cljc` already uses, because
  both are faithful integer reductions of the same `1.0` minimum ratio.
  The `* 100` factor is a no-op scale here (both sides use the same
  multiplier, since `minimum-coverage-ratio` is exactly `1.0`, not a
  fraction like `affordability.kotoba`'s `0.43`) but is kept explicit
  rather than simplified away, so the module stays legible as a genuine
  ratio comparison and stays byte-compatible with `gate.cljc`'s
  established constant name/value if a future minimum-ratio change ever
  needs both to move together.
- Preserves the source's own `financed-amount <= 0` behavior exactly:
  `collateral-coverage-ratio-insufficient?` treats a non-positive
  `financed-amount` as NOT insufficient (the `and`'s `pos?` guard makes
  the whole predicate false -- "zero financed-amount -> no division by
  zero, never flagged", per `test/leasing/registry_test.cljc`). This is
  the opposite fail direction from `gate.cljc`'s own 3-arg
  `coverage-insufficient`, which treats a non-positive `financed`
  reaching the kernel as insufficient (fail-closed) -- but `gate.cljc`'s
  docstring explains that difference is reconciled one layer up: its
  façade passes `applicable=0` for non-positive fields BEFORE the kernel
  runs, so the kernel's raw fail-closed branch is never actually hit by
  `leasing.governor`'s real behavior. This module ports
  `registry.cljc` directly (no `applicable` flag in its signature), so
  it reproduces `registry.cljc`'s literal behavior, not `gate.cljc`'s
  internal fail-closed default.

## ABI — parameterized invocation

`kotoba wasm emit` rejects any `main` with parameters (`:main-arity` --
the compiler only ever exports a 0-arity `main`, see `compile-wasm-expr`
in `kotoba-lang/kotoba/src/kotoba/runtime.clj`), so real inputs are
passed through the guest's exported linear memory instead -- the same
convention `cloud-itonami-isic-6492`'s `affordability.kotoba` and
`cloud-itonami-isic-6630`'s `fee_accrual.kotoba` use. A host writes two
little-endian i32 values (cents) before calling `main()`:

| offset | field               |
|--------|---------------------|
| 0      | `collateral-value`  |
| 4      | `financed-amount`   |

`main()` returns `1` (coverage insufficient -- `leasing.governor`'s
`:collateral-coverage-insufficient` HARD violation) or `0` (sufficient,
or a non-positive `financed-amount`, matching `registry.cljc`'s own
guard). Both offsets are well below `heap-base` (2048), so they never
collide with anything the compiler itself places in memory.

## Rebuilding

```sh
cd ../../kotoba-lang/kotoba   # sibling checkout, west-managed
bin/kotoba-clj wasm emit ../../cloud-itonami/cloud-itonami-isic-6491/wasm/collateral_coverage.kotoba \
  --package-lock kotoba.lock.edn \
  --output ../../cloud-itonami/cloud-itonami-isic-6491/wasm/collateral_coverage.wasm --json
```

(Built here with absolute paths for `<source>`/`--output`/`--package-lock`
instead -- `bin/kotoba-clj` resolves relative paths against the JVM's
*physical* cwd, which silently lands in the real shared `orgs/` checkout
rather than a symlinked sibling worktree when the `kotoba-lang/kotoba`
directory itself is a symlink.)

Fleet deployment: not attempted in this pass -- see
`cloud-itonami-isic-6492`/`cloud-itonami-isic-6511` for the established
pattern.
