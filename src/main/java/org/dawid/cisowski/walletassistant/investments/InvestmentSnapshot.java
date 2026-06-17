package org.dawid.cisowski.walletassistant.investments;

import org.dawid.cisowski.walletassistant.walletevents.api.StoredEventData;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

record InvestmentSnapshot(
        String snapshotId,
        String eventId,
        String userId,
        InvestmentType investmentType,
        String investmentName,
        BigDecimal currentValue,
        BigDecimal investedAmount,
        String currency,
        Instant recordedAt,
        LocalDate recordedDate
) {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final String DEFAULT_CURRENCY = "PLN";

    InvestmentSnapshot {
        if (currentValue == null || currentValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("currentValue must be greater than zero");
        }
    }

    static InvestmentSnapshot createFromEvent(StoredEventData event) {
        Map<String, Object> payload = event.payload();
        return new InvestmentSnapshot(
                UUID.randomUUID().toString(),
                event.eventId(),
                event.userId(),
                InvestmentType.fromString(payload.get("investmentType").toString()),
                payload.get("investmentName").toString(),
                new BigDecimal(payload.get("currentValue").toString()),
                Optional.ofNullable(payload.get("investedAmount"))
                        .map(value -> new BigDecimal(value.toString()))
                        .orElse(null),
                Optional.ofNullable(payload.get("currency"))
                        .map(Object::toString)
                        .orElse(DEFAULT_CURRENCY),
                event.occurredAt(),
                resolveRecordedDate(payload, event.occurredAt())
        );
    }

    private static LocalDate resolveRecordedDate(Map<String, Object> payload, Instant occurredAt) {
        return Optional.ofNullable(payload.get("date"))
                .map(Object::toString)
                .map(LocalDate::parse)
                .or(() -> Optional.ofNullable(payload.get("recordedAt"))
                        .map(Object::toString)
                        .map(s -> Instant.parse(s).atZone(WARSAW).toLocalDate()))
                .orElseGet(() -> LocalDate.ofInstant(occurredAt, WARSAW));
    }
}
