package org.dawid.cisowski.walletassistant.assets.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetPriceResponse(
        String assetSymbol,
        BigDecimal price,
        String currency,
        LocalDate priceDate
) {
}
