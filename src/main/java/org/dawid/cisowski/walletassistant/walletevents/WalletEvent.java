package org.dawid.cisowski.walletassistant.walletevents;

import org.dawid.cisowski.walletassistant.walletevents.api.EventType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

record WalletEvent(
        String eventId,
        String idempotencyKey,
        EventType eventType,
        Instant occurredAt,
        Map<String, Object> payload,
        String userId,
        Instant createdAt,
        String deletedByEventId,
        String supersededByEventId
) {

    WalletEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    boolean isActive() {
        return deletedByEventId == null && supersededByEventId == null;
    }

    static WalletEvent create(
            String idempotencyKey,
            EventType eventType,
            Instant occurredAt,
            Map<String, Object> payload,
            String userId
    ) {
        return new WalletEvent(
                UUID.randomUUID().toString(),
                idempotencyKey,
                eventType,
                occurredAt,
                payload,
                userId,
                Instant.now(),
                null,
                null
        );
    }
}
