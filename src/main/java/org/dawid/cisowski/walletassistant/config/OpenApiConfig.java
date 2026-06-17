package org.dawid.cisowski.walletassistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI walletAssistantOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Wallet Assistant API")
                .version("1.0.0")
                .description("Personal finance management service exposing MCP tools and REST read APIs"));
    }
}
