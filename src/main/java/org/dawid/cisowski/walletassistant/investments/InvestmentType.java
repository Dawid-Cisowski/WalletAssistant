package org.dawid.cisowski.walletassistant.investments;

import java.util.Arrays;

public enum InvestmentType {

    IKE("IKE"),
    XTB_STOCKS("XTB Akcje"),
    XTB_ETF("XTB ETF"),
    SAVINGS_ACCOUNT("Konto oszczędnościowe"),
    CRYPTO("Kryptowaluty"),
    OTHER("Inne");

    private final String displayName;

    InvestmentType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static InvestmentType fromString(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown investment type: " + value));
    }
}
