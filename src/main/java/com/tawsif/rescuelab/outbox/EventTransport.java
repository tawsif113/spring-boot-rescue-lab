package com.tawsif.rescuelab.outbox;

@FunctionalInterface
public interface EventTransport {

    void publish(OutboxEvent event);
}
