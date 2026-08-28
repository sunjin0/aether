package com.aether.agent.skill.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 创建或编辑 Skill 草稿的请求参数。
 */
@Data
@ApiModel("技能草稿创建或更新请求")
public class AgentSkillDraftDto {
    @ApiModelProperty(value = "技能名称", required = true, example = "Support summarizer")
    private String name;
    @ApiModelProperty(value = "唯一技能编码", required = true, example = "support-summarizer")
    private String code;
    @ApiModelProperty(value = "技能描述", example = "Summarizes customer-support conversations")
    private String description;
    @ApiModelProperty(value = "技能分类", example = "support")
    private String category;
    @ApiModelProperty(value = "图标标识", example = "FileText")
    private String icon;
    @ApiModelProperty(value = "标签，通常以逗号分隔", example = "support,summary")
    private String tags;
    @ApiModelProperty(value = "提供给智能体的指令", required = true, example = "Summarize the conversation in three concise bullets.")
    private String instruction;
    @ApiModelProperty(value = "输入 JSON 架构", example = "{\"type\":\"object\",\"properties\":{\"transcript\":{\"type\":\"string\"}}}")
    private String inputSchema;
    @ApiModelProperty(value = "输出 JSON 架构", example = "{\"type\":\"object\",\"properties\":{\"summary\":{\"type\":\"string\"}}}")
    private String outputSchema;
    @ApiModelProperty(value = "工具使用策略", example = "ALLOW_DECLARED")
    private String toolPolicy;
    @ApiModelProperty(value = "简短路由描述", example = "Summarize support conversations")
    private String routingSummary;
    @ApiModelProperty(value = "触发此技能的词语", example = "[\"summarize ticket\",\"ticket summary\"]")
    private java.util.List<String> triggerTerms;
    @ApiModelProperty(value = "排除此技能的词语", example = "[\"translate\"]")
    private java.util.List<String> excludeTerms;
    @ApiModelProperty(value = "用于技能路由的关键词", example = "[\"support\",\"summary\"]")
    private java.util.List<String> routingKeywords;
    @ApiModelProperty(value = "用于路由的代表性请求", example = "[\"Summarize this customer issue\"]")
    private java.util.List<String> routingExamples;
    @ApiModelProperty(value = "此版本的说明", example = "Initial support workflow")
    private String changeNote;
    @ApiModelProperty(value = "声明的工具依赖")
    private List<AgentSkillToolDto> tools;
    @ApiModelProperty(value = "技能可用的知识库 ID", example = "[\"knowledge-base-123\"]")
    private List<String> knowledgeBaseIds;
    /** Applied to selected knowledge bases; defaults to RETRIEVE_ONLY for compatibility. */
    @ApiModelProperty(value = "知识库声明模式", example = "RETRIEVE_ONLY")
    private String knowledgeDeclarationMode;
}
