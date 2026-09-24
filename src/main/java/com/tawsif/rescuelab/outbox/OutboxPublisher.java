package com.tawsif.rescuelab.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        prefix = "rescue-lab.events.publisher",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final EventTransport eventTransport;
    private final Clock clock;
    private final OutboxPublisherProperties properties;

    public OutboxPublisher(
            OutboxEventRepository outboxRepository,
            EventTransport eventTransport,
            Clock clock,
            OutboxPublisherProperties properties
    ) {
        this.outboxRepository = outboxRepository;
        this.eventTransport = eventTransport;
        this.clock = clock;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString = "${rescue-lab.events.publisher.initial-delay:PT5S}",
            fixedDelayString = "${rescue-lab.events.publisher.interval:PT2S}"
    )
    @Transactional
    public PublishBatchResult publishDueEvents() {
        Instant now = clock.instant();
        List<OutboxEvent> batch = outboxRepository.lockNextBatch(now, properties.getBatchSize());
        int published = 0;
        int failed = 0;

        for (OutboxEvent event : batch) {
            try {
                eventTransport.publish(event);
                event.markPublished(clock.instant());
                published += 1;
            } catch (Exception exception) {
                event.markPublishFailed(
                        exception,
                        clock.instant(),
                        properties.getRetryBase(),
                        properties.getRetryMax()
                );
                failed += 1;
                log.atWarn()
                        .addKeyValue("eventId", event.getId())
                        .addKeyValue("attempt", event.getAttempts())
                        .setCause(exception)
                        .log("Outbox publication failed; event scheduled for retry");
            }
        }

        return new PublishBatchResult(batch.size(), published, failed);
    }

    public record PublishBatchResult(int attempted, int published, int failed) {
    }
}
