# ADR 0005: Transactional outbox with at-least-once delivery

- **Status:** Accepted
- **Date:** 2026-09-24
- **Incident:** [INC-005](../../incidents/INC-005-lost-events.md)

## Context

Order creation changes PostgreSQL state and must also emit an integration event to RabbitMQ. Those systems do not share one local transaction. Direct publication before or after the database commit leaves a failure window that can create phantom messages or lost events.

## Decision

Persist an outbox event in the same PostgreSQL transaction as the order. A scheduled publisher locks due rows with `FOR UPDATE SKIP LOCKED`, publishes them to RabbitMQ, waits for publisher confirms, and marks them published only after acknowledgement.

Failed publication remains durable in PostgreSQL with attempt count, last error, and the next retry time. The retry delay uses capped exponential backoff.

Treat delivery as **at least once**. Every event has a stable UUID that becomes the RabbitMQ message ID. Consumers record that UUID in `processed_events` and ignore duplicate deliveries.

Malformed/rejected messages are routed to a durable dead-letter queue.

## Consequences

### Positive

- Committed order state always includes durable event intent.
- Broker outages do not require rejecting otherwise safe orders.
- Publication retry state is queryable and survives process restarts.
- Multiple publisher instances can poll safely.
- Consumer behavior is correct when a message is delivered more than once.
- Poison messages are quarantined instead of requeued forever.

### Negative

- Event publication is eventually consistent.
- The outbox table needs retention/archival in a long-running production system.
- Consumers must implement idempotency.
- Operators must monitor backlog age, retry counts, and DLQ depth.

## Operational boundary

PostgreSQL participates in readiness. RabbitMQ does not: when RabbitMQ is unavailable, the API should continue committing orders and accumulating outbox rows for later publication.

## Rejected alternatives

- Direct publish before DB commit.
- Direct publish after DB commit.
- In-memory retry buffers.
- XA/distributed transactions.
