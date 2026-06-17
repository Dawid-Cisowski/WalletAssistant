package org.dawid.cisowski.walletassistant.accounts;

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
@Table(name = "account_balance_snapshots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BalanceSnapshotJpaEntity {

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

    @Column(name = "account_type", nullable = false, length = 64)
    private String accountType;

    @Column(name = "account_name", nullable = false, length = 255)
    private String accountName;

    @Column(name = "balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

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

    private BalanceSnapshotJpaEntity(
            String snapshotId,
            String eventId,
            String userId,
            String accountType,
            String accountName,
            BigDecimal balance,
            String currency,
            Instant recordedAt,
            LocalDate recordedDate
    ) {
        this.snapshotId = snapshotId;
        this.eventId = eventId;
        this.userId = userId;
        this.accountType = accountType;
        this.accountName = accountName;
        this.balance = balance;
        this.currency = currency;
        this.recordedAt = recordedAt;
        this.recordedDate = recordedDate;
    }

    static BalanceSnapshotJpaEntity from(BalanceSnapshot snapshot) {
        return new BalanceSnapshotJpaEntity(
                snapshot.snapshotId(),
                snapshot.eventId(),
                snapshot.userId(),
                snapshot.accountType().name(),
                snapshot.accountName(),
                snapshot.balance(),
                snapshot.currency(),
                snapshot.recordedAt(),
                snapshot.recordedDate()
        );
    }

    Long id() {
        return id;
    }

    String snapshotId() {
        return snapshotId;
    }

    String eventId() {
        return eventId;
    }

    String userId() {
        return userId;
    }

    String accountType() {
        return accountType;
    }

    String accountName() {
        return accountName;
    }

    BigDecimal balance() {
        return balance;
    }

    String currency() {
        return currency;
    }

    Instant recordedAt() {
        return recordedAt;
    }

    LocalDate recordedDate() {
        return recordedDate;
    }

    Long version() {
        return version;
    }
}
