package org.dawid.cisowski.walletassistant.assets.api;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioGroupResponse(
        String portfolioType,
        BigDecimal totalCurrentValue,
        BigDecimal totalInvested,
        BigDecimal totalGainLoss,
        BigDecimal totalGainLossPercent,
        String currency,
        List<AssetPositionResponse> positions
) {
}
