package com.aether.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.dto.AgentWorkflowBusinessStartDto;
import com.aether.agent.dto.AgentWorkflowInteractionDto;
import com.aether.agent.entity.*;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.service.*;
import com.aether.agent.tools.AgentToolWorkflow;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.agent.vo.AgentWorkflowInstanceVo;
import com.aether.agent.workflow.WorkflowConditionEvaluator;
import com.aether.agent.workflow.WorkflowDefinitionValidator;
import com.aether.agent.workflow.WorkflowVariableRenderer;
import com.aether.agent.workflow.WorkflowSseHub;
import com.aether.agent.workflow.WorkflowCallbackService;
import com.aether.agent.workflow.WorkflowExecutionJobDispatcher;
import com.aether.agent.workflow.WorkflowSensitiveDataSanitizer;
import com.aether.sys.entity.Role;
import com.aether.sys.entity.UserRole;
import com.aether.sys.service.RoleService;
import com.aether.sys.service.UserRoleService;
import com.aether.exception.ServerException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final UserRoleService userRoleService;
    private final RoleService roleService;

    public AgentWorkflowExecutionServiceImpl(AgentWorkflowService workflowService, AgentWorkflowVersionService versionService,
            AgentWorkflowInstanceService instanceService, AgentWorkflowNodeInstanceService nodeService,
            AgentChatService chatService, AgentToolWorkflow toolWorkflow, WorkflowSseHub sseHub,
            WorkflowCallbackService callbackService, WorkflowExecutionJobDispatcher executionJobDispatcher,
            WorkflowSensitiveDataSanitizer sensitiveDataSanitizer, UserRoleService userRoleService, RoleService roleService) {
        this.workflowService = workflowService; this.versionService = versionService; this.instanceService = instanceService;
        this.nodeService = nodeService; this.chatService = chatService; this.toolWorkflow = toolWorkflow; this.sseHub = sseHub;
        this.callbackService = callbackService;
        this.executionJobDispatcher = executionJobDispatcher;
        this.sensitiveDataSanitizer = sensitiveDataSanitizer;
        this.userRoleService = userRoleService;
        this.roleService = roleService;
    }

    // ── 公共接口 ─────────────────────────────────────────────

    @Override @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowInstance start(String workflowId, Map<String, Object> variables, String userId) {
        return startInternal(workflowId, variables, userId, null);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowInstance startBusiness(String workflowId, AgentWorkflowBusinessStartDto dto, String userId) {
        if (dto == null || StringUtils.isBlank(dto.getBusinessType()) || StringUtils.isBlank(dto.getBusinessId()) || StringUtils.isBlank(dto.getIdempotencyKey()))
            throw new ServerException(422, "业务启动必须提供 businessType、businessId 和 idempotencyKey");
        if (dto.getBusinessType().length() > 64 || dto.getBusinessId().length() > 128 || dto.getIdempotencyKey().length() > 128)
            throw new ServerException(422, "业务标识或幂等键长度超限");
        if (dto.getDeadlineAt() != null && dto.getDeadlineAt() <= System.currentTimeMillis())
            throw new ServerException(422, "业务流程截止时间必须晚于当前时间");
        try {
            callbackService.validateCallbackUrl(dto.getCallbackUrl());
        } catch (IllegalArgumentException ex) {
            throw new ServerException(422, ex.getMessage());
        }
        // 锁定定义行，使“查询既有实例 → 创建实例”在同一个工作流内串行化；
        // 避免并发的同幂等请求同时越过查询并触发重复节点执行。
        AgentWorkflow lockedWorkflow = workflowService.getOne(Wrappers.lambdaQuery(AgentWorkflow.class)
                .eq(AgentWorkflow::getId, workflowId).eq(AgentWorkflow::getDeleted, false).last("FOR UPDATE"));
        if (lockedWorkflow == null) throw new ServerException(404, "工作流不存在");
        AgentWorkflowInstance existing = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getWorkflowId, workflowId).eq(AgentWorkflowInstance::getUserId, userId)
                .eq(AgentWorkflowInstance::getIdempotencyKey, dto.getIdempotencyKey())
                .eq(AgentWorkflowInstance::getDeleted, false));
        if (existing != null) return existing;
        return startInternal(workflowId, dto.getVariables(), userId, dto);
    }

    private AgentWorkflowInstance startInternal(String workflowId, Map<String, Object> variables, String userId,
                                                AgentWorkflowBusinessStartDto business) {
        // 以定义行锁串行化容量检查与实例插入；不同应用实例不能同时越过并发上限。
        AgentWorkflow workflow = workflowService.getOne(Wrappers.lambdaQuery(AgentWorkflow.class)
                .eq(AgentWorkflow::getId, workflowId).eq(AgentWorkflow::getDeleted, false).last("FOR UPDATE"));
        if (workflow == null) throw new ServerException(404, "工作流不存在");
        if (!Integer.valueOf(1).equals(workflow.getStatus()) || workflow.getPublishedVersion() == null)
            throw new ServerException(422, "工作流尚未发布，不能启动");
        int maxConcurrent = workflow.getMaxConcurrentInstances() == null ? 0 : workflow.getMaxConcurrentInstances();
        if (maxConcurrent > 0) {
            long activeCount = instanceService.count(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                    .eq(AgentWorkflowInstance::getWorkflowId, workflowId)
                    .in(AgentWorkflowInstance::getStatus, "RUNNING", "WAITING_USER")
                    .eq(AgentWorkflowInstance::getDeleted, false));
            if (activeCount >= maxConcurrent)
                throw new ServerException(429, "工作流当前运行实例已达到并发上限，请稍后重试");
        }
        AgentWorkflowVersion version = versionService.getOne(Wrappers.lambdaQuery(AgentWorkflowVersion.class)
                .eq(AgentWorkflowVersion::getWorkflowId, workflowId).eq(AgentWorkflowVersion::getVersionNo, workflow.getPublishedVersion()));
        if (version == null) throw new ServerException(409, "工作流发布版本不存在");
        WorkflowDefinitionValidator.validateStartVariables(version.getInputSchema(), variables);
        AgentWorkflowInstance instance = new AgentWorkflowInstance();
        instance.setWorkflowId(workflowId); instance.setWorkflowVersionId(version.getId()); instance.setUserId(userId);
        if (business != null) {
            instance.setBusinessType(business.getBusinessType()); instance.setBusinessId(business.getBusinessId());
            instance.setIdempotencyKey(business.getIdempotencyKey()); instance.setCallbackUrl(business.getCallbackUrl());
            instance.setDeadlineAt(business.getDeadlineAt());
        }
        instance.setStatus("RUNNING"); instance.setVariables(JSON.toJSONString(variables == null ? new LinkedHashMap<String,Object>() : variables));
        instance.setStartedAt(System.currentTimeMillis()); instanceService.save(instance);
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
        return instance;
    }

    @Override
    public AgentWorkflowInstanceVo detail(String instanceId, String userId) {
        AgentWorkflowInstance instance = owned(instanceId, userId);
        AgentWorkflowInstanceVo vo = new AgentWorkflowInstanceVo();
        org.springframework.beans.BeanUtils.copyProperties(instance, vo);
        AgentWorkflow workflow = workflowService.getById(instance.getWorkflowId()); if (workflow != null) vo.setWorkflowName(workflow.getName());
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        if (version != null) { vo.setVersionNodes(version.getNodes()); vo.setVersionEdges(version.getEdges()); }
        vo.setNodes(nodeService.list(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                .eq(AgentWorkflowNodeInstance::getInstanceId, instanceId).orderByAsc(AgentWorkflowNodeInstance::getCreatedAt)));
        return vo;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void answer(String instanceId, AgentWorkflowInteractionDto dto, String userId) {
        AgentWorkflowInstance instance = ownedForUpdate(instanceId, userId);
        if (!"WAITING_USER".equals(instance.getStatus())) throw new ServerException(409, "当前流程不在等待用户操作");
        AgentWorkflowNodeInstance node = currentNode(instance);
        Map<String,Object> answer = dto == null || dto.getAnswer() == null ? new LinkedHashMap<String,Object>() : dto.getAnswer();
        JSONObject config = StringUtils.isBlank(node.getInteractionConfig()) ? new JSONObject() : JSONObject.parseObject(node.getInteractionConfig());
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
        if ("mcp".equals(node.getNodeType()) || isMcpToolApprovalConfig(config)) {
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
            Map<String,Object> variables = variables(instance);
            JSONObject definition = currentDefinition(instance, node);
            // 人工回答通常为单个字段（如 {answer: "内容"}），传给后续 AI 时只取内容而非整段 JSON
            Object answerValue = answer.size() == 1 ? answer.values().iterator().next() : answer;
            if (definition != null) {
                applyStateMapping(definition, answerValue, variables);
            } else {
                String outputKey = config.getString("outputKey"); if (StringUtils.isNotBlank(outputKey)) variables.put(outputKey, answerValue);
                String internalKey = config.getString("internalKey"); if (StringUtils.isNotBlank(internalKey)) variables.put(internalKey, answerValue);
            }
            instance.setVariables(JSON.toJSONString(variables)); completeNode(node, JSON.toJSONString(answer));
        }
        // 完成当前节点后，找到下一个节点继续执行
        String nextNodeId = findNextNodeId(instance, node);
        if (nextNodeId != null) {
            instance.setCurrentNodeId(nextNodeId);
        }
        instance.setStatus("RUNNING"); instanceService.updateById(instance);
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void retry(String instanceId, String userId) {
        AgentWorkflowInstance instance = ownedForUpdate(instanceId, userId);
        if (!"FAILED".equals(instance.getStatus())) throw new ServerException(409, "只有失败待重试的流程可以重试");
        AgentWorkflowNodeInstance node = currentNode(instance); node.setStatus("PENDING"); node.setErrorMessage(null);
        node.setRetryCount((node.getRetryCount() == null ? 0 : node.getRetryCount()) + 1); nodeService.updateById(node);
        instance.setStatus("RUNNING"); instance.setErrorMessage(null); instanceService.updateById(instance);
        executionJobDispatcher.enqueueAfterCommit(instance.getId());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public AgentWorkflowInstance replay(String instanceId, String userId) {
        AgentWorkflowInstance source = owned(instanceId, userId);
        if (StringUtils.isNotBlank(source.getBusinessType()) || StringUtils.isNotBlank(source.getBusinessId()) || StringUtils.isNotBlank(source.getIdempotencyKey()))
            throw new ServerException(409, "业务关联实例不能回放；请由业务系统使用新的幂等键重新发起");
        Map<String, Object> variables;
        try { variables = StringUtils.isBlank(source.getVariables()) ? new LinkedHashMap<String, Object>() : JSON.parseObject(source.getVariables(), Map.class); }
        catch (Exception ex) { throw new ServerException(422, "原始实例变量格式无效，不能回放"); }
        return startInternal(source.getWorkflowId(), variables, userId, null);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void executePending(String instanceId) {
        AgentWorkflowInstance instance = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getId, instanceId).eq(AgentWorkflowInstance::getDeleted, false).last("FOR UPDATE"));
        if (instance != null && "RUNNING".equals(instance.getStatus())) advance(instance);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void failPendingExecution(String instanceId, String errorMessage) {
        AgentWorkflowInstance instance = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getId, instanceId).eq(AgentWorkflowInstance::getDeleted, false).last("FOR UPDATE"));
        if (instance == null || !"RUNNING".equals(instance.getStatus())) return;
        instance.setStatus("FAILED");
        instance.setErrorMessage(StringUtils.defaultIfBlank(errorMessage, "后台执行任务连续失败"));
        instance.setCompletedAt(System.currentTimeMillis());
        instanceService.updateById(instance);
        sseHub.publish(instance.getId(), "run.failed", instance);
        callbackService.recordTerminal(instance);
    }

    @Override @Transactional(rollbackFor = Exception.class) public void terminate(String instanceId, String userId) {
        AgentWorkflowInstance instance = ownedForUpdate(instanceId, userId);
        if ("COMPLETED".equals(instance.getStatus()) || "TERMINATED".equals(instance.getStatus()) || "TIMED_OUT".equals(instance.getStatus())) throw new ServerException(409, "流程已结束");
        instance.setStatus("TERMINATED"); instance.setCompletedAt(System.currentTimeMillis()); instanceService.updateById(instance);
        callbackService.recordTerminal(instance);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void updateVariables(String instanceId, Map<String, Object> variables, String userId) {
        AgentWorkflowInstance instance = owned(instanceId, userId);
        if (!"RUNNING".equals(instance.getStatus()) && !"WAITING_USER".equals(instance.getStatus()) && !"FAILED".equals(instance.getStatus()))
            throw new ServerException(409, "当前流程状态不允许修改变量");
        if (variables == null || variables.isEmpty()) return;
        Map<String,Object> current = variables(instance);
        for (Map.Entry<String,Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (StringUtils.isBlank(key) || key.startsWith("_")) continue;
            if (entry.getValue() == null) current.remove(key);
            else current.put(key, entry.getValue());
        }
        instance.setVariables(JSON.toJSONString(current));
        instanceService.updateById(instance);
        sseHub.publish(instance.getId(), "variables.updated", instance);
    }

    // ── 图遍历执行引擎 ───────────────────────────────────────

    /**
     * 从当前节点开始，沿图遍历执行工作流。
     * <p>执行语义：每次调用推进一个节点。节点完成后递归调用自身继续推进。
     * 遇到人工操作或 MCP 审批时暂停，等待 answer() 回调后继续。</p>
     */
    private void advance(AgentWorkflowInstance instance) {
        if (!"RUNNING".equals(instance.getStatus())) return;

        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        Map<String, JSONObject> nodeMap = buildNodeMap(version.getNodes());
        Map<String, List<JSONObject>> adj = WorkflowDefinitionValidator.buildAdjacency(version.getEdges());
        Map<String,Object> variables = variables(instance);

        // 确定要执行的节点
        String nodeId = instance.getCurrentNodeId();
        if (nodeId == null) {
            nodeId = findStartNode(nodeMap);
        }

        // 循环执行直到暂停或完成
        while (nodeId != null && "RUNNING".equals(instance.getStatus())) {
            JSONObject definition = nodeMap.get(nodeId);
            if (definition == null) { failCurrentNode(instance, nodeId, "节点定义不存在: " + nodeId); return; }

            // 获取或创建节点实例
            AgentWorkflowNodeInstance history = getOrCreateNodeInstance(instance, nodeId, definition);

            // 已完成的节点跳过
            if ("COMPLETED".equals(history.getStatus())) {
                nodeId = findNextNodeIdFromGraph(adj, nodeId, nodeMap, variables, history, instance);
                continue;
            }

            // 执行节点
            instance.setCurrentNodeId(nodeId); instanceService.updateById(instance);
            executeNode(instance, history, definition, variables);

            if (!"RUNNING".equals(instance.getStatus())) return;

            // 节点完成，找下一个节点
            nodeId = findNextNodeIdFromGraph(adj, nodeId, nodeMap, variables, history, instance);
        }

        // 只有实际到达结束节点才能完成实例；发布校验会保证所有路径最终可到达结束节点。
        if (nodeId == null && "RUNNING".equals(instance.getStatus())) {
            JSONObject terminal = nodeMap.get(instance.getCurrentNodeId());
            if (terminal != null && "end".equals(terminal.getString("type"))) {
                instance.setStatus("COMPLETED"); instance.setCurrentNodeId(null);
                instance.setCompletedAt(System.currentTimeMillis()); instanceService.updateById(instance);
                sseHub.publish(instance.getId(), "run.completed", instance);
                callbackService.recordTerminal(instance);
            } else {
                instance.setStatus("FAILED");
                instance.setErrorMessage("流程未到达结束节点");
                instanceService.updateById(instance);
                sseHub.publish(instance.getId(), "run.failed", instance);
                callbackService.recordTerminal(instance);
            }
        }
    }

    private void executeNode(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node, JSONObject definition, Map<String,Object> variables) {
        node.setStatus("RUNNING"); node.setStartedAt(System.currentTimeMillis()); node.setInputData(sensitiveDataSanitizer.sanitizeJson(JSON.toJSONString(variables))); nodeService.updateById(node);
        String type = node.getNodeType();
        try {
            if ("start".equals(type) || "end".equals(type)) { completeNode(node, null); return; }
            if ("human".equals(type)) { waitForHuman(instance, node, definition, variables, false); return; }
            if ("mcp".equals(type)) {
                JSONObject interaction = StringUtils.isBlank(node.getInteractionConfig()) ? null : JSONObject.parseObject(node.getInteractionConfig());
                if (interaction != null && interaction.containsKey("pendingAnswer")) {
                    resumeMcpApproval(instance, node, interaction, readPendingAnswer(interaction), instance.getUserId());
                } else waitForHuman(instance, node, definition, variables, true);
                return;
            }
            if ("agent".equals(type)) {
                JSONObject interaction = StringUtils.isBlank(node.getInteractionConfig()) ? null : JSONObject.parseObject(node.getInteractionConfig());
                if (interaction != null && "agent".equals(interaction.getString("source")) && interaction.containsKey("pendingAnswer")) {
                    Object pending = interaction.get("pendingAnswer");
                    Map<String,Object> answer = pending instanceof Map ? new LinkedHashMap<String,Object>((Map<String,Object>) pending)
                            : JSONObject.parseObject(JSON.toJSONString(pending), Map.class);
                    resumeAgentInteraction(instance, node, interaction, answer == null ? new LinkedHashMap<String,Object>() : answer, instance.getUserId());
                    return;
                }
                AgentChatDto request = new AgentChatDto(); request.setAgentId(definition.getString("resourceId")); request.setUserId(instance.getUserId());
                request.setMessage(WorkflowVariableRenderer.render(definition.getString("prompt"), variables)); request.setTemporary(true);
                AgentMessageVo response = chatService.chat(request);
                // 普通聊天服务遇到需要确认的 MCP 调用时会返回 interaction 消息。工作流必须
                // 将它转换为当前节点的暂停状态，不能把“请确认”误当作 Agent 的最终回答。
                if (isAgentInteraction(response)) {
                    waitForAgentInteraction(instance, node, definition, response);
                    return;
                }
                String output = response == null ? "" : response.getContent();
                applyStateMapping(definition, output, variables);
                instance.setVariables(JSON.toJSONString(variables)); instanceService.updateById(instance); completeNode(node, output); return;
            }
            throw new ServerException(422, "未知工作流节点类型: " + type);
        } catch (Exception e) { fail(instance, node, e.getMessage()); }
    }

    /**
     * 根据当前节点类型和输出，找到下一个要执行的节点 ID。
     * <ul>
     *   <li>节点完成后按出边 condition 表达式求值选择走向</li>
     *   <li>循环边：累加迭代计数器，超限则标记失败</li>
     * </ul>
     */
    private String findNextNodeIdFromGraph(Map<String, List<JSONObject>> adj, String currentNodeId,
                                           Map<String, JSONObject> nodeMap, Map<String,Object> variables,
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
     * @return 下一个节点 ID，如果循环超限返回 null
     */
    private String handleLoopEdge(JSONObject edge, String targetId, Map<String,Object> variables,
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
        instance.setVariables(JSON.toJSONString(variables)); instanceService.updateById(instance);

        return targetId;
    }

    /** 重置循环体内节点为 PENDING，以便循环回跳后重新执行。 */
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
                n.setStatus("PENDING"); n.setOutputData(null); n.setErrorMessage(null);
                n.setStartedAt(null); n.setCompletedAt(null);
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

    /** DFS 探测回跳边：目标节点当前仍在 DFS 栈中（是源节点的祖先）即为回跳。 */
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

    /** 简化实现：收集拓扑序在 entry 和 backSource 之间的所有节点（含两端）。 */
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

    private AgentWorkflowNodeInstance getOrCreateNodeInstance(AgentWorkflowInstance instance, String nodeId, JSONObject definition) {
        AgentWorkflowNodeInstance existing = nodeService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                .eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId()).eq(AgentWorkflowNodeInstance::getNodeId, nodeId));
        if (existing != null) return existing;
        AgentWorkflowNodeInstance newNode = new AgentWorkflowNodeInstance();
        newNode.setInstanceId(instance.getId()); newNode.setNodeId(nodeId);
        newNode.setNodeType(definition.getString("type")); newNode.setStatus("PENDING"); newNode.setRetryCount(0);
        nodeService.save(newNode);
        return newNode;
    }

    private String findNextNodeId(AgentWorkflowInstance instance, AgentWorkflowNodeInstance completedNode) {
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        Map<String, List<JSONObject>> adj = WorkflowDefinitionValidator.buildAdjacency(version.getEdges());
        Map<String, JSONObject> nodeMap = buildNodeMap(version.getNodes());
        Map<String,Object> vars = variables(instance);
        return findNextNodeIdFromGraph(adj, completedNode.getNodeId(), nodeMap, vars, completedNode, instance);
    }

    private int getTopologicalOrder(String nodeId, Map<String, JSONObject> nodeMap) {
        int idx = 0;
        for (String nid : nodeMap.keySet()) {
            if (nid.equals(nodeId)) return idx;
            idx++;
        }
        return -1;
    }

    private void failCurrentNode(AgentWorkflowInstance instance, String nodeId, String error) {
        AgentWorkflowNodeInstance node = nodeService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                .eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId()).eq(AgentWorkflowNodeInstance::getNodeId, nodeId));
        if (node != null) fail(instance, node, error);
        else {
            instance.setStatus("FAILED"); instance.setErrorMessage(error);
            instanceService.updateById(instance); sseHub.publish(instance.getId(), "run.failed", null);
            callbackService.recordTerminal(instance);
        }
    }

    private AgentWorkflowNodeInstance currentNodeById(AgentWorkflowInstance instance, String nodeId) {
        return nodeService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                .eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId()).eq(AgentWorkflowNodeInstance::getNodeId, nodeId));
    }

    private void waitForHuman(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node, JSONObject definition, Map<String,Object> variables, boolean mcp) {
        JSONObject config = new JSONObject(); config.put("type", mcp ? "mcp_tool_approval" : "group"); config.put("question", definition.getString("question")); config.put("outputKey", definition.getString("outputKey")); config.put("internalKey", definition.getString("internalKey"));
        if (mcp) { config.put("toolId", definition.getString("resourceId")); config.put("toolName", definition.getString("toolName")); config.put("agentId", resolveMcpAgentId(instance)); config.put("arguments", WorkflowVariableRenderer.render(definition.getString("argumentsTemplate"), variables)); }
        else config.put("questions", definition.getJSONArray("questions"));
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

    /** 将 Agent 聊天服务产生的 MCP 确认或 ask_user 交互挂接到当前工作流节点。 */
    private void waitForAgentInteraction(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node,
                                         JSONObject definition, AgentMessageVo response) {
        JSONObject config = JSONObject.parseObject(response.getQuestionConfig());
        config.put("agentId", definition.getString("resourceId"));
        config.put("source", "agent");
        config.put("agentConversationId", response.getConversationId());
        config.put("agentApprovalMessageId", response.getId());
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

    private boolean isAgentInteraction(AgentMessageVo response) {
        if (response == null || StringUtils.isBlank(response.getQuestionConfig())) return false;
        try {
            JSONObject config = JSONObject.parseObject(response.getQuestionConfig());
            return "interaction".equals(response.getMessageType()) && config != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> readPendingAnswer(JSONObject config) {
        Object pending = config.get("pendingAnswer");
        if (pending instanceof Map) return new LinkedHashMap<String,Object>((Map<String,Object>) pending);
        Map<String,Object> parsed = pending == null ? null : JSONObject.parseObject(JSON.toJSONString(pending), Map.class);
        return parsed == null ? new LinkedHashMap<String,Object>() : parsed;
    }

    private void resumeMcpApproval(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node,
                                   JSONObject config, Map<String,Object> answer, String userId) {
        String decision = String.valueOf(answer.get("decision"));
        if ("reject".equals(decision)) { fail(instance, node, "用户拒绝执行 MCP 工具"); return; }
        Map<String,Object> args = readToolArguments(config);
        String agentId = config.getString("agentId");
        if (StringUtils.isBlank(agentId)) {
            JSONObject definition = currentDefinition(instance, node);
            agentId = definition == null ? null : definition.getString("resourceId");
        }
        if (StringUtils.isBlank(agentId)) agentId = resolveMcpAgentId(instance);
        ToolExecutionResult result = toolWorkflow.executeWorkflowApprovedMcpTool(config.getString("toolId"), config.getString("toolName"), args,
                instance.getId(), userId, agentId, "allow_10m".equals(decision),
                "workflow:" + instance.getId() + ":node:" + node.getNodeId());
        if (result.getStatus() != null && result.getStatus() != 0) { fail(instance, node, result.getErrorMsg()); return; }
        Map<String,Object> variables = variables(instance);
        JSONObject definition = currentDefinition(instance, node);
        if (definition != null) applyStateMapping(definition, result, variables);
        instance.setVariables(JSON.toJSONString(variables)); instanceService.updateById(instance);
        completeNode(node, JSON.toJSONString(result));
    }

    /**
     * 通过普通 Agent 的交互恢复链路继续执行。该链路会更新原审批审计、把工具结果回填给模型，
     * 并允许模型基于结果产出最终回答或发起下一次交互。
     */
    private void resumeAgentInteraction(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node,
                                        JSONObject config, Map<String,Object> answer, String userId) {
        String conversationId = config.getString("agentConversationId");
        String parentMessageId = config.getString("agentApprovalMessageId");
        if (StringUtils.isBlank(conversationId) || StringUtils.isBlank(parentMessageId))
            throw new ServerException(409, "Agent 交互上下文已失效，请重试节点");
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
            @Override public void onMessage(String ignored, String chunk) { }
            @Override public void onReasoning(String ignored, String chunk) { }
            @Override public void onToolCall(String ignored, String toolCallJson) { }
            @Override public void onQuestion(String ignored, String runId, AgentMessageVo question) { nextQuestion[0] = question; }
            @Override public void onDone(String ignored, String messageId, ModelStreamResponse response) { output[0] = response == null ? "" : response.getContent(); }
            @Override public void onError(int code, String message) { error[0] = message; }
            @Override public boolean isClosed() { return false; }
        });
        if (StringUtils.isNotBlank(error[0])) throw new ServerException(502, error[0]);
        JSONObject definition = currentDefinition(instance, node);
        if (nextQuestion[0] != null) {
            waitForAgentInteraction(instance, node, definition, nextQuestion[0]);
            return;
        }
        Map<String,Object> variables = variables(instance);
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

    private boolean isMcpToolApprovalConfig(JSONObject config) {
        return config != null && "mcp_tool_approval".equals(config.getString("approvalType"));
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> readToolArguments(JSONObject config) {
        Object value = config.get("arguments");
        if (value instanceof Map) return new LinkedHashMap<String,Object>((Map<String,Object>) value);
        if (value == null) return new LinkedHashMap<String,Object>();
        Map<String,Object> parsed = JSONObject.parseObject(String.valueOf(value), Map.class);
        return parsed == null ? new LinkedHashMap<String,Object>() : parsed;
    }
    private void completeNode(AgentWorkflowNodeInstance node, String output) { node.setStatus("COMPLETED"); node.setOutputData(sensitiveDataSanitizer.sanitizeJson(output)); node.setCompletedAt(System.currentTimeMillis()); node.setInteractionConfig(null); nodeService.updateById(node); sseHub.publish(node.getInstanceId(), "node.completed", node); }
    private void fail(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node, String error) { node.setStatus("FAILED"); node.setErrorMessage(StringUtils.defaultIfBlank(error, "节点执行失败")); node.setCompletedAt(System.currentTimeMillis()); nodeService.updateById(node); instance.setStatus("FAILED"); instance.setErrorMessage(node.getErrorMessage()); instanceService.updateById(instance); sseHub.publish(instance.getId(), "run.failed", node); callbackService.recordTerminal(instance); }
    private AgentWorkflow requiredWorkflow(String id) { AgentWorkflow value = workflowService.getById(id); if (value == null || Boolean.TRUE.equals(value.getDeleted())) throw new ServerException(404, "工作流不存在"); return value; }
    private AgentWorkflowInstance owned(String id, String userId) { AgentWorkflowInstance value = instanceService.getById(id); if (value == null || Boolean.TRUE.equals(value.getDeleted())) throw new ServerException(404, "流程实例不存在"); if (!StringUtils.equals(value.getUserId(), userId) && !isAdministrator(userId)) throw new ServerException(403, "无权操作其他用户的流程实例"); return value; }
    /** 人工回答、终止等状态变化持有实例行锁，避免与 SLA 超时任务相互覆盖状态。 */
    private AgentWorkflowInstance ownedForUpdate(String id, String userId) {
        AgentWorkflowInstance value = instanceService.getOne(Wrappers.lambdaQuery(AgentWorkflowInstance.class)
                .eq(AgentWorkflowInstance::getId, id).eq(AgentWorkflowInstance::getDeleted, false).last("FOR UPDATE"));
        if (value == null) throw new ServerException(404, "流程实例不存在");
        if (!StringUtils.equals(value.getUserId(), userId) && !isAdministrator(userId)) throw new ServerException(403, "无权操作其他用户的流程实例");
        return value;
    }
    /** root 角色可处理业务服务账号创建的异常实例，普通用户仍严格隔离。 */
    @Override public boolean isAdministrator(String userId) {
        if (StringUtils.isBlank(userId)) return false;
        List<UserRole> bindings = userRoleService.list(Wrappers.lambdaQuery(UserRole.class)
                .eq(UserRole::getUserId, userId).eq(UserRole::getDeleted, false));
        if (bindings == null || bindings.isEmpty()) return false;
        Set<String> roleIds = new HashSet<String>();
        for (UserRole binding : bindings) if (StringUtils.isNotBlank(binding.getRoleId())) roleIds.add(binding.getRoleId());
        if (roleIds.isEmpty()) return false;
        return roleService.count(Wrappers.lambdaQuery(Role.class).in(Role::getId, roleIds)
                .eq(Role::getName, "root").eq(Role::getDeleted, false)) > 0;
    }
    private AgentWorkflowNodeInstance currentNode(AgentWorkflowInstance instance) { AgentWorkflowNodeInstance value = nodeService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class).eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId()).eq(AgentWorkflowNodeInstance::getNodeId, instance.getCurrentNodeId())); if (value == null) throw new ServerException(409, "当前节点不存在"); return value; }
    private Map<String,Object> variables(AgentWorkflowInstance instance) { Map<String,Object> value = StringUtils.isBlank(instance.getVariables()) ? null : JSONObject.parseObject(instance.getVariables(), Map.class); return value == null ? new LinkedHashMap<String,Object>() : new LinkedHashMap<String,Object>(value); }

    // ── 共享状态写回 ─────────────────────────────────────────

    /**
     * 将节点输出写入共享状态。
     * <p>优先使用节点 stateMapping（JSON 对象：目标键 → $output | $json.<path>）；
     * 未配置时兼容旧字段 outputKey（将整个输出写入该键）。
     * 另支持 internalKey（内部变量，需带 _ 前缀）：始终写入但不进共享状态面板，
     * 可被后续节点以 ${_变量名} 引用。</p>
     */
    private void applyStateMapping(JSONObject definition, Object output, Map<String,Object> variables) {
        if (definition == null) return;
        String stateMapping = definition.getString("stateMapping");
        boolean mapped = false;
        if (StringUtils.isNotBlank(stateMapping)) {
            try {
                JSONObject mapping = JSONObject.parseObject(stateMapping);
                for (Map.Entry<String,Object> entry : mapping.entrySet()) {
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

    private Object resolveMappingExpr(String expr, Object output) {
        String trimmed = expr == null ? "" : expr.trim();
        if ("$output".equals(trimmed)) return output;
        if (trimmed.startsWith("$json.")) return extractJsonPath(output, trimmed.substring("$json.".length()));
        return expr; // 字面量
    }

    /** 从输出中按点号路径提取字段；输出为字符串时先尝试 JSON 解析。 */
    private Object extractJsonPath(Object output, String path) {
        Object current = output;
        if (current instanceof String) {
            try { current = JSON.parse((String) current); } catch (Exception e) { return null; }
        }
        for (String segment : path.split("\\.")) {
            if (segment.isEmpty()) return null;
            if (current instanceof Map) {
                current = ((Map<?,?>) current).get(segment);
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

    private JSONObject currentDefinition(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node) {
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        if (version == null) return null;
        return buildNodeMap(version.getNodes()).get(node.getNodeId());
    }

    /** 解析工作流绑定的 Agent 定义 ID。 */
    private String resolveMcpAgentId(AgentWorkflowInstance instance) {
        AgentWorkflow workflow = workflowService.getById(instance.getWorkflowId());
        if (workflow != null && StringUtils.isNotBlank(workflow.getAgentDefinitionId())) return workflow.getAgentDefinitionId();
        return "workflow:" + instance.getWorkflowId();
    }

    private Map<String, JSONObject> buildNodeMap(String nodesText) {
        Map<String, JSONObject> map = new LinkedHashMap<String, JSONObject>();
        if (StringUtils.isBlank(nodesText)) return map;
        for (Object value : com.alibaba.fastjson2.JSONArray.parseArray(nodesText)) {
            JSONObject node = (JSONObject) value;
            map.put(node.getString("id"), node);
        }
        return map;
    }

    private String findStartNode(Map<String, JSONObject> nodeMap) {
        for (Map.Entry<String, JSONObject> e : nodeMap.entrySet()) {
            if ("start".equals(e.getValue().getString("type"))) return e.getKey();
        }
        return null;
    }
}
