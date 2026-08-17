package com.aether.agent.skill.vo;

import lombok.Data;

import java.util.List;

/**
 * 预览结果：合成的技能指令段落、工具/知识库收敛范围与资源参考，以及最坏情况预算估算。
 */
@Data
public class AgentSkillPreviewVo {
    private String skillId;
    private String skillCode;
    private String skillName;
    private Integer versionNo;
    /**
     * 0-草稿预览，1-已发布版本预览
     */
    private Integer versionStatus;
    /**
     * 合成的 [Installed Skill] 指令段落（不含 Agent Identity）
     */
    private String prompt;
    private List<ToolItem> tools;
    private List<String> knowledgeBaseIds;
    private List<ResourceItem> resources;
    /**
     * 粗略 token 估算（字符数 / 4），仅用于提示词长度提示
     */
    private long estimatedTokens;

    /**
     * 表示ToolItem。
     */
    @Data
    public static class ToolItem {
        private String toolId;
        private String toolName;
        private String toolCode;
        private Boolean required;
        private Integer priority;
        /**
         * 工具当前是否可用（存在且启用）
         */
        private Boolean available;
    }

    /**
     * 表示资源Item。
     */
    @Data
    public static class ResourceItem {
        private String resourceId;
        private String name;
        private String type;
        private String language;
        private Long size;
        private String purpose;
        private String contentSha256;
    }
}
