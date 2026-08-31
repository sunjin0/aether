package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 持久化聊天运行记录及其状态变化。
 */
@Service
public class ChatRunService {
    private static final int RUN_STATUS_FAILED = 1;
    private static final java.util.regex.Pattern SENSITIVE_FIELD = java.util.regex.Pattern.compile(
            "(?i)(\\\"?(?:password|passwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|private[_-]?key)\\\"?\\s*[:=]\\s*\\\"?)([^\\\"\\s,}]+)");
    private final AgentRunService agentRunService;
    @Autowired(required = false)
    private AuditDataProtectionService auditDataProtectionService;

    /**
     * 创建 {@code ChatRunService} 实例。
     */
    public ChatRunService(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    /** 查询同一调用方请求创建的未删除运行记录。 */
    public AgentRun findByRequest(String agentId, String userId, String requestId) {
        if (org.apache.commons.lang3.StringUtils.isAnyBlank(agentId, userId, requestId)) return null;
        return agentRunService.getOne(Wrappers.lambdaQuery(AgentRun.class)
                .eq(AgentRun::getAgentDefinitionId, agentId)
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getRequestId, requestId)
                .eq(AgentRun::getDeleted, false)
                .orderByDesc(AgentRun::getCreatedAt).last("limit 1"), false);
    }

    /**
     * 创建当前请求。
     */
    public String create(AgentDefinition agent, ModelProvider provider, String userId, String conversationId,
                         String messageId, String input, ModelChatResponse response, long latencyMs,
                         Integer status, String errorMsg) {
        AgentRun run = new AgentRun();
        run.setApplicationId(agent.getApplicationId());
        run.setAgentDefinitionId(agent.getId());
        run.setConversationId(conversationId);
        run.setMessageId(messageId);
        run.setUserId(userId);
        // agent_run input/output columns are TEXT: preserve both sides in full for audit.
        run.setInputContent(protect(redact(input)));
        run.setOutputContent(response == null ? null : protect(redact(response.getContent())));
        run.setRawResponse(response == null ? null : protect(redact(response.getRawResponse())));
        run.setModel(response == null ? agent.getModel() : response.getModel());
        run.setModelProviderId(provider.getId());
        if (response != null) {
            run.setPromptTokens(response.getPromptTokens());
            run.setCompletionTokens(response.getCompletionTokens());
            run.setTotalTokens(response.getTotalTokens());
        }
        run.setLatencyMs((int) latencyMs);
        run.setStatus(status);
        run.setErrorMsg(errorMsg);
        // 普通聊天快照携带调用方请求标识，作为数据库级幂等约束的查询条件。
        String requestId = null;
        if (org.apache.commons.lang3.StringUtils.isNotBlank(input)) {
            try {
                JSONObject snapshot = JSON.parseObject(input);
                if (snapshot != null) {
                    requestId = snapshot.getString("requestId");
                    run.setRequestId(requestId);
                }
            } catch (RuntimeException ignored) {
                // 工具轮次输入可能是纯文本，无法解析为请求快照时按无幂等键处理。
            }
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(requestId)) {
            AgentRun existing = findByRequest(agent.getId(), userId, requestId);
            if (existing != null) return existing.getId();
        }
        try {
            agentRunService.save(run);
        } catch (DataIntegrityViolationException duplicate) {
            // 多实例可能在查询和插入之间抢到同一幂等键，此时返回已创建的运行记录。
            if (org.apache.commons.lang3.StringUtils.isNotBlank(requestId)) {
                AgentRun existing = findByRequest(agent.getId(), userId, requestId);
                if (existing != null) return existing.getId();
            }
            throw duplicate;
        }
        return run.getId();
    }

    /**
     * 创建标准聊天运行并持久化请求开始时冻结的 Skill 审计快照。
     */
    public String create(AgentDefinition agent, ModelProvider provider, String userId, String conversationId,
                         String messageId, String input, ModelChatResponse response, long latencyMs,
                         Integer status, String errorMsg, String skillSnapshot) {
        String runId = create(agent, provider, userId, conversationId, messageId, input, response, latencyMs, status, errorMsg);
        if (skillSnapshot != null) {
            AgentRun update = new AgentRun();
            update.setId(runId);
            update.setSkillSnapshot(skillSnapshot);
            agentRunService.updateById(update);
        }
        return runId;
    }

    /**
     * 更新当前请求。
     */
    public void update(String runId, String messageId, ModelChatResponse response, long latencyMs,
                       Integer status, String errorMsg) {
        AgentRun run = new AgentRun();
        run.setId(runId);
        run.setMessageId(messageId);
        run.setOutputContent(response == null ? null : protect(redact(response.getContent())));
        run.setRawResponse(response == null ? null : protect(redact(response.getRawResponse())));
        if (response != null) {
            run.setModel(response.getModel());
            run.setPromptTokens(response.getPromptTokens());
            run.setCompletionTokens(response.getCompletionTokens());
            run.setTotalTokens(response.getTotalTokens());
        }
        run.setLatencyMs((int) latencyMs);
        run.setStatus(status);
        run.setErrorMsg(errorMsg);
        agentRunService.updateById(run);
    }

    /**
     * 更新同一运行的审计快照，例如工具返回后重新计算的上下文预算。
     */
    public void updateSkillSnapshot(String runId, String skillSnapshot) {
        if (runId == null || skillSnapshot == null) {
            return;
        }
        AgentRun run = new AgentRun();
        run.setId(runId);
        run.setSkillSnapshot(skillSnapshot);
        agentRunService.updateById(run);
    }

    /** 脱敏凭据类字段，同时保留字段名称以便审计定位。 */
    private String redact(String value) {
        if (value == null) return null;
        return SENSITIVE_FIELD.matcher(value).replaceAll("$1[REDACTED]");
    }

    private String protect(String value) {
        return auditDataProtectionService == null ? value : auditDataProtectionService.protect(value);
    }

    /**
     * 保存Failure。
     */
    public void saveFailure(AgentDefinition agent, ModelProvider provider, String userId, String conversationId,
                            String messageId, String input, long latencyMs, RuntimeException exception) {
        ModelChatResponse response = new ModelChatResponse();
        response.setModel(agent.getModel());
        create(agent, provider, userId, conversationId, messageId, input, response,
                latencyMs, RUN_STATUS_FAILED, exception.getMessage());
    }
}
