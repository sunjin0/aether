package com.aether.agent.mcp;

import com.aether.agent.entity.AgentMcpServer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory MCP session cache.
 */
@Component
public class McpSessionManager {

    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();

    public McpSession getSession(AgentMcpServer server) {
        McpSession session = sessions.computeIfAbsent(server.getId(), key -> {
            McpSession created = new McpSession();
            created.setServerId(key);
            return created;
        });
        session.setLastAccessAt(System.currentTimeMillis());
        return session;
    }

    public void invalidate(String serverId) {
        sessions.remove(serverId);
    }
}
