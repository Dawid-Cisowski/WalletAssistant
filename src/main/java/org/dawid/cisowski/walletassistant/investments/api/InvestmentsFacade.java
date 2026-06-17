package org.dawid.cisowski.walletassistant.investments.api;

import org.dawid.cisowski.walletassistant.investments.InvestmentType;

import java.util.List;

public interface InvestmentsFacade {

    PortfolioSummaryResponse getPortfolioSummary(String userId);

    List<InvestmentResponse> getInvestmentHistory(String userId, InvestmentType investmentType);
}
