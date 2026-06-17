package org.dawid.cisowski.walletassistant.mcp;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
class McpSseForwardController {

    private final RestClient.Builder restClientBuilder;

    @PostMapping(value = "/sse", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> forwardToMcp(
            @RequestBody String body,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String sessionId,
            HttpServletRequest request
    ) {
        var targetUri = baseUrl(request) + "/mcp";
        log.debug("Forwarding /sse POST to {}", targetUri);

        return restClientBuilder.build().post()
                .uri(targetUri)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, Optional.ofNullable(accept)
                        .orElse(MediaType.APPLICATION_JSON_VALUE + ", " + MediaType.TEXT_EVENT_STREAM_VALUE))
                .headers(headers -> Optional.ofNullable(sessionId)
                        .ifPresent(value -> headers.add("Mcp-Session-Id", value)))
                .body(body)
                .exchange((forwardRequest, forwardResponse) -> ResponseEntity
                        .status(forwardResponse.getStatusCode())
                        .headers(copyableHeaders(forwardResponse.getHeaders()))
                        .body(new String(forwardResponse.getBody().readAllBytes())));
    }

    private HttpHeaders copyableHeaders(HttpHeaders source) {
        var headers = new HttpHeaders();
        Optional.ofNullable(source.getContentType()).ifPresent(headers::setContentType);
        Optional.ofNullable(source.getFirst("Mcp-Session-Id"))
                .ifPresent(value -> headers.add("Mcp-Session-Id", value));
        return headers;
    }

    private String baseUrl(HttpServletRequest request) {
        var scheme = Optional.ofNullable(request.getHeader("X-Forwarded-Proto")).orElse(request.getScheme());
        var host = Optional.ofNullable(request.getHeader("X-Forwarded-Host"))
                .orElseGet(() -> request.getServerName() + portSuffix(request));
        return scheme + "://" + host;
    }

    private String portSuffix(HttpServletRequest request) {
        var port = request.getServerPort();
        return Map.of(80, "", 443, "").getOrDefault(port, ":" + port);
    }
}
