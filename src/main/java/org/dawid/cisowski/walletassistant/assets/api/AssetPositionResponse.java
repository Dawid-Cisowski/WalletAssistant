package org.dawid.cisowski.walletassistant.assets.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetPositionResponse(
        String positionId,
        String portfolioType,
        String assetSymbol,
        String assetType,
        String assetName,
        BigDecimal quantity,
        BigDecimal purchasePrice,
        BigDecimal investedAmount,
        BigDecimal currentPrice,
        BigDecimal currentValue,
        BigDecimal gainLoss,
        BigDecimal gainLossPercent,
        String currency,
        LocalDate purchasedAt,
        String status,
        BigDecimal salePrice,
        LocalDate soldAt
) {
}
