package org.dawid.cisowski.walletassistant.walletevents.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface WalletEventsFacade {

    StoreEventsResult storeEvents(StoreEventsCommand command);

    record StoreEventsCommand(String userId, List<EventEnvelope> events) {
    }

    record EventEnvelope(
            String idempotencyKey,
            String eventType,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
    }

    record StoreEventsResult(List<EventResult> results) {
    }

    record EventResult(int index, EventStatus status, String eventId, String errorMessage) {
    }

    enum EventStatus {
        STORED,
        DUPLICATE,
        INVALID
    }
}
