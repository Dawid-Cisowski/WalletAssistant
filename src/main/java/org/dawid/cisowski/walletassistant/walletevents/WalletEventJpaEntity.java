package org.dawid.cisowski.walletassistant.walletevents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "wallet_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class WalletEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_by_event_id")
    private String deletedByEventId;

    @Column(name = "superseded_by_event_id")
    private String supersededByEventId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private WalletEventJpaEntity(
            String eventId,
            String idempotencyKey,
            String eventType,
            Instant occurredAt,
            Map<String, Object> payload,
            String userId,
            Instant createdAt,
            String deletedByEventId,
            String supersededByEventId
    ) {
        this.eventId = eventId;
        this.idempotencyKey = idempotencyKey;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.payload = payload;
        this.userId = userId;
        this.createdAt = createdAt;
        this.deletedByEventId = deletedByEventId;
        this.supersededByEventId = supersededByEventId;
    }

    static WalletEventJpaEntity from(WalletEvent event) {
        return new WalletEventJpaEntity(
                event.eventId(),
                event.idempotencyKey(),
                event.eventType().typeName(),
                event.occurredAt(),
                event.payload(),
                event.userId(),
                event.createdAt(),
                event.deletedByEventId(),
                event.supersededByEventId()
        );
    }

    Long getId() {
        return id;
    }

    String getEventId() {
        return eventId;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    String getEventType() {
        return eventType;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }

    Map<String, Object> getPayload() {
        return payload;
    }

    String getUserId() {
        return userId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    String getDeletedByEventId() {
        return deletedByEventId;
    }

    String getSupersededByEventId() {
        return supersededByEventId;
    }

    Long getVersion() {
        return version;
    }
}
