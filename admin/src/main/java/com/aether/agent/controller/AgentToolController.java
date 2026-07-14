package com.aether.agent.controller;

import com.aether.agent.dto.AgentToolDto;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.executor.ToolExecutor;
import com.aether.agent.executor.ToolExecutorFactory;
import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.vo.AgentToolVo;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final AgentMcpServerService agentMcpServerService;
    private final ToolExecutorFactory toolExecutorFactory;

    @Autowired
    public AgentToolController(AgentToolService agentToolService,
                               AgentMcpServerService agentMcpServerService,
                               ToolExecutorFactory toolExecutorFactory) {
        this.agentToolService = agentToolService;
        this.agentMcpServerService = agentMcpServerService;
        this.toolExecutorFactory = toolExecutorFactory;
    }

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
                .eq(StringUtils.isNotBlank(vo.getMcpServerId()), AgentTool::getMcpServerId, vo.getMcpServerId())
                .eq(vo.getStatus() != null, AgentTool::getStatus, vo.getStatus())
                .eq(AgentTool::getDeleted, false)
                .orderByDesc(AgentTool::getCreatedAt);
        Page<AgentTool> result = agentToolService.page(page, wrapper);
        List<AgentToolVo> list = result.getRecords().stream().map(item -> {
            AgentToolVo itemVo = new AgentToolVo();
            BeanUtils.copyProperties(item, itemVo);
            fillMcpServerInfo(itemVo);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    @ApiOperation("工具详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工具ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}")
    public WebResponse<AgentToolVo> detail(@PathVariable @NotBlank String id) {
        AgentTool tool = agentToolService.getById(id);
        if (tool == null || Boolean.TRUE.equals(tool.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        AgentToolVo vo = new AgentToolVo();
        BeanUtils.copyProperties(tool, vo);
        fillMcpServerInfo(vo);
        return WebResponse.OK(vo);
    }

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
        return WebResponse.OK(saved ? I18nUtils.getMessage("add.success") : I18nUtils.getMessage("add.fail"), tool.getId());
    }

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
        return WebResponse.OK(updated ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"));
    }

    @ApiOperation("删除工具")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工具ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/tool", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        boolean removed = agentToolService.removeById(id);
        return WebResponse.OK(removed ? I18nUtils.getMessage("delete.success") : I18nUtils.getMessage("delete.fail"));
    }

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
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }

        if (!Integer.valueOf(1).equals(tool.getStatus())) {
            throw new ServerException(422, "工具未启用");
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
                return WebResponse.OK("工具测试成功", result);
            } else {
                return WebResponse.Error(result.getStatus(), "工具测试失败: " + result.getErrorMsg(), result);
            }
        } catch (Exception e) {
            ToolExecutionResult errorResult = ToolExecutionResult.failure(e.getMessage(), 1);
            return WebResponse.Error(500, "工具测试异常: " + e.getMessage(), errorResult);
        }
    }
    
    @ApiOperation("获取模拟用户信息")
    @Permission(required = false)
    @GetMapping("/user/info")
    @ResponseBody
    public WebResponse<Map<String, Object>> info(@RequestParam("name") @NotBlank String name) {
        //生成模拟个人数据（随机）
        Map<String, Object> info = new HashMap<>();
        info.put("name", name);
        info.put("age", 18 + (int)(Math.random() * 50));
        info.put("gender", Math.random() > 0.5 ? "male" : "female");
        info.put("height", 150 + Math.random() * 40);
        info.put("weight", 45 + Math.random() * 50);
        info.put("birthday", String.format("%d-%02d-%02d", 
                1970 + (int)(Math.random() * 35), 
                1 + (int)(Math.random() * 12), 
                1 + (int)(Math.random() * 28)));
        info.put("id", String.valueOf((long)(Math.random() * 9000000000L) + 1000000000L));
        String[] cities = {"Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Hangzhou", "Chengdu"};
        info.put("address", cities[(int)(Math.random() * cities.length)]);
        info.put("phone", "+86" + (long)(Math.random() * 9000000000L + 1000000000L));
        info.put("email", name.toLowerCase() + (int)(Math.random() * 1000) + "@example.com");
        String[] edu = {"高中", "大专", "本科", "硕士", "博士"};
        info.put("education", edu[(int)(Math.random() * edu.length)]);
        String[] majors = {"Computer Science", "Mathematics", "Physics", "Business", "Engineering"};
        info.put("major", majors[(int)(Math.random() * majors.length)]);
        String[] schools = {"Peking University", "Tsinghua University", "Fudan University", "Zhejiang University"};
        info.put("school", schools[(int)(Math.random() * schools.length)]);
        String[] degrees = {"学士", "硕士", "博士"};
        info.put("degree", degrees[(int)(Math.random() * degrees.length)]);
        info.put("graduation_year", 2015 + (int)(Math.random() * 11));
        info.put("is_student", Math.random() > 0.7);
        return WebResponse.OK(info);
    }
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

    private void validateMcpServer(String mcpServerId) {
        if (StringUtils.isBlank(mcpServerId)) {
            throw new ServerException(422, "MCP服务不能为空");
        }
        AgentMcpServer server = agentMcpServerService.getById(mcpServerId);
        if (server == null || Boolean.TRUE.equals(server.getDeleted())) {
            throw new ServerException(404, "MCP服务不存在");
        }
    }

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
}
