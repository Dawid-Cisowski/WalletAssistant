package org.dawid.cisowski.walletassistant.walletevents.api;

import java.util.List;

public record ExpensesStoredEvent(List<StoredEventData> events) {
}
