package com.tawsif.rescuelab.order;

public record OrderCreationResult(
        OrderResponse order,
        boolean replayed
) {
}
