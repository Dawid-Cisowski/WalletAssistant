package org.dawid.cisowski.walletassistant.expenses

import spock.lang.Specification
import spock.lang.Title

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Title("Expense value object")
class ExpenseSpec extends Specification {

    static final String EXPENSE_ID = "expense-1"
    static final String EVENT_ID = "event-1"
    static final String USER_ID = "user-1"
    static final BigDecimal AMOUNT = new BigDecimal("99.99")
    static final String CURRENCY = "PLN"
    static final ExpenseCategory CATEGORY = ExpenseCategory.FOOD_AND_DRINKS
    static final Instant OCCURRED_AT = Instant.parse("2025-01-15T10:00:00Z")
    static final LocalDate OCCURRED_DATE = LocalDate.parse("2025-01-15")

    def "should create a valid expense when all required fields are provided"() {
        when: "an expense is constructed with valid data"
        def expense = new Expense(EXPENSE_ID, EVENT_ID, USER_ID, AMOUNT, CURRENCY, CATEGORY,
                "Lunch", "Restaurant", "checking", OCCURRED_AT, OCCURRED_DATE)

        then: "all fields are set as provided"
        expense.expenseId() == EXPENSE_ID
        expense.eventId() == EVENT_ID
        expense.userId() == USER_ID
        expense.amount() == AMOUNT
        expense.currency() == CURRENCY
        expense.category() == CATEGORY
        expense.description() == "Lunch"
        expense.merchant() == "Restaurant"
        expense.accountType() == "checking"
        expense.occurredAt() == OCCURRED_AT
        expense.occurredDate() == OCCURRED_DATE
    }

    def "should throw NullPointerException when amount is null"() {
        when: "an expense is constructed with a null amount"
        new Expense(EXPENSE_ID, EVENT_ID, USER_ID, null, CURRENCY, CATEGORY,
                "Lunch", "Restaurant", "checking", OCCURRED_AT, OCCURRED_DATE)

        then: "construction fails with a descriptive NullPointerException"
        def exception = thrown(NullPointerException)
        exception.message == "amount must not be null"
    }

    def "should throw NullPointerException when currency is null"() {
        when: "an expense is constructed with a null currency"
        new Expense(EXPENSE_ID, EVENT_ID, USER_ID, AMOUNT, null, CATEGORY,
                "Lunch", "Restaurant", "checking", OCCURRED_AT, OCCURRED_DATE)

        then: "construction fails with a descriptive NullPointerException"
        def exception = thrown(NullPointerException)
        exception.message == "currency must not be null"
    }

    def "should throw IllegalArgumentException when amount is #amount"() {
        when: "an expense is constructed with a non-positive amount"
        new Expense(EXPENSE_ID, EVENT_ID, USER_ID, amount, CURRENCY, CATEGORY,
                "Lunch", "Restaurant", "checking", OCCURRED_AT, OCCURRED_DATE)

        then: "construction fails because the amount must be greater than zero"
        def exception = thrown(IllegalArgumentException)
        exception.message == "amount must be greater than zero"

        where:
        amount << [BigDecimal.ZERO, new BigDecimal("-0.01"), new BigDecimal("-100")]
    }

    def "should accept a small positive amount at the boundary"() {
        when: "an expense is constructed with the smallest positive amount"
        def expense = new Expense(EXPENSE_ID, EVENT_ID, USER_ID, new BigDecimal("0.01"), CURRENCY, CATEGORY,
                "Lunch", "Restaurant", "checking", OCCURRED_AT, OCCURRED_DATE)

        then: "the expense is created successfully"
        expense.amount() == new BigDecimal("0.01")
    }
}
