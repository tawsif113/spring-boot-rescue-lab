# INC-005 evidence manifest

| Evidence | Producer | Output |
|---|---|---|
| Atomic order/outbox commit and rollback | `OutboxReliabilityIntegrationTest` | `build/evidence/inc-005/outbox-reliability.json` |
| Publisher outage retry metadata | `OutboxReliabilityIntegrationTest` | Same generated artifact |
| Duplicate delivery deduplication | `OutboxReliabilityIntegrationTest` | Same generated artifact |
| Verified CI snapshot | GitHub Actions run [`35957856126`](https://github.com/tawsif113/spring-boot-rescue-lab/actions/runs/35957856126) | [`outbox-reliability-ci.json`](outbox-reliability-ci.json) |

The integration test uses PostgreSQL Testcontainers and the real Flyway/JPA transaction boundary. RabbitMQ failure is injected through the `EventTransport` boundary so the retry state can be verified deterministically.

The checked-in snapshot is copied from the passing Java 25 workflow artifact and records the workflow head SHA, artifact ID, digest, and verification timestamp, matching the provenance style used by INC-001 through INC-004.
