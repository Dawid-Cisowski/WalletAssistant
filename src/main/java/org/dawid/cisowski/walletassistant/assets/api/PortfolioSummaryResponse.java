package org.dawid.cisowski.walletassistant.assets.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioSummaryResponse(
        BigDecimal totalCurrentValue,
        BigDecimal totalInvested,
        BigDecimal totalGainLoss,
        BigDecimal totalGainLossPercent,
        String currency,
        LocalDate asOf,
        List<PortfolioGroupResponse> portfolios
) {
}
