package com.tawsif.rescuelab;

import static org.assertj.core.api.Assertions.assertThat;

import com.tawsif.rescuelab.order.CreateOrderRequest;
import com.tawsif.rescuelab.order.OrderLineRequest;
import com.tawsif.rescuelab.order.OrderResponse;
import com.tawsif.rescuelab.order.OrderService;
import com.tawsif.rescuelab.order.PurchaseOrderRepository;
import com.tawsif.rescuelab.product.CreateProductRequest;
import com.tawsif.rescuelab.product.ProductRepository;
import com.tawsif.rescuelab.product.ProductResponse;
import com.tawsif.rescuelab.product.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrderWorkflowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PurchaseOrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void cleanDatabase() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void persistsAnOrderAndItsInventoryChange() {
        ProductResponse product = productService.create(
                new CreateProductRequest("KEYBOARD-01", "Mechanical Keyboard", new BigDecimal("80.00"), 10)
        );

        OrderResponse order = orderService.create(
                UUID.randomUUID(),
                new CreateOrderRequest(List.of(new OrderLineRequest(product.id(), 2)))
        );

        assertThat(order.id()).isNotNull();
        assertThat(order.totalAmount()).isEqualByComparingTo("160.00");
        assertThat(productService.findById(product.id()).availableStock()).isEqualTo(8);
    }
}

