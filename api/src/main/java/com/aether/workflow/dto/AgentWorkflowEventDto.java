package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/** 外部业务事件提交给等待事件节点的载荷。 */
@Data
public class AgentWorkflowEventDto {
    @ApiModelProperty(value = "业务事件唯一 ID；通用事件入口必填，用于去重")
    private String eventId;
    @ApiModelProperty(value = "事件关联键；若节点配置了 correlationKey 则必须匹配")
    private String correlationKey;
    @ApiModelProperty(value = "事件变量，将合并到工作流变量")
    private Map<String, Object> data;
}
