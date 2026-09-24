# Production configuration checklist

The repository is a portfolio lab, not a drop-in production service. Before deploying a derived system, review every item below.

## Identity and transport security

- Replace demo HTTP Basic users with an external OIDC/OAuth2 provider.
- Enforce TLS at the ingress/load balancer and between internal components where required.
- Store credentials in a secret manager; never use the documented local defaults.
- Map immutable identity-provider subjects to internal customer IDs.
- Keep ownership predicates in repository queries even after changing authentication.
- Restrict Swagger/OpenAPI in environments where public API metadata is inappropriate.

## PostgreSQL

- Use managed backups and test restores.
- Configure connection-pool limits against the database's real capacity.
- Apply Flyway migrations as a controlled deployment step.
- Monitor slow queries, lock waits, deadlocks, replication lag, and disk growth.
- Define retention/archival for `outbox_events`, `processed_events`, and expired idempotency records.
- Review indexes using production cardinality and query plans.

## RabbitMQ and outbox

- Use separate durable credentials/vhosts per environment.
- Monitor pending outbox count, oldest pending age, retry count, publisher-confirm failures, queue depth, and DLQ depth.
- Alert on sustained outbox backlog or any unexpected DLQ traffic.
- Add explicit DLQ replay tooling and an operator runbook.
- Size publisher batches and intervals from measured traffic.
- Preserve stable event IDs and idempotent consumer behavior.
- Version integration-event schemas before external consumers depend on them.

## Availability

- Liveness must not depend on PostgreSQL, RabbitMQ, Redis, or downstream HTTP services.
- Readiness should include only dependencies required to safely accept new work.
- RabbitMQ is intentionally not a readiness dependency for order intake because the outbox buffers outages.
- Configure graceful termination so in-flight HTTP and publisher work can finish.

## Observability

- Ship ECS JSON logs to a centralized log platform.
- Propagate `X-Correlation-Id` to outgoing HTTP calls and message headers.
- Scrape Prometheus metrics from a protected network path or service account.
- Add dashboards and alerts for latency, error rate, saturation, DB pool usage, JVM health, outbox backlog, and DLQ traffic.
- Define SLOs before choosing alert thresholds.

## Delivery

- Run `./gradlew clean check` on every change.
- Run container/integration tests with Docker in CI.
- Scan dependencies and container images.
- Sign and retain build provenance if required.
- Deploy immutable images.
- Use staged rollout/rollback procedures.
- Backward-compatible database and event changes should precede code that requires them.

## Data and compliance

- Avoid logging credentials, tokens, personal data, or full sensitive request payloads.
- Define retention and deletion requirements.
- Review audit-log requirements separately from diagnostic application logs.
