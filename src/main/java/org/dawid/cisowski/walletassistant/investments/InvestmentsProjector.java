package org.dawid.cisowski.walletassistant.investments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.walletevents.api.StoredEventData;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class InvestmentsProjector {

    private final InvestmentsRepository repository;

    void projectSnapshot(StoredEventData event) {
        repository.findByEventId(event.eventId())
                .ifPresentOrElse(
                        existing -> log.debug("Skipping already projected investment event {}", event.eventId()),
                        () -> persistWithRetry(event)
                );
    }

    private void persistWithRetry(StoredEventData event) {
        try {
            persist(event);
        } catch (ObjectOptimisticLockingFailureException retryable) {
            log.warn("Optimistic locking failure projecting investment event {}, retrying once", event.eventId());
            persist(event);
        }
    }

    private void persist(StoredEventData event) {
        InvestmentSnapshot snapshot = InvestmentSnapshot.createFromEvent(event);
        repository.save(InvestmentSnapshotJpaEntity.from(snapshot));
    }
}
