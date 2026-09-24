package com.tawsif.rescuelab.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rescue-lab.events.publisher")
public class OutboxPublisherProperties {

    private int batchSize = 25;
    private Duration retryBase = Duration.ofSeconds(2);
    private Duration retryMax = Duration.ofMinutes(5);

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getRetryBase() {
        return retryBase;
    }

    public void setRetryBase(Duration retryBase) {
        this.retryBase = retryBase;
    }

    public Duration getRetryMax() {
        return retryMax;
    }

    public void setRetryMax(Duration retryMax) {
        this.retryMax = retryMax;
    }
}
