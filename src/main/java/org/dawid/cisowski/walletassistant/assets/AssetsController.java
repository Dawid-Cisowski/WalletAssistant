package org.dawid.cisowski.walletassistant.assets;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dawid.cisowski.walletassistant.assets.api.AssetPositionResponse;
import org.dawid.cisowski.walletassistant.assets.api.AssetPriceResponse;
import org.dawid.cisowski.walletassistant.assets.api.AssetsFacade;
import org.dawid.cisowski.walletassistant.assets.api.PortfolioSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/assets")
@RequiredArgsConstructor
class AssetsController {

    private final AssetsFacade assetsFacade;

    @GetMapping("/portfolio")
    ResponseEntity<PortfolioSummaryResponse> getPortfolio(HttpServletRequest request) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(assetsFacade.getPortfolioSummary(userId)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/positions")
    ResponseEntity<List<AssetPositionResponse>> getPositions(
            @RequestParam(defaultValue = "OPEN") String status,
            HttpServletRequest request
    ) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(resolvePositions(userId, status)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/{assetSymbol}/prices")
    ResponseEntity<List<AssetPriceResponse>> getPriceHistory(
            @PathVariable String assetSymbol,
            HttpServletRequest request
    ) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(assetsFacade.getAssetPriceHistory(userId, assetSymbol)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private List<AssetPositionResponse> resolvePositions(String userId, String status) {
        return switch (PositionStatus.valueOf(status.toUpperCase())) {
            case OPEN -> assetsFacade.getOpenPositions(userId);
            case CLOSED -> assetsFacade.getClosedPositions(userId);
        };
    }

    private Optional<String> authenticated(HttpServletRequest request) {
        return Optional.ofNullable((String) request.getAttribute("deviceId"));
    }
}
