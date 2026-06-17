package org.dawid.cisowski.walletassistant.investments.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioSummaryResponse(
        BigDecimal totalValue,
        BigDecimal totalInvested,
        BigDecimal totalGainLoss,
        BigDecimal totalGainLossPercent,
        List<InvestmentResponse> investments,
        String currency,
        LocalDate asOf
) {
}
