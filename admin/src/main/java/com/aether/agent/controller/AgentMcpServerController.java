package com.aether.agent.controller;

import com.aether.agent.dto.AgentMcpServerDto;
import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.mcp.McpClient;
import com.aether.agent.mcp.McpToolDefinition;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.vo.AgentMcpServerVo;
import com.aether.entity.WebResponse;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
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

    public AgentMcpServerController(AgentMcpServerService agentMcpServerService,
                                    AgentToolService agentToolService,
                                    McpClient mcpClient) {
        this.agentMcpServerService = agentMcpServerService;
        this.agentToolService = agentToolService;
        this.mcpClient = mcpClient;
    }

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

    @ApiOperation("MCP服务详情")
    @GetMapping("/{id}")
    public WebResponse<AgentMcpServerVo> detail(@PathVariable @NotBlank String id) {
        AgentMcpServer server = getExistingServer(id);
        AgentMcpServerVo vo = new AgentMcpServerVo();
        BeanUtils.copyProperties(server, vo);
        vo.setAuthToken(null);
        return WebResponse.OK(vo);
    }

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
        return WebResponse.OK(saved ? I18nUtils.getMessage("add.success") : I18nUtils.getMessage("add.fail"), server.getId());
    }

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
        return WebResponse.OK(updated ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"));
    }

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
        return WebResponse.OK(removed ? I18nUtils.getMessage("delete.success") : I18nUtils.getMessage("delete.fail"));
    }

    @ApiOperation("发现MCP工具")
    @Permission(path = "/agent/mcp-server", type = Permission.Type.Write)
    @PostMapping("/{id}/tools")
    public WebResponse<List<McpToolDefinition>> listTools(@PathVariable @NotBlank String id) {
        AgentMcpServer server = getEnabledServer(id);
        validateTransport(server);
        mcpClient.ping(server);
        return WebResponse.OK(mcpClient.listTools(server));
    }

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

    private AgentMcpServer getExistingServer(String id) {
        AgentMcpServer server = agentMcpServerService.getById(id);
        if (server == null || Boolean.TRUE.equals(server.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        return server;
    }

    private AgentMcpServer getEnabledServer(String id) {
        AgentMcpServer server = getExistingServer(id);
        if (!Integer.valueOf(1).equals(server.getStatus())) {
            throw new ServerException(422, I18nUtils.getMessage("mcp.server.disabled"));
        }
        return server;
    }

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

    private void encryptAuthToken(AgentMcpServer server) {
        if (StringUtils.isNotBlank(server.getAuthToken())) {
            server.setAuthToken(AesUtil.encrypt(server.getAuthToken()));
        } else {
            server.setAuthToken(null);
        }
    }

    private void applyAuthTokenForUpdate(AgentMcpServer server, AgentMcpServer existing, AgentMcpServerDto dto) {
        if (Boolean.TRUE.equals(dto.getClearAuthToken())) {
            server.setAuthToken("");
        } else if (StringUtils.isNotBlank(dto.getAuthToken())) {
            server.setAuthToken(AesUtil.encrypt(dto.getAuthToken()));
        } else {
            server.setAuthToken(existing.getAuthToken());
        }
    }

    private void validateTransport(AgentMcpServer server) {
        if (StringUtils.isBlank(server.getBaseUrl())) {
            throw new ServerException(422, I18nUtils.getMessage("mcp.server.endpoint.required"));
        }
        if (!mcpClient.supportsTransport(server.getTransport())) {
            throw new ServerException(422, I18nUtils.getMessage("mcp.server.transport.unsupported"));
        }
    }

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

    private String sanitizeCode(String value) {
        String code = StringUtils.defaultString(value).toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return StringUtils.defaultIfBlank(code, "mcp_tool");
    }
}
