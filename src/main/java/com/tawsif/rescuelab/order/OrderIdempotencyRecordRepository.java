package com.tawsif.rescuelab.order;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderIdempotencyRecordRepository extends JpaRepository<OrderIdempotencyRecord, UUID> {

    @EntityGraph(attributePaths = {"order", "order.items", "order.items.product"})
    Optional<OrderIdempotencyRecord> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    @Modifying
    @Query("delete from OrderIdempotencyRecord record where record.expiresAt <= :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
