package org.dawid.cisowski.walletassistant.assets.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Single price snapshot for an asset symbol — one record per symbol per day")
public record AssetPriceResponse(
        @Schema(description = "Asset ticker or symbol", example = "XAU") String assetSymbol,
        @Schema(description = "Price per unit on this date", example = "2050.00") BigDecimal price,
        @Schema(description = "ISO 4217 currency code", example = "PLN") String currency,
        @Schema(description = "Date of this price snapshot", example = "2026-06-18") LocalDate priceDate
) {
}
