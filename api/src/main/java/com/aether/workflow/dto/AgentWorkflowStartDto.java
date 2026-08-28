package com.aether.workflow.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * 手动启动流程时提交的开始节点变量。
 */
@Data
@ApiModel("手动工作流启动或变量更新请求")
public class AgentWorkflowStartDto {
    @ApiModelProperty(value = "供工作流使用的变量；启动端点支持空请求体，因此为可选", example = "{\"ticketId\":\"TKT-1042\",\"priority\":\"high\"}")
    private Map<String, Object> variables;
}
