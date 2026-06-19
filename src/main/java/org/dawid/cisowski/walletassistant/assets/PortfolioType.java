package org.dawid.cisowski.walletassistant.assets;

import java.util.Arrays;

enum PortfolioType {
    IKE("IKE"),
    PERSONAL("Prywatne");

    private final String displayName;

    PortfolioType(String displayName) {
        this.displayName = displayName;
    }

    String displayName() {
        return displayName;
    }

    static PortfolioType fromString(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown portfolio type: " + value));
    }
}
