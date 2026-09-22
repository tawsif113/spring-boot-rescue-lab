package com.tawsif.rescuelab.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "order_idempotency_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_idempotency_customer_key",
                columnNames = {"customer_id", "idempotency_key"}
        )
)
public class OrderIdempotencyRecord {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder order;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected OrderIdempotencyRecord() {
    }

    OrderIdempotencyRecord(
            UUID customerId,
            String idempotencyKey,
            String requestFingerprint,
            PurchaseOrder order,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.order = order;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    boolean hasFingerprint(String fingerprint) {
        return requestFingerprint.equals(fingerprint);
    }

    boolean isExpiredAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }

    PurchaseOrder getOrder() {
        return order;
    }
}
