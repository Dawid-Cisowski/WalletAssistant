package org.dawid.cisowski.walletassistant.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.accounts.api.AccountBalanceResponse;
import org.dawid.cisowski.walletassistant.accounts.api.AccountsFacade;
import org.dawid.cisowski.walletassistant.config.AppProperties;
import org.dawid.cisowski.walletassistant.expenses.api.ExpenseResponse;
import org.dawid.cisowski.walletassistant.expenses.api.ExpensesFacade;
import org.dawid.cisowski.walletassistant.expenses.api.MonthlyExpenseSummaryResponse;
import org.dawid.cisowski.walletassistant.assets.api.AssetsFacade;
import org.dawid.cisowski.walletassistant.assets.api.PortfolioSummaryResponse;
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
    private final AssetsFacade assetsFacade;
    private final WalletEventsFacade walletEventsFacade;
    private final AppProperties appProperties;

    @Tool(description = "Record an expense. Category must be one of: DINING_OUT (restaurants, takeaway), GROCERIES (food bought for home), TRANSPORT (fuel, parking, tickets, car), HOME_SUPPLIES (cleaning products, toilet paper, household items), ENTERTAINMENT, SUBSCRIPTIONS (Netflix, YouTube etc.), HEALTH (doctors, medicine, tests), EDUCATION (courses, books), KIDS_TOYS, CLOTHING, OTHER. Account type must be one of: BUSINESS, PERSONAL_SAVINGS, PERSONAL_SPENDING.")
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

    @Tool(description = "Correct an existing expense. Provide the expenseId of the expense to update (get it from getExpenses), then supply all fields with corrected values — all fields will be overwritten. Category must be one of: DINING_OUT (restaurants, takeaway), GROCERIES (food bought for home), TRANSPORT (fuel, parking, tickets, car), HOME_SUPPLIES (cleaning products, toilet paper, household items), ENTERTAINMENT, SUBSCRIPTIONS (Netflix, YouTube etc.), HEALTH (doctors, medicine, tests), EDUCATION (courses, books), KIDS_TOYS, CLOTHING, OTHER. Account type must be one of: BUSINESS, PERSONAL_SAVINGS, PERSONAL_SPENDING.")
    String correctExpense(
            @ToolParam(description = "expenseId of the expense to correct (from getExpenses)") String expenseId,
            @ToolParam(description = "Short human-readable description of the expense") String description,
            @ToolParam(description = "Corrected amount, positive number") BigDecimal amount,
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
        payload.put("expenseId", expenseId);
        payload.put("amount", amount.toPlainString());
        payload.put("currency", normalizedCurrency(currency));
        payload.put("category", category);
        payload.put("description", description);
        payload.put("merchant", merchant);
        payload.put("accountType", accountType);
        payload.put("date", date);

        var result = store("ExpenseCorrected.v1", occurredAt, payload, getDeviceId(toolContext));
        return confirmation(result, "Expense corrected: %s %s for %s".formatted(
                amount.toPlainString(), normalizedCurrency(currency), description));
    }

    @Tool(description = "Delete an expense by its expenseId. Get the expenseId from getExpenses. This action cannot be undone.")
    String deleteExpense(
            @ToolParam(description = "expenseId of the expense to delete (from getExpenses)") String expenseId,
            ToolContext toolContext
    ) {
        var payload = new HashMap<String, Object>();
        payload.put("expenseId", expenseId);

        var result = store("ExpenseDeleted.v1", Instant.now(), payload, getDeviceId(toolContext));
        return confirmation(result, "Expense deleted (expenseId=" + expenseId + ")");
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

    @Tool(description = """
            Open an asset position (record a purchase). \
            portfolioType: IKE | PERSONAL. \
            assetType: STOCK | ETF | GOLD | CRYPTO | OTHER. \
            assetSymbol: ticker or short code, e.g. PKNORLEN, XAU, BTC.""")
    String openAssetPosition(
            @ToolParam(description = "Portfolio type: IKE or PERSONAL") String portfolioType,
            @ToolParam(description = "Asset symbol, e.g. XAU, BTC, PKNORLEN") String assetSymbol,
            @ToolParam(description = "Asset type: STOCK | ETF | GOLD | CRYPTO | OTHER") String assetType,
            @ToolParam(description = "Human-readable asset name, e.g. Złoto, Bitcoin") String assetName,
            @ToolParam(description = "Quantity purchased (supports up to 8 decimal places)") BigDecimal quantity,
            @ToolParam(description = "Purchase price per unit") BigDecimal purchasePrice,
            @ToolParam(description = "ISO 4217 currency code, e.g. PLN") String currency,
            @ToolParam(description = "Purchase date in ISO format YYYY-MM-DD") String purchaseDate,
            ToolContext toolContext
    ) {
        var occurredAt = toInstant(purchaseDate, null);
        var payload = new HashMap<String, Object>();
        payload.put("positionId", UUID.randomUUID().toString());
        payload.put("portfolioType", portfolioType);
        payload.put("assetSymbol", assetSymbol.toUpperCase());
        payload.put("assetType", assetType);
        payload.put("assetName", assetName);
        payload.put("quantity", quantity.toPlainString());
        payload.put("purchasePrice", purchasePrice.toPlainString());
        payload.put("currency", normalizedCurrency(currency));
        payload.put("purchaseDate", purchaseDate);

        var result = store("AssetPositionOpened.v1", occurredAt, payload, getDeviceId(toolContext));
        return confirmation(result, "Position opened: %s x%s @ %s %s".formatted(
                assetName, quantity.toPlainString(), purchasePrice.toPlainString(), normalizedCurrency(currency)));
    }

    @Tool(description = "Close an asset position (record a sale). Supports partial close — quantity can be less than original.")
    String closeAssetPosition(
            @ToolParam(description = "positionId of the open position being closed") String openPositionId,
            @ToolParam(description = "Quantity being sold") BigDecimal quantity,
            @ToolParam(description = "Sale price per unit") BigDecimal salePrice,
            @ToolParam(description = "ISO 4217 currency code, e.g. PLN") String currency,
            @ToolParam(description = "Sale date in ISO format YYYY-MM-DD") String saleDate,
            ToolContext toolContext
    ) {
        var occurredAt = toInstant(saleDate, null);
        var payload = new HashMap<String, Object>();
        payload.put("openPositionId", openPositionId);
        payload.put("quantity", quantity.toPlainString());
        payload.put("salePrice", salePrice.toPlainString());
        payload.put("currency", normalizedCurrency(currency));
        payload.put("saleDate", saleDate);

        var result = store("AssetPositionClosed.v1", occurredAt, payload, getDeviceId(toolContext));
        return confirmation(result, "Position closed: %s units @ %s %s".formatted(
                quantity.toPlainString(), salePrice.toPlainString(), normalizedCurrency(currency)));
    }

    @Tool(description = "Record a price snapshot for an asset symbol. Used for P&L calculation and charts.")
    String recordAssetPrice(
            @ToolParam(description = "Asset symbol, e.g. XAU, BTC, PKNORLEN") String assetSymbol,
            @ToolParam(description = "Current price per unit") BigDecimal price,
            @ToolParam(description = "ISO 4217 currency code, e.g. PLN") String currency,
            @ToolParam(description = "Price date in ISO format YYYY-MM-DD") String priceDate,
            ToolContext toolContext
    ) {
        var occurredAt = toInstant(priceDate, null);
        var payload = new HashMap<String, Object>();
        payload.put("assetSymbol", assetSymbol.toUpperCase());
        payload.put("price", price.toPlainString());
        payload.put("currency", normalizedCurrency(currency));
        payload.put("priceDate", priceDate);

        var result = store("AssetPriceSnapshotRecorded.v1", occurredAt, payload, getDeviceId(toolContext));
        return confirmation(result, "Price recorded for %s: %s %s".formatted(
                assetSymbol.toUpperCase(), price.toPlainString(), normalizedCurrency(currency)));
    }

    @Tool(description = "Get portfolio summary: all open positions with current value, P&L per position and totals per portfolio type (IKE/PERSONAL).")
    PortfolioSummaryResponse getPortfolioSummary(ToolContext toolContext) {
        return assetsFacade.getPortfolioSummary(getDeviceId(toolContext));
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
                .orElseGet(() -> appProperties.getApiKey().getDeviceId());
    }
}
