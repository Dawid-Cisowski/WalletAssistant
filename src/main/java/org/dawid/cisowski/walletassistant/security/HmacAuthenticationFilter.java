package org.dawid.cisowski.walletassistant.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.config.AppProperties;
import org.dawid.cisowski.walletassistant.config.SecurityUtils;
import org.dawid.cisowski.walletassistant.security.api.DeviceSecretProvider;
import org.dawid.cisowski.walletassistant.security.api.NonceCache;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
class HmacAuthenticationFilter extends OncePerRequestFilter {

    private static final String DEVICE_ID_ATTRIBUTE = "deviceId";
    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/v1/wallet-events", "/v1/expenses", "/v1/accounts", "/v1/investments", "/sse", "/mcp");
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private final DeviceSecretProvider deviceSecretProvider;
    private final NonceCache nonceCache;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!requiresAuthentication(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (request.getAttribute(DEVICE_ID_ATTRIBUTE) != null) {
            filterChain.doFilter(request, response);
            return;
        }

        var effectiveRequest = wrapIfBodyMethod(request);
        try {
            var deviceId = validateHmacAuthentication(effectiveRequest);
            effectiveRequest.setAttribute(DEVICE_ID_ATTRIBUTE, deviceId);
            filterChain.doFilter(effectiveRequest, response);
        } catch (HmacAuthenticationException exception) {
            log.warn("HMAC authentication failed: {}", SecurityUtils.sanitizeForLog(exception.getMessage()));
            writeUnauthorized(response, exception.getMessage());
        }
    }

    private boolean requiresAuthentication(String path) {
        return PROTECTED_PATHS.stream().anyMatch(path::startsWith);
    }

    private HttpServletRequest wrapIfBodyMethod(HttpServletRequest request) throws IOException {
        return BODY_METHODS.contains(request.getMethod())
                ? CachedBodyHttpServletRequest.of(request)
                : request;
    }

    private String validateHmacAuthentication(HttpServletRequest request) throws HmacAuthenticationException {
        var deviceId = requireHeader(request, "X-Device-Id");
        var timestamp = requireHeader(request, "X-Timestamp");
        var nonce = requireHeader(request, "X-Nonce");
        var signature = requireHeader(request, "X-Signature");

        var secret = deviceSecretProvider.getSecret(deviceId)
                .orElseThrow(() -> new HmacAuthenticationException("Unknown device"));

        verifyTimestamp(timestamp);
        verifyNonce(deviceId, nonce);

        var canonicalString = buildCanonicalString(request, timestamp, nonce, deviceId);
        var expected = HmacSignature.calculate(canonicalString, secret);
        if (!HmacSignature.verify(expected, signature)) {
            throw new HmacAuthenticationException("Signature mismatch");
        }
        return deviceId;
    }

    private void verifyTimestamp(String timestamp) throws HmacAuthenticationException {
        var requestInstant = parseTimestamp(timestamp);
        var skew = Duration.between(requestInstant, Instant.now()).abs().getSeconds();
        if (skew > appProperties.getHmac().getToleranceSeconds()) {
            throw new HmacAuthenticationException("Timestamp outside tolerance window");
        }
    }

    private Instant parseTimestamp(String timestamp) throws HmacAuthenticationException {
        try {
            return Instant.parse(timestamp);
        } catch (RuntimeException exception) {
            throw new HmacAuthenticationException("Invalid timestamp format");
        }
    }

    private void verifyNonce(String deviceId, String nonce) throws HmacAuthenticationException {
        if (!nonceCache.markAsUsedIfAbsent(deviceId, nonce)) {
            throw new HmacAuthenticationException("Nonce already used or cache unavailable");
        }
    }

    private String buildCanonicalString(HttpServletRequest request, String timestamp, String nonce, String deviceId) {
        return String.join("\n",
                request.getMethod(),
                pathWithQuery(request),
                timestamp,
                nonce,
                deviceId,
                bodyOf(request));
    }

    private String pathWithQuery(HttpServletRequest request) {
        return Optional.ofNullable(request.getQueryString())
                .map(query -> request.getRequestURI() + "?" + query)
                .orElseGet(request::getRequestURI);
    }

    private String bodyOf(HttpServletRequest request) {
        return request instanceof CachedBodyHttpServletRequest cached ? cached.getBody() : "";
    }

    private String requireHeader(HttpServletRequest request, String name) throws HmacAuthenticationException {
        return Optional.ofNullable(request.getHeader(name))
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new HmacAuthenticationException("Missing header: " + name));
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                new ErrorResponse("HMAC_AUTH_FAILED", message));
    }

    static final class HmacAuthenticationException extends Exception {
        HmacAuthenticationException(String message) {
            super(message);
        }
    }
}
