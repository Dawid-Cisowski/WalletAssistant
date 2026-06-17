package org.dawid.cisowski.walletassistant.investments;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dawid.cisowski.walletassistant.investments.api.InvestmentResponse;
import org.dawid.cisowski.walletassistant.investments.api.InvestmentsFacade;
import org.dawid.cisowski.walletassistant.investments.api.PortfolioSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/investments")
@RequiredArgsConstructor
class InvestmentsController {

    private final InvestmentsFacade investmentsFacade;

    @GetMapping("/portfolio")
    ResponseEntity<PortfolioSummaryResponse> getPortfolio(HttpServletRequest request) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(investmentsFacade.getPortfolioSummary(userId)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/{investmentType}/history")
    ResponseEntity<List<InvestmentResponse>> getHistory(
            @PathVariable String investmentType,
            HttpServletRequest request
    ) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(
                        investmentsFacade.getInvestmentHistory(userId, InvestmentType.fromString(investmentType))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private Optional<String> authenticated(HttpServletRequest request) {
        return Optional.ofNullable((String) request.getAttribute("deviceId"));
    }
}
