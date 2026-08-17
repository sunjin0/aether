package com.aether.agent.controller;

import com.aether.agent.dto.AgentToolDto;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.executor.ToolExecutor;
import com.aether.agent.executor.ToolExecutorFactory;
import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.mcp.McpClient;
import com.aether.agent.mcp.McpToolDefinition;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.agent.vo.AgentToolCallLogVo;
import com.aether.agent.vo.AgentToolCallStatisticsVo;
import com.aether.agent.vo.AgentToolFacetsVo;
import com.aether.agent.vo.AgentToolStatisticsVo;
import com.aether.agent.vo.AgentToolVo;
import com.aether.entity.WebResponse;
import com.aether.entity.Option;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.aether.sys.service.DictService;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具管理 Controller
 */
@Api(tags = "工具管理 API")
@Validated
@RestController
@Permission(path = "/agent/tool")
@RequestMapping("/api/agent/tool")
public class AgentToolController {

    private static final int DEFAULT_TOOL_TIMEOUT_MS = 30000;

    private final AgentToolService agentToolService;
    private final AgentToolCallLogService agentToolCallLogService;
    private final AgentMcpServerService agentMcpServerService;
    private final ToolExecutorFactory toolExecutorFactory;
    private final DictService dictService;

    @Autowired(required = false)
    private McpClient mcpClient;
    @Autowired(required = false)
    private AgentToolCatalog agentToolCatalog;

    /**
     * 创建 {@code AgentToolController} 实例。
     */
    @Autowired
    public AgentToolController(AgentToolService agentToolService,
                               AgentToolCallLogService agentToolCallLogService,
                               AgentMcpServerService agentMcpServerService,
                               ToolExecutorFactory toolExecutorFactory,
                               DictService dictService) {
        this.agentToolService = agentToolService;
        this.agentToolCallLogService = agentToolCallLogService;
        this.agentMcpServerService = agentMcpServerService;
        this.toolExecutorFactory = toolExecutorFactory;
        this.dictService = dictService;
    }

    /**
     * 分页查询工具，并支持 MCP 服务、状态和关键词筛选。
     */
    @ApiOperation("工具列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<AgentToolVo>> list(@RequestBody AgentToolVo vo) {
        Page<AgentTool> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<AgentTool> wrapper = Wrappers.lambdaQuery(AgentTool.class)
                .like(StringUtils.isNotBlank(vo.getName()), AgentTool::getName, vo.getName())
                .like(StringUtils.isNotBlank(vo.getCode()), AgentTool::getCode, vo.getCode())
                .eq(StringUtils.isNotBlank(vo.getToolType()), AgentTool::getToolType, vo.getToolType())
                .eq(StringUtils.isNotBlank(vo.getMcpServerId()), AgentTool::getMcpServerId, vo.getMcpServerId())
                .eq(vo.getStatus() != null, AgentTool::getStatus, vo.getStatus())
                .eq(AgentTool::getDeleted, false)
                .orderByDesc(AgentTool::getCreatedAt);
        Page<AgentTool> result = agentToolService.page(page, wrapper);
        Map<String, AgentToolCallStatisticsVo> statisticsMap = agentToolCallLogService.toolStatisticsMap(buildToolCallLogQuery(vo));
        List<AgentToolVo> list = result.getRecords().stream().map(item -> {
            AgentToolVo itemVo = new AgentToolVo();
            BeanUtils.copyProperties(item, itemVo);
            fillMcpServerInfo(itemVo);
            fillCallStatistics(itemVo, statisticsMap);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    /**
     * 查询可绑定的启用工具选项。
     * mcpServerId 不为空时，仅返回指定 MCP 服务下的工具。
     */
    @ApiOperation("Agent工具下拉选项")
    @Permission(required = false)
    @GetMapping("/options")
    public WebResponse<List<Option>> options(@RequestParam(value = "mcpServerId", required = false) String mcpServerId) {
        List<Option> options = agentToolService.list(Wrappers.lambdaQuery(AgentTool.class)
                        .eq(StringUtils.isNotBlank(mcpServerId), AgentTool::getMcpServerId, mcpServerId)
                        .eq(AgentTool::getStatus, 1).eq(AgentTool::getDeleted, false)
                        .orderByAsc(AgentTool::getName))
                .stream().map(item -> new Option(StringUtils.defaultIfBlank(item.getName(), item.getCode()), item.getId())).collect(Collectors.toList());
        return WebResponse.OK(options);
    }

    /**
     * Manually refresh the remote MCP schema for one already imported tool.
     */
    @ApiOperation("更新MCP工具定义")
    @Permission(path = "/agent/tool", type = Permission.Type.Write)
    @PostMapping("/{id}/refresh-definition")
    public WebResponse<Void> refreshDefinition(@PathVariable @NotBlank String id) {
        AgentTool tool = agentToolService.getById(id);
        if (tool == null || Boolean.TRUE.equals(tool.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.tool.not-found"));
        }
        if (StringUtils.isBlank(tool.getMcpServerId()) || StringUtils.isBlank(tool.getMcpToolName()) || mcpClient == null) {
            throw new ServerException(422, I18nUtils.getMessage("agent.tool.refresh-definition.unsupported"));
        }
        AgentMcpServer server = agentMcpServerService.getById(tool.getMcpServerId());
        if (server == null || Boolean.TRUE.equals(server.getDeleted()) || !Integer.valueOf(1).equals(server.getStatus())) {
            throw new ServerException(422, I18nUtils.getMessage("mcp.server.disabled"));
        }
        if (StringUtils.isBlank(server.getBaseUrl()) || !mcpClient.supportsTransport(server.getTransport())) {
            throw new ServerException(422, I18nUtils.getMessage("mcp.server.transport.unsupported"));
        }
        mcpClient.ping(server);
        McpToolDefinition remote = mcpClient.listTools(server).stream()
                .filter(item -> item != null && tool.getMcpToolName().equals(item.getName()))
                .findFirst().orElseThrow(() -> new ServerException(404, I18nUtils.getMessage("agent.tool.refresh-definition.not-found")));
        AgentTool update = new AgentTool();
        update.setId(tool.getId());
        update.setDescription(remote.getDescription());
        update.setMcpInputSchema(remote.getInputSchema());
        agentToolService.updateById(update);
        if (agentToolCatalog != null) agentToolCatalog.evictByToolId(tool.getId());
        return WebResponse.OK(I18nUtils.getMessage("agent.tool.refresh-definition.success"));
    }

    /**
     * Refreshes selected MCP-imported tools in one request. Each MCP server is
     * queried once so the client receives one consolidated operation result.
     */
    @ApiOperation("批量更新MCP工具定义")
    @Permission(path = "/agent/tool", type = Permission.Type.Write)
    @PostMapping("/batch-refresh-definition")
    public WebResponse<Map<String, Integer>> batchRefreshDefinition(@RequestBody Map<String, List<String>> body) {
        List<String> ids = body == null ? Collections.emptyList() : body.get("toolIds");
        if (ids == null || ids.isEmpty()) {
            throw new ServerException(400, I18nUtils.getMessage("agent.tool.refresh-definition.unsupported"));
        }
        List<AgentTool> tools = agentToolService.listByIds(ids).stream()
                .filter(tool -> !Boolean.TRUE.equals(tool.getDeleted()) && StringUtils.isNotBlank(tool.getMcpServerId())
                        && StringUtils.isNotBlank(tool.getMcpToolName()))
                .collect(Collectors.toList());
        Map<String, List<AgentTool>> toolsByServer = tools.stream()
                .collect(Collectors.groupingBy(AgentTool::getMcpServerId));
        int succeeded = 0;
        int failed = ids.size() - tools.size();
        for (Map.Entry<String, List<AgentTool>> entry : toolsByServer.entrySet()) {
            try {
                AgentMcpServer server = agentMcpServerService.getById(entry.getKey());
                if (server == null || Boolean.TRUE.equals(server.getDeleted()) || !Integer.valueOf(1).equals(server.getStatus())
                        || StringUtils.isBlank(server.getBaseUrl()) || mcpClient == null || !mcpClient.supportsTransport(server.getTransport())) {
                    failed += entry.getValue().size();
                    continue;
                }
                mcpClient.ping(server);
                Map<String, McpToolDefinition> definitions = mcpClient.listTools(server).stream()
                        .filter(item -> item != null && StringUtils.isNotBlank(item.getName()))
                        .collect(Collectors.toMap(McpToolDefinition::getName, item -> item, (left, right) -> left));
                for (AgentTool tool : entry.getValue()) {
                    McpToolDefinition remote = definitions.get(tool.getMcpToolName());
                    if (remote == null) {
                        failed++;
                        continue;
                    }
                    AgentTool update = new AgentTool();
                    update.setId(tool.getId());
                    if (tool.getMcpToolName().equals(tool.getName())) update.setName(remote.getName());
                    update.setDescription(remote.getDescription());
                    update.setMcpInputSchema(remote.getInputSchema());
                    update.setTimeoutMs(server.getTimeoutMs());
                    agentToolService.updateById(update);
                    if (agentToolCatalog != null) agentToolCatalog.evictByToolId(tool.getId());
                    succeeded++;
                }
            } catch (Exception ignored) {
                failed += entry.getValue().size();
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", ids.size());
        result.put("succeeded", succeeded);
        result.put("failed", failed);
        return WebResponse.OK(result);
    }

    /**
     * 汇总工具中心的来源、类型和状态筛选项。
     */
    @ApiOperation("工具中心筛选聚合")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/facets")
    public WebResponse<AgentToolFacetsVo> facets() {
        List<AgentTool> tools = agentToolService.list(Wrappers.lambdaQuery(AgentTool.class)
                .eq(AgentTool::getDeleted, false));
        AgentToolFacetsVo facets = new AgentToolFacetsVo();
        if (tools.isEmpty()) {
            return WebResponse.OK(facets);
        }

        Map<String, Long> categoryCounts = tools.stream()
                .filter(tool -> StringUtils.isNotBlank(tool.getToolType()))
                .collect(Collectors.groupingBy(AgentTool::getToolType, Collectors.counting()));
        Map<String, String> categoryLabels = dictService.getOptions("Agent_Tool_Business_Type", true).stream()
                .filter(option -> option.getValue() != null)
                .collect(Collectors.toMap(option -> String.valueOf(option.getValue()), Option::getLabel, (left, right) -> left));
        categoryCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> facets.getCategories().add(new AgentToolFacetsVo.Item(
                        entry.getKey(), categoryLabels.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue())));

        Map<Integer, Long> statusCounts = tools.stream()
                .filter(tool -> Integer.valueOf(0).equals(tool.getStatus()) || Integer.valueOf(1).equals(tool.getStatus()))
                .collect(Collectors.groupingBy(AgentTool::getStatus, Collectors.counting()));
        addStatusFacet(facets.getStatuses(), statusCounts, 1, "已集成");
        addStatusFacet(facets.getStatuses(), statusCounts, 0, "未集成");

        List<String> serverIds = tools.stream()
                .map(AgentTool::getMcpServerId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> serverNames = serverIds.isEmpty() ? Collections.emptyMap() : agentMcpServerService.list(
                        Wrappers.lambdaQuery(AgentMcpServer.class)
                                .in(AgentMcpServer::getId, serverIds)
                                .eq(AgentMcpServer::getDeleted, false))
                .stream()
                .collect(Collectors.toMap(AgentMcpServer::getId, AgentMcpServer::getName));
        Map<String, Long> sourceCounts = tools.stream()
                .map(AgentTool::getMcpServerId)
                .map(id -> StringUtils.defaultIfBlank(id, "none"))
                .filter(id -> "none".equals(id) || serverNames.containsKey(id))
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
        sourceCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> facets.getSources().add(new AgentToolFacetsVo.Item(
                        entry.getKey(), sourceLabel(entry.getKey(), serverNames), entry.getValue())));
        return WebResponse.OK(facets);
    }

    /**
     * Tool statistics。
     */
    @ApiOperation("Tool statistics")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "璁块棶浠ょ墝", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/statistics")
    public WebResponse<AgentToolStatisticsVo> statistics(@RequestParam(required = false) String toolType,
                                                         @RequestParam(required = false) String mcpServerId) {
        AgentToolVo query = new AgentToolVo();
        query.setToolType(toolType);
        query.setMcpServerId(mcpServerId);

        AgentToolStatisticsVo statistics = new AgentToolStatisticsVo();
        statistics.setTotalCount(agentToolService.count(toolCountWrapper(query, null)));
        statistics.setEnabledCount(agentToolService.count(toolCountWrapper(query, 1)));
        statistics.setDisabledCount(agentToolService.count(toolCountWrapper(query, 0)));

        List<AgentTool> tools = agentToolService.list(toolCountWrapper(query, null));
        Map<String, AgentToolCallStatisticsVo> callStatisticsMap = agentToolCallLogService.toolStatisticsMap(buildToolCallLogQuery(query));
        long callCount = 0L;
        long successCount = 0L;
        for (AgentTool tool : tools) {
            AgentToolCallStatisticsVo item = callStatisticsMap.get(tool.getId());
            if (item == null) {
                continue;
            }
            callCount += defaultLong(item.getCallCount());
            successCount += defaultLong(item.getSuccessCount());
        }
        statistics.setCallCount(callCount);
        statistics.setSuccessCount(successCount);
        statistics.setSuccessRate(callCount == 0 ? 0D : successCount * 100D / callCount);
        return WebResponse.OK(statistics);
    }

    /**
     * 查询工具详情及其 MCP 配置。
     */
    @ApiOperation("工具详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工具ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}")
    public WebResponse<AgentToolVo> detail(@PathVariable @NotBlank String id) {
        AgentTool tool = agentToolService.getById(id);
        if (tool == null || Boolean.TRUE.equals(tool.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.tool.not-found"));
        }
        AgentToolVo vo = new AgentToolVo();
        BeanUtils.copyProperties(tool, vo);
        fillMcpServerInfo(vo);
        return WebResponse.OK(vo);
    }

    /**
     * 创建工具定义。
     */
    @ApiOperation("新增工具")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/tool", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping
    public WebResponse<String> save(@RequestBody AgentToolDto dto) {
        AgentTool tool = new AgentTool();
        BeanUtils.copyProperties(dto, tool);
        fillToolDefaults(tool);
        boolean saved = agentToolService.save(tool);
        return WebResponse.OK(saved ? I18nUtils.getMessage("agent.tool.create.success") : I18nUtils.getMessage("agent.tool.create.fail"), tool.getId());
    }

    /**
     * 更新工具定义。
     */
    @ApiOperation("编辑工具")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/tool", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable @NotBlank String id, @RequestBody AgentToolDto dto) {
        AgentTool tool = new AgentTool();
        BeanUtils.copyProperties(dto, tool);
        tool.setId(id);
        fillToolDefaults(tool);
        boolean updated = agentToolService.updateById(tool);
        return WebResponse.OK(updated ? I18nUtils.getMessage("agent.tool.update.success") : I18nUtils.getMessage("agent.tool.update.fail"));
    }

    /**
     * 软删除工具定义。
     */
    @ApiOperation("删除工具")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工具ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/tool", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        boolean removed = agentToolService.removeById(id);
        return WebResponse.OK(removed ? I18nUtils.getMessage("agent.tool.delete.success") : I18nUtils.getMessage("agent.tool.delete.fail"));
    }

    /**
     * 测试Tool。
     */
    @ApiOperation("测试工具")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/tool", type = Permission.Type.Write)
    @PostMapping("/{id}/test")
    public WebResponse<ToolExecutionResult> testTool(@PathVariable @NotBlank String id,
                                                     @RequestBody Map<String, Object> params) {
        // 1. 获取工具配置
        AgentTool tool = agentToolService.getById(id);
        if (tool == null || Boolean.TRUE.equals(tool.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.tool.not-found"));
        }

        if (!Integer.valueOf(1).equals(tool.getStatus())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.tool.disabled"));
        }

        // 2. 构建执行上下文
        ToolExecutionContext context = new ToolExecutionContext();
        context.setTool(tool);
        context.setArguments(params);
        context.setUserId("test");
        context.setRunId(null);

        // 3. 执行工具
        try {
            ToolExecutor executor = toolExecutorFactory.getExecutor("mcp");
            ToolExecutionResult result = executor.execute(context);

            if (result.isSuccess()) {
                return WebResponse.OK(I18nUtils.getMessage("agent.tool.test.success"), result);
            } else {
                ToolExecutionResult errorResult = ToolExecutionResult.failure(I18nUtils.getMessage("agent.tool.test.failed"), result.getStatus());
                return WebResponse.Error(result.getStatus(), I18nUtils.getMessage("agent.tool.test.failed"), errorResult);
            }
        } catch (Exception e) {
            ToolExecutionResult errorResult = ToolExecutionResult.failure(I18nUtils.getMessage("agent.tool.test.failed"), 1);
            return WebResponse.Error(500, I18nUtils.getMessage("agent.tool.test.failed"), errorResult);
        }
    }

    /**
     * 获取模拟用户信息。
     */
    @ApiOperation("获取模拟用户信息")
    @Permission(required = false)
    @GetMapping("/user/info")
    @ResponseBody
    public WebResponse<Map<String, Object>> info(@RequestParam("name") @NotBlank String name) {
        //生成模拟个人数据（随机）
        Map<String, Object> info = new HashMap<>();
        info.put("name", name);
        info.put("age", 18 + (int) (Math.random() * 50));
        info.put("gender", Math.random() > 0.5 ? "male" : "female");
        info.put("height", 150 + Math.random() * 40);
        info.put("weight", 45 + Math.random() * 50);
        info.put("birthday", String.format("%d-%02d-%02d",
                1970 + (int) (Math.random() * 35),
                1 + (int) (Math.random() * 12),
                1 + (int) (Math.random() * 28)));
        info.put("id", String.valueOf((long) (Math.random() * 9000000000L) + 1000000000L));
        String[] cities = {"Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Hangzhou", "Chengdu"};
        info.put("address", cities[(int) (Math.random() * cities.length)]);
        info.put("phone", "+86" + (long) (Math.random() * 9000000000L + 1000000000L));
        info.put("email", name.toLowerCase() + (int) (Math.random() * 1000) + "@example.com");
        String[] edu = {"高中", "大专", "本科", "硕士", "博士"};
        info.put("education", edu[(int) (Math.random() * edu.length)]);
        String[] majors = {"Computer Science", "Mathematics", "Physics", "Business", "Engineering"};
        info.put("major", majors[(int) (Math.random() * majors.length)]);
        String[] schools = {"Peking University", "Tsinghua University", "Fudan University", "Zhejiang University"};
        info.put("school", schools[(int) (Math.random() * schools.length)]);
        String[] degrees = {"学士", "硕士", "博士"};
        info.put("degree", degrees[(int) (Math.random() * degrees.length)]);
        info.put("graduation_year", 2015 + (int) (Math.random() * 11));
        info.put("is_student", Math.random() > 0.7);
        return WebResponse.OK(info);
    }

    /**
     * 处理fillToolDefaults。
     */
    private void fillToolDefaults(AgentTool tool) {
        validateMcpServer(tool.getMcpServerId());
        if (StringUtils.isBlank(tool.getMcpToolName())) {
            tool.setMcpToolName(tool.getName());
        }
        if (tool.getStatus() == null) {
            tool.setStatus(1);
        }
        if (tool.getTimeoutMs() == null) {
            tool.setTimeoutMs(DEFAULT_TOOL_TIMEOUT_MS);
        }
    }

    /**
     * 校验McpServer。
     */
    private void validateMcpServer(String mcpServerId) {
        if (StringUtils.isBlank(mcpServerId)) {
            throw new ServerException(422, I18nUtils.getMessage("mcp.server.required"));
        }
        AgentMcpServer server = agentMcpServerService.getById(mcpServerId);
        if (server == null || Boolean.TRUE.equals(server.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("mcp.server.not.found"));
        }
    }

    /**
     * 处理fillMcpServerInfo。
     */
    private void fillMcpServerInfo(AgentToolVo vo) {
        if (StringUtils.isBlank(vo.getMcpServerId())) {
            return;
        }
        AgentMcpServer server = agentMcpServerService.getById(vo.getMcpServerId());
        if (server != null && !Boolean.TRUE.equals(server.getDeleted())) {
            vo.setMcpServerName(server.getName());
            vo.setMcpBaseUrl(server.getBaseUrl());
        }
    }

    /**
     * 新增状态Facet。
     */
    private void addStatusFacet(List<AgentToolFacetsVo.Item> statuses, Map<Integer, Long> counts,
                                int status, String label) {
        Long count = counts.get(status);
        if (count != null) {
            statuses.add(new AgentToolFacetsVo.Item(status, label, count));
        }
    }

    /**
     * 处理sourceLabel。
     */
    private String sourceLabel(String sourceId, Map<String, String> serverNames) {
        if ("none".equals(sourceId)) {
            return "无来源";
        }
        return serverNames.get(sourceId);
    }

    /**
     * 处理tool统计Wrapper。
     */
    private Wrapper<AgentTool> toolCountWrapper(AgentToolVo vo, Integer status) {
        return Wrappers.lambdaQuery(AgentTool.class)
                .eq(StringUtils.isNotBlank(vo.getToolType()), AgentTool::getToolType, vo.getToolType())
                .eq(StringUtils.isNotBlank(vo.getMcpServerId()), AgentTool::getMcpServerId, vo.getMcpServerId())
                .eq(status != null, AgentTool::getStatus, status)
                .eq(AgentTool::getDeleted, false);
    }

    /**
     * 构建ToolCallLog查询。
     */
    private AgentToolCallLogVo buildToolCallLogQuery(AgentToolVo vo) {
        AgentToolCallLogVo query = new AgentToolCallLogVo();
        query.setToolType(vo.getToolType());
        return query;
    }

    /**
     * 处理fillCallStatistics。
     */
    private void fillCallStatistics(AgentToolVo vo, Map<String, AgentToolCallStatisticsVo> statisticsMap) {
        AgentToolCallStatisticsVo statistics = statisticsMap.get(vo.getId());
        if (statistics == null) {
            vo.setCallCount(0L);
            vo.setSuccessRate(0D);
            return;
        }
        vo.setCallCount(statistics.getCallCount());
        vo.setSuccessRate(statistics.getSuccessRate());
    }

    /**
     * 处理defaultLong。
     */
    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
