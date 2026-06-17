package org.dawid.cisowski.walletassistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class McpCorsConfig implements WebMvcConfigurer {

    private static final String CLAUDE_ORIGIN = "https://claude.ai";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/sse")
                .allowedOrigins(CLAUDE_ORIGIN)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Mcp-Session-Id")
                .allowCredentials(true);

        registry.addMapping("/mcp/**")
                .allowedOrigins(CLAUDE_ORIGIN)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Mcp-Session-Id")
                .allowCredentials(true);

        registry.addMapping("/.well-known/**")
                .allowedOrigins(CLAUDE_ORIGIN)
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*");
    }
}
