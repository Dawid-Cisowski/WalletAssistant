package org.dawid.cisowski.walletassistant.assets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Assets", description = "Query asset positions, portfolio P&L and price history")
@RestController
@RequestMapping("/v1/assets")
@RequiredArgsConstructor
class AssetsController {

    private final AssetsFacade assetsFacade;

    @Operation(
            summary = "Portfolio summary",
            description = "All open positions grouped by portfolio type (IKE/PERSONAL) with current value, gain/loss and totals. currentPrice/currentValue/gainLoss are null when no price snapshot exists for the symbol."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Portfolio summary with P&L"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @GetMapping("/portfolio")
    ResponseEntity<PortfolioSummaryResponse> getPortfolio(HttpServletRequest request) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(assetsFacade.getPortfolioSummary(userId)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Operation(summary = "List positions by status", description = "Returns OPEN or CLOSED positions. Defaults to OPEN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of asset positions"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @GetMapping("/positions")
    ResponseEntity<List<AssetPositionResponse>> getPositions(
            @Parameter(description = "Position status filter: OPEN or CLOSED", example = "OPEN")
            @RequestParam(defaultValue = "OPEN") String status,
            HttpServletRequest request
    ) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(resolvePositions(userId, status)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Operation(summary = "Price history for an asset symbol", description = "All recorded price snapshots for a given symbol, newest first. Used for charts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Price history ordered by date descending"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @GetMapping("/{assetSymbol}/prices")
    ResponseEntity<List<AssetPriceResponse>> getPriceHistory(
            @Parameter(description = "Asset symbol, e.g. XAU, BTC, PKNORLEN", example = "XAU")
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
