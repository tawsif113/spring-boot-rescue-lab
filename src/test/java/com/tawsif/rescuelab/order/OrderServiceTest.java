package com.tawsif.rescuelab.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tawsif.rescuelab.product.Product;
import com.tawsif.rescuelab.product.ProductService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private PurchaseOrderRepository orderRepository;

    @Mock
    private ProductService productService;

    @Mock
    private OrderIdempotencyRecordRepository idempotencyRepository;

    @Mock
    private IdempotencyLock idempotencyLock;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);
        orderService = new OrderService(
                orderRepository,
                productService,
                idempotencyRepository,
                idempotencyLock,
                clock,
                Duration.ofHours(24)
        );
    }

    @Test
    void createsOrderAndReservesStock() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product product = new Product("SKU-1", "Mechanical Keyboard", new BigDecimal("80.00"), 10);
        when(productService.reserveForOrder(productId, 2)).thenReturn(product);
        when(orderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderCreationResult result = orderService.create(
                customerId,
                "checkout-attempt-1",
                new CreateOrderRequest(List.of(new OrderLineRequest(productId, 2)))
        );
        OrderResponse response = result.order();

        assertThat(result.replayed()).isFalse();
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.totalAmount()).isEqualByComparingTo("160.00");
        assertThat(response.items()).hasSize(1);
        verify(idempotencyLock).acquire(customerId, "checkout-attempt-1");
        verify(idempotencyRepository).save(any(OrderIdempotencyRecord.class));
        verify(productService).reserveForOrder(productId, 2);
        verify(orderRepository).save(any(PurchaseOrder.class));
    }
}
