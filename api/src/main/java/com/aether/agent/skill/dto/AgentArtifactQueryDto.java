package com.aether.agent.skill.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Query parameters for the current user's generated-file library.
 */
@Data
@ApiModel("生成制品库查询")
public class AgentArtifactQueryDto {
    @ApiModelProperty(value = "从 1 开始的页码", example = "1")
    private Long current = 1L;
    @ApiModelProperty(value = "每页数量", example = "24")
    private Long pageSize = 24L;
    @ApiModelProperty(value = "按文件名筛选", example = "quarterly-report")
    private String fileName;
    @ApiModelProperty(value = "按不含点号的扩展名筛选", example = "pdf")
    private String extension;
    @ApiModelProperty(value = "按生成智能体定义 ID 筛选", example = "agent-123")
    private String agentDefinitionId;
    @ApiModelProperty(value = "创建时间起始值（含），Unix 时间戳毫秒", example = "1767225600000")
    private Long startTime;
    @ApiModelProperty(value = "创建时间结束值（含），Unix 时间戳毫秒", example = "1767312000000")
    private Long endTime;
    /**
     * false: active library; true: recycle bin.
     */
    @ApiModelProperty(value = "是否查询回收站", example = "false")
    private Boolean recycled = false;
}
