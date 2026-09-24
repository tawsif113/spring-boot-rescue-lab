package com.tawsif.rescuelab.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "rescue-lab.catalog-cache.enabled=true",
        "rescue-lab.catalog-cache.simulated-db-latency=PT0.15S",
        "rescue-lab.catalog-cache.ttl=PT5M",
        "rescue-lab.catalog-cache.ttl-jitter=PT30S",
        "rescue-lab.catalog-cache.lock-ttl=PT3S",
        "rescue-lab.catalog-cache.wait-timeout=PT2S",
        "rescue-lab.catalog-cache.poll-interval=PT0.025S"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProductCatalogCacheIntegrationTest {

    private static final int CONCURRENT_READERS = 24;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductCatalogSource productSource;

    @Autowired
    private ProductCatalogService productCatalogService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ProductResponse product;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        product = productService.create(new CreateProductRequest(
                "HOT-KEY-01",
                "Launch-day keyboard",
                new BigDecimal("80.00"),
                10
        ));
        redisTemplate.delete(ProductCacheKeys.data(product.id()));
        redisTemplate.delete(ProductCacheKeys.lock(product.id()));
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(ProductCacheKeys.data(product.id()));
        redisTemplate.delete(ProductCacheKeys.lock(product.id()));
        productRepository.deleteAll();
    }

    @Test
    void exposesTheCatalogAsAReadOnlyPublicEndpoint() throws Exception {
        mockMvc.perform(get("/api/catalog/products/{id}", product.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.id().toString()))
                .andExpect(jsonPath("$.availableStock").value(10));
    }

    @Test
    void collapsesConcurrentColdMissesFromTwentyFourDatabaseLoadsToOne() throws Exception {
        double beforeBaselineLoads = databaseLoads();
        List<ProductResponse> baseline = runConcurrently(
                CONCURRENT_READERS,
                () -> productSource.load(product.id())
        );
        double baselineLoads = databaseLoads() - beforeBaselineLoads;

        assertThat(baseline).hasSize(CONCURRENT_READERS);
        assertThat(baseline).allMatch(response -> response.id().equals(product.id()));
        assertThat(baselineLoads).isEqualTo(CONCURRENT_READERS);

        redisTemplate.delete(ProductCacheKeys.data(product.id()));
        redisTemplate.delete(ProductCacheKeys.lock(product.id()));

        double beforeRescuedLoads = databaseLoads();
        double beforeContention = meterRegistry
                .get("rescue.catalog.cache.lock.contention")
                .counter()
                .count();

        List<ProductResponse> rescued = runConcurrently(
                CONCURRENT_READERS,
                () -> productCatalogService.findById(product.id())
        );

        double rescuedLoads = databaseLoads() - beforeRescuedLoads;
        double contention = meterRegistry
                .get("rescue.catalog.cache.lock.contention")
                .counter()
                .count() - beforeContention;

        assertThat(rescued).hasSize(CONCURRENT_READERS);
        assertThat(rescued).allMatch(response -> response.id().equals(product.id()));
        assertThat(rescuedLoads).isEqualTo(1.0);
        assertThat(contention).isGreaterThan(0.0);

        writeEvidence((long) baselineLoads, (long) rescuedLoads, (long) contention);
    }

    @Test
    void invalidatesCachedStockOnlyAfterTheReservationTransactionCommits() {
        ProductResponse cached = productCatalogService.findById(product.id());
        assertThat(cached.availableStock()).isEqualTo(10);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> productService.reserveForOrder(product.id(), 3));

        ProductResponse refreshed = productCatalogService.findById(product.id());

        assertThat(refreshed.availableStock()).isEqualTo(7);
    }

    private double databaseLoads() {
        return meterRegistry.get("rescue.catalog.db.loads").counter().count();
    }

    private <T> List<T> runConcurrently(int workers, Callable<T> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent start");
                    }
                    return task.call();
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private void writeEvidence(long baselineLoads, long rescuedLoads, long contention) throws IOException {
        Path outputDirectory = Path.of("build", "evidence", "inc-006");
        Files.createDirectories(outputDirectory);
        Files.writeString(
                outputDirectory.resolve("cache-stampede.json"),
                """
                {
                  "scenario": "INC-006 hot product cache stampede",
                  "concurrentReaders": %d,
                  "baselineDatabaseLoads": %d,
                  "rescuedDatabaseLoads": %d,
                  "databaseLoadReductionFactor": %.1f,
                  "lockContentionObservations": %d,
                  "cacheStrategy": "redis distributed single-flight with ttl jitter",
                  "redisFailureMode": "fail-open to PostgreSQL"
                }
                """.formatted(
                        CONCURRENT_READERS,
                        baselineLoads,
                        rescuedLoads,
                        (double) baselineLoads / rescuedLoads,
                        contention
                )
        );
    }
}
