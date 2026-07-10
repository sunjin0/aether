package com.aether.agent.model;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 模型聊天请求。
 */
@Data
public class ModelChatRequest {

    private ModelProvider provider;

    private AgentDefinition agent;

    private List<ModelChatMessage> messages;

    private List<AgentTool> tools;

    /**
     * 模型标识符（可选，覆盖AgentDefinition中的model）
     */
    private String model;

    /**
     * 是否流式响应
     */
    private Boolean stream;

    /**
     * 采样温度（0-2），越高越随机
     */
    private BigDecimal temperature;

    /**
     * 核采样概率（0-1）
     */
    private BigDecimal topP;

    /**
     * 生成的最大token数（已废弃，推荐使用maxCompletionTokens）
     */
    @Deprecated
    private Integer maxTokens;

    /**
     * 生成的最大完成token数（包含推理token）
     */
    private Integer maxCompletionTokens;

    /**
     * 频率惩罚（-2到2）
     */
    private BigDecimal frequencyPenalty;

    /**
     * 存在惩罚（-2到2）
     */
    private BigDecimal presencePenalty;

    /**
     * 停止生成的序列
     */
    private List<String> stop;

    /**
     * 响应格式（如 {"type": "json_object"}）
     */
    private Map<String, Object> responseFormat;

    /**
     * 可重复性种子
     */
    private Integer seed;

    /**
     * 最终用户标识符
     */
    private String user;

    /**
     * 流式响应选项（如 {"include_usage": true}）
     */
    private Map<String, Object> streamOptions;

    /**
     * 推理力度（none/minimal/low/medium/high）
     */
    private String reasoningEffort;

    /**
     * 是否返回输出token的对数概率
     */
    private Boolean logprobs;

    /**
     * 每个位置返回的最可能token数量（0-5）
     */
    private Integer topLogprobs;

    /**
     * 修改特定token出现概率的映射
     */
    private Map<Integer, Double> logitBias;

    /**
     * 工具选择模式（none/auto/required）
     */
    private String toolChoice;

    /**
     * 指定调用的工具名称
     */
    private String toolChoiceName;
}
