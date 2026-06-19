package org.dawid.cisowski.walletassistant.assets.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Asset position — a single purchase record, open or closed")
public record AssetPositionResponse(
        @Schema(description = "Unique position identifier") String positionId,
        @Schema(description = "Portfolio type: IKE or PERSONAL", example = "IKE") String portfolioType,
        @Schema(description = "Asset ticker or symbol", example = "PKNORLEN") String assetSymbol,
        @Schema(description = "Asset type: STOCK, ETF, GOLD, CRYPTO, OTHER", example = "STOCK") String assetType,
        @Schema(description = "Human-readable asset name", example = "PKN Orlen") String assetName,
        @Schema(description = "Number of units held (up to 8 decimal places)", example = "10.0") BigDecimal quantity,
        @Schema(description = "Price per unit at purchase", example = "60.00") BigDecimal purchasePrice,
        @Schema(description = "Total amount invested = quantity × purchasePrice", example = "600.00") BigDecimal investedAmount,
        @Schema(description = "Latest recorded market price per unit; null if no price snapshot exists", example = "72.50") BigDecimal currentPrice,
        @Schema(description = "Current market value = quantity × currentPrice; null if no price", example = "725.00") BigDecimal currentValue,
        @Schema(description = "Unrealised gain or loss = currentValue − investedAmount; null if no price", example = "125.00") BigDecimal gainLoss,
        @Schema(description = "Gain/loss as a percentage of invested amount; null if no price", example = "20.83") BigDecimal gainLossPercent,
        @Schema(description = "ISO 4217 currency code", example = "PLN") String currency,
        @Schema(description = "Date the position was opened", example = "2026-01-10") LocalDate purchasedAt,
        @Schema(description = "Position status: OPEN or CLOSED", example = "OPEN") String status,
        @Schema(description = "Sale price per unit; null when OPEN", example = "72.50") BigDecimal salePrice,
        @Schema(description = "Date the position was closed; null when OPEN", example = "2026-06-01") LocalDate soldAt
) {
}
