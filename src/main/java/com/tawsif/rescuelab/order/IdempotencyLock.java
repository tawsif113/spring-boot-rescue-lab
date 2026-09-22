package com.tawsif.rescuelab.order;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class IdempotencyLock {

    private final EntityManager entityManager;

    IdempotencyLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * PostgreSQL transaction-scoped advisory locks serialize only requests
     * that share the same customer and idempotency key. The lock is released
     * automatically on commit or rollback.
     */
    void acquire(UUID customerId, String idempotencyKey) {
        String lockKey = customerId + ":" + idempotencyKey;
        entityManager.createNativeQuery(
                        "select pg_advisory_xact_lock(hashtextextended(cast(:lockKey as text), 0))"
                )
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }
}
