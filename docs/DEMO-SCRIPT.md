# Three-minute demo script

## 0:00–0:25 — Frame the project

"This is Spring Boot Rescue Lab, a deliberately fragile order API that I repair through six production-style incidents. Each repair has a reproduction, root-cause analysis, regression test, ADR, and CI evidence."

Show the README incident table and the `baseline-fragile-v0.1.0` tag.

## 0:25–0:55 — Performance

Open INC-001.

"The baseline produced N+1 query amplification. A 20-order page required at least 42 SQL statements. I changed it to pagination-safe ID selection followed by a bounded graph fetch and added the supporting index. The regression test proves exactly three statements."

Show the query-count evidence.

## 0:55–1:25 — Retry safety and concurrency

Open INC-002 and INC-003.

"Client retries used to create duplicate orders, so I added customer-scoped persistent idempotency with request fingerprints and PostgreSQL advisory locking. Separately, two buyers could oversell the final unit; I replaced the read-modify-write path with an atomic conditional stock update."

Show the eight-way retry and two-buyers/one-unit tests.

## 1:25–1:50 — Authorization

Open INC-004.

"The API originally trusted a caller-controlled customer header. I moved identity to the authenticated principal, enforced ownership in repository predicates, separated customer/admin routes, and tested the complete 401/403/404/200 matrix."

Show `OrderAuthorizationIntegrationTest`.

## 1:50–2:35 — Lost events

Open INC-005 and its architecture diagram.

"A database commit and RabbitMQ publish cannot be one local transaction. I added a transactional outbox, publisher confirms, durable retry metadata, `SKIP LOCKED` batching, a dead-letter queue, and consumer-side deduplication. The failure-injection test proves rollback atomicity, retry behavior during a broker outage, and duplicate-delivery safety."

Point out that RabbitMQ is intentionally excluded from readiness because the outbox buffers outages.

## 2:20–2:40 — Cache stampede

Open INC-006.

"A plain Redis cache still allows every request to hit PostgreSQL when a hot key expires. I reproduced 24 simultaneous cold reads causing 24 database loads, then added a cross-instance Redis SET-NX rebuild lock, double-checking, bounded waiting, TTL jitter, and after-commit invalidation. The same 24 reads now produce exactly one PostgreSQL load, and Redis failure falls open to the database."

Show the 24 → 1 evidence and cache metrics.

## 2:40–3:00 — Production handoff

Show Swagger UI, correlation ID response header, structured logs, health probes, Grafana example, and the production checklist.

"The point of the project is not feature count. It is showing how I diagnose a failure, choose a trade-off, prove the repair, and leave the service more operable than I found it."
