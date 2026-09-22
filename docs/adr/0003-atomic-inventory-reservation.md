# ADR 0003: Reserve inventory with an atomic conditional update

- **Status:** Accepted
- **Date:** 2026-09-22
- **Incident:** [INC-003](../../incidents/INC-003-inventory-race.md)

## Context

The read-check-write entity method allows two transactions to validate the same stock snapshot. A non-negative check constraint cannot detect the resulting lost update because both writers may store the same valid value.

## Decision

Use one conditional update to decrement stock only when sufficient units remain. Interpret the affected-row count as the concurrency outcome. Execute it inside the existing order transaction with mandatory propagation.

## Consequences

### Positive

- The database evaluates the invariant against the row version it locks.
- There is no application-level retry for the losing buyer.
- The update is one round trip on the success path.
- Reservation rolls back with the order.

### Negative

- The operation bypasses normal entity dirty checking.
- A managed `Product` instance can contain a stale stock field after a second bulk update in the same persistence context; order mapping must not rely on that field.
- More complicated inventory rules may eventually need a reservation ledger rather than a counter.

## Alternatives

Optimistic versioning is the preferred fallback when an aggregate has several concurrent invariants and retrying the whole command is safe. Pessimistic locking is appropriate when several decisions must observe one stable row state. Neither extra mechanism is required for this single numeric condition.
