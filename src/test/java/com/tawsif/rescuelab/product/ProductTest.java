package com.tawsif.rescuelab.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tawsif.rescuelab.shared.InsufficientStockException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void reservesAvailableStock() {
        Product product = new Product("SKU-1", "Mechanical Keyboard", new BigDecimal("80.00"), 10);

        product.reserve(3);

        assertThat(product.getAvailableStock()).isEqualTo(7);
    }

    @Test
    void rejectsReservationWhenStockIsInsufficient() {
        Product product = new Product("SKU-1", "Mechanical Keyboard", new BigDecimal("80.00"), 2);

        assertThatThrownBy(() -> product.reserve(3))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("3 were requested");
    }
}

