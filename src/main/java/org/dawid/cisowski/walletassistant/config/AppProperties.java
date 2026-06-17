package org.dawid.cisowski.walletassistant.config;

import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Getter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final HmacConfig hmac = new HmacConfig();
    private final NonceConfig nonce = new NonceConfig();
    private final ApiKeyConfig apiKey = new ApiKeyConfig();

    @PostConstruct
    void init() {
        hmac.init();
        apiKey.validate();
    }

    @Data
    public static class HmacConfig {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private String devicesJson;
        private int toleranceSeconds = 600;
        private Map<String, byte[]> deviceSecrets = new HashMap<>();

        void init() {
            Optional.ofNullable(devicesJson)
                    .filter(json -> !json.isBlank())
                    .ifPresentOrElse(this::loadSecrets, () ->
                            log.info("No HMAC devices configured, skipping device secret initialization"));
        }

        private void loadSecrets(String json) {
            Map<String, String> parsed = OBJECT_MAPPER.readValue(
                    json,
                    OBJECT_MAPPER.getTypeFactory().constructMapType(HashMap.class, String.class, String.class));
            this.deviceSecrets = new HashMap<>();
            parsed.forEach(this::registerSecret);
            log.info("Loaded {} HMAC device secret(s)", deviceSecrets.size());
        }

        private void registerSecret(String deviceId, String base64Secret) {
            deviceSecrets.put(deviceId, Base64.getDecoder().decode(base64Secret));
            log.info("Loaded HMAC secret for device {}", SecurityUtils.maskDeviceId(deviceId));
        }
    }

    @Data
    public static class NonceConfig {
        private int cacheTtlSeconds = 600;
    }

    @Data
    public static class ApiKeyConfig {

        private static final int MIN_KEY_LENGTH = 32;

        private boolean enabled = false;
        private String key;
        private String deviceId = "claude-ai";
        private boolean readOnly = false;

        @Setter(AccessLevel.NONE)
        private byte[] keyHash;

        public byte[] getKeyHash() {
            return keyHash == null ? null : keyHash.clone();
        }

        void validate() {
            if (!enabled) {
                return;
            }
            if (key == null || key.isBlank() || key.length() < MIN_KEY_LENGTH) {
                throw new IllegalStateException(
                        "app.api-key.key must be non-blank and at least " + MIN_KEY_LENGTH + " characters when enabled");
            }
            this.keyHash = sha256(key);
            log.info("API key authentication enabled for device {}", SecurityUtils.maskDeviceId(deviceId));
        }

        private static byte[] sha256(String value) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 algorithm not available", exception);
            }
        }
    }
}
