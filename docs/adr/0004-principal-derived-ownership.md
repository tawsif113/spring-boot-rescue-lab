# ADR 0004: Derive ownership from the authenticated principal

- **Status:** Accepted
- **Date:** 2026-09-22
- **Incident:** [INC-004](../../incidents/INC-004-broken-authorization.md)

## Context

The API previously accepted `X-Customer-Id` as the customer identity. Any caller could forge it, and order reads did not filter by ownership.

## Decision

Use Spring Security's `RescueUserPrincipal` as the only customer identity source. Apply role checks in the filter chain and ownership predicates in repository queries. Return 404 for both missing and cross-customer order IDs.

## Consequences

### Positive

- Callers cannot select their own authorization identity.
- Unauthorized entities are not loaded into application memory.
- Collection and object reads use the same ownership boundary.
- An OIDC resource server can replace the demo provider without changing domain-service method contracts.

### Negative

- The local demo requires explicit credentials.
- Administrators and customers follow different read paths.
- In-memory demo users are not a production identity store.

## Production evolution

Replace HTTP Basic and `InMemoryUserDetailsManager` with OIDC/OAuth2 JWT validation. Map the stable subject and roles into a principal containing the internal customer ID. Keep repository ownership predicates and deny-by-default route rules unchanged.
