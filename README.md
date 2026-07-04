# cloud-itonami-isic-6491

Open Business Blueprint for **ISIC Rev.5 6491**: Financial leasing.

This repository designs a forkable OSS business for financial leasing -- extending long-term financing where the lessee acquires substantially all the risks/rewards of the leased asset -- run by a qualified, licensed operator so a community or
independent professional never surrenders customer data and ledgers to a
closed SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here an asset-inspection robot documents leased-equipment condition before funding,
under an actor that proposes actions and an independent **Leasing Governor**
that gates them. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions require human sign-off.

## Core Contract

```text
intake + identity + case/account records
        |
        v
Leasing-LLM -> Leasing Governor -> hold, proceed, or human approval
        |
        v
case/account ledger + evidence record + audit
```

No automated proposal, by itself, can complete the following without governor
approval and audit evidence: disbursing lease funding.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`6491`). Required capabilities are implemented by:

- [`kotoba-lang/banking`](https://github.com/kotoba-lang/banking)
  -- accounts, IBAN, double-entry ledger, clearing

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Maturity

`:blueprint` -- this repository is the published business/operator design.
The governed actor implementation (`Leasing-LLM` + `Leasing Governor` as
running code) is a follow-up, same as any other `:blueprint`-tier
`cloud-itonami-*` entry in `kotoba-lang/industry`'s registry.

## License

Code and implementation templates are AGPL-3.0-or-later.
