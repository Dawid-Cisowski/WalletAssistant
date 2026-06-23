package org.dawid.cisowski.walletassistant

import spock.lang.Title

@Title("Feature: Expense Tracking")
class ExpensesSpec extends BaseIntegrationSpec {

    static final String DEVICE_ID = "test-expenses"
    static final String SECRET = TEST_SECRET_BASE64

    def setup() {
        jdbcTemplate.update("DELETE FROM expense_projections WHERE user_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM expense_projections WHERE user_id = ?", DIFFERENT_DEVICE_ID)
        jdbcTemplate.update("DELETE FROM wallet_events WHERE user_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM wallet_events WHERE user_id = ?", DIFFERENT_DEVICE_ID)
    }

    private Map expensePayload(Map overrides) {
        def base = [
                expenseId  : UUID.randomUUID().toString(),
                amount     : 26.00,
                currency   : "PLN",
                category   : "DINING_OUT",
                description: "Kebab",
                merchant   : "Kebab Shop",
                accountType: "PERSONAL_SPENDING",
                occurredAt : "2026-06-10T12:00:00Z"
        ]
        base.putAll(overrides)
        return base
    }

    private void submitExpense(String deviceId, String secret, Map overrides, String idempotencyKey = UUID.randomUUID().toString()) {
        submitEvent(deviceId, secret, "EXPENSE_RECORDED", idempotencyKey, expensePayload(overrides))
    }

    private def getExpenses(String from, String to) {
        def path = "/v1/expenses?from=${from}&to=${to}"
        return authenticatedGet(DEVICE_ID, SECRET, path).get(path)
    }

    def "recording an expense stores it and makes it retrievable"() {
        given: "an EXPENSE_RECORDED event for a kebab"
        submitExpense(DEVICE_ID, SECRET, [
                amount     : 26.00,
                category   : "DINING_OUT",
                accountType: "PERSONAL_SPENDING",
                occurredAt : "2026-06-10T12:00:00Z"
        ])

        and: "the projection has been applied"
        awaitProjection { getExpenses("2026-06-01", "2026-06-30").jsonPath().getList("").size() > 0 }

        when: "expenses for June 2026 are requested"
        def response = getExpenses("2026-06-01", "2026-06-30")

        then: "the expense is returned with the recorded amount and category"
        response.statusCode() == 200
        def expenses = response.jsonPath().getList("")
        expenses.size() == 1
        response.jsonPath().getDouble("[0].amount") == 26.0
        response.jsonPath().getString("[0].category") == "DINING_OUT"
    }

    def "submitting duplicate idempotencyKey returns DUPLICATE status"() {
        given: "a fixed idempotency key"
        def key = "expenses-dup-key-fixed"

        when: "the same event is submitted twice"
        def firstStatus = submitEvent(DEVICE_ID, SECRET, "EXPENSE_RECORDED", key, expensePayload([:]))
        def secondStatus = submitEvent(DEVICE_ID, SECRET, "EXPENSE_RECORDED", key, expensePayload([:]))

        then: "the first is stored and the second is reported as a duplicate"
        firstStatus == "STORED"
        secondStatus == "DUPLICATE"
    }

    def "expenses from other users are not visible"() {
        given: "an expense recorded for a different device"
        submitExpense(DIFFERENT_DEVICE_ID, DIFFERENT_SECRET_BASE64, [
                amount     : 999.00,
                description: "Other user expense",
                occurredAt : "2026-06-12T12:00:00Z"
        ])

        and: "time for any projection to settle"
        awaitProjection {
            def path = "/v1/expenses?from=2026-06-01&to=2026-06-30"
            authenticatedGet(DIFFERENT_DEVICE_ID, DIFFERENT_SECRET_BASE64, path).get(path)
                    .jsonPath().getList("").size() > 0
        }

        when: "the test device requests its expenses"
        def response = getExpenses("2026-06-01", "2026-06-30")

        then: "none of the other user's expenses appear"
        response.statusCode() == 200
        def descriptions = response.jsonPath().getList("description")
        !descriptions.contains("Other user expense")
    }

    def "monthly summary aggregates expenses by category"() {
        given: "three expenses across two categories in June 2026"
        submitExpense(DEVICE_ID, SECRET, [amount: 26.00, category: "DINING_OUT", description: "Kebab", occurredAt: "2026-06-10T12:00:00Z"])
        submitExpense(DEVICE_ID, SECRET, [amount: 50.00, category: "SUBSCRIPTIONS", description: "Netflix", occurredAt: "2026-06-11T12:00:00Z"])
        submitExpense(DEVICE_ID, SECRET, [amount: 30.00, category: "DINING_OUT", description: "Burger", occurredAt: "2026-06-12T12:00:00Z"])

        and: "all three projections are applied"
        awaitProjection { getExpenses("2026-06-01", "2026-06-30").jsonPath().getList("").size() == 3 }

        when: "the monthly summary is requested"
        def path = "/v1/expenses/summary/monthly?year=2026&month=6"
        def response = authenticatedGet(DEVICE_ID, SECRET, path).get(path)

        then: "categories are aggregated and the transaction count is correct"
        response.statusCode() == 200
        def json = response.jsonPath()
        json.getInt("transactionCount") == 3
        categoryTotal(json, "DINING_OUT") == 56.0
        categoryTotal(json, "SUBSCRIPTIONS") == 50.0
    }

    def "expenses outside date range are not returned"() {
        given: "an expense recorded in March 2026"
        submitExpense(DEVICE_ID, SECRET, [
                amount     : 100.00,
                description: "March expense",
                occurredAt : "2026-03-15T12:00:00Z"
        ])

        and: "the projection is applied (visible in a wide range)"
        awaitProjection { getExpenses("2026-01-01", "2026-12-31").jsonPath().getList("").size() > 0 }

        when: "expenses for June 2026 are requested"
        def response = getExpenses("2026-06-01", "2026-06-30")

        then: "the March expense is not included"
        response.statusCode() == 200
        def descriptions = response.jsonPath().getList("description")
        !descriptions.contains("March expense")
    }

    /**
     * Reads the total for a category from the monthly summary response. The exact shape
     * (list of {category,total} vs map keyed by category) is part of the contract the
     * production code must satisfy; both common shapes are tolerated here.
     */
    private static double categoryTotal(def json, String category) {
        def byList = json.getList("categories.findAll { it.category == '${category}' }.total")
        if (byList != null && !byList.isEmpty()) {
            return byList.collect { it as double }.sum() as double
        }
        def direct = json.get("categoryTotals.${category}")
        return direct == null ? 0.0d : (direct as double)
    }
}
