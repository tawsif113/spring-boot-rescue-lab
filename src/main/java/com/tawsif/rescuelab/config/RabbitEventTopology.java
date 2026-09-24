package com.tawsif.rescuelab.config;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitEventTopology {

    @Bean
    DirectExchange orderEventsExchange(
            @Value("${rescue-lab.events.exchange:rescue-lab.events}") String exchange
    ) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    DirectExchange orderEventsDeadLetterExchange(
            @Value("${rescue-lab.events.dead-letter-exchange:rescue-lab.events.dlx}") String exchange
    ) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    Queue orderEventsQueue(
            @Value("${rescue-lab.events.queue:rescue-lab.order-events}") String queue,
            @Value("${rescue-lab.events.dead-letter-exchange:rescue-lab.events.dlx}") String deadLetterExchange,
            @Value("${rescue-lab.events.dead-letter-routing-key:order.created.dead}") String deadLetterRoutingKey
    ) {
        return QueueBuilder.durable(queue)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", deadLetterExchange,
                        "x-dead-letter-routing-key", deadLetterRoutingKey
                ))
                .build();
    }

    @Bean
    Queue orderEventsDeadLetterQueue(
            @Value("${rescue-lab.events.dead-letter-queue:rescue-lab.order-events.dlq}") String queue
    ) {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    Binding orderEventsBinding(
            Queue orderEventsQueue,
            DirectExchange orderEventsExchange,
            @Value("${rescue-lab.events.routing-key:order.created}") String routingKey
    ) {
        return BindingBuilder.bind(orderEventsQueue).to(orderEventsExchange).with(routingKey);
    }

    @Bean
    Binding orderEventsDeadLetterBinding(
            Queue orderEventsDeadLetterQueue,
            DirectExchange orderEventsDeadLetterExchange,
            @Value("${rescue-lab.events.dead-letter-routing-key:order.created.dead}") String routingKey
    ) {
        return BindingBuilder.bind(orderEventsDeadLetterQueue)
                .to(orderEventsDeadLetterExchange)
                .with(routingKey);
    }
}
