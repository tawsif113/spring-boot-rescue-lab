# Spring Boot Rescue Lab — portfolio case study

## The problem

Many backend portfolios show only greenfield CRUD. They do not show the work that dominates mature systems: diagnosing slow queries, making retries safe, reasoning about races, repairing authorization, and surviving partial infrastructure failure.

Spring Boot Rescue Lab is an order-management API intentionally built with those failure modes and then repaired one incident at a time.

## What I implemented

| Incident | Production failure | Repair |
|---|---|---|
| INC-001 | N+1 order search | Pagination-safe two-phase fetch + PostgreSQL index |
| INC-002 | Duplicate orders after retries | Persistent idempotency, fingerprints, advisory locking |
| INC-003 | Inventory overselling | Atomic conditional stock reservation |
| INC-004 | Broken object authorization | Principal-derived identity, ownership-scoped queries, role boundaries |
| INC-005 | Lost integration events | Transactional outbox, confirms, retries, DLQ, consumer dedup |

## How the work is proved

The repository treats every remediation as an engineering case study rather than a code diff.

- deterministic reproduction;
- root-cause explanation;
- explicit alternative analysis;
- Flyway-managed schema change where required;
- unit or PostgreSQL Testcontainers regression tests;
- CI-generated evidence artifacts;
- architecture decision record;
- operational consequences.

Examples include an exact SQL-query-count regression, an eight-way concurrent idempotency test, a coordinated two-buyer inventory race, a real Spring Security authorization matrix, and an outbox failure-injection test.

## Reliability design

Order creation commits inventory, order state, idempotency state, and the integration-event intent in one PostgreSQL transaction. RabbitMQ publication happens asynchronously from the outbox and is acknowledged with publisher confirms.

This is intentionally **at least once**. Consumer deduplication makes duplicate delivery harmless. RabbitMQ outages create a visible backlog rather than turning a safe order commit into an unavailable API.

## Security design

The lab's Basic Auth identities are local-only test fixtures. The meaningful design choice is that customer identity comes from a trusted principal and ownership is enforced in the database query. A production OIDC/JWT provider can replace the demo authentication mechanism without weakening that boundary.

## Operability

The final release adds:

- ECS structured JSON logging;
- `X-Correlation-Id` propagation into MDC;
- liveness/readiness health groups;
- Prometheus/Actuator metrics;
- a Grafana dashboard example;
- OpenAPI/Swagger UI;
- a Postman collection;
- Docker Compose infrastructure;
- a production deployment checklist.

## Stack

Java 25, Spring Boot 4.1.1, Spring MVC, Spring Security, JPA/Hibernate, PostgreSQL, Flyway, RabbitMQ, Redis, Micrometer/Prometheus, Gradle, Docker Compose, JUnit 5, Testcontainers, GitHub Actions, and springdoc-openapi.

## What this demonstrates

The project is designed to demonstrate backend engineering judgment: establish evidence before optimizing, define consistency boundaries, make concurrency behavior deterministic, enforce authorization below the controller, model message delivery honestly, and leave failure states observable and recoverable.
