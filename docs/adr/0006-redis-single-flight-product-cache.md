# ADR 0006: Redis distributed single-flight for hot catalog keys

- **Status:** Accepted
- **Date:** 2026-09-24
- **Incident:** [INC-006](../../incidents/INC-006-cache-stampede.md)

## Context

A plain cache-aside product endpoint can overload PostgreSQL when a popular cache key expires. Every concurrent request observes the same miss and independently performs the same database load.

The application can run multiple instances, so an in-process mutex does not provide sufficient coordination.

Product stock is mutable and is updated inside the order transaction, which also makes cache invalidation ordering important.

## Decision

Use Redis as a read-through cache and a distributed rebuild coordinator.

For each product key:

1. read Redis;
2. on miss, attempt a short-lived `SET NX` rebuild lock with a unique token;
3. the winner double-checks Redis, loads PostgreSQL, and caches the value;
4. contenders wait for a bounded interval for the rebuilt value;
5. release the lock with token-checked Lua compare-and-delete;
6. add positive TTL jitter to cache entries;
7. invalidate cached stock through an `afterCommit` transaction synchronization;
8. fail open to PostgreSQL when Redis operations fail.

## Consequences

### Positive

- Concurrent cold misses collapse to one normal database load.
- Coordination works across application instances.
- TTL jitter reduces synchronized key expiry.
- Rolled-back inventory changes do not invalidate a still-correct cache value.
- Redis outages degrade performance instead of making the catalog unavailable.
- Metrics expose cache efficiency, contention, wait timeouts, DB loads, and Redis failures.

### Negative

- Redis becomes part of the performance path even though it is not a correctness dependency.
- Cold misses can wait briefly behind a rebuild.
- Bounded-wait fallback can still create duplicate database loads during unusually slow rebuilds.
- Cache payload/schema evolution requires key versioning or compatible decoding.
- Operators must monitor Redis latency, cache hit rate, contention, and fallback load.

## Rejected alternatives

- JVM-local locking: cannot coordinate multiple service instances.
- Fixed TTL without jitter: can synchronize expiry spikes.
- Eviction before transaction commit: can repopulate stale data during an uncommitted write.
- Make Redis a readiness dependency: unnecessary because PostgreSQL remains authoritative.
