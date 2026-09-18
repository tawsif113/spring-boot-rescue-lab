# INC-002 — Duplicate orders after retries

**Status:** Planned

## Scenario

A client times out after the server commits an order and retries the same request. The fragile baseline treats the retry as a new command and creates another order.

The investigation will define an idempotency contract, persist request fingerprints, and prove safe behavior under concurrent retries.

