package com.aether.agent.controller;

import com.aether.agent.dto.AgentMcpServerDto;
import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.mcp.McpClient;
import com.aether.agent.mcp.McpToolDefinition;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.agent.vo.AgentMcpServerVo;
import com.aether.entity.WebResponse;
import com.aether.entity.Option;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.aether.utils.AesUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP服务管理 Controller
 */
@Api(tags = "MCP服务管理 API")
@Validated
@RestController
@Permission(path = "/agent/mcp-server")
@RequestMapping("/api/agent/mcp-server")
public class AgentMcpServerController {

    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private final AgentMcpServerService agentMcpServerService;
    private final AgentToolService agentToolService;
    private final McpClient mcpClient;

    @Autowired(required = false)
    private AgentToolCatalog agentToolCatalog;

    /**
     * 创建 {@code AgentMcpServerController} 实例。
     */
    public AgentMcpServerController(AgentMcpServerService agentMcpServerService,
                                    AgentToolService agentToolService,
                                    McpClient mcpClient) {
        this.agentMcpServerService = agentMcpServerService;
        this.agentToolService = agentToolService;
        this.mcpClient = mcpClient;
    }

    /**
     * 分页查询 MCP 服务，认证令牌不会返回给前端。
     */
    @ApiOperation("MCP服务列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<AgentMcpServerVo>> list(@RequestBody AgentMcpServerVo vo) {
        Page<AgentMcpServer> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<AgentMcpServer> wrapper = Wrappers.lambdaQuery(AgentMcpServer.class)
                .like(StringUtils.isNotBlank(vo.getName()), AgentMcpServer::getName, vo.getName())
                .like(StringUtils.isNotBlank(vo.getCode()), AgentMcpServer::getCode, vo.getCode())
                .eq(StringUtils.isNotBlank(vo.getTransport()), AgentMcpServer::getTransport, vo.getTransport())
                .eq(vo.getStatus() != null, AgentMcpServer::getStatus, vo.getStatus())
                .eq(AgentMcpServer::getDeleted, false)
                .orderByDesc(AgentMcpServer::getCreatedAt);
        Page<AgentMcpServer> result = agentMcpServerService.page(page, wrapper);
        List<AgentMcpServerVo> list = result.getRecords().stream().map(item -> {
            AgentMcpServerVo itemVo = new AgentMcpServerVo();
            BeanUtils.copyProperties(item, itemVo);
            itemVo.setAuthToken(null);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    /**
     * 查询启用的 MCP 服务选项，返回值不包含认证令牌等敏感字段。
     */
    @ApiOperation("MCP服务下拉选项")
    @Permission(required = false)
    @GetMapping("/options")
    public WebResponse<List<Option>> options() {
        List<Option> options = agentMcpServerService.list(Wrappers.lambdaQuery(AgentMcpServer.class)
                        .eq(AgentMcpServer::getStatus, 1).eq(AgentMcpServer::getDeleted, false)
                        .orderByAsc(AgentMcpServer::getName))
                .stream().map(item -> new Option(item.getName(), item.getId())).collect(Collectors.toList());
        return WebResponse.OK(options);
    }

    /**
     * 查询 MCP 服务详情，并隐藏认证令牌。
     */
    @ApiOperation("MCP服务详情")
    @GetMapping("/{id}")
    public WebResponse<AgentMcpServerVo> detail(@PathVariable @NotBlank String id) {
        AgentMcpServer server = getExistingServer(id);
        AgentMcpServerVo vo = new AgentMcpServerVo();
        BeanUtils.copyProperties(server, vo);
        vo.setAuthToken(null);
        return WebResponse.OK(vo);
    }

    /**
     * 创建 MCP 服务并加密保存认证令牌。
     */
    @ApiOperation("新增MCP服务")
    @Permission(path = "/agent/mcp-server", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping
    public WebResponse<String> save(@RequestBody AgentMcpServerDto dto) {
        AgentMcpServer server = new AgentMcpServer();
        BeanUtils.copyProperties(dto, server);
        fillDefaults(server);
        encryptAuthToken(server);
        boolean saved = agentMcpServerService.save(server);
        return WebResponse.OK(saved ? I18nUtils.getMessage("agent.mcp-server.create.success") : I18nUtils.getMessage("agent.mcp-server.create.fail"), server.getId());
    }

    /**
     * 更新 MCP 服务配置，保留未修改的认证令牌。
     */
    @ApiOperation("编辑MCP服务")
    @Permission(path = "/agent/mcp-server", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable @NotBlank String id, @RequestBody AgentMcpServerDto dto) {
        AgentMcpServer existing = getExistingServer(id);
        AgentMcpServer server = new AgentMcpServer();
        BeanUtils.copyProperties(dto, server);
        server.setId(id);
        fillDefaults(server);
        applyAuthTokenForUpdate(server, existing, dto);
        boolean updated = agentMcpServerService.updateById(server);
        if (updated) {
            refreshImportedToolDefinitions(server);
        }
        return WebResponse.OK(updated ? I18nUtils.getMessage("agent.mcp-server.update.success") : I18nUtils.getMessage("agent.mcp-server.update.fail"));
    }

    /**
     * 删除未绑定工具的 MCP 服务。
     */
    @ApiOperation("删除MCP服务")
    @Permission(path = "/agent/mcp-server", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        long toolCount = agentToolService.count(Wrappers.lambdaQuery(AgentTool.class)
                .eq(AgentTool::getMcpServerId, id)
                .eq(AgentTool::getDeleted, false));
        if (toolCount > 0) {
            throw new ServerException(422, I18nUtils.getMessage("mcp.server.delete.tools.bound"));
        }
        boolean removed = agentMcpServerService.removeById(id);
        return WebResponse.OK(removed ? I18nUtils.getMessage("agent.mcp-server.delete.success") : I18nUtils.getMessage("agent.mcp-server.delete.fail"));
    }

    /**
     * 连接 MCP 服务并发现远端工具定义。
     */
    @ApiOperation("发现MCP工具")
    @Permission(path = "/agent/mcp-server", type = Permission.Type.Write)
    @PostMapping("/{id}/tools")
    public WebResponse<List<McpToolDefinition>> listTools(@PathVariable @NotBlank String id) {
        AgentMcpServer server = getEnabledServer(id);
        validateTransport(server);
        mcpClient.ping(server);
        return WebResponse.OK(mcpClient.listTools(server));
    }

    /**
     * 将选中的远端 MCP 工具导入本地工具中心。
     */
    @ApiOperation("从MCP服务导入工具")
    @Permission(path = "/agent/mcp-server", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/{id}/import-tools")
    public WebResponse<List<AgentTool>> importTools(@PathVariable @NotBlank String id,
                                                    @RequestBody(required = false) Map<String, List<String>> body) {
        AgentMcpServer server = getEnabledServer(id);
        validateTransport(server);
        mcpClient.ping(server);
        List<String> selectedNames = body == null ? null : body.get("toolNames");
        List<McpToolDefinition> definitions = mcpClient.listTools(server);
        List<AgentTool> imported = new ArrayList<>();
        for (McpToolDefinition definition : definitions) {
            if (definition == null || StringUtils.isBlank(definition.getName())) {
                continue;
            }
            if (selectedNames != null && !selectedNames.isEmpty() && !selectedNames.contains(definition.getName())) {
                continue;
            }
            boolean exists = agentToolService.count(Wrappers.lambdaQuery(AgentTool.class)
                    .eq(AgentTool::getMcpServerId, id)
                    .eq(AgentTool::getMcpToolName, definition.getName())
                    .eq(AgentTool::getDeleted, false)) > 0;
            if (exists) {
                continue;
            }
            AgentTool tool = new AgentTool();
            tool.setMcpServerId(id);
            tool.setMcpToolName(definition.getName());
            tool.setName(definition.getName());
            tool.setCode(uniqueToolCode(server.getCode(), definition.getName()));
            tool.setDescription(definition.getDescription());
            tool.setMcpInputSchema(definition.getInputSchema());
            tool.setTimeoutMs(server.getTimeoutMs());
            tool.setStatus(1);
            agentToolService.save(tool);
            imported.add(tool);
        }
        return WebResponse.OK(imported);
    }

    /**
     * 获取ExistingServer。
     */
    private AgentMcpServer getExistingServer(String id) {
        AgentMcpServer server = agentMcpServerService.getById(id);
        if (server == null || Boolean.TRUE.equals(server.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("mcp.server.not.found"));
        }
        return server;
    }

    /**
     * 获取EnabledServer。
     */
    private AgentMcpServer getEnabledServer(String id) {
        AgentMcpServer server = getExistingServer(id);
        if (!Integer.valueOf(1).equals(server.getStatus())) {
            throw new ServerException(422, I18nUtils.getMessage("mcp.server.disabled"));
        }
        return server;
    }

    /**
     * 处理fillDefaults。
     */
    private void fillDefaults(AgentMcpServer server) {
        if (StringUtils.isBlank(server.getTransport())) {
            server.setTransport("http");
        }
        if (StringUtils.isBlank(server.getAuthType())) {
            server.setAuthType("none");
        }
        if (server.getTimeoutMs() == null) {
            server.setTimeoutMs(DEFAULT_TIMEOUT_MS);
        }
        if (server.getStatus() == null) {
            server.setStatus(1);
        }
    }

    /**
     * 加密Auth令牌。
     */
    private void encryptAuthToken(AgentMcpServer server) {
        if (StringUtils.isNotBlank(server.getAuthToken())) {
            server.setAuthToken(AesUtil.encrypt(server.getAuthToken()));
        } else {
            server.setAuthToken(null);
        }
    }

    /**
     * 处理applyAuth令牌用于更新。
     */
    private void applyAuthTokenForUpdate(AgentMcpServer server, AgentMcpServer existing, AgentMcpServerDto dto) {
        if (Boolean.TRUE.equals(dto.getClearAuthToken())) {
            server.setAuthToken("");
        } else if (StringUtils.isNotBlank(dto.getAuthToken())) {
            server.setAuthToken(AesUtil.encrypt(dto.getAuthToken()));
        } else {
            server.setAuthToken(existing.getAuthToken());
        }
    }

    /**
     * 校验Transport。
     */
    private void validateTransport(AgentMcpServer server) {
        if (StringUtils.isBlank(server.getBaseUrl())) {
            throw new ServerException(422, I18nUtils.getMessage("mcp.server.endpoint.required"));
        }
        if (!mcpClient.supportsTransport(server.getTransport())) {
            throw new ServerException(422, I18nUtils.getMessage("mcp.server.transport.unsupported"));
        }
    }

    /**
     * Keep imported MCP schemas aligned whenever the connection is explicitly
     * updated. Local display fields, status and Agent bindings stay untouched.
     */
    private void refreshImportedToolDefinitions(AgentMcpServer server) {
        List<AgentTool> localTools = agentToolService.list(Wrappers.lambdaQuery(AgentTool.class)
                .eq(AgentTool::getMcpServerId, server.getId()).eq(AgentTool::getDeleted, false));
        if (localTools.isEmpty()) {
            return;
        }
        validateTransport(server);
        mcpClient.ping(server);
        Map<String, McpToolDefinition> definitions = new HashMap<>();
        for (McpToolDefinition definition : mcpClient.listTools(server)) {
            if (definition != null && StringUtils.isNotBlank(definition.getName())) {
                definitions.put(definition.getName(), definition);
            }
        }
        for (AgentTool localTool : localTools) {
            McpToolDefinition definition = definitions.get(localTool.getMcpToolName());
            if (definition == null) {
                continue;
            }
            AgentTool update = new AgentTool();
            update.setId(localTool.getId());
            update.setDescription(definition.getDescription());
            update.setMcpInputSchema(definition.getInputSchema());
            agentToolService.updateById(update);
            if (agentToolCatalog != null) {
                agentToolCatalog.evictByToolId(localTool.getId());
            }
        }
    }

    /**
     * 处理uniqueToolCode。
     */
    private String uniqueToolCode(String serverCode, String toolName) {
        String baseCode = sanitizeCode(StringUtils.defaultIfBlank(serverCode, "mcp") + "_" + toolName);
        String code = baseCode;
        int suffix = 1;
        while (agentToolService.count(Wrappers.lambdaQuery(AgentTool.class)
                .eq(AgentTool::getCode, code)
                .eq(AgentTool::getDeleted, false)) > 0) {
            code = baseCode + "_" + suffix++;
        }
        return code;
    }

    /**
     * 清理敏感信息Code。
     */
    private String sanitizeCode(String value) {
        String code = StringUtils.defaultString(value).toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return StringUtils.defaultIfBlank(code, "mcp_tool");
    }
}
