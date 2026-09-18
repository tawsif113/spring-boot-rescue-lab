# INC-001 — Slow order search

**Status:** Planned

## Scenario

The paginated order endpoint becomes progressively slower as more orders and line items are returned. The fragile baseline loads lazy relationships while mapping every order response and omits useful order-list indexes.

## Planned evidence

- SQL statement count per request.
- PostgreSQL execution plan.
- p50, p95, and p99 latency from a repeatable load test.
- Dataset size and hardware/runtime details.

No performance result will be published until it is measured reproducibly.

