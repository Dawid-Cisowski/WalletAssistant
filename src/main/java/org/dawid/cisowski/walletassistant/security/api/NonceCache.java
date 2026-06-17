package org.dawid.cisowski.walletassistant.security.api;

public interface NonceCache {

    boolean markAsUsedIfAbsent(String deviceId, String nonce);
}
