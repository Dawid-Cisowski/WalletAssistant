package org.dawid.cisowski.walletassistant.walletevents.api;

import java.time.Instant;
import java.util.Map;

public record StoredEventData(
        String eventId,
        EventType eventType,
        Instant occurredAt,
        Map<String, Object> payload,
        String userId
) {
}
