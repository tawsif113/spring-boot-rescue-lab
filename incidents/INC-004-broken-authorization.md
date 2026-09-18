# INC-004 — Broken object-level authorization

**Status:** Planned

## Scenario

The fragile baseline trusts an `X-Customer-Id` header and allows unrestricted access to order identifiers. A caller can request an order belonging to another customer.

The investigation will add authenticated identities, administrative roles, ownership-aware queries, and negative security tests.

