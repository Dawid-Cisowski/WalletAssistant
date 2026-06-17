package org.dawid.cisowski.walletassistant.expenses;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dawid.cisowski.walletassistant.expenses.api.ExpenseResponse;
import org.dawid.cisowski.walletassistant.expenses.api.ExpensesFacade;
import org.dawid.cisowski.walletassistant.expenses.api.MonthlyExpenseSummaryResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/expenses")
@RequiredArgsConstructor
class ExpensesController {

    private final ExpensesFacade expensesFacade;

    @GetMapping
    ResponseEntity<List<ExpenseResponse>> getExpenses(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request
    ) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(expensesFacade.getExpenses(userId, from, to)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/summary/monthly")
    ResponseEntity<MonthlyExpenseSummaryResponse> getMonthlySummary(
            @RequestParam int year,
            @RequestParam int month,
            HttpServletRequest request
    ) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(expensesFacade.getMonthlySummary(userId, year, month)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private Optional<String> authenticated(HttpServletRequest request) {
        return Optional.ofNullable((String) request.getAttribute("deviceId"));
    }
}
