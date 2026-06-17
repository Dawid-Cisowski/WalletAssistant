package org.dawid.cisowski.walletassistant.config;

import java.util.Optional;

public final class SecurityUtils {

    private static final int MIN_MASKABLE_LENGTH = 8;
    private static final int VISIBLE_PREFIX = 4;
    private static final int VISIBLE_SUFFIX = 4;
    private static final int MAX_LOG_LENGTH = 100;
    private static final String SHORT_MASK = "***";

    private SecurityUtils() {
    }

    public static String maskDeviceId(String deviceId) {
        return Optional.ofNullable(deviceId)
                .filter(id -> id.length() >= MIN_MASKABLE_LENGTH)
                .map(SecurityUtils::mask)
                .orElse(SHORT_MASK);
    }

    public static String sanitizeForLog(String input) {
        return Optional.ofNullable(input)
                .map(value -> value.replaceAll("[\\r\\n\\t]", "_"))
                .map(SecurityUtils::truncate)
                .orElse("");
    }

    private static String mask(String deviceId) {
        return deviceId.substring(0, VISIBLE_PREFIX)
                + "..."
                + deviceId.substring(deviceId.length() - VISIBLE_SUFFIX);
    }

    private static String truncate(String value) {
        return value.length() > MAX_LOG_LENGTH ? value.substring(0, MAX_LOG_LENGTH) : value;
    }
}
