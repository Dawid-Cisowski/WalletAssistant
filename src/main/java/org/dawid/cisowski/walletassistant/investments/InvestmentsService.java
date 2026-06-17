package org.dawid.cisowski.walletassistant.investments;

import lombok.RequiredArgsConstructor;
import org.dawid.cisowski.walletassistant.investments.api.InvestmentResponse;
import org.dawid.cisowski.walletassistant.investments.api.InvestmentsFacade;
import org.dawid.cisowski.walletassistant.investments.api.PortfolioSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class InvestmentsService implements InvestmentsFacade {

    private static final String DEFAULT_CURRENCY = "PLN";
    private static final int PERCENT_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InvestmentsRepository repository;

    @Override
    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getPortfolioSummary(String userId) {
        List<InvestmentResponse> latestPerType = latestSnapshotsPerType(userId);

        BigDecimal totalValue = latestPerType.stream()
                .map(InvestmentResponse::currentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInvested = latestPerType.stream()
                .map(InvestmentResponse::investedAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGainLoss = totalValue.subtract(totalInvested);
        BigDecimal totalGainLossPercent = percentOf(totalGainLoss, totalInvested);

        String currency = latestPerType.stream()
                .map(InvestmentResponse::currency)
                .findFirst()
                .orElse(DEFAULT_CURRENCY);

        LocalDate asOf = latestPerType.stream()
                .map(InvestmentResponse::recordedDate)
                .max(Comparator.naturalOrder())
                .orElseGet(LocalDate::now);

        return new PortfolioSummaryResponse(
                totalValue,
                totalInvested,
                totalGainLoss,
                totalGainLossPercent,
                latestPerType,
                currency,
                asOf
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvestmentResponse> getInvestmentHistory(String userId, InvestmentType investmentType) {
        return repository.findByUserIdAndInvestmentTypeOrderByRecordedDateDesc(userId, investmentType.name()).stream()
                .map(this::toResponse)
                .toList();
    }

    private List<InvestmentResponse> latestSnapshotsPerType(String userId) {
        return repository.findByUserIdOrderByRecordedDateDesc(userId).stream()
                .collect(LinkedHashMap<String, InvestmentSnapshotJpaEntity>::new,
                        (map, entity) -> map.putIfAbsent(entity.getInvestmentType(), entity),
                        LinkedHashMap::putAll)
                .values().stream()
                .map(this::toResponse)
                .toList();
    }

    private InvestmentResponse toResponse(InvestmentSnapshotJpaEntity entity) {
        BigDecimal investedAmount = entity.getInvestedAmount();
        BigDecimal gainLoss = Optional.ofNullable(investedAmount)
                .map(entity.getCurrentValue()::subtract)
                .orElse(null);
        BigDecimal gainLossPercent = gainLoss == null ? null : percentOf(gainLoss, investedAmount);

        return new InvestmentResponse(
                entity.getSnapshotId(),
                entity.getInvestmentType(),
                entity.getInvestmentName(),
                entity.getCurrentValue(),
                investedAmount,
                gainLoss,
                gainLossPercent,
                entity.getCurrency(),
                entity.getRecordedDate()
        );
    }

    private BigDecimal percentOf(BigDecimal gainLoss, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return gainLoss.multiply(HUNDRED).divide(base, PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
