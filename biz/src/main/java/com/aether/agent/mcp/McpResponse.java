package com.aether.agent.mcp;

import lombok.Data;

/**
 * Raw MCP transport response.
 */
@Data
public class McpResponse {

    private String body;

    private String sessionId;

    private Integer statusCode;
}
