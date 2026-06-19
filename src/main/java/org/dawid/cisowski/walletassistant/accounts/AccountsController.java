package org.dawid.cisowski.walletassistant.accounts;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dawid.cisowski.walletassistant.accounts.api.AccountBalanceResponse;
import org.dawid.cisowski.walletassistant.accounts.api.AccountsFacade;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@Tag(name = "Accounts", description = "Query cash account balance snapshots")
@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
class AccountsController {

    private final AccountsFacade accountsFacade;

    @Operation(summary = "Current account balances", description = "Returns the latest balance snapshot for each account type")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Latest balance per account type"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @GetMapping("/balances")
    ResponseEntity<List<AccountBalanceResponse>> getCurrentBalances(HttpServletRequest request) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(accountsFacade.getCurrentBalances(userId)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Operation(summary = "Balance history for an account type", description = "All balance snapshots for the given account type, newest first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance snapshots ordered by date descending"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @GetMapping("/{accountType}/history")
    ResponseEntity<List<AccountBalanceResponse>> getBalanceHistory(
            @Parameter(description = "Account type: PERSONAL_SAVINGS or PERSONAL_SPENDING", example = "PERSONAL_SAVINGS")
            @PathVariable String accountType,
            HttpServletRequest request
    ) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(
                        accountsFacade.getBalanceHistory(userId, AccountType.fromString(accountType))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private Optional<String> authenticated(HttpServletRequest request) {
        return Optional.ofNullable((String) request.getAttribute("deviceId"));
    }
}
