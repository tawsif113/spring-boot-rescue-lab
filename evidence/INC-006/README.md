# INC-006 evidence manifest

| Evidence | Producer | Output |
|---|---|---|
| 24-way cold-miss baseline vs single-flight remediation | `ProductCatalogCacheIntegrationTest` | `build/evidence/inc-006/cache-stampede.json` |
| Post-commit inventory cache invalidation | `ProductCatalogCacheIntegrationTest` | Test assertion |
| Redis outage fail-open behavior | `ProductCatalogServiceTest` | Test assertion |
| Verified CI snapshot | GitHub Actions run [`36015602420`](https://github.com/tawsif113/spring-boot-rescue-lab/actions/runs/36015602420) | [`cache-stampede-ci.json`](cache-stampede-ci.json) |

The integration test uses real PostgreSQL and Redis Testcontainers. A controlled database delay makes the cold-miss race deterministic enough to prove the stampede rather than relying on timing anecdotes.

The checked-in snapshot records the passing Java 25 workflow head SHA, artifact ID, artifact digest, and verification timestamp.
