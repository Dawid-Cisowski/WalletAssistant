package org.dawid.cisowski.walletassistant.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.config.AppProperties;
import org.dawid.cisowski.walletassistant.config.SecurityUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnProperty(name = "app.api-key.enabled", havingValue = "true")
@RequiredArgsConstructor
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String DEVICE_ID_ATTRIBUTE = "deviceId";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/v1/wallet-events", "/v1/expenses", "/v1/accounts", "/v1/investments", "/sse", "/mcp");

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

        var token = extractToken(request);
        if (token.filter(this::matches).isEmpty()) {
            handleMissingOrInvalidToken(request, response, filterChain, token);
            return;
        }

        var deviceId = appProperties.getApiKey().getDeviceId();
        request.setAttribute(DEVICE_ID_ATTRIBUTE, deviceId);
        log.debug("API key authentication succeeded for device {}", SecurityUtils.maskDeviceId(deviceId));
        filterChain.doFilter(request, response);
    }

    private void handleMissingOrInvalidToken(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            Optional<String> token
    ) throws ServletException, IOException {
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("API key authentication failed for request to {}",
                SecurityUtils.sanitizeForLog(request.getRequestURI()));
        writeUnauthorized(response);
    }

    private boolean requiresAuthentication(String path) {
        return PROTECTED_PATHS.stream().anyMatch(path::startsWith);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        return bearerToken(request).or(() -> queryToken(request));
    }

    private Optional<String> bearerToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION))
                .filter(header -> header.startsWith(BEARER_PREFIX))
                .map(header -> header.substring(BEARER_PREFIX.length()))
                .filter(token -> !token.isBlank());
    }

    private Optional<String> queryToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getParameter("token"))
                .filter(token -> !token.isBlank());
    }

    private boolean matches(String token) {
        return MessageDigest.isEqual(sha256(token), appProperties.getApiKey().getKeyHash());
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                new ErrorResponse("API_KEY_AUTH_FAILED", "Invalid authentication credentials"));
    }

}
