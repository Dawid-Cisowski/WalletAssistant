package org.dawid.cisowski.walletassistant.walletevents;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
interface WalletEventsRepository extends JpaRepository<WalletEventJpaEntity, Long> {

    Optional<WalletEventJpaEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<WalletEventJpaEntity> findByEventId(String eventId);

    List<WalletEventJpaEntity> findByUserIdAndEventTypeAndOccurredAtBetween(
            String userId,
            String eventType,
            Instant from,
            Instant to
    );
}
