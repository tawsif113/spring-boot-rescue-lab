package com.tawsif.rescuelab.product;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class ProductCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogService.class);

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """,
            Long.class
    );

    private final ProductCatalogSource productSource;
    private final StringRedisTemplate redisTemplate;
    private final ProductCatalogCacheProperties properties;
    private final Clock clock;

    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter lockContention;
    private final Counter waitTimeouts;
    private final Counter redisFailures;

    public ProductCatalogService(
            ProductCatalogSource productSource,
            StringRedisTemplate redisTemplate,
            ProductCatalogCacheProperties properties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.productSource = productSource;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
        this.cacheHits = Counter.builder("rescue.catalog.cache.hits")
                .description("Catalog reads served from Redis")
                .register(meterRegistry);
        this.cacheMisses = Counter.builder("rescue.catalog.cache.misses")
                .description("Catalog reads that missed Redis")
                .register(meterRegistry);
        this.lockContention = Counter.builder("rescue.catalog.cache.lock.contention")
                .description("Catalog reads that observed another node rebuilding the same key")
                .register(meterRegistry);
        this.waitTimeouts = Counter.builder("rescue.catalog.cache.wait.timeouts")
                .description("Catalog reads that stopped waiting for a cache rebuild")
                .register(meterRegistry);
        this.redisFailures = Counter.builder("rescue.catalog.cache.redis.failures")
                .description("Catalog cache operations that failed because Redis was unavailable")
                .register(meterRegistry);
    }

    public ProductResponse findById(UUID productId) {
        if (!properties.isEnabled()) {
            return productSource.load(productId);
        }

        CacheLookup initial = lookup(productId);
        if (!initial.redisAvailable()) {
            return productSource.load(productId);
        }
        if (initial.value().isPresent()) {
            cacheHits.increment();
            return initial.value().orElseThrow();
        }

        cacheMisses.increment();

        String token = UUID.randomUUID().toString();
        LockAttempt attempt = tryAcquireLock(productId, token);

        if (attempt == LockAttempt.REDIS_UNAVAILABLE) {
            return productSource.load(productId);
        }

        if (attempt == LockAttempt.ACQUIRED) {
            try {
                CacheLookup afterLock = lookup(productId);
                if (!afterLock.redisAvailable()) {
                    return productSource.load(productId);
                }
                if (afterLock.value().isPresent()) {
                    cacheHits.increment();
                    return afterLock.value().orElseThrow();
                }

                ProductResponse loaded = productSource.load(productId);
                put(productId, loaded);
                return loaded;
            } finally {
                releaseLock(productId, token);
            }
        }

        lockContention.increment();
        return waitForRebuildOrFallBack(productId);
    }

    private ProductResponse waitForRebuildOrFallBack(UUID productId) {
        Instant deadline = clock.instant().plus(properties.getWaitTimeout());

        while (clock.instant().isBefore(deadline)) {
            sleep(properties.getPollInterval());

            CacheLookup lookup = lookup(productId);
            if (!lookup.redisAvailable()) {
                return productSource.load(productId);
            }
            if (lookup.value().isPresent()) {
                cacheHits.increment();
                return lookup.value().orElseThrow();
            }
        }

        waitTimeouts.increment();
        log.atWarn()
                .addKeyValue("productId", productId)
                .log("Timed out waiting for another node to rebuild product cache; falling back to PostgreSQL");

        ProductResponse loaded = productSource.load(productId);
        put(productId, loaded);
        return loaded;
    }

    private CacheLookup lookup(UUID productId) {
        try {
            String encoded = redisTemplate.opsForValue().get(ProductCacheKeys.data(productId));
            return new CacheLookup(true, encoded == null ? Optional.empty() : Optional.of(decode(encoded)));
        } catch (DataAccessException exception) {
            redisFailures.increment();
            logRedisFailure("read", productId, exception);
            return new CacheLookup(false, Optional.empty());
        }
    }

    private void put(UUID productId, ProductResponse response) {
        try {
            redisTemplate.opsForValue().set(
                    ProductCacheKeys.data(productId),
                    encode(response),
                    ttlWithJitter()
            );
        } catch (DataAccessException exception) {
            redisFailures.increment();
            logRedisFailure("write", productId, exception);
        }
    }

    private LockAttempt tryAcquireLock(UUID productId, String token) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    ProductCacheKeys.lock(productId),
                    token,
                    properties.getLockTtl()
            );
            return Boolean.TRUE.equals(acquired) ? LockAttempt.ACQUIRED : LockAttempt.CONTENDED;
        } catch (DataAccessException exception) {
            redisFailures.increment();
            logRedisFailure("lock", productId, exception);
            return LockAttempt.REDIS_UNAVAILABLE;
        }
    }

    private void releaseLock(UUID productId, String token) {
        try {
            redisTemplate.execute(
                    RELEASE_LOCK_SCRIPT,
                    List.of(ProductCacheKeys.lock(productId)),
                    token
            );
        } catch (DataAccessException exception) {
            redisFailures.increment();
            logRedisFailure("unlock", productId, exception);
        }
    }

    private Duration ttlWithJitter() {
        long jitterMillis = Math.max(0L, properties.getTtlJitter().toMillis());
        long extra = jitterMillis == 0L ? 0L : ThreadLocalRandom.current().nextLong(jitterMillis + 1L);
        return properties.getTtl().plusMillis(extra);
    }

    private void logRedisFailure(String operation, UUID productId, DataAccessException exception) {
        log.atWarn()
                .addKeyValue("operation", operation)
                .addKeyValue("productId", productId)
                .setCause(exception)
                .log("Redis catalog cache unavailable; failing open to the database");
    }

    private String encode(ProductResponse response) {
        return String.join(
                "|",
                response.id().toString(),
                encodeText(response.sku()),
                encodeText(response.name()),
                response.unitPrice().toPlainString(),
                Integer.toString(response.availableStock()),
                response.createdAt().toString(),
                response.updatedAt().toString()
        );
    }

    private ProductResponse decode(String encoded) {
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 7) {
            throw new IllegalStateException("Invalid cached product payload");
        }

        return new ProductResponse(
                UUID.fromString(parts[0]),
                decodeText(parts[1]),
                decodeText(parts[2]),
                new BigDecimal(parts[3]),
                Integer.parseInt(parts[4]),
                Instant.parse(parts[5]),
                Instant.parse(parts[6])
        );
    }

    private String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeText(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for catalog cache rebuild", exception);
        }
    }

    private record CacheLookup(boolean redisAvailable, Optional<ProductResponse> value) {
    }

    private enum LockAttempt {
        ACQUIRED,
        CONTENDED,
        REDIS_UNAVAILABLE
    }
}
