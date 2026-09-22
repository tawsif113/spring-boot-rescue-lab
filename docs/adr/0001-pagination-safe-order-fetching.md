# ADR 0001: Use two-phase fetching for paginated order aggregates

- **Status:** Accepted
- **Date:** 2026-09-22
- **Incident:** [INC-001](../../incidents/INC-001-slow-order-search.md)

## Context

The API returns orders together with line items and product display fields. Fetching lazy relationships while mapping a page creates an N+1 pattern. Applying pagination directly to a collection fetch join can paginate duplicated joined rows rather than root orders, or force in-memory pagination.

## Decision

Use two database phases inside one read-only transaction:

1. Select a deterministic page of root order IDs and total count.
2. Fetch the complete graph for those IDs with `@EntityGraph`.
3. Reorder the fetched entities according to the ID page before DTO mapping.

## Consequences

### Positive

- Statement count is bounded at three for every non-empty page.
- Pagination applies to root orders, not joined rows.
- All data required by the DTO is loaded inside the service transaction.
- The query shape is explicit and protected by an integration test.

### Negative

- The service performs an in-memory map and reorder operation.
- Large page sizes increase the size of the `IN` predicate; the API therefore caps pages at 100.
- Offset pagination can still degrade for very deep pages; cursor pagination remains a future option.

## Alternatives

- Eager relationships were rejected because they affect unrelated reads.
- A pageable collection fetch join was rejected because of pagination correctness.
- Batch fetching was rejected because it makes query count depend on tuning rather than removing the underlying access pattern.
- A DTO projection remains viable if the endpoint evolves into an independent read model.
