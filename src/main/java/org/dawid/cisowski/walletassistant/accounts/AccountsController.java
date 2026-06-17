package org.dawid.cisowski.walletassistant.accounts;

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

@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
class AccountsController {

    private final AccountsFacade accountsFacade;

    @GetMapping("/balances")
    ResponseEntity<List<AccountBalanceResponse>> getCurrentBalances(HttpServletRequest request) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(accountsFacade.getCurrentBalances(userId)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/{accountType}/history")
    ResponseEntity<List<AccountBalanceResponse>> getBalanceHistory(
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
