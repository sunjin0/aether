package com.aether.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.dto.AgentWorkflowInteractionDto;
import com.aether.agent.entity.*;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.service.*;
import com.aether.agent.tools.AgentToolWorkflow;
import com.aether.agent.vo.AgentMessageVo;
import com.aether.agent.vo.AgentWorkflowInstanceVo;
import com.aether.agent.workflow.WorkflowDefinitionValidator;
import com.aether.agent.workflow.WorkflowVariableRenderer;
import com.aether.agent.workflow.WorkflowSseHub;
import com.aether.exception.ServerException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * 顺序工作流执行器。每次状态改变均先持久化；因此进程重启后可以继续等待人工操作，
 * 成功节点不会在重试时被重复调用。
 */
@Service
public class AgentWorkflowExecutionServiceImpl implements AgentWorkflowExecutionService {
    private final AgentWorkflowService workflowService;
    private final AgentWorkflowVersionService versionService;
    private final AgentWorkflowInstanceService instanceService;
    private final AgentWorkflowNodeInstanceService nodeService;
    private final AgentChatService chatService;
    private final AgentToolWorkflow toolWorkflow;
    private final WorkflowSseHub sseHub;

    public AgentWorkflowExecutionServiceImpl(AgentWorkflowService workflowService, AgentWorkflowVersionService versionService,
            AgentWorkflowInstanceService instanceService, AgentWorkflowNodeInstanceService nodeService,
            AgentChatService chatService, AgentToolWorkflow toolWorkflow, WorkflowSseHub sseHub) {
        this.workflowService = workflowService; this.versionService = versionService; this.instanceService = instanceService;
        this.nodeService = nodeService; this.chatService = chatService; this.toolWorkflow = toolWorkflow; this.sseHub = sseHub;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowInstance start(String workflowId, Map<String, Object> variables, String userId) {
        AgentWorkflow workflow = requiredWorkflow(workflowId);
        if (!Integer.valueOf(1).equals(workflow.getStatus()) || workflow.getPublishedVersion() == null)
            throw new ServerException(422, "工作流尚未发布，不能启动");
        AgentWorkflowVersion version = versionService.getOne(Wrappers.lambdaQuery(AgentWorkflowVersion.class)
                .eq(AgentWorkflowVersion::getWorkflowId, workflowId).eq(AgentWorkflowVersion::getVersionNo, workflow.getPublishedVersion()));
        if (version == null) throw new ServerException(409, "工作流发布版本不存在");
        AgentWorkflowInstance instance = new AgentWorkflowInstance();
        instance.setWorkflowId(workflowId); instance.setWorkflowVersionId(version.getId()); instance.setUserId(userId);
        instance.setStatus("RUNNING"); instance.setVariables(JSON.toJSONString(variables == null ? new LinkedHashMap<String,Object>() : variables));
        instance.setStartedAt(System.currentTimeMillis()); instanceService.save(instance);
        advance(instance);
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
        AgentWorkflowInstance instance = owned(instanceId, userId);
        if (!"WAITING_USER".equals(instance.getStatus())) throw new ServerException(409, "当前流程不在等待用户操作");
        AgentWorkflowNodeInstance node = currentNode(instance);
        Map<String,Object> answer = dto == null || dto.getAnswer() == null ? new LinkedHashMap<String,Object>() : dto.getAnswer();
        JSONObject config = StringUtils.isBlank(node.getInteractionConfig()) ? new JSONObject() : JSONObject.parseObject(node.getInteractionConfig());
        if ("mcp".equals(node.getNodeType())) {
            String decision = String.valueOf(answer.get("decision"));
            if ("reject".equals(decision)) { fail(instance, node, "用户拒绝执行 MCP 工具"); return; }
            Map<String,Object> args = JSONObject.parseObject(config.getString("arguments"), Map.class);
            String agentId = config.getString("agentId");
            if (StringUtils.isBlank(agentId)) agentId = resolveMcpAgentId(instance);
            ToolExecutionResult result = toolWorkflow.executeWorkflowApprovedMcpTool(config.getString("toolId"), config.getString("toolName"), args,
                    instance.getId(), userId, agentId, "allow_10m".equals(decision));
            if (result.getStatus() != null && result.getStatus() != 0) { fail(instance, node, result.getErrorMsg()); return; }
            completeNode(node, JSON.toJSONString(result));
        } else {
            Map<String,Object> variables = variables(instance);
            String outputKey = config.getString("outputKey"); if (StringUtils.isNotBlank(outputKey)) variables.put(outputKey, answer);
            instance.setVariables(JSON.toJSONString(variables)); completeNode(node, JSON.toJSONString(answer));
        }
        instance.setStatus("RUNNING"); instanceService.updateById(instance); advance(instance);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void retry(String instanceId, String userId) {
        AgentWorkflowInstance instance = owned(instanceId, userId);
        if (!"FAILED".equals(instance.getStatus())) throw new ServerException(409, "只有失败待重试的流程可以重试");
        AgentWorkflowNodeInstance node = currentNode(instance); node.setStatus("PENDING"); node.setErrorMessage(null);
        node.setRetryCount((node.getRetryCount() == null ? 0 : node.getRetryCount()) + 1); nodeService.updateById(node);
        instance.setStatus("RUNNING"); instance.setErrorMessage(null); instanceService.updateById(instance); advance(instance);
    }

    @Override public void terminate(String instanceId, String userId) {
        AgentWorkflowInstance instance = owned(instanceId, userId);
        if ("COMPLETED".equals(instance.getStatus()) || "TERMINATED".equals(instance.getStatus())) throw new ServerException(409, "流程已结束");
        instance.setStatus("TERMINATED"); instance.setCompletedAt(System.currentTimeMillis()); instanceService.updateById(instance);
    }

    private void advance(AgentWorkflowInstance instance) {
        if (!"RUNNING".equals(instance.getStatus())) return;
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        List<JSONObject> nodes = WorkflowDefinitionValidator.orderedNodes(version.getNodes(), version.getEdges());
        Map<String,Object> variables = variables(instance);
        for (JSONObject definition : nodes) {
            String nodeId = definition.getString("id"); AgentWorkflowNodeInstance history = nodeService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                    .eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId()).eq(AgentWorkflowNodeInstance::getNodeId, nodeId));
            if (history != null && "COMPLETED".equals(history.getStatus())) continue;
            if (history == null) { history = new AgentWorkflowNodeInstance(); history.setInstanceId(instance.getId()); history.setNodeId(nodeId); history.setNodeType(definition.getString("type")); history.setStatus("PENDING"); history.setRetryCount(0); nodeService.save(history); }
            instance.setCurrentNodeId(nodeId); instanceService.updateById(instance);
            executeNode(instance, history, definition, variables);
            if (!"RUNNING".equals(instance.getStatus())) return;
        }
        instance.setStatus("COMPLETED"); instance.setCurrentNodeId(null); instance.setCompletedAt(System.currentTimeMillis()); instanceService.updateById(instance); sseHub.publish(instance.getId(), "run.completed", instance);
    }

    private void executeNode(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node, JSONObject definition, Map<String,Object> variables) {
        node.setStatus("RUNNING"); node.setStartedAt(System.currentTimeMillis()); node.setInputData(JSON.toJSONString(variables)); nodeService.updateById(node);
        String type = node.getNodeType();
        try {
            if ("start".equals(type) || "end".equals(type)) { completeNode(node, null); return; }
            if ("human".equals(type)) { waitForHuman(instance, node, definition, variables, false); return; }
            if ("mcp".equals(type)) { waitForHuman(instance, node, definition, variables, true); return; }
            if ("agent".equals(type)) {
                AgentChatDto request = new AgentChatDto(); request.setAgentId(definition.getString("resourceId")); request.setUserId(instance.getUserId());
                request.setMessage(WorkflowVariableRenderer.render(definition.getString("prompt"), variables)); request.setTemporary(true);
                AgentMessageVo response = chatService.chat(request); String output = response == null ? "" : response.getContent();
                String outputKey = definition.getString("outputKey"); if (StringUtils.isNotBlank(outputKey)) variables.put(outputKey, output);
                instance.setVariables(JSON.toJSONString(variables)); instanceService.updateById(instance); completeNode(node, output); return;
            }
            throw new ServerException(422, "未知工作流节点类型: " + type);
        } catch (Exception e) { fail(instance, node, e.getMessage()); }
    }

    private void waitForHuman(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node, JSONObject definition, Map<String,Object> variables, boolean mcp) {
        JSONObject config = new JSONObject(); config.put("type", mcp ? "mcp_tool_approval" : "group"); config.put("question", definition.getString("question")); config.put("outputKey", definition.getString("outputKey"));
        if (mcp) { config.put("toolId", definition.getString("resourceId")); config.put("toolName", definition.getString("toolName")); config.put("agentId", resolveMcpAgentId(instance)); config.put("arguments", WorkflowVariableRenderer.render(definition.getString("argumentsTemplate"), variables)); }
        else config.put("questions", definition.getJSONArray("questions"));
        node.setStatus("WAITING_USER"); node.setInteractionConfig(config.toJSONString()); nodeService.updateById(node);
        instance.setStatus("WAITING_USER"); instanceService.updateById(instance); sseHub.publish(instance.getId(), mcp ? "tool.approval.required" : "ask_user.required", node);
    }
    private void completeNode(AgentWorkflowNodeInstance node, String output) { node.setStatus("COMPLETED"); node.setOutputData(output); node.setCompletedAt(System.currentTimeMillis()); node.setInteractionConfig(null); nodeService.updateById(node); sseHub.publish(node.getInstanceId(), "node.completed", node); }
    private void fail(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node, String error) { node.setStatus("FAILED"); node.setErrorMessage(StringUtils.defaultIfBlank(error, "节点执行失败")); node.setCompletedAt(System.currentTimeMillis()); nodeService.updateById(node); instance.setStatus("FAILED"); instance.setErrorMessage(node.getErrorMessage()); instanceService.updateById(instance); sseHub.publish(instance.getId(), "run.failed", node); }
    private AgentWorkflow requiredWorkflow(String id) { AgentWorkflow value = workflowService.getById(id); if (value == null || Boolean.TRUE.equals(value.getDeleted())) throw new ServerException(404, "工作流不存在"); return value; }
    private AgentWorkflowInstance owned(String id, String userId) { AgentWorkflowInstance value = instanceService.getById(id); if (value == null || Boolean.TRUE.equals(value.getDeleted())) throw new ServerException(404, "流程实例不存在"); if (!StringUtils.equals(value.getUserId(), userId)) throw new ServerException(403, "无权操作其他用户的流程实例"); return value; }
    private AgentWorkflowNodeInstance currentNode(AgentWorkflowInstance instance) { AgentWorkflowNodeInstance value = nodeService.getOne(Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class).eq(AgentWorkflowNodeInstance::getInstanceId, instance.getId()).eq(AgentWorkflowNodeInstance::getNodeId, instance.getCurrentNodeId())); if (value == null) throw new ServerException(409, "当前节点不存在"); return value; }
    private Map<String,Object> variables(AgentWorkflowInstance instance) { Map<String,Object> value = StringUtils.isBlank(instance.getVariables()) ? null : JSONObject.parseObject(instance.getVariables(), Map.class); return value == null ? new LinkedHashMap<String,Object>() : new LinkedHashMap<String,Object>(value); }
    /** 工作流 MCP 节点没有独立 Agent 上下文；优先沿用工作流绑定的 Agent，否则使用工作流自身的委派标识。 */
    private String resolveMcpAgentId(AgentWorkflowInstance instance) {
        AgentWorkflow workflow = workflowService.getById(instance.getWorkflowId());
        if (workflow != null && StringUtils.isNotBlank(workflow.getAgentDefinitionId())) return workflow.getAgentDefinitionId();
        return "workflow:" + instance.getWorkflowId();
    }
}
