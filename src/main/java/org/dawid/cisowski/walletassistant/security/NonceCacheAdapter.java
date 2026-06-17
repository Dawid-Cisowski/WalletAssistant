package org.dawid.cisowski.walletassistant.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.config.SecurityUtils;
import org.dawid.cisowski.walletassistant.security.api.NonceCache;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
class NonceCacheAdapter implements NonceCache {

    private final CacheManager cacheManager;

    @Override
    public boolean markAsUsedIfAbsent(String deviceId, String nonce) {
        try {
            var cache = Optional.ofNullable(cacheManager.getCache(CacheConfig.NONCE_CACHE))
                    .orElseThrow(() -> new IllegalStateException("Nonce cache is not configured"));
            return isFresh(cache, deviceId + ":" + nonce);
        } catch (RuntimeException exception) {
            log.warn("Nonce cache check failed for device {}, failing secure",
                    SecurityUtils.maskDeviceId(deviceId), exception);
            return false;
        }
    }

    private boolean isFresh(Cache cache, String key) {
        return cache.putIfAbsent(key, Boolean.TRUE) == null;
    }
}
