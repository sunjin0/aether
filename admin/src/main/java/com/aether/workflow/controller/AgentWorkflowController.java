package com.aether.workflow.controller;

import com.aether.workflow.dto.*;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.application.service.AgentApplicationService;
import com.aether.workflow.entity.AgentWorkflowCallbackDelivery;
import com.aether.workflow.entity.AgentWorkflowAuditEvent;
import com.aether.workflow.entity.AgentWorkflowExternalInvocation;
import com.aether.workflow.entity.AgentWorkflowNodeToken;
import com.aether.workflow.entity.AgentWorkflowVariableSnapshot;
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
import com.aether.sys.entity.ServiceAccount;
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
    private final AgentWorkflowAuditEventService auditEventService;
    private final AgentWorkflowExternalInvocationService externalInvocationService;
    private final AgentWorkflowNodeTokenService nodeTokenService;
    private final AgentWorkflowVariableSnapshotService variableSnapshotService;
    private final WorkflowCallbackService workflowCallbackService;
    private final ServiceAccountService serviceAccountService;
    private final AgentWorkflowWebhookTriggerService webhookTriggerService;
    private final AgentWorkflowOperationsService operationsService;
    private final AgentWorkflowTemplateService templateService;
    private final AgentApplicationService applicationService;

    /**
     * 创建 {@code AgentWorkflowController} 实例。
     */
    public AgentWorkflowController(AgentWorkflowService workflowService, AgentWorkflowVersionService versionService,
                                   AgentWorkflowInstanceService instanceService, AgentWorkflowExecutionService executionService, WorkflowSseHub sseHub,
                                   AgentDefinitionService agentDefinitionService, AgentToolService agentToolService,
                                   AgentWorkflowCallbackDeliveryService callbackDeliveryService, AgentWorkflowAuditEventService auditEventService,
                                   AgentWorkflowExternalInvocationService externalInvocationService,
                                   AgentWorkflowNodeTokenService nodeTokenService,
                                   AgentWorkflowVariableSnapshotService variableSnapshotService,
                                   WorkflowCallbackService workflowCallbackService, ServiceAccountService serviceAccountService,
                                   AgentWorkflowWebhookTriggerService webhookTriggerService, AgentWorkflowOperationsService operationsService,
                                   AgentWorkflowTemplateService templateService, AgentApplicationService applicationService) {
        this.workflowService = workflowService;
        this.versionService = versionService;
        this.instanceService = instanceService;
        this.executionService = executionService;
        this.sseHub = sseHub;
        this.agentDefinitionService = agentDefinitionService;
        this.agentToolService = agentToolService;
        this.callbackDeliveryService = callbackDeliveryService;
        this.auditEventService = auditEventService;
        this.externalInvocationService = externalInvocationService;
        this.nodeTokenService = nodeTokenService;
        this.variableSnapshotService = variableSnapshotService;
        this.workflowCallbackService = workflowCallbackService;
        this.serviceAccountService = serviceAccountService;
        this.webhookTriggerService = webhookTriggerService;
        this.operationsService = operationsService;
        this.templateService = templateService;
        this.applicationService = applicationService;
    }

    /**
     * 工作流列表。
     *
     * @param query 查询参数
     * @return 工作流列表
     */
    @ApiOperation("查询工作流列表")
    @PostMapping("/list")
    public WebResponse<List<AgentWorkflowVo>> list(@RequestBody AgentWorkflowListRequest query) {
        Page<AgentWorkflow> page = workflowService.page(new Page<AgentWorkflow>(query.getCurrent(), query.getPageSize()), Wrappers.lambdaQuery(AgentWorkflow.class)
                .like(StringUtils.isNotBlank(query.getName()), AgentWorkflow::getName, query.getName()).eq(query.getStatus() != null, AgentWorkflow::getStatus, query.getStatus())
                .eq(StringUtils.isNotBlank(query.getApplicationId()), AgentWorkflow::getApplicationId, query.getApplicationId())
                .eq(AgentWorkflow::getDeleted, false).orderByDesc(AgentWorkflow::getUpdatedAt));
        return WebResponse.Page(page.getRecords().stream().map(item -> {
            AgentWorkflowVo vo = new AgentWorkflowVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList()), page.getTotal());
    }

    /**
     * 返回画布可用节点类型；MCP 是历史协议类型，产品界面统一展示为工具节点。
     */
    @ApiOperation("工作流节点类型")
    @Permission(path = "/workflow/workflow")
    @GetMapping("/node-types")
    public WebResponse<List<Map<String, String>>> nodeTypes() {
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        addNodeType(result, "start", "开始节点");
        addNodeType(result, "end", "结束节点");
        addNodeType(result, "agent", "Agent 节点");
        addNodeType(result, "tool", "工具节点");
        addNodeType(result, "human", "人工录入节点");
        addNodeType(result, "approval", "审批节点");
        addNodeType(result, "rule", "规则节点");
        addNodeType(result, "transform", "数据转换节点");
        addNodeType(result, "http", "HTTP 调用节点");
        addNodeType(result, "notification", "通知节点");
        addNodeType(result, "subflow", "子流程节点");
        addNodeType(result, "parallel", "并行分叉节点");
        addNodeType(result, "join", "汇聚节点");
        addNodeType(result, "wait_event", "等待事件节点");
        addNodeType(result, "delay", "延时节点");
        return WebResponse.OK(result);
    }

    /**
     * 详情当前请求。
     */
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

    /**
     * 创建当前请求。
     */
    @ApiOperation("创建工作流草稿")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping
    public WebResponse<String> create(@RequestBody AgentWorkflowCreateRequest request) {
        AgentWorkflowDto dto = toWorkflowDto(request);
        validateConcurrencyLimit(dto);
        String applicationId = requireActiveApplication(dto == null ? null : dto.getApplicationId());
        dto.setApplicationId(applicationId);
        if (StringUtils.isBlank(dto.getCode()) || !dto.getCode().matches("[A-Za-z][A-Za-z0-9_-]{2,63}"))
            throw new ServerException(422, "工作流编码必须为 3-64 位字母、数字、下划线或短横线");
        if (workflowService.count(Wrappers.lambdaQuery(AgentWorkflow.class).eq(AgentWorkflow::getApplicationId,
                applicationId).eq(AgentWorkflow::getCode, dto.getCode())
                .eq(AgentWorkflow::getDeleted, false)) > 0) throw new ServerException(422, "工作流编码已存在");
        AgentWorkflow entity = new AgentWorkflow();
        BeanUtils.copyProperties(dto, entity);
        entity.setApplicationId(applicationId);
        entity.setStatus(0);
        // 新建草稿即具备一个合法的最小顺序流程，避免尚未编辑画布时保存或发布被空画布校验拦截。
        entity.setNodes("[{\"id\":\"start\",\"type\":\"start\",\"name\":\"开始\",\"position\":{\"x\":80,\"y\":180}},{\"id\":\"end\",\"type\":\"end\",\"name\":\"结束\",\"position\":{\"x\":420,\"y\":180}}]");
        entity.setEdges("[{\"source\":\"start\",\"target\":\"end\"}]");
        entity.setInputSchema("[]");
        entity.setOutputSchema("[]");
        workflowService.save(entity);
        return WebResponse.OK(I18nUtils.getMessage("workflow.draft.create.success"), entity.getId());
    }

    /**
     * 更新当前请求。
     */
    @ApiOperation("保存工作流画布草稿")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable String id, @RequestBody AgentWorkflowUpdateRequest request) {
        AgentWorkflowDto dto = toWorkflowDto(request);
        validateConcurrencyLimit(dto);
        AgentWorkflow entity = required(id);
        String applicationId = requireActiveApplication(StringUtils.defaultIfBlank(dto.getApplicationId(), entity.getApplicationId()));
        dto.setApplicationId(applicationId);
        validateResources(StringUtils.defaultIfBlank(dto.getNodes(), entity.getNodes()), applicationId);
        BeanUtils.copyProperties(dto, entity, "status", "publishedVersion", "agentDefinitionId");
        workflowService.updateById(entity);
        return WebResponse.OK(I18nUtils.getMessage("workflow.draft.save.success"));
    }

    /**
     * 发布当前请求。
     */
    @ApiOperation("发布工作流版本")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/{id}/publish")
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Integer> publish(@PathVariable String id) {
        AgentWorkflow workflow = required(id);
        String applicationId = requireActiveApplication(workflow.getApplicationId());
        WorkflowDefinitionValidator.validate(workflow.getNodes(), workflow.getEdges());
        WorkflowDefinitionValidator.validateVariables(workflow.getNodes(), workflow.getEdges(), workflow.getInputSchema());
        WorkflowDefinitionValidator.validateOutputSchema(workflow.getNodes(), workflow.getEdges(), workflow.getInputSchema(), workflow.getOutputSchema());
        validateResources(workflow.getNodes(), applicationId);
        validateSubflowDependencies(workflow);
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
        return WebResponse.OK(I18nUtils.getMessage("workflow.publish.success"), number);
    }

    /**
     * 下线工作流。
     */
    @ApiOperation("下线工作流")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/{id}/offline")
    public WebResponse<Void> offline(@PathVariable String id) {
        AgentWorkflow workflow = required(id);
        workflow.setStatus(2);
        workflowService.updateById(workflow);
        return WebResponse.OK(I18nUtils.getMessage("workflow.offline.success"));
    }

    /**
     * 工作流已发布版本列表。
     */
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

    /**
     * 对比两个工作流发布版本。
     */
    @ApiOperation("对比两个工作流发布版本")
    @Permission(path = "/workflow/workflow")
    @GetMapping("/{id}/versions/diff")
    public WebResponse<AgentWorkflowVersionDiffVo> versionDiff(@PathVariable String id, @RequestParam int from, @RequestParam int to) {
        required(id);
        AgentWorkflowVersion before = requiredVersion(id, from);
        AgentWorkflowVersion after = requiredVersion(id, to);
        AgentWorkflowVersionDiffVo diff = new AgentWorkflowVersionDiffVo();
        diff.setFromVersion(from);
        diff.setToVersion(to);
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

    /**
     * 导出工作流草稿。
     */
    @ApiOperation("导出工作流草稿")

    @Permission(path = "/workflow/workflow")
    @GetMapping("/{id}/export")
    public WebResponse<AgentWorkflowDto> exportWorkflow(@PathVariable String id) {
        AgentWorkflow workflow = required(id);
        AgentWorkflowDto dto = new AgentWorkflowDto();
        BeanUtils.copyProperties(workflow, dto);
        return WebResponse.OK(dto);
    }

    /**
     * 导入工作流为新草稿。
     */
    @ApiOperation("导入工作流为新草稿")

    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/import")
    public WebResponse<String> importWorkflow(@RequestBody AgentWorkflowImportRequest request) {
        AgentWorkflowDto dto = toWorkflowDto(request);
        if (dto == null || StringUtils.isBlank(dto.getName()) || StringUtils.isBlank(dto.getNodes()) || StringUtils.isBlank(dto.getEdges()))
            throw new ServerException(422, I18nUtils.getMessage("workflow.import.name-nodes-edges.required"));
        WorkflowDefinitionValidator.validate(dto.getNodes(), dto.getEdges());
        WorkflowDefinitionValidator.validateVariables(dto.getNodes(), dto.getEdges(), dto.getInputSchema());
        WorkflowDefinitionValidator.validateOutputSchema(dto.getNodes(), dto.getEdges(), dto.getInputSchema(), dto.getOutputSchema());
        dto.setApplicationId(requireActiveApplication(dto.getApplicationId()));
        validateResources(dto.getNodes(), dto.getApplicationId());
        AgentWorkflow workflow = new AgentWorkflow();
        BeanUtils.copyProperties(dto, workflow);
        workflow.setStatus(0);
        workflow.setPublishedVersion(null);
        workflowService.save(workflow);
        return WebResponse.OK(I18nUtils.getMessage("workflow.import.success"), workflow.getId());
    }

    /**
     * 创建Template。
     */
    @ApiOperation("从工作流创建模板")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/{id}/templates")
    public WebResponse<com.aether.workflow.entity.AgentWorkflowTemplate> createTemplate(@PathVariable String id, @RequestBody AgentWorkflowCreateTemplateRequest request) {
        AgentWorkflow workflow = required(id);
        return WebResponse.OK(templateService.createFromWorkflow(workflow, request == null ? null : request.getName(), request == null ? null : request.getDescription()));
    }

    /**
     * 工作流模板列表。
     */
    @ApiOperation("工作流模板列表")

    @Permission(path = "/workflow/workflow")
    @PostMapping("/templates/list")
    public WebResponse<List<com.aether.workflow.entity.AgentWorkflowTemplate>> templates(@RequestBody(required = false) AgentWorkflowListTemplatesRequest query) {
        return WebResponse.OK(templateService.list(Wrappers.lambdaQuery(com.aether.workflow.entity.AgentWorkflowTemplate.class)
                .like(query != null && StringUtils.isNotBlank(query.getName()), com.aether.workflow.entity.AgentWorkflowTemplate::getName, query == null ? null : query.getName())
                .eq(com.aether.workflow.entity.AgentWorkflowTemplate::getDeleted, false).orderByDesc(com.aether.workflow.entity.AgentWorkflowTemplate::getCreatedAt)));
    }

    /**
     * 从模板创建草稿工作流。
     */
    @ApiOperation("从模板创建草稿工作流")

    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/templates/{id}/instantiate")
    public WebResponse<String> instantiateTemplate(@PathVariable String id, @RequestBody AgentWorkflowInstantiateTemplateRequest request) {
        AgentWorkflow workflow = templateService.instantiate(id, request == null ? null : request.getCode(),
                request == null ? null : request.getName(), request == null ? null : request.getDescription());
        return WebResponse.OK(I18nUtils.getMessage("workflow.template.instantiate.success"), workflow.getId());
    }

    /**
     * 校验Draft。
     */
    @ApiOperation("校验草稿工作流")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/{id}/draft/validate")
    public WebResponse<Void> validateDraft(@PathVariable String id) {
        AgentWorkflow workflow = required(id);
        String applicationId = requireActiveApplication(workflow.getApplicationId());
        WorkflowDefinitionValidator.validate(workflow.getNodes(), workflow.getEdges());
        WorkflowDefinitionValidator.validateVariables(workflow.getNodes(), workflow.getEdges(), workflow.getInputSchema());
        WorkflowDefinitionValidator.validateOutputSchema(workflow.getNodes(), workflow.getEdges(), workflow.getInputSchema(), workflow.getOutputSchema());
        validateResources(workflow.getNodes(), applicationId);
        return WebResponse.OK(I18nUtils.getMessage("workflow.draft.validate.success"));
    }

    /**
     * 删除当前请求。
     */
    @ApiOperation("删除工作流")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable String id) {
        required(id);
        workflowService.removeById(id);
        return WebResponse.OK(I18nUtils.getMessage("workflow.delete.success"));
    }

    /**
     * 启动处理流程。
     */
    @ApiOperation("启动已发布工作流")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/{id}/instances")
    public WebResponse<String> start(@PathVariable String id, @RequestBody(required = false) AgentWorkflowStartInstanceRequest request) {
        AgentWorkflowInstance instance = executionService.start(id, request == null ? null : request.getVariables(), userId());
        return WebResponse.OK(I18nUtils.getMessage("workflow.instance.start.success"), instance.getId());
    }

    /**
     * 由业务系统启动已发布工作流（支持幂等和终态回调）。
     */
    @ApiOperation("由业务系统启动已发布工作流（支持幂等和终态回调）")

    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/{id}/business-instances")
    public WebResponse<String> startBusiness(@PathVariable String id, @RequestBody AgentWorkflowStartBusinessInstanceRequest request) {
        Map<String, String> current = CurrentUser.getUser();
        if (current == null || StringUtils.isBlank(current.get("serviceAccountId")))
            throw new ServerException(403, I18nUtils.getMessage("workflow.business-start.service-account.required"));
        serviceAccountService.assertWorkflowStartAllowed(current.get("serviceAccountId"), id);
        AgentWorkflowInstance instance = executionService.startBusiness(id, toBusinessStartDto(request), userId());
        return WebResponse.OK(I18nUtils.getMessage("workflow.business-instance.start.success"), instance.getId());
    }

    /**
     * 创建Webhook。
     */
    @ApiOperation("创建工作流 Webhook；签名密钥仅本次返回")
    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/webhooks")
    public WebResponse<AgentWorkflowWebhookTriggerSecretVo> createWebhook(@RequestBody AgentWorkflowCreateWebhookRequest request,
                                                                           HttpServletResponse response) {
        noStore(response);
        return WebResponse.OK(I18nUtils.getMessage("workflow.webhook.create.success"), webhookTriggerService.create(toWebhookTriggerDto(request)));
    }

    /**
     * Webhook 列表。
     */
    @ApiOperation("Webhook 列表")

    @Permission(path = "/workflow/workflow")
    @PostMapping("/webhooks/list")
    public WebResponse<List<AgentWorkflowWebhookTriggerVo>> webhooks(@RequestBody AgentWorkflowListWebhooksRequest query) {
        Page<com.aether.workflow.entity.AgentWorkflowWebhookTrigger> page = webhookTriggerService.page(
                new Page<com.aether.workflow.entity.AgentWorkflowWebhookTrigger>(query.getCurrent(), query.getPageSize()),
                Wrappers.lambdaQuery(com.aether.workflow.entity.AgentWorkflowWebhookTrigger.class)
                        .eq(StringUtils.isNotBlank(query.getWorkflowId()), com.aether.workflow.entity.AgentWorkflowWebhookTrigger::getWorkflowId, query.getWorkflowId())
                        .eq(com.aether.workflow.entity.AgentWorkflowWebhookTrigger::getDeleted, false)
                        .orderByDesc(com.aether.workflow.entity.AgentWorkflowWebhookTrigger::getCreatedAt));
        List<AgentWorkflowWebhookTriggerVo> result = page.getRecords().stream().map(item -> {
            AgentWorkflowWebhookTriggerVo vo = new AgentWorkflowWebhookTriggerVo();
            BeanUtils.copyProperties(item, vo);
            vo.setSigningSecret(null);
            return vo;
        }).collect(Collectors.toList());
        return WebResponse.Page(result, page.getTotal());
    }

    /**
     * 轮换 Webhook 签名密钥。
     */
    @ApiOperation("轮换 Webhook 签名密钥")

    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/webhooks/{id}/rotate-secret")
    public WebResponse<AgentWorkflowWebhookTriggerSecretVo> rotateWebhookSecret(@PathVariable String id, HttpServletResponse response) {
        noStore(response);
        return WebResponse.OK(I18nUtils.getMessage("workflow.webhook.secret.rotate.success"), webhookTriggerService.rotateSecret(id));
    }

    /**
     * 启用或停用 Webhook。
     */
    @ApiOperation("启用或停用 Webhook")

    @Permission(path = "/workflow/workflow", type = Permission.Type.Write)
    @PostMapping("/webhooks/{id}/enabled")
    public WebResponse<Void> setWebhookEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        webhookTriggerService.setEnabled(id, enabled);
        return WebResponse.OK(I18nUtils.getMessage("workflow.webhook.status.update.success"));
    }

    /**
     * 工作流运营指标。
     */
    @ApiOperation("工作流运营指标")

    @Permission(path = "/workflow/operations")
    @GetMapping("/operations/metrics")
    public WebResponse<AgentWorkflowOperationsMetricsVo> operationsMetrics() {
        return WebResponse.OK(operationsService.metrics());
    }

    /**
     * 工作流死信列表。
     */
    @ApiOperation("工作流死信列表")

    @Permission(path = "/workflow/operations")
    @GetMapping("/operations/dead-letters")
    public WebResponse<List<AgentWorkflowDeadLetterVo>> deadLetters(@RequestParam(defaultValue = "50") int limit) {
        return WebResponse.OK(operationsService.deadLetters(limit));
    }

    /**
     * 接收Webhook。
     */
    @ApiOperation("接收外部 Webhook 事件")
    @Permission(required = false)
    @PostMapping("/webhook/{id}")
    public WebResponse<String> receiveWebhook(@PathVariable String id, @RequestHeader("X-Aether-Webhook-Timestamp") String timestamp,
                                              @RequestHeader("X-Aether-Webhook-Signature") String signature,
                                              @RequestBody String rawBody, HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        AgentWorkflowInstance instance = webhookTriggerService.trigger(id, timestamp, signature, rawBody, headers);
        return WebResponse.OK(I18nUtils.getMessage("workflow.webhook.event.accepted"), instance.getId());
    }

    /**
     * 流程实例列表。
     */
    @ApiOperation("流程实例列表")

    @Permission(path = "/workflow/run")
    @PostMapping("/instances/list")
    public WebResponse<List<AgentWorkflowInstanceVo>> instances(@RequestBody AgentWorkflowListInstancesRequest query) {
        boolean administrator = executionService.isAdministrator(userId());
        Page<AgentWorkflowInstance> page = instanceService.page(new Page<AgentWorkflowInstance>(query.getCurrent(), query.getPageSize()), Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(!administrator, AgentWorkflowInstance::getUserId, userId()).eq(StringUtils.isNotBlank(query.getWorkflowId()), AgentWorkflowInstance::getWorkflowId, query.getWorkflowId())
                .eq(StringUtils.isNotBlank(query.getBusinessType()), AgentWorkflowInstance::getBusinessType, query.getBusinessType())
                .eq(StringUtils.isNotBlank(query.getBusinessId()), AgentWorkflowInstance::getBusinessId, query.getBusinessId())
                .eq(StringUtils.isNotBlank(query.getStatus()), AgentWorkflowInstance::getStatus, query.getStatus()).orderByDesc(AgentWorkflowInstance::getCreatedAt));
        List<AgentWorkflowInstanceVo> records = page.getRecords().stream().map(item -> executionService.detail(item.getId(), userId())).collect(Collectors.toList());
        return WebResponse.Page(records, page.getTotal());
    }

    /**
     * 流程实例详情。
     */
    @ApiOperation("流程实例详情")

    @Permission(path = "/workflow/run")
    @GetMapping("/instances/{id}")
    public WebResponse<AgentWorkflowInstanceVo> instance(@PathVariable String id) {
        return WebResponse.OK(executionService.detail(id, userId()));
    }

    /**
     * 查询流程实例的业务回调投递记录。
     */
    @ApiOperation("查询流程实例的业务回调投递记录")

    @Permission(path = "/workflow/run")
    @GetMapping("/instances/{id}/callbacks")
    public WebResponse<List<AgentWorkflowCallbackDelivery>> callbacks(@PathVariable String id) {
        executionService.detail(id, userId());
        return WebResponse.OK(callbackDeliveryService.list(Wrappers.lambdaQuery(AgentWorkflowCallbackDelivery.class)
                .eq(AgentWorkflowCallbackDelivery::getInstanceId, id).orderByAsc(AgentWorkflowCallbackDelivery::getCreatedAt)));
    }

    /**
     * 查询流程实例审计轨迹。
     */
    @ApiOperation("查询流程实例审计轨迹")
    @Permission(path = "/workflow/run")
    @GetMapping("/instances/{id}/audit-events")
    public WebResponse<List<AgentWorkflowAuditEvent>> auditEvents(@PathVariable String id) {
        executionService.detail(id, userId());
        return WebResponse.OK(auditEventService.listByInstanceId(id));
    }

    @ApiOperation("查询流程实例统一时间线")
    @Permission(path = "/workflow/run")
    @GetMapping("/instances/{id}/timeline")
    public WebResponse<List<AgentWorkflowAuditEvent>> timeline(@PathVariable String id) {
        executionService.detail(id, userId());
        return WebResponse.OK(auditEventService.listByInstanceId(id));
    }

    @ApiOperation("查询流程实例节点令牌")
    @Permission(path = "/workflow/run")
    @GetMapping("/instances/{id}/tokens")
    public WebResponse<List<AgentWorkflowNodeToken>> tokens(@PathVariable String id) {
        executionService.detail(id, userId());
        return WebResponse.OK(nodeTokenService.list(Wrappers.lambdaQuery(AgentWorkflowNodeToken.class)
                .eq(AgentWorkflowNodeToken::getInstanceId, id).eq(AgentWorkflowNodeToken::getDeleted, false)
                .orderByAsc(AgentWorkflowNodeToken::getCreatedAt)));
    }

    @ApiOperation("查询流程实例变量快照")
    @Permission(path = "/workflow/run")
    @GetMapping("/instances/{id}/variable-snapshots")
    public WebResponse<List<AgentWorkflowVariableSnapshot>> variableSnapshots(@PathVariable String id) {
        executionService.detail(id, userId());
        return WebResponse.OK(variableSnapshotService.list(Wrappers.lambdaQuery(AgentWorkflowVariableSnapshot.class)
                .eq(AgentWorkflowVariableSnapshot::getInstanceId, id).eq(AgentWorkflowVariableSnapshot::getDeleted, false)
                .orderByAsc(AgentWorkflowVariableSnapshot::getCreatedAt)));
    }

    @ApiOperation("重试流程实例的当前失败节点")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/nodes/{nodeId}/retry")
    public WebResponse<Void> retryNode(@PathVariable String id, @PathVariable String nodeId) {
        executionService.retryNode(id, nodeId, userId());
        return WebResponse.OK("已请求重试当前节点");
    }

    /**
     * 查询流程实例的外部调用记录，供结果未知时人工核对。
     */
    @ApiOperation("查询流程实例外部调用记录")
    @Permission(path = "/workflow/run")
    @GetMapping("/instances/{id}/external-invocations")
    public WebResponse<List<AgentWorkflowExternalInvocation>> externalInvocations(@PathVariable String id) {
        executionService.detail(id, userId());
        return WebResponse.OK(externalInvocationService.listByInstanceId(id));
    }

    @ApiOperation("人工确认结果未知的外部调用已成功")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/external-invocations/{invocationId}/confirm")
    public WebResponse<Void> confirmExternalInvocation(@PathVariable String id, @PathVariable String invocationId,
                                                        @RequestBody(required = false) AgentWorkflowConfirmExternalInvocationRequest request) {
        executionService.confirmExternalInvocation(id, invocationId, request == null ? null : request.getResponseData(), userId());
        return WebResponse.OK("外部调用已确认并恢复工作流");
    }

    @ApiOperation("人工确认后重试结果未知的外部调用")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/external-invocations/{invocationId}/retry")
    public WebResponse<Void> retryExternalInvocation(@PathVariable String id, @PathVariable String invocationId) {
        executionService.retryExternalInvocation(id, invocationId, userId());
        return WebResponse.OK("已请求重试外部调用");
    }

    /**
     * 重试回调。
     */
    @ApiOperation("人工重投失败的业务回调")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/callbacks/{deliveryId}/retry")
    public WebResponse<Void> retryCallback(@PathVariable String id, @PathVariable String deliveryId) {
        executionService.detail(id, userId());
        AgentWorkflowCallbackDelivery delivery = callbackDeliveryService.getById(deliveryId);
        if (delivery == null || !StringUtils.equals(id, delivery.getInstanceId()))
            throw new ServerException(404, I18nUtils.getMessage("workflow.callback-delivery.not-found"));
        if (!workflowCallbackService.retryFailed(deliveryId))
            throw new ServerException(409, I18nUtils.getMessage("workflow.callback-delivery.retry.failed-only"));
        return WebResponse.OK(I18nUtils.getMessage("workflow.callback-delivery.retry.success"));
    }

    /**
     * 流程实例实时事件。
     */
    @ApiOperation("流程实例实时事件")

    @Permission(path = "/workflow/run")
    @GetMapping(value = "/instances/{id}/events", produces = "text/event-stream")
    public SseEmitter events(@PathVariable String id) {
        AgentWorkflowInstanceVo snapshot = executionService.detail(id, userId());
        SseEmitter emitter = sseHub.subscribe(id);
        sseHub.publish(id, "instance.status", snapshot);
        return emitter;
    }

    /**
     * 提交人工节点回答或 MCP 确认。
     */
    @ApiOperation("提交人工节点回答或 MCP 确认")

    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/answer")
    public WebResponse<Void> answer(@PathVariable String id, @RequestBody AgentWorkflowAnswerInstanceRequest request) {
        AgentWorkflowInteractionDto dto = new AgentWorkflowInteractionDto();
        dto.setAnswer(request.getAnswer());
        executionService.answer(id, dto, userId());
        return WebResponse.OK(I18nUtils.getMessage("workflow.instance.answer.success"));
    }

    /**
     * 重试当前请求。
     */
    @ApiOperation("重试失败节点")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/retry")
    public WebResponse<Void> retry(@PathVariable String id) {
        executionService.retry(id, userId());
        return WebResponse.OK(I18nUtils.getMessage("workflow.instance.retry.success"));
    }

    /**
     * 回放手动启动的流程实例。
     */
    @ApiOperation("回放手动启动的流程实例")

    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/replay")
    public WebResponse<String> replay(@PathVariable String id) {
        AgentWorkflowInstance instance = executionService.replay(id, userId());
        return WebResponse.OK(I18nUtils.getMessage("workflow.instance.replay.success"), instance.getId());
    }

    /**
     * 终止流程实例。
     */
    @ApiOperation("终止流程实例")

    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PostMapping("/instances/{id}/terminate")
    public WebResponse<Void> terminate(@PathVariable String id) {
        executionService.terminate(id, userId());
        return WebResponse.OK(I18nUtils.getMessage("workflow.instance.terminate.success"));
    }

    /**
     * 更新Variables。
     */
    @ApiOperation("运行中修改开始变量")
    @Permission(path = "/workflow/run", type = Permission.Type.Write)
    @PutMapping("/instances/{id}/variables")
    public WebResponse<Void> updateVariables(@PathVariable String id, @RequestBody(required = false) AgentWorkflowUpdateInstanceVariablesRequest request) {
        executionService.updateVariables(id, request == null ? null : request.getVariables(), userId());
        return WebResponse.OK(I18nUtils.getMessage("workflow.instance.variables.update.success"));
    }

    /**
     * 处理requiredVersion。
     */
    private AgentWorkflowVersion requiredVersion(String workflowId, int versionNo) {
        AgentWorkflowVersion version = versionService.getOne(Wrappers.lambdaQuery(AgentWorkflowVersion.class)
                .eq(AgentWorkflowVersion::getWorkflowId, workflowId).eq(AgentWorkflowVersion::getVersionNo, versionNo)
                .eq(AgentWorkflowVersion::getDeleted, false));
        if (version == null) throw new ServerException(404, I18nUtils.getMessage("workflow.version.not-found"));
        return version;
    }

    private AgentWorkflowDto toWorkflowDto(Object request) {
        if (request == null) return null;
        AgentWorkflowDto dto = new AgentWorkflowDto();
        BeanUtils.copyProperties(request, dto);
        return dto;
    }

    private AgentWorkflowBusinessStartDto toBusinessStartDto(AgentWorkflowStartBusinessInstanceRequest request) {
        if (request == null) return null;
        AgentWorkflowBusinessStartDto dto = new AgentWorkflowBusinessStartDto();
        BeanUtils.copyProperties(request, dto);
        return dto;
    }

    private AgentWorkflowWebhookTriggerDto toWebhookTriggerDto(AgentWorkflowCreateWebhookRequest request) {
        if (request == null) return null;
        AgentWorkflowWebhookTriggerDto dto = new AgentWorkflowWebhookTriggerDto();
        BeanUtils.copyProperties(request, dto);
        return dto;
    }

    /**
     * 校验ConcurrencyLimit。
     */
    private void validateConcurrencyLimit(AgentWorkflowDto dto) {
        if (dto != null && dto.getMaxConcurrentInstances() != null && dto.getMaxConcurrentInstances() < 0)
            throw new ServerException(422, I18nUtils.getMessage("workflow.max-concurrent-instances.invalid"));
    }

    /**
     * 处理jsonItems。
     */
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

    /**
     * 处理difference。
     */
    private List<String> difference(Set<String> left, Set<String> right) {
        return left.stream().filter(item -> !right.contains(item)).sorted().collect(Collectors.toList());
    }

    /**
     * 处理intersectionChanged。
     */
    private List<String> intersectionChanged(Map<String, String> before, Map<String, String> after) {
        return before.keySet().stream().filter(after::containsKey).filter(key -> !StringUtils.equals(before.get(key), after.get(key)))
                .sorted().collect(Collectors.toList());
    }

    /**
     * 规范化Json。
     */
    private String normalizeJson(String source) {
        if (StringUtils.isBlank(source)) return "";
        try {
            return JSONObject.parse(source).toJSONString();
        } catch (Exception ignored) {
            return source;
        }
    }

    /**
     * 处理required。
     */
    private AgentWorkflow required(String id) {
        AgentWorkflow value = workflowService.getById(id);
        if (value == null || Boolean.TRUE.equals(value.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("workflow.not-found"));
        return value;
    }

    private void addNodeType(List<Map<String, String>> types, String type, String label) {
        Map<String, String> item = new LinkedHashMap<String, String>();
        item.put("type", type);
        item.put("label", label);
        types.add(item);
    }

    /**
     * 校验Resources。
     */
    private void validateResources(String nodes, String applicationId) {
        for (Object value : JSONArray.parseArray(nodes)) {
            JSONObject node = (JSONObject) value;
            String type = node.getString("type"), resourceId = node.getString("resourceId");
            if ("agent".equals(type)) {
                AgentDefinition agent = agentDefinitionService.getById(resourceId);
                if (agent == null || Boolean.TRUE.equals(agent.getDeleted()) || !Integer.valueOf(1).equals(agent.getStatus()))
                    throw new ServerException(422, I18nUtils.getMessage("workflow.node.agent.unavailable"));
                if (!StringUtils.equals(applicationId, normalizedApplicationId(agent.getApplicationId())))
                    throw new ServerException(422, "工作流与 Agent 必须绑定到同一业务空间");
            }
            if ("tool".equals(type)) {
                AgentTool tool = agentToolService.getById(resourceId);
                if (tool == null || Boolean.TRUE.equals(tool.getDeleted()) || !Integer.valueOf(1).equals(tool.getStatus()))
                    throw new ServerException(422, I18nUtils.getMessage("workflow.node.mcp-tool.unavailable"));
            }
            if ("approval".equals(type) && StringUtils.isNotBlank(node.getString("approverServiceAccountId"))) {
                ServiceAccount approver = serviceAccountService.getById(node.getString("approverServiceAccountId"));
                if (approver == null || Boolean.TRUE.equals(approver.getDeleted()) || !Boolean.TRUE.equals(approver.getEnabled()))
                    throw new ServerException(422, "审批节点绑定的服务账号不存在或已停用");
                if (!StringUtils.equals(applicationId, normalizedApplicationId(approver.getApplicationId())))
                    throw new ServerException(422, "工作流与审批服务账号必须绑定到同一业务空间");
            }
            if ("subflow".equals(type)) {
                AgentWorkflow child = workflowService.getById(node.getString("workflowId"));
                if (child == null || Boolean.TRUE.equals(child.getDeleted()) || !Integer.valueOf(1).equals(child.getStatus()))
                    throw new ServerException(422, "子流程不存在或未发布");
                if (!StringUtils.equals(applicationId, normalizedApplicationId(child.getApplicationId())))
                    throw new ServerException(422, "工作流与子流程必须绑定到同一业务空间");
                AgentWorkflowVersion childVersion = versionService.getOne(Wrappers.lambdaQuery(AgentWorkflowVersion.class)
                        .eq(AgentWorkflowVersion::getWorkflowId, child.getId()).eq(AgentWorkflowVersion::getVersionNo, node.getIntValue("versionNo"))
                        .eq(AgentWorkflowVersion::getDeleted, false));
                if (childVersion == null) throw new ServerException(422, "子流程固定版本不存在");
            }
        }
    }

    /** 发布时递归检查子流程引用环，防止实例运行后递归启动。 */
    private void validateSubflowDependencies(AgentWorkflow root) {
        validateSubflowDependencies(root, new LinkedHashSet<String>());
    }

    private void validateSubflowDependencies(AgentWorkflow workflow, Set<String> visiting) {
        if (!visiting.add(workflow.getId())) throw new ServerException(422, "子流程存在循环引用");
        try {
            for (Object value : JSONArray.parseArray(StringUtils.defaultIfBlank(workflow.getNodes(), "[]"))) {
                JSONObject node = (JSONObject) value;
                if (!"subflow".equals(node.getString("type"))) continue;
                String childId = node.getString("workflowId");
                if (StringUtils.equals(childId, workflow.getId())) throw new ServerException(422, "子流程不能引用自身");
                AgentWorkflow child = workflowService.getById(childId);
                if (child == null || Boolean.TRUE.equals(child.getDeleted()) || !Integer.valueOf(1).equals(child.getStatus()))
                    throw new ServerException(422, "子流程不存在或未发布");
                if (!StringUtils.equals(workflow.getApplicationId(), child.getApplicationId()))
                    throw new ServerException(422, "工作流与子流程必须绑定到同一业务空间");
                AgentWorkflowVersion fixedVersion = versionService.getOne(Wrappers.lambdaQuery(AgentWorkflowVersion.class)
                        .eq(AgentWorkflowVersion::getWorkflowId, childId).eq(AgentWorkflowVersion::getVersionNo, node.getIntValue("versionNo"))
                        .eq(AgentWorkflowVersion::getDeleted, false));
                if (fixedVersion == null) throw new ServerException(422, "子流程固定版本不存在");
                AgentWorkflow snapshot = new AgentWorkflow();
                snapshot.setId(child.getId());
                snapshot.setApplicationId(child.getApplicationId());
                snapshot.setStatus(child.getStatus());
                snapshot.setNodes(fixedVersion.getNodes());
                validateSubflowDependencies(snapshot, visiting);
            }
        } finally {
            visiting.remove(workflow.getId());
        }
    }

    /**
     * 统一将空业务空间归入平台默认空间，并拒绝已停用或不存在的空间。
     */
    private String requireActiveApplication(String applicationId) {
        String value = normalizedApplicationId(applicationId);
        applicationService.requireActive(value);
        return value;
    }

    private String normalizedApplicationId(String applicationId) {
        return StringUtils.defaultIfBlank(applicationId, AgentApplicationService.PLATFORM_APPLICATION_ID);
    }

    /**
     * 用户Id。
     */
    private String userId() {
        Map<String, String> user = CurrentUser.getUser();
        if (user == null || StringUtils.isBlank(user.get("userId")))
            throw new ServerException(401, I18nUtils.getMessage("auth.session.expired"));
        return user.get("userId");
    }

    /**
     * 处理noStore。
     */
    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }
}
