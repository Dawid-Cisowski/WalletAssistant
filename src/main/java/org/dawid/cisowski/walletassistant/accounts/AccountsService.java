package org.dawid.cisowski.walletassistant.accounts;

import lombok.RequiredArgsConstructor;
import org.dawid.cisowski.walletassistant.accounts.api.AccountBalanceResponse;
import org.dawid.cisowski.walletassistant.accounts.api.AccountsFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class AccountsService implements AccountsFacade {

    private final AccountsRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<AccountBalanceResponse> getCurrentBalances(String userId) {
        Collection<BalanceSnapshotJpaEntity> latestPerType = repository.findByUserIdOrderByRecordedDateDesc(userId).stream()
                .collect(Collectors.toMap(
                        BalanceSnapshotJpaEntity::accountType,
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new))
                .values();
        return latestPerType.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountBalanceResponse> getBalanceHistory(String userId, AccountType accountType) {
        return repository.findByUserIdOrderByRecordedDateDesc(userId).stream()
                .filter(entity -> entity.accountType().equals(accountType.name()))
                .map(this::toResponse)
                .toList();
    }

    private AccountBalanceResponse toResponse(BalanceSnapshotJpaEntity entity) {
        return new AccountBalanceResponse(
                entity.snapshotId(),
                entity.accountType(),
                entity.accountName(),
                entity.balance(),
                entity.currency(),
                entity.recordedDate()
        );
    }
}
