package com.tawsif.rescuelab.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 160) String name,
        @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice,
        @Min(0) int availableStock
) {
}

