# Delivery roadmap

The project is developed as a sequence of reproducible production-incident investigations. Main should finish each milestone in a working state; before/after behavior is preserved through Git history, issue references, test fixtures, and benchmark artifacts.

## Milestone 0 — Fragile baseline

Deliverables:

- Spring Boot 4 and Java 25 Gradle project.
- PostgreSQL schema managed by Flyway.
- Product creation and retrieval.
- Order creation, inventory reservation, order retrieval, and pagination.
- Docker Compose services for PostgreSQL, RabbitMQ, and Redis.
- Unit tests and PostgreSQL Testcontainers integration test.
- Actuator health and Prometheus endpoints.
- GitHub Actions verification.
- Explicit documentation of every intentional weakness.

Exit criteria:

- `./gradlew clean test` succeeds with JDK 25.
- Unit tests run without Docker.
- Integration test runs when Docker is present.
- `docker compose config` validates.
- Application starts against the Compose PostgreSQL service.

## Milestone 1 — INC-001: slow order search

Status: **Remediated**

- [x] Add deterministic seed data.
- [x] Capture and assert the baseline SQL query count.
- [x] Add a repeatable k6 load-test scenario and PostgreSQL plan capture.
- [x] Explain the N+1 query mechanism and missing-index cost.
- [x] Implement a pagination-safe fetch strategy and index.
- [x] Add query-count and functional regression tests.
- [x] Publish an evidence manifest and architecture decision record.

## Milestone 2 — INC-002: duplicate orders

Status: **Remediated**

- [x] Reproduce duplicate order creation using retrying clients.
- [x] Define idempotency-key semantics and SHA-256 request fingerprinting.
- [x] Add persistent idempotency records and a unique database constraint.
- [x] Return the original response for safe replays.
- [x] Reject key reuse with a different payload.
- [x] Add configurable expiry and scheduled cleanup behavior.
- [x] Add an eight-way concurrent replay integration test using virtual threads.
- [x] Publish an evidence manifest and architecture decision record.

## Milestone 3 — INC-003: inventory race

Status: **Remediated**

- [x] Reproduce overselling with coordinated concurrent transactions.
- [x] Capture the interleaving that causes the lost update.
- [x] Compare serializable, optimistic, pessimistic, and atomic strategies.
- [x] Select and document the appropriate trade-off.
- [x] Implement an atomic conditional stock update in the order transaction.
- [x] Prove one winner and one rejection for two buyers and one unit.
- [x] Publish an evidence manifest and architecture decision record.

## Milestone 4 — INC-004: broken authorization

Status: **Remediated**

- [x] Add authenticated customer and administrative principals.
- [x] Reproduce and block cross-customer order access.
- [x] Add ownership-based object and collection queries.
- [x] Restrict product administration to `ROLE_ADMIN`.
- [x] Keep health public while protecting detailed metrics.
- [x] Add positive and negative filter-chain integration tests.
- [x] Prove that the legacy customer header cannot spoof identity.
- [x] Publish an evidence manifest and architecture decision record.

## Milestone 5 — INC-005: lost events

Status: **Remediated**

- [x] Reproduce the database/message consistency failure window.
- [x] Introduce a transactional outbox table.
- [x] Add an outbox publisher with RabbitMQ publisher confirms and retry metadata.
- [x] Configure durable dead-letter handling.
- [x] Add duplicate-delivery-safe consumer behavior.
- [x] Add failure-injection integration tests.
- [x] Publish an evidence manifest and architecture decision record.

## Milestone 6 — Portfolio release

Status: **Complete**

- [x] Structured ECS JSON logs and request correlation IDs.
- [x] Explicit liveness and readiness health groups.
- [x] Prometheus metrics and an importable Grafana dashboard example.
- [x] OpenAPI/Swagger and a Postman collection.
- [x] Production configuration checklist.
- [x] Final architecture documentation and ADR set.
- [x] Three-minute demo script; the actual video recording is an external portfolio publishing step.
- [x] Portfolio case-study page.

## Milestone 7 — INC-006: hot product cache stampede

Status: **Remediated**

- [x] Reproduce a synchronized cold-cache burst against PostgreSQL.
- [x] Add a Redis-backed public product catalog.
- [x] Collapse cross-instance misses with a short-lived SET-NX rebuild lock.
- [x] Double-check after lock acquisition and release with token-safe Lua.
- [x] Add bounded wait/fallback semantics and positive TTL jitter.
- [x] Invalidate mutable stock only after the database transaction commits.
- [x] Fail open to PostgreSQL when Redis is unavailable.
- [x] Publish cache/DB-load Micrometer metrics.
- [x] Prove 24 concurrent cold reads collapse from 24 DB loads to exactly 1.
- [x] Publish an evidence manifest and architecture decision record.
