package org.dawid.cisowski.walletassistant.expenses.api;

import org.dawid.cisowski.walletassistant.expenses.ExpenseCategory;

import java.time.LocalDate;
import java.util.List;

public interface ExpensesFacade {

    List<ExpenseResponse> getExpenses(String userId, LocalDate from, LocalDate to);

    List<ExpenseResponse> getExpensesByCategory(String userId, ExpenseCategory category, LocalDate from, LocalDate to);

    MonthlyExpenseSummaryResponse getMonthlySummary(String userId, int year, int month);
}
