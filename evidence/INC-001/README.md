# INC-001 evidence manifest

This directory explains how the evidence is produced; generated measurements remain build artifacts rather than hand-edited claims.

| Evidence | Producer | Output |
|---|---|---|
| SQL statement count | `OrderSearchQueryCountIntegrationTest` | `build/evidence/inc-001/query-count.json` |
| PostgreSQL plans | `performance/sql/inc-001-explain.sql` | `build/evidence/inc-001/explain-plan.txt` |
| HTTP latency distribution | `performance/k6/inc-001-order-search.js` | `build/evidence/inc-001/k6-summary.json` |

The deterministic dataset contains 10,000 orders, 30,000 order items, 1,000 products, and 250 customers. Run `performance/inc-001-run.sh` after starting the application.

The case study distinguishes two evidence types:

- **Invariant evidence:** the optimized page uses three prepared statements. CI enforces this.
- **Environment evidence:** latency and execution time vary by hardware, container runtime, and background load. The harness records them, but the documentation does not generalize one machine's results.
