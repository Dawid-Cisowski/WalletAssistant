package org.dawid.cisowski.walletassistant.walletevents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.walletevents.api.AccountSnapshotsStoredEvent;
import org.dawid.cisowski.walletassistant.walletevents.api.EventType;
import org.dawid.cisowski.walletassistant.walletevents.api.ExpensesStoredEvent;
import org.dawid.cisowski.walletassistant.walletevents.api.InvestmentSnapshotsStoredEvent;
import org.dawid.cisowski.walletassistant.walletevents.api.StoredEventData;
import org.dawid.cisowski.walletassistant.walletevents.api.WalletEventsFacade;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
class WalletEventsService implements WalletEventsFacade {

    private final WalletEventsRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public StoreEventsResult storeEvents(StoreEventsCommand command) {
        var index = new AtomicInteger(0);
        var outcomes = command.events().stream()
                .map(envelope -> processEnvelope(envelope, command.userId(), index.getAndIncrement()))
                .toList();

        var storedEvents = outcomes.stream()
                .flatMap(outcome -> outcome.storedEvent().stream())
                .toList();

        registerAfterCommitPublication(storedEvents);

        return new StoreEventsResult(outcomes.stream().map(EnvelopeOutcome::result).toList());
    }

    private void registerAfterCommitPublication(List<StoredEventData> storedEvents) {
        if (storedEvents.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishGrouped(storedEvents);
            }
        });
    }

    private EnvelopeOutcome processEnvelope(EventEnvelope envelope, String userId, int index) {
        return parseEventType(envelope.eventType())
                .map(eventType -> storeIfNew(envelope, eventType, userId, index))
                .orElseGet(() -> EnvelopeOutcome.invalid(index, "Unknown event type: " + envelope.eventType()));
    }

    private EnvelopeOutcome storeIfNew(EventEnvelope envelope, EventType eventType, String userId, int index) {
        return repository.findByIdempotencyKey(envelope.idempotencyKey())
                .map(existing -> EnvelopeOutcome.duplicate(index, existing.getEventId()))
                .orElseGet(() -> persist(envelope, eventType, userId, index));
    }

    private EnvelopeOutcome persist(EventEnvelope envelope, EventType eventType, String userId, int index) {
        var event = WalletEvent.create(
                envelope.idempotencyKey(),
                eventType,
                envelope.occurredAt(),
                Optional.ofNullable(envelope.payload()).orElseGet(Map::of),
                userId
        );
        var saved = repository.save(WalletEventJpaEntity.from(event));
        return EnvelopeOutcome.stored(index, toStoredEventData(saved));
    }

    private Optional<EventType> parseEventType(String rawType) {
        try {
            return Optional.of(EventType.fromTypeName(rawType));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void publishGrouped(List<StoredEventData> storedEvents) {
        var grouped = storedEvents.stream().collect(Collectors.groupingBy(StoredEventData::eventType));

        var expenses = grouped.entrySet().stream()
                .filter(entry -> isExpenseType(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
        if (!expenses.isEmpty()) {
            eventPublisher.publishEvent(new ExpensesStoredEvent(expenses));
        }

        Optional.ofNullable(grouped.get(EventType.ACCOUNT_BALANCE_SNAPSHOT_RECORDED))
                .filter(events -> !events.isEmpty())
                .ifPresent(events -> eventPublisher.publishEvent(new AccountSnapshotsStoredEvent(events)));

        Optional.ofNullable(grouped.get(EventType.INVESTMENT_SNAPSHOT_RECORDED))
                .filter(events -> !events.isEmpty())
                .ifPresent(events -> eventPublisher.publishEvent(new InvestmentSnapshotsStoredEvent(events)));
    }

    private boolean isExpenseType(EventType eventType) {
        return switch (eventType) {
            case EXPENSE_RECORDED, EXPENSE_CORRECTED, EXPENSE_DELETED -> true;
            default -> false;
        };
    }

    private StoredEventData toStoredEventData(WalletEventJpaEntity entity) {
        return new StoredEventData(
                entity.getEventId(),
                EventType.fromTypeName(entity.getEventType()),
                entity.getOccurredAt(),
                entity.getPayload(),
                entity.getUserId()
        );
    }

    private record EnvelopeOutcome(EventResult result, Optional<StoredEventData> storedEvent) {

        static EnvelopeOutcome stored(int index, StoredEventData event) {
            return new EnvelopeOutcome(
                    new EventResult(index, EventStatus.STORED, event.eventId(), null),
                    Optional.of(event)
            );
        }

        static EnvelopeOutcome duplicate(int index, String eventId) {
            return new EnvelopeOutcome(
                    new EventResult(index, EventStatus.DUPLICATE, eventId, null),
                    Optional.empty()
            );
        }

        static EnvelopeOutcome invalid(int index, String message) {
            return new EnvelopeOutcome(
                    new EventResult(index, EventStatus.INVALID, null, message),
                    Optional.empty()
            );
        }
    }
}
