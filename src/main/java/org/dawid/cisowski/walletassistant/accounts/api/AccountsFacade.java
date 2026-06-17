package org.dawid.cisowski.walletassistant.accounts.api;

import org.dawid.cisowski.walletassistant.accounts.AccountType;

import java.util.List;

public interface AccountsFacade {

    List<AccountBalanceResponse> getCurrentBalances(String userId);

    List<AccountBalanceResponse> getBalanceHistory(String userId, AccountType accountType);
}
