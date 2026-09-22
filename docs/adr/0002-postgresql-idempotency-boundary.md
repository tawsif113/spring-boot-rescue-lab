# ADR 0002: Keep order idempotency in the PostgreSQL transaction

- **Status:** Accepted
- **Date:** 2026-09-22
- **Incident:** [INC-002](../../incidents/INC-002-duplicate-orders.md)

## Context

Order creation and its idempotency decision must succeed or fail together. The system already depends on PostgreSQL and also runs Redis, but coordinating a Redis claim with a relational commit would introduce a crash window or require a more complex recovery protocol.

## Decision

Store customer-scoped idempotency records in PostgreSQL. Before lookup or creation, acquire a transaction-scoped advisory lock derived from the customer and key. Bind each record to a SHA-256 request fingerprint and the resulting order.

## Consequences

### Positive

- The order, inventory change, and idempotency record share one atomic commit.
- All application replicas observe the same result.
- Rollback automatically releases the lock and leaves no poisoned key.
- A replay can reconstruct the original API response from domain data.

### Negative

- The implementation is intentionally PostgreSQL-specific.
- Same-key requests hold a database connection while waiting.
- Advisory-lock observability must be included in production monitoring.
- The response must remain reconstructable, or a response snapshot must be added later.

## Guardrails

- Maximum key length: 128 characters.
- Default retention: 24 hours.
- Unique constraint on `(customer_id, idempotency_key)`.
- Indexed expiry column for cleanup.
- A changed request fingerprint for an active key is always a conflict.
