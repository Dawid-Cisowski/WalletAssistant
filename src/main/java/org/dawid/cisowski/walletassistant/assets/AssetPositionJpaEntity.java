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
import java.util.UUID;

@Entity
@Table(name = "asset_positions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AssetPositionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "position_id", nullable = false, unique = true, length = 64)
    private String positionId;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "portfolio_type", nullable = false, length = 32)
    private String portfolioType;

    @Column(name = "asset_symbol", nullable = false, length = 32)
    private String assetSymbol;

    @Column(name = "asset_type", nullable = false, length = 32)
    private String assetType;

    @Column(name = "asset_name", nullable = false, length = 255)
    private String assetName;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "purchase_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal purchasePrice;

    @Column(name = "purchased_at", nullable = false)
    private LocalDate purchasedAt;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "sale_price", precision = 12, scale = 4)
    private BigDecimal salePrice;

    @Column(name = "sold_at")
    private LocalDate soldAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    private AssetPositionJpaEntity(
            String positionId,
            String eventId,
            String userId,
            String portfolioType,
            String assetSymbol,
            String assetType,
            String assetName,
            BigDecimal quantity,
            BigDecimal purchasePrice,
            LocalDate purchasedAt,
            String currency,
            String status
    ) {
        this.positionId = positionId;
        this.eventId = eventId;
        this.userId = userId;
        this.portfolioType = portfolioType;
        this.assetSymbol = assetSymbol;
        this.assetType = assetType;
        this.assetName = assetName;
        this.quantity = quantity;
        this.purchasePrice = purchasePrice;
        this.purchasedAt = purchasedAt;
        this.currency = currency;
        this.status = status;
    }

    static AssetPositionJpaEntity from(AssetPosition position) {
        return new AssetPositionJpaEntity(
                position.positionId(),
                position.eventId(),
                position.userId(),
                position.portfolioType().name(),
                position.assetSymbol(),
                position.assetType().name(),
                position.assetName(),
                position.quantity(),
                position.purchasePrice(),
                position.purchasedAt(),
                position.currency(),
                PositionStatus.OPEN.name()
        );
    }

    void close(BigDecimal salePrice, LocalDate soldAt) {
        this.status = PositionStatus.CLOSED.name();
        this.salePrice = salePrice;
        this.soldAt = soldAt;
    }

    AssetPositionJpaEntity split(BigDecimal soldQuantity, BigDecimal salePrice, LocalDate soldAt) {
        var remainingQuantity = this.quantity.subtract(soldQuantity);
        this.quantity = soldQuantity;
        close(salePrice, soldAt);
        return new AssetPositionJpaEntity(
                UUID.randomUUID().toString(),
                this.eventId,
                this.userId,
                this.portfolioType,
                this.assetSymbol,
                this.assetType,
                this.assetName,
                remainingQuantity,
                this.purchasePrice,
                this.purchasedAt,
                this.currency,
                PositionStatus.OPEN.name()
        );
    }

    String positionId() {
        return positionId;
    }

    String eventId() {
        return eventId;
    }

    String userId() {
        return userId;
    }

    String portfolioType() {
        return portfolioType;
    }

    String assetSymbol() {
        return assetSymbol;
    }

    String assetType() {
        return assetType;
    }

    String assetName() {
        return assetName;
    }

    BigDecimal quantity() {
        return quantity;
    }

    BigDecimal purchasePrice() {
        return purchasePrice;
    }

    LocalDate purchasedAt() {
        return purchasedAt;
    }

    String currency() {
        return currency;
    }

    String status() {
        return status;
    }

    BigDecimal salePrice() {
        return salePrice;
    }

    LocalDate soldAt() {
        return soldAt;
    }

    Long version() {
        return version;
    }
}
