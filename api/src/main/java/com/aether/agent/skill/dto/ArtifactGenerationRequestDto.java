package com.aether.agent.skill.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * 平台通用产物请求。Skill 只影响本轮模型生成内容的提示词规范，
 * 不再由模型选择脚本、模板或 Skill 编码。
 */
@Data
@ApiModel("生成制品请求")
public class ArtifactGenerationRequestDto {
    @ApiModelProperty(value = "文档标题", required = true, example = "Q1 customer-support summary")
    private String title;
    @ApiModelProperty(value = "输出文件名", required = true, example = "q1-support-summary.pdf")
    private String fileName;
    @ApiModelProperty(value = "输出格式", required = true, example = "pdf")
    private String format;
    @ApiModelProperty(value = "待渲染的源内容", required = true, example = "# Summary\nSupport volume increased by 12%.")
    private String content;
    /**
     * 可选的结构化文档计划；平台渲染器按需消费，未知字段会被忽略。
     */
    @ApiModelProperty(value = "可选的结构化文档计划", example = "{\"sections\":[{\"heading\":\"Summary\",\"content\":\"Support volume increased.\"}]}")
    private Map<String, Object> document;
}
