package com.tawsif.rescuelab.outbox;

import com.tawsif.rescuelab.order.PurchaseOrder;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderEventOutbox {

    private final OutboxEventRepository outboxRepository;

    public OrderEventOutbox(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public OutboxEvent recordOrderCreated(PurchaseOrder order, Instant now) {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "ORDER_CREATED",
                  "occurredAt": "%s",
                  "order": {
                    "id": "%s",
                    "customerId": "%s",
                    "status": "%s",
                    "totalAmount": %s,
                    "createdAt": "%s"
                  }
                }
                """.formatted(
                eventId,
                now,
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount().toPlainString(),
                order.getCreatedAt()
        );

        return outboxRepository.save(OutboxEvent.pending(
                eventId,
                "ORDER",
                order.getId(),
                "ORDER_CREATED",
                payload,
                now
        ));
    }
}
