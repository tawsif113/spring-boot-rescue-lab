package com.tawsif.rescuelab.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class ProductCatalogServiceTest {

    @Test
    void failsOpenToDatabaseWhenRedisIsUnavailable() {
        UUID productId = UUID.randomUUID();
        ProductResponse expected = new ProductResponse(
                productId,
                "REDIS-DOWN",
                "Database remains available",
                new BigDecimal("25.00"),
                7,
                Instant.parse("2026-09-24T00:00:00Z"),
                Instant.parse("2026-09-24T00:00:00Z")
        );

        ProductCatalogSource productSource = org.mockito.Mockito.mock(ProductCatalogSource.class);
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ProductCatalogCacheProperties properties = new ProductCatalogCacheProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        when(redisTemplate.opsForValue())
                .thenThrow(new DataAccessResourceFailureException("simulated Redis outage"));
        when(productSource.load(productId)).thenReturn(expected);

        ProductCatalogService service = new ProductCatalogService(
                productSource,
                redisTemplate,
                properties,
                meterRegistry,
                Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC)
        );

        ProductResponse actual = service.findById(productId);

        assertThat(actual).isEqualTo(expected);
        verify(productSource).load(productId);
        assertThat(meterRegistry.get("rescue.catalog.cache.redis.failures").counter().count())
                .isEqualTo(1.0);
    }
}
