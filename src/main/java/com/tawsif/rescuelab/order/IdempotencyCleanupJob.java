package com.tawsif.rescuelab.order;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class IdempotencyCleanupJob {

    private final OrderIdempotencyRecordRepository idempotencyRepository;
    private final Clock clock;

    IdempotencyCleanupJob(OrderIdempotencyRecordRepository idempotencyRepository, Clock clock) {
        this.idempotencyRepository = idempotencyRepository;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${rescue-lab.idempotency.cleanup-initial-delay:PT1M}",
            fixedDelayString = "${rescue-lab.idempotency.cleanup-interval:PT1H}"
    )
    @Transactional
    public void removeExpiredRecords() {
        idempotencyRepository.deleteExpiredBefore(clock.instant());
    }
}
