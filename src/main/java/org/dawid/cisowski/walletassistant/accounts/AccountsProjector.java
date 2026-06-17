package org.dawid.cisowski.walletassistant.accounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.walletevents.api.StoredEventData;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AccountsProjector {

    private final AccountsRepository repository;

    void projectSnapshot(StoredEventData event) {
        try {
            persistIfAbsent(event);
        } catch (ObjectOptimisticLockingFailureException retryable) {
            log.warn("Optimistic locking failure projecting snapshot for event {}, retrying once", event.eventId());
            persistIfAbsent(event);
        }
    }

    private void persistIfAbsent(StoredEventData event) {
        repository.findByEventId(event.eventId())
                .ifPresentOrElse(
                        existing -> log.debug("Snapshot for event {} already projected, skipping", event.eventId()),
                        () -> repository.save(BalanceSnapshotJpaEntity.from(BalanceSnapshot.createFromEvent(event)))
                );
    }
}
