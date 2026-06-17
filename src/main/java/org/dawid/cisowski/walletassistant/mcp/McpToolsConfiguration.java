package org.dawid.cisowski.walletassistant.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class McpToolsConfiguration {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    ToolCallbackProvider walletToolCallbacks(WalletTools walletTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(walletTools)
                .build();
    }
}
