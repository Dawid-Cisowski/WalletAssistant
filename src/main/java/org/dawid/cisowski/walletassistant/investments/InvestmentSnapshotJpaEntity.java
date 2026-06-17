package org.dawid.cisowski.walletassistant.investments;

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
@Table(name = "investment_snapshots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class InvestmentSnapshotJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "snapshot_id", nullable = false, unique = true, length = 64)
    private String snapshotId;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "investment_type", nullable = false, length = 64)
    private String investmentType;

    @Column(name = "investment_name", nullable = false, length = 255)
    private String investmentName;

    @Column(name = "current_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "invested_amount", precision = 12, scale = 2)
    private BigDecimal investedAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_date", nullable = false)
    private LocalDate recordedDate;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    private InvestmentSnapshotJpaEntity(
            String snapshotId,
            String eventId,
            String userId,
            String investmentType,
            String investmentName,
            BigDecimal currentValue,
            BigDecimal investedAmount,
            String currency,
            Instant recordedAt,
            LocalDate recordedDate
    ) {
        this.snapshotId = snapshotId;
        this.eventId = eventId;
        this.userId = userId;
        this.investmentType = investmentType;
        this.investmentName = investmentName;
        this.currentValue = currentValue;
        this.investedAmount = investedAmount;
        this.currency = currency;
        this.recordedAt = recordedAt;
        this.recordedDate = recordedDate;
    }

    static InvestmentSnapshotJpaEntity from(InvestmentSnapshot snapshot) {
        return new InvestmentSnapshotJpaEntity(
                snapshot.snapshotId(),
                snapshot.eventId(),
                snapshot.userId(),
                snapshot.investmentType().name(),
                snapshot.investmentName(),
                snapshot.currentValue(),
                snapshot.investedAmount(),
                snapshot.currency(),
                snapshot.recordedAt(),
                snapshot.recordedDate()
        );
    }

    String getSnapshotId() {
        return snapshotId;
    }

    String getEventId() {
        return eventId;
    }

    String getUserId() {
        return userId;
    }

    String getInvestmentType() {
        return investmentType;
    }

    String getInvestmentName() {
        return investmentName;
    }

    BigDecimal getCurrentValue() {
        return currentValue;
    }

    BigDecimal getInvestedAmount() {
        return investedAmount;
    }

    String getCurrency() {
        return currency;
    }

    Instant getRecordedAt() {
        return recordedAt;
    }

    LocalDate getRecordedDate() {
        return recordedDate;
    }
}
