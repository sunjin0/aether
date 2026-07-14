package com.aether.agent.mcp;

import lombok.Data;

/**
 * MCP client session state.
 */
@Data
public class McpSession {

    private String serverId;

    private String sessionId;

    private boolean initialized;

    private long lastAccessAt;
}
