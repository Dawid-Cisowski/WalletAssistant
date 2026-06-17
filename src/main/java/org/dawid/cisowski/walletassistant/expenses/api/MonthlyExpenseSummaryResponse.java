package org.dawid.cisowski.walletassistant.expenses.api;

import java.math.BigDecimal;
import java.util.Map;

public record MonthlyExpenseSummaryResponse(
        int year,
        int month,
        BigDecimal totalAmount,
        String currency,
        Map<String, BigDecimal> categoryTotals,
        int transactionCount
) {
}
