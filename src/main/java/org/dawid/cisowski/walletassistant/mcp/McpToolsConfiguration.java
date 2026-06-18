package org.dawid.cisowski.walletassistant.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
class McpToolsConfiguration {

    @Bean
    ToolCallbackProvider walletToolCallbacks(WalletTools walletTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(walletTools)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
    WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
            McpServerStreamableHttpProperties serverProperties) {

        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(serverProperties.getMcpEndpoint())
                .keepAliveInterval(serverProperties.getKeepAliveInterval())
                .disallowDelete(serverProperties.isDisallowDelete())
                .contextExtractor(serverRequest -> {
                    var deviceId = serverRequest.servletRequest().getAttribute("deviceId");
                    if (deviceId instanceof String s && !s.isBlank()) {
                        return McpTransportContext.create(java.util.Map.of("deviceId", s));
                    }
                    return McpTransportContext.EMPTY;
                })
                .build();
    }
}
