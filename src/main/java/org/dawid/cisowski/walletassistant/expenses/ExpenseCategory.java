package org.dawid.cisowski.walletassistant.expenses;

import java.util.Arrays;

public enum ExpenseCategory {

    FOOD_AND_DRINKS("Jedzenie i napoje"),
    TRANSPORT("Transport"),
    SHOPPING("Zakupy"),
    ENTERTAINMENT("Rozrywka"),
    SUBSCRIPTIONS("Subskrypcje"),
    HEALTH("Zdrowie"),
    HOUSING("Mieszkanie"),
    UTILITIES("Media"),
    EDUCATION("Edukacja"),
    TRAVEL("Podróże"),
    BUSINESS("Firmowe"),
    SAVINGS_TRANSFER("Transfer na oszczędności"),
    OTHER("Inne");

    private final String displayName;

    ExpenseCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ExpenseCategory fromString(String value) {
        return Arrays.stream(values())
                .filter(category -> category.name().equalsIgnoreCase(value)
                        || category.displayName.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown expense category: " + value));
    }
}
