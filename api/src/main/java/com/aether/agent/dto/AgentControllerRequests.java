package com.aether.agent.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Request models used by Agent HTTP controllers. */
public final class AgentControllerRequests {
    private AgentControllerRequests() { }

    @Data @ApiModel("智能体定义列表请求") public static class DefinitionList {
        @ApiModelProperty("页码") private Long current;
        @ApiModelProperty("每页数量") private Long pageSize;
        private String name; private String code; private Integer status; private String modelId; private String applicationId;
    }
    @Data @ApiModel("状态更新请求") public static class Status { @ApiModelProperty("状态") private Integer status; }
    @Data @ApiModel("会话列表请求") public static class ConversationList {
        private Long current; private Long pageSize; private String agentDefinitionId; private Integer status;
    }
    @Data @ApiModel("工具审批策略请求") public static class ToolApprovalPolicy { private String toolApprovalPolicy; }
    @Data @ApiModel("智能体运行列表请求") public static class RunList {
        private Long current; private Long pageSize; private String agentDefinitionId; private String userId; private Integer status; private Long startTime; private Long endTime;
    }
    @Data @ApiModel("知识库绑定列表请求") public static class KnowledgeBindingList {
        private Long current; private Long pageSize; private String agentDefinitionId; private String knowledgeBaseId; private Integer status;
    }
    @Data @ApiModel("创建知识库绑定请求") public static class KnowledgeBindingCreate {
        private String agentDefinitionId; private String knowledgeBaseId; private Integer status;
    }
    @Data @ApiModel("MCP 服务器列表请求") public static class McpServerList {
        private Long current; private Long pageSize; private String name; private String code; private String transport; private Integer status;
    }
    @Data @ApiModel("导入 MCP 工具请求") public static class McpToolImport { private List<String> toolNames; }
    @Data @ApiModel("任务反馈请求") public static class TaskFeedback { private Object rating; private Object note; }
    @Data @ApiModel("任务交互输入请求") public static class TaskInput {
        private String messageId; private Map<String, Object> values = new java.util.LinkedHashMap<>();
        public void setMessageId(String messageId) { this.messageId = messageId; values.put("messageId", messageId); }
        @JsonAnySetter public void put(String name, Object value) { values.put(name, value); }
    }
    @Data @ApiModel("会话记忆偏好反馈请求") public static class SessionPreferenceFeedback {
        private String category; private String keyName; private String value; private Boolean confirmed;
    }
    @Data @ApiModel("技能列表请求") public static class SkillList {
        private Long current; private Long pageSize; private String name; private String code; private String category; private Integer status;
    }
    @Data @ApiModel("可用技能列表请求") public static class AvailableSkillList {
        private Long current; private Long pageSize; private String name; private String code; private String description; private String category;
    }
    @Data @ApiModel("已安装技能列表请求") public static class InstalledSkillList { private Long current; private Long pageSize; private String keyword; }
    @Data @ApiModel("智能体工具绑定列表请求") public static class ToolBindingList { private Long current; private Long pageSize; private String keyword; }
    @Data @ApiModel("可用工具列表请求") public static class AvailableToolList {
        private Long current; private Long pageSize; private String name; private String code; private String description; private String toolType;
    }
    @Data @ApiModel("工具列表请求") public static class ToolList {
        private Long current; private Long pageSize; private String name; private String code; private String toolType; private String mcpServerId; private Integer status;
    }
    @Data @ApiModel("刷新工具定义请求") public static class ToolDefinitionRefresh { private List<String> toolIds; }
    @Data @ApiModel("工具测试请求") public static class ToolTest {
        @ApiModelProperty("传入工具的参数") private Map<String, Object> parameters = new java.util.LinkedHashMap<>();
        @JsonAnySetter public void put(String name, Object value) { parameters.put(name, value); }
    }
    @Data @ApiModel("工具路由配置请求") public static class ToolRoutingConfig { private String embeddingModelId; private Integer topK; }
    @Data @ApiModel("工具调用日志列表请求") public static class ToolCallLogList {
        private Long current; private Long pageSize; private String runId; private String toolId; private String agentDefinitionId; private Integer status;
    }
    @Data @ApiModel("模型供应商列表请求") public static class ModelProviderList {
        private Long current; private Long pageSize; private String name; private String type; private Integer status;
    }
    @Data @ApiModel("模型目录请求") public static class ModelCatalogRequest {
        private String providerId; private String name; private String capabilities; private Integer contextWindow; private String endpointOverride; private Integer status; private String remark;
    }
    @Data @ApiModel("批量模型目录请求") public static class ModelCatalogBatch { private List<ModelCatalogRequest> models; }
    @Data @ApiModel("智能体应用列表请求") public static class ApplicationList { private Long current; private Long pageSize; private String name; private String code; private Integer status; }
    @Data @ApiModel("业务智能体异步运行请求") public static class BusinessRun {
        @ApiModelProperty(required = true) private String message;
        private String conversationId; private String idempotencyKey; private Map<String, Object> variables; private Map<String, Object> metadata;
    }
    @Data @ApiModel("业务智能体同步聊天请求") public static class BusinessChat {
        @ApiModelProperty(required = true) private String message;
        private String conversationId; private String idempotencyKey; private Map<String, Object> variables; private Map<String, Object> metadata;
    }
    @Data @ApiModel("业务智能体流式请求") public static class BusinessStream {
        @ApiModelProperty(required = true) private String message;
        private String conversationId;
    }
}
