package com.aether.agent.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.AdminPreferenceExtractionService;
import com.aether.sys.entity.AdminPreference;
import com.aether.sys.service.AdminPreferenceService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AdminPreferenceExtractionServiceImpl implements AdminPreferenceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(AdminPreferenceExtractionServiceImpl.class);
    private static final int STATUS_ENABLED = 1;
    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.80");
    private static final String EXTRACTION_PROMPT =
            "你是后台用户长期偏好抽取器。请从本轮用户消息和助手回答中提取稳定、长期、可复用的用户偏好。" +
            "只抽取明确表达的偏好，例如语言、格式、风格、技术栈、工作方式、常用约束。" +
            "不要抽取一次性任务、临时问题、密码、Token、密钥、隐私凭据或未经确认的推断。" +
            "只返回 JSON 数组，每项格式为 {\"category\":\"...\",\"content\":\"...\",\"confidence\":0.0-1.0}。" +
            "如果没有偏好，返回 []。";

    private final ModelClientFactory modelClientFactory;
    private final AdminPreferenceService adminPreferenceService;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public AdminPreferenceExtractionServiceImpl(ModelClientFactory modelClientFactory,
                                               AdminPreferenceService adminPreferenceService) {
        this.modelClientFactory = modelClientFactory;
        this.adminPreferenceService = adminPreferenceService;
    }

    @Override
    public void extractAsync(String adminId,
                             String conversationId,
                             AgentMessage userMessage,
                             AgentMessage assistantMessage,
                             AgentDefinition agent,
                             ModelProvider provider) {
        if (StringUtils.isBlank(adminId) || userMessage == null || assistantMessage == null || agent == null || provider == null) {
            return;
        }
        executor.submit(() -> extract(adminId, conversationId, userMessage, assistantMessage, agent, provider));
    }

    private void extract(String adminId,
                         String conversationId,
                         AgentMessage userMessage,
                         AgentMessage assistantMessage,
                         AgentDefinition agent,
                         ModelProvider provider) {
        try {
            ModelChatRequest request = new ModelChatRequest();
            request.setAgent(agent);
            request.setProvider(provider);
            request.setTools(null);
            request.setMessages(Arrays.asList(
                    new ModelChatMessage("system", EXTRACTION_PROMPT),
                    new ModelChatMessage("user", "用户消息：\n" + StringUtils.defaultString(userMessage.getContent()) +
                            "\n\n助手回答：\n" + StringUtils.defaultString(assistantMessage.getContent()))
            ));
            ModelClient client = modelClientFactory.getClient(provider);
            ModelChatResponse response = client.chat(request);
            JSONArray items = parseArray(response.getContent());
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                String content = StringUtils.trimToNull(item.getString("content"));
                if (StringUtils.isBlank(content) || content.length() > 512) {
                    continue;
                }
                String category = StringUtils.defaultIfBlank(item.getString("category"), "general");
                BigDecimal confidence = item.getBigDecimal("confidence");
                if (confidence == null) {
                    confidence = DEFAULT_CONFIDENCE;
                }
                if (confidence.compareTo(new BigDecimal("0.60")) < 0) {
                    continue;
                }
                boolean exists = adminPreferenceService.count(Wrappers.lambdaQuery(AdminPreference.class)
                        .eq(AdminPreference::getAdminId, adminId)
                        .eq(AdminPreference::getContent, content)
                        .eq(AdminPreference::getDeleted, false)) > 0;
                if (exists) {
                    continue;
                }
                AdminPreference preference = new AdminPreference();
                preference.setAdminId(adminId);
                preference.setCategory(category);
                preference.setContent(content);
                preference.setSourceConversationId(conversationId);
                preference.setSourceMessageId(assistantMessage.getId());
                preference.setConfidence(confidence);
                preference.setStatus(STATUS_ENABLED);
                adminPreferenceService.save(preference);
            }
        } catch (Exception e) {
            log.warn("用户偏好异步提取失败: adminId={}, conversationId={}", adminId, conversationId, e);
        }
    }

    private JSONArray parseArray(String content) {
        if (StringUtils.isBlank(content)) {
            return new JSONArray();
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstNewLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewLine >= 0 && lastFence > firstNewLine) {
                text = text.substring(firstNewLine + 1, lastFence).trim();
            }
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return JSONArray.parseArray(text);
    }
}
