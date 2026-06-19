package org.dawid.cisowski.walletassistant.assets;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.walletevents.api.StoredEventData;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
class AssetsProjector {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final AssetPositionsRepository positionsRepository;
    private final AssetPriceHistoryRepository priceHistoryRepository;

    void projectPositionOpened(StoredEventData event) {
        withOptimisticRetry(() -> applyPositionOpened(event));
    }

    void projectPositionClosed(StoredEventData event) {
        withOptimisticRetry(() -> applyPositionClosed(event));
    }

    void projectPriceSnapshot(StoredEventData event) {
        withOptimisticRetry(() -> applyPriceSnapshot(event));
    }

    private void applyPositionOpened(StoredEventData event) {
        positionsRepository.findByEventId(event.eventId())
                .ifPresentOrElse(
                        existing -> log.debug("Asset position event {} already projected, skipping", event.eventId()),
                        () -> positionsRepository.save(AssetPositionJpaEntity.from(AssetPosition.createFromEvent(event)))
                );
    }

    private void applyPositionClosed(StoredEventData event) {
        positionsRepository.findByEventId(event.eventId())
                .ifPresentOrElse(
                        existing -> log.debug("Asset close event {} already projected, skipping", event.eventId()),
                        () -> closePosition(event)
                );
    }

    private void closePosition(StoredEventData event) {
        var payload = event.payload();
        var openPositionId = stringValue(payload, "positionId");
        positionsRepository.findByPositionId(openPositionId)
                .ifPresentOrElse(
                        position -> applyClose(position, event),
                        () -> log.warn("Asset close event {} references unknown position {}", event.eventId(), openPositionId)
                );
    }

    private void applyClose(AssetPositionJpaEntity position, StoredEventData event) {
        var payload = event.payload();
        var salePrice = new BigDecimal(stringValue(payload, "salePrice"));
        var soldAt = resolveSoldAt(payload, event.occurredAt());
        var requestedQuantity = Optional.ofNullable(payload.get("quantity"))
                .map(Object::toString)
                .map(BigDecimal::new)
                .orElse(position.quantity());

        if (requestedQuantity.compareTo(position.quantity()) >= 0) {
            position.close(salePrice, soldAt);
            return;
        }
        var remaining = position.split(requestedQuantity, salePrice, soldAt);
        positionsRepository.save(remaining);
    }

    private void applyPriceSnapshot(StoredEventData event) {
        priceHistoryRepository.findByEventId(event.eventId())
                .ifPresentOrElse(
                        existing -> log.debug("Asset price event {} already projected, skipping", event.eventId()),
                        () -> persistPrice(event)
                );
    }

    private void persistPrice(StoredEventData event) {
        try {
            priceHistoryRepository.saveAndFlush(
                    AssetPriceHistoryJpaEntity.from(AssetPriceSnapshot.createFromEvent(event)));
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Asset price for event {} already exists for symbol/date, skipping", event.eventId());
        }
    }

    private LocalDate resolveSoldAt(Map<String, Object> payload, Instant occurredAt) {
        return Optional.ofNullable(payload.get("saleDate"))
                .map(Object::toString)
                .map(LocalDate::parse)
                .or(() -> Optional.ofNullable(payload.get("soldAt"))
                        .map(Object::toString)
                        .map(s -> Instant.parse(s).atZone(WARSAW).toLocalDate()))
                .orElseGet(() -> occurredAt.atZone(WARSAW).toLocalDate());
    }

    private String stringValue(Map<String, Object> payload, String key) {
        return Optional.ofNullable(payload.get(key))
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Missing payload field: " + key));
    }

    private void withOptimisticRetry(Runnable action) {
        try {
            action.run();
        } catch (ObjectOptimisticLockingFailureException firstFailure) {
            log.warn("Optimistic lock conflict on asset projection, retrying once", firstFailure);
            action.run();
        }
    }
}
