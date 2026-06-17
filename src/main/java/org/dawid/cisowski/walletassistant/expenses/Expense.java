package org.dawid.cisowski.walletassistant.expenses;

import org.dawid.cisowski.walletassistant.walletevents.api.StoredEventData;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

record Expense(
        String expenseId,
        String eventId,
        String userId,
        BigDecimal amount,
        String currency,
        ExpenseCategory category,
        String description,
        String merchant,
        String accountType,
        Instant occurredAt,
        LocalDate occurredDate
) {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    Expense {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        Objects.requireNonNull(currency, "currency must not be null");
    }

    static Expense createFromEvent(StoredEventData event) {
        var payload = event.payload();
        return new Expense(
                resolveExpenseId(payload),
                event.eventId(),
                event.userId(),
                new BigDecimal(stringValue(payload, "amount")),
                stringOrDefault(payload, "currency", "PLN"),
                ExpenseCategory.fromString(stringValue(payload, "category")),
                nullableString(payload, "description"),
                nullableString(payload, "merchant"),
                stringValue(payload, "accountType"),
                event.occurredAt(),
                resolveDate(payload, event.occurredAt())
        );
    }

    private static String resolveExpenseId(Map<String, Object> payload) {
        return Optional.ofNullable(payload.get("expenseId"))
                .map(Object::toString)
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    private static LocalDate resolveDate(Map<String, Object> payload, Instant occurredAt) {
        return Optional.ofNullable(payload.get("date"))
                .map(Object::toString)
                .map(LocalDate::parse)
                .or(() -> Optional.ofNullable(payload.get("occurredAt"))
                        .map(Object::toString)
                        .map(s -> LocalDate.ofInstant(Instant.parse(s), ZONE)))
                .orElseGet(() -> LocalDate.ofInstant(occurredAt, ZONE));
    }

    private static String stringValue(Map<String, Object> payload, String key) {
        return Optional.ofNullable(payload.get(key))
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Missing required field: " + key));
    }

    private static String stringOrDefault(Map<String, Object> payload, String key, String fallback) {
        return Optional.ofNullable(payload.get(key)).map(Object::toString).orElse(fallback);
    }

    private static String nullableString(Map<String, Object> payload, String key) {
        return Optional.ofNullable(payload.get(key)).map(Object::toString).orElse(null);
    }
}
