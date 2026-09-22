# INC-004 evidence manifest

| Evidence | Producer | Output |
|---|---|---|
| Authentication, ownership, roles, and management matrix | `OrderAuthorizationIntegrationTest` | `build/evidence/inc-004/authorization-matrix.json` |

The test uses the real Spring Security filter chain, HTTP Basic request processing, controllers, service transactions, ownership-scoped repositories, Flyway schema, and PostgreSQL Testcontainers instance.
