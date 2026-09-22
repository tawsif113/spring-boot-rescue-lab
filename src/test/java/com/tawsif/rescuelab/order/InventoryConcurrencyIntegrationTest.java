package com.tawsif.rescuelab.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.tawsif.rescuelab.product.CreateProductRequest;
import com.tawsif.rescuelab.product.Product;
import com.tawsif.rescuelab.product.ProductRepository;
import com.tawsif.rescuelab.product.ProductResponse;
import com.tawsif.rescuelab.product.ProductService;
import com.tawsif.rescuelab.shared.InsufficientStockException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class InventoryConcurrencyIntegrationTest {

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        idempotencyRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void atomicReservationAllowsOnlyOneBuyerToClaimTheLastUnit() throws Exception {
        ProductResponse baselineProduct = createProduct("RACE-BASELINE", 1);
        int baselineSuccesses = reproduceLostUpdate(baselineProduct.id());
        int baselineFinalStock = productService.findById(baselineProduct.id()).availableStock();

        ProductResponse protectedProduct = createProduct("RACE-FIXED", 1);
        List<ReservationAttempt> protectedAttempts = runProtectedRace(protectedProduct.id());
        long protectedSuccesses = protectedAttempts.stream().filter(ReservationAttempt::success).count();
        long protectedRejections = protectedAttempts.size() - protectedSuccesses;

        assertThat(baselineSuccesses).isEqualTo(2);
        assertThat(baselineFinalStock).isZero();
        assertThat(protectedSuccesses).isEqualTo(1);
        assertThat(protectedRejections).isEqualTo(1);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productService.findById(protectedProduct.id()).availableStock()).isZero();

        writeEvidence(baselineSuccesses, baselineFinalStock, protectedSuccesses, protectedRejections);
    }

    private int reproduceLostUpdate(UUID productId) throws Exception {
        CyclicBarrier bothTransactionsLoadedStock = new CyclicBarrier(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        List<Future<Boolean>> futures;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(index -> executor.submit(() -> transaction.execute(status -> {
                        Product product = productRepository.findById(productId).orElseThrow();
                        await(bothTransactionsLoadedStock);
                        product.reserve(1);
                        return true;
                    })))
                    .toList();
        }

        int successes = 0;
        for (Future<Boolean> future : futures) {
            if (Boolean.TRUE.equals(future.get(20, TimeUnit.SECONDS))) {
                successes++;
            }
        }
        return successes;
    }

    private List<ReservationAttempt> runProtectedRace(UUID productId) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<ReservationAttempt>> futures;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting for inventory race start");
                        }
                        try {
                            OrderCreationResult result = orderService.create(
                                    UUID.randomUUID(),
                                    "inventory-race-" + index,
                                    new CreateOrderRequest(List.of(new OrderLineRequest(productId, 1)))
                            );
                            return new ReservationAttempt(true, result.order().id());
                        } catch (InsufficientStockException exception) {
                            return new ReservationAttempt(false, null);
                        }
                    }))
                    .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
        }

        return futures.stream().map(this::awaitAttempt).toList();
    }

    private ProductResponse createProduct(String sku, int stock) {
        return productService.create(new CreateProductRequest(
                sku,
                "Inventory race product",
                new BigDecimal("25.00"),
                stock
        ));
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to coordinate the baseline lost update", exception);
        }
    }

    private ReservationAttempt awaitAttempt(Future<ReservationAttempt> future) {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Protected inventory attempt failed", exception);
        }
    }

    private void writeEvidence(
            int baselineSuccesses,
            int baselineFinalStock,
            long protectedSuccesses,
            long protectedRejections
    ) throws IOException {
        Path outputDirectory = Path.of("build", "evidence", "inc-003");
        Files.createDirectories(outputDirectory);
        Files.writeString(
                outputDirectory.resolve("inventory-race.json"),
                """
                {
                  "scenario": "INC-003 inventory race",
                  "initialStockPerProduct": 1,
                  "concurrentBuyers": 2,
                  "baselineSuccessfulReservations": %d,
                  "baselineFinalStock": %d,
                  "protectedSuccessfulReservations": %d,
                  "protectedRejectedReservations": %d,
                  "protectedOrdersCreated": 1,
                  "protectedFinalStock": 0
                }
                """.formatted(
                        baselineSuccesses,
                        baselineFinalStock,
                        protectedSuccesses,
                        protectedRejections
                )
        );
    }

    private record ReservationAttempt(boolean success, UUID orderId) {
    }
}
