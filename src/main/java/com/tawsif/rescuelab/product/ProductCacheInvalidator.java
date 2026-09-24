package com.tawsif.rescuelab.product;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ProductCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheInvalidator.class);

    private final StringRedisTemplate redisTemplate;
    private final Counter redisFailures;

    public ProductCacheInvalidator(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.redisFailures = Counter.builder("rescue.catalog.cache.redis.failures")
                .description("Catalog cache operations that failed because Redis was unavailable")
                .register(meterRegistry);
    }

    public void evictAfterCommit(UUID productId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictNow(productId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictNow(productId);
            }
        });
    }

    void evictNow(UUID productId) {
        try {
            redisTemplate.delete(ProductCacheKeys.data(productId));
        } catch (DataAccessException exception) {
            redisFailures.increment();
            log.atWarn()
                    .addKeyValue("productId", productId)
                    .setCause(exception)
                    .log("Redis cache eviction failed; database remains authoritative");
        }
    }
}
