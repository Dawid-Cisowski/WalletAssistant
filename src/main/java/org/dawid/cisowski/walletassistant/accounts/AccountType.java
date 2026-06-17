package org.dawid.cisowski.walletassistant.accounts;

import java.util.Arrays;

public enum AccountType {
    BUSINESS("Firmowe"),
    PERSONAL_SAVINGS("Oszczędności prywatne"),
    PERSONAL_SPENDING("Wydatki prywatne");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static AccountType fromString(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown account type: " + value));
    }
}
