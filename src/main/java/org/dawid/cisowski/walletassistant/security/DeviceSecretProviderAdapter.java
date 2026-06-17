package org.dawid.cisowski.walletassistant.security;

import lombok.RequiredArgsConstructor;
import org.dawid.cisowski.walletassistant.config.AppProperties;
import org.dawid.cisowski.walletassistant.security.api.DeviceSecretProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class DeviceSecretProviderAdapter implements DeviceSecretProvider {

    private final AppProperties appProperties;

    @Override
    public Optional<byte[]> getSecret(String deviceId) {
        return Optional.ofNullable(appProperties.getHmac().getDeviceSecrets().get(deviceId))
                .map(byte[]::clone);
    }
}
