package org.dawid.cisowski.walletassistant.expenses;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.walletevents.api.StoredEventData;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
class ExpensesProjector {

    private final ExpensesRepository repository;

    void projectExpense(StoredEventData event) {
        withOptimisticRetry(() -> applyProjection(event));
    }

    void deleteExpense(StoredEventData event) {
        withOptimisticRetry(() -> applyDeletion(event));
    }

    private void applyProjection(StoredEventData event) {
        var expense = Expense.createFromEvent(event);
        repository.findByEventId(expense.eventId())
                .ifPresentOrElse(
                        existing -> log.debug("Expense event {} already projected", expense.eventId()),
                        () -> upsert(expense)
                );
    }

    private void upsert(Expense expense) {
        repository.findByExpenseId(expense.expenseId())
                .ifPresentOrElse(
                        existing -> existing.applyCorrection(expense),
                        () -> repository.save(ExpenseProjectionJpaEntity.from(expense))
                );
    }

    private void applyDeletion(StoredEventData event) {
        Optional.ofNullable(event.payload().get("expenseId"))
                .map(Object::toString)
                .ifPresentOrElse(
                        repository::deleteByExpenseId,
                        () -> log.warn("Expense deletion event {} missing expenseId", event.eventId())
                );
    }

    private void withOptimisticRetry(Runnable action) {
        try {
            action.run();
        } catch (ObjectOptimisticLockingFailureException firstFailure) {
            log.warn("Optimistic lock conflict on expense projection, retrying once", firstFailure);
            action.run();
        }
    }
}
