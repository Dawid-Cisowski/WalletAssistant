package org.dawid.cisowski.walletassistant.expenses;

import java.util.Arrays;

public enum ExpenseCategory {

    DINING_OUT("Jedzenie na mieście"),
    GROCERIES("Zakupy spożywcze"),
    TRANSPORT("Transport"),
    HOME_SUPPLIES("Artykuły domowe"),
    ENTERTAINMENT("Rozrywka"),
    SUBSCRIPTIONS("Subskrypcje"),
    HEALTH("Zdrowie"),
    EDUCATION("Edukacja"),
    KIDS_TOYS("Zabawki dla dzieci"),
    CLOTHING("Odzież"),
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
                .orElse(OTHER);
    }
}
