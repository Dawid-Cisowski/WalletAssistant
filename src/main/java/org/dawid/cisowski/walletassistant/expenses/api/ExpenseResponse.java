package org.dawid.cisowski.walletassistant.expenses.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseResponse(
        String expenseId,
        String eventId,
        BigDecimal amount,
        String currency,
        String category,
        String description,
        String merchant,
        String accountType,
        Instant occurredAt,
        LocalDate occurredDate
) {
}
