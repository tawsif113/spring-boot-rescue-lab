package com.tawsif.rescuelab.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tawsif.rescuelab.product.Product;
import com.tawsif.rescuelab.product.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
    private ProductRepository productRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productRepository);
    }

    @Test
    void createsOrderAndReservesStock() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product product = new Product("SKU-1", "Mechanical Keyboard", new BigDecimal("80.00"), 10);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.create(
                customerId,
                new CreateOrderRequest(List.of(new OrderLineRequest(productId, 2)))
        );

        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.totalAmount()).isEqualByComparingTo("160.00");
        assertThat(response.items()).hasSize(1);
        assertThat(product.getAvailableStock()).isEqualTo(8);
        verify(orderRepository).save(any(PurchaseOrder.class));
    }
}

