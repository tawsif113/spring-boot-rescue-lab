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

1. Add deterministic seed data.
2. Capture the baseline SQL query count and latency distribution.
3. Add a repeatable k6 load-test scenario.
4. Explain the N+1 query mechanism and missing-index cost.
5. Implement a pagination-safe fetch strategy and indexes.
6. Add query-count and regression tests.
7. Publish before/after evidence.

## Milestone 2 — INC-002: duplicate orders

1. Reproduce duplicate order creation using retrying clients.
2. Define idempotency-key semantics and request fingerprinting.
3. Add persistent idempotency records and a unique database constraint.
4. Return the original response for safe replays.
5. Reject key reuse with a different payload.
6. Add expiry and cleanup behavior.
7. Add concurrent replay integration tests.

## Milestone 3 — INC-003: inventory race

1. Reproduce overselling with coordinated concurrent requests.
2. Capture the interleaving that causes the lost update.
3. Compare optimistic locking, pessimistic locking, and atomic SQL updates.
4. Select and document the appropriate trade-off.
5. Implement the chosen strategy.
6. Add concurrency and retry tests.

## Milestone 4 — INC-004: broken authorization

1. Add authenticated user identities and roles.
2. Reproduce cross-customer order access.
3. Add ownership-based query methods and authorization rules.
4. Restrict administrative endpoints.
5. Reduce exposed Actuator endpoints.
6. Add positive and negative security tests.

## Milestone 5 — INC-005: lost events

1. Add direct RabbitMQ publication and reproduce the database/message inconsistency.
2. Introduce a transactional outbox table.
3. Add an idempotent outbox publisher with retry metadata.
4. Configure dead-letter handling.
5. Add duplicate-delivery-safe consumer behavior.
6. Add failure-injection integration tests.

## Milestone 6 — Portfolio release

- Structured logs and request correlation IDs.
- Liveness and readiness probes.
- Prometheus dashboard examples.
- OpenAPI document and Postman collection.
- Production configuration checklist.
- Architecture decision records.
- Three-minute demo script and recording.
- Portfolio case-study page.

