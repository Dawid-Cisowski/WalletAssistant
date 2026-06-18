package org.dawid.cisowski.walletassistant.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.accounts.api.AccountBalanceResponse;
import org.dawid.cisowski.walletassistant.accounts.api.AccountsFacade;
import org.dawid.cisowski.walletassistant.config.AppProperties;
import org.dawid.cisowski.walletassistant.expenses.api.ExpenseResponse;
import org.dawid.cisowski.walletassistant.expenses.api.ExpensesFacade;
import org.dawid.cisowski.walletassistant.expenses.api.MonthlyExpenseSummaryResponse;
import org.dawid.cisowski.walletassistant.investments.api.InvestmentsFacade;
import org.dawid.cisowski.walletassistant.investments.api.PortfolioSummaryResponse;
import org.dawid.cisowski.walletassistant.walletevents.api.WalletEventsFacade;
import org.dawid.cisowski.walletassistant.walletevents.api.WalletEventsFacade.EventEnvelope;
import org.dawid.cisowski.walletassistant.walletevents.api.WalletEventsFacade.StoreEventsCommand;
import org.dawid.cisowski.walletassistant.walletevents.api.WalletEventsFacade.StoreEventsResult;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
class WalletTools {

    private static final String TOOL_CONTEXT_DEVICE_ID = "deviceId";
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final ExpensesFacade expensesFacade;
    private final AccountsFacade accountsFacade;
    private final InvestmentsFacade investmentsFacade;
    private final WalletEventsFacade walletEventsFacade;
    private final AppProperties appProperties;

    @Tool(description = "Record an expense. Category must be one of: FOOD_AND_DRINKS, TRANSPORT, SHOPPING, ENTERTAINMENT, SUBSCRIPTIONS, HEALTH, HOUSING, UTILITIES, EDUCATION, TRAVEL, BUSINESS, OTHER. Account type must be one of: BUSINESS, PERSONAL_SAVINGS, PERSONAL_SPENDING.")
    String recordExpense(
            @ToolParam(description = "Short human-readable description of the expense") String description,
            @ToolParam(description = "Expense amount, positive number") BigDecimal amount,
            @ToolParam(description = "ISO 4217 currency code, e.g. PLN") String currency,
            @ToolParam(description = "Expense category enum name") String category,
            @ToolParam(description = "Merchant or vendor name", required = false) String merchant,
            @ToolParam(description = "Account type enum name") String accountType,
            @ToolParam(description = "Date in ISO format YYYY-MM-DD") String date,
            @ToolParam(description = "Time in ISO format HH:mm, optional", required = false) String time,
            ToolContext toolContext
    ) {
        var occurredAt = toInstant(date, time);
        var payload = new HashMap<String, Object>();
        payload.put("amount", amount.toPlainString());
        payload.put("currency", normalizedCurrency(currency));
        payload.put("category", category);
        payload.put("description", description);
        payload.put("merchant", merchant);
        payload.put("accountType", accountType);
        payload.put("date", date);

        var result = store("ExpenseRecorded.v1", occurredAt, payload, getDeviceId(toolContext));
        return confirmation(result, "Expense recorded: %s %s for %s".formatted(
                amount.toPlainString(), normalizedCurrency(currency), description));
    }

    @Tool(description = "Get expenses for a date range. Dates in ISO format (YYYY-MM-DD).")
    List<ExpenseResponse> getExpenses(
            @ToolParam(description = "Start date YYYY-MM-DD") String fromDate,
            @ToolParam(description = "End date YYYY-MM-DD") String toDate,
            ToolContext toolContext
    ) {
        return expensesFacade.getExpenses(getDeviceId(toolContext), LocalDate.parse(fromDate), LocalDate.parse(toDate));
    }

    @Tool(description = "Get monthly expense summary with breakdown by category.")
    MonthlyExpenseSummaryResponse getMonthlySummary(
            @ToolParam(description = "Year, e.g. 2026") int year,
            @ToolParam(description = "Month number 1-12") int month,
            ToolContext toolContext
    ) {
        return expensesFacade.getMonthlySummary(getDeviceId(toolContext), year, month);
    }

    @Tool(description = "Record current account balance snapshot. Account type must be one of: BUSINESS, PERSONAL_SAVINGS, PERSONAL_SPENDING.")
    String recordAccountBalance(
            @ToolParam(description = "Account type enum name") String accountType,
            @ToolParam(description = "Human-readable account name") String accountName,
            @ToolParam(description = "Current balance") BigDecimal balance,
            @ToolParam(description = "ISO 4217 currency code, e.g. PLN") String currency,
            @ToolParam(description = "Date in ISO format YYYY-MM-DD") String date,
            ToolContext toolContext
    ) {
        var occurredAt = toInstant(date, null);
        var payload = new HashMap<String, Object>();
        payload.put("accountType", accountType);
        payload.put("accountName", accountName);
        payload.put("balance", balance.toPlainString());
        payload.put("currency", normalizedCurrency(currency));
        payload.put("date", date);

        var result = store("AccountBalanceSnapshotRecorded.v1", occurredAt, payload, getDeviceId(toolContext));
        return confirmation(result, "Balance recorded for %s: %s %s".formatted(
                accountName, balance.toPlainString(), normalizedCurrency(currency)));
    }

    @Tool(description = "Get current balances for all accounts.")
    List<AccountBalanceResponse> getCurrentBalances(ToolContext toolContext) {
        return accountsFacade.getCurrentBalances(getDeviceId(toolContext));
    }

    @Tool(description = "Record investment portfolio snapshot. Investment type must be one of: IKE, XTB_STOCKS, XTB_ETF, SAVINGS_ACCOUNT, CRYPTO, OTHER.")
    String recordInvestmentSnapshot(
            @ToolParam(description = "Investment type enum name") String investmentType,
            @ToolParam(description = "Human-readable investment name") String investmentName,
            @ToolParam(description = "Current market value") BigDecimal currentValue,
            @ToolParam(description = "Total invested amount", required = false) BigDecimal investedAmount,
            @ToolParam(description = "ISO 4217 currency code, e.g. PLN") String currency,
            @ToolParam(description = "Date in ISO format YYYY-MM-DD") String date,
            ToolContext toolContext
    ) {
        var occurredAt = toInstant(date, null);
        var payload = new HashMap<String, Object>();
        payload.put("investmentType", investmentType);
        payload.put("investmentName", investmentName);
        payload.put("currentValue", currentValue.toPlainString());
        payload.put("investedAmount", Optional.ofNullable(investedAmount).map(BigDecimal::toPlainString).orElse(null));
        payload.put("currency", normalizedCurrency(currency));
        payload.put("date", date);

        var result = store("InvestmentSnapshotRecorded.v1", occurredAt, payload, getDeviceId(toolContext));
        return confirmation(result, "Investment snapshot recorded for %s: %s %s".formatted(
                investmentName, currentValue.toPlainString(), normalizedCurrency(currency)));
    }

    @Tool(description = "Get current investment portfolio summary with total value and gain/loss.")
    PortfolioSummaryResponse getPortfolioSummary(ToolContext toolContext) {
        return investmentsFacade.getPortfolioSummary(getDeviceId(toolContext));
    }

    private StoreEventsResult store(String eventType, Instant occurredAt, Map<String, Object> payload, String userId) {
        var envelope = new EventEnvelope(UUID.randomUUID().toString(), eventType, occurredAt, payload);
        return walletEventsFacade.storeEvents(new StoreEventsCommand(userId, List.of(envelope)));
    }

    private String confirmation(StoreEventsResult result, String successMessage) {
        return result.results().stream()
                .findFirst()
                .map(eventResult -> switch (eventResult.status()) {
                    case STORED -> successMessage + " (eventId=" + eventResult.eventId() + ")";
                    case DUPLICATE -> "Already recorded (eventId=" + eventResult.eventId() + ")";
                    case INVALID -> "Could not record: " + eventResult.errorMessage();
                })
                .orElse("No event was processed");
    }

    private Instant toInstant(String date, String time) {
        var localDate = LocalDate.parse(date);
        var localTime = Optional.ofNullable(time)
                .filter(value -> !value.isBlank())
                .map(LocalTime::parse)
                .orElse(LocalTime.NOON);
        return localDate.atTime(localTime).atZone(ZONE).toInstant();
    }

    private String normalizedCurrency(String currency) {
        return Optional.ofNullable(currency)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .orElse("PLN");
    }

    private String getDeviceId(ToolContext toolContext) {
        return McpToolUtils.getMcpExchange(toolContext)
                .map(exchange -> exchange.transportContext().get(TOOL_CONTEXT_DEVICE_ID))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(s -> !s.isBlank())
                .orElse(appProperties.getApiKey().getDeviceId());
    }
}
