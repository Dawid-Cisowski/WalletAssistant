package org.dawid.cisowski.walletassistant.assets.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Overall portfolio summary across all portfolio types")
public record PortfolioSummaryResponse(
        @Schema(description = "Total market value of all open positions", example = "42500.00") BigDecimal totalCurrentValue,
        @Schema(description = "Total amount invested in all open positions", example = "38000.00") BigDecimal totalInvested,
        @Schema(description = "Total unrealised gain or loss", example = "4500.00") BigDecimal totalGainLoss,
        @Schema(description = "Total gain/loss as percentage of total invested", example = "11.84") BigDecimal totalGainLossPercent,
        @Schema(description = "ISO 4217 currency code", example = "PLN") String currency,
        @Schema(description = "Date of the most recent price snapshot used", example = "2026-06-18") LocalDate asOf,
        @Schema(description = "Positions grouped by portfolio type (IKE, PERSONAL)") List<PortfolioGroupResponse> portfolios
) {
}
