package com.tawsif.rescuelab.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdempotencyFingerprintTest {

    @Test
    void producesTheSameFingerprintForTheSameLogicalRequest() {
        UUID productId = UUID.randomUUID();
        CreateOrderRequest first = new CreateOrderRequest(List.of(new OrderLineRequest(productId, 2)));
        CreateOrderRequest second = new CreateOrderRequest(List.of(new OrderLineRequest(productId, 2)));

        assertThat(IdempotencyFingerprint.of(first)).isEqualTo(IdempotencyFingerprint.of(second));
    }

    @Test
    void changesTheFingerprintWhenThePayloadChanges() {
        UUID productId = UUID.randomUUID();
        CreateOrderRequest first = new CreateOrderRequest(List.of(new OrderLineRequest(productId, 1)));
        CreateOrderRequest second = new CreateOrderRequest(List.of(new OrderLineRequest(productId, 2)));

        assertThat(IdempotencyFingerprint.of(first)).isNotEqualTo(IdempotencyFingerprint.of(second));
    }
}
