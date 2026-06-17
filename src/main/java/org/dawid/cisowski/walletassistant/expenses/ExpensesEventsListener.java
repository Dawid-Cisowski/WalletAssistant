package org.dawid.cisowski.walletassistant.expenses;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.walletevents.api.ExpensesStoredEvent;
import org.dawid.cisowski.walletassistant.walletevents.api.StoredEventData;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class ExpensesEventsListener {
    private final ExpensesProjector projector;

    @ApplicationModuleListener
    public void onExpensesStored(ExpensesStoredEvent event) {
        event.events().forEach(this::route);
    }

    private void route(StoredEventData event) {
        switch (event.eventType()) {
            case EXPENSE_RECORDED, EXPENSE_CORRECTED -> projector.projectExpense(event);
            case EXPENSE_DELETED -> projector.deleteExpense(event);
            default -> log.warn("Unexpected event type in expenses listener: {}", event.eventType());
        }
    }
}
