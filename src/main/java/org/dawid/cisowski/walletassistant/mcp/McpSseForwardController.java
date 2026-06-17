package org.dawid.cisowski.walletassistant.mcp;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;

/**
 * Forwards /sse → /mcp (Spring AI Streamable HTTP transport).
 * Claude.ai sends requests to /sse, but Spring AI registers at /mcp by default.
 * Servlet forward preserves request attributes (including deviceId set by auth filters).
 */
@Slf4j
@Controller
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
class McpSseForwardController {

    @PostMapping("/sse")
    void forwardPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        log.debug("MCP: forwarding POST /sse → /mcp");
        request.getRequestDispatcher("/mcp").forward(request, response);
    }

    @GetMapping("/sse")
    void forwardGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        log.debug("MCP: forwarding GET /sse → /mcp");
        request.getRequestDispatcher("/mcp").forward(request, response);
    }

    @DeleteMapping("/sse")
    void forwardDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        log.debug("MCP: forwarding DELETE /sse → /mcp");
        request.getRequestDispatcher("/mcp").forward(request, response);
    }
}
