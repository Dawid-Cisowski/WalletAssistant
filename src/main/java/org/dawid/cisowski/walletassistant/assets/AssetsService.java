package org.dawid.cisowski.walletassistant.assets;

import lombok.RequiredArgsConstructor;
import org.dawid.cisowski.walletassistant.assets.api.AssetPositionResponse;
import org.dawid.cisowski.walletassistant.assets.api.AssetPriceResponse;
import org.dawid.cisowski.walletassistant.assets.api.AssetsFacade;
import org.dawid.cisowski.walletassistant.assets.api.PortfolioGroupResponse;
import org.dawid.cisowski.walletassistant.assets.api.PortfolioSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class AssetsService implements AssetsFacade {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DEFAULT_CURRENCY = "PLN";

    private final AssetPositionsRepository positionsRepository;
    private final AssetPriceHistoryRepository priceHistoryRepository;

    @Override
    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getPortfolioSummary(String userId) {
        var openPositions = enrichedOpenPositions(userId);

        var groups = openPositions.stream()
                .collect(Collectors.groupingBy(
                        AssetPositionResponse::portfolioType,
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> toGroup(entry.getKey(), entry.getValue()))
                .toList();

        var totalInvested = sumInvested(openPositions);
        var totalCurrentValue = sumCurrentValue(openPositions);
        var totalGainLoss = totalCurrentValue.subtract(totalInvested).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new PortfolioSummaryResponse(
                totalCurrentValue,
                totalInvested,
                totalGainLoss,
                percentOf(totalGainLoss, totalInvested),
                resolveCurrency(openPositions),
                LocalDate.now(WARSAW),
                groups
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetPositionResponse> getOpenPositions(String userId) {
        return enrichedOpenPositions(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetPositionResponse> getClosedPositions(String userId) {
        return positionsRepository
                .findByUserIdAndStatusOrderByPurchasedAtDesc(userId, PositionStatus.CLOSED.name()).stream()
                .map(this::toClosedResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetPriceResponse> getAssetPriceHistory(String userId, String assetSymbol) {
        return priceHistoryRepository
                .findByUserIdAndAssetSymbolOrderByPriceDateDesc(userId, assetSymbol.toUpperCase()).stream()
                .map(this::toPriceResponse)
                .toList();
    }

    private List<AssetPositionResponse> enrichedOpenPositions(String userId) {
        var openPositions = positionsRepository
                .findByUserIdAndStatusOrderByPurchasedAtDesc(userId, PositionStatus.OPEN.name());

        var latestPriceBySymbol = openPositions.stream()
                .map(AssetPositionJpaEntity::assetSymbol)
                .distinct()
                .map(symbol -> priceHistoryRepository
                        .findTopByUserIdAndAssetSymbolOrderByPriceDateDesc(userId, symbol))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(
                        AssetPriceHistoryJpaEntity::assetSymbol,
                        AssetPriceHistoryJpaEntity::price,
                        (first, second) -> first,
                        LinkedHashMap::new));

        return openPositions.stream()
                .map(position -> toOpenResponse(position, latestPriceBySymbol))
                .toList();
    }

    private PortfolioGroupResponse toGroup(String portfolioType, List<AssetPositionResponse> positions) {
        var invested = sumInvested(positions);
        var currentValue = sumCurrentValue(positions);
        var gainLoss = currentValue.subtract(invested).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new PortfolioGroupResponse(
                portfolioType,
                currentValue,
                invested,
                gainLoss,
                percentOf(gainLoss, invested),
                resolveCurrency(positions),
                positions
        );
    }

    private AssetPositionResponse toOpenResponse(AssetPositionJpaEntity entity, Map<String, BigDecimal> latestPriceBySymbol) {
        var investedAmount = entity.quantity().multiply(entity.purchasePrice())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        var currentPrice = Optional.ofNullable(latestPriceBySymbol.get(entity.assetSymbol()));
        var currentValue = currentPrice
                .map(price -> entity.quantity().multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        var gainLoss = currentValue
                .map(value -> value.subtract(investedAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        var gainLossPercent = gainLoss.map(loss -> percentOrNull(loss, investedAmount));

        return new AssetPositionResponse(
                entity.positionId(),
                entity.portfolioType(),
                entity.assetSymbol(),
                entity.assetType(),
                entity.assetName(),
                entity.quantity(),
                entity.purchasePrice(),
                investedAmount,
                currentPrice.orElse(null),
                currentValue.orElse(null),
                gainLoss.orElse(null),
                gainLossPercent.orElse(null),
                entity.currency(),
                entity.purchasedAt(),
                entity.status(),
                entity.salePrice(),
                entity.soldAt()
        );
    }

    private AssetPositionResponse toClosedResponse(AssetPositionJpaEntity entity) {
        var investedAmount = entity.quantity().multiply(entity.purchasePrice())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        var salePrice = Optional.ofNullable(entity.salePrice());
        var currentValue = salePrice
                .map(price -> entity.quantity().multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        var gainLoss = currentValue
                .map(value -> value.subtract(investedAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        var gainLossPercent = gainLoss.map(loss -> percentOrNull(loss, investedAmount));

        return new AssetPositionResponse(
                entity.positionId(),
                entity.portfolioType(),
                entity.assetSymbol(),
                entity.assetType(),
                entity.assetName(),
                entity.quantity(),
                entity.purchasePrice(),
                investedAmount,
                entity.salePrice(),
                currentValue.orElse(null),
                gainLoss.orElse(null),
                gainLossPercent.orElse(null),
                entity.currency(),
                entity.purchasedAt(),
                entity.status(),
                entity.salePrice(),
                entity.soldAt()
        );
    }

    private AssetPriceResponse toPriceResponse(AssetPriceHistoryJpaEntity entity) {
        return new AssetPriceResponse(
                entity.assetSymbol(),
                entity.price(),
                entity.currency(),
                entity.priceDate()
        );
    }

    private BigDecimal sumInvested(List<AssetPositionResponse> positions) {
        return positions.stream()
                .map(AssetPositionResponse::investedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sumCurrentValue(List<AssetPositionResponse> positions) {
        return positions.stream()
                .map(position -> Optional.ofNullable(position.currentValue())
                        .orElse(position.investedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percentOf(BigDecimal gainLoss, BigDecimal invested) {
        return Optional.ofNullable(percentOrNull(gainLoss, invested)).orElse(BigDecimal.ZERO);
    }

    private BigDecimal percentOrNull(BigDecimal gainLoss, BigDecimal invested) {
        if (invested.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return gainLoss.multiply(HUNDRED).divide(invested, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private String resolveCurrency(List<AssetPositionResponse> positions) {
        return positions.stream()
                .map(AssetPositionResponse::currency)
                .findFirst()
                .orElse(DEFAULT_CURRENCY);
    }
}
