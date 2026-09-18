package com.tawsif.rescuelab.shared;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(UUID productId, int available, int requested) {
        super("Product %s has %d units available but %d were requested"
                .formatted(productId, available, requested));
    }
}

