# INC-005 evidence manifest

| Evidence | Producer | Output |
|---|---|---|
| Atomic order/outbox commit and rollback | `OutboxReliabilityIntegrationTest` | `build/evidence/inc-005/outbox-reliability.json` |
| Publisher outage retry metadata | `OutboxReliabilityIntegrationTest` | Same generated artifact |
| Duplicate delivery deduplication | `OutboxReliabilityIntegrationTest` | Same generated artifact |
| Verified CI snapshot | GitHub Actions | `outbox-reliability-ci.json` after the final passing run |

The integration test uses PostgreSQL Testcontainers and the real Flyway/JPA transaction boundary. RabbitMQ failure is injected through the `EventTransport` boundary so the retry state can be verified deterministically.

The checked-in CI snapshot is added only after the Java 25 workflow passes, matching the provenance style used by INC-001 through INC-004.
