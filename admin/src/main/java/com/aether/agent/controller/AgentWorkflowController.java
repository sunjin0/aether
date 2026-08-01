package com.aether.agent.controller;

import com.aether.agent.dto.AgentWorkflowDto;
import com.aether.agent.dto.AgentWorkflowInteractionDto;
import com.aether.agent.dto.AgentWorkflowStartDto;
import com.aether.agent.entity.AgentWorkflow;
import com.aether.agent.entity.AgentWorkflowInstance;
import com.aether.agent.entity.AgentWorkflowVersion;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.*;
import com.aether.agent.vo.AgentWorkflowInstanceVo;
import com.aether.agent.vo.AgentWorkflowVo;
import com.aether.agent.workflow.WorkflowDefinitionValidator;
import com.aether.agent.workflow.WorkflowSseHub;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 工作流定义、发布和运行实例 API。
 */
@Api(tags = "AI 工作流 API")
@RestController
@Permission(path = "/agent/workflow")
@RequestMapping("/api/agent/workflow")
public class AgentWorkflowController {
    private final AgentWorkflowService workflowService;
    private final AgentWorkflowVersionService versionService;
    private final AgentWorkflowInstanceService instanceService;
    private final AgentWorkflowExecutionService executionService;
    private final WorkflowSseHub sseHub;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentToolService agentToolService;

    public AgentWorkflowController(AgentWorkflowService workflowService, AgentWorkflowVersionService versionService,
                                   AgentWorkflowInstanceService instanceService, AgentWorkflowExecutionService executionService, WorkflowSseHub sseHub,
                                   AgentDefinitionService agentDefinitionService, AgentToolService agentToolService) {
        this.workflowService = workflowService;
        this.versionService = versionService;
        this.instanceService = instanceService;
        this.executionService = executionService;
        this.sseHub = sseHub;
        this.agentDefinitionService = agentDefinitionService;
        this.agentToolService = agentToolService;
    }

    @ApiOperation("工作流列表")
    @PostMapping("/list")
    public WebResponse<List<AgentWorkflowVo>> list(@RequestBody AgentWorkflowVo query) {
        Page<AgentWorkflow> page = workflowService.page(new Page<AgentWorkflow>(query.getCurrent(), query.getPageSize()), Wrappers.lambdaQuery(AgentWorkflow.class)
                .like(StringUtils.isNotBlank(query.getName()), AgentWorkflow::getName, query.getName()).eq(query.getStatus() != null, AgentWorkflow::getStatus, query.getStatus())
                .eq(AgentWorkflow::getDeleted, false).orderByDesc(AgentWorkflow::getUpdatedAt));
        return WebResponse.Page(page.getRecords().stream().map(item -> {
            AgentWorkflowVo vo = new AgentWorkflowVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList()), page.getTotal());
    }

    @ApiOperation("工作流详情")
    @GetMapping("/{id}")
    public WebResponse<AgentWorkflowVo> detail(@PathVariable String id) {
        AgentWorkflow item = required(id);
        AgentWorkflowVo vo = new AgentWorkflowVo();
        BeanUtils.copyProperties(item, vo);
        if (item.getPublishedVersion() != null) {
            AgentWorkflowVersion version = versionService.getOne(Wrappers.lambdaQuery(AgentWorkflowVersion.class)
                    .eq(AgentWorkflowVersion::getWorkflowId, id)
                    .eq(AgentWorkflowVersion::getVersionNo, item.getPublishedVersion()));
            if (version != null) vo.setPublishedInputSchema(version.getInputSchema());
        }
        return WebResponse.OK(vo);
    }

    @ApiOperation("创建工作流草稿")
    @Permission(path = "/agent/workflow", type = Permission.Type.Write)
    @PostMapping
    public WebResponse<String> create(@RequestBody AgentWorkflowDto dto) {
        AgentWorkflow entity = new AgentWorkflow();
        BeanUtils.copyProperties(dto, entity);
        entity.setStatus(0);
        // 新建草稿即具备一个合法的最小顺序流程，避免尚未编辑画布时保存或发布被空画布校验拦截。
        entity.setNodes("[{\"id\":\"start\",\"type\":\"start\",\"name\":\"开始\",\"position\":{\"x\":80,\"y\":180}},{\"id\":\"end\",\"type\":\"end\",\"name\":\"结束\",\"position\":{\"x\":420,\"y\":180}}]");
        entity.setEdges("[{\"source\":\"start\",\"target\":\"end\"}]");
        entity.setInputSchema("[]");
        workflowService.save(entity);
        return WebResponse.OK(I18nUtils.getMessage("add.success"), entity.getId());
    }

    @ApiOperation("保存工作流画布草稿")
    @Permission(path = "/agent/workflow", type = Permission.Type.Write)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable String id, @RequestBody AgentWorkflowDto dto) {
        AgentWorkflow entity = required(id);
        BeanUtils.copyProperties(dto, entity, "status", "publishedVersion", "agentDefinitionId");
        workflowService.updateById(entity);
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("发布工作流版本")
    @Permission(path = "/agent/workflow", type = Permission.Type.Write)
    @PostMapping("/{id}/publish")
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Integer> publish(@PathVariable String id) {
        AgentWorkflow workflow = required(id);
        WorkflowDefinitionValidator.validate(workflow.getNodes(), workflow.getEdges());
        WorkflowDefinitionValidator.validateVariables(workflow.getNodes(), workflow.getEdges(), workflow.getInputSchema());
        validateResources(workflow.getNodes());
        int number = workflow.getPublishedVersion() == null ? 1 : workflow.getPublishedVersion() + 1;
        AgentWorkflowVersion version = new AgentWorkflowVersion();
        version.setWorkflowId(id);
        version.setVersionNo(number);
        version.setNodes(workflow.getNodes());
        version.setEdges(workflow.getEdges());
        version.setInputSchema(workflow.getInputSchema());
        version.setPublishedAt(System.currentTimeMillis());
        versionService.save(version);
        workflow.setPublishedVersion(number);
        workflow.setStatus(1);
        workflowService.updateById(workflow);
        return WebResponse.OK(number);
    }

    @ApiOperation("下线工作流")
    @Permission(path = "/agent/workflow", type = Permission.Type.Write)
    @PostMapping("/{id}/offline")
    public WebResponse<Void> offline(@PathVariable String id) {
        AgentWorkflow workflow = required(id);
        workflow.setStatus(2);
        workflowService.updateById(workflow);
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("删除工作流")
    @Permission(path = "/agent/workflow", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable String id) {
        required(id);
        workflowService.removeById(id);
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("启动已发布工作流")
    @Permission(path = "/agent/workflow/run", type = Permission.Type.Write)
    @PostMapping("/{id}/instances")
    public WebResponse<String> start(@PathVariable String id, @RequestBody(required = false) AgentWorkflowStartDto dto) {
        AgentWorkflowInstance instance = executionService.start(id, dto == null ? null : dto.getVariables(), userId());
        return WebResponse.OK(I18nUtils.getMessage("request.success"), instance.getId());
    }

    @ApiOperation("流程实例列表")
    @Permission(path = "/agent/workflow/run")
    @PostMapping("/instances/list")
    public WebResponse<List<AgentWorkflowInstanceVo>> instances(@RequestBody AgentWorkflowInstanceVo query) {
        Page<AgentWorkflowInstance> page = instanceService.page(new Page<AgentWorkflowInstance>(query.getCurrent(), query.getPageSize()), Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getUserId, userId()).eq(StringUtils.isNotBlank(query.getWorkflowId()), AgentWorkflowInstance::getWorkflowId, query.getWorkflowId())
                .eq(StringUtils.isNotBlank(query.getStatus()), AgentWorkflowInstance::getStatus, query.getStatus()).orderByDesc(AgentWorkflowInstance::getCreatedAt));
        List<AgentWorkflowInstanceVo> records = page.getRecords().stream().map(item -> executionService.detail(item.getId(), userId())).collect(Collectors.toList());
        return WebResponse.Page(records, page.getTotal());
    }

    @ApiOperation("流程实例详情")
    @Permission(path = "/agent/workflow/run")
    @GetMapping("/instances/{id}")
    public WebResponse<AgentWorkflowInstanceVo> instance(@PathVariable String id) {
        return WebResponse.OK(executionService.detail(id, userId()));
    }

    @ApiOperation("流程实例实时事件")
    @Permission(path = "/agent/workflow/run")
    @GetMapping(value = "/instances/{id}/events", produces = "text/event-stream")
    public SseEmitter events(@PathVariable String id) {
        AgentWorkflowInstanceVo snapshot = executionService.detail(id, userId());
        SseEmitter emitter = sseHub.subscribe(id);
        sseHub.publish(id, "instance.status", snapshot);
        return emitter;
    }

    @ApiOperation("提交人工节点回答或 MCP 确认")
    @Permission(path = "/agent/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/answer")
    public WebResponse<Void> answer(@PathVariable String id, @RequestBody AgentWorkflowInteractionDto dto) {
        executionService.answer(id, dto, userId());
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("重试失败节点")
    @Permission(path = "/agent/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/retry")
    public WebResponse<Void> retry(@PathVariable String id) {
        executionService.retry(id, userId());
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("终止流程实例")
    @Permission(path = "/agent/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/terminate")
    public WebResponse<Void> terminate(@PathVariable String id) {
        executionService.terminate(id, userId());
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("运行中修改开始变量")
    @Permission(path = "/agent/workflow/run", type = Permission.Type.Write)
    @PutMapping("/instances/{id}/variables")
    public WebResponse<Void> updateVariables(@PathVariable String id, @RequestBody(required = false) AgentWorkflowStartDto dto) {
        executionService.updateVariables(id, dto == null ? null : dto.getVariables(), userId());
        return WebResponse.OK((Void) null);
    }

    private AgentWorkflow required(String id) {
        AgentWorkflow value = workflowService.getById(id);
        if (value == null || Boolean.TRUE.equals(value.getDeleted())) throw new ServerException(404, "工作流不存在");
        return value;
    }

    private void validateResources(String nodes) {
        for (Object value : JSONArray.parseArray(nodes)) {
            JSONObject node = (JSONObject) value;
            String type = node.getString("type"), resourceId = node.getString("resourceId");
            if ("agent".equals(type)) {
                AgentDefinition agent = agentDefinitionService.getById(resourceId);
                if (agent == null || Boolean.TRUE.equals(agent.getDeleted()) || !Integer.valueOf(1).equals(agent.getStatus()))
                    throw new ServerException(422, "节点选择的普通 Agent 不存在或未启用");
            }
            if ("mcp".equals(type)) {
                AgentTool tool = agentToolService.getById(resourceId);
                if (tool == null || Boolean.TRUE.equals(tool.getDeleted()) || !Integer.valueOf(1).equals(tool.getStatus()))
                    throw new ServerException(422, "节点选择的 MCP 工具不存在或未启用");
            }
        }
    }

    private String userId() {
        Map<String, String> user = CurrentUser.getUser();
        if (user == null || StringUtils.isBlank(user.get("userId"))) throw new ServerException(401, "登录状态已失效");
        return user.get("userId");
    }
}
