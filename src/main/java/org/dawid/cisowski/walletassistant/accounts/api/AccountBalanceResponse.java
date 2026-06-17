package org.dawid.cisowski.walletassistant.accounts.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountBalanceResponse(
        String snapshotId,
        String accountType,
        String accountName,
        BigDecimal balance,
        String currency,
        LocalDate recordedDate
) {
}
