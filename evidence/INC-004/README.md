# INC-004 evidence manifest

| Evidence | Producer | Output |
|---|---|---|
| Authentication, ownership, roles, and management matrix | `OrderAuthorizationIntegrationTest` | `build/evidence/inc-004/authorization-matrix.json` |
| Verified CI snapshot and provenance | GitHub Actions run [`35692302177`](https://github.com/tawsif113/spring-boot-rescue-lab/actions/runs/35692302177) | [`authorization-matrix-ci.json`](authorization-matrix-ci.json) |

The test uses the real Spring Security filter chain, HTTP Basic request processing, controllers, service transactions, ownership-scoped repositories, Flyway schema, and PostgreSQL Testcontainers instance.

The checked-in snapshot is copied from artifact `10679087370` only after the complete Java 25 CI job passes. Its recorded artifact digest makes the result traceable to the downloaded workflow output.
