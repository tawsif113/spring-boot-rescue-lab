package com.tawsif.rescuelab.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tawsif.rescuelab.product.CreateProductRequest;
import com.tawsif.rescuelab.product.ProductRepository;
import com.tawsif.rescuelab.product.ProductResponse;
import com.tawsif.rescuelab.product.ProductService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class OrderIdempotencyIntegrationTest {

    private static final int CONCURRENT_RETRIES = 8;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private PurchaseOrderRepository orderRepository;

    @Autowired
    private OrderIdempotencyRecordRepository idempotencyRepository;

    @Autowired
    private ProductRepository productRepository;

    private ProductResponse product;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        product = productService.create(new CreateProductRequest(
                "IDEMPOTENCY-01",
                "Retry-safe keyboard",
                new BigDecimal("80.00"),
                50
        ));
        customerId = UUID.randomUUID();
    }

    @AfterEach
    void cleanDatabase() {
        idempotencyRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void eightConcurrentRetriesCreateOneOrderAndReserveStockOnce() throws Exception {
        CountDownLatch ready = new CountDownLatch(CONCURRENT_RETRIES);
        CountDownLatch start = new CountDownLatch(1);
        CreateOrderRequest request = requestWithQuantity(1);

        List<Future<OrderCreationResult>> futures;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = java.util.stream.IntStream.range(0, CONCURRENT_RETRIES)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting for concurrent start");
                        }
                        return orderService.create(customerId, "payment-timeout-retry", request);
                    }))
                    .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
        }

        List<OrderCreationResult> results = futures.stream()
                .map(this::awaitResult)
                .toList();

        assertThat(results).extracting(result -> result.order().id()).containsOnly(results.getFirst().order().id());
        assertThat(results).filteredOn(OrderCreationResult::replayed).hasSize(CONCURRENT_RETRIES - 1);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(idempotencyRepository.count()).isEqualTo(1);
        assertThat(productService.findById(product.id()).availableStock()).isEqualTo(49);

        writeEvidence(results);
    }

    @Test
    void rejectsReuseOfAKeyWithADifferentPayload() {
        orderService.create(customerId, "checkout-42", requestWithQuantity(1));

        assertThatThrownBy(() -> orderService.create(customerId, "checkout-42", requestWithQuantity(2)))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("different request payload");

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productService.findById(product.id()).availableStock()).isEqualTo(49);
    }

    private CreateOrderRequest requestWithQuantity(int quantity) {
        return new CreateOrderRequest(List.of(new OrderLineRequest(product.id(), quantity)));
    }

    private OrderCreationResult awaitResult(Future<OrderCreationResult> future) {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent idempotency request failed", exception);
        }
    }

    private void writeEvidence(List<OrderCreationResult> results) throws IOException {
        long replayCount = results.stream().filter(OrderCreationResult::replayed).count();
        Path outputDirectory = Path.of("build", "evidence", "inc-002");
        Files.createDirectories(outputDirectory);
        Files.writeString(
                outputDirectory.resolve("concurrent-retries.json"),
                """
                {
                  "scenario": "INC-002 duplicate orders",
                  "concurrentRequests": %d,
                  "ordersCreated": 1,
                  "stockReservations": 1,
                  "replayedResponses": %d,
                  "uniqueOrderIds": 1
                }
                """.formatted(CONCURRENT_RETRIES, replayCount)
        );
    }
}
