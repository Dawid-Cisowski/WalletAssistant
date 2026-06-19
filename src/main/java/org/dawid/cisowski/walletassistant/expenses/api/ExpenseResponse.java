package org.dawid.cisowski.walletassistant.expenses.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Single projected expense")
public record ExpenseResponse(
        @Schema(description = "Unique expense identifier") String expenseId,
        @Schema(description = "Source event identifier") String eventId,
        @Schema(description = "Expense amount", example = "49.90") BigDecimal amount,
        @Schema(description = "ISO 4217 currency code", example = "PLN") String currency,
        @Schema(description = "Expense category, e.g. FOOD_AND_DRINKS", example = "FOOD_AND_DRINKS") String category,
        @Schema(description = "Short description", example = "Lunch w Żabce") String description,
        @Schema(description = "Merchant or vendor name", example = "Żabka") String merchant,
        @Schema(description = "Account type used, e.g. PERSONAL_SPENDING", example = "PERSONAL_SPENDING") String accountType,
        @Schema(description = "Exact timestamp of the transaction") Instant occurredAt,
        @Schema(description = "Business date in Europe/Warsaw timezone", example = "2026-06-19") LocalDate occurredDate
) {
}
