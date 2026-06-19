package org.dawid.cisowski.walletassistant.assets.api;

import java.util.List;

public interface AssetsFacade {

    PortfolioSummaryResponse getPortfolioSummary(String userId);

    List<AssetPositionResponse> getOpenPositions(String userId);

    List<AssetPositionResponse> getClosedPositions(String userId);

    List<AssetPriceResponse> getAssetPriceHistory(String userId, String assetSymbol);
}
