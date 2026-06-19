package org.dawid.cisowski.walletassistant.assets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "asset_price_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AssetPriceHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "asset_symbol", nullable = false, length = 32)
    private String assetSymbol;

    @Column(name = "price", nullable = false, precision = 12, scale = 4)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    private AssetPriceHistoryJpaEntity(
            String eventId,
            String userId,
            String assetSymbol,
            BigDecimal price,
            String currency,
            LocalDate priceDate
    ) {
        this.eventId = eventId;
        this.userId = userId;
        this.assetSymbol = assetSymbol;
        this.price = price;
        this.currency = currency;
        this.priceDate = priceDate;
    }

    static AssetPriceHistoryJpaEntity from(AssetPriceSnapshot snapshot) {
        return new AssetPriceHistoryJpaEntity(
                snapshot.eventId(),
                snapshot.userId(),
                snapshot.assetSymbol(),
                snapshot.price(),
                snapshot.currency(),
                snapshot.priceDate()
        );
    }

    String eventId() {
        return eventId;
    }

    String userId() {
        return userId;
    }

    String assetSymbol() {
        return assetSymbol;
    }

    BigDecimal price() {
        return price;
    }

    String currency() {
        return currency;
    }

    LocalDate priceDate() {
        return priceDate;
    }

    Long version() {
        return version;
    }
}
