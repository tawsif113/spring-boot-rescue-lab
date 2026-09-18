package com.tawsif.rescuelab.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        BigDecimal unitPrice,
        int availableStock,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getUnitPrice(),
                product.getAvailableStock(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}

