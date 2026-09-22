package com.tawsif.rescuelab.order;

import static com.tawsif.rescuelab.security.DemoIdentities.ALICE_CUSTOMER_ID;
import static com.tawsif.rescuelab.security.DemoIdentities.BOB_CUSTOMER_ID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tawsif.rescuelab.product.CreateProductRequest;
import com.tawsif.rescuelab.product.ProductRepository;
import com.tawsif.rescuelab.product.ProductResponse;
import com.tawsif.rescuelab.product.ProductService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrderAuthorizationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("management.health.rabbit.enabled", () -> "false");
        registry.add("management.health.redis.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseOrderRepository orderRepository;

    @Autowired
    private OrderIdempotencyRecordRepository idempotencyRepository;

    private ProductResponse product;
    private OrderResponse aliceOrder;
    private OrderResponse bobOrder;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        product = productService.create(new CreateProductRequest(
                "SECURITY-01",
                "Ownership test product",
                new BigDecimal("15.00"),
                20
        ));
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderLineRequest(product.id(), 1)));
        aliceOrder = orderService.create(ALICE_CUSTOMER_ID, "alice-seed-order", request).order();
        bobOrder = orderService.create(BOB_CUSTOMER_ID, "bob-seed-order", request).order();
    }

    @AfterEach
    void cleanDatabase() {
        idempotencyRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void enforcesAuthenticationOwnershipAndAdministrativeRoles() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", aliceOrder.id()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/orders/{id}", aliceOrder.id())
                        .with(httpBasic("bob", "bob-change-me")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/orders/{id}", aliceOrder.id())
                        .with(httpBasic("alice", "alice-change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(ALICE_CUSTOMER_ID.toString()));

        mockMvc.perform(get("/api/orders/{id}", bobOrder.id())
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/products")
                        .with(httpBasic("alice", "alice-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("CUSTOMER-FORBIDDEN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/products")
                        .with(httpBasic("admin", "admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("ADMIN-ALLOWED")))
                .andExpect(status().isCreated());

        writeEvidence();
    }

    @Test
    void derivesOrderOwnershipFromThePrincipalAndIgnoresSpoofedHeaders() throws Exception {
        String requestBody = """
                {
                  "items": [
                    {"productId": "%s", "quantity": 1}
                  ]
                }
                """.formatted(product.id());

        mockMvc.perform(post("/api/orders")
                        .with(httpBasic("alice", "alice-change-me"))
                        .header("X-Customer-Id", BOB_CUSTOMER_ID)
                        .header("Idempotency-Key", "spoof-attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(ALICE_CUSTOMER_ID.toString()));
    }

    @Test
    void limitsCustomerListsWhileAllowingAdministrativeVisibility() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(httpBasic("alice", "alice-change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].customerId").value(ALICE_CUSTOMER_ID.toString()));

        mockMvc.perform(get("/api/orders")
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void keepsHealthPublicButProtectsPrometheusMetrics() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .with(httpBasic("alice", "alice-change-me")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/prometheus")
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk());
    }

    private String productJson(String sku) {
        return """
                {
                  "sku": "%s",
                  "name": "Role-protected product",
                  "unitPrice": 10.00,
                  "availableStock": 5
                }
                """.formatted(sku);
    }

    private void writeEvidence() throws IOException {
        Path outputDirectory = Path.of("build", "evidence", "inc-004");
        Files.createDirectories(outputDirectory);
        Files.writeString(
                outputDirectory.resolve("authorization-matrix.json"),
                """
                {
                  "scenario": "INC-004 broken authorization",
                  "unauthenticatedOrderRead": 401,
                  "crossCustomerOrderRead": 404,
                  "ownerOrderRead": 200,
                  "adminOrderRead": 200,
                  "customerAdminRoute": 403,
                  "adminAdminRoute": 201,
                  "identitySource": "authenticated principal"
                }
                """
        );
    }
}
