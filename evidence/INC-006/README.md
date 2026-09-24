# INC-006 evidence manifest

| Evidence | Producer | Output |
|---|---|---|
| 24-way cold-miss baseline vs single-flight remediation | `ProductCatalogCacheIntegrationTest` | `build/evidence/inc-006/cache-stampede.json` |
| Post-commit inventory cache invalidation | `ProductCatalogCacheIntegrationTest` | Test assertion |
| Redis outage fail-open behavior | `ProductCatalogServiceTest` | Test assertion |
| Verified CI snapshot | GitHub Actions | `cache-stampede-ci.json` after the final passing run |

The integration test uses real PostgreSQL and Redis Testcontainers. A controlled database delay makes the cold-miss race deterministic enough to prove the stampede rather than relying on timing anecdotes.

The checked-in CI snapshot is added only after the final Java 25 workflow passes.
