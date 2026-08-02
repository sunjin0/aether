package com.aether.workflow.controller;

import com.aether.workflow.dto.AgentWorkflowDto;
import com.aether.workflow.dto.AgentWorkflowInteractionDto;
import com.aether.workflow.dto.AgentWorkflowStartDto;
import com.aether.workflow.dto.AgentWorkflowBusinessStartDto;
import com.aether.workflow.dto.AgentWorkflowWebhookTriggerDto;
import com.aether.workflow.dto.AgentWorkflowTemplateDto;
import com.aether.workflow.dto.AgentWorkflowScheduleTriggerDto;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentToolService;
import com.aether.workflow.entity.AgentWorkflowCallbackDelivery;
import com.aether.workflow.service.*;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import com.aether.workflow.vo.AgentWorkflowVo;
import com.aether.workflow.vo.AgentWorkflowWebhookTriggerVo;
import com.aether.workflow.vo.AgentWorkflowWebhookTriggerSecretVo;
import com.aether.workflow.vo.AgentWorkflowOperationsMetricsVo;
import com.aether.workflow.vo.AgentWorkflowDeadLetterVo;
import com.aether.workflow.vo.AgentWorkflowVersionVo;
import com.aether.workflow.vo.AgentWorkflowVersionDiffVo;
import com.aether.workflow.runtime.WorkflowDefinitionValidator;
import com.aether.workflow.runtime.WorkflowSseHub;
import com.aether.workflow.runtime.WorkflowCallbackService;
import com.aether.sys.service.ServiceAccountService;
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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 工作流定义、发布和运行实例 API。
 */
@Api(tags = "AI 工作流 API")
@RestController
@Permission(path = "/workflow/workflow")
@RequestMapping("/api/agent/workflow")
public class AgentWorkflowController {
    private final AgentWorkflowService workflowService;
    private final AgentWorkflowVersionService versionService;
    private final AgentWorkflowInstanceService instanceService;
    private final AgentWorkflowExecutionService executionService;
    private final WorkflowSseHub sseHub;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentToolService agentToolService;
    private final AgentWorkflowCallbackDeliveryService callbackDeliveryService;
    private final WorkflowCallbackService workflowCallbackService;
    private final ServiceAccountService serviceAccountService;
    private final AgentWorkflowWebhookTriggerService webhookTriggerService;
    private final AgentWorkflowOperationsService operationsService;
    private final AgentWorkflowTemplateService templateService;
    private final AgentWorkflowScheduleTriggerService scheduleTriggerService;

    public AgentWorkflowController(AgentWorkflowService workflowService, AgentWorkflowVersionService versionService,
                                   AgentWorkflowInstanceService instanceService, AgentWorkflowExecutionService executionService, WorkflowSseHub sseHub,
                                   AgentDefinitionService agentDefinitionService, AgentToolService agentToolService,
                                   AgentWorkflowCallbackDeliveryService callbackDeliveryService,
                                   WorkflowCallbackService workflowCallbackService, ServiceAccountService serviceAccountService,
                                   AgentWorkflowWebhookTriggerService webhookTriggerService, AgentWorkflowOperationsService operationsService,
                                   AgentWorkflowTemplateService templateService, AgentWorkflowScheduleTriggerService scheduleTriggerService) {
        this.workflowService = workflowService;
        this.versionService = versionService;
        this.instanceService = instanceService;
        this.executionService = executionService;
        this.sseHub = sseHub;
        this.agentDefinitionService = agentDefinitionService;
        this.agentToolService = agentToolService;
        this.callbackDeliveryService = callbackDeliveryService;
        this.workflowCallbackService = workflowCallbackService;
        this.serviceAccountService = serviceAccountService;
        this.webhookTriggerService = webhookTriggerService;
        this.operationsService = operationsService;
        this.templateService = templateService;
        this.scheduleTriggerService = scheduleTriggerService;
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
            if (version != null) {
                vo.setPublishedInputSchema(version.getInputSchema());
                vo.setPublishedOutputSchema(version.getOutputSchema());
            }
        }
        return WebResponse.OK(vo);
    }

    @ApiOperation("创建工作流草稿")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping
    public WebResponse<String> create(@RequestBody AgentWorkflowDto dto) {
        validateConcurrencyLimit(dto);
        AgentWorkflow entity = new AgentWorkflow();
        BeanUtils.copyProperties(dto, entity);
        entity.setStatus(0);
        // 新建草稿即具备一个合法的最小顺序流程，避免尚未编辑画布时保存或发布被空画布校验拦截。
        entity.setNodes("[{\"id\":\"start\",\"type\":\"start\",\"name\":\"开始\",\"position\":{\"x\":80,\"y\":180}},{\"id\":\"end\",\"type\":\"end\",\"name\":\"结束\",\"position\":{\"x\":420,\"y\":180}}]");
        entity.setEdges("[{\"source\":\"start\",\"target\":\"end\"}]");
        entity.setInputSchema("[]");
        entity.setOutputSchema("[]");
        workflowService.save(entity);
        return WebResponse.OK(I18nUtils.getMessage("add.success"), entity.getId());
    }

    @ApiOperation("保存工作流画布草稿")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable String id, @RequestBody AgentWorkflowDto dto) {
        validateConcurrencyLimit(dto);
        AgentWorkflow entity = required(id);
        BeanUtils.copyProperties(dto, entity, "status", "publishedVersion", "agentDefinitionId");
        workflowService.updateById(entity);
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("发布工作流版本")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/{id}/publish")
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Integer> publish(@PathVariable String id) {
        AgentWorkflow workflow = required(id);
        WorkflowDefinitionValidator.validate(workflow.getNodes(), workflow.getEdges());
        WorkflowDefinitionValidator.validateVariables(workflow.getNodes(), workflow.getEdges(), workflow.getInputSchema());
        WorkflowDefinitionValidator.validateOutputSchema(workflow.getNodes(), workflow.getEdges(), workflow.getInputSchema(), workflow.getOutputSchema());
        validateResources(workflow.getNodes());
        int number = workflow.getPublishedVersion() == null ? 1 : workflow.getPublishedVersion() + 1;
        AgentWorkflowVersion version = new AgentWorkflowVersion();
        version.setWorkflowId(id);
        version.setVersionNo(number);
        version.setNodes(workflow.getNodes());
        version.setEdges(workflow.getEdges());
        version.setInputSchema(workflow.getInputSchema());
        version.setOutputSchema(workflow.getOutputSchema());
        version.setPublishedAt(System.currentTimeMillis());
        versionService.save(version);
        workflow.setPublishedVersion(number);
        workflow.setStatus(1);
        workflowService.updateById(workflow);
        return WebResponse.OK(number);
    }

    @ApiOperation("下线工作流")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/{id}/offline")
    public WebResponse<Void> offline(@PathVariable String id) {
        AgentWorkflow workflow = required(id);
        workflow.setStatus(2);
        workflowService.updateById(workflow);
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("工作流已发布版本列表")
    @Permission(path = "/workflow/workflow")
    @GetMapping("/{id}/versions")
    public WebResponse<List<AgentWorkflowVersionVo>> versions(@PathVariable String id) {
        required(id);
        List<AgentWorkflowVersionVo> result = versionService.list(Wrappers.lambdaQuery(AgentWorkflowVersion.class)
                .eq(AgentWorkflowVersion::getWorkflowId, id).eq(AgentWorkflowVersion::getDeleted, false)
                .orderByDesc(AgentWorkflowVersion::getVersionNo)).stream().map(item -> {
            AgentWorkflowVersionVo vo = new AgentWorkflowVersionVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
        return WebResponse.OK(result);
    }

    @ApiOperation("对比两个工作流发布版本")
    @Permission(path = "/workflow/workflow")
    @GetMapping("/{id}/versions/diff")
    public WebResponse<AgentWorkflowVersionDiffVo> versionDiff(@PathVariable String id, @RequestParam int from, @RequestParam int to) {
        required(id);
        AgentWorkflowVersion before = requiredVersion(id, from);
        AgentWorkflowVersion after = requiredVersion(id, to);
        AgentWorkflowVersionDiffVo diff = new AgentWorkflowVersionDiffVo();
        diff.setFromVersion(from); diff.setToVersion(to);
        Map<String, String> beforeNodes = jsonItems(before.getNodes(), false), afterNodes = jsonItems(after.getNodes(), false);
        Map<String, String> beforeEdges = jsonItems(before.getEdges(), true), afterEdges = jsonItems(after.getEdges(), true);
        diff.setAddedNodeIds(difference(afterNodes.keySet(), beforeNodes.keySet()));
        diff.setRemovedNodeIds(difference(beforeNodes.keySet(), afterNodes.keySet()));
        diff.setChangedNodeIds(intersectionChanged(beforeNodes, afterNodes));
        diff.setAddedEdgeIds(difference(afterEdges.keySet(), beforeEdges.keySet()));
        diff.setRemovedEdgeIds(difference(beforeEdges.keySet(), afterEdges.keySet()));
        diff.setInputSchemaChanged(!StringUtils.equals(normalizeJson(before.getInputSchema()), normalizeJson(after.getInputSchema())));
        diff.setOutputSchemaChanged(!StringUtils.equals(normalizeJson(before.getOutputSchema()), normalizeJson(after.getOutputSchema())));
        return WebResponse.OK(diff);
    }

    @ApiOperation("导出工作流草稿")
    @Permission(path = "/workflow/workflow")
    @GetMapping("/{id}/export")
    public WebResponse<AgentWorkflowDto> exportWorkflow(@PathVariable String id) {
        AgentWorkflow workflow = required(id);
        AgentWorkflowDto dto = new AgentWorkflowDto();
        BeanUtils.copyProperties(workflow, dto);
        return WebResponse.OK(dto);
    }

    @ApiOperation("导入工作流为新草稿")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/import")
    public WebResponse<String> importWorkflow(@RequestBody AgentWorkflowDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getName()) || StringUtils.isBlank(dto.getNodes()) || StringUtils.isBlank(dto.getEdges()))
            throw new ServerException(422, "导入文件缺少名称、节点或连线");
        WorkflowDefinitionValidator.validate(dto.getNodes(), dto.getEdges());
        WorkflowDefinitionValidator.validateVariables(dto.getNodes(), dto.getEdges(), dto.getInputSchema());
        WorkflowDefinitionValidator.validateOutputSchema(dto.getNodes(), dto.getEdges(), dto.getInputSchema(), dto.getOutputSchema());
        validateResources(dto.getNodes());
        AgentWorkflow workflow = new AgentWorkflow();
        BeanUtils.copyProperties(dto, workflow);
        workflow.setStatus(0); workflow.setPublishedVersion(null);
        workflowService.save(workflow);
        return WebResponse.OK(I18nUtils.getMessage("add.success"), workflow.getId());
    }

    @ApiOperation("从工作流创建模板")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/{id}/templates")
    public WebResponse<com.aether.workflow.entity.AgentWorkflowTemplate> createTemplate(@PathVariable String id, @RequestBody AgentWorkflowTemplateDto dto) {
        AgentWorkflow workflow = required(id);
        return WebResponse.OK(templateService.createFromWorkflow(workflow, dto == null ? null : dto.getName(), dto == null ? null : dto.getDescription()));
    }

    @ApiOperation("工作流模板列表")
    @Permission(path = "/workflow/workflow")
    @PostMapping("/templates/list")
    public WebResponse<List<com.aether.workflow.entity.AgentWorkflowTemplate>> templates(@RequestBody(required = false) AgentWorkflowTemplateDto query) {
        return WebResponse.OK(templateService.list(Wrappers.lambdaQuery(com.aether.workflow.entity.AgentWorkflowTemplate.class)
                .like(query != null && StringUtils.isNotBlank(query.getName()), com.aether.workflow.entity.AgentWorkflowTemplate::getName, query == null ? null : query.getName())
                .eq(com.aether.workflow.entity.AgentWorkflowTemplate::getDeleted, false).orderByDesc(com.aether.workflow.entity.AgentWorkflowTemplate::getCreatedAt)));
    }

    @ApiOperation("从模板创建草稿工作流")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/templates/{id}/instantiate")
    public WebResponse<String> instantiateTemplate(@PathVariable String id, @RequestBody AgentWorkflowTemplateDto dto) {
        AgentWorkflow workflow = templateService.instantiate(id, dto == null ? null : dto.getName(), dto == null ? null : dto.getDescription());
        return WebResponse.OK(I18nUtils.getMessage("request.success"), workflow.getId());
    }

    @ApiOperation("校验草稿工作流")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/{id}/draft/validate")
    public WebResponse<Void> validateDraft(@PathVariable String id) {
        AgentWorkflow workflow = required(id);
        WorkflowDefinitionValidator.validate(workflow.getNodes(), workflow.getEdges());
        WorkflowDefinitionValidator.validateVariables(workflow.getNodes(), workflow.getEdges(), workflow.getInputSchema());
        WorkflowDefinitionValidator.validateOutputSchema(workflow.getNodes(), workflow.getEdges(), workflow.getInputSchema(), workflow.getOutputSchema());
        validateResources(workflow.getNodes());
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("删除工作流")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable String id) {
        required(id);
        workflowService.removeById(id);
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("启动已发布工作流")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/{id}/instances")
    public WebResponse<String> start(@PathVariable String id, @RequestBody(required = false) AgentWorkflowStartDto dto) {
        AgentWorkflowInstance instance = executionService.start(id, dto == null ? null : dto.getVariables(), userId());
        return WebResponse.OK(I18nUtils.getMessage("request.success"), instance.getId());
    }

    @ApiOperation("由业务系统启动已发布工作流（支持幂等和终态回调）")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/{id}/business-instances")
    public WebResponse<String> startBusiness(@PathVariable String id, @RequestBody AgentWorkflowBusinessStartDto dto) {
        Map<String, String> current = CurrentUser.getUser();
        if (current == null || StringUtils.isBlank(current.get("serviceAccountId")))
            throw new ServerException(403, "业务工作流启动仅允许服务账号调用");
        serviceAccountService.assertWorkflowStartAllowed(current.get("serviceAccountId"), id);
        AgentWorkflowInstance instance = executionService.startBusiness(id, dto, userId());
        return WebResponse.OK(I18nUtils.getMessage("request.success"), instance.getId());
    }

    @ApiOperation("创建工作流 Webhook；签名密钥仅本次返回")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/webhooks")
    public WebResponse<AgentWorkflowWebhookTriggerSecretVo> createWebhook(@RequestBody AgentWorkflowWebhookTriggerDto dto,
                                                                             HttpServletResponse response) {
        noStore(response);
        return WebResponse.OK(webhookTriggerService.create(dto));
    }

    @ApiOperation("Webhook 列表")
    @Permission(path = "/workflow/workflow")
    @PostMapping("/webhooks/list")
    public WebResponse<List<AgentWorkflowWebhookTriggerVo>> webhooks(@RequestBody AgentWorkflowWebhookTriggerVo query) {
        Page<com.aether.workflow.entity.AgentWorkflowWebhookTrigger> page = webhookTriggerService.page(
                new Page<com.aether.workflow.entity.AgentWorkflowWebhookTrigger>(query.getCurrent(), query.getPageSize()),
                Wrappers.lambdaQuery(com.aether.workflow.entity.AgentWorkflowWebhookTrigger.class)
                        .eq(StringUtils.isNotBlank(query.getWorkflowId()), com.aether.workflow.entity.AgentWorkflowWebhookTrigger::getWorkflowId, query.getWorkflowId())
                        .eq(com.aether.workflow.entity.AgentWorkflowWebhookTrigger::getDeleted, false)
                        .orderByDesc(com.aether.workflow.entity.AgentWorkflowWebhookTrigger::getCreatedAt));
        List<AgentWorkflowWebhookTriggerVo> result = page.getRecords().stream().map(item -> {
            AgentWorkflowWebhookTriggerVo vo = new AgentWorkflowWebhookTriggerVo(); BeanUtils.copyProperties(item, vo); vo.setSigningSecret(null); return vo;
        }).collect(Collectors.toList());
        return WebResponse.Page(result, page.getTotal());
    }

    @ApiOperation("轮换 Webhook 签名密钥")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/webhooks/{id}/rotate-secret")
    public WebResponse<AgentWorkflowWebhookTriggerSecretVo> rotateWebhookSecret(@PathVariable String id, HttpServletResponse response) {
        noStore(response);
        return WebResponse.OK(webhookTriggerService.rotateSecret(id));
    }

    @ApiOperation("启用或停用 Webhook")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/webhooks/{id}/enabled")
    public WebResponse<Void> setWebhookEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        webhookTriggerService.setEnabled(id, enabled);
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("创建工作流定时触发器")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/schedules")
    public WebResponse<com.aether.workflow.entity.AgentWorkflowScheduleTrigger> createSchedule(@RequestBody AgentWorkflowScheduleTriggerDto dto) {
        return WebResponse.OK(scheduleTriggerService.create(dto));
    }

    @ApiOperation("工作流定时触发器列表")
    @Permission(path = "/workflow/workflow")
    @PostMapping("/schedules/list")
    public WebResponse<List<com.aether.workflow.entity.AgentWorkflowScheduleTrigger>> schedules(@RequestBody(required = false) com.aether.workflow.entity.AgentWorkflowScheduleTrigger query) {
        return WebResponse.OK(scheduleTriggerService.list(Wrappers.lambdaQuery(com.aether.workflow.entity.AgentWorkflowScheduleTrigger.class)
                .eq(query != null && StringUtils.isNotBlank(query.getWorkflowId()), com.aether.workflow.entity.AgentWorkflowScheduleTrigger::getWorkflowId, query == null ? null : query.getWorkflowId())
                .eq(com.aether.workflow.entity.AgentWorkflowScheduleTrigger::getDeleted, false).orderByDesc(com.aether.workflow.entity.AgentWorkflowScheduleTrigger::getCreatedAt)));
    }

    @ApiOperation("启用或停用工作流定时触发器")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/schedules/{id}/enabled")
    public WebResponse<Void> setScheduleEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        scheduleTriggerService.setEnabled(id, enabled);
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("工作流运营指标")
    @Permission(path = "/workflow/operations")
    @GetMapping("/operations/metrics")
    public WebResponse<AgentWorkflowOperationsMetricsVo> operationsMetrics() {
        return WebResponse.OK(operationsService.metrics());
    }

    @ApiOperation("工作流死信列表")
    @Permission(path = "/workflow/operations")
    @GetMapping("/operations/dead-letters")
    public WebResponse<List<AgentWorkflowDeadLetterVo>> deadLetters(@RequestParam(defaultValue = "50") int limit) {
        return WebResponse.OK(operationsService.deadLetters(limit));
    }

    @ApiOperation("接收外部 Webhook 事件")
    @Permission(required = false)
    @PostMapping("/webhook/{id}")
    public WebResponse<String> receiveWebhook(@PathVariable String id, @RequestHeader("X-Aether-Webhook-Timestamp") String timestamp,
                                               @RequestHeader("X-Aether-Webhook-Signature") String signature,
                                               @RequestBody String rawBody, HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) { String name = names.nextElement(); headers.put(name, request.getHeader(name)); }
        AgentWorkflowInstance instance = webhookTriggerService.trigger(id, timestamp, signature, rawBody, headers);
        return WebResponse.OK(I18nUtils.getMessage("request.success"), instance.getId());
    }

    @ApiOperation("流程实例列表")
    @Permission(path = "/workflow/run")
    @PostMapping("/instances/list")
    public WebResponse<List<AgentWorkflowInstanceVo>> instances(@RequestBody AgentWorkflowInstanceVo query) {
        boolean administrator = executionService.isAdministrator(userId());
        Page<AgentWorkflowInstance> page = instanceService.page(new Page<AgentWorkflowInstance>(query.getCurrent(), query.getPageSize()), Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(!administrator, AgentWorkflowInstance::getUserId, userId()).eq(StringUtils.isNotBlank(query.getWorkflowId()), AgentWorkflowInstance::getWorkflowId, query.getWorkflowId())
                .eq(StringUtils.isNotBlank(query.getBusinessType()), AgentWorkflowInstance::getBusinessType, query.getBusinessType())
                .eq(StringUtils.isNotBlank(query.getBusinessId()), AgentWorkflowInstance::getBusinessId, query.getBusinessId())
                .eq(StringUtils.isNotBlank(query.getStatus()), AgentWorkflowInstance::getStatus, query.getStatus()).orderByDesc(AgentWorkflowInstance::getCreatedAt));
        List<AgentWorkflowInstanceVo> records = page.getRecords().stream().map(item -> executionService.detail(item.getId(), userId())).collect(Collectors.toList());
        return WebResponse.Page(records, page.getTotal());
    }

    @ApiOperation("流程实例详情")
    @Permission(path = "/workflow/run")
    @GetMapping("/instances/{id}")
    public WebResponse<AgentWorkflowInstanceVo> instance(@PathVariable String id) {
        return WebResponse.OK(executionService.detail(id, userId()));
    }

    @ApiOperation("查询流程实例的业务回调投递记录")
    @Permission(path = "/workflow/run")
    @GetMapping("/instances/{id}/callbacks")
    public WebResponse<List<AgentWorkflowCallbackDelivery>> callbacks(@PathVariable String id) {
        executionService.detail(id, userId());
        return WebResponse.OK(callbackDeliveryService.list(Wrappers.lambdaQuery(AgentWorkflowCallbackDelivery.class)
                .eq(AgentWorkflowCallbackDelivery::getInstanceId, id).orderByAsc(AgentWorkflowCallbackDelivery::getCreatedAt)));
    }

    @ApiOperation("人工重投失败的业务回调")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/callbacks/{deliveryId}/retry")
    public WebResponse<Void> retryCallback(@PathVariable String id, @PathVariable String deliveryId) {
        executionService.detail(id, userId());
        AgentWorkflowCallbackDelivery delivery = callbackDeliveryService.getById(deliveryId);
        if (delivery == null || !StringUtils.equals(id, delivery.getInstanceId())) throw new ServerException(404, "回调投递记录不存在");
        if (!workflowCallbackService.retryFailed(deliveryId)) throw new ServerException(409, "仅失败的回调投递记录可以重试");
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("流程实例实时事件")
    @Permission(path = "/workflow/run")
    @GetMapping(value = "/instances/{id}/events", produces = "text/event-stream")
    public SseEmitter events(@PathVariable String id) {
        AgentWorkflowInstanceVo snapshot = executionService.detail(id, userId());
        SseEmitter emitter = sseHub.subscribe(id);
        sseHub.publish(id, "instance.status", snapshot);
        return emitter;
    }

    @ApiOperation("提交人工节点回答或 MCP 确认")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/answer")
    public WebResponse<Void> answer(@PathVariable String id, @RequestBody AgentWorkflowInteractionDto dto) {
        executionService.answer(id, dto, userId());
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("重试失败节点")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/retry")
    public WebResponse<Void> retry(@PathVariable String id) {
        executionService.retry(id, userId());
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("回放手动启动的流程实例")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/replay")
    public WebResponse<String> replay(@PathVariable String id) {
        AgentWorkflowInstance instance = executionService.replay(id, userId());
        return WebResponse.OK(I18nUtils.getMessage("request.success"), instance.getId());
    }

    @ApiOperation("终止流程实例")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/terminate")
    public WebResponse<Void> terminate(@PathVariable String id) {
        executionService.terminate(id, userId());
        return WebResponse.OK((Void) null);
    }

    @ApiOperation("运行中修改开始变量")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PutMapping("/instances/{id}/variables")
    public WebResponse<Void> updateVariables(@PathVariable String id, @RequestBody(required = false) AgentWorkflowStartDto dto) {
        executionService.updateVariables(id, dto == null ? null : dto.getVariables(), userId());
        return WebResponse.OK((Void) null);
    }

    private AgentWorkflowVersion requiredVersion(String workflowId, int versionNo) {
        AgentWorkflowVersion version = versionService.getOne(Wrappers.lambdaQuery(AgentWorkflowVersion.class)
                .eq(AgentWorkflowVersion::getWorkflowId, workflowId).eq(AgentWorkflowVersion::getVersionNo, versionNo)
                .eq(AgentWorkflowVersion::getDeleted, false));
        if (version == null) throw new ServerException(404, "工作流版本不存在");
        return version;
    }

    private void validateConcurrencyLimit(AgentWorkflowDto dto) {
        if (dto != null && dto.getMaxConcurrentInstances() != null && dto.getMaxConcurrentInstances() < 0)
            throw new ServerException(422, "最大并发实例数不能小于 0");
    }

    private Map<String, String> jsonItems(String source, boolean edge) {
        Map<String, String> result = new TreeMap<String, String>();
        JSONArray values = JSONArray.parseArray(StringUtils.defaultIfBlank(source, "[]"));
        for (int index = 0; index < values.size(); index++) {
            JSONObject item = values.getJSONObject(index);
            String key = item.getString("id");
            if (StringUtils.isBlank(key) && edge) key = StringUtils.defaultString(item.getString("source")) + "->"
                    + StringUtils.defaultString(item.getString("target")) + "#" + StringUtils.defaultString(item.getString("sourceHandle"))
                    + "#" + StringUtils.defaultString(item.getString("targetHandle"));
            if (StringUtils.isBlank(key)) key = "index:" + index;
            result.put(key, normalizeJson(item.toJSONString()));
        }
        return result;
    }

    private List<String> difference(Set<String> left, Set<String> right) {
        return left.stream().filter(item -> !right.contains(item)).sorted().collect(Collectors.toList());
    }

    private List<String> intersectionChanged(Map<String, String> before, Map<String, String> after) {
        return before.keySet().stream().filter(after::containsKey).filter(key -> !StringUtils.equals(before.get(key), after.get(key)))
                .sorted().collect(Collectors.toList());
    }

    private String normalizeJson(String source) {
        if (StringUtils.isBlank(source)) return "";
        try { return JSONObject.parse(source).toJSONString(); } catch (Exception ignored) { return source; }
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

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }
}
