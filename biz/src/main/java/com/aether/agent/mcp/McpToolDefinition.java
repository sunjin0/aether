package com.aether.agent.mcp;

import lombok.Data;

/**
 * MCP tool metadata discovered from a server.
 */
@Data
public class McpToolDefinition {

    private String name;

    private String description;

    private String inputSchema;

    private String outputSchema;
}
