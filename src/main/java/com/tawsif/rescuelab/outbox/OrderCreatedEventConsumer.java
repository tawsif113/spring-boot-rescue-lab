package com.tawsif.rescuelab.outbox;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventConsumer.class);

    private final ProcessedEventStore processedEventStore;

    public OrderCreatedEventConsumer(ProcessedEventStore processedEventStore) {
        this.processedEventStore = processedEventStore;
    }

    @RabbitListener(
            queues = "${rescue-lab.events.queue:rescue-lab.order-events}",
            autoStartup = "${rescue-lab.events.consumer-enabled:true}"
    )
    @Transactional
    public void handle(Message message) {
        String rawEventId = message.getMessageProperties().getMessageId();
        if (rawEventId == null || rawEventId.isBlank()) {
            throw new AmqpRejectAndDontRequeueException("Integration event has no message ID");
        }

        UUID eventId;
        try {
            eventId = UUID.fromString(rawEventId);
        } catch (IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Integration event has an invalid message ID", exception);
        }

        String eventType = message.getMessageProperties().getType();
        if (eventType == null || eventType.isBlank()) {
            eventType = "UNKNOWN";
        }

        boolean firstDelivery = processedEventStore.recordIfFirst(eventId, eventType);
        if (!firstDelivery) {
            log.atInfo()
                    .addKeyValue("eventId", eventId)
                    .log("Duplicate integration event ignored");
            return;
        }

        log.atInfo()
                .addKeyValue("eventId", eventId)
                .addKeyValue("eventType", eventType)
                .log("Order integration event processed");
    }
}
