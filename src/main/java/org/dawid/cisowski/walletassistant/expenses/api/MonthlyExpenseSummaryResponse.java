package org.dawid.cisowski.walletassistant.expenses.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "Monthly expense summary with per-category breakdown")
public record MonthlyExpenseSummaryResponse(
        @Schema(description = "Year", example = "2026") int year,
        @Schema(description = "Month (1–12)", example = "6") int month,
        @Schema(description = "Total amount across all categories", example = "3420.50") BigDecimal totalAmount,
        @Schema(description = "ISO 4217 currency code", example = "PLN") String currency,
        @Schema(description = "Amount per category, e.g. {FOOD_AND_DRINKS: 850.00, TRANSPORT: 210.00}") Map<String, BigDecimal> categoryTotals,
        @Schema(description = "Number of individual transactions", example = "47") int transactionCount
) {
}
