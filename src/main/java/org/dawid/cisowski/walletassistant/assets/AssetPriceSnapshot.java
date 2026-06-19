package org.dawid.cisowski.walletassistant.assets;

import org.dawid.cisowski.walletassistant.walletevents.api.StoredEventData;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

record AssetPriceSnapshot(
        String eventId,
        String userId,
        String assetSymbol,
        BigDecimal price,
        String currency,
        LocalDate priceDate
) {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    AssetPriceSnapshot {
        Objects.requireNonNull(price, "price must not be null");
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        Objects.requireNonNull(currency, "currency must not be null");
    }

    static AssetPriceSnapshot createFromEvent(StoredEventData event) {
        var payload = event.payload();
        return new AssetPriceSnapshot(
                event.eventId(),
                event.userId(),
                stringValue(payload, "assetSymbol").toUpperCase(),
                new BigDecimal(stringValue(payload, "price")),
                stringOrDefault(payload, "currency", "PLN"),
                resolvePriceDate(payload, event.occurredAt())
        );
    }

    private static LocalDate resolvePriceDate(Map<String, Object> payload, Instant occurredAt) {
        return Optional.ofNullable(payload.get("priceDate"))
                .map(Object::toString)
                .map(LocalDate::parse)
                .or(() -> Optional.ofNullable(payload.get("date"))
                        .map(Object::toString)
                        .map(LocalDate::parse))
                .orElseGet(() -> occurredAt.atZone(WARSAW).toLocalDate());
    }

    private static String stringValue(Map<String, Object> payload, String key) {
        return Optional.ofNullable(payload.get(key))
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Missing payload field: " + key));
    }

    private static String stringOrDefault(Map<String, Object> payload, String key, String fallback) {
        return Optional.ofNullable(payload.get(key)).map(Object::toString).orElse(fallback);
    }
}
