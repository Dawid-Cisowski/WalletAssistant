package org.dawid.cisowski.walletassistant.investments.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentResponse(
        String snapshotId,
        String investmentType,
        String investmentName,
        BigDecimal currentValue,
        BigDecimal investedAmount,
        BigDecimal gainLoss,
        BigDecimal gainLossPercent,
        String currency,
        LocalDate recordedDate
) {
}
