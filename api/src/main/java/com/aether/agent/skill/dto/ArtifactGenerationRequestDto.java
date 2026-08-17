package com.aether.agent.skill.dto;

import lombok.Data;

import java.util.Map;

/**
 * 平台通用产物请求。Skill 只影响本轮模型生成内容的提示词规范，
 * 不再由模型选择脚本、模板或 Skill 编码。
 */
@Data
public class ArtifactGenerationRequestDto {
    private String title;
    private String fileName;
    private String format;
    private String content;
    /**
     * 可选的结构化文档计划；平台渲染器按需消费，未知字段会被忽略。
     */
    private Map<String, Object> document;
}
