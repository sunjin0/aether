package com.aether.agent.mcp.transport;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects a transport implementation by server transport type.
 */
@Component
public class McpTransportFactory {

    private final List<McpTransport> transports;

    /**
     * 创建 {@code McpTransportFactory} 实例。
     */
    public McpTransportFactory(List<McpTransport> transports) {
        this.transports = transports;
    }

    /**
     * 获取Transport。
     */
    public McpTransport getTransport(String transportType) {
        for (McpTransport transport : transports) {
            if (transport.supports(transportType)) {
                return transport;
            }
        }
        throw new IllegalArgumentException("Unsupported MCP transport: " + transportType);
    }

    /**
     * 处理supports。
     */
    public boolean supports(String transportType) {
        for (McpTransport transport : transports) {
            if (transport.supports(transportType)) {
                return true;
            }
        }
        return false;
    }
}
