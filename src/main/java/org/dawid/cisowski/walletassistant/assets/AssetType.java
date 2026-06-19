package org.dawid.cisowski.walletassistant.assets;

import java.util.Arrays;

enum AssetType {
    STOCK("Akcje"),
    ETF("ETF"),
    GOLD("Złoto"),
    CRYPTO("Kryptowaluty"),
    OTHER("Inne");

    private final String displayName;

    AssetType(String displayName) {
        this.displayName = displayName;
    }

    String displayName() {
        return displayName;
    }

    static AssetType fromString(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown asset type: " + value));
    }
}
