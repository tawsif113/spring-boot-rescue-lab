package com.tawsif.rescuelab.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tawsif.rescuelab.order.CreateOrderRequest;
import com.tawsif.rescuelab.order.OrderIdempotencyRecordRepository;
import com.tawsif.rescuelab.order.OrderLineRequest;
import com.tawsif.rescuelab.order.OrderResponse;
import com.tawsif.rescuelab.order.OrderService;
import com.tawsif.rescuelab.order.PurchaseOrderRepository;
import com.tawsif.rescuelab.product.CreateProductRequest;
import com.tawsif.rescuelab.product.ProductRepository;
import com.tawsif.rescuelab.product.ProductResponse;
import com.tawsif.rescuelab.product.ProductService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
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
class OutboxReliabilityIntegrationTest {

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
    private OutboxEventRepository outboxRepository;

    @Autowired
    private ProcessedEventStore processedEventStore;

    @Autowired
    private OrderIdempotencyRecordRepository idempotencyRepository;

    @Autowired
    private PurchaseOrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    private TransactionTemplate transactionTemplate;
    private ProductResponse product;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanDatabase();
        product = productService.create(new CreateProductRequest(
                "OUTBOX-01",
                "Reliable event keyboard",
                new BigDecimal("80.00"),
                10
        ));
        customerId = UUID.randomUUID();
    }

    @AfterEach
    void cleanDatabase() {
        processedEventStore.deleteAll();
        outboxRepository.deleteAll();
        idempotencyRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void provesAtomicOutboxRetryAndDuplicateDeliverySafety() throws IOException {
        OrderResponse committedOrder = createOrder("outbox-committed");

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
        OutboxEvent committedEvent = outboxRepository.findAll().getFirst();
        assertThat(committedEvent.getAggregateId()).isEqualTo(committedOrder.id());
        assertThat(committedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);

        ProductResponse rollbackProduct = productService.create(new CreateProductRequest(
                "OUTBOX-ROLLBACK",
                "Rollback proof product",
                new BigDecimal("25.00"),
                5
        ));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            orderService.create(
                    customerId,
                    "outbox-rollback",
                    new CreateOrderRequest(List.of(new OrderLineRequest(rollbackProduct.id(), 1)))
            );
            throw new IllegalStateException("simulated process failure before commit");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(productService.findById(rollbackProduct.id()).availableStock()).isEqualTo(5);

        OutboxPublisherProperties properties = new OutboxPublisherProperties();
        properties.setBatchSize(10);
        properties.setRetryBase(Duration.ofSeconds(1));
        properties.setRetryMax(Duration.ofSeconds(30));

        EventTransport failingTransport = event -> {
            throw new IllegalStateException("simulated RabbitMQ outage");
        };
        OutboxPublisher publisher = new OutboxPublisher(
                outboxRepository,
                failingTransport,
                clock,
                properties
        );

        OutboxPublisher.PublishBatchResult result = transactionTemplate.execute(
                status -> publisher.publishDueEvents()
        );

        assertThat(result).isNotNull();
        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.published()).isZero();
        assertThat(result.failed()).isEqualTo(1);

        OutboxEvent retryableEvent = outboxRepository.findById(committedEvent.getId()).orElseThrow();
        assertThat(retryableEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(retryableEvent.getAttempts()).isEqualTo(1);
        assertThat(retryableEvent.getLastError()).contains("simulated RabbitMQ outage");
        assertThat(retryableEvent.getNextAttemptAt()).isAfter(retryableEvent.getCreatedAt());

        UUID deliveredEventId = UUID.randomUUID();
        assertThat(processedEventStore.recordIfFirst(deliveredEventId, "ORDER_CREATED")).isTrue();
        assertThat(processedEventStore.recordIfFirst(deliveredEventId, "ORDER_CREATED")).isFalse();
        assertThat(processedEventStore.count()).isEqualTo(1);

        writeEvidence(committedOrder.id(), committedEvent.getId(), retryableEvent);
    }

    private OrderResponse createOrder(String idempotencyKey) {
        return orderService.create(
                customerId,
                idempotencyKey,
                new CreateOrderRequest(List.of(new OrderLineRequest(product.id(), 1)))
        ).order();
    }

    private void writeEvidence(UUID orderId, UUID eventId, OutboxEvent retryableEvent) throws IOException {
        Path outputDirectory = Path.of("build", "evidence", "inc-005");
        Files.createDirectories(outputDirectory);
        Files.writeString(
                outputDirectory.resolve("outbox-reliability.json"),
                """
                {
                  "scenario": "INC-005 lost events",
                  "committedOrderId": "%s",
                  "committedOutboxEventId": "%s",
                  "ordersAfterRollbackInjection": 1,
                  "outboxEventsAfterRollbackInjection": 1,
                  "publisherFailureStatus": "%s",
                  "publisherFailureAttempts": %d,
                  "duplicateDeliveries": 2,
                  "consumerReceipts": 1
                }
                """.formatted(
                        orderId,
                        eventId,
                        retryableEvent.getStatus(),
                        retryableEvent.getAttempts()
                )
        );
    }
}
