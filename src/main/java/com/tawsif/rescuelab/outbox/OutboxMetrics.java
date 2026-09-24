package com.tawsif.rescuelab.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics implements MeterBinder {

    private final OutboxEventRepository outboxRepository;

    public OutboxMetrics(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(
                        "rescue.outbox.pending",
                        outboxRepository,
                        repository -> repository.countByStatus(OutboxStatus.PENDING)
                )
                .description("Number of order integration events waiting for publication")
                .register(registry);
    }
}
