package org.dawid.cisowski.walletassistant.expenses;

import lombok.RequiredArgsConstructor;
import org.dawid.cisowski.walletassistant.expenses.api.ExpenseResponse;
import org.dawid.cisowski.walletassistant.expenses.api.ExpensesFacade;
import org.dawid.cisowski.walletassistant.expenses.api.MonthlyExpenseSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class ExpensesService implements ExpensesFacade {

    private final ExpensesRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(String userId, LocalDate from, LocalDate to) {
        return repository.findByUserIdAndOccurredDateBetween(userId, from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpensesByCategory(String userId, ExpenseCategory category, LocalDate from, LocalDate to) {
        return repository.findByUserIdAndCategoryAndOccurredDateBetween(userId, category.name(), from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyExpenseSummaryResponse getMonthlySummary(String userId, int year, int month) {
        var from = LocalDate.of(year, month, 1);
        var to = from.withDayOfMonth(from.lengthOfMonth());
        var expenses = repository.findByUserIdAndOccurredDateBetween(userId, from, to);

        var byCategory = expenses.stream().collect(Collectors.groupingBy(
                ExpenseProjectionJpaEntity::getCategory,
                LinkedHashMap::new,
                Collectors.reducing(BigDecimal.ZERO, ExpenseProjectionJpaEntity::getAmount, BigDecimal::add)
        ));

        var total = expenses.stream()
                .map(ExpenseProjectionJpaEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var currency = expenses.stream()
                .map(ExpenseProjectionJpaEntity::getCurrency)
                .findFirst()
                .orElse("PLN");

        return new MonthlyExpenseSummaryResponse(year, month, total, currency, byCategory, expenses.size());
    }

    private ExpenseResponse toResponse(ExpenseProjectionJpaEntity entity) {
        return new ExpenseResponse(
                entity.getExpenseId(),
                entity.getEventId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getCategory(),
                entity.getDescription(),
                entity.getMerchant(),
                entity.getAccountType(),
                entity.getOccurredAt(),
                entity.getOccurredDate()
        );
    }
}
