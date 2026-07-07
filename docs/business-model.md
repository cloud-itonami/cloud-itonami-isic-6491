# Business Model: Financial leasing

## Classification

- Repository: `cloud-itonami-isic-6491`
- ISIC Rev.5: `6491`
- Activity: financial leasing -- extending long-term financing where the lessee acquires substantially all the risks/rewards of the leased asset
- Social impact: financial inclusion, data sovereignty, transparent audit

## Customer

- independent equipment-leasing companies
- cooperative asset-finance pools
- community leasing programs

## Offer

- lease-application intake
- creditworthiness/collateral disclosure proposal
- lease-funding disbursement proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per lease-in-force
- support: monthly retainer with SLA
- migration: import from an incumbent leasing system
- funding-disbursement fee

## Trust Controls

- no lease is funded/disbursed without human sign-off
- a fabricated jurisdiction citation, incomplete evidence, insufficient
  collateral coverage, or an unresolved adverse credit flag -- each
  forces a hold, not an override
- a lease's funding cannot be disbursed twice: a double-disbursement
  attempt is held off this actor's own lease facts alone, with no
  upstream comparison needed
- every intake, assessment, screening and disbursement path is
  auditable
- emergency manual override paths remain outside LLM control
