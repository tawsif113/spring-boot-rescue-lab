package com.tawsif.rescuelab.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OrderLineRequest(
        @NotNull UUID productId,
        @Min(1) int quantity
) {
}

