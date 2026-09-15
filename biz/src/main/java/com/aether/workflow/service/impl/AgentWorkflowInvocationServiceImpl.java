package com.aether.workflow.service.impl;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowInvocation;
import com.aether.workflow.entity.AgentWorkflowInvocationCommand;
import com.aether.workflow.entity.AgentWorkflowNodeInstance;
import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.workflow.entity.AgentDefinitionWorkflowCapabilityBinding;
import com.aether.workflow.dto.AgentWorkflowEventDto;
import com.aether.workflow.dto.AgentWorkflowInteractionDto;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.aether.workflow.service.AgentDefinitionWorkflowCapabilityBindingService;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowExternalInvocationService;
import com.aether.workflow.service.AgentWorkflowEventReceiptService;
import com.aether.workflow.service.AgentWorkflowInstanceService;
import com.aether.workflow.service.AgentWorkflowInvocationService;
import com.aether.workflow.service.AgentWorkflowInvocationRecordService;
import com.aether.workflow.service.AgentWorkflowInvocationCommandService;
import com.aether.workflow.service.AgentWorkflowNodeInstanceService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.service.AgentWorkflowVersionService;
import com.aether.workflow.runtime.WorkflowOutputResolver;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import com.aether.workflow.vo.AgentWorkflowInvocationObservation;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Agent 调用工作流的 P0 领域服务。 */
@Service
public class AgentWorkflowInvocationServiceImpl implements AgentWorkflowInvocationService {
    private static final String ACTION_START = "START";
    private static final String ACTION_OBSERVE = "OBSERVE";
    private static final String ACTION_STOP = "STOP";

    private final AgentWorkflowCapabilityService capabilityService;
    private final AgentDefinitionWorkflowCapabilityBindingService capabilityBindingService;
    private final AgentWorkflowInvocationRecordService invocationStore;
    private final AgentWorkflowService workflowService;
    private final AgentWorkflowVersionService versionService;
    private final AgentWorkflowInstanceService instanceService;
    private final AgentWorkflowNodeInstanceService nodeService;
    private final AgentWorkflowExecutionService executionService;
    private final WorkflowOutputResolver outputResolver;
    private final AgentWorkflowExternalInvocationService externalInvocationService;
    private final AgentWorkflowEventReceiptService eventReceiptService;
    private final AgentWorkflowInvocationCommandService commandService;

    public AgentWorkflowInvocationServiceImpl(AgentWorkflowCapabilityService capabilityService,
                                              AgentDefinitionWorkflowCapabilityBindingService capabilityBindingService,
                                              AgentWorkflowService workflowService,
                                              AgentWorkflowVersionService versionService,
                                              AgentWorkflowInstanceService instanceService,
                                              AgentWorkflowNodeInstanceService nodeService,
                                              AgentWorkflowExecutionService executionService,
                                              WorkflowOutputResolver outputResolver,
                                              AgentWorkflowInvocationRecordService invocationStore,
                                              AgentWorkflowExternalInvocationService externalInvocationService,
                                              AgentWorkflowEventReceiptService eventReceiptService,
                                              AgentWorkflowInvocationCommandService commandService) {
        this.capabilityService = capabilityService;
        this.capabilityBindingService = capabilityBindingService;
        this.workflowService = workflowService;
        this.versionService = versionService;
        this.instanceService = instanceService;
        this.nodeService = nodeService;
        this.executionService = executionService;
        this.outputResolver = outputResolver;
        this.invocationStore = invocationStore;
        this.externalInvocationService = externalInvocationService;
        this.eventReceiptService = eventReceiptService;
        this.commandService = commandService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowInvocationResult start(String capabilityId, String operationKey, Map<String, Object> input,
                                               String agentDefinitionId, String agentRunId, String agentTaskId,
                                               String toolCallId, String userId, String applicationId) {
        if (StringUtils.isAnyBlank(capabilityId, operationKey, agentDefinitionId, userId))
            throw failure(400, "agent.workflow.invocation.arguments.required");
        if (operationKey.length() > 256)
            throw failure(422, "agent.workflow.invocation.operation-key.invalid");
        AgentWorkflowCapability capability = requiredCapability(capabilityId, applicationId);
        requireAction(capability, ACTION_START);
        requireAgent(capability, agentDefinitionId);
        String principalId = userId;
        AgentWorkflowInvocation existing = invocationStore.findByOperation(capabilityId, principalId, operationKey);
        if (existing != null) return result(existing, "WORKFLOW_INVOCATION_IDEMPOTENT");

        AgentWorkflow workflow = workflowService.getById(capability.getWorkflowId());
        AgentWorkflowVersion version = versionService.getById(capability.getWorkflowVersionId());
        if (workflow == null || version == null || !StringUtils.equals(capability.getApplicationId(), workflow.getApplicationId())
                || !capability.getWorkflowId().equals(version.getWorkflowId())
                || !Integer.valueOf(1).equals(workflow.getStatus())
                || workflow.getPublishedVersion() == null || !workflow.getPublishedVersion().equals(version.getVersionNo())) {
            throw failure(409, "agent.workflow.capability.version.invalid");
        }
        if (StringUtils.isNotBlank(applicationId) && !StringUtils.equals(applicationId, capability.getApplicationId()))
            throw failure(403, "agent.workflow.capability.application.denied");

        AgentWorkflowInvocation invocation = new AgentWorkflowInvocation();
        invocation.setTenantId(capability.getTenantId());
        invocation.setApplicationId(capability.getApplicationId());
        invocation.setCapabilityId(capabilityId);
        invocation.setWorkflowId(capability.getWorkflowId());
        invocation.setWorkflowVersionId(capability.getWorkflowVersionId());
        invocation.setAgentDefinitionId(agentDefinitionId);
        invocation.setAgentRunId(agentRunId);
        invocation.setAgentTaskId(agentTaskId);
        invocation.setPrincipalType("USER");
        invocation.setPrincipalId(principalId);
        invocation.setToolCallId(toolCallId);
        invocation.setOperationKey(operationKey);
        invocation.setStatus("ACCEPTED");
        invocation.setInputSnapshot(JSONObject.toJSONString(input == null ? Collections.emptyMap() : input));
        invocation.setStartedAt(System.currentTimeMillis());
        try {
            if (!invocationStore.save(invocation)) throw failure(500, "agent.workflow.invocation.save.failed");
        } catch (DuplicateKeyException duplicate) {
            AgentWorkflowInvocation duplicateInvocation = invocationStore.findByOperation(capabilityId, principalId, operationKey);
            if (duplicateInvocation != null) return result(duplicateInvocation, "WORKFLOW_INVOCATION_IDEMPOTENT");
            throw duplicate;
        }

        try {
            AgentWorkflowInstance instance = executionService.start(capability.getWorkflowId(), input, userId);
            if (!capability.getWorkflowVersionId().equals(instance.getWorkflowVersionId())) {
                executionService.terminate(instance.getId(), userId);
                invocation.setStatus("FAILED");
                invocationStore.updateById(invocation);
                throw failure(409, "agent.workflow.capability.version.changed");
            }
            invocation.setWorkflowInstanceId(instance.getId());
            invocation.setStatus("RUNNING");
            invocationStore.updateById(invocation);
            AgentWorkflowInstance instanceUpdate = new AgentWorkflowInstance();
            instanceUpdate.setId(instance.getId());
            instanceUpdate.setInvocationId(invocation.getId());
            instanceService.updateById(instanceUpdate);
            recordCommand(invocation, "START", operationKey, input, null, "WORKFLOW_INVOCATION_ACCEPTED");
            return result(invocation, "WORKFLOW_INVOCATION_ACCEPTED");
        } catch (ServerException e) {
            invocation.setStatus("FAILED");
            invocationStore.updateById(invocation);
            throw e;
        } catch (RuntimeException e) {
            invocation.setStatus("FAILED");
            invocationStore.updateById(invocation);
            throw e;
        }
    }

    @Override
    public AgentWorkflowInvocationObservation observe(String invocationId, String userId, String agentDefinitionId) {
        AgentWorkflowInvocation invocation = requiredInvocation(invocationId);
        requireOwner(invocation, userId, agentDefinitionId);
        // A capability may be disabled after an invocation has started.  Disabling
        // blocks new START calls, but must not strand an already-owned invocation
        // that still needs OBSERVE or STOP for cleanup and recovery.
        AgentWorkflowCapability capability = requiredCapability(invocation.getCapabilityId(), invocation.getApplicationId(), false);
        requireAgent(capability, agentDefinitionId);
        requireAction(capability, ACTION_OBSERVE);
        AgentWorkflowInstanceVo snapshot = executionService.detail(invocation.getWorkflowInstanceId(), userId);
        AgentWorkflowInvocationObservation result = new AgentWorkflowInvocationObservation();
        result.setInvocationId(invocation.getId());
        result.setInstanceId(invocation.getWorkflowInstanceId());
        result.setWorkflowId(invocation.getWorkflowId());
        result.setWorkflowVersionId(invocation.getWorkflowVersionId());
        result.setStatus(snapshot.getStatus());
        result.setStateVersion(snapshot.getStateVersion() == null ? 0L : snapshot.getStateVersion());
        result.setCurrentNodeId(snapshot.getCurrentNodeId());
        com.aether.workflow.entity.AgentWorkflowNodeInstance current = currentNode(snapshot);
        if (current != null) {
            result.setCurrentNodeType(current.getNodeType());
            result.setCurrentNodeName(nodeName(snapshot.getVersionNodes(), current.getNodeId()));
        }
        result.setOutput(outputResolver.resolve(snapshot, capability.getOutputSchema()));
        result.setResultCode(resultCode(snapshot.getStatus()));
        result.setNextAction(nextAction(invocation.getCapabilityId(), snapshot));
        if (isTerminal(snapshot.getStatus())) {
            // Preserve the workflow's stable terminal code (including TIMED_OUT)
            // instead of collapsing every non-success outcome into FAILED.
            invocation.setStatus(snapshot.getStatus());
            invocation.setCompletedAt(System.currentTimeMillis());
            invocation.setOutputSnapshot(JSONObject.toJSONString(result.getOutput()));
            invocationStore.updateById(invocation);
        } else {
            String projected = StringUtils.startsWith(snapshot.getStatus(), "WAITING_") ? "WAITING_ACTION" : "RUNNING";
            if (!StringUtils.equals(projected, invocation.getStatus())) {
                invocation.setStatus(projected);
                invocationStore.updateById(invocation);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowInvocationResult stop(String invocationId, String reason, Long expectedStateVersion,
                                              String userId, String agentDefinitionId) {
        AgentWorkflowInvocation invocation = requiredInvocation(invocationId);
        requireOwner(invocation, userId, agentDefinitionId);
        AgentWorkflowCapability capability = requiredCapability(invocation.getCapabilityId(), invocation.getApplicationId(), false);
        requireAgent(capability, agentDefinitionId);
        requireAction(capability, ACTION_STOP);
        AgentWorkflowInstance instance = lockedInstance(invocation);
        boolean alreadyTerminal = isTerminal(instance.getStatus());
        if (!alreadyTerminal) {
            ensureExpectedState(instance, expectedStateVersion);
            executionService.terminate(instance.getId(), userId);
            invocation.setStatus("TERMINATED");
            invocation.setCompletedAt(System.currentTimeMillis());
        } else {
            // A late stop must be idempotent and must not rewrite a completed,
            // failed, or timed-out workflow as TERMINATED.
            invocation.setStatus(instance.getStatus());
        }
        invocationStore.updateById(invocation);
        String resultCode = alreadyTerminal ? "WORKFLOW_INVOCATION_ALREADY_TERMINAL" : "WORKFLOW_INVOCATION_STOP_ACCEPTED";
        recordCommand(invocation, "STOP", StringUtils.defaultIfBlank(invocation.getToolCallId(), "stop:" + invocation.getId()),
                Collections.singletonMap("reason", reason), alreadyTerminal ? null : expectedStateVersion, resultCode);
        return result(invocation, resultCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowInvocationResult provideAgentInput(String invocationId, Map<String, Object> input,
                                                            Long expectedStateVersion, String userId, String agentDefinitionId) {
        AgentWorkflowInvocation invocation = requiredInvocation(invocationId);
        requireOwner(invocation, userId, agentDefinitionId);
        AgentWorkflowCapability capability = requiredCapability(invocation.getCapabilityId(), invocation.getApplicationId(), false);
        requireAgent(capability, agentDefinitionId);
        requireAction(capability, "PROVIDE_AGENT_INPUT");
        AgentWorkflowInstance instance = lockedInstance(invocation);
        ensureExpectedState(instance, expectedStateVersion);
        if (!"WAITING_USER".equals(instance.getStatus()))
            throw failure(409, "agent.workflow.invocation.input.state.invalid");
        AgentWorkflowNodeInstance node = currentNode(instance);
        JSONObject definition = currentDefinition(instance, node);
        if (node == null || definition == null || !"interaction".equals(node.getNodeType()) || !agentInputAllowed(definition))
            throw failure(403, "agent.workflow.invocation.input.not-enabled");
        Map<String, Object> safeInput = validateAgentInput(capability, definition, input);
        AgentWorkflowInteractionDto answer = new AgentWorkflowInteractionDto();
        answer.setAnswer(safeInput);
        executionService.answer(instance.getId(), answer, userId);
        invocation.setStatus("RUNNING");
        invocationStore.updateById(invocation);
        recordCommand(invocation, "PROVIDE_AGENT_INPUT", "state:" + expectedStateVersion,
                safeInput, expectedStateVersion, "WORKFLOW_AGENT_INPUT_ACCEPTED");
        return result(invocation, "WORKFLOW_AGENT_INPUT_ACCEPTED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowInvocationResult resolveMcpApproval(String invocationId, String decision,
                                                            Long expectedStateVersion, String userId,
                                                            String agentDefinitionId) {
        AgentWorkflowInvocation invocation = requiredInvocation(invocationId);
        requireOwner(invocation, userId, agentDefinitionId);
        AgentWorkflowCapability capability = requiredCapability(invocation.getCapabilityId(), invocation.getApplicationId(), false);
        requireAgent(capability, agentDefinitionId);
        requireAction(capability, "RESOLVE_MCP_APPROVAL");
        String normalizedDecision = StringUtils.lowerCase(StringUtils.trimToEmpty(decision));
        if (!isMcpApprovalDecision(normalizedDecision))
            throw failure(422, "agent.workflow.invocation.mcp-approval.decision.invalid");
        AgentWorkflowInstance instance = lockedInstance(invocation);
        String operationKey = "decision:" + expectedStateVersion + ":" + normalizedDecision;
        if (commandService != null && commandService.findByOperation(invocation.getId(), "RESOLVE_MCP_APPROVAL", operationKey) != null)
            return result(invocation, "WORKFLOW_MCP_APPROVAL_IDEMPOTENT");
        ensureExpectedState(instance, expectedStateVersion);
        if (!"WAITING_USER".equals(instance.getStatus()))
            throw failure(409, "agent.workflow.invocation.mcp-approval.state.invalid");
        AgentWorkflowNodeInstance node = currentNode(instance);
        JSONObject config = node == null || StringUtils.isBlank(node.getInteractionConfig())
                ? new JSONObject() : JSONObject.parseObject(node.getInteractionConfig());
        if (!isMcpToolApprovalConfig(config))
            throw failure(409, "agent.workflow.invocation.mcp-approval.node.invalid");
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("decision", normalizedDecision);
        AgentWorkflowInteractionDto interaction = new AgentWorkflowInteractionDto();
        interaction.setAnswer(answer);
        // Reuse the workflow's reliable answer path: the MCP call is dispatched
        // asynchronously and retains its existing idempotency/unknown-result rules.
        executionService.answer(instance.getId(), interaction, userId);
        invocation.setStatus("RUNNING");
        invocationStore.updateById(invocation);
        recordCommand(invocation, "RESOLVE_MCP_APPROVAL", operationKey,
                answer, expectedStateVersion, "WORKFLOW_MCP_APPROVAL_ACCEPTED");
        return result(invocation, "WORKFLOW_MCP_APPROVAL_ACCEPTED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowInvocationResult signalEvent(String invocationId, String eventType, String eventId,
                                                      String correlationKey, Map<String, Object> data,
                                                      Long expectedStateVersion, String userId, String agentDefinitionId) {
        AgentWorkflowInvocation invocation = requiredInvocation(invocationId);
        requireOwner(invocation, userId, agentDefinitionId);
        AgentWorkflowCapability capability = requiredCapability(invocation.getCapabilityId(), invocation.getApplicationId(), false);
        requireAgent(capability, agentDefinitionId);
        requireAction(capability, "SIGNAL_EVENT");
        if (StringUtils.isBlank(eventType) || StringUtils.isBlank(eventId))
            throw failure(400, "agent.workflow.invocation.event.arguments.required");
        if (eventId.length() > 256)
            throw failure(422, "agent.workflow.invocation.event.id.invalid");
        if (!jsonArrayContains(capability.getAllowedEventTypes(), eventType))
            throw failure(403, "agent.workflow.invocation.event.denied");
        AgentWorkflowInstance instance = lockedInstance(invocation);
        ensureExpectedState(instance, expectedStateVersion);
        if (!"WAITING_EVENT".equals(instance.getStatus()))
            throw failure(409, "agent.workflow.invocation.event.state.invalid");
        AgentWorkflowNodeInstance waitingNode = currentNode(instance);
        JSONObject eventConfig = waitingNode == null || StringUtils.isBlank(waitingNode.getInteractionConfig())
                ? new JSONObject() : JSONObject.parseObject(waitingNode.getInteractionConfig());
        if (!StringUtils.equals(eventType, eventConfig.getString("eventType"))
                || (StringUtils.isNotBlank(eventConfig.getString("correlationKey"))
                && !StringUtils.equals(eventConfig.getString("correlationKey"), correlationKey)))
            throw failure(422, "agent.workflow.invocation.event.mismatch");
        if (eventReceiptService != null && !eventReceiptService.claim(invocation.getApplicationId(), eventType, eventId, correlationKey))
            return result(invocation, "WORKFLOW_EVENT_IDEMPOTENT");
        AgentWorkflowEventDto event = new AgentWorkflowEventDto();
        event.setEventId(eventId);
        event.setCorrelationKey(correlationKey);
        event.setData(data == null ? Collections.<String, Object>emptyMap() : data);
        executionService.signalEvent(instance.getId(), eventType, event, userId);
        invocation.setStatus("RUNNING");
        invocationStore.updateById(invocation);
        recordCommand(invocation, "SIGNAL_EVENT", eventId, event, expectedStateVersion, "WORKFLOW_EVENT_ACCEPTED");
        return result(invocation, "WORKFLOW_EVENT_ACCEPTED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowInvocationResult retryNode(String invocationId, String nodeId, Long expectedStateVersion,
                                                    String userId, String agentDefinitionId) {
        AgentWorkflowInvocation invocation = requiredInvocation(invocationId);
        requireOwner(invocation, userId, agentDefinitionId);
        AgentWorkflowCapability capability = requiredCapability(invocation.getCapabilityId(), invocation.getApplicationId(), false);
        requireAgent(capability, agentDefinitionId);
        requireAction(capability, "RETRY_NODE");
        if (StringUtils.isBlank(nodeId)) throw failure(400, "agent.workflow.invocation.node.required");
        AgentWorkflowInstance instance = lockedInstance(invocation);
        ensureExpectedState(instance, expectedStateVersion);
        if (!"FAILED".equals(instance.getStatus()) || !StringUtils.equals(nodeId, instance.getCurrentNodeId()))
            throw failure(409, "agent.workflow.invocation.retry.state.invalid");
        if (externalInvocationService != null && externalInvocationService.listByInstanceId(instance.getId()).stream()
                .anyMatch(item -> "UNKNOWN".equals(item.getStatus())))
            throw failure(409, "agent.workflow.invocation.retry.unknown-external");
        executionService.retryNode(instance.getId(), nodeId, userId);
        invocation.setStatus("RUNNING");
        invocationStore.updateById(invocation);
        recordCommand(invocation, "RETRY_NODE", nodeId + ":" + expectedStateVersion,
                Collections.singletonMap("nodeId", nodeId), expectedStateVersion, "WORKFLOW_RETRY_ACCEPTED");
        return result(invocation, "WORKFLOW_RETRY_ACCEPTED");
    }

    private AgentWorkflowCapability requiredCapability(String id, String applicationId) {
        return requiredCapability(id, applicationId, true);
    }

    private AgentWorkflowCapability requiredCapability(String id, String applicationId, boolean requireEnabled) {
        AgentWorkflowCapability value = capabilityService.getById(id);
        if (value == null || Boolean.TRUE.equals(value.getDeleted())
                || (requireEnabled && (Boolean.FALSE.equals(value.getEnabled()) || !Integer.valueOf(1).equals(value.getStatus()))))
            throw failure(404, "agent.workflow.capability.not-found");
        if (StringUtils.isNotBlank(applicationId) && !StringUtils.equals(applicationId, value.getApplicationId()))
            throw failure(403, "agent.workflow.capability.application.denied");
        return value;
    }

    private AgentWorkflowInstance lockedInstance(AgentWorkflowInvocation invocation) {
        if (StringUtils.isBlank(invocation.getWorkflowInstanceId()))
            throw failure(404, "agent.workflow.invocation.instance.not-found");
        AgentWorkflowInstance instance = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getId, invocation.getWorkflowInstanceId())
                .eq(AgentWorkflowInstance::getDeleted, false).last("FOR UPDATE"));
        if (instance == null || (StringUtils.isNotBlank(instance.getInvocationId())
                && !StringUtils.equals(instance.getInvocationId(), invocation.getId())))
            throw failure(404, "agent.workflow.invocation.instance.not-found");
        return instance;
    }

    private void ensureExpectedState(AgentWorkflowInstance instance, Long expectedStateVersion) {
        if (expectedStateVersion == null)
            throw failure(400, "agent.workflow.invocation.state-version.required");
        long current = instance.getStateVersion() == null ? 0L : instance.getStateVersion();
        if (current != expectedStateVersion)
            throw failure(409, "agent.workflow.invocation.state.changed");
    }

    private AgentWorkflowNodeInstance currentNode(AgentWorkflowInstance instance) {
        if (instance == null || StringUtils.isBlank(instance.getCurrentNodeId())) return null;
        return nodeService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                .eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId())
                .eq(AgentWorkflowNodeInstance::getNodeId, instance.getCurrentNodeId())
                .eq(AgentWorkflowNodeInstance::getDeleted, false));
    }

    private JSONObject currentDefinition(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node) {
        if (instance == null || node == null) return null;
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        if (version == null || StringUtils.isBlank(version.getNodes())) return null;
        try {
            for (Object item : JSONArray.parseArray(version.getNodes())) {
                JSONObject definition = item instanceof JSONObject ? (JSONObject) item : null;
                if (definition != null && StringUtils.equals(node.getNodeId(), definition.getString("id"))) return definition;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private boolean agentInputAllowed(JSONObject definition) {
        if (definition == null || "approval".equals(definition.getString("mode"))) return false;
        if (definition.getBooleanValue("agentInputAllowed")) return true;
        String policy = StringUtils.defaultIfBlank(definition.getString("agentInputPolicy"),
                definition.getString("inputPolicy"));
        return "AGENT_INPUT_ALLOWED".equalsIgnoreCase(policy);
    }

    private boolean isMcpToolApprovalConfig(JSONObject config) {
        return config != null && ("mcp_tool_approval".equals(config.getString("approvalType"))
                || "mcp_tool_approval".equals(config.getString("type")));
    }

    private boolean isMcpApprovalDecision(String decision) {
        return "once".equalsIgnoreCase(StringUtils.defaultString(decision))
                || "allow_10m".equalsIgnoreCase(StringUtils.defaultString(decision))
                || "reject".equalsIgnoreCase(StringUtils.defaultString(decision));
    }

    private Map<String, Object> validateAgentInput(AgentWorkflowCapability capability, JSONObject definition,
                                                   Map<String, Object> input) {
        Map<String, Object> supplied = input == null ? Collections.<String, Object>emptyMap() : input;
        java.util.Set<String> declared = new java.util.LinkedHashSet<>();
        java.util.Set<String> required = new java.util.LinkedHashSet<>();
        collectSchemaFields(definition.get("agentInputSchema"), declared, required);
        if (declared.isEmpty()) collectSchemaFields(definition.get("inputSchema"), declared, required);
        if (declared.isEmpty()) collectQuestionFields(definition.getJSONArray("questions"), declared, required);
        java.util.Set<String> capabilityFields = jsonArrayValues(capability.getAgentWritableVariables());
        if (!capabilityFields.isEmpty()) {
            if (declared.isEmpty()) declared.addAll(capabilityFields);
            else declared.retainAll(capabilityFields);
            required.retainAll(declared);
        }
        if (declared.isEmpty()) throw failure(422, "agent.workflow.invocation.input.schema.invalid");
        for (String key : supplied.keySet()) {
            if (StringUtils.isBlank(key) || key.startsWith("_") || !declared.contains(key))
                throw failure(422, "agent.workflow.invocation.input.field.denied");
        }
        for (String key : required) {
            Object value = supplied.get(key);
            if (value == null || StringUtils.isBlank(String.valueOf(value)))
                throw failure(422, "agent.workflow.invocation.input.required");
        }
        return new LinkedHashMap<>(supplied);
    }

    private void collectSchemaFields(Object schemaValue, java.util.Set<String> declared, java.util.Set<String> required) {
        if (schemaValue == null) return;
        try {
            if (schemaValue instanceof JSONArray || String.valueOf(schemaValue).trim().startsWith("[")) {
                JSONArray fields = schemaValue instanceof JSONArray ? (JSONArray) schemaValue : JSONArray.parseArray(String.valueOf(schemaValue));
                for (Object item : fields) {
                    if (!(item instanceof JSONObject)) continue;
                    JSONObject field = (JSONObject) item;
                    String name = StringUtils.defaultIfBlank(field.getString("name"), field.getString("id"));
                    if (StringUtils.isNotBlank(name)) {
                        declared.add(name);
                        if (field.getBooleanValue("required")) required.add(name);
                    }
                }
                return;
            }
            JSONObject schema = schemaValue instanceof JSONObject ? (JSONObject) schemaValue : JSONObject.parseObject(String.valueOf(schemaValue));
            if (schema == null) return;
            JSONObject properties = schema.getJSONObject("properties");
            if (properties != null) declared.addAll(properties.keySet());
            JSONArray requiredValues = schema.getJSONArray("required");
            if (requiredValues != null) for (Object value : requiredValues) required.add(String.valueOf(value));
        } catch (Exception ignored) { }
    }

    private void collectQuestionFields(JSONArray questions, java.util.Set<String> declared, java.util.Set<String> required) {
        if (questions == null) return;
        for (Object item : questions) {
            if (!(item instanceof JSONObject)) continue;
            JSONObject question = (JSONObject) item;
            String name = StringUtils.defaultIfBlank(question.getString("id"), question.getString("name"));
            if (StringUtils.isNotBlank(name)) {
                declared.add(name);
                if (question.getBooleanValue("required")) required.add(name);
            }
        }
    }

    private java.util.Set<String> jsonArrayValues(String serialized) {
        java.util.Set<String> values = new java.util.LinkedHashSet<>();
        if (StringUtils.isBlank(serialized)) return values;
        try {
            JSONArray array = JSONArray.parseArray(serialized);
            if (array != null) for (Object value : array) if (value != null) values.add(String.valueOf(value));
        } catch (Exception ignored) { }
        return values;
    }

    private void recordCommand(AgentWorkflowInvocation invocation, String commandType, String operationKey,
                               Object payload, Long expectedStateVersion, String resultCode) {
        if (commandService == null || invocation == null) return;
        AgentWorkflowInvocationCommand command = commandService.findByOperation(invocation.getId(), commandType, operationKey);
        if (command != null) return;
        command = new AgentWorkflowInvocationCommand();
        command.setTenantId(invocation.getTenantId());
        command.setApplicationId(invocation.getApplicationId());
        command.setInvocationId(invocation.getId());
        command.setCommandType(commandType);
        command.setOperationKey(operationKey);
        command.setCommandPayload(JSONObject.toJSONString(payload));
        command.setRequestedByType("AGENT");
        command.setRequestedById(invocation.getAgentDefinitionId());
        command.setExpectedStateVersion(expectedStateVersion);
        command.setStatus("APPLIED");
        command.setResultCode(resultCode);
        command.setResultSummary(resultCode);
        command.setRequestedAt(System.currentTimeMillis());
        command.setAppliedAt(System.currentTimeMillis());
        try { commandService.save(command); }
        catch (DuplicateKeyException ignored) { /* concurrent duplicate is already recorded */ }
    }

    private AgentWorkflowInvocation requiredInvocation(String id) {
        AgentWorkflowInvocation value = invocationStore.getById(id);
        if (value == null || Boolean.TRUE.equals(value.getDeleted())) throw failure(404, "agent.workflow.invocation.not-found");
        return value;
    }

    private void requireOwner(AgentWorkflowInvocation value, String userId, String agentId) {
        if (!StringUtils.equals(value.getPrincipalId(), userId) || !StringUtils.equals(value.getAgentDefinitionId(), agentId))
            throw failure(403, "agent.workflow.invocation.denied");
    }

    private void requireAgent(AgentWorkflowCapability capability, String agentId) {
        if (capabilityBindingService == null || capability == null || StringUtils.isBlank(agentId)
                || capabilityBindingService.count(Wrappers.lambdaQuery(AgentDefinitionWorkflowCapabilityBinding.class)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getAgentDefinitionId, agentId)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getCapabilityId, capability.getId())
                .eq(AgentDefinitionWorkflowCapabilityBinding::getStatus, 1)
                .eq(AgentDefinitionWorkflowCapabilityBinding::getDeleted, false)) <= 0) {
            throw failure(403, "agent.workflow.capability.agent.denied");
        }
    }

    private void requireAction(AgentWorkflowCapability capability, String action) {
        if (!jsonArrayContains(capability.getAllowedActions(), action)) throw failure(403, "agent.workflow.capability.action.denied");
    }

    private boolean jsonArrayContains(String value, String expected) {
        try { return StringUtils.isNotBlank(value) && JSONArray.parseArray(value).contains(expected); }
        catch (Exception ignored) { return false; }
    }

    private AgentWorkflowNodeInstance currentNode(AgentWorkflowInstanceVo snapshot) {
        if (snapshot.getNodes() == null || StringUtils.isBlank(snapshot.getCurrentNodeId())) return null;
        for (AgentWorkflowNodeInstance node : snapshot.getNodes())
            if (snapshot.getCurrentNodeId().equals(node.getNodeId())) return node;
        return null;
    }

    private String nodeName(String nodesJson, String nodeId) {
        try {
            for (Object item : JSONArray.parseArray(nodesJson)) {
                JSONObject node = (JSONObject) item;
                if (StringUtils.equals(nodeId, node.getString("id"))) return node.getString("name");
            }
        } catch (Exception ignored) { }
        return null;
    }

    private Map<String, Object> nextAction(String capabilityId, AgentWorkflowInstanceVo snapshot) {
        Map<String, Object> action = new LinkedHashMap<>();
        String status = snapshot.getStatus();
        AgentWorkflowCapability capability = capabilityService.getById(capabilityId);
        AgentWorkflowNodeInstance node = currentNode(snapshot);
        JSONObject definition = node == null ? null : definition(snapshot, node.getNodeId());
        if ("WAITING_USER".equals(status) && hasAction(capability, "RESOLVE_MCP_APPROVAL")
                && isMcpToolApprovalConfig(node == null || StringUtils.isBlank(node.getInteractionConfig())
                ? null : JSONObject.parseObject(node.getInteractionConfig()))) {
            action.put("type", "RESOLVE_MCP_APPROVAL");
            action.put("required", java.util.Arrays.asList("invocationId", "expectedStateVersion", "decision"));
            action.put("authorizationRequired", true);
            action.put("decisions", java.util.Arrays.asList("once", "allow_10m", "reject"));
            JSONObject config = JSONObject.parseObject(node.getInteractionConfig());
            if (node != null && StringUtils.isNotBlank(node.getNodeId())) action.put("nodeId", node.getNodeId());
            if (StringUtils.isNotBlank(config.getString("toolName"))) action.put("toolName", config.getString("toolName"));
            if (StringUtils.isNotBlank(config.getString("question"))) action.put("question", config.getString("question"));
        } else if ("WAITING_USER".equals(status) && hasAction(capability, "PROVIDE_AGENT_INPUT")
                && agentInputAllowed(definition)) {
            action.put("type", "PROVIDE_AGENT_INPUT");
            action.put("required", java.util.Arrays.asList("invocationId", "expectedStateVersion", "input"));
            if (node != null && StringUtils.isNotBlank(node.getNodeId())) action.put("nodeId", node.getNodeId());
            action.put("schema", agentInputSchema(definition));
        } else if ("WAITING_USER".equals(status)) action.put("type", "WAITING_HUMAN");
        else if ("WAITING_EVENT".equals(status) && hasAction(capability, "SIGNAL_EVENT")) {
            action.put("type", "SIGNAL_EVENT");
            action.put("required", java.util.Arrays.asList("invocationId", "expectedStateVersion", "eventType", "eventId"));
            if (node != null && StringUtils.isNotBlank(node.getInteractionConfig())) {
                JSONObject config = JSONObject.parseObject(node.getInteractionConfig());
                action.put("eventType", config.getString("eventType"));
                action.put("correlationKey", config.getString("correlationKey"));
            }
        } else if ("FAILED".equals(status) && hasAction(capability, "RETRY_NODE")) {
            if (hasUnknownExternalResult(snapshot.getId())) {
                action.put("type", "WAIT_HUMAN");
                action.put("reason", "EXTERNAL_RESULT_UNKNOWN");
                action.put("retryable", false);
            } else {
                action.put("type", "RETRY_NODE");
                action.put("required", java.util.Arrays.asList("invocationId", "expectedStateVersion", "nodeId"));
                action.put("nodeId", snapshot.getCurrentNodeId());
            }
        }
        else if (isTerminal(status)) action.put("type", "NONE");
        else {
            action.put("type", ACTION_OBSERVE);
            action.put("required", java.util.Collections.singletonList("invocationId"));
        }
        return action;
    }

    private boolean hasUnknownExternalResult(String instanceId) {
        if (externalInvocationService == null || StringUtils.isBlank(instanceId)) return false;
        try {
            return externalInvocationService.listByInstanceId(instanceId).stream()
                    .anyMatch(item -> "UNKNOWN".equals(item.getStatus()));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasAction(String capabilityId, String action) {
        return hasAction(capabilityService.getById(capabilityId), action);
    }

    private boolean hasAction(AgentWorkflowCapability capability, String action) {
        return capability != null && jsonArrayContains(capability.getAllowedActions(), action);
    }

    private JSONObject definition(AgentWorkflowInstanceVo snapshot, String nodeId) {
        if (snapshot == null || StringUtils.isBlank(snapshot.getVersionNodes())) return null;
        try {
            for (Object item : JSONArray.parseArray(snapshot.getVersionNodes())) {
                JSONObject value = item instanceof JSONObject ? (JSONObject) item : null;
                if (value != null && StringUtils.equals(nodeId, value.getString("id"))) return value;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private Map<String, Object> agentInputSchema(JSONObject definition) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (definition == null) return schema;
        Object raw = definition.get("agentInputSchema");
        if (raw == null) raw = definition.get("inputSchema");
        if (raw instanceof JSONObject) {
            JSONObject object = (JSONObject) raw;
            schema.putAll(object);
        } else if (raw != null) {
            try { schema.put("fields", JSONArray.parseArray(String.valueOf(raw))); }
            catch (Exception ignored) { }
        }
        if (schema.isEmpty() && definition.getJSONArray("questions") != null)
            schema.put("fields", definition.getJSONArray("questions"));
        return schema;
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "TERMINATED".equals(status) || "TIMED_OUT".equals(status);
    }

    private String resultCode(String status) {
        if ("COMPLETED".equals(status)) return "WORKFLOW_COMPLETED";
        if ("FAILED".equals(status)) return "WORKFLOW_FAILED";
        if ("TERMINATED".equals(status)) return "WORKFLOW_TERMINATED";
        if ("TIMED_OUT".equals(status)) return "WORKFLOW_TIMED_OUT";
        if ("WAITING_USER".equals(status)) return "WORKFLOW_WAITING_HUMAN";
        return "WORKFLOW_RUNNING";
    }

    private AgentWorkflowInvocationResult result(AgentWorkflowInvocation invocation, String code) {
        AgentWorkflowInvocationResult result = new AgentWorkflowInvocationResult();
        result.setInvocationId(invocation.getId());
        result.setInstanceId(invocation.getWorkflowInstanceId());
        result.setStatus(invocation.getStatus());
        result.setResultCode(code);
        result.setWorkflowVersionId(invocation.getWorkflowVersionId());
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("type", ("WORKFLOW_INVOCATION_STOP_ACCEPTED".equals(code)
                || "WORKFLOW_INVOCATION_ALREADY_TERMINAL".equals(code)) ? "NONE" : "OBSERVE");
        result.setNextAction(next);
        return result;
    }

    private ServerException failure(int status, String key) {
        return new ServerException(status, I18nUtils.getMessage(key));
    }

}
