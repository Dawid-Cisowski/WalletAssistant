package org.dawid.cisowski.walletassistant.accounts;

import org.dawid.cisowski.walletassistant.walletevents.api.StoredEventData;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

record BalanceSnapshot(
        String snapshotId,
        String eventId,
        String userId,
        AccountType accountType,
        String accountName,
        BigDecimal balance,
        String currency,
        Instant recordedAt,
        LocalDate recordedDate
) {
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    BalanceSnapshot {
        Objects.requireNonNull(balance, "balance must not be null");
    }

    static BalanceSnapshot createFromEvent(StoredEventData event) {
        var payload = event.payload();
        var recordedAt = event.occurredAt();
        return new BalanceSnapshot(
                UUID.randomUUID().toString(),
                event.eventId(),
                event.userId(),
                AccountType.fromString(stringValue(payload, "accountType")),
                stringValue(payload, "accountName"),
                new BigDecimal(stringValue(payload, "balance")),
                stringValueOrDefault(payload, "currency", "PLN"),
                recordedAt,
                resolveRecordedDate(payload, recordedAt)
        );
    }

    private static LocalDate resolveRecordedDate(Map<String, Object> payload, Instant recordedAt) {
        return Optional.ofNullable(payload.get("date"))
                .map(Object::toString)
                .map(LocalDate::parse)
                .or(() -> Optional.ofNullable(payload.get("recordedAt"))
                        .map(Object::toString)
                        .map(s -> Instant.parse(s).atZone(WARSAW).toLocalDate()))
                .orElseGet(() -> recordedAt.atZone(WARSAW).toLocalDate());
    }

    private static String stringValue(Map<String, Object> payload, String key) {
        return Optional.ofNullable(payload.get(key))
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Missing payload field: " + key));
    }

    private static String stringValueOrDefault(Map<String, Object> payload, String key, String defaultValue) {
        return Optional.ofNullable(payload.get(key))
                .map(Object::toString)
                .orElse(defaultValue);
    }
}
