package org.dawid.cisowski.walletassistant.accounts.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Account balance snapshot")
public record AccountBalanceResponse(
        @Schema(description = "Unique snapshot identifier") String snapshotId,
        @Schema(description = "Account type: PERSONAL_SAVINGS or PERSONAL_SPENDING", example = "PERSONAL_SAVINGS") String accountType,
        @Schema(description = "Human-readable account name", example = "PKO BP Oszczędnościowe") String accountName,
        @Schema(description = "Balance at the time of the snapshot", example = "15000.00") BigDecimal balance,
        @Schema(description = "ISO 4217 currency code", example = "PLN") String currency,
        @Schema(description = "Date the snapshot was recorded", example = "2026-06-19") LocalDate recordedDate
) {
}
