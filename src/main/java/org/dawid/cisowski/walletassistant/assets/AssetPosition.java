package org.dawid.cisowski.walletassistant.assets;

import org.dawid.cisowski.walletassistant.walletevents.api.StoredEventData;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

record AssetPosition(
        String positionId,
        String eventId,
        String userId,
        PortfolioType portfolioType,
        String assetSymbol,
        AssetType assetType,
        String assetName,
        BigDecimal quantity,
        BigDecimal purchasePrice,
        String currency,
        LocalDate purchasedAt
) {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    AssetPosition {
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(purchasePrice, "purchasePrice must not be null");
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        if (purchasePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("purchasePrice must be greater than zero");
        }
        Objects.requireNonNull(currency, "currency must not be null");
    }

    static AssetPosition createFromEvent(StoredEventData event) {
        var payload = event.payload();
        return new AssetPosition(
                resolvePositionId(payload),
                event.eventId(),
                event.userId(),
                PortfolioType.fromString(stringValue(payload, "portfolioType")),
                stringValue(payload, "assetSymbol").toUpperCase(),
                AssetType.fromString(stringValue(payload, "assetType")),
                stringValue(payload, "assetName"),
                new BigDecimal(stringValue(payload, "quantity")),
                new BigDecimal(stringValue(payload, "purchasePrice")),
                stringOrDefault(payload, "currency", "PLN"),
                resolvePurchaseDate(payload, event.occurredAt())
        );
    }

    private static String resolvePositionId(Map<String, Object> payload) {
        return Optional.ofNullable(payload.get("positionId"))
                .map(Object::toString)
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    private static LocalDate resolvePurchaseDate(Map<String, Object> payload, Instant occurredAt) {
        return Optional.ofNullable(payload.get("purchaseDate"))
                .map(Object::toString)
                .map(LocalDate::parse)
                .or(() -> Optional.ofNullable(payload.get("purchasedAt"))
                        .map(Object::toString)
                        .map(s -> Instant.parse(s).atZone(WARSAW).toLocalDate()))
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
