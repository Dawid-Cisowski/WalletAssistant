package org.dawid.cisowski.walletassistant.expenses

import spock.lang.Specification
import spock.lang.Title

@Title("ExpenseCategory enum")
class ExpenseCategorySpec extends Specification {

    def "should expose a non-null, non-empty display name for #category"() {
        expect: "every category has a human-readable display name"
        category.displayName() != null
        !category.displayName().isBlank()

        where:
        category << ExpenseCategory.values()
    }

    def "should resolve '#value' to #expected by case-insensitive enum name"() {
        expect: "fromString matches the enum constant regardless of case"
        ExpenseCategory.fromString(value) == expected

        where:
        value               | expected
        "FOOD_AND_DRINKS"   | ExpenseCategory.FOOD_AND_DRINKS
        "food_and_drinks"   | ExpenseCategory.FOOD_AND_DRINKS
        "Food_And_Drinks"   | ExpenseCategory.FOOD_AND_DRINKS
        "TRANSPORT"         | ExpenseCategory.TRANSPORT
        "transport"         | ExpenseCategory.TRANSPORT
        "OTHER"             | ExpenseCategory.OTHER
        "other"             | ExpenseCategory.OTHER
    }

    def "should resolve every enum constant by its own name"() {
        expect: "each enum name maps back to the same constant"
        ExpenseCategory.fromString(category.name()) == category

        where:
        category << ExpenseCategory.values()
    }

    def "should throw IllegalArgumentException for an unknown value '#value'"() {
        when: "fromString is called with a value matching no category"
        ExpenseCategory.fromString(value)

        then: "it fails with a descriptive exception"
        def exception = thrown(IllegalArgumentException)
        exception.message == "Unknown expense category: ${value}"

        where:
        value << ["UNKNOWN", "not-a-category", ""]
    }
}
