package com.tawsif.rescuelab.product;

import com.tawsif.rescuelab.shared.ResourceNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaProductCatalogSource implements ProductCatalogSource {

    private final ProductRepository productRepository;
    private final Counter databaseLoads;
    private final Duration simulatedDbLatency;

    public JpaProductCatalogSource(
            ProductRepository productRepository,
            MeterRegistry meterRegistry,
            @Value("${rescue-lab.catalog-cache.simulated-db-latency:PT0S}") Duration simulatedDbLatency
    ) {
        this.productRepository = productRepository;
        this.databaseLoads = Counter.builder("rescue.catalog.db.loads")
                .description("Catalog product loads that reached PostgreSQL")
                .register(meterRegistry);
        this.simulatedDbLatency = simulatedDbLatency;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse load(UUID productId) {
        databaseLoads.increment();
        simulateLatency();
        return ProductResponse.from(productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId)));
    }

    private void simulateLatency() {
        if (simulatedDbLatency.isZero() || simulatedDbLatency.isNegative()) {
            return;
        }
        try {
            Thread.sleep(simulatedDbLatency.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during catalog latency simulation", exception);
        }
    }
}
