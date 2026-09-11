package com.aether.agent.tools;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.AgentMcpServerService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 判断 Agent 工具当前是否可用于本轮下发。
 *
 * <p>工具自身必须启用且未删除，再按来源区分：</p>
 * <ul>
 *   <li><b>MCP 工具</b>（{@code mcpServerId} 非空）要求所属 MCP 服务存在、未删除且已启用。
 *       绑定关系只说明 Agent 被授权使用该工具，MCP 服务停用或删除后绑定行仍然存在，
 *       所以每次下发前都要重新判定，不能只信绑定表。</li>
 *   <li><b>内置工具</b>（{@code mcpServerId} 为空，由 {@link com.aether.agent.tools.core.ToolRegistry}
 *       提供，如 ask_user / human_handoff）是进程内实现，不发起 MCP 调用，只受自身启停约束。</li>
 * </ul>
 *
 * <p>{@code agent_tool.mcp_server_id} 建表即为 NOT NULL，因此空值只可能来自内置工具，
 * 可据此区分两种来源。不能把空值当成"服务缺失"而丢弃——内置交互工具是交互模式的
 * 承重能力，被裁掉后模型既看不到 ask_user 的入参结构，也无法主动向用户追问。</p>
 */
@Component
public class AgentToolLiveness {

    private final AgentMcpServerService mcpServerService;

    /**
     * 创建 {@code AgentToolLiveness} 实例。
     */
    public AgentToolLiveness(AgentMcpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    /**
     * 工具自身启用、未删除，且其来源当前可用。
     *
     * @param tool 待判断工具，可为 null
     * @return 可用于本轮下发时为 true
     */
    public boolean isLive(AgentTool tool) {
        if (!isEnabled(tool)) return false;
        if (StringUtils.isBlank(tool.getMcpServerId())) return true;
        // MCP 工具缺少远端工具名则无法派发，与所属服务是否在线无关。
        if (StringUtils.isBlank(tool.getMcpToolName())) return false;
        return isServerLive(mcpServerService.getById(tool.getMcpServerId()));
    }

    /**
     * 过滤出当前可用的工具，保持入参顺序。
     *
     * <p>所属 MCP 服务按 id 一次查清，避免逐个工具往返查询。</p>
     *
     * @param tools 候选工具；null 原样返回
     * @return 可用工具列表（新集合）
     */
    public List<AgentTool> filterLive(List<AgentTool> tools) {
        if (tools == null) return null;
        Set<String> serverIds = new LinkedHashSet<>();
        for (AgentTool tool : tools) {
            if (isEnabled(tool) && StringUtils.isNotBlank(tool.getMcpServerId())) {
                serverIds.add(tool.getMcpServerId());
            }
        }
        Map<String, AgentMcpServer> servers = new HashMap<>();
        if (!serverIds.isEmpty()) {
            for (AgentMcpServer server : mcpServerService.listByIds(serverIds)) {
                if (server != null && server.getId() != null) servers.put(server.getId(), server);
            }
        }
        List<AgentTool> result = new ArrayList<>();
        for (AgentTool tool : tools) {
            if (!isEnabled(tool)) continue;
            if (StringUtils.isBlank(tool.getMcpServerId())) {
                result.add(tool);
                continue;
            }
            if (StringUtils.isBlank(tool.getMcpToolName())) continue;
            if (isServerLive(servers.get(tool.getMcpServerId()))) result.add(tool);
        }
        return result;
    }

    private boolean isEnabled(AgentTool tool) {
        return tool != null && Integer.valueOf(1).equals(tool.getStatus()) && !Boolean.TRUE.equals(tool.getDeleted());
    }

    private boolean isServerLive(AgentMcpServer server) {
        return server != null && !Boolean.TRUE.equals(server.getDeleted()) && Integer.valueOf(1).equals(server.getStatus());
    }
}
