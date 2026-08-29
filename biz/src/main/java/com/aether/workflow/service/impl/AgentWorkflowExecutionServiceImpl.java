package com.aether.workflow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentStreamCallback;
import com.aether.workflow.dto.AgentWorkflowBusinessStartDto;
import com.aether.workflow.dto.AgentWorkflowInteractionDto;
import com.aether.workflow.dto.AgentWorkflowEventDto;
import com.aether.workflow.entity.*;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.workflow.service.*;
import com.aether.agent.tools.AgentToolWorkflow;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.msg.entity.Email;
import com.aether.msg.service.EmailMessageService;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import com.aether.workflow.runtime.WorkflowConditionEvaluator;
import com.aether.workflow.runtime.WorkflowDefinitionValidator;
import com.aether.workflow.runtime.WorkflowVariableRenderer;
import com.aether.workflow.runtime.WorkflowSseHub;
import com.aether.workflow.runtime.WorkflowCallbackService;
import com.aether.workflow.runtime.WorkflowExecutionJobDispatcher;
import com.aether.workflow.runtime.WorkflowSensitiveDataSanitizer;
import com.aether.workflow.runtime.WorkflowHttpNodeExecutor;
import com.aether.workflow.runtime.WorkflowOutputResolver;
import com.aether.sys.entity.Role;
import com.aether.sys.entity.UserRole;
import com.aether.sys.service.RoleService;
import com.aether.sys.service.UserRoleService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;

import java.util.*;

/**
 * 工作流图遍历执行器。支持顺序、连线条件分支和循环流程。
 * <p>每次状态改变均先持久化；进程重启后可继续等待人工操作，已完成节点不会被重复调用。
 * 循环通过回跳边实现，每条回跳边可配置 maxIterations（默认 10，上限 100）。</p>
 */
@Service
public class AgentWorkflowExecutionServiceImpl implements AgentWorkflowExecutionService {
    private static final int DEFAULT_MAX_ITERATIONS = 10;
    private static final int MAX_ITERATIONS_CAP = 100;
    private final ThreadLocal<Boolean> partialParallelBranch = new ThreadLocal<Boolean>();

    private final AgentWorkflowService workflowService;
    private final AgentWorkflowVersionService versionService;
    private final AgentWorkflowInstanceService instanceService;
    private final AgentWorkflowNodeInstanceService nodeService;
    private final AgentChatService chatService;
    private final AgentToolWorkflow toolWorkflow;
    private final WorkflowSseHub sseHub;
    private final WorkflowCallbackService callbackService;
    private final WorkflowExecutionJobDispatcher executionJobDispatcher;
    private final WorkflowSensitiveDataSanitizer sensitiveDataSanitizer;
    private final AgentWorkflowAuditEventService auditEventService;
    private final AgentWorkflowExternalInvocationService externalInvocationService;
    private final WorkflowHttpNodeExecutor httpNodeExecutor;
    private final EmailMessageService emailMessageService;
    private final AgentWorkflowSubflowLinkService subflowLinkService;
    private final WorkflowOutputResolver outputResolver;
    private final AgentWorkflowNodeTokenService nodeTokenService;
    private final AgentWorkflowJoinStateService joinStateService;
    private final AgentWorkflowVariableSnapshotService variableSnapshotService;
    private final AgentWorkflowEventReceiptService eventReceiptService;
    private final UserRoleService userRoleService;
    private final RoleService roleService;

    /**
     * 创建 {@code AgentWorkflowExecutionServiceImpl} 实例。
     */
    public AgentWorkflowExecutionServiceImpl(AgentWorkflowService workflowService, AgentWorkflowVersionService versionService,
                                             AgentWorkflowInstanceService instanceService, AgentWorkflowNodeInstanceService nodeService,
                                             AgentChatService chatService, AgentToolWorkflow toolWorkflow, WorkflowSseHub sseHub,
                                             WorkflowCallbackService callbackService, WorkflowExecutionJobDispatcher executionJobDispatcher,
                                             WorkflowSensitiveDataSanitizer sensitiveDataSanitizer, AgentWorkflowAuditEventService auditEventService,
                                             AgentWorkflowExternalInvocationService externalInvocationService, WorkflowHttpNodeExecutor httpNodeExecutor,
                                             EmailMessageService emailMessageService, AgentWorkflowSubflowLinkService subflowLinkService,
                                             WorkflowOutputResolver outputResolver, AgentWorkflowNodeTokenService nodeTokenService,
                                             AgentWorkflowJoinStateService joinStateService, AgentWorkflowVariableSnapshotService variableSnapshotService,
                                             AgentWorkflowEventReceiptService eventReceiptService, UserRoleService userRoleService, RoleService roleService) {
        this.workflowService = workflowService;
        this.versionService = versionService;
        this.instanceService = instanceService;
        this.nodeService = nodeService;
        this.chatService = chatService;
        this.toolWorkflow = toolWorkflow;
        this.sseHub = sseHub;
        this.callbackService = callbackService;
        this.executionJobDispatcher = executionJobDispatcher;
        this.sensitiveDataSanitizer = sensitiveDataSanitizer;
        this.auditEventService = auditEventService;
        this.externalInvocationService = externalInvocationService;
        this.httpNodeExecutor = httpNodeExecutor;
        this.emailMessageService = emailMessageService;
        this.subflowLinkService = subflowLinkService;
        this.outputResolver = outputResolver;
        this.nodeTokenService = nodeTokenService;
        this.joinStateService = joinStateService;
        this.variableSnapshotService = variableSnapshotService;
        this.eventReceiptService = eventReceiptService;
        this.userRoleService = userRoleService;
        this.roleService = roleService;
    }

    // ── 公共接口 ─────────────────────────────────────────────

    /**
     * 启动处理流程。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowInstance start(String workflowId, Map<String, Object> variables, String userId) {
        return startInternal(workflowId, variables, userId, null, null);
    }

    /**
     * 处理startBusiness。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowInstance startBusiness(String workflowId, AgentWorkflowBusinessStartDto dto, String userId) {
        if (dto == null || StringUtils.isBlank(dto.getBusinessType()) || StringUtils.isBlank(dto.getBusinessId()) || StringUtils.isBlank(dto.getIdempotencyKey()))
            throw new ServerException(422, I18nUtils.getMessage("workflow.business-start.fields.required"));
        if (dto.getBusinessType().length() > 64 || dto.getBusinessId().length() > 128 || dto.getIdempotencyKey().length() > 128)
            throw new ServerException(422, I18nUtils.getMessage("workflow.business-start.identifier.length.exceeded"));
        if (dto.getDeadlineAt() != null && dto.getDeadlineAt() <= System.currentTimeMillis())
            throw new ServerException(422, I18nUtils.getMessage("workflow.business-start.deadline.invalid"));
        try {
            callbackService.validateCallbackUrl(dto.getCallbackUrl());
        } catch (IllegalArgumentException ex) {
            throw new ServerException(422, ex.getMessage());
        }
        // 锁定定义行，使“查询既有实例 → 创建实例”在同一个工作流内串行化；
        // 避免并发的同幂等请求同时越过查询并触发重复节点执行。
        AgentWorkflow lockedWorkflow = workflowService.getOne(Wrappers.lambdaQuery(AgentWorkflow.class)
                .eq(AgentWorkflow::getId, workflowId).eq(AgentWorkflow::getDeleted, false).last("FOR UPDATE"));
        if (lockedWorkflow == null) throw new ServerException(404, I18nUtils.getMessage("workflow.not-found"));
        AgentWorkflowInstance existing = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getWorkflowId, workflowId).eq(AgentWorkflowInstance::getUserId, userId)
                .eq(AgentWorkflowInstance::getIdempotencyKey, dto.getIdempotencyKey())
                .eq(AgentWorkflowInstance::getDeleted, false));
        if (existing != null) return existing;
        return startInternal(workflowId, dto.getVariables(), userId, dto, null);
    }

    /**
     * 处理startInternal。
     */
    private AgentWorkflowInstance startInternal(String workflowId, Map<String, Object> variables, String userId,
                                                AgentWorkflowBusinessStartDto business, Integer fixedVersionNo) {
        return startInternal(workflowId, variables, userId, business, fixedVersionNo, null);
    }

    private AgentWorkflowInstance startInternal(String workflowId, Map<String, Object> variables, String userId,
                                                AgentWorkflowBusinessStartDto business, Integer fixedVersionNo,
                                                Long inheritedDeadlineAt) {
        // 以定义行锁串行化容量检查与实例插入；不同应用实例不能同时越过并发上限。
        AgentWorkflow workflow = workflowService.getOne(Wrappers.lambdaQuery(AgentWorkflow.class)
                .eq(AgentWorkflow::getId, workflowId).eq(AgentWorkflow::getDeleted, false).last("FOR UPDATE"));
        if (workflow == null) throw new ServerException(404, I18nUtils.getMessage("workflow.not-found"));
        if (!Integer.valueOf(1).equals(workflow.getStatus()) || workflow.getPublishedVersion() == null)
            throw new ServerException(422, I18nUtils.getMessage("workflow.start.unpublished"));
        int maxConcurrent = workflow.getMaxConcurrentInstances() == null ? 0 : workflow.getMaxConcurrentInstances();
        if (maxConcurrent > 0) {
            long activeCount = instanceService.count(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                    .eq(AgentWorkflowInstance::getWorkflowId, workflowId)
                    .in(AgentWorkflowInstance::getStatus, "RUNNING", "WAITING_USER", "WAITING_EVENT", "WAITING_DELAY", "WAITING_SUBFLOW")
                    .eq(AgentWorkflowInstance::getDeleted, false));
            if (activeCount >= maxConcurrent)
                throw new ServerException(429, I18nUtils.getMessage("workflow.concurrent-instance-limit.exceeded"));
        }
        int versionNo = fixedVersionNo == null ? workflow.getPublishedVersion() : fixedVersionNo;
        AgentWorkflowVersion version = versionService.getOne(Wrappers.lambdaQuery(AgentWorkflowVersion.class)
                .eq(AgentWorkflowVersion::getWorkflowId, workflowId).eq(AgentWorkflowVersion::getVersionNo, versionNo)
                .eq(AgentWorkflowVersion::getDeleted, false));
        if (version == null)
            throw new ServerException(409, I18nUtils.getMessage("workflow.published-version.not-found"));
        WorkflowDefinitionValidator.validateStartVariables(version.getInputSchema(), variables);
        AgentWorkflowInstance instance = new AgentWorkflowInstance();
        instance.setApplicationId(workflow.getApplicationId());
        instance.setWorkflowId(workflowId);
        instance.setWorkflowVersionId(version.getId());
        instance.setUserId(userId);
        if (business != null) {
            instance.setBusinessType(business.getBusinessType());
            instance.setBusinessId(business.getBusinessId());
            instance.setIdempotencyKey(business.getIdempotencyKey());
            instance.setCallbackUrl(business.getCallbackUrl());
            instance.setDeadlineAt(business.getDeadlineAt());
        }
        if (inheritedDeadlineAt != null) instance.setDeadlineAt(inheritedDeadlineAt);
        instance.setStatus("RUNNING");
        instance.setVariables(JSON.toJSONString(variables == null ? new LinkedHashMap<String, Object>() : variables));
        instance.setStartedAt(System.currentTimeMillis());
        instanceService.save(instance);
        auditEventService.record(instance.getId(), null, "INSTANCE_STARTED", userId, "工作流实例已启动", instance.getVariables());
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
        return instance;
    }

    /**
     * 详情当前请求。
     */
    @Override
    public AgentWorkflowInstanceVo detail(String instanceId, String userId) {
        AgentWorkflowInstance instance = owned(instanceId, userId);
        AgentWorkflowInstanceVo vo = new AgentWorkflowInstanceVo();
        org.springframework.beans.BeanUtils.copyProperties(instance, vo);
        AgentWorkflow workflow = workflowService.getById(instance.getWorkflowId());
        if (workflow != null) vo.setWorkflowName(workflow.getName());
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        if (version != null) {
            vo.setVersionNodes(version.getNodes());
            vo.setVersionEdges(version.getEdges());
        }
        vo.setNodes(nodeService.list(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                .eq(AgentWorkflowNodeInstance::getInstanceId, instanceId).orderByAsc(AgentWorkflowNodeInstance::getCreatedAt)));
        return vo;
    }

    /**
     * 处理answer。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void answer(String instanceId, AgentWorkflowInteractionDto dto, String userId) {
        AgentWorkflowInstance instance = ownedForUpdate(instanceId, userId);
        if (!"WAITING_USER".equals(instance.getStatus()))
            throw new ServerException(409, I18nUtils.getMessage("workflow.instance.not-waiting-for-user"));
        AgentWorkflowNodeInstance node = currentNode(instance);
        Map<String, Object> answer = dto == null || dto.getAnswer() == null ? new LinkedHashMap<String, Object>() : dto.getAnswer();
        JSONObject config = StringUtils.isBlank(node.getInteractionConfig()) ? new JSONObject() : JSONObject.parseObject(node.getInteractionConfig());
        if ("approval".equals(config.getString("type"))) {
            String approver = config.getString("approverServiceAccountId");
            if (StringUtils.isNotBlank(approver) && !StringUtils.equals("sa:" + approver, userId))
                throw new ServerException(403, "当前服务账号不是该审批节点的审批人");
            auditEventService.record(instance.getId(), node.getId(), "APPROVAL_SUBMITTED", userId, "已提交审批结论", JSON.toJSONString(answer));
        } else {
            auditEventService.record(instance.getId(), node.getId(), "HUMAN_ANSWER_SUBMITTED", userId, "已提交人工节点回答或工具确认", JSON.toJSONString(answer));
        }
        if ("agent".equals(node.getNodeType()) && "agent".equals(config.getString("source"))) {
            // 将用户回答持久化为节点恢复上下文，模型续聊交给后台任务，不能占住 HTTP 请求。
            config.put("pendingAnswer", answer);
            node.setInteractionConfig(config.toJSONString());
            node.setStatus("RUNNING");
            nodeService.updateById(node);
            instance.setStatus("RUNNING");
            instance.setErrorMessage(null);
            instanceService.updateById(instance);
            executionJobDispatcher.enqueueAfterCommit(instance.getId());
            return;
        }
        if (isToolNode(node.getNodeType()) || isMcpToolApprovalConfig(config)) {
            // 工具执行可访问远端 MCP，和模型调用一样交由后台消费者，回答接口只负责可靠落库。
            config.put("pendingAnswer", answer);
            node.setInteractionConfig(config.toJSONString());
            node.setStatus("RUNNING");
            nodeService.updateById(node);
            instance.setStatus("RUNNING");
            instance.setErrorMessage(null);
            instanceService.updateById(instance);
            executionJobDispatcher.enqueueAfterCommit(instance.getId());
            return;
        } else {
            Map<String, Object> variables = variables(instance);
            JSONObject definition = currentDefinition(instance, node);
            // 人工回答通常为单个字段（如 {answer: "内容"}），传给后续 AI 时只取内容而非整段 JSON
            Object answerValue = answer.size() == 1 ? answer.values().iterator().next() : answer;
            if (definition != null) {
                applyStateMapping(definition, answerValue, variables);
            } else {
                String outputKey = config.getString("outputKey");
                if (StringUtils.isNotBlank(outputKey)) variables.put(outputKey, answerValue);
                String internalKey = config.getString("internalKey");
                if (StringUtils.isNotBlank(internalKey)) variables.put(internalKey, answerValue);
            }
            instance.setVariables(JSON.toJSONString(variables));
            completeNode(node, JSON.toJSONString(answer));
        }
        // 完成当前节点后，找到下一个节点继续执行
        String nextNodeId = findNextNodeId(instance, node);
        if (nextNodeId != null) {
            instance.setCurrentNodeId(nextNodeId);
        }
        instance.setStatus("RUNNING");
        instanceService.updateById(instance);
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signalEvent(String instanceId, String eventType, AgentWorkflowEventDto dto, String userId) {
        AgentWorkflowInstance instance = ownedForUpdate(instanceId, userId);
        if (!"WAITING_EVENT".equals(instance.getStatus()))
            throw new ServerException(409, "工作流实例当前未等待业务事件");
        AgentWorkflowNodeInstance node = currentNode(instance);
        resumeEvent(instance, node, eventType, dto, userId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int signalEventByType(String applicationId, String eventType, AgentWorkflowEventDto dto, String userId) {
        if (StringUtils.isBlank(eventType)) throw new ServerException(422, "业务事件类型不能为空");
        if (dto == null || StringUtils.isBlank(dto.getEventId())) throw new ServerException(422, "通用业务事件必须提供 eventId");
        if (dto.getEventId().length() > 256) throw new ServerException(422, "eventId 长度不能超过 256");
        if (!eventReceiptService.claim(applicationId, eventType, dto.getEventId(), dto.getCorrelationKey())) return 0;
        int resumed = 0;
        List<AgentWorkflowInstance> candidates = instanceService.list(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getApplicationId, applicationId).eq(AgentWorkflowInstance::getStatus, "WAITING_EVENT")
                .eq(AgentWorkflowInstance::getDeleted, false).orderByAsc(AgentWorkflowInstance::getCreatedAt).last("LIMIT 100"));
        for (AgentWorkflowInstance candidate : candidates) {
            AgentWorkflowInstance instance = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                    .eq(AgentWorkflowInstance::getId, candidate.getId()).eq(AgentWorkflowInstance::getStatus, "WAITING_EVENT")
                    .eq(AgentWorkflowInstance::getDeleted, false).last("FOR UPDATE"));
            if (instance == null) continue;
            AgentWorkflowNodeInstance node = currentNode(instance);
            JSONObject config = StringUtils.isBlank(node.getInteractionConfig()) ? new JSONObject() : JSONObject.parseObject(node.getInteractionConfig());
            if (!StringUtils.equals(eventType, config.getString("eventType"))) continue;
            String expected = config.getString("correlationKey");
            if (StringUtils.isNotBlank(expected) && !StringUtils.equals(expected, dto == null ? null : dto.getCorrelationKey())) continue;
            resumeEvent(instance, node, eventType, dto, userId, false);
            resumed++;
        }
        return resumed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void timeoutEvent(String instanceId, String nodeId) {
        AgentWorkflowInstance instance = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getId, instanceId).eq(AgentWorkflowInstance::getStatus, "WAITING_EVENT")
                .eq(AgentWorkflowInstance::getDeleted, false).last("FOR UPDATE"));
        if (instance == null || !StringUtils.equals(nodeId, instance.getCurrentNodeId())) return;
        AgentWorkflowNodeInstance node = currentNode(instance);
        JSONObject config = StringUtils.isBlank(node.getInteractionConfig()) ? new JSONObject() : JSONObject.parseObject(node.getInteractionConfig());
        if (config.getLongValue("timeoutAt") <= 0 || config.getLongValue("timeoutAt") > System.currentTimeMillis()) return;
        String target = config.getString("timeoutTargetId");
        if (StringUtils.isBlank(target)) { fail(instance, node, "等待业务事件超时"); return; }
        completeNode(node, "{\"timedOut\":true}");
        instance.setCurrentNodeId(target); instance.setStatus("RUNNING"); instance.setErrorMessage(null);
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), node.getId(), "BUSINESS_EVENT_TIMED_OUT", "SYSTEM", "等待业务事件超时，已进入超时分支", null);
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
    }

    private void resumeEvent(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node, String eventType,
                             AgentWorkflowEventDto dto, String userId, boolean strict) {
        JSONObject config = StringUtils.isBlank(node.getInteractionConfig()) ? new JSONObject() : JSONObject.parseObject(node.getInteractionConfig());
        if (strict && !StringUtils.equals(eventType, config.getString("eventType")))
            throw new ServerException(422, "业务事件类型与等待节点不匹配");
        String expectedCorrelation = config.getString("correlationKey");
        String actualCorrelation = dto == null ? null : dto.getCorrelationKey();
        if (strict && StringUtils.isNotBlank(expectedCorrelation) && !StringUtils.equals(expectedCorrelation, actualCorrelation))
            throw new ServerException(422, "业务事件关联键不匹配");
        Map<String, Object> variables = variables(instance);
        if (dto != null && dto.getData() != null) {
            variables.putAll(dto.getData());
            applyStateMapping(currentDefinition(instance, node), dto.getData(), variables);
        }
        instance.setVariables(JSON.toJSONString(variables));
        completeNode(node, dto == null ? null : JSON.toJSONString(dto.getData()));
        String nextNodeId = findNextNodeId(instance, node);
        if (nextNodeId != null) instance.setCurrentNodeId(nextNodeId);
        instance.setStatus("RUNNING");
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), node.getId(), "BUSINESS_EVENT_RECEIVED", userId, "已接收业务事件：" + eventType,
                dto == null ? null : JSON.toJSONString(dto.getData()));
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumeDelay(String instanceId, String nodeId) {
        AgentWorkflowInstance instance = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getId, instanceId).eq(AgentWorkflowInstance::getStatus, "WAITING_DELAY")
                .eq(AgentWorkflowInstance::getDeleted, false).last("FOR UPDATE"));
        if (instance == null || !StringUtils.equals(nodeId, instance.getCurrentNodeId())) return;
        AgentWorkflowNodeInstance node = currentNode(instance);
        if (node == null || !"WAITING_DELAY".equals(node.getStatus())) return;
        JSONObject config = StringUtils.isBlank(node.getInteractionConfig()) ? new JSONObject() : JSONObject.parseObject(node.getInteractionConfig());
        if (config.getLongValue("resumeAt") > System.currentTimeMillis()) return;
        completeNode(node, null);
        String nextNodeId = findNextNodeId(instance, node);
        if (nextNodeId != null) instance.setCurrentNodeId(nextNodeId);
        instance.setStatus("RUNNING");
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), node.getId(), "DELAY_ELAPSED", "SYSTEM", "延时节点已到期", null);
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmExternalInvocation(String instanceId, String invocationId, String responseData, String userId) {
        AgentWorkflowInstance instance = ownedForUpdate(instanceId, userId);
        AgentWorkflowExternalInvocation invocation = requiredUnknownExternalInvocation(instance, invocationId);
        if (!"FAILED".equals(instance.getStatus()))
            throw new ServerException(409, "仅失败实例可以确认外部调用结果");
        AgentWorkflowNodeInstance node = currentNode(instance);
        if (!StringUtils.equals(node.getId(), invocation.getNodeInstanceId()))
            throw new ServerException(409, "外部调用记录不属于当前失败节点");
        String output = StringUtils.defaultIfBlank(responseData, invocation.getResponseData());
        externalInvocationService.confirmSuccess(invocation.getId(), output);
        JSONObject definition = currentDefinition(instance, node);
        Map<String, Object> variables = variables(instance);
        applyStateMapping(definition, output, variables);
        instance.setVariables(JSON.toJSONString(variables));
        completeNode(node, output);
        String nextNodeId = findNextNodeId(instance, node);
        if (nextNodeId != null) instance.setCurrentNodeId(nextNodeId);
        instance.setStatus("RUNNING");
        instance.setErrorMessage(null);
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), node.getId(), "EXTERNAL_INVOCATION_CONFIRMED", userId,
                "已人工确认外部调用成功", output);
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryExternalInvocation(String instanceId, String invocationId, String userId) {
        AgentWorkflowInstance instance = ownedForUpdate(instanceId, userId);
        AgentWorkflowExternalInvocation invocation = requiredUnknownExternalInvocation(instance, invocationId);
        if (!"FAILED".equals(instance.getStatus()))
            throw new ServerException(409, "仅失败实例可以重试外部调用");
        AgentWorkflowNodeInstance node = currentNode(instance);
        if (!StringUtils.equals(node.getId(), invocation.getNodeInstanceId()))
            throw new ServerException(409, "外部调用记录不属于当前失败节点");
        node.setStatus("PENDING");
        node.setErrorMessage(null);
        node.setRetryCount((node.getRetryCount() == null ? 0 : node.getRetryCount()) + 1);
        nodeService.updateById(node);
        externalInvocationService.resetForManualRetry(node.getId());
        instance.setStatus("RUNNING");
        instance.setErrorMessage(null);
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), node.getId(), "EXTERNAL_INVOCATION_RETRY_REQUESTED", userId,
                "已人工确认重试外部调用", null);
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
    }

    /**
     * 重试当前请求。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retry(String instanceId, String userId) {
        AgentWorkflowInstance instance = ownedForUpdate(instanceId, userId);
        if (!"FAILED".equals(instance.getStatus()))
            throw new ServerException(409, I18nUtils.getMessage("workflow.instance.retry.failed-only"));
        AgentWorkflowNodeInstance node = currentNode(instance);
        node.setStatus("PENDING");
        node.setErrorMessage(null);
        node.setRetryCount((node.getRetryCount() == null ? 0 : node.getRetryCount()) + 1);
        nodeService.updateById(node);
        externalInvocationService.resetForManualRetry(node.getId());
        auditEventService.record(instance.getId(), node.getId(), "INSTANCE_RETRY_REQUESTED", userId, "已请求重试当前节点", null);
        instance.setStatus("RUNNING");
        instance.setErrorMessage(null);
        instanceService.updateById(instance);
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryNode(String instanceId, String nodeId, String userId) {
        AgentWorkflowInstance instance = ownedForUpdate(instanceId, userId);
        if (!"FAILED".equals(instance.getStatus()))
            throw new ServerException(409, I18nUtils.getMessage("workflow.instance.retry.failed-only"));
        AgentWorkflowNodeInstance node = currentNode(instance);
        if (!StringUtils.equals(nodeId, node.getNodeId()))
            throw new ServerException(409, "只能重试当前失败节点");
        node.setStatus("PENDING");
        node.setErrorMessage(null);
        node.setRetryCount((node.getRetryCount() == null ? 0 : node.getRetryCount()) + 1);
        nodeService.updateById(node);
        externalInvocationService.resetForManualRetry(node.getId());
        instance.setStatus("RUNNING");
        instance.setErrorMessage(null);
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), node.getId(), "NODE_RETRY_REQUESTED", userId, "已请求重试当前节点", null);
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
    }

    /**
     * 处理replay。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public AgentWorkflowInstance replay(String instanceId, String userId) {
        AgentWorkflowInstance source = owned(instanceId, userId);
        if (StringUtils.isNotBlank(source.getBusinessType()) || StringUtils.isNotBlank(source.getBusinessId()) || StringUtils.isNotBlank(source.getIdempotencyKey()))
            throw new ServerException(409, I18nUtils.getMessage("workflow.instance.replay.business-instance.disallowed"));
        Map<String, Object> variables;
        try {
            variables = StringUtils.isBlank(source.getVariables()) ? new LinkedHashMap<String, Object>() : JSON.parseObject(source.getVariables(), Map.class);
        } catch (Exception ex) {
            throw new ServerException(422, I18nUtils.getMessage("workflow.instance.replay.variables.invalid"));
        }
        return startInternal(source.getWorkflowId(), variables, userId, null, null);
    }

    /**
     * 执行Pending。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executePending(String instanceId) {
        AgentWorkflowInstance instance = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getId, instanceId).eq(AgentWorkflowInstance::getDeleted, false).last("FOR UPDATE"));
        if (instance != null && "RUNNING".equals(instance.getStatus())) advance(instance);
    }

    /**
     * 处理failPendingExecution。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void failPendingExecution(String instanceId, String errorMessage) {
        AgentWorkflowInstance instance = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getId, instanceId).eq(AgentWorkflowInstance::getDeleted, false).last("FOR UPDATE"));
        if (instance == null || !"RUNNING".equals(instance.getStatus())) return;
        instance.setStatus("FAILED");
        instance.setErrorMessage(StringUtils.defaultIfBlank(errorMessage, "后台执行任务连续失败"));
        instance.setCompletedAt(System.currentTimeMillis());
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), null, "INSTANCE_FAILED", "SYSTEM", instance.getErrorMessage(), null);
        sseHub.publish(instance.getId(), "run.failed", instance);
        callbackService.recordTerminal(instance);
        resumeParentSubflow(instance);
    }

    /**
     * 处理terminate。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(String instanceId, String userId) {
        AgentWorkflowInstance instance = ownedForUpdate(instanceId, userId);
        if ("COMPLETED".equals(instance.getStatus()) || "TERMINATED".equals(instance.getStatus()) || "TIMED_OUT".equals(instance.getStatus()))
            throw new ServerException(409, I18nUtils.getMessage("workflow.instance.completed"));
        instance.setStatus("TERMINATED");
        instance.setCompletedAt(System.currentTimeMillis());
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), null, "INSTANCE_TERMINATED", userId, "工作流实例已终止", null);
        callbackService.recordTerminal(instance);
        resumeParentSubflow(instance);
    }

    /**
     * 更新Variables。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVariables(String instanceId, Map<String, Object> variables, String userId) {
        AgentWorkflowInstance instance = owned(instanceId, userId);
        if (!"RUNNING".equals(instance.getStatus()) && !"WAITING_USER".equals(instance.getStatus()) && !"FAILED".equals(instance.getStatus()))
            throw new ServerException(409, I18nUtils.getMessage("workflow.instance.variables.update.disallowed"));
        if (variables == null || variables.isEmpty()) return;
        Map<String, Object> current = variables(instance);
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (StringUtils.isBlank(key) || key.startsWith("_")) continue;
            if (entry.getValue() == null) current.remove(key);
            else current.put(key, entry.getValue());
        }
        instance.setVariables(JSON.toJSONString(current));
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), null, "VARIABLES_UPDATED", userId, "已更新工作流变量", instance.getVariables());
        sseHub.publish(instance.getId(), "variables.updated", instance);
    }

    // ── 图遍历执行引擎 ───────────────────────────────────────

    /**
     * 从当前节点开始，沿图遍历执行工作流。
     * <p>执行语义：每次调用推进一个节点。节点完成后递归调用自身继续推进。
     * 遇到人工操作或 MCP 审批时暂停，等待 answer() 回调后继续；定时任务中的 MCP 审批自动通过。</p>
     */
    private void advance(AgentWorkflowInstance instance) {
        if (!"RUNNING".equals(instance.getStatus())) return;

        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        Map<String, JSONObject> nodeMap = buildNodeMap(version.getNodes());
        Map<String, List<JSONObject>> adj = WorkflowDefinitionValidator.buildAdjacency(version.getEdges());
        Map<String, Object> variables = variables(instance);

        // 确定要执行的节点
        String nodeId = instance.getCurrentNodeId();
        if (nodeId == null) {
            nodeId = findStartNode(nodeMap);
        }

        // 循环执行直到暂停或完成
        while (nodeId != null && "RUNNING".equals(instance.getStatus())) {
            JSONObject definition = nodeMap.get(nodeId);
            if (definition == null) {
                failCurrentNode(instance, nodeId, "节点定义不存在: " + nodeId);
                return;
            }

            // 获取或创建节点实例
            AgentWorkflowNodeInstance history = getOrCreateNodeInstance(instance, nodeId, definition);

            // 已完成的节点跳过
            if ("COMPLETED".equals(history.getStatus())) {
                // 并行节点完成时分支已经由 executeParallel 执行完毕，重试/恢复时必须直接进入汇聚节点，
                // 不能再次沿普通边进入第一个分支。
                if ("parallel".equals(history.getNodeType())) {
                    JSONObject parallelDefinition = nodeMap.get(nodeId);
                    String joinId = findParallelJoinNodeId(parallelDefinition, nodeMap, adj);
                    nodeId = StringUtils.isBlank(joinId)
                            ? findNextNodeIdFromGraph(adj, nodeId, nodeMap, variables, history, instance)
                            : joinId;
                } else {
                    nodeId = findNextNodeIdFromGraph(adj, nodeId, nodeMap, variables, history, instance);
                }
                continue;
            }

            // 执行节点
            instance.setCurrentNodeId(nodeId);
            instanceService.updateById(instance);
            executeNode(instance, history, definition, variables);

            if (!"RUNNING".equals(instance.getStatus())) return;

            // 节点完成，找下一个节点
            if ("parallel".equals(history.getNodeType()) && "COMPLETED".equals(history.getStatus())) {
                JSONObject parallelDefinition = nodeMap.get(nodeId);
                String joinId = findParallelJoinNodeId(parallelDefinition, nodeMap, adj);
                nodeId = StringUtils.isBlank(joinId) ? findNextNodeIdFromGraph(adj, nodeId, nodeMap, variables, history, instance) : joinId;
            } else {
                nodeId = findNextNodeIdFromGraph(adj, nodeId, nodeMap, variables, history, instance);
            }
        }

        // 只有实际到达结束节点才能完成实例；发布校验会保证所有路径最终可到达结束节点。
        if (nodeId == null && "RUNNING".equals(instance.getStatus())) {
            JSONObject terminal = nodeMap.get(instance.getCurrentNodeId());
            if (terminal != null && "end".equals(terminal.getString("type"))) {
                instance.setStatus("COMPLETED");
                instance.setCurrentNodeId(null);
                instance.setCompletedAt(System.currentTimeMillis());
                instanceService.updateById(instance);
                auditEventService.record(instance.getId(), null, "INSTANCE_COMPLETED", null, "工作流实例已完成", instance.getVariables());
                sseHub.publish(instance.getId(), "run.completed", instance);
                callbackService.recordTerminal(instance);
                resumeParentSubflow(instance);
            } else {
                instance.setStatus("FAILED");
                instance.setErrorMessage("流程未到达结束节点");
                instanceService.updateById(instance);
                auditEventService.record(instance.getId(), null, "INSTANCE_FAILED", null, "流程未到达结束节点", null);
                sseHub.publish(instance.getId(), "run.failed", instance);
                callbackService.recordTerminal(instance);
                resumeParentSubflow(instance);
            }
        }
    }

    /**
     * 执行Node。
     */
    private void executeNode(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node, JSONObject definition, Map<String, Object> variables) {
        node.setStatus("RUNNING");
        node.setStartedAt(System.currentTimeMillis());
        node.setInputData(sensitiveDataSanitizer.sanitizeJson(JSON.toJSONString(variables)));
        nodeService.updateById(node);
        String type = node.getNodeType();
        try {
            if ("start".equals(type) || "end".equals(type)) {
                completeNode(node, null);
                return;
            }
            if ("human".equals(type)) {
                waitForHuman(instance, node, definition, variables, false);
                return;
            }
            if ("approval".equals(type)) {
                waitForApproval(instance, node, definition, variables);
                return;
            }
            if (isToolNode(type)) {
                JSONObject interaction = StringUtils.isBlank(node.getInteractionConfig()) ? null : JSONObject.parseObject(node.getInteractionConfig());
                if (interaction != null && interaction.containsKey("pendingAnswer")) {
                    resumeMcpApproval(instance, node, interaction, readPendingAnswer(interaction), instance.getUserId());
                } else if (isScheduledInstance(instance)) {
                    resumeMcpApproval(instance, node, mcpInteractionConfig(instance, definition, variables), approvedMcpNodeAnswer(), instance.getUserId());
                } else waitForHuman(instance, node, definition, variables, true);
                return;
            }
            if ("transform".equals(type)) {
                Map<String, Object> transformed = applyTransform(definition, variables);
                applyStateMapping(definition, transformed, variables);
                instance.setVariables(JSON.toJSONString(variables));
                instanceService.updateById(instance);
                completeNode(node, JSON.toJSONString(transformed));
                return;
            }
            if ("rule".equals(type)) {
                Object result = evaluateRule(definition, variables);
                applyStateMapping(definition, result, variables);
                instance.setVariables(JSON.toJSONString(variables));
                instanceService.updateById(instance);
                completeNode(node, JSON.toJSONString(result));
                return;
            }
            if ("http".equals(type)) {
                String output = executeHttpNode(instance, node, definition, variables);
                applyStateMapping(definition, output, variables);
                instance.setVariables(JSON.toJSONString(variables));
                instanceService.updateById(instance);
                completeNode(node, output);
                return;
            }
            if ("notification".equals(type)) {
                String output = executeNotificationNode(instance, node, definition, variables);
                applyStateMapping(definition, output, variables);
                instance.setVariables(JSON.toJSONString(variables));
                instanceService.updateById(instance);
                completeNode(node, output);
                return;
            }
            if ("subflow".equals(type)) {
                startSubflow(instance, node, definition, variables);
                return;
            }
            if ("parallel".equals(type)) {
                executeParallel(instance, node, definition, variables);
                return;
            }
            if ("join".equals(type)) {
                executeJoin(instance, node, definition);
                return;
            }
            if ("wait_event".equals(type)) {
                waitForEvent(instance, node, definition, variables);
                return;
            }
            if ("delay".equals(type)) {
                waitForDelay(instance, node, definition);
                return;
            }
            if ("agent".equals(type)) {
                JSONObject interaction = StringUtils.isBlank(node.getInteractionConfig()) ? null : JSONObject.parseObject(node.getInteractionConfig());
                if (interaction != null && "agent".equals(interaction.getString("source")) && interaction.containsKey("pendingAnswer")) {
                    Object pending = interaction.get("pendingAnswer");
                    Map<String, Object> answer = pending instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>) pending)
                            : JSONObject.parseObject(JSON.toJSONString(pending), Map.class);
                    resumeAgentInteraction(instance, node, interaction, answer == null ? new LinkedHashMap<String, Object>() : answer, instance.getUserId());
                    return;
                }
                AgentChatDto request = new AgentChatDto();
                request.setAgentId(definition.getString("resourceId"));
                request.setUserId(instance.getUserId());
                request.setMessage(WorkflowVariableRenderer.render(definition.getString("prompt"), variables));
                request.setTemporary(true);
                AgentMessageVo response = chatService.chat(request);
                // 普通聊天服务遇到需要确认的 MCP 调用时会返回 interaction 消息。工作流必须
                // 将它转换为当前节点的暂停状态，不能把“请确认”误当作 Agent 的最终回答。
                if (isAgentInteraction(response)) {
                    waitForAgentInteraction(instance, node, definition, response);
                    return;
                }
                String output = response == null ? "" : response.getContent();
                applyStateMapping(definition, output, variables);
                instance.setVariables(JSON.toJSONString(variables));
                instanceService.updateById(instance);
                completeNode(node, output);
                return;
            }
            throw new ServerException(422, I18nUtils.getMessage("workflow.node.type.unknown", new Object[]{type}));
        } catch (Exception e) {
            fail(instance, node, e.getMessage());
        }
    }

    /**
     * 根据当前节点类型和输出，找到下一个要执行的节点 ID。
     * <ul>
     *   <li>节点完成后按出边 condition 表达式求值选择走向</li>
     *   <li>循环边：累加迭代计数器，超限则标记失败</li>
     * </ul>
     */
    private String findNextNodeIdFromGraph(Map<String, List<JSONObject>> adj, String currentNodeId,
                                           Map<String, JSONObject> nodeMap, Map<String, Object> variables,
                                           AgentWorkflowNodeInstance nodeInstance, AgentWorkflowInstance instance) {
        List<JSONObject> edges = adj.get(currentNodeId);
        if (edges == null || edges.isEmpty()) return null;

        // 根据图的 DFS 结构识别回跳边（指向当前 DFS 祖先的边才算循环边）
        Set<String> backEdges = buildBackEdgeIds(instance);

        String targetId;
        // 依次求值各出边的 condition 表达式。
        for (JSONObject edge : edges) {
            String condition = edge.getString("condition");
            if (StringUtils.isNotBlank(condition) && WorkflowConditionEvaluator.evaluate(condition, variables)) {
                targetId = edge.getString("target");
                return handleLoopEdge(edge, targetId, variables, instance, backEdges);
            }
        }
        // 默认分支：找 isDefault=true 的边，或最后一条边
        for (JSONObject edge : edges) {
            if (edge.getBooleanValue("isDefault")) {
                targetId = edge.getString("target");
                return handleLoopEdge(edge, targetId, variables, instance, backEdges);
            }
        }
        // 都没有默认边则走最后一条
        JSONObject lastEdge = edges.get(edges.size() - 1);
        targetId = lastEdge.getString("target");
        return handleLoopEdge(lastEdge, targetId, variables, instance, backEdges);
    }

    /**
     * 处理回跳边：累加迭代计数器，超限则标记失败并重置循环体内节点。
     *
     * @return 下一个节点 ID，如果循环超限返回 null
     */
    private String handleLoopEdge(JSONObject edge, String targetId, Map<String, Object> variables,
                                  AgentWorkflowInstance instance, Set<String> backEdges) {
        String edgeId = edge.getString("id");
        if (edgeId == null || !backEdges.contains(edgeId)) return targetId;

        String counterKey = "_loop_" + edgeId + "_count";
        int count = variables.containsKey(counterKey) ? ((Number) variables.get(counterKey)).intValue() : 0;
        count++;
        variables.put(counterKey, count);

        int maxIter = edge.getIntValue("maxIterations");
        if (maxIter <= 0) maxIter = DEFAULT_MAX_ITERATIONS;
        if (maxIter > MAX_ITERATIONS_CAP) maxIter = MAX_ITERATIONS_CAP;

        if (count > maxIter) {
            // 循环超限
            AgentWorkflowNodeInstance currentNode = currentNodeById(instance, edge.getString("source"));
            if (currentNode != null) {
                fail(instance, currentNode, "循环边 [" + edgeId + "] 已达最大迭代次数 " + maxIter);
            }
            return null;
        }

        // 重置循环体内节点状态为 PENDING，以便重新执行
        resetLoopBodyNodes(instance, targetId, edge.getString("source"));

        // 持久化迭代计数
        instance.setVariables(JSON.toJSONString(variables));
        instanceService.updateById(instance);

        return targetId;
    }

    /**
     * 重置循环体内节点为 PENDING，以便循环回跳后重新执行。
     */
    private void resetLoopBodyNodes(AgentWorkflowInstance instance, String loopEntryId, String loopBackSourceId) {
        // 收集从 loopEntryId 到 loopBackSourceId 之间的所有节点
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        Map<String, JSONObject> nodeMap = version == null ? Collections.emptyMap() : buildNodeMap(version.getNodes());
        Set<String> bodyNodes = collectLoopBody(loopEntryId, loopBackSourceId, nodeMap);
        for (String nid : bodyNodes) {
            AgentWorkflowNodeInstance n = nodeService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                    .eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId())
                    .eq(AgentWorkflowNodeInstance::getNodeId, nid));
            if (n != null && "COMPLETED".equals(n.getStatus())) {
                n.setStatus("PENDING");
                n.setOutputData(null);
                n.setErrorMessage(null);
                n.setStartedAt(null);
                n.setCompletedAt(null);
                nodeService.updateById(n);
            }
        }
    }

    /**
     * 基于 DFS 的边反向结构识别回跳边。
     * <p>回跳边的判定不再依赖节点数组顺序（数组顺序可能与执行顺序不一致，会把
     * agent→end 这类前向边误判为循环边），而是遍历图：一条边指向 DFS 栈上仍在
     * 访问中的祖先节点时才视为回跳边。</p>
     */
    private Set<String> buildBackEdgeIds(AgentWorkflowInstance instance) {
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        if (version == null) return Collections.emptySet();
        Map<String, List<Object[]>> graph = new LinkedHashMap<String, List<Object[]>>();
        for (Object value : com.alibaba.fastjson2.JSONArray.parseArray(version.getEdges())) {
            JSONObject edge = (JSONObject) value;
            String source = edge.getString("source");
            String target = edge.getString("target");
            String edgeId = edge.getString("id");
            if (source == null || target == null || edgeId == null) continue;
            graph.computeIfAbsent(source, k -> new ArrayList<Object[]>()).add(new Object[]{edgeId, target});
        }
        Set<String> back = new HashSet<String>();
        Set<String> visited = new HashSet<String>();
        Deque<String> stack = new ArrayDeque<String>();
        // 从 start 节点开始 DFS
        String start = findStartNode(buildNodeMap(version.getNodes()));
        if (start == null) return back;
        dfsBackEdges(start, graph, back, visited, stack);
        return back;
    }

    /**
     * DFS 探测回跳边：目标节点当前仍在 DFS 栈中（是源节点的祖先）即为回跳。
     */
    private void dfsBackEdges(String node, Map<String, List<Object[]>> graph, Set<String> back,
                              Set<String> visited, Deque<String> stack) {
        visited.add(node);
        stack.push(node);
        for (Object[] ref : graph.getOrDefault(node, Collections.emptyList())) {
            String edgeId = (String) ref[0];
            String target = (String) ref[1];
            if (stack.contains(target)) {
                back.add(edgeId);
            } else if (!visited.contains(target)) {
                dfsBackEdges(target, graph, back, visited, stack);
            }
        }
        stack.pop();
    }

    /**
     * 简化实现：收集拓扑序在 entry 和 backSource 之间的所有节点（含两端）。
     */
    private Set<String> collectLoopBody(String entryId, String backSourceId, Map<String, JSONObject> nodeMap) {
        Set<String> body = new HashSet<String>();
        int entryOrder = getTopologicalOrder(entryId, nodeMap);
        int backOrder = getTopologicalOrder(backSourceId, nodeMap);
        if (entryOrder < 0 || backOrder < 0) return body;
        int min = Math.min(entryOrder, backOrder);
        int max = Math.max(entryOrder, backOrder);
        int idx = 0;
        for (String nid : nodeMap.keySet()) {
            if (idx >= min && idx <= max) body.add(nid);
            idx++;
        }
        return body;
    }

    // ── 辅助方法 ─────────────────────────────────────────────

    /**
     * 获取Or创建NodeInstance。
     */
    private AgentWorkflowNodeInstance getOrCreateNodeInstance(AgentWorkflowInstance instance, String nodeId, JSONObject definition) {
        AgentWorkflowNodeInstance existing = nodeService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                .eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId()).eq(AgentWorkflowNodeInstance::getNodeId, nodeId));
        if (existing != null) return existing;
        AgentWorkflowNodeInstance newNode = new AgentWorkflowNodeInstance();
        newNode.setInstanceId(instance.getId());
        newNode.setNodeId(nodeId);
        newNode.setNodeType(definition.getString("type"));
        newNode.setStatus("PENDING");
        newNode.setRetryCount(0);
        nodeService.save(newNode);
        return newNode;
    }

    /**
     * 查找下一个NodeId。
     */
    private String findNextNodeId(AgentWorkflowInstance instance, AgentWorkflowNodeInstance completedNode) {
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        Map<String, List<JSONObject>> adj = WorkflowDefinitionValidator.buildAdjacency(version.getEdges());
        Map<String, JSONObject> nodeMap = buildNodeMap(version.getNodes());
        Map<String, Object> vars = variables(instance);
        return findNextNodeIdFromGraph(adj, completedNode.getNodeId(), nodeMap, vars, completedNode, instance);
    }

    /**
     * 获取TopologicalOrder。
     */
    private int getTopologicalOrder(String nodeId, Map<String, JSONObject> nodeMap) {
        int idx = 0;
        for (String nid : nodeMap.keySet()) {
            if (nid.equals(nodeId)) return idx;
            idx++;
        }
        return -1;
    }

    /**
     * 处理fail当前Node。
     */
    private void failCurrentNode(AgentWorkflowInstance instance, String nodeId, String error) {
        AgentWorkflowNodeInstance node = nodeService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                .eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId()).eq(AgentWorkflowNodeInstance::getNodeId, nodeId));
        if (node != null) fail(instance, node, error);
        else {
            instance.setStatus("FAILED");
            instance.setErrorMessage(error);
            instanceService.updateById(instance);
            sseHub.publish(instance.getId(), "run.failed", null);
            callbackService.recordTerminal(instance);
        }
    }

    /**
     * 当前Node按Id。
     */
    private AgentWorkflowNodeInstance currentNodeById(AgentWorkflowInstance instance, String nodeId) {
        return nodeService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                .eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId()).eq(AgentWorkflowNodeInstance::getNodeId, nodeId));
    }

    /**
     * 处理wait用于Human。
     */
    private void waitForHuman(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node, JSONObject definition, Map<String, Object> variables, boolean mcp) {
        JSONObject config = new JSONObject();
        config.put("type", mcp ? "mcp_tool_approval" : "group");
        config.put("question", definition.getString("question"));
        config.put("outputKey", definition.getString("outputKey"));
        config.put("internalKey", definition.getString("internalKey"));
        if (mcp) {
            config.put("toolId", definition.getString("resourceId"));
            config.put("toolName", definition.getString("toolName"));
            config.put("agentId", resolveMcpAgentId(instance));
            config.put("arguments", WorkflowVariableRenderer.render(definition.getString("argumentsTemplate"), variables));
        } else config.put("questions", definition.getJSONArray("questions"));
        // 重试失败节点后会重新进入人工交互。此时不能继续携带上一次失败的错误，
        // 否则实例虽已处于 WAITING_USER，详情页仍会显示“执行失败”。
        node.setStatus("WAITING_USER");
        node.setErrorMessage(null);
        node.setCompletedAt(null);
        node.setInteractionConfig(config.toJSONString());
        nodeService.updateById(node);
        instance.setStatus("WAITING_USER");
        instance.setErrorMessage(null);
        instanceService.updateById(instance);
        sseHub.publish(instance.getId(), mcp ? "tool.approval.required" : "ask_user.required", node);
    }

    /**
     * 审批节点复用服务账号作为审批主体，但交互类型和审计语义独立于普通人工录入。
     */
    private void waitForApproval(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node,
                                 JSONObject definition, Map<String, Object> variables) {
        JSONObject config = new JSONObject();
        config.put("type", "approval");
        config.put("question", definition.getString("question"));
        config.put("outputKey", definition.getString("outputKey"));
        config.put("internalKey", definition.getString("internalKey"));
        config.put("approvalMode", StringUtils.defaultIfBlank(definition.getString("approvalMode"), "ANY"));
        config.put("approverServiceAccountId", definition.getString("approverServiceAccountId"));
        node.setStatus("WAITING_USER");
        node.setErrorMessage(null);
        node.setCompletedAt(null);
        node.setInteractionConfig(config.toJSONString());
        nodeService.updateById(node);
        instance.setStatus("WAITING_USER");
        instance.setErrorMessage(null);
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), node.getId(), "APPROVAL_REQUIRED", null, "等待服务账号审批", config.toJSONString());
        sseHub.publish(instance.getId(), "approval.required", node);
    }

    /**
     * 将 Agent 聊天服务产生的 MCP 确认或 ask_user 交互挂接到当前工作流节点。
     */
    private void waitForAgentInteraction(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node,
                                         JSONObject definition, AgentMessageVo response) {
        JSONObject config = JSONObject.parseObject(response.getQuestionConfig());
        config.put("agentId", definition.getString("resourceId"));
        config.put("source", "agent");
        config.put("agentConversationId", response.getConversationId());
        config.put("agentApprovalMessageId", response.getId());
        if (isScheduledInstance(instance) && isMcpToolApprovalConfig(config)) {
            resumeAgentInteraction(instance, node, config, approvedAgentMcpAnswer(), instance.getUserId());
            return;
        }
        node.setStatus("WAITING_USER");
        node.setErrorMessage(null);
        node.setCompletedAt(null);
        node.setInteractionConfig(config.toJSONString());
        nodeService.updateById(node);
        instance.setStatus("WAITING_USER");
        instance.setErrorMessage(null);
        instanceService.updateById(instance);
        sseHub.publish(instance.getId(), isMcpToolApprovalConfig(config) ? "tool.approval.required" : "ask_user.required", node);
    }

    /**
     * 判断是否为智能体Interaction。
     */
    private boolean isAgentInteraction(AgentMessageVo response) {
        if (response == null || StringUtils.isBlank(response.getQuestionConfig())) return false;
        try {
            JSONObject config = JSONObject.parseObject(response.getQuestionConfig());
            return "interaction".equals(response.getMessageType()) && config != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 处理readPendingAnswer。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readPendingAnswer(JSONObject config) {
        Object pending = config.get("pendingAnswer");
        if (pending instanceof Map) return new LinkedHashMap<String, Object>((Map<String, Object>) pending);
        Map<String, Object> parsed = pending == null ? null : JSONObject.parseObject(JSON.toJSONString(pending), Map.class);
        return parsed == null ? new LinkedHashMap<String, Object>() : parsed;
    }

    /**
     * 处理resumeMcpApproval。
     */
    private void resumeMcpApproval(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node,
                                   JSONObject config, Map<String, Object> answer, String userId) {
        String decision = String.valueOf(answer.get("decision"));
        if ("reject".equals(decision)) {
            fail(instance, node, "用户拒绝执行 MCP 工具");
            return;
        }
        Map<String, Object> args = readToolArguments(config);
        String agentId = config.getString("agentId");
        if (StringUtils.isBlank(agentId)) {
            JSONObject definition = currentDefinition(instance, node);
            agentId = definition == null ? null : definition.getString("resourceId");
        }
        if (StringUtils.isBlank(agentId)) agentId = resolveMcpAgentId(instance);
        String idempotencyKey = "workflow:" + instance.getId() + ":node:" + node.getNodeId();
        AgentWorkflowExternalInvocation invocation = externalInvocationService.recordIntent(instance.getApplicationId(), instance.getId(),
                node.getId(), node.getNodeId(), "TOOL", idempotencyKey, "CALL", config.getString("toolName"), JSON.toJSONString(args));
        if ("COMPLETED".equals(invocation.getStatus()))
            throw new ServerException(409, "工具调用已完成但流程状态未确认，请由管理员核对调用记录后恢复");
        if ("UNKNOWN".equals(invocation.getStatus()))
            throw new ServerException(409, "工具调用结果未知，请确认后手动重试");
        externalInvocationService.markRunning(invocation.getId());
        ToolExecutionResult result;
        try {
            result = toolWorkflow.executeWorkflowApprovedMcpTool(config.getString("toolId"), config.getString("toolName"), args,
                    instance.getId(), userId, agentId, "allow_10m".equals(decision), idempotencyKey);
            if (result.getStatus() != null && result.getStatus() != 0) {
                externalInvocationService.markUnknown(invocation.getId(), result.getErrorMsg());
                fail(instance, node, result.getErrorMsg());
                return;
            }
            externalInvocationService.complete(invocation.getId(), JSON.toJSONString(result));
        } catch (Exception ex) {
            externalInvocationService.markUnknown(invocation.getId(), ex.getMessage());
            throw ex;
        }
        Map<String, Object> variables = variables(instance);
        JSONObject definition = currentDefinition(instance, node);
        if (definition != null) applyStateMapping(definition, result, variables);
        instance.setVariables(JSON.toJSONString(variables));
        instanceService.updateById(instance);
        completeNode(node, JSON.toJSONString(result));
    }

    /**
     * 通过普通 Agent 的交互恢复链路继续执行。该链路会更新原审批审计、把工具结果回填给模型，
     * 并允许模型基于结果产出最终回答或发起下一次交互。
     */
    private void resumeAgentInteraction(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node,
                                        JSONObject config, Map<String, Object> answer, String userId) {
        String conversationId = config.getString("agentConversationId");
        String parentMessageId = config.getString("agentApprovalMessageId");
        if (StringUtils.isBlank(conversationId) || StringUtils.isBlank(parentMessageId))
            throw new ServerException(409, I18nUtils.getMessage("workflow.agent-interaction.context.expired"));
        AgentChatDto request = new AgentChatDto();
        request.setConversationId(conversationId);
        request.setParentMessageId(parentMessageId);
        request.setAnswer(answer);
        request.setUserId(userId);
        request.setTemporary(true);
        final AgentMessageVo[] nextQuestion = new AgentMessageVo[1];
        final String[] output = new String[]{""};
        final String[] error = new String[1];
        chatService.stream(request, new AgentStreamCallback() {
            /**
             * 处理on消息。
             */
            @Override
            public void onMessage(String ignored, String chunk) {
            }

            /**
             * 处理onReasoning。
             */
            @Override
            public void onReasoning(String ignored, String chunk) {
            }

            /**
             * 处理onToolCall。
             */
            @Override
            public void onToolCall(String ignored, String toolCallJson) {
            }

            /**
             * 处理onQuestion。
             */
            @Override
            public void onQuestion(String ignored, String runId, AgentMessageVo question) {
                nextQuestion[0] = question;
            }

            /**
             * 处理onDone。
             */
            @Override
            public void onDone(String ignored, String messageId, ModelStreamResponse response) {
                output[0] = response == null ? "" : response.getContent();
            }

            /**
             * 处理onError。
             */
            @Override
            public void onError(int code, String message) {
                error[0] = message;
            }

            /**
             * 判断是否为Closed。
             */
            @Override
            public boolean isClosed() {
                return false;
            }
        });
        if (StringUtils.isNotBlank(error[0])) throw new ServerException(502, error[0]);
        JSONObject definition = currentDefinition(instance, node);
        if (nextQuestion[0] != null) {
            waitForAgentInteraction(instance, node, definition, nextQuestion[0]);
            return;
        }
        Map<String, Object> variables = variables(instance);
        applyStateMapping(definition, output[0], variables);
        instance.setVariables(JSON.toJSONString(variables));
        instanceService.updateById(instance);
        completeNode(node, output[0]);
        String nextNodeId = findNextNodeId(instance, node);
        if (nextNodeId != null) instance.setCurrentNodeId(nextNodeId);
        instance.setStatus("RUNNING");
        instanceService.updateById(instance);
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
    }

    /**
     * 判断是否为McpToolApproval配置。
     */
    private boolean isMcpToolApprovalConfig(JSONObject config) {
        return config != null && "mcp_tool_approval".equals(config.getString("approvalType"));
    }

    private boolean isToolNode(String type) {
        return "tool".equals(type);
    }

    /**
     * 将确定性字段映射写入共享变量。mapping 支持 source、template 和 value 三种来源。
     */
    private Map<String, Object> applyTransform(JSONObject definition, Map<String, Object> variables) {
        Map<String, Object> output = new LinkedHashMap<String, Object>();
        JSONArray mappings = definition.getJSONArray("mappings");
        if (mappings == null) return output;
        for (Object value : mappings) {
            JSONObject mapping = (JSONObject) value;
            String target = mapping.getString("target");
            Object mapped;
            if (mapping.containsKey("source")) mapped = resolveVariablePath(variables, mapping.getString("source"));
            else if (mapping.containsKey("template")) mapped = WorkflowVariableRenderer.render(mapping.getString("template"), variables);
            else mapped = mapping.get("value");
            variables.put(target, mapped);
            output.put(target, mapped);
        }
        return output;
    }

    /**
     * 按顺序执行规则；第一个命中规则的 value 为结果，未命中时使用 defaultValue。
     */
    private Object evaluateRule(JSONObject definition, Map<String, Object> variables) {
        JSONArray rules = definition.getJSONArray("rules");
        if (rules != null) for (Object value : rules) {
            JSONObject rule = (JSONObject) value;
            if (WorkflowConditionEvaluator.evaluate(rule.getString("condition"), variables)) return rule.get("value");
        }
        return definition.get("defaultValue");
    }

    private void waitForEvent(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node,
                              JSONObject definition, Map<String, Object> variables) {
        JSONObject config = new JSONObject();
        config.put("eventType", definition.getString("eventType"));
        config.put("correlationKey", WorkflowVariableRenderer.render(definition.getString("correlationKeyTemplate"), variables));
        long timeoutMillis = definition.getLongValue("timeoutMillis");
        if (timeoutMillis > 0) config.put("timeoutAt", System.currentTimeMillis() + timeoutMillis);
        config.put("timeoutTargetId", definition.getString("timeoutTargetId"));
        node.setStatus("WAITING_EVENT");
        node.setInteractionConfig(config.toJSONString());
        nodeService.updateById(node);
        instance.setStatus("WAITING_EVENT");
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), node.getId(), "BUSINESS_EVENT_WAITING", null,
                "等待业务事件：" + definition.getString("eventType"), config.toJSONString());
        sseHub.publish(instance.getId(), "business_event.required", node);
    }

    private void waitForDelay(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node, JSONObject definition) {
        long resumeAt = System.currentTimeMillis() + definition.getLongValue("delayMillis");
        JSONObject config = new JSONObject();
        config.put("resumeAt", resumeAt);
        node.setStatus("WAITING_DELAY");
        node.setInteractionConfig(config.toJSONString());
        nodeService.updateById(node);
        instance.setStatus("WAITING_DELAY");
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), node.getId(), "DELAY_WAITING", null, "延时节点等待中", config.toJSONString());
        sseHub.publish(instance.getId(), "delay.waiting", node);
    }

    /**
     * 执行受控 HTTP 节点。调用意图在独立事务中落库，异常后保留 UNKNOWN 状态而不自动重放。
     */
    private String executeHttpNode(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node,
                                   JSONObject definition, Map<String, Object> variables) {
        String method = StringUtils.defaultIfBlank(definition.getString("method"), "POST").toUpperCase(Locale.ROOT);
        String url = WorkflowVariableRenderer.render(definition.getString("url"), variables);
        String body = definition.containsKey("bodyTemplate")
                ? WorkflowVariableRenderer.render(definition.getString("bodyTemplate"), variables)
                : (definition.containsKey("body") ? JSON.toJSONString(definition.get("body")) : null);
        HttpHeaders headers = new HttpHeaders();
        JSONObject configuredHeaders = definition.getJSONObject("headers");
        if (configuredHeaders != null) for (Map.Entry<String, Object> header : configuredHeaders.entrySet())
            headers.add(header.getKey(), WorkflowVariableRenderer.render(String.valueOf(header.getValue()), variables));
        String idempotencyKey = definition.containsKey("idempotencyKeyTemplate")
                ? WorkflowVariableRenderer.render(definition.getString("idempotencyKeyTemplate"), variables)
                : "workflow:" + instance.getId() + ":node:" + node.getNodeId();
        headers.set("X-Idempotency-Key", idempotencyKey);
        JSONObject requestAudit = new JSONObject();
        requestAudit.put("headers", headers.toSingleValueMap());
        requestAudit.put("body", body);
        AgentWorkflowExternalInvocation invocation = externalInvocationService.recordIntent(instance.getApplicationId(), instance.getId(),
                node.getId(), node.getNodeId(), "HTTP", idempotencyKey, method, url, requestAudit.toJSONString());
        if ("COMPLETED".equals(invocation.getStatus()))
            throw new ServerException(409, "HTTP 节点外部调用已完成但流程状态未确认，请由管理员核对调用记录后恢复");
        if ("UNKNOWN".equals(invocation.getStatus()))
            throw new ServerException(409, "HTTP 节点上次调用结果未知，请确认后手动重试");
        externalInvocationService.markRunning(invocation.getId());
        try {
            String response = httpNodeExecutor.execute(method, url, headers, body);
            externalInvocationService.complete(invocation.getId(), response);
            return response;
        } catch (Exception ex) {
            externalInvocationService.markUnknown(invocation.getId(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * 发送确定性通知。首版复用平台邮件通道；其他渠道须由工具或 HTTP 节点显式接入。
     */
    private String executeNotificationNode(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node,
                                           JSONObject definition, Map<String, Object> variables) {
        String channel = StringUtils.defaultIfBlank(definition.getString("channel"), "email");
        if (!"email".equalsIgnoreCase(channel)) throw new ServerException(422, "通知节点不支持渠道：" + channel);
        String recipient = WorkflowVariableRenderer.render(definition.getString("toTemplate"), variables);
        String subject = WorkflowVariableRenderer.render(definition.getString("subjectTemplate"), variables);
        String body = WorkflowVariableRenderer.render(definition.getString("bodyTemplate"), variables);
        if (StringUtils.isBlank(recipient)) throw new ServerException(422, "通知节点收件人不能为空");
        String idempotencyKey = definition.containsKey("idempotencyKeyTemplate")
                ? WorkflowVariableRenderer.render(definition.getString("idempotencyKeyTemplate"), variables)
                : "workflow:" + instance.getId() + ":node:" + node.getNodeId();
        JSONObject requestAudit = new JSONObject();
        requestAudit.put("channel", "email");
        requestAudit.put("recipient", recipient);
        requestAudit.put("subject", subject);
        requestAudit.put("body", body);
        AgentWorkflowExternalInvocation invocation = externalInvocationService.recordIntent(instance.getApplicationId(), instance.getId(),
                node.getId(), node.getNodeId(), "NOTIFICATION", idempotencyKey, "SEND", "email:" + recipient, requestAudit.toJSONString());
        if ("COMPLETED".equals(invocation.getStatus()))
            throw new ServerException(409, "通知已发送但流程状态未确认，请由管理员核对调用记录后恢复");
        if ("UNKNOWN".equals(invocation.getStatus()))
            throw new ServerException(409, "通知上次发送结果未知，请确认后手动重试");
        externalInvocationService.markRunning(invocation.getId());
        try {
            Email email = new Email();
            email.setUserId(instance.getUserId());
            email.setEmail(recipient);
            email.setType("workflow_notification");
            email.setSubject(subject);
            email.setBody(body);
            if (!Boolean.TRUE.equals(emailMessageService.send(email))) throw new ServerException(502, "邮件通知发送失败");
            JSONObject result = new JSONObject();
            result.put("channel", "email");
            result.put("recipient", recipient);
            result.put("status", "SENT");
            externalInvocationService.complete(invocation.getId(), result.toJSONString());
            return result.toJSONString();
        } catch (Exception ex) {
            externalInvocationService.markUnknown(invocation.getId(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * 使用已发布的固定版本启动子流程。父节点进入 WAITING_SUBFLOW，子流程终态由 resumeParentSubflow 回传。
     */
    private void executeParallel(AgentWorkflowInstance instance, AgentWorkflowNodeInstance parallelNode,
                                 JSONObject definition, Map<String, Object> variables) {
        JSONArray branches = definition.getJSONArray("branches");
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        Map<String, JSONObject> nodeMap = buildNodeMap(version.getNodes());
        Map<String, List<JSONObject>> adjacency = WorkflowDefinitionValidator.buildAdjacency(version.getEdges());
        String joinNodeId = findParallelJoinNodeId(definition, nodeMap, adjacency);
        if (StringUtils.isBlank(joinNodeId) || nodeMap.get(joinNodeId) == null || !"join".equals(nodeMap.get(joinNodeId).getString("type")))
            throw new ServerException(422, "并行节点必须连接汇聚节点");
        int maxBranches = definition.getIntValue("maxBranches");
        if (maxBranches > 0 && branches.size() > maxBranches)
            throw new ServerException(429, "并行分支数量超过节点配额");
        long branchTimeoutMillis = definition.getLongValue("branchTimeoutMillis");
        long branchStartedAt = System.currentTimeMillis();
        String tokenKey = parallelNode.getNodeId();
        AgentWorkflowJoinState state = joinStateService.getOne(Wrappers.lambdaQuery(AgentWorkflowJoinState.class)
                .eq(AgentWorkflowJoinState::getInstanceId, instance.getId()).eq(AgentWorkflowJoinState::getJoinNodeId, joinNodeId)
                .eq(AgentWorkflowJoinState::getTokenKey, tokenKey).eq(AgentWorkflowJoinState::getDeleted, false).last("FOR UPDATE"));
        if (state == null) {
            state = new AgentWorkflowJoinState();
            state.setInstanceId(instance.getId()); state.setJoinNodeId(joinNodeId); state.setTokenKey(tokenKey);
            state.setJoinMode(StringUtils.defaultIfBlank(definition.getString("joinMode"), "ALL_SUCCESS"));
            state.setExpectedCount(branches.size()); state.setCompletedCount(0); state.setFailedCount(0); state.setStatus("WAITING");
            joinStateService.save(state);
        }
        if ("READY".equals(state.getStatus()) || "COMPLETED".equals(state.getStatus())) return;
        int completed = 0, failed = 0;
        for (int i = 0; i < branches.size(); i++) {
            if (branchTimeoutMillis > 0 && System.currentTimeMillis() - branchStartedAt > branchTimeoutMillis)
                throw new ServerException(504, "并行分支执行超时");
            String entryId = String.valueOf(branches.get(i));
            AgentWorkflowNodeToken token = nodeTokenService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeToken.class)
                    .eq(AgentWorkflowNodeToken::getInstanceId, instance.getId()).eq(AgentWorkflowNodeToken::getNodeId, entryId)
                    .eq(AgentWorkflowNodeToken::getTokenKey, tokenKey + ":" + i).eq(AgentWorkflowNodeToken::getDeleted, false));
            if (token == null) {
                token = new AgentWorkflowNodeToken(); token.setInstanceId(instance.getId()); token.setNodeId(entryId);
                token.setTokenKey(tokenKey + ":" + i); token.setStatus("RUNNING"); nodeTokenService.save(token);
            }
            if ("COMPLETED".equals(token.getStatus())) { completed++; continue; }
            try {
                if (!"ALL_SUCCESS".equals(state.getJoinMode())) partialParallelBranch.set(Boolean.TRUE);
                try {
                    runParallelBranch(instance, entryId, joinNodeId, adjacency, nodeMap, variables, branchTimeoutMillis, branchStartedAt);
                } finally {
                    partialParallelBranch.remove();
                }
                token.setStatus("COMPLETED"); token.setErrorMessage(null); nodeTokenService.updateById(token); completed++;
            } catch (Exception ex) {
                token.setStatus("FAILED"); token.setErrorMessage(StringUtils.abbreviate(ex.getMessage(), 2048)); nodeTokenService.updateById(token); failed++;
                String mode = state.getJoinMode();
                instance.setStatus("RUNNING"); instance.setErrorMessage(null); instanceService.updateById(instance);
                if ("ALL_SUCCESS".equals(mode)) {
                    if (ex instanceof RuntimeException) throw (RuntimeException) ex;
                    throw new ServerException(502, ex.getMessage());
                }
            }
        }
        state.setCompletedCount(completed); state.setFailedCount(failed);
        if ("ANY_SUCCESS".equals(state.getJoinMode()) && completed == 0) {
            state.setStatus("FAILED"); state.setErrorMessage("并行分支没有成功分支"); joinStateService.updateById(state);
            throw new ServerException(502, state.getErrorMessage());
        }
        if (completed + failed < branches.size()) { state.setStatus("WAITING"); joinStateService.updateById(state); return; }
        state.setStatus("READY"); joinStateService.updateById(state);
        completeNode(parallelNode, JSON.toJSONString(Collections.singletonMap("branches", branches.size())));
    }

    private void runParallelBranch(AgentWorkflowInstance instance, String entryId, String joinNodeId,
                                   Map<String, List<JSONObject>> adjacency, Map<String, JSONObject> nodeMap,
                                   Map<String, Object> variables, long timeoutMillis, long startedAt) {
        String nodeId = entryId;
        Set<String> visited = new HashSet<String>();
        for (int guard = 0; guard < 100 && nodeId != null; guard++) {
            if (timeoutMillis > 0 && System.currentTimeMillis() - startedAt > timeoutMillis)
                throw new ServerException(504, "并行分支执行超时");
            if (StringUtils.equals(nodeId, joinNodeId)) return;
            if (!visited.add(nodeId)) throw new ServerException(422, "并行分支存在循环：" + nodeId);
            JSONObject definition = nodeMap.get(nodeId);
            if (definition == null) throw new ServerException(422, "并行分支节点不存在：" + nodeId);
            AgentWorkflowNodeInstance history = getOrCreateNodeInstance(instance, nodeId, definition);
            if (!"COMPLETED".equals(history.getStatus())) {
                instance.setCurrentNodeId(nodeId); instanceService.updateById(instance);
                executeNode(instance, history, definition, variables);
                if ("FAILED".equals(history.getStatus())) throw new ServerException(502, "并行分支节点执行失败：" + nodeId);
                if (!"RUNNING".equals(instance.getStatus())) throw new ServerException(502, "并行分支节点未完成：" + nodeId);
            }
            nodeId = findNextNodeIdFromGraph(adjacency, nodeId, nodeMap, variables, history, instance);
        }
        throw new ServerException(422, "并行分支未在限定步数内到达汇聚节点");
    }

    private String findParallelJoinNodeId(JSONObject definition, Map<String, JSONObject> nodeMap,
                                          Map<String, List<JSONObject>> adjacency) {
        String configured = definition == null ? null : definition.getString("joinNodeId");
        if (StringUtils.isNotBlank(configured) && nodeMap.get(configured) != null && "join".equals(nodeMap.get(configured).getString("type"))) return configured;
        JSONArray branches = definition == null ? null : definition.getJSONArray("branches");
        if (branches == null || branches.isEmpty()) return null;
        for (JSONObject candidate : nodeMap.values()) {
            if (!"join".equals(candidate.getString("type"))) continue;
            boolean reachableFromEveryBranch = true;
            for (Object branch : branches) {
                if (!canReach(adjacency, String.valueOf(branch), candidate.getString("id"))) { reachableFromEveryBranch = false; break; }
            }
            if (reachableFromEveryBranch) return candidate.getString("id");
        }
        return null;
    }

    private boolean canReach(Map<String, List<JSONObject>> adjacency, String source, String target) {
        Queue<String> queue = new LinkedList<String>();
        Set<String> visited = new HashSet<String>();
        queue.add(source);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) continue;
            if (StringUtils.equals(current, target)) return true;
            for (JSONObject edge : adjacency.getOrDefault(current, Collections.<JSONObject>emptyList())) queue.add(edge.getString("target"));
        }
        return false;
    }

    private void executeJoin(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node, JSONObject definition) {
        AgentWorkflowJoinState state = joinStateService.getOne(Wrappers.lambdaQuery(AgentWorkflowJoinState.class)
                .eq(AgentWorkflowJoinState::getInstanceId, instance.getId()).eq(AgentWorkflowJoinState::getJoinNodeId, node.getNodeId())
                .eq(AgentWorkflowJoinState::getDeleted, false).orderByDesc(AgentWorkflowJoinState::getCreatedAt).last("LIMIT 1"));
        if (state == null || !"READY".equals(state.getStatus())) throw new ServerException(409, "汇聚节点尚未满足执行条件");
        completeNode(node, JSON.toJSONString(state));
        state.setStatus("COMPLETED"); joinStateService.updateById(state);
    }

    private void startSubflow(AgentWorkflowInstance parent, AgentWorkflowNodeInstance node,
                              JSONObject definition, Map<String, Object> variables) {
        AgentWorkflowSubflowLink existing = subflowLinkService.getOne(Wrappers.lambdaQuery(AgentWorkflowSubflowLink.class)
                .eq(AgentWorkflowSubflowLink::getParentInstanceId, parent.getId())
                .eq(AgentWorkflowSubflowLink::getParentNodeId, node.getNodeId())
                .eq(AgentWorkflowSubflowLink::getDeleted, false));
        if (existing != null) {
            parent.setStatus("WAITING_SUBFLOW");
            instanceService.updateById(parent);
            return;
        }
        String childWorkflowId = definition.getString("workflowId");
        int versionNo = definition.getIntValue("versionNo");
        if (StringUtils.equals(childWorkflowId, parent.getWorkflowId())) throw new ServerException(422, "子流程不能引用自身");
        String idempotencyKey = "workflow:" + parent.getId() + ":node:" + node.getNodeId();
        AgentWorkflowExternalInvocation invocation = externalInvocationService.recordIntent(parent.getApplicationId(), parent.getId(),
                node.getId(), node.getNodeId(), "SUBFLOW", idempotencyKey, "START", childWorkflowId + ":" + versionNo, null);
        if ("COMPLETED".equals(invocation.getStatus()))
            throw new ServerException(409, "子流程已启动但父流程状态未确认，请由管理员核对调用记录后恢复");
        if ("UNKNOWN".equals(invocation.getStatus()))
            throw new ServerException(409, "子流程启动结果未知，请确认后手动重试");
        externalInvocationService.markRunning(invocation.getId());
        Long childDeadline = parent.getDeadlineAt();
        long timeoutMillis = definition.getLongValue("timeoutMillis");
        if (timeoutMillis > 0) {
            long configuredDeadline = System.currentTimeMillis() + timeoutMillis;
            childDeadline = childDeadline == null ? configuredDeadline : Math.min(childDeadline, configuredDeadline);
        }
        AgentWorkflowInstance child;
        try {
            child = startInternal(childWorkflowId,
                    mapSubflowVariables(definition.getJSONArray("inputMappings"), variables), parent.getUserId(), null, versionNo, childDeadline);
            externalInvocationService.complete(invocation.getId(), "{\"childInstanceId\":\"" + child.getId() + "\"}");
        } catch (Exception ex) {
            externalInvocationService.markUnknown(invocation.getId(), ex.getMessage());
            throw ex;
        }
        AgentWorkflowSubflowLink link = new AgentWorkflowSubflowLink();
        link.setParentInstanceId(parent.getId());
        link.setParentNodeId(node.getNodeId());
        link.setChildInstanceId(child.getId());
        link.setChildWorkflowId(child.getWorkflowId());
        link.setChildWorkflowVersionId(child.getWorkflowVersionId());
        link.setStatus("RUNNING");
        subflowLinkService.save(link);
        JSONObject config = new JSONObject();
        config.put("childInstanceId", child.getId());
        config.put("childWorkflowVersionId", child.getWorkflowVersionId());
        node.setStatus("WAITING_SUBFLOW");
        node.setInteractionConfig(config.toJSONString());
        nodeService.updateById(node);
        parent.setStatus("WAITING_SUBFLOW");
        parent.setErrorMessage(null);
        instanceService.updateById(parent);
        auditEventService.record(parent.getId(), node.getId(), "SUBFLOW_STARTED", parent.getUserId(), "子流程已按固定版本启动", config.toJSONString());
        sseHub.publish(parent.getId(), "subflow.waiting", node);
    }

    /** 子流程进入终态后恢复父节点；失败、终止或超时会让父节点失败，避免静默卡住。 */
    private void resumeParentSubflow(AgentWorkflowInstance child) {
        AgentWorkflowSubflowLink link = subflowLinkService.getOne(Wrappers.lambdaQuery(AgentWorkflowSubflowLink.class)
                .eq(AgentWorkflowSubflowLink::getChildInstanceId, child.getId())
                .eq(AgentWorkflowSubflowLink::getStatus, "RUNNING")
                .eq(AgentWorkflowSubflowLink::getDeleted, false).last("FOR UPDATE"));
        if (link == null) return;
        AgentWorkflowInstance parent = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getId, link.getParentInstanceId()).eq(AgentWorkflowInstance::getDeleted, false).last("FOR UPDATE"));
        if (parent == null || !"WAITING_SUBFLOW".equals(parent.getStatus())) return;
        AgentWorkflowNodeInstance node = currentNode(parent);
        if (!StringUtils.equals(node.getNodeId(), link.getParentNodeId())) return;
        if (!"COMPLETED".equals(child.getStatus())) {
            link.setStatus("FAILED");
            subflowLinkService.updateById(link);
            fail(parent, node, "子流程未成功完成：" + child.getStatus());
            return;
        }
        JSONObject definition = currentDefinition(parent, node);
        Map<String, Object> childOutput = outputResolver.resolve(child);
        Map<String, Object> variables = variables(parent);
        variables.putAll(mapSubflowVariables(definition == null ? null : definition.getJSONArray("outputMappings"), childOutput));
        applyStateMapping(definition, childOutput, variables);
        parent.setVariables(JSON.toJSONString(variables));
        completeNode(node, JSON.toJSONString(childOutput));
        String nextNodeId = findNextNodeId(parent, node);
        if (nextNodeId != null) parent.setCurrentNodeId(nextNodeId);
        parent.setStatus("RUNNING");
        parent.setErrorMessage(null);
        instanceService.updateById(parent);
        link.setStatus("COMPLETED");
        subflowLinkService.updateById(link);
        auditEventService.record(parent.getId(), node.getId(), "SUBFLOW_COMPLETED", child.getUserId(), "子流程已完成并回填输出", JSON.toJSONString(childOutput));
        executionJobDispatcher.enqueueAfterCommit(parent.getId());
    }

    private Map<String, Object> mapSubflowVariables(JSONArray mappings, Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (mappings == null) return result;
        for (Object value : mappings) {
            JSONObject mapping = (JSONObject) value;
            String target = mapping.getString("target");
            if (StringUtils.isBlank(target)) continue;
            Object mapped = mapping.containsKey("source") ? resolveVariablePath(source, mapping.getString("source"))
                    : mapping.containsKey("template") ? WorkflowVariableRenderer.render(mapping.getString("template"), source)
                    : mapping.get("value");
            result.put(target, mapped);
        }
        return result;
    }

    /**
     * 定时触发器创建的实例使用受控的 schedule: 前缀幂等键，MCP 调用无需人工在线确认。
     */
    private boolean isScheduledInstance(AgentWorkflowInstance instance) {
        return instance != null && StringUtils.startsWith(instance.getIdempotencyKey(), "schedule:");
    }

    /**
     * 处理mcpInteraction配置。
     */
    private JSONObject mcpInteractionConfig(AgentWorkflowInstance instance, JSONObject definition, Map<String, Object> variables) {
        JSONObject config = new JSONObject();
        config.put("toolId", definition.getString("resourceId"));
        config.put("toolName", definition.getString("toolName"));
        config.put("agentId", resolveMcpAgentId(instance));
        config.put("arguments", WorkflowVariableRenderer.render(definition.getString("argumentsTemplate"), variables));
        return config;
    }

    /**
     * 处理approvedMcpNodeAnswer。
     */
    private Map<String, Object> approvedMcpNodeAnswer() {
        Map<String, Object> answer = new LinkedHashMap<String, Object>();
        answer.put("decision", "once");
        return answer;
    }

    /**
     * 处理approved智能体McpAnswer。
     */
    private Map<String, Object> approvedAgentMcpAnswer() {
        Map<String, Object> decision = new LinkedHashMap<String, Object>();
        decision.put("selected", "once");
        Map<String, Object> answers = new LinkedHashMap<String, Object>();
        answers.put("decision", decision);
        Map<String, Object> answer = new LinkedHashMap<String, Object>();
        answer.put("answers", answers);
        return answer;
    }

    /**
     * 处理readToolArguments。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readToolArguments(JSONObject config) {
        Object value = config.get("arguments");
        if (value instanceof Map) return new LinkedHashMap<String, Object>((Map<String, Object>) value);
        if (value == null) return new LinkedHashMap<String, Object>();
        Map<String, Object> parsed = JSONObject.parseObject(String.valueOf(value), Map.class);
        return parsed == null ? new LinkedHashMap<String, Object>() : parsed;
    }

    /**
     * 处理completeNode。
     */
    private void completeNode(AgentWorkflowNodeInstance node, String output) {
        node.setStatus("COMPLETED");
        node.setOutputData(sensitiveDataSanitizer.sanitizeJson(output));
        node.setCompletedAt(System.currentTimeMillis());
        node.setInteractionConfig(null);
        nodeService.updateById(node);
        auditEventService.record(node.getInstanceId(), node.getId(), "NODE_COMPLETED", null, "节点执行完成", node.getOutputData());
        AgentWorkflowInstance snapshotInstance = instanceService.getById(node.getInstanceId());
        if (snapshotInstance != null)
            variableSnapshotService.capture(snapshotInstance.getId(), node.getId(), node.getNodeId(), snapshotInstance.getVariables());
        sseHub.publish(node.getInstanceId(), "node.completed", node);
    }

    /**
     * 处理fail。
     */
    private void fail(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node, String error) {
        node.setStatus("FAILED");
        node.setErrorMessage(StringUtils.defaultIfBlank(error, "节点执行失败"));
        node.setCompletedAt(System.currentTimeMillis());
        nodeService.updateById(node);
        if (Boolean.TRUE.equals(partialParallelBranch.get())) {
            auditEventService.record(instance.getId(), node.getId(), "NODE_FAILED", null, node.getErrorMessage(), null);
            sseHub.publish(instance.getId(), "node.failed", node);
            return;
        }
        instance.setStatus("FAILED");
        instance.setErrorMessage(node.getErrorMessage());
        instanceService.updateById(instance);
        auditEventService.record(instance.getId(), node.getId(), "NODE_FAILED", null, node.getErrorMessage(), null);
        sseHub.publish(instance.getId(), "run.failed", node);
        callbackService.recordTerminal(instance);
        resumeParentSubflow(instance);
    }

    /**
     * 处理required工作流。
     */
    private AgentWorkflow requiredWorkflow(String id) {
        AgentWorkflow value = workflowService.getById(id);
        if (value == null || Boolean.TRUE.equals(value.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("workflow.not-found"));
        return value;
    }

    private AgentWorkflowExternalInvocation requiredUnknownExternalInvocation(AgentWorkflowInstance instance, String invocationId) {
        AgentWorkflowExternalInvocation invocation = externalInvocationService.getById(invocationId);
        if (invocation == null || Boolean.TRUE.equals(invocation.getDeleted()) || !StringUtils.equals(instance.getId(), invocation.getInstanceId()))
            throw new ServerException(404, "外部调用记录不存在");
        if (!"UNKNOWN".equals(invocation.getStatus()))
            throw new ServerException(409, "仅结果未知的外部调用支持此操作");
        return invocation;
    }

    /**
     * 处理owned。
     */
    private AgentWorkflowInstance owned(String id, String userId) {
        AgentWorkflowInstance value = instanceService.getById(id);
        if (value == null || Boolean.TRUE.equals(value.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("workflow.instance.not-found"));
        if (!StringUtils.equals(value.getUserId(), userId) && !isAdministrator(userId))
            throw new ServerException(403, I18nUtils.getMessage("workflow.instance.operation.denied"));
        return value;
    }

    /**
     * 人工回答、终止等状态变化持有实例行锁，避免与 SLA 超时任务相互覆盖状态。
     */
    private AgentWorkflowInstance ownedForUpdate(String id, String userId) {
        AgentWorkflowInstance value = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getId, id).eq(AgentWorkflowInstance::getDeleted, false).last("FOR UPDATE"));
        if (value == null) throw new ServerException(404, I18nUtils.getMessage("workflow.instance.not-found"));
        if (!StringUtils.equals(value.getUserId(), userId) && !isAdministrator(userId))
            throw new ServerException(403, I18nUtils.getMessage("workflow.instance.operation.denied"));
        return value;
    }

    /**
     * root 角色可处理业务服务账号创建的异常实例，普通用户仍严格隔离。
     */
    @Override
    public boolean isAdministrator(String userId) {
        if (StringUtils.isBlank(userId)) return false;
        List<UserRole> bindings = userRoleService.list(Wrappers.lambdaQuery(UserRole.class)
                .eq(UserRole::getUserId, userId).eq(UserRole::getDeleted, false));
        if (bindings == null || bindings.isEmpty()) return false;
        Set<String> roleIds = new HashSet<String>();
        for (UserRole binding : bindings)
            if (StringUtils.isNotBlank(binding.getRoleId())) roleIds.add(binding.getRoleId());
        if (roleIds.isEmpty()) return false;
        return roleService.count(Wrappers.lambdaQuery(Role.class).in(Role::getId, roleIds)
                .eq(Role::getName, "root").eq(Role::getDeleted, false)) > 0;
    }

    /**
     * 当前Node。
     */
    private AgentWorkflowNodeInstance currentNode(AgentWorkflowInstance instance) {
        AgentWorkflowNodeInstance value = nodeService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class).eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId()).eq(AgentWorkflowNodeInstance::getNodeId, instance.getCurrentNodeId()));
        if (value == null)
            throw new ServerException(409, I18nUtils.getMessage("workflow.instance.current-node.not-found"));
        return value;
    }

    /**
     * 处理variables。
     */
    private Map<String, Object> variables(AgentWorkflowInstance instance) {
        Map<String, Object> value = StringUtils.isBlank(instance.getVariables()) ? null : JSONObject.parseObject(instance.getVariables(), Map.class);
        return value == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(value);
    }

    // ── 共享状态写回 ─────────────────────────────────────────

    /**
     * 将节点输出写入共享状态。
     * <p>优先使用节点 stateMapping（JSON 对象：目标键 → $output | $json.<path>）；
     * 未配置时兼容旧字段 outputKey（将整个输出写入该键）。
     * 另支持 internalKey（内部变量，需带 _ 前缀）：始终写入但不进共享状态面板，
     * 可被后续节点以 ${_变量名} 引用。</p>
     */
    private void applyStateMapping(JSONObject definition, Object output, Map<String, Object> variables) {
        if (definition == null) return;
        String stateMapping = definition.getString("stateMapping");
        boolean mapped = false;
        if (StringUtils.isNotBlank(stateMapping)) {
            try {
                JSONObject mapping = JSONObject.parseObject(stateMapping);
                for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                    String targetKey = entry.getKey();
                    if (StringUtils.isBlank(targetKey) || entry.getValue() == null) continue;
                    Object value = resolveMappingExpr(String.valueOf(entry.getValue()), output);
                    if (value != null) variables.put(targetKey, value);
                }
                mapped = true;
            } catch (Exception ignored) { /* 回退到 outputKey，保留既有输出 */ }
        }
        if (!mapped) {
            String outputKey = definition.getString("outputKey");
            if (StringUtils.isNotBlank(outputKey) && output != null) variables.put(outputKey, output);
        }
        String internalKey = definition.getString("internalKey");
        if (StringUtils.isNotBlank(internalKey) && output != null) variables.put(internalKey, output);
    }

    /**
     * 解析MappingExpr。
     */
    private Object resolveMappingExpr(String expr, Object output) {
        String trimmed = expr == null ? "" : expr.trim();
        if ("$output".equals(trimmed)) return output;
        if (trimmed.startsWith("$json.")) return extractJsonPath(output, trimmed.substring("$json.".length()));
        return expr; // 字面量
    }

    /** 解析转换与子流程映射的安全变量路径：root.path 或 $.root.path。 */
    private Object resolveVariablePath(Map<String, Object> variables, String path) {
        if (StringUtils.isBlank(path)) return null;
        String normalized = StringUtils.removeStart(path.trim(), "$." );
        String[] parts = normalized.split("\\.");
        Object current = variables.get(parts[0]);
        for (int i = 1; i < parts.length && current != null; i++) {
            String part = parts[i];
            if (current instanceof Map) current = ((Map<?, ?>) current).get(part);
            else if (current instanceof JSONObject) current = ((JSONObject) current).get(part);
            else if (current instanceof JSONArray && part.matches("\\d+")) {
                int index = Integer.parseInt(part);
                current = index >= 0 && index < ((JSONArray) current).size() ? ((JSONArray) current).get(index) : null;
            } else return null;
        }
        return current;
    }

    /**
     * 从输出中按点号路径提取字段；输出为字符串时先尝试 JSON 解析。
     */
    private Object extractJsonPath(Object output, String path) {
        Object current = output;
        if (current instanceof String) {
            try {
                current = JSON.parse((String) current);
            } catch (Exception e) {
                return null;
            }
        }
        for (String segment : path.split("\\.")) {
            if (segment.isEmpty()) return null;
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(segment);
            } else if (current instanceof JSONObject) {
                current = ((JSONObject) current).get(segment);
            } else if (current instanceof JSONArray && segment.matches("\\d+")) {
                int index = Integer.parseInt(segment);
                if (index < 0 || index >= ((JSONArray) current).size()) return null;
                current = ((JSONArray) current).get(index);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * 当前Definition。
     */
    private JSONObject currentDefinition(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node) {
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        if (version == null) return null;
        return buildNodeMap(version.getNodes()).get(node.getNodeId());
    }

    /**
     * 解析工作流绑定的 Agent 定义 ID。
     */
    private String resolveMcpAgentId(AgentWorkflowInstance instance) {
        AgentWorkflow workflow = workflowService.getById(instance.getWorkflowId());
        if (workflow != null && StringUtils.isNotBlank(workflow.getAgentDefinitionId()))
            return workflow.getAgentDefinitionId();
        return "workflow:" + instance.getWorkflowId();
    }

    /**
     * 构建NodeMap。
     */
    private Map<String, JSONObject> buildNodeMap(String nodesText) {
        Map<String, JSONObject> map = new LinkedHashMap<String, JSONObject>();
        if (StringUtils.isBlank(nodesText)) return map;
        for (Object value : com.alibaba.fastjson2.JSONArray.parseArray(nodesText)) {
            JSONObject node = (JSONObject) value;
            map.put(node.getString("id"), node);
        }
        return map;
    }

    /**
     * 查找StartNode。
     */
    private String findStartNode(Map<String, JSONObject> nodeMap) {
        for (Map.Entry<String, JSONObject> e : nodeMap.entrySet()) {
            if ("start".equals(e.getValue().getString("type"))) return e.getKey();
        }
        return null;
    }
}
