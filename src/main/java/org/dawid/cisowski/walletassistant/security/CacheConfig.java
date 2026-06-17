package org.dawid.cisowski.walletassistant.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.dawid.cisowski.walletassistant.config.AppProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
class CacheConfig {

    static final String NONCE_CACHE = "nonces";

    @Bean
    CacheManager cacheManager(AppProperties appProperties) {
        var cacheManager = new CaffeineCacheManager(NONCE_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(appProperties.getNonce().getCacheTtlSeconds(), TimeUnit.SECONDS));
        return cacheManager;
    }
}
