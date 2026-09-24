# Incident investigations

Every incident is a transparent simulation built to demonstrate a repeatable troubleshooting process.

| Incident | Failure | Status |
|---|---|---|
| [INC-001](INC-001-slow-order-search.md) | Slow order search / N+1 queries | Remediated |
| [INC-002](INC-002-duplicate-orders.md) | Duplicate orders after retries | Remediated |
| [INC-003](INC-003-inventory-race.md) | Concurrent inventory overselling | Remediated |
| [INC-004](INC-004-broken-authorization.md) | Broken object-level authorization | Remediated |
| [INC-005](INC-005-lost-events.md) | Lost integration events | Remediated |
| [INC-006](INC-006-cache-stampede.md) | Hot product cache stampede | Remediated |

Each report follows the same engineering shape: business impact, symptoms/evidence, deterministic reproduction, root cause, alternative analysis, selected remediation, regression tests, measured proof, and production follow-up.

The original weaknesses are preserved through Git history and the `baseline-fragile-v0.1.0` tag so the repaired behavior can be compared with the deliberately fragile starting point.
