package org.dawid.cisowski.walletassistant.expenses;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Expenses", description = "Query projected expense records")
@RestController
@RequestMapping("/v1/expenses")
@RequiredArgsConstructor
class ExpensesController {

    private final ExpensesFacade expensesFacade;

    @Operation(summary = "List expenses by date range")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Expenses in the given range, ordered by date descending"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @GetMapping
    ResponseEntity<List<ExpenseResponse>> getExpenses(
            @Parameter(description = "Start date (inclusive), YYYY-MM-DD", example = "2026-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date (inclusive), YYYY-MM-DD", example = "2026-01-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request
    ) {
        return authenticated(request)
                .map(userId -> ResponseEntity.ok(expensesFacade.getExpenses(userId, from, to)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Operation(summary = "Monthly expense summary", description = "Returns total amount and per-category breakdown for a given month")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Monthly summary with category totals"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @GetMapping("/summary/monthly")
    ResponseEntity<MonthlyExpenseSummaryResponse> getMonthlySummary(
            @Parameter(description = "Year", example = "2026") @RequestParam int year,
            @Parameter(description = "Month (1–12)", example = "6") @RequestParam int month,
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
