package com.tawsif.rescuelab.outbox;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitEventTransport implements EventTransport {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;
    private final Duration confirmTimeout;

    public RabbitEventTransport(
            RabbitTemplate rabbitTemplate,
            @Value("${rescue-lab.events.exchange:rescue-lab.events}") String exchange,
            @Value("${rescue-lab.events.routing-key:order.created}") String routingKey,
            @Value("${rescue-lab.events.confirm-timeout:PT5S}") Duration confirmTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(OutboxEvent event) {
        CorrelationData correlationData = new CorrelationData(event.getId().toString());

        rabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                event.getPayload(),
                message -> {
                    MessageProperties properties = message.getMessageProperties();
                    properties.setMessageId(event.getId().toString());
                    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    properties.setType(event.getEventType());
                    properties.setHeader("eventId", event.getId().toString());
                    properties.setHeader("aggregateId", event.getAggregateId().toString());
                    return message;
                },
                correlationData
        );

        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ negatively acknowledged event: " + confirm.getReason());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for RabbitMQ publisher confirm", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("RabbitMQ publisher confirm failed", exception);
        }
    }
}
