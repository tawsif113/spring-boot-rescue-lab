package com.tawsif.rescuelab.outbox;

import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEventStore {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ProcessedEventStore(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public boolean recordIfFirst(UUID eventId, String eventType) {
        return jdbcTemplate.update(
                """
                INSERT INTO processed_events (event_id, event_type, processed_at)
                VALUES (?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                eventId,
                eventType,
                clock.instant()
        ) == 1;
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_events", Long.class);
        return count == null ? 0L : count;
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM processed_events");
    }
}
