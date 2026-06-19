package org.dawid.cisowski.walletassistant.assets.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Summary for a single portfolio type (IKE or PERSONAL) with its positions")
public record PortfolioGroupResponse(
        @Schema(description = "Portfolio type: IKE or PERSONAL", example = "IKE") String portfolioType,
        @Schema(description = "Total market value of all open positions in this portfolio", example = "25000.00") BigDecimal totalCurrentValue,
        @Schema(description = "Total amount invested in this portfolio", example = "22000.00") BigDecimal totalInvested,
        @Schema(description = "Unrealised gain or loss for this portfolio", example = "3000.00") BigDecimal totalGainLoss,
        @Schema(description = "Gain/loss as percentage of invested amount", example = "13.64") BigDecimal totalGainLossPercent,
        @Schema(description = "ISO 4217 currency code", example = "PLN") String currency,
        @Schema(description = "Open positions in this portfolio") List<AssetPositionResponse> positions
) {
}
