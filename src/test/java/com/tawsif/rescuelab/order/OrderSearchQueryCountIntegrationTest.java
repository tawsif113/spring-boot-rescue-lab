package com.tawsif.rescuelab.order;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.tawsif.rescuelab.product.CreateProductRequest;
import com.tawsif.rescuelab.product.ProductRepository;
import com.tawsif.rescuelab.product.ProductResponse;
import com.tawsif.rescuelab.product.ProductService;
import com.tawsif.rescuelab.shared.PageResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class OrderSearchQueryCountIntegrationTest {

    private static final int PAGE_SIZE = 20;

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

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void seedOrders() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        for (int index = 0; index < PAGE_SIZE; index++) {
            ProductResponse product = productService.create(new CreateProductRequest(
                    "QUERY-" + index,
                    "Query Evidence Product " + index,
                    new BigDecimal("19.99"),
                    100
            ));
            UUID customerId = UUID.nameUUIDFromBytes(("customer-" + index).getBytes(UTF_8));
            orderService.create(
                    customerId,
                    new CreateOrderRequest(List.of(new OrderLineRequest(product.id(), 1)))
            );
        }

        entityManager.flush();
        entityManager.clear();
    }

    @AfterEach
    void cleanDatabase() {
        statistics.setStatisticsEnabled(false);
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @SuppressWarnings("deprecation")
    @Test
    void keepsQueryCountConstantInsteadOfGrowingWithThePage() throws IOException {
        statistics.clear();

        Page<PurchaseOrder> legacyPage = orderRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, PAGE_SIZE));
        List<OrderResponse> legacyResponses = legacyPage.getContent().stream()
                .map(OrderResponse::from)
                .toList();
        long baselineStatements = statistics.getPrepareStatementCount();

        entityManager.clear();
        statistics.clear();

        PageResponse<OrderResponse> optimizedPage = orderService.findAll(0, PAGE_SIZE);
        long optimizedStatements = statistics.getPrepareStatementCount();

        assertThat(legacyResponses).hasSize(PAGE_SIZE);
        assertThat(optimizedPage.content()).hasSize(PAGE_SIZE);
        assertThat(baselineStatements).isGreaterThanOrEqualTo(2L + (2L * PAGE_SIZE));
        assertThat(optimizedStatements).isEqualTo(3);
        assertThat(optimizedStatements).isLessThan(baselineStatements);

        writeEvidence(baselineStatements, optimizedStatements);
    }

    private void writeEvidence(long baselineStatements, long optimizedStatements) throws IOException {
        Path outputDirectory = Path.of("build", "evidence", "inc-001");
        Files.createDirectories(outputDirectory);
        Files.writeString(
                outputDirectory.resolve("query-count.json"),
                """
                {
                  "scenario": "INC-001 slow order search",
                  "pageSize": %d,
                  "baselinePreparedStatements": %d,
                  "optimizedPreparedStatements": %d,
                  "assertion": "optimized query count remains constant at three"
                }
                """.formatted(PAGE_SIZE, baselineStatements, optimizedStatements)
        );
    }
}
